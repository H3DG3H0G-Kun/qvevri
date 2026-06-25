package com.game.sim.threats.weather;

import com.game.core.data.Fault;
import com.game.core.data.PhenoStage;
import com.game.sim.threats.api.ThreatCategory;
import com.game.sim.threats.api.ThreatContext;
import com.game.sim.threats.api.ThreatEffect;
import com.game.sim.threats.api.ThreatMemory;
import com.game.sim.threats.api.ThreatSource;

/**
 * Spring frost — the classic "year-killer" for grapevines.
 *
 * <p>Tender shoot tissue after budbreak is killed at temperatures below a
 * critical threshold. Damage scales with frost severity (how far tMin falls
 * below the kill threshold) and the vine's frost-exposure risk.  A well-drained
 * hillside site with good cold-air drainage ({@code site.frostRisk} low) suffers
 * far less than a valley-floor site.
 *
 * <h3>Trigger window</h3>
 * Day-of-year 60–150 (approx. early March to end of May in Kakheti) while the
 * vine is at BUDBREAK, SHOOT_GROWTH, or FLOWERING — the stages at which tissue
 * is most tender and susceptible.
 *
 * <h3>Damage model</h3>
 * <pre>
 *   frostIntensity = max(0, SHOOT_KILL_TEMP_C - tMin)   // degrees below kill temp
 *   siteExposure   = site.frostRisk                     // 0 (safe slope) .. 1 (valley floor)
 *   rawDamage      = frostIntensity * INTENSITY_SCALE * siteExposure
 *   healthDelta    = -clamp(rawDamage, 0, MAX_HEALTH_HIT)
 *   yieldMultiplier = max(MIN_YIELD_MULT, 1 - rawDamage * YIELD_DAMAGE_SCALE)
 * </pre>
 *
 * <h3>Counter</h3>
 * {@code site.frostRisk} (slope / cold-air drainage) is the primary counter — a
 * site with frostRisk=0.1 takes ~10% of the damage of a site with frostRisk=1.0.
 * Late pruning (not modelled as a direct lever in ThreatContext yet) is noted in
 * the spec but has no field; the spec mentions "a proxy for smudge" — not present
 * in ThreatContext, so only site.frostRisk is used.
 *
 * <h3>Memory</h3>
 * {@code level} accumulates the season's total frost damage delivered so that the
 * day-log can surface cumulative severity. {@code ticksActive} counts frost days.
 */
public final class SpringFrost implements ThreatSource {

    // --- season window (day-of-year, 0-based) ---
    private static final int FROST_WINDOW_START_DOY  = 60;   // ~1 March
    private static final int FROST_WINDOW_END_DOY    = 150;  // ~31 May

    // --- temperature thresholds ---
    /** Tissue kill temperature for tender post-budbreak shoots (°C). */
    private static final double SHOOT_KILL_TEMP_C    = -1.0;
    /** Below this tMin we consider a hard frost regardless of kill threshold. */
    private static final double HARD_FROST_TEMP_C    = -3.0;

    // --- damage scaling ---
    /** Maps each degree below kill temp to a raw damage fraction. */
    private static final double INTENSITY_SCALE      = 0.18;
    /** Maximum health hit from a single severe frost day. */
    private static final double MAX_HEALTH_HIT       = 0.35;
    /** Yield damage multiplier per unit raw damage. */
    private static final double YIELD_DAMAGE_SCALE   = 1.2;
    /** Floor for the yield multiplier in a catastrophic frost. */
    private static final double MIN_YIELD_MULT       = 0.10;

    // --- phenological stages susceptible to frost ---
    // Using ordinal comparison for ordered enum check.
    private static final PhenoStage SUSCEPTIBLE_FROM = PhenoStage.BUDBREAK;
    private static final PhenoStage SUSCEPTIBLE_TO   = PhenoStage.FLOWERING;

    @Override
    public String id() {
        return "weather.spring_frost";
    }

    @Override
    public ThreatCategory category() {
        return ThreatCategory.WEATHER;
    }

    @Override
    public ThreatEffect evaluate(ThreatContext ctx) {
        ThreatMemory mem = ctx.memory();

        // Only active during the spring frost window
        int doy = ctx.today().dayOfYear();
        if (doy < FROST_WINDOW_START_DOY || doy > FROST_WINDOW_END_DOY) {
            return ThreatEffect.none(mem);
        }

        // Only damaging when the vine has tender tissue (post-budbreak, pre-fruit-set)
        PhenoStage stage = ctx.vine().stage();
        if (!isSusceptibleStage(stage)) {
            return ThreatEffect.none(mem);
        }

        double tMin = ctx.today().tMinC();

        // No frost if temperature is above the kill threshold
        if (tMin >= SHOOT_KILL_TEMP_C) {
            return ThreatEffect.none(mem);
        }

        // Frost intensity = degrees below the tissue-kill threshold
        double frostIntensity = SHOOT_KILL_TEMP_C - tMin;  // > 0

        // Site exposure: valley floor sites (frostRisk=1) take full damage;
        // well-drained hillside (frostRisk near 0) is mostly protected.
        double siteExposure = ctx.site().frostRisk();

        double rawDamage = frostIntensity * INTENSITY_SCALE * siteExposure;

        double healthDelta     = -Math.min(rawDamage, MAX_HEALTH_HIT);
        double yieldMultiplier = Math.max(MIN_YIELD_MULT, 1.0 - rawDamage * YIELD_DAMAGE_SCALE);

        // Accumulate season frost damage in memory.level; count frost days
        double newLevel      = Math.min(1.0, mem.level() + rawDamage);
        int    newTicks      = mem.ticksActive() + 1;
        boolean established  = mem.established() || rawDamage > 0.05;
        ThreatMemory nextMem = new ThreatMemory(newLevel, frostIntensity, newTicks,
                                                mem.yearsActive(), established);

        String tell = buildTell(tMin, frostIntensity, rawDamage);

        return new ThreatEffect(
                healthDelta,
                yieldMultiplier,
                Math.min(0.30, rawDamage * 0.6),  // quality penalty proportional to damage
                Fault.NONE,
                false,
                tell,
                nextMem
        );
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** True when the vine is in a frost-susceptible phenological stage. */
    private static boolean isSusceptibleStage(PhenoStage stage) {
        int ord = stage.ordinal();
        return ord >= SUSCEPTIBLE_FROM.ordinal() && ord <= SUSCEPTIBLE_TO.ordinal();
    }

    private static String buildTell(double tMin, double frostIntensity, double rawDamage) {
        if (tMin <= HARD_FROST_TEMP_C) {
            return String.format(
                    "hard spring frost (%.1f°C) burned the shoots — severe bud and tissue loss",
                    tMin);
        }
        if (rawDamage >= 0.15) {
            return String.format(
                    "spring frost (%.1f°C) damaged tender shoot tissue — significant yield loss expected",
                    tMin);
        }
        return String.format(
                "light spring frost (%.1f°C) singed shoot tips — minor damage",
                tMin);
    }
}
