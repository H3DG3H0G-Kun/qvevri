package com.game.sim.threats.weather;

import com.game.core.data.Fault;
import com.game.core.data.PhenoStage;
import com.game.sim.threats.api.ThreatCategory;
import com.game.sim.threats.api.ThreatContext;
import com.game.sim.threats.api.ThreatEffect;
import com.game.sim.threats.api.ThreatMemory;
import com.game.sim.threats.api.ThreatSource;

/**
 * Flood — heavy rainfall on flat or low-lying sites causes waterlogging and
 * root zone anoxia, plus erosion on sloped sites with poor drainage.
 *
 * <p>Unlike the sharp, single-day hail event, flooding is a cumulative process:
 * heavy rain days push soil saturation up; between rain events the soil slowly
 * drains. Damage occurs when soil saturation exceeds a critical threshold and the
 * root zone becomes anaerobic (roots drown). Flat valley-floor sites with high
 * waterProximity are most vulnerable.
 *
 * <h3>Saturation model</h3>
 * <pre>
 *   saturation += rainMm / SATURATION_RAIN_SCALE     (each day)
 *   saturation -= DRAIN_RATE_PER_DAY                 (drainage)
 *   saturation  = clamp(saturation, 0, 1)
 *   floodRisk   = saturation * site.waterProximity * flatnessFactor
 * </pre>
 * where {@code flatnessFactor = max(0, 1 - site.slopeDeg / MAX_SLOPE_FOR_FLOOD)}
 * (steep slopes drain better / floods run off rather than pool).
 *
 * <h3>Damage</h3>
 * Damage fires when {@code floodRisk > FLOOD_DAMAGE_THRESHOLD} and the vine is
 * in a season-active stage. Root suffocation and erosion reduce health and yield.
 *
 * <h3>Memory</h3>
 * {@code level} = soil saturation (0..1);
 * {@code aux} = days of waterlogging above threshold this season;
 * {@code ticksActive} = total waterlogged days.
 */
public final class Flood implements ThreatSource {

    // --- rain / saturation model ---
    /** Rain in mm that increases saturation by 0.1 (i.e. the scale divisor). */
    private static final double SATURATION_RAIN_SCALE     = 20.0;
    /** Saturation drained per day (approx. 1/10 drained per day on open soil). */
    private static final double DRAIN_RATE_PER_DAY        = 0.08;
    /** Saturation threshold above which flood damage begins. */
    private static final double FLOOD_DAMAGE_THRESHOLD    = 0.50;

    // --- site geometry ---
    /** Slopes steeper than this drain too well to pool (degrees). */
    private static final double MAX_SLOPE_FOR_FLOOD       = 20.0;

    // --- damage scaling ---
    /** Health loss per unit of flood risk above the damage threshold (daily). */
    private static final double HEALTH_DAMAGE_SCALE       = 0.08;
    /** Maximum health loss per flooded day. */
    private static final double MAX_DAILY_HEALTH_HIT      = 0.10;
    /** Yield penalty per unit flood risk above threshold. */
    private static final double YIELD_DAMAGE_SCALE        = 0.12;
    /** Minimum yield multiplier after prolonged flooding. */
    private static final double MIN_YIELD_MULT            = 0.45;
    /** Quality penalty per unit flood risk above threshold. */
    private static final double QUALITY_PENALTY_SCALE     = 0.15;
    /** Maximum quality penalty per flooded day. */
    private static final double MAX_QUALITY_PENALTY       = 0.15;

    // --- phenological window ---
    private static final PhenoStage ACTIVE_FROM = PhenoStage.BUDBREAK;
    private static final PhenoStage ACTIVE_TO   = PhenoStage.RIPENING;

    @Override
    public String id() {
        return "weather.flood";
    }

    @Override
    public ThreatCategory category() {
        return ThreatCategory.WEATHER;
    }

    @Override
    public ThreatEffect evaluate(ThreatContext ctx) {
        ThreatMemory mem = ctx.memory();

        // Update soil saturation regardless of phenological stage
        double rainMm      = ctx.today().rainMm();
        double saturation  = mem.level();
        saturation += rainMm / SATURATION_RAIN_SCALE;
        saturation -= DRAIN_RATE_PER_DAY;
        saturation  = Math.max(0.0, Math.min(1.0, saturation));

        // Site flood susceptibility
        double slopeDeg    = ctx.site().slopeDeg();
        double flatness    = Math.max(0.0, 1.0 - slopeDeg / MAX_SLOPE_FOR_FLOOD);
        double waterProx   = ctx.site().waterProximity();
        double floodRisk   = saturation * waterProx * flatness;

        // No active vine stage — update saturation memory only, no damage
        PhenoStage stage = ctx.vine().stage();
        if (!isActiveStage(stage) || floodRisk <= FLOOD_DAMAGE_THRESHOLD) {
            double waterlogDays  = mem.aux();
            // still count if saturated even if vine not yet active
            ThreatMemory nextMem = new ThreatMemory(saturation, waterlogDays,
                                                    mem.ticksActive(), mem.yearsActive(), false);
            return ThreatEffect.none(nextMem);
        }

        // --- Flood damage fires ---
        double excess        = floodRisk - FLOOD_DAMAGE_THRESHOLD;
        double healthDelta   = -Math.min(MAX_DAILY_HEALTH_HIT, excess * HEALTH_DAMAGE_SCALE);
        double yieldMult     = Math.max(MIN_YIELD_MULT, 1.0 - excess * YIELD_DAMAGE_SCALE);
        double qualityPen    = Math.min(MAX_QUALITY_PENALTY, excess * QUALITY_PENALTY_SCALE);

        double waterlogDays  = mem.aux() + 1.0;
        int ticksActive      = mem.ticksActive() + 1;
        boolean established  = mem.established() || waterlogDays >= 2.0;
        ThreatMemory nextMem = new ThreatMemory(saturation, waterlogDays,
                                                ticksActive, mem.yearsActive(), established);

        String tell = buildTell(rainMm, floodRisk, waterlogDays, slopeDeg);

        return new ThreatEffect(
                healthDelta,
                yieldMult,
                qualityPen,
                Fault.NONE,
                false,
                tell,
                nextMem
        );
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static boolean isActiveStage(PhenoStage stage) {
        int ord = stage.ordinal();
        return ord >= ACTIVE_FROM.ordinal() && ord <= ACTIVE_TO.ordinal();
    }

    private static String buildTell(double rainMm, double floodRisk,
                                    double waterlogDays, double slopeDeg) {
        if (waterlogDays >= 5.0) {
            return String.format(
                    "prolonged waterlogging (%.0f days) — roots suffocating, vine health declining rapidly",
                    waterlogDays);
        }
        if (slopeDeg < 5.0 && rainMm > 30.0) {
            return String.format(
                    "heavy rain (%.0f mm) on flat ground — root zone flooding and topsoil erosion",
                    rainMm);
        }
        return String.format(
                "waterlogged soil (flood risk %.0f%%) — root zone anoxia reducing vine vigour",
                floodRisk * 100.0);
    }
}
