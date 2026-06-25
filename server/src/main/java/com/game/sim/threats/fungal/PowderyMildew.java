package com.game.sim.threats.fungal;

import com.game.core.data.Fault;
import com.game.core.data.PhenoStage;
import com.game.sim.threats.api.ThreatCategory;
import com.game.sim.threats.api.ThreatContext;
import com.game.sim.threats.api.ThreatEffect;
import com.game.sim.threats.api.ThreatMemory;
import com.game.sim.threats.api.ThreatSource;

/**
 * Powdery Mildew (Erysiphe necator / Uncinula necator) — unlike downy, this
 * oidium thrives in warm-to-moderate temps with moderate humidity and shade.
 * It does NOT need free water (in fact heavy rain washes conidia away).
 *
 * <p><b>Tell:</b> "white dusty film on leaves and young berries"
 *
 * <p><b>Trigger window:</b> mean temp 16–32 °C (optimal 25–28 °C); relative
 * humidity 40–90 % (very dry air inhibits); crucially no heavy rain that day.
 * Deep shade in dense canopies massively amplifies the risk.
 *
 * <p><b>Counters:</b>
 * <ul>
 *   <li>sulfurSpray01 — sulfur dust/wettable sulfur, classic powdery counter.
 *   <li>canopyOpenness01 — open canopy lets sun/wind in, killing surface mycelium.
 *   <li>leafPulled — removes the shaded micro-climate near bunches.
 *   <li>sunlight proxy: south-facing aspect (aspectDeg ~180) and low altitude.
 * </ul>
 *
 * <p><b>Memory mapping:</b>
 * <ul>
 *   <li>{@code level}      — current infection severity 0..1
 *   <li>{@code aux}        — cumulative conidial pressure (spore load) this season
 *   <li>{@code ticksActive}— days the disease has been active
 *   <li>{@code established}— latched once infection crosses threshold
 * </ul>
 */
public final class PowderyMildew implements ThreatSource {

    // ── Temperature window ────────────────────────────────────────────────────
    private static final double TEMP_MIN_C           = 16.0;
    private static final double TEMP_OPT_LOW_C       = 25.0;
    private static final double TEMP_OPT_HIGH_C      = 28.0;
    private static final double TEMP_MAX_C           = 35.0;

    // ── Humidity window ───────────────────────────────────────────────────────
    /** Very dry air slows spore germination. */
    private static final double HUMIDITY_MIN         = 0.40;
    /** Saturation (free water) suppresses powdery by washing conidia. */
    private static final double HUMIDITY_MAX         = 0.92;
    /** Heavy rain threshold — above this, washing suppresses powdery. */
    private static final double RAIN_SUPPRESSION_MM  = 8.0;

    // ── Shade amplifier ───────────────────────────────────────────────────────
    /** A fully closed canopy multiplies pressure. */
    private static final double SHADE_AMPLIFIER      = 1.60;

    // ── Spore accumulation ────────────────────────────────────────────────────
    private static final double SPORE_GAIN_PER_DAY   = 0.07;
    private static final double SPORE_DECAY_RATE     = 0.025;
    private static final double ESTABLISH_THRESHOLD  = 0.18;

    // ── Level dynamics ────────────────────────────────────────────────────────
    private static final double LEVEL_GAIN_RATE      = 0.035;
    private static final double LEVEL_DECAY_RATE     = 0.020;

    // ── Damage ────────────────────────────────────────────────────────────────
    private static final double MAX_HEALTH_DELTA     = -0.012;
    private static final double MAX_YIELD_LOSS       = 0.010;
    private static final double MAX_QUALITY_PENALTY  = 0.010;

    // ── Counter efficacy ──────────────────────────────────────────────────────
    private static final double SULFUR_EFFICACY       = 0.80;
    private static final double CANOPY_OPEN_EFFICACY  = 0.35;
    private static final double LEAF_PULL_BONUS       = 0.20;
    /** South-facing (aspectDeg ≈ 180) adds sun exposure bonus. */
    private static final double SUN_ASPECT_BONUS      = 0.15;

    // ── Tells ─────────────────────────────────────────────────────────────────
    private static final String TELL_LIGHT   = "White dusty film on shoot tips — early powdery mildew.";
    private static final String TELL_SEVERE  = "Dense white powder coating leaves and berries — severe powdery mildew.";

    @Override
    public String id() {
        return "fungal.powdery";
    }

    @Override
    public ThreatCategory category() {
        return ThreatCategory.FUNGAL;
    }

    @Override
    public ThreatEffect evaluate(ThreatContext ctx) {
        ThreatMemory mem = ctx.memory();

        PhenoStage stage = ctx.vine().stage();
        if (stage == PhenoStage.DORMANCY || stage == PhenoStage.HARVESTED
                || stage == PhenoStage.LEAF_FALL || stage == PhenoStage.BUD_SWELL) {
            double decayedAux = Math.max(0.0, mem.aux() - SPORE_DECAY_RATE);
            ThreatMemory next = new ThreatMemory(
                    mem.level() * 0.90, decayedAux, 0,
                    mem.yearsActive(), mem.established());
            return ThreatEffect.none(next);
        }

        double rain     = ctx.today().rainMm();
        double humidity = ctx.today().humidity01();
        double meanTemp = ctx.today().meanTempC();

        // ── Raw daily pressure ────────────────────────────────────────────
        double tempFactor     = computeTempFactor(meanTemp);
        double humidFactor    = computeHumidFactor(humidity);
        boolean rainSuppressed = rain >= RAIN_SUPPRESSION_MM;
        double shadeFactor    = computeShadeFactor(ctx.canopyOpenness01());

        double rawPressure = rainSuppressed ? 0.0
                : tempFactor * humidFactor * shadeFactor;

        // ── Counter reduction ─────────────────────────────────────────────
        double counter = counterEfficacy(ctx);
        double netPressure = rawPressure * (1.0 - counter);

        // ── Spore load ────────────────────────────────────────────────────
        double newAux = Math.max(0.0,
                mem.aux() + netPressure * SPORE_GAIN_PER_DAY - SPORE_DECAY_RATE);

        boolean nowEstablished = mem.established() || newAux >= ESTABLISH_THRESHOLD;

        // ── Infection level ───────────────────────────────────────────────
        double newLevel;
        if (nowEstablished) {
            newLevel = mem.level()
                    + netPressure * LEVEL_GAIN_RATE
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

        // ── Damage ────────────────────────────────────────────────────────
        double healthDelta    = newLevel * MAX_HEALTH_DELTA;
        double yieldMult      = 1.0 - newLevel * MAX_YIELD_LOSS;
        double qualityPenalty = newLevel * MAX_QUALITY_PENALTY;
        String tell           = newLevel > 0.50 ? TELL_SEVERE : TELL_LIGHT;

        return new ThreatEffect(
                healthDelta, yieldMult, qualityPenalty,
                Fault.NONE, false, tell, nextMem);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static double computeTempFactor(double mean) {
        if (mean < TEMP_MIN_C || mean > TEMP_MAX_C) return 0.0;
        if (mean <= TEMP_OPT_LOW_C)  return (mean - TEMP_MIN_C)  / (TEMP_OPT_LOW_C  - TEMP_MIN_C);
        if (mean <= TEMP_OPT_HIGH_C) return 1.0;
        return 1.0 - (mean - TEMP_OPT_HIGH_C) / (TEMP_MAX_C - TEMP_OPT_HIGH_C);
    }

    private static double computeHumidFactor(double h) {
        if (h < HUMIDITY_MIN || h > HUMIDITY_MAX) return 0.0;
        // Linear ramp: full at 0.65..0.85, reduced at edges
        double lo = HUMIDITY_MIN, hi = HUMIDITY_MAX;
        double midLo = 0.55, midHi = 0.85;
        if (h <= midLo) return (h - lo) / (midLo - lo);
        if (h <= midHi) return 1.0;
        return 1.0 - (h - midHi) / (hi - midHi);
    }

    /**
     * Dense canopy (low openness) amplifies powdery via shade and stagnant air.
     * Returns a multiplier ≥ 1 (shade amplifies) that collapses to ~0.7 in an open canopy.
     */
    private static double computeShadeFactor(double canopyOpenness01) {
        // 0 openness → SHADE_AMPLIFIER, 1 openness → 0.7 (open air suppresses slightly)
        return SHADE_AMPLIFIER - canopyOpenness01 * (SHADE_AMPLIFIER - 0.70);
    }

    private static double counterEfficacy(ThreatContext ctx) {
        double sulfur    = ctx.sulfurSpray01()   * SULFUR_EFFICACY;
        double canopy    = ctx.canopyOpenness01() * CANOPY_OPEN_EFFICACY;
        double leafPull  = ctx.leafPulled()       ? LEAF_PULL_BONUS : 0.0;
        // South-facing gets more sun; sun kills powdery surface mycelium
        double sunBonus  = computeSunBonus(ctx.site().aspectDeg());
        return Math.min(1.0, sulfur + canopy + leafPull + sunBonus);
    }

    /**
     * Bonus for south-facing aspect (aspectDeg ≈ 180 = max sun in northern hemisphere).
     */
    private static double computeSunBonus(double aspectDeg) {
        // cos distance from 180° south, normalised 0..1
        double rad    = Math.toRadians(aspectDeg - 180.0);
        double cosVal = Math.cos(rad); // 1 at 180°, -1 at 0°(N)
        double normed = (cosVal + 1.0) / 2.0; // 0..1
        return normed * SUN_ASPECT_BONUS;
    }
}
