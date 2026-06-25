package com.game.sim.threats.fungal;

import com.game.core.data.Fault;
import com.game.core.data.PhenoStage;
import com.game.sim.threats.api.ThreatCategory;
import com.game.sim.threats.api.ThreatContext;
import com.game.sim.threats.api.ThreatEffect;
import com.game.sim.threats.api.ThreatMemory;
import com.game.sim.threats.api.ThreatSource;

/**
 * Esca / Grapevine Trunk Disease (GTD) — a complex of wood-rotting fungi
 * (Phaeomoniella chlamydospora, Phaeoacremonium spp., Fomitiporia mediterranea)
 * that enters via fresh pruning wounds and slowly destroys the vascular tissue.
 *
 * <h3>Terminal latch via ThreatMemory:</h3>
 * <ul>
 *   <li>Once established ({@code memory.established = true}), the disease CANNOT
 *       be cured — it is terminal. The vine can survive for years in a declining
 *       state ("chronic Esca") but may suffer acute apoplexy (sudden wilting).
 *   <li>Establishment is latched in {@code memory.established}.
 *   <li>{@code memory.yearsActive} tracks how many seasons the trunk has been
 *       infected; severity increases with time.
 *   <li>{@code memory.aux} tracks cumulative wood necrosis 0..1; when it reaches
 *       {@code NECROSIS_KILL_THRESHOLD} the vine is killed.
 * </ul>
 *
 * <h3>Entry route (pruning wounds):</h3>
 * The inoculation risk occurs during dormancy/early spring (pruning season).
 * The harness currently does not expose "pruning wound freshness" as a distinct
 * lever, so we proxy it: infection attempt happens in early season (dayOfYear
 * within {@code WOUND_WINDOW_END}), conditioned on wet weather (spores need
 * moisture to germinate in wound tissue).
 *
 * <p>Counter: there is NO cure once established. Prevention only — the
 * {@code copperSpray01} lever is used as a proxy for wound-paste / Bordeaux
 * pruning wound protection; it reduces the establishment probability.
 *
 * <h3>Memory mapping:</h3>
 * <ul>
 *   <li>{@code level}      — current symptom severity (leaf tiger-stripe / wilting) 0..1
 *   <li>{@code aux}        — cumulative wood necrosis fraction 0..1
 *   <li>{@code ticksActive}— days with active symptoms this season
 *   <li>{@code yearsActive}— seasons since establishment (incremented per season end)
 *   <li>{@code established}— permanent latch: vine is infected with trunk disease
 * </ul>
 *
 * <h3>Tell:</h3>
 * "Tiger-stripe leaf chlorosis and/or apoplexy (sudden wilting) — Esca trunk disease."
 */
public final class EscaTrunkDisease implements ThreatSource {

    // ── Wound-entry window (day of year, Northern-hemisphere pruning season) ──
    /** Pruning + wound-infection window: dormancy through shoot growth. */
    private static final int  WOUND_WINDOW_END_DOY    = 120;

    // ── Establishment conditions ──────────────────────────────────────────────
    /** Wet-wound probability modifier: rain or high humidity on wound day. */
    private static final double HUMID_ENTRY_HUMIDITY  = 0.70;
    private static final double HUMID_ENTRY_RAIN_MM   = 5.0;
    /** Per-day establishment probability in the wound window (before counters). */
    private static final double ESTABLISH_PROB_PER_DAY = 0.025;
    /** Wound paste / copper reduces establishment probability. */
    private static final double COPPER_PROTECT_EFFICACY = 0.70;

    // ── Wood necrosis progression ─────────────────────────────────────────────
    /** Daily necrosis increase once established — slow but relentless. */
    private static final double NECROSIS_GAIN_PER_YEAR = 0.08; // per season-year
    /** Converts to daily rate over a 200-day active season proxy. */
    private static final double NECROSIS_GAIN_DAILY    = NECROSIS_GAIN_PER_YEAR / 200.0;
    /** Wood necrosis fraction at which the vine is killed. */
    private static final double NECROSIS_KILL_THRESHOLD = 0.90;

    // ── Symptom / level ───────────────────────────────────────────────────────
    /** Level (symptom severity) rises with years active — chronic progression. */
    private static final double LEVEL_GAIN_PER_YEAR   = 0.12;
    /**
     * Acute apoplexy (sudden wilting) probability per day in hot dry spells —
     * proxied by low humidity and high temp; causes a health spike damage.
     */
    private static final double APOPLEXY_HUMIDITY_MAX = 0.35;
    private static final double APOPLEXY_TEMP_MIN_C   = 30.0;
    private static final double APOPLEXY_PROB         = 0.04;

    // ── Damage scaling ────────────────────────────────────────────────────────
    private static final double CHRONIC_MAX_HEALTH_DELTA   = -0.008;
    private static final double APOPLEXY_HEALTH_SPIKE      = -0.060;
    private static final double CHRONIC_MAX_YIELD_LOSS     = 0.012;
    private static final double CHRONIC_MAX_QUALITY_PENALTY = 0.007;

    // ── Tells ─────────────────────────────────────────────────────────────────
    private static final String TELL_EARLY    = "Tiger-stripe leaf chlorosis: Esca trunk disease detected.";
    private static final String TELL_APOPLEXY = "APOPLEXY: vine wilted suddenly — acute Esca episode, severe wood necrosis.";
    private static final String TELL_TERMINAL = "Vine in severe decline from Esca trunk disease — near-terminal necrosis.";

    @Override
    public String id() {
        return "fungal.esca";
    }

    @Override
    public ThreatCategory category() {
        return ThreatCategory.FUNGAL;
    }

    @Override
    public ThreatEffect evaluate(ThreatContext ctx) {
        ThreatMemory mem  = ctx.memory();
        boolean established = mem.established();

        // ── Phase 1: Attempt establishment (wound window, not yet established) ─
        if (!established) {
            return attemptEstablishment(ctx, mem);
        }

        // ── Phase 2: Established — chronic progression ─────────────────────
        return progressChronicDisease(ctx, mem);
    }

    // ── Establishment attempt ─────────────────────────────────────────────────

    private ThreatEffect attemptEstablishment(ThreatContext ctx, ThreatMemory mem) {
        int doy = ctx.dayOfYear();

        // Only attempt entry during the pruning-wound window
        if (doy > WOUND_WINDOW_END_DOY) {
            // Outside window; no change
            return ThreatEffect.none(new ThreatMemory(0, 0, 0, 0, false));
        }

        double rain     = ctx.today().rainMm();
        double humidity = ctx.today().humidity01();

        boolean moistWound = humidity >= HUMID_ENTRY_HUMIDITY || rain >= HUMID_ENTRY_RAIN_MM;
        if (!moistWound) {
            return ThreatEffect.none(mem);
        }

        // Counter: copper wound paste
        double copperProtection = ctx.copperSpray01() * COPPER_PROTECT_EFFICACY;
        double netProb = ESTABLISH_PROB_PER_DAY * (1.0 - copperProtection);
        boolean established = ctx.rng().nextDouble() < netProb;

        if (!established) {
            return ThreatEffect.none(mem);
        }

        // Established for the first time
        ThreatMemory nextMem = new ThreatMemory(
                0.05,   // small initial symptom level
                0.01,   // tiny initial necrosis
                1,
                0,      // yearsActive = 0, incremented at season end by engine
                true);

        return new ThreatEffect(
                -0.005,  // tiny initial health cost from wound infection
                1.0,
                0.002,
                Fault.NONE,
                false,
                "Pruning wound infected by Esca fungal complex — early trunk colonisation.",
                nextMem);
    }

    // ── Chronic disease progression ───────────────────────────────────────────

    private ThreatEffect progressChronicDisease(ThreatContext ctx, ThreatMemory mem) {
        PhenoStage stage = ctx.vine().stage();

        // Esca symptoms mostly show during the growing season (leaf symptoms)
        // Wood necrosis advances year-round but slowly
        double necrosisGain = NECROSIS_GAIN_DAILY;
        double newAux       = Math.min(1.0, mem.aux() + necrosisGain);

        // Symptom level rises with years of infection
        double targetLevel  = Math.min(1.0, mem.yearsActive() * LEVEL_GAIN_PER_YEAR + 0.05);
        double newLevel     = mem.level() + (targetLevel - mem.level()) * 0.05; // smoothed
        newLevel = Math.min(1.0, Math.max(0.0, newLevel));

        boolean activeSymptoms = (stage != PhenoStage.DORMANCY
                && stage != PhenoStage.BUD_SWELL
                && stage != PhenoStage.HARVESTED
                && stage != PhenoStage.LEAF_FALL);
        int newTicks = activeSymptoms ? mem.ticksActive() + 1 : mem.ticksActive();

        // ── Check apoplexy risk (hot dry day stress) ────────────────────────
        double humidity = ctx.today().humidity01();
        double meanTemp = ctx.today().meanTempC();
        boolean apoplexyCandidateCondition = humidity <= APOPLEXY_HUMIDITY_MAX
                && meanTemp >= APOPLEXY_TEMP_MIN_C
                && activeSymptoms
                && newLevel > 0.30;

        boolean apoplexy = apoplexyCandidateCondition
                && ctx.rng().nextDouble() < APOPLEXY_PROB;

        // ── Terminal kill check ─────────────────────────────────────────────
        boolean killVine = newAux >= NECROSIS_KILL_THRESHOLD;

        ThreatMemory nextMem = new ThreatMemory(
                newLevel, newAux, newTicks, mem.yearsActive(), true);

        if (killVine) {
            return new ThreatEffect(
                    -1.0, 0.0, 1.0, Fault.NONE, true,
                    "Vine killed by Esca trunk disease — complete vascular necrosis.",
                    nextMem);
        }

        double healthDelta;
        String tell;

        if (apoplexy) {
            healthDelta = APOPLEXY_HEALTH_SPIKE;
            tell        = TELL_APOPLEXY;
        } else if (!activeSymptoms) {
            // Dormant season: just track necrosis, no visible symptom
            return ThreatEffect.none(nextMem);
        } else if (newLevel > 0.60) {
            healthDelta = newLevel * CHRONIC_MAX_HEALTH_DELTA;
            tell        = TELL_TERMINAL;
        } else {
            healthDelta = newLevel * CHRONIC_MAX_HEALTH_DELTA;
            tell        = newLevel > 0.15 ? TELL_EARLY : "";
        }

        double yieldMult      = 1.0 - newLevel * CHRONIC_MAX_YIELD_LOSS;
        double qualityPenalty = newLevel * CHRONIC_MAX_QUALITY_PENALTY;

        return new ThreatEffect(
                healthDelta, yieldMult, qualityPenalty,
                Fault.NONE, false, tell, nextMem);
    }
}
