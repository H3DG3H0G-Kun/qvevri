package com.game.sim.threats.fungal;

import com.game.core.data.Fault;
import com.game.core.data.PhenoStage;
import com.game.sim.threats.api.ThreatCategory;
import com.game.sim.threats.api.ThreatContext;
import com.game.sim.threats.api.ThreatEffect;
import com.game.sim.threats.api.ThreatMemory;
import com.game.sim.threats.api.ThreatSource;

/**
 * Black Rot (Guignardia bidwellii) — a destructive fungal disease affecting
 * leaves, shoots, and fruit. Most damaging from shoot growth through fruit set;
 * infected berries turn into hard black mummified "raisins" (pycnidia).
 *
 * <p><b>Tell:</b> "circular tan leaf lesions with dark border; black shrivelled
 * berries (mummies)."
 *
 * <p><b>Trigger conditions:</b> wet weather (rain ≥ 2.5 mm or humidity ≥ 0.80)
 * + warm temperatures (13–32 °C, optimal ~26 °C). The fungus overwinters in
 * mummified berries; spring rains release ascospores.
 *
 * <p><b>Counters:</b>
 * <ul>
 *   <li>copperSpray01 — Bordeaux mixture, very effective.
 *   <li>canopyOpenness01 — reduces humidity in canopy.
 *   <li>leafPulled — improves spray coverage and airflow.
 * </ul>
 *
 * <p><b>Memory mapping:</b>
 * <ul>
 *   <li>{@code level}      — current disease severity 0..1
 *   <li>{@code aux}        — accumulated inoculum (overwintered mummies) pressure
 *   <li>{@code ticksActive}— consecutive active days this season
 *   <li>{@code established}— latched once disease crosses threshold
 * </ul>
 */
public final class BlackRot implements ThreatSource {

    // ── Temperature response ──────────────────────────────────────────────────
    private static final double TEMP_MIN_C            = 13.0;
    private static final double TEMP_OPT_C            = 26.0;
    private static final double TEMP_MAX_C            = 34.0;

    // ── Wetness thresholds ────────────────────────────────────────────────────
    private static final double RAIN_TRIGGER_MM       = 2.5;
    private static final double HUMIDITY_TRIGGER      = 0.80;

    // ── Accumulation ─────────────────────────────────────────────────────────
    private static final double SPORE_GAIN_PER_DAY    = 0.055;
    private static final double SPORE_DECAY_RATE      = 0.020;
    private static final double ESTABLISH_THRESHOLD   = 0.16;
    private static final double LEVEL_GAIN_RATE       = 0.040;
    private static final double LEVEL_DECAY_RATE      = 0.018;

    // ── Damage ────────────────────────────────────────────────────────────────
    /** Black rot is especially damaging to yield (mummified berries lost). */
    private static final double MAX_HEALTH_DELTA      = -0.012;
    private static final double MAX_YIELD_LOSS        = 0.030;
    private static final double MAX_QUALITY_PENALTY   = 0.012;

    // ── Counters ──────────────────────────────────────────────────────────────
    private static final double COPPER_EFFICACY       = 0.75;
    private static final double CANOPY_OPEN_EFFICACY  = 0.25;
    private static final double LEAF_PULL_BONUS       = 0.15;

    // ── Tells ─────────────────────────────────────────────────────────────────
    private static final String TELL_LIGHT   = "Circular tan leaf lesions with dark border — early black rot.";
    private static final String TELL_SEVERE  = "Black shrivelled berry mummies hanging in clusters — severe black rot.";

    @Override
    public String id() {
        return "fungal.blackrot";
    }

    @Override
    public ThreatCategory category() {
        return ThreatCategory.FUNGAL;
    }

    @Override
    public ThreatEffect evaluate(ThreatContext ctx) {
        ThreatMemory mem  = ctx.memory();
        PhenoStage stage  = ctx.vine().stage();

        // Black rot mainly matters from budbreak through ripening
        if (stage == PhenoStage.DORMANCY || stage == PhenoStage.HARVESTED
                || stage == PhenoStage.LEAF_FALL) {
            double decayedAux   = Math.max(0.0, mem.aux()   - SPORE_DECAY_RATE);
            double decayedLevel = Math.max(0.0, mem.level() - LEVEL_DECAY_RATE);
            ThreatMemory next = new ThreatMemory(
                    decayedLevel, decayedAux, 0, mem.yearsActive(), mem.established());
            return ThreatEffect.none(next);
        }

        double rain     = ctx.today().rainMm();
        double humidity = ctx.today().humidity01();
        double meanTemp = ctx.today().meanTempC();

        boolean wet          = rain >= RAIN_TRIGGER_MM || humidity >= HUMIDITY_TRIGGER;
        double tempFactor    = computeTempFactor(meanTemp);
        double rawPressure   = wet ? tempFactor : 0.0;

        double counter       = counterEfficacy(ctx);
        double netPressure   = rawPressure * (1.0 - counter);

        double newAux = Math.max(0.0,
                mem.aux() + netPressure * SPORE_GAIN_PER_DAY - SPORE_DECAY_RATE);
        boolean nowEstablished = mem.established() || newAux >= ESTABLISH_THRESHOLD;

        double newLevel;
        if (nowEstablished) {
            newLevel = mem.level() + netPressure * LEVEL_GAIN_RATE
                    - (netPressure < 0.01 ? LEVEL_DECAY_RATE : 0.0);
            newLevel = Math.min(1.0, Math.max(0.0, newLevel));
        } else {
            newLevel = Math.max(0.0, mem.level() - LEVEL_DECAY_RATE);
        }

        int newTicks = newLevel > 0.01 ? mem.ticksActive() + 1 : 0;
        ThreatMemory nextMem = new ThreatMemory(
                newLevel, newAux, newTicks, mem.yearsActive(), nowEstablished);

        if (!nowEstablished || newLevel < 0.01) {
            return ThreatEffect.none(nextMem);
        }

        double healthDelta    = newLevel * MAX_HEALTH_DELTA;
        double yieldMult      = 1.0 - newLevel * MAX_YIELD_LOSS;
        double qualityPenalty = newLevel * MAX_QUALITY_PENALTY;
        String tell           = newLevel > 0.40 ? TELL_SEVERE : TELL_LIGHT;

        return new ThreatEffect(
                healthDelta, yieldMult, qualityPenalty,
                Fault.NONE, false, tell, nextMem);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static double computeTempFactor(double mean) {
        if (mean < TEMP_MIN_C || mean > TEMP_MAX_C) return 0.0;
        if (mean <= TEMP_OPT_C) {
            return (mean - TEMP_MIN_C) / (TEMP_OPT_C - TEMP_MIN_C);
        } else {
            return 1.0 - (mean - TEMP_OPT_C) / (TEMP_MAX_C - TEMP_OPT_C);
        }
    }

    private static double counterEfficacy(ThreatContext ctx) {
        double copper   = ctx.copperSpray01()    * COPPER_EFFICACY;
        double canopy   = ctx.canopyOpenness01() * CANOPY_OPEN_EFFICACY;
        double leafPull = ctx.leafPulled()        ? LEAF_PULL_BONUS : 0.0;
        return Math.min(1.0, copper + canopy + leafPull);
    }
}
