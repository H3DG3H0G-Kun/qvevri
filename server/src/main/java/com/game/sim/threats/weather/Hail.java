package com.game.sim.threats.weather;

import com.game.core.data.Fault;
import com.game.core.data.PhenoStage;
import com.game.sim.threats.api.ThreatCategory;
import com.game.sim.threats.api.ThreatContext;
import com.game.sim.threats.api.ThreatEffect;
import com.game.sim.threats.api.ThreatMemory;
import com.game.sim.threats.api.ThreatSource;

/**
 * Hail — sudden mechanical shredding of canopy and berries during ripening.
 *
 * <p>Hail is a probabilistic event: each day during the ripening season there is a
 * base daily probability of a hail cell crossing the plot, amplified by convective
 * conditions (high tMax, significant rain on the same day — hallmark of
 * convective-storm weather). When a hail event fires, yield and quality take a
 * sharp, immediate hit. Netting ({@code ctx.netting}) provides strong mitigation.
 *
 * <h3>Trigger window</h3>
 * Day-of-year 180–290 (approx. late June to mid-October) while the vine is at
 * BERRY_DEVELOPMENT, VERAISON, or RIPENING — when berry clusters are present and
 * mechanical damage translates directly to yield / quality loss.
 *
 * <h3>Event probability (per day)</h3>
 * <pre>
 *   convectiveFactor = clamp((tMax - CONVECTIVE_TEMP_THRESHOLD_C) / CONVECTIVE_TEMP_RANGE, 0, 1)
 *   rainFactor       = clamp(rainMm / HAIL_RAIN_THRESHOLD_MM, 0, 1)
 *   dailyProb        = BASE_HAIL_PROB + convectiveFactor * CONVECTIVE_PROB_BONUS
 *                                     + rainFactor * RAIN_PROB_BONUS
 * </pre>
 * A single uniform draw from {@code ctx.rng} against {@code dailyProb} decides
 * whether a hail cell fires today.
 *
 * <h3>Damage model</h3>
 * <pre>
 *   hailSeverity    = rng.nextDouble()                  // severity of this cell
 *   nettingFactor   = netting ? NETTING_MITIGATION : 1.0
 *   effectiveDamage = hailSeverity * nettingFactor
 *   healthDelta     = -effectiveDamage * HEALTH_DAMAGE_SCALE
 *   yieldMultiplier = max(MIN_YIELD_MULT, 1 - effectiveDamage * YIELD_DAMAGE_SCALE)
 *   qualityPenalty  = effectiveDamage * QUALITY_PENALTY_SCALE
 * </pre>
 *
 * <h3>Memory</h3>
 * {@code level} records cumulative hail damage this season; {@code ticksActive}
 * counts hail days; {@code established} latches once a damaging event occurs.
 */
public final class Hail implements ThreatSource {

    // --- season window (day-of-year, 0-based) ---
    private static final int HAIL_WINDOW_START_DOY       = 180;  // ~late June
    private static final int HAIL_WINDOW_END_DOY         = 290;  // ~mid-October

    // --- daily event probability ---
    /** Base probability of a hail cell on any given day in the window. */
    private static final double BASE_HAIL_PROB            = 0.015;
    /** Temperature threshold above which convective storms become likely (°C). */
    private static final double CONVECTIVE_TEMP_THRESHOLD_C = 28.0;
    /** Temperature range over which convective probability bonus ramps up. */
    private static final double CONVECTIVE_TEMP_RANGE     = 10.0;
    /** Additional daily probability at peak convective conditions. */
    private static final double CONVECTIVE_PROB_BONUS     = 0.06;
    /** Rain threshold above which probability bonus applies (mm). */
    private static final double HAIL_RAIN_THRESHOLD_MM    = 10.0;
    /** Additional daily probability at high rain (convective cell companion). */
    private static final double RAIN_PROB_BONUS           = 0.03;

    // --- damage scaling ---
    /** Health loss per unit effective damage. */
    private static final double HEALTH_DAMAGE_SCALE       = 0.25;
    /** Yield loss per unit effective damage. */
    private static final double YIELD_DAMAGE_SCALE        = 0.90;
    /** Floor for yield multiplier after a catastrophic hail strike. */
    private static final double MIN_YIELD_MULT            = 0.05;
    /** Quality penalty per unit effective damage. */
    private static final double QUALITY_PENALTY_SCALE     = 0.40;
    /** Max quality penalty from a single hail event. */
    private static final double MAX_QUALITY_PENALTY       = 0.45;

    // --- netting mitigation ---
    /**
     * Fraction of damage that passes through netting (0 = perfect, 1 = none).
     * Netting primarily catches hail stones; ~35% of damage can still occur
     * from rebound and canopy bruising.
     */
    private static final double NETTING_PASS_THROUGH      = 0.35;

    // --- susceptible stages (ordinal range check) ---
    private static final PhenoStage SUSCEPTIBLE_FROM      = PhenoStage.BERRY_DEVELOPMENT;
    private static final PhenoStage SUSCEPTIBLE_TO        = PhenoStage.RIPENING;

    @Override
    public String id() {
        return "weather.hail";
    }

    @Override
    public ThreatCategory category() {
        return ThreatCategory.WEATHER;
    }

    @Override
    public ThreatEffect evaluate(ThreatContext ctx) {
        ThreatMemory mem = ctx.memory();

        int doy = ctx.today().dayOfYear();
        if (doy < HAIL_WINDOW_START_DOY || doy > HAIL_WINDOW_END_DOY) {
            return ThreatEffect.none(mem);
        }

        PhenoStage stage = ctx.vine().stage();
        if (!isSusceptibleStage(stage)) {
            return ThreatEffect.none(mem);
        }

        // --- Probability roll ---
        double tMax         = ctx.today().tMaxC();
        double rainMm       = ctx.today().rainMm();

        double convectiveFactor = clamp01(
                (tMax - CONVECTIVE_TEMP_THRESHOLD_C) / CONVECTIVE_TEMP_RANGE);
        double rainFactor       = clamp01(rainMm / HAIL_RAIN_THRESHOLD_MM);

        double dailyProb = BASE_HAIL_PROB
                + convectiveFactor * CONVECTIVE_PROB_BONUS
                + rainFactor * RAIN_PROB_BONUS;

        double eventRoll = ctx.rng().nextDouble();
        if (eventRoll >= dailyProb) {
            return ThreatEffect.none(mem);
        }

        // --- Event fires: draw severity ---
        double hailSeverity = ctx.rng().nextDouble();   // 0..1, uniform

        double nettingFactor    = ctx.netting() ? NETTING_PASS_THROUGH : 1.0;
        double effectiveDamage  = hailSeverity * nettingFactor;

        double healthDelta      = -(effectiveDamage * HEALTH_DAMAGE_SCALE);
        double yieldMultiplier  = Math.max(MIN_YIELD_MULT,
                                           1.0 - effectiveDamage * YIELD_DAMAGE_SCALE);
        double qualityPenalty   = Math.min(MAX_QUALITY_PENALTY,
                                           effectiveDamage * QUALITY_PENALTY_SCALE);

        // Memory: level = cumulative damage, aux = this event's severity
        double newLevel    = Math.min(1.0, mem.level() + effectiveDamage);
        int newTicks       = mem.ticksActive() + 1;
        boolean established = true;
        ThreatMemory nextMem = new ThreatMemory(newLevel, hailSeverity, newTicks,
                                                mem.yearsActive(), established);

        String tell = buildTell(hailSeverity, ctx.netting(), stage);

        return new ThreatEffect(
                healthDelta,
                yieldMultiplier,
                qualityPenalty,
                Fault.NONE,
                false,
                tell,
                nextMem
        );
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static boolean isSusceptibleStage(PhenoStage stage) {
        int ord = stage.ordinal();
        return ord >= SUSCEPTIBLE_FROM.ordinal() && ord <= SUSCEPTIBLE_TO.ordinal();
    }

    private static double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    private static String buildTell(double severity, boolean netted, PhenoStage stage) {
        String stageDesc = (stage == PhenoStage.VERAISON || stage == PhenoStage.RIPENING)
                ? "ripe clusters" : "developing berries";
        if (netted) {
            if (severity > 0.6) {
                return "heavy hail storm — netting reduced the damage but " + stageDesc
                       + " still shredded";
            }
            return "hail event — netting protected most of the crop; minor shredding on "
                   + stageDesc;
        }
        if (severity > 0.7) {
            return "violent hail storm shredded the " + stageDesc
                   + " — catastrophic yield and quality loss";
        }
        if (severity > 0.35) {
            return "hail storm shredded the " + stageDesc
                   + " — sharp yield and quality hit";
        }
        return "light hail event — minor bruising on " + stageDesc;
    }
}
