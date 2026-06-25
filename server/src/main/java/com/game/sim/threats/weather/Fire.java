package com.game.sim.threats.weather;

import com.game.core.data.Fault;
import com.game.core.data.PhenoStage;
import com.game.sim.threats.api.ThreatCategory;
import com.game.sim.threats.api.ThreatContext;
import com.game.sim.threats.api.ThreatEffect;
import com.game.sim.threats.api.ThreatMemory;
import com.game.sim.threats.api.ThreatSource;

/**
 * Wildfire / vineyard fire — a rare, catastrophic event.
 *
 * <p>In the Kakheti context, fire risk is highest during hot-dry summer and early
 * autumn (late ripening season) when the landscape dries out after a drought
 * period. A fire event represents either a direct vineyard burn (catastrophic
 * structural damage) or a near-miss smoke-taint event — smoke settling over
 * ripening clusters during the 3–4 week window before harvest is the more common
 * hazard in Georgian viticulture.
 *
 * <h3>Fire risk model</h3>
 * <pre>
 *   dryStreak       = memory.aux (consecutive dry days from DroughtHeatwave or local calc)
 *   drynessFactor   = clamp(dryStreak / DROUGHT_STREAK_FOR_MAX_RISK, 0, 1)
 *   heatFactor      = clamp((tMax - FIRE_HEAT_THRESHOLD_C) / FIRE_HEAT_RANGE, 0, 1)
 *   dailyFireProb   = BASE_FIRE_PROB * (1 + drynessFactor * DRY_PROB_MULTIPLIER)
 *                                    * (1 + heatFactor   * HEAT_PROB_MULTIPLIER)
 * </pre>
 * One RNG draw fires the event; if it fires a second draw chooses direct-burn vs
 * smoke-taint (weighted toward smoke taint — the more likely outcome).
 *
 * <h3>Damage</h3>
 * <ul>
 *   <li><b>Direct burn:</b> severe health hit, major yield loss, quality penalty.
 *       Can kill the vine if health falls below zero.</li>
 *   <li><b>Smoke taint:</b> moderate quality penalty + induced fault {@code OXIDATION}
 *       (used as the closest available Fault to smoke-taint; noted as proxy in spec).
 *       Yield is mostly unaffected (berries intact, chemistry compromised).</li>
 * </ul>
 *
 * <h3>Memory</h3>
 * {@code level} = cumulative fire damage this season;
 * {@code aux} = local dry-day streak (independent of DroughtHeatwave);
 * {@code ticksActive} = fire/smoke event count;
 * {@code established} = true once a fire event has occurred.
 */
public final class Fire implements ThreatSource {

    // --- temperature / dryness for fire risk ---
    /** Temperature above which fire risk starts ramping (°C). */
    private static final double FIRE_HEAT_THRESHOLD_C     = 33.0;
    /** Temperature range over which the heat multiplier reaches maximum. */
    private static final double FIRE_HEAT_RANGE           = 10.0;
    /** Consecutive dry-day streak needed to reach maximum dryness factor. */
    private static final double DROUGHT_STREAK_FOR_MAX_RISK = 21.0;
    /** Rain below this counts as a dry day for local fire-risk streak (mm). */
    private static final double DRY_DAY_RAIN_THRESHOLD_MM = 5.0;

    // --- base probability and multipliers ---
    /** Base daily fire probability (very low — fire is rare). */
    private static final double BASE_FIRE_PROB            = 0.002;
    /** Multiplier on base prob contributed by max dryness. */
    private static final double DRY_PROB_MULTIPLIER       = 8.0;
    /** Multiplier on base prob contributed by max heat. */
    private static final double HEAT_PROB_MULTIPLIER      = 5.0;

    // --- event type split ---
    /**
     * Probability threshold for a direct burn vs smoke taint, given that a fire
     * event fires. Below this = direct burn; above = smoke taint.
     * Smoke taint is ~80% of fire events in the model (more common hazard).
     */
    private static final double DIRECT_BURN_PROB_THRESHOLD = 0.20;

    // --- direct burn damage ---
    private static final double BURN_HEALTH_HIT           = 0.55;
    private static final double BURN_YIELD_MULT           = 0.20;
    private static final double BURN_QUALITY_PENALTY      = 0.60;

    // --- smoke taint damage ---
    private static final double SMOKE_HEALTH_HIT          = 0.05;
    private static final double SMOKE_YIELD_MULT          = 0.85;  // slight berry desiccation
    private static final double SMOKE_QUALITY_PENALTY     = 0.45;  // smoke taint is a serious fault

    // --- phenological window for smoke taint (ripening season only) ---
    private static final PhenoStage SMOKE_WINDOW_FROM     = PhenoStage.BERRY_DEVELOPMENT;
    private static final PhenoStage SMOKE_WINDOW_TO       = PhenoStage.RIPENING;
    /** Outside this window, fire can still cause direct burn but smoke taint is negligible. */
    private static final double SMOKE_OUTSIDE_WINDOW_PENALTY_SCALE = 0.20;

    @Override
    public String id() {
        return "weather.fire";
    }

    @Override
    public ThreatCategory category() {
        return ThreatCategory.WEATHER;
    }

    @Override
    public ThreatEffect evaluate(ThreatContext ctx) {
        ThreatMemory mem = ctx.memory();

        // Maintain local dry-streak counter
        double rainMm     = ctx.today().rainMm();
        double dryStreak  = rainMm < DRY_DAY_RAIN_THRESHOLD_MM
                ? mem.aux() + 1.0
                : Math.max(0.0, mem.aux() - 2.0);  // rain knocks streak down

        // --- Fire risk probability ---
        double tMax = ctx.today().tMaxC();
        double drynessFactor = clamp01(dryStreak / DROUGHT_STREAK_FOR_MAX_RISK);
        double heatFactor    = clamp01((tMax - FIRE_HEAT_THRESHOLD_C) / FIRE_HEAT_RANGE);

        double dailyFireProb = BASE_FIRE_PROB
                * (1.0 + drynessFactor * DRY_PROB_MULTIPLIER)
                * (1.0 + heatFactor   * HEAT_PROB_MULTIPLIER);

        double eventRoll = ctx.rng().nextDouble();
        if (eventRoll >= dailyFireProb) {
            // No fire event today — update dry-streak memory and return no-op
            ThreatMemory nextMem = new ThreatMemory(mem.level(), dryStreak,
                                                    mem.ticksActive(), mem.yearsActive(),
                                                    mem.established());
            return ThreatEffect.none(nextMem);
        }

        // --- Fire event fires — determine type ---
        double typeRoll = ctx.rng().nextDouble();
        boolean isDirectBurn = typeRoll < DIRECT_BURN_PROB_THRESHOLD;

        PhenoStage stage        = ctx.vine().stage();
        boolean inSmokeWindow   = isSmokeWindow(stage);

        double healthDelta;
        double yieldMultiplier;
        double qualityPenalty;
        Fault inducedFault;
        boolean killVine = false;
        String tell;

        if (isDirectBurn) {
            healthDelta    = -BURN_HEALTH_HIT;
            yieldMultiplier = BURN_YIELD_MULT;
            qualityPenalty = BURN_QUALITY_PENALTY;
            inducedFault   = Fault.OXIDATION;  // fire oxidation / smoke combined
            // kill vine if health would go to zero
            killVine = (ctx.vine().healthFraction() + healthDelta) <= 0.0;
            tell = String.format(
                    "wildfire crossed the vineyard — severe burn damage to vine structure and fruit"
                    + " (tMax %.1f°C, %.0f dry days)",
                    tMax, dryStreak);
        } else {
            // Smoke taint — severity depends on whether berries are present
            double smokeSeverity = inSmokeWindow ? 1.0 : SMOKE_OUTSIDE_WINDOW_PENALTY_SCALE;
            healthDelta    = -SMOKE_HEALTH_HIT * smokeSeverity;
            yieldMultiplier = inSmokeWindow
                    ? SMOKE_YIELD_MULT
                    : Math.max(SMOKE_YIELD_MULT, 0.95);
            qualityPenalty = SMOKE_QUALITY_PENALTY * smokeSeverity;
            // OXIDATION is the closest available Fault to smoke taint (spec §5)
            inducedFault   = inSmokeWindow ? Fault.OXIDATION : Fault.NONE;
            tell = inSmokeWindow
                    ? String.format(
                            "wildfire smoke settled over ripening clusters — smoke taint risk"
                            + " (%.0f dry days, tMax %.1f°C)",
                            dryStreak, tMax)
                    : String.format(
                            "distant wildfire smoke — minimal taint risk outside ripening window"
                            + " (tMax %.1f°C)",
                            tMax);
        }

        double newLevel  = Math.min(1.0, mem.level() + Math.abs(healthDelta));
        int newTicks     = mem.ticksActive() + 1;
        ThreatMemory nextMem = new ThreatMemory(newLevel, dryStreak, newTicks,
                                                mem.yearsActive(), true);

        return new ThreatEffect(
                healthDelta,
                yieldMultiplier,
                qualityPenalty,
                inducedFault,
                killVine,
                tell,
                nextMem
        );
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static boolean isSmokeWindow(PhenoStage stage) {
        int ord = stage.ordinal();
        return ord >= SMOKE_WINDOW_FROM.ordinal() && ord <= SMOKE_WINDOW_TO.ordinal();
    }

    private static double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }
}
