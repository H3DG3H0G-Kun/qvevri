package com.game.sim.threats.engine;

import com.game.core.data.Fault;
import com.game.core.data.VineState;
import com.game.sim.threats.api.ThreatContext;
import com.game.sim.threats.api.ThreatEffect;
import com.game.sim.threats.api.ThreatMemory;
import com.game.sim.threats.api.ThreatSource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Core threat evaluation engine (SIM-THREATS-SPEC §4 Lane D).
 *
 * <p>Holds an ordered {@link List} of {@link ThreatSource} instances and a
 * {@link LinkedHashMap} of per-source {@link ThreatMemory} keyed by source id.
 * The LinkedHashMap guarantees deterministic iteration order.
 *
 * <h2>Aggregation rules (spec §4)</h2>
 * <ul>
 *   <li>{@code healthFraction} = clamp(prev + Σ healthDelta, 0, 1)</li>
 *   <li>{@code potentialYieldKg} *= Π yieldMultiplier</li>
 *   <li>{@code qualityPenalty} = min(1, running_total + Σ qualityPenalty01)</li>
 *   <li>{@code inducedFault} = most-severe non-NONE
 *       (order: STUCK_FERMENT &gt; VOLATILE_ACIDITY &gt; REDUCTION_H2S &gt; OXIDATION &gt; NONE)</li>
 *   <li>{@code killVine} = OR of all killVine flags</li>
 * </ul>
 */
public final class ThreatEngine {

    // Fault severity order: index = severity (higher = worse)
    private static final List<Fault> FAULT_SEVERITY = List.of(
            Fault.NONE,
            Fault.OXIDATION,
            Fault.REDUCTION_H2S,
            Fault.VOLATILE_ACIDITY,
            Fault.STUCK_FERMENT
    );

    // Ordered list — never mutated after construction
    private final List<ThreatSource> sources;
    // LinkedHashMap: deterministic insertion-order iteration, keyed by source id
    private final Map<String, ThreatMemory> memories;
    // Cumulative quality penalty accumulated across the season
    private double cumulativeQualityPenalty;

    // Locked fruit-set yield (peak seen) and the seasonal yield multiplier, kept
    // separate so daily reductions don't compound through the vine feedback loop.
    private double baseYieldKg = 0.0;
    private double cumulativeYieldMult = 1.0;
    /** A surviving vine never yields less than this fraction of its locked crop. */
    private static final double MIN_SURVIVING_YIELD_FRACTION = 0.08;

    /**
     * Construct an engine with the given ordered source list.
     * All per-source memories initialise to {@link ThreatMemory#none()}.
     *
     * @param sources ordered list of threat sources (use {@link ThreatRegistry#all()})
     */
    public ThreatEngine(List<ThreatSource> sources) {
        this.sources = List.copyOf(sources);
        this.memories = new LinkedHashMap<>();
        for (ThreatSource s : sources) {
            memories.put(s.id(), ThreatMemory.none());
        }
        this.cumulativeQualityPenalty = 0.0;
    }

    /**
     * Evaluate all threats for one day and return the updated vine + report.
     *
     * <p>Must be called AFTER the daily vine tick (spec §2 composition):
     * {@code vine = vineSimulator.tick(...); result = engine.step(vine, env)}.
     *
     * @param vine current post-tick vine state
     * @param env  all daily inputs (weather, levers, rng)
     * @return updated vine with threat adjustments applied, plus today's threat report
     */
    public DayResult step(VineState vine, DayInputs env) {
        int doy = env.today().dayOfYear();

        double sumHealthDelta   = 0.0;
        double productYieldMult = 1.0;
        double sumQualPenalty   = 0.0;
        Fault  worstFault       = Fault.NONE;
        boolean kill            = false;
        List<String> tells      = new ArrayList<>();

        for (ThreatSource source : sources) {
            ThreatMemory mem = memories.get(source.id());

            // Inject per-source named RNG stream + prior memory into the context
            ThreatContext ctx = new ThreatContext(
                    doy,
                    env.today(),
                    env.gddSeason(),
                    vine,
                    env.site(),
                    env.ownRoots(),
                    env.canopyOpenness01(),
                    env.leafPulled(),
                    env.copperSpray01(),
                    env.sulfurSpray01(),
                    env.netting(),
                    env.guardDog(),
                    env.falcons(),
                    env.cats(),
                    env.ducks(),
                    env.coverCrop01(),
                    env.rng().stream("threat." + source.id()),
                    mem
            );

            ThreatEffect effect = source.evaluate(ctx);

            // Persist updated memory immediately (not batched)
            memories.put(source.id(), effect.nextMemory());

            // Aggregate
            sumHealthDelta   += effect.healthDelta();
            productYieldMult *= effect.yieldMultiplier();
            sumQualPenalty   += effect.qualityPenalty01();
            if (severityOf(effect.inducedFault()) > severityOf(worstFault)) {
                worstFault = effect.inducedFault();
            }
            kill = kill || effect.killVine();
            if (!effect.tell().isEmpty()) {
                tells.add(effect.tell());
            }
        }

        // Update cumulative quality penalty (capped at 1.0)
        cumulativeQualityPenalty = Math.min(1.0, cumulativeQualityPenalty + sumQualPenalty);

        // Apply to vine state.
        double newHealth = clamp(vine.healthFraction() + sumHealthDelta, 0.0, 1.0);

        // Yield: accumulate a SEASONAL multiplier against the locked fruit-set base
        // instead of compounding each day's product onto an already-reduced
        // (fed-back) yield — which otherwise decays a season's crop to ~0. A vine
        // that survives retains at least MIN_SURVIVING_YIELD_FRACTION; only a dead
        // vine yields nothing.
        baseYieldKg = Math.max(baseYieldKg, vine.potentialYieldKg());
        cumulativeYieldMult *= productYieldMult;
        double effectiveYieldMult = kill
                ? 0.0
                : Math.max(cumulativeYieldMult, MIN_SURVIVING_YIELD_FRACTION);
        double newYield = baseYieldKg * effectiveYieldMult;

        VineState updatedVine = new VineState(
                vine.stage(),
                vine.gddAccum(),
                newHealth,
                newYield,
                vine.brix(),
                vine.taGL(),
                vine.pH(),
                vine.yanMgL(),
                vine.tanninRipeness01()
        );

        ThreatReport report = new ThreatReport(
                List.copyOf(tells),
                sumHealthDelta,
                productYieldMult - 1.0,
                cumulativeQualityPenalty,
                worstFault,
                kill
        );

        return new DayResult(updatedVine, report);
    }

    /** Current cumulative quality penalty (0..1) — for use by the harness at harvest. */
    public double cumulativeQualityPenalty() {
        return cumulativeQualityPenalty;
    }

    /**
     * Advance all per-source memories from one season to the next.
     *
     * <p>Per spec §4: increments {@code yearsActive} on every stored memory and
     * resets the within-season {@code ticksActive} counter.  The {@code level},
     * {@code aux}, and {@code established} fields are preserved so that chronic /
     * terminal threats (phylloxera, viruses, esca) survive the winter correctly.
     *
     * <p>Also resets the cumulative quality penalty for the new season.
     *
     * <p>Call this once at the end of each simulated year before starting the
     * next year's daily loop.
     */
    public void endSeason() {
        memories.replaceAll((id, mem) -> new ThreatMemory(
                mem.level(),
                mem.aux(),
                0,                         // reset within-season ticksActive
                mem.yearsActive() + 1,     // increment cross-season counter
                mem.established()          // preserve established latch
        ));
        cumulativeQualityPenalty = 0.0;
        baseYieldKg = 0.0;
        cumulativeYieldMult = 1.0;
    }

    /** Read-only snapshot of current per-source memories (for inspection/testing). */
    public Map<String, ThreatMemory> memories() {
        return Map.copyOf(memories);
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private static int severityOf(Fault f) {
        int idx = FAULT_SEVERITY.indexOf(f);
        return idx < 0 ? 0 : idx;
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
