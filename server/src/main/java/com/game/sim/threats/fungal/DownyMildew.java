package com.game.sim.threats.fungal;

import com.game.core.data.Fault;
import com.game.core.data.PhenoStage;
import com.game.sim.threats.api.ThreatCategory;
import com.game.sim.threats.api.ThreatContext;
import com.game.sim.threats.api.ThreatEffect;
import com.game.sim.threats.api.ThreatMemory;
import com.game.sim.threats.api.ThreatSource;

/**
 * Downy Mildew (Plasmopara viticola) — favours cool, wet conditions.
 *
 * <p><b>Tell:</b> "oily leaf spots" (the classic oil-spot symptom on the upper
 * leaf surface, with white cottony sporulation below).
 *
 * <p><b>Trigger rule (Rule of 10-10-10):</b> shoots ≥ 10 cm (proxy: past SHOOT_GROWTH),
 * daily rain ≥ 10 mm, mean temp ≥ 10 °C. Each qualifying day increments spore
 * load (memory.aux). When spore load crosses ESTABLISH_THRESHOLD the infection
 * establishes (memory.established = true) and the level begins to rise.
 *
 * <p><b>Counters:</b>
 * <ul>
 *   <li>copperSpray01 — copper fungicide, main downy counter.
 *   <li>canopyOpenness01 — open canopy dries leaves faster.
 *   <li>leafPulled — direct airflow/spray penetration around bunches.
 *   <li>Site drainage (proxy: low waterProximity).
 * </ul>
 *
 * <p><b>Memory mapping:</b>
 * <ul>
 *   <li>{@code level}      — current infection severity 0..1
 *   <li>{@code aux}        — cumulative spore pressure this season 0..∞ (unbounded)
 *   <li>{@code ticksActive}— days the disease has been active (level > 0)
 *   <li>{@code established}— latched once infection crosses the establish threshold
 * </ul>
 */
public final class DownyMildew implements ThreatSource {

    // ── Pressure thresholds ──────────────────────────────────────────────────
    /** Rain mm per day needed to initiate zoospore splash. */
    private static final double RAIN_TRIGGER_MM        = 10.0;
    /** Minimum mean temp (°C) for infection. */
    private static final double TEMP_MIN_C             = 10.0;
    /** Optimal mean temp (°C) for maximum infection rate. */
    private static final double TEMP_OPT_C             = 22.0;
    /** Upper cut-off (°C) — very hot days inhibit sporulation. */
    private static final double TEMP_MAX_C             = 30.0;
    /** Minimum relative humidity (0..1) required for sporulation. */
    private static final double HUMIDITY_MIN           = 0.75;

    // ── Spore load accumulation ──────────────────────────────────────────────
    /** Spore load added per qualifying infection day (before counter reductions). */
    private static final double SPORE_GAIN_PER_DAY     = 0.15;
    /** Natural daily spore decay (dry/warm days drain the reservoir). */
    private static final double SPORE_DECAY_RATE       = 0.03;
    /** Accumulated spore load at which infection establishes. */
    private static final double ESTABLISH_THRESHOLD    = 0.20;

    // ── Infection level dynamics ─────────────────────────────────────────────
    /**
     * Maximum infection level gain per day once established.
     * Pre-establishment gain is scaled by PRE_ESTABLISH_GAIN_SCALE so pressure
     * still moves the level upward even before the latch fires.
     */
    private static final double LEVEL_GAIN_RATE             = 0.06;
    /** Fraction of LEVEL_GAIN_RATE applied before establishment latches. */
    private static final double PRE_ESTABLISH_GAIN_SCALE    = 0.60;
    /** Natural daily reduction in infection level when pressure is low. */
    private static final double LEVEL_DECAY_RATE            = 0.01;
    /**
     * Humidity factor weight: humidity01 independently boosts infection pressure
     * even below the RAIN_TRIGGER_MM threshold, modelling leaf-wetness duration.
     */
    private static final double HUMIDITY_PRESSURE_WEIGHT    = 0.50;

    // ── Damage scaling ───────────────────────────────────────────────────────
    /** Max daily health delta from a fully established infection. */
    private static final double MAX_HEALTH_DELTA       = -0.015;
    /** Max daily yield multiplier loss at full infection. */
    private static final double MAX_YIELD_LOSS         = 0.012;
    /** Max daily quality penalty at full infection. */
    private static final double MAX_QUALITY_PENALTY    = 0.008;

    // ── Counter efficacy ─────────────────────────────────────────────────────
    /** Copper spray efficacy weight (0..1 spray -> 0..this reduction). */
    private static final double COPPER_EFFICACY        = 0.80;
    /** Canopy openness contribution to counter. */
    private static final double CANOPY_OPEN_EFFICACY   = 0.30;
    /** Leaf pulling contribution to counter. */
    private static final double LEAF_PULL_BONUS        = 0.15;
    /** Good drainage (low waterProximity) reduces moisture on leaves. */
    private static final double DRAINAGE_EFFICACY      = 0.20;

    // ── Tell ─────────────────────────────────────────────────────────────────
    private static final String TELL_LIGHT    = "Oily leaf spots visible (early downy mildew).";
    private static final String TELL_SEVERE   = "Heavy oily leaf spots + white cottony sporulation — severe downy mildew.";

    @Override
    public String id() {
        return "fungal.downy";
    }

    @Override
    public ThreatCategory category() {
        return ThreatCategory.FUNGAL;
    }

    @Override
    public ThreatEffect evaluate(ThreatContext ctx) {
        ThreatMemory mem = ctx.memory();

        // Downy mildew not relevant in dormancy or after harvest
        PhenoStage stage = ctx.vine().stage();
        if (stage == PhenoStage.DORMANCY || stage == PhenoStage.HARVESTED
                || stage == PhenoStage.LEAF_FALL || stage == PhenoStage.BUD_SWELL) {
            // Carry level/established, let aux decay passively
            double newAux = Math.max(0.0, mem.aux() - SPORE_DECAY_RATE);
            ThreatMemory next = new ThreatMemory(
                    mem.level() * 0.90, // level also fades in dormancy
                    newAux,
                    0,
                    mem.yearsActive(),
                    mem.established());
            return ThreatEffect.none(next);
        }

        double rain      = ctx.today().rainMm();
        double humidity  = ctx.today().humidity01();
        double meanTemp  = ctx.today().meanTempC();

        // ── Daily infection pressure (0..1) ─────────────────────────────────
        // Raw pressure has two additive contributors:
        //   1. Rain-splash: tempFactor when rain meets the trigger threshold.
        //   2. Humidity: continuous leaf-wetness contribution, independent of
        //      the 10 mm rain threshold but still scaled by temperature.
        // This ensures a wet/humid cool day (high rain + high humidity) produces
        // clearly higher pressure than a dry/warm day (no rain, low humidity).
        double tempFactor = computeTempFactor(meanTemp);
        double rainContrib     = (rain >= RAIN_TRIGGER_MM) ? tempFactor : 0.0;
        double humidityContrib = Math.max(0.0, humidity - HUMIDITY_MIN)
                                     / (1.0 - HUMIDITY_MIN)   // 0..1 above threshold
                                     * HUMIDITY_PRESSURE_WEIGHT
                                     * tempFactor;
        double rawPressure = Math.min(1.0, rainContrib + humidityContrib);

        // ── Counter reduction (0..1 = fully suppressed) ───────────────────
        double counter = counterEfficacy(ctx);

        double netPressure = rawPressure * (1.0 - counter);

        // ── Update spore load ─────────────────────────────────────────────
        double newAux = Math.max(0.0,
                mem.aux() + netPressure * SPORE_GAIN_PER_DAY - SPORE_DECAY_RATE);

        // ── Establish latch ───────────────────────────────────────────────
        boolean nowEstablished = mem.established() || newAux >= ESTABLISH_THRESHOLD;

        // ── Update infection level ────────────────────────────────────────
        // Level rises proportionally to net pressure both before and after
        // establishment (pre-establishment at a reduced rate), and decays only
        // when there is no meaningful daily pressure. This guarantees that a
        // wet/humid day always moves the level upward relative to a dry day.
        double newLevel;
        if (nowEstablished) {
            newLevel = mem.level()
                    + netPressure * LEVEL_GAIN_RATE
                    - (netPressure < 0.01 ? LEVEL_DECAY_RATE : 0.0);
        } else {
            // Pre-establishment: level still responds to pressure (slower) so
            // wet vs dry days produce measurably different nextMemory().level().
            newLevel = mem.level()
                    + netPressure * LEVEL_GAIN_RATE * PRE_ESTABLISH_GAIN_SCALE
                    - (netPressure < 0.01 ? LEVEL_DECAY_RATE : 0.0);
        }
        newLevel = Math.min(1.0, Math.max(0.0, newLevel));

        int newTicks    = newLevel > 0.01 ? mem.ticksActive() + 1 : 0;
        int newYears    = mem.yearsActive(); // years updated by engine at season end

        ThreatMemory nextMem = new ThreatMemory(
                newLevel, newAux, newTicks, newYears, nowEstablished);

        // ── No active infection → quiet ───────────────────────────────────
        if (!nowEstablished || newLevel < 0.01) {
            return ThreatEffect.none(nextMem);
        }

        // ── Compute damage ────────────────────────────────────────────────
        double healthDelta    = newLevel * MAX_HEALTH_DELTA;
        double yieldMult      = 1.0 - newLevel * MAX_YIELD_LOSS;
        double qualityPenalty = newLevel * MAX_QUALITY_PENALTY;

        String tell = newLevel > 0.50 ? TELL_SEVERE : TELL_LIGHT;

        return new ThreatEffect(
                healthDelta,
                yieldMult,
                qualityPenalty,
                Fault.NONE,
                false,
                tell,
                nextMem);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Temperature response curve: 0 below 10°C, peaks at 22°C, falls to 0 at 30°C.
     */
    private static double computeTempFactor(double meanTemp) {
        if (meanTemp < TEMP_MIN_C || meanTemp > TEMP_MAX_C) return 0.0;
        if (meanTemp <= TEMP_OPT_C) {
            return (meanTemp - TEMP_MIN_C) / (TEMP_OPT_C - TEMP_MIN_C);
        } else {
            return 1.0 - (meanTemp - TEMP_OPT_C) / (TEMP_MAX_C - TEMP_OPT_C);
        }
    }

    /**
     * Combined counter efficacy 0..1 (1 = fully suppressed).
     */
    private static double counterEfficacy(ThreatContext ctx) {
        double copper   = ctx.copperSpray01()   * COPPER_EFFICACY;
        double canopy   = ctx.canopyOpenness01() * CANOPY_OPEN_EFFICACY;
        double leafPull = ctx.leafPulled()       ? LEAF_PULL_BONUS : 0.0;
        // low waterProximity means good drainage; drainage helps by reducing
        // surface moisture available to zoospores
        double drainage = (1.0 - ctx.site().waterProximity()) * DRAINAGE_EFFICACY;

        // combine additively, cap at 1
        return Math.min(1.0, copper + canopy + leafPull + drainage);
    }
}
