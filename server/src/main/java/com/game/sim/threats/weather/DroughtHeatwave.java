package com.game.sim.threats.weather;

import com.game.core.data.Fault;
import com.game.core.data.PhenoStage;
import com.game.sim.threats.api.ThreatCategory;
import com.game.sim.threats.api.ThreatContext;
import com.game.sim.threats.api.ThreatEffect;
import com.game.sim.threats.api.ThreatMemory;
import com.game.sim.threats.api.ThreatSource;

/**
 * Drought + Heatwave — sustained heat with low rainfall causes vine shutdown,
 * leaf scorch, and berry sunburn.
 *
 * <p>In Kakheti the combination of prolonged tMax above a critical threshold and
 * low cumulative rainfall is the primary summer stress. The vine enters temporary
 * shutdown (stomata close) on individual extreme days, and chronic drought
 * accumulates soil moisture deficit over consecutive dry-hot days.
 *
 * <h3>Mechanism</h3>
 * <ol>
 *   <li><b>Daily stress:</b> any day where tMax exceeds {@code HEAT_TRIGGER_TEMP_C}
 *       AND rainMm is below {@code LOW_RAIN_MM} adds to an accumulating stress
 *       level in memory.</li>
 *   <li><b>Acute shutdown:</b> if tMax exceeds {@code SHUTDOWN_TEMP_C} the vine
 *       loses a fixed health fraction that day (sunburn / leaf scorch).</li>
 *   <li><b>Chronic damage:</b> when cumulative stress level ({@code memory.level})
 *       exceeds {@code STRESS_DAMAGE_THRESHOLD}, daily health and yield damage
 *       scales with the excess stress level.</li>
 * </ol>
 *
 * <h3>Counters</h3>
 * <ul>
 *   <li>{@code canopyOpenness01}: a more closed canopy (lower value) provides
 *       shade to berries, reducing sunburn damage.</li>
 *   <li>{@code coverCrop01}: cover crops improve soil water-holding and reduce
 *       temperature at the soil surface, slowing stress accumulation.</li>
 * </ul>
 *
 * <h3>Memory</h3>
 * {@code level} = cumulative drought-heat stress (0..1, decays slowly with rain);
 * {@code aux} = consecutive hot-dry days (resets on a cool/wet day);
 * {@code ticksActive} = total hot-dry days this season.
 */
public final class DroughtHeatwave implements ThreatSource {

    // --- temperature thresholds ---
    /** Daily tMax above which stress accumulates (°C). */
    private static final double HEAT_TRIGGER_TEMP_C      = 32.0;
    /** Daily tMax above which acute vine shutdown occurs (°C). */
    private static final double SHUTDOWN_TEMP_C          = 38.0;

    // --- rainfall thresholds ---
    /** Rain below this daily amount counts as "dry" for stress accumulation (mm). */
    private static final double LOW_RAIN_MM              = 3.0;
    /** Rain above this amount resets the consecutive dry-day streak (mm). */
    private static final double RELIEF_RAIN_MM           = 15.0;

    // --- stress accumulation / decay ---
    /** Stress added per hot-dry day as a fraction of max stress. */
    private static final double STRESS_PER_HOT_DRY_DAY  = 0.06;
    /** Stress decay per cool or wet day (partial recovery). */
    private static final double STRESS_DECAY_PER_DAY    = 0.03;
    /** Cover crop reduces stress accumulation by up to this fraction. */
    private static final double COVER_CROP_STRESS_REDUCTION = 0.40;

    // --- damage thresholds / scaling ---
    /** Stress level must exceed this before chronic damage begins. */
    private static final double STRESS_DAMAGE_THRESHOLD  = 0.20;
    /** Health lost per unit of stress above the threshold (chronic damage). */
    private static final double CHRONIC_HEALTH_SCALE     = 0.04;
    /** Health lost on an acute shutdown day. */
    private static final double SHUTDOWN_HEALTH_HIT      = 0.06;
    /** Sunburn damage is reduced by shade from a closed canopy. */
    private static final double CANOPY_SHADE_REDUCTION   = 0.50;
    /** Maximum chronic health delta per day. */
    private static final double MAX_CHRONIC_HEALTH_HIT   = 0.12;
    /** Yield multiplier penalty per unit stress above threshold (chronic). */
    private static final double CHRONIC_YIELD_SCALE      = 0.15;
    /** Minimum yield multiplier after severe drought. */
    private static final double MIN_YIELD_MULT           = 0.40;
    /** Quality penalty proportional to chronic stress. */
    private static final double QUALITY_PENALTY_SCALE    = 0.25;
    /** Max quality penalty from drought in a single day. */
    private static final double MAX_QUALITY_PENALTY      = 0.20;

    // --- phenological window: only worry from shoot growth through harvest ---
    private static final PhenoStage ACTIVE_FROM = PhenoStage.SHOOT_GROWTH;
    private static final PhenoStage ACTIVE_TO   = PhenoStage.RIPENING;

    @Override
    public String id() {
        return "weather.drought_heatwave";
    }

    @Override
    public ThreatCategory category() {
        return ThreatCategory.WEATHER;
    }

    @Override
    public ThreatEffect evaluate(ThreatContext ctx) {
        ThreatMemory mem = ctx.memory();

        PhenoStage stage = ctx.vine().stage();
        if (!isActiveStage(stage)) {
            return ThreatEffect.none(resetStressIfDormant(mem));
        }

        double tMax   = ctx.today().tMaxC();
        double rainMm = ctx.today().rainMm();

        boolean isHotDay = tMax > HEAT_TRIGGER_TEMP_C;
        boolean isDryDay = rainMm < LOW_RAIN_MM;
        boolean isRelief = rainMm >= RELIEF_RAIN_MM;
        boolean isShutdown = tMax > SHUTDOWN_TEMP_C;

        // --- Update stress level in memory ---
        double stressLevel   = mem.level();
        double consecutiveDry = mem.aux();
        int ticksActive      = mem.ticksActive();

        double coverCropProtection = ctx.coverCrop01() * COVER_CROP_STRESS_REDUCTION;

        if (isHotDay && isDryDay) {
            double stressAdd = STRESS_PER_HOT_DRY_DAY * (1.0 - coverCropProtection);
            stressLevel   = Math.min(1.0, stressLevel + stressAdd);
            consecutiveDry = consecutiveDry + 1.0;
            ticksActive   += 1;
        } else {
            // Cool or wet day: stress decays
            double decay = isRelief
                    ? STRESS_DECAY_PER_DAY * 3.0   // heavy rain = faster recovery
                    : STRESS_DECAY_PER_DAY;
            stressLevel    = Math.max(0.0, stressLevel - decay);
            consecutiveDry = isRelief ? 0.0 : Math.max(0.0, consecutiveDry - 1.0);
        }

        // If no significant stress and not a shutdown day, return no-op
        if (stressLevel < STRESS_DAMAGE_THRESHOLD && !isShutdown) {
            ThreatMemory nextMem = new ThreatMemory(stressLevel, consecutiveDry,
                                                    ticksActive, mem.yearsActive(), false);
            return ThreatEffect.none(nextMem);
        }

        // --- Calculate damage ---
        double healthDelta     = 0.0;
        double yieldMultiplier = 1.0;
        double qualityPenalty  = 0.0;
        String tell            = "";

        // Acute shutdown damage (sunburn / leaf scorch)
        if (isShutdown) {
            // Closed canopy provides shade
            double shadeProtection = (1.0 - ctx.canopyOpenness01()) * CANOPY_SHADE_REDUCTION;
            double shutdownDamage  = SHUTDOWN_HEALTH_HIT * (1.0 - shadeProtection);
            healthDelta           -= shutdownDamage;
            qualityPenalty        += shutdownDamage * 0.4;
            tell = String.format(
                    "extreme heat (%.1f°C) — vines shut down, berries scorching in the sun",
                    tMax);
        }

        // Chronic drought damage (stress above threshold)
        if (stressLevel > STRESS_DAMAGE_THRESHOLD) {
            double excess        = stressLevel - STRESS_DAMAGE_THRESHOLD;
            double chronicHealth = Math.min(MAX_CHRONIC_HEALTH_HIT,
                                            excess * CHRONIC_HEALTH_SCALE);
            double chronicYield  = excess * CHRONIC_YIELD_SCALE;

            healthDelta    -= chronicHealth;
            yieldMultiplier = Math.max(MIN_YIELD_MULT, 1.0 - chronicYield);
            qualityPenalty += Math.min(MAX_QUALITY_PENALTY,
                                       excess * QUALITY_PENALTY_SCALE);

            if (tell.isEmpty()) {
                tell = String.format(
                        "prolonged drought stress (%.0f consecutive dry days) — vine shutdown and yield shrinkage",
                        consecutiveDry);
            }
        }

        boolean established = mem.established() || stressLevel > STRESS_DAMAGE_THRESHOLD;
        ThreatMemory nextMem = new ThreatMemory(stressLevel, consecutiveDry,
                                                ticksActive, mem.yearsActive(), established);

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

    private static boolean isActiveStage(PhenoStage stage) {
        int ord = stage.ordinal();
        return ord >= ACTIVE_FROM.ordinal() && ord <= ACTIVE_TO.ordinal();
    }

    /** If the vine goes dormant, clear the transient stress level. */
    private static ThreatMemory resetStressIfDormant(ThreatMemory mem) {
        if (mem.level() == 0.0 && mem.aux() == 0.0) return mem;
        return new ThreatMemory(0.0, 0.0, mem.ticksActive(), mem.yearsActive(), false);
    }
}
