package com.game.sim;

import com.game.core.data.*;
import com.game.core.time.RngStreams;
import com.game.core.weather.WeatherModel;
import com.game.sim.soil.SiteSuitability;
import com.game.sim.vine.VineSimulator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * §5 Acceptance test 4 — Vintage / weather effects.
 *
 * <p>Verifies that the weather model and vine simulator correctly encode the
 * vintage effect: warm years allow faster Brix accumulation than cool years,
 * and cool years retain higher TA at a fixed calendar day.
 *
 * <h2>Seed selection strategy</h2>
 * Rather than assuming that specific seed numbers produce warm or cool vintages
 * (which depends on the WeatherModel implementation and would break if the model
 * changes), these tests measure the actual GDD produced by each candidate seed
 * and select the highest-GDD seed as the WARM vintage and the lowest-GDD seed
 * as the COOL vintage.
 *
 * <p>{@link #selectWarmAndCoolSeeds(WeatherModel)} scans {@link #CANDIDATE_SEEDS},
 * generates a full year + rollVintage for each, and returns a two-element array
 * {@code [warmSeed, coolSeed]} based on measured GDD.
 *
 * <p>This approach keeps the directional assertions (warm ripens faster; cool
 * keeps more acid) meaningful without hardcoding which seed number is which.
 */
@DisplayName("§5.4 Vintage/weather: warm seed ripens faster; cool seed retains higher TA")
class VintageWeatherTest {

    /**
     * Candidate seeds to scan when selecting warm vs. cool vintages.
     * A set of 12 gives sufficient GDD spread while keeping test setup fast.
     * Seeds are stable values — adding more does not break existing assertions.
     */
    private static final long[] CANDIDATE_SEEDS = { 1L, 7L, 13L, 17L, 23L, 42L, 57L, 77L, 99L, 123L, 200L, 314L };

    private static final int BUD_LOAD = 12;

    /**
     * The target Brix level used to compare how many days each vintage takes
     * to reach it.  20°Bx is a reasonable mid-ripening milestone for Saperavi
     * (véraison typically starts ~18-19°Bx, harvest window at 22-25°Bx).
     */
    private static final double TARGET_BRIX = 20.0;

    /**
     * Fixed calendar day used for the TA comparison assertion.
     * Day 260 ≈ mid-September; both warm and cool vintages should have
     * non-trivial Brix at this point but the cool one should retain more acid.
     */
    private static final int FIXED_DAY_FOR_TA = 260;

    // -------------------------------------------------------------------------
    // Helper: measure GDD for each candidate seed; return [warmSeed, coolSeed]
    // -------------------------------------------------------------------------

    /**
     * Scans {@link #CANDIDATE_SEEDS}, generates a full weather year and rolls the
     * vintage for each, then returns a two-element array {@code [warmSeed, coolSeed]}
     * where {@code warmSeed} produced the highest seasonal GDD and {@code coolSeed}
     * produced the lowest.
     *
     * <p>This is deterministic: the same WeatherModel implementation always maps
     * each seed to the same GDD, so the chosen warm/cool seeds are stable across
     * test runs while remaining immune to changes in which specific number happens
     * to produce a warm year.
     */
    private static long[] selectWarmAndCoolSeeds(WeatherModel weather) {
        long warmSeed = CANDIDATE_SEEDS[0];
        long coolSeed = CANDIDATE_SEEDS[0];
        double maxGdd = Double.NEGATIVE_INFINITY;
        double minGdd = Double.POSITIVE_INFINITY;

        for (long seed : CANDIDATE_SEEDS) {
            RngStreams rng = new RngStreams(seed);
            List<DailyWeather> days = weather.generateYear(rng, Region.KAKHETI, 1);
            Vintage vintage = weather.rollVintage(rng, Region.KAKHETI, 1, days);
            double gdd = vintage.gddSeason();
            if (gdd > maxGdd) { maxGdd = gdd; warmSeed = seed; }
            if (gdd < minGdd) { minGdd = gdd; coolSeed = seed; }
        }

        return new long[]{ warmSeed, coolSeed };
    }

    // -------------------------------------------------------------------------
    // Helper: find the first day a vine reaches a target Brix level
    // -------------------------------------------------------------------------

    /**
     * Returns the first day-of-year on which the vine's Brix equals or exceeds
     * {@code targetBrix}, or {@code Integer.MAX_VALUE} if the target is never
     * reached within the year.
     */
    private static int daysToReachBrix(long seed, int budLoad, double targetBrix,
                                        WeatherModel weather, VineSimulator vine) {
        RngStreams rng = new RngStreams(seed);
        List<DailyWeather> days = weather.generateYear(rng, Region.KAKHETI, 1);

        SiteProfile site = SimTestHelper.kahetianSite();
        PruningDecision pruning = new PruningDecision(budLoad);
        double suitability = SiteSuitability.score(Variety.SAPERAVI, site);

        VineState state = new VineState(
                PhenoStage.DORMANCY,
                0.0, 1.0, 0.0, 0.0, 8.0, 3.0, 200.0, 0.0
        );

        for (DailyWeather today : days) {
            state = vine.tick(state, today, site, suitability, pruning);
            if (state.brix() >= targetBrix) {
                return today.dayOfYear();
            }
        }
        return Integer.MAX_VALUE; // target Brix never reached
    }

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    /**
     * A warm-seeded vintage must reach {@code TARGET_BRIX} (20°Bx) in fewer
     * calendar days from the start of the year than a cool-seeded vintage.
     *
     * <p>Seeds are chosen by measured GDD from {@link #CANDIDATE_SEEDS}: the
     * highest-GDD seed is the WARM vintage, the lowest-GDD seed is the COOL
     * vintage.  This avoids assuming that any specific seed number produces a
     * particular vintage character.
     *
     * <p>Spec ref: §5.4 "a warm seed/year reaches a target Brix in fewer days
     * than a cool one".
     */
    @Test
    @DisplayName("Highest-GDD seed reaches 20°Bx faster than lowest-GDD seed")
    void warmVintageReachesTargetBrixFaster() {
        WeatherModel weather = SimServiceLocator.weatherModel();
        VineSimulator vine   = SimServiceLocator.vineSimulator();

        long[] seeds   = selectWarmAndCoolSeeds(weather);
        long seedWarm  = seeds[0];
        long seedCool  = seeds[1];

        int warmDays = daysToReachBrix(seedWarm, BUD_LOAD, TARGET_BRIX, weather, vine);
        int coolDays = daysToReachBrix(seedCool, BUD_LOAD, TARGET_BRIX, weather, vine);

        // Guard: both vintages must actually reach the target Brix within the year.
        assertNotEquals(Integer.MAX_VALUE, warmDays,
                "Warm vintage (seed=" + seedWarm + ") never reached " + TARGET_BRIX + "°Bx");
        assertNotEquals(Integer.MAX_VALUE, coolDays,
                "Cool vintage (seed=" + seedCool + ") never reached " + TARGET_BRIX + "°Bx");

        assertTrue(warmDays < coolDays,
                String.format("Warm seed=%d (highest GDD) reached %.1f°Bx on day %d; " +
                              "cool seed=%d (lowest GDD) reached it on day %d — warm must be earlier",
                        seedWarm, TARGET_BRIX, warmDays, seedCool, coolDays));
    }

    /**
     * On a fixed calendar day ({@code FIXED_DAY_FOR_TA} = day 260), the cool
     * vintage must retain higher titratable acidity (taGL) than the warm vintage.
     *
     * <p>Seeds are chosen by measured GDD from {@link #CANDIDATE_SEEDS}: the
     * highest-GDD seed is the WARM vintage, the lowest-GDD seed is the COOL
     * vintage.
     *
     * <p>Spec ref: §5.4 "cool retains higher TA at a fixed day".  This follows
     * from the warmer-day TA-drop model in §3.4.
     */
    @Test
    @DisplayName("Lowest-GDD seed retains higher TA on day 260 than highest-GDD seed")
    void coolVintageRetainsHigherTaAtFixedDay() {
        WeatherModel weather = SimServiceLocator.weatherModel();
        VineSimulator vine   = SimServiceLocator.vineSimulator();

        long[] seeds   = selectWarmAndCoolSeeds(weather);
        long seedWarm  = seeds[0];
        long seedCool  = seeds[1];

        VineState warm = SimTestHelper.vineStateAtPickDay(seedWarm, BUD_LOAD, FIXED_DAY_FOR_TA, weather, vine);
        VineState cool = SimTestHelper.vineStateAtPickDay(seedCool, BUD_LOAD, FIXED_DAY_FOR_TA, weather, vine);

        assertTrue(cool.taGL() > warm.taGL(),
                String.format("Cool vintage (seed=%d, lowest GDD) taGL=%.2f g/L must be > " +
                              "warm vintage (seed=%d, highest GDD) taGL=%.2f g/L at day %d",
                        seedCool, cool.taGL(), seedWarm, warm.taGL(), FIXED_DAY_FOR_TA));
    }

    /**
     * Sanity guard: confirm that the warm and cool seeds (chosen by measured GDD)
     * actually produce vintages with meaningfully different total GDD and Winkler
     * class.  Because these seeds are selected as the maximum and minimum GDD from
     * {@link #CANDIDATE_SEEDS}, the GDD values are guaranteed to differ unless all
     * candidate seeds produce identical GDD — which would indicate a degenerate
     * (seed-ignoring) WeatherModel.
     *
     * <p>We additionally assert that the Winkler classes differ, requiring at least
     * one full Winkler boundary between the warmest and coolest seeds in the
     * candidate pool.  This guards against the warmth offset being so small that
     * all seeds still land in the same Winkler class.
     */
    @Test
    @DisplayName("Warm and cool candidate seeds produce different GDD and Winkler class")
    void warmAndCoolSeedProduceDifferentVintages() {
        WeatherModel weather = SimServiceLocator.weatherModel();

        long[] seeds   = selectWarmAndCoolSeeds(weather);
        long seedWarm  = seeds[0];
        long seedCool  = seeds[1];

        RngStreams rngWarm = new RngStreams(seedWarm);
        List<DailyWeather> daysWarm = weather.generateYear(rngWarm, Region.KAKHETI, 1);
        Vintage vintageWarm = weather.rollVintage(rngWarm, Region.KAKHETI, 1, daysWarm);

        RngStreams rngCool = new RngStreams(seedCool);
        List<DailyWeather> daysCool = weather.generateYear(rngCool, Region.KAKHETI, 1);
        Vintage vintageCool = weather.rollVintage(rngCool, Region.KAKHETI, 1, daysCool);

        // GDD must differ (by construction: warm seed has the highest, cool has the lowest).
        assertTrue(vintageWarm.gddSeason() > vintageCool.gddSeason(),
                String.format("Warm seed=%d GDD=%.1f must exceed cool seed=%d GDD=%.1f; " +
                              "equal GDD means WeatherModel is ignoring the master seed",
                        seedWarm, vintageWarm.gddSeason(), seedCool, vintageCool.gddSeason()));

        // Winkler classes must also differ — confirms the warmth offset is large enough
        // to cross at least one class boundary across the candidate seed pool.
        assertNotEquals(vintageWarm.winkler(), vintageCool.winkler(),
                String.format("Warm seed=%d (%s, GDD=%.1f) and cool seed=%d (%s, GDD=%.1f) " +
                              "must differ in Winkler class; check VINTAGE_WARMTH_STDDEV_C",
                        seedWarm, vintageWarm.winkler(), vintageWarm.gddSeason(),
                        seedCool, vintageCool.winkler(), vintageCool.gddSeason()));
    }
}
