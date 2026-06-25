package com.game.sim.threats;

import com.game.core.data.*;
import com.game.core.time.RngStreams;
import com.game.core.weather.KakhetiWeatherModel;
import com.game.sim.harness.YearRunner;
import com.game.sim.threats.engine.*;
import com.game.sim.threats.harness.ThreatYearRunner;
import com.game.sim.vine.KakhetiVineSimulator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * §6.6 — Plausibility / no-regression:
 * No NaN / negative / out-of-range fields; health stays in [0,1]; quality stays
 * within ~25 points of the Phase-0 no-threat baseline.
 */
class PlausibilityTest {

    @Test
    void bottleFieldsAreFiniteAndInRange() {
        WineLot b = ThreatYearRunner.run(42L, 12, 290, false);

        assertFalse(Double.isNaN(b.quality()),         "quality must not be NaN");
        assertFalse(Double.isNaN(b.abv()),             "ABV must not be NaN");
        assertFalse(Double.isNaN(b.volumeL()),         "volume must not be NaN");
        assertFalse(Double.isNaN(b.ageabilityYears()), "ageability must not be NaN");

        assertTrue(b.quality() >= 0.0 && b.quality() <= 100.0,
            "quality must be in [0,100], got " + b.quality());
        assertTrue(b.abv() > 0.0 && b.abv() < 25.0,
            "ABV must be in (0,25), got " + b.abv());
        assertTrue(b.volumeL() >= 0.0,
            "volume must be >= 0, got " + b.volumeL());
        assertTrue(b.ageabilityYears() >= 0.0,
            "ageability must be >= 0, got " + b.ageabilityYears());

        assertNotNull(b.fault(),   "fault must not be null");
        assertNotNull(b.variety(), "variety must not be null");
        assertNotNull(b.style(),   "style must not be null");
        assertNotNull(b.aroma(),   "aroma map must not be null");
        assertFalse(b.aroma().isEmpty(), "aroma map must not be empty");
    }

    @Test
    void healthStaysInZeroOneRangeEveryDay() {
        KakhetiWeatherModel model = new KakhetiWeatherModel();
        RngStreams rng = new RngStreams(42L);
        List<DailyWeather> days = model.generateYear(rng, Region.KAKHETI, 1);
        Vintage vintage = model.rollVintage(rng, Region.KAKHETI, 1, days);

        SiteProfile site = new SiteProfile(SoilType.HUMUS_CARBONATE, 12.0, 180.0, 450.0, 0.15, 0.25);
        KakhetiVineSimulator vSim = new KakhetiVineSimulator();
        PruningDecision pruning   = new PruningDecision(12);
        double suitability = com.game.sim.soil.SiteSuitability.score(Variety.SAPERAVI, site);
        VineState vine = new VineState(PhenoStage.DORMANCY, 0.0, 1.0, 0.0, 0.0, 8.0, 3.0, 200.0, 0.0);

        ThreatEngine engine = new ThreatEngine(ThreatRegistry.all());

        for (int d = 0; d < 365; d++) {
            DailyWeather today = days.get(d);
            vine = vSim.tick(vine, today, site, suitability, pruning);
            DayInputs inputs = new DayInputs(today, vintage.gddSeason(), site,
                    true, 0.40, false, 0.0, 0.0, false, false, false, false, false, 0.0, rng);
            DayResult r = engine.step(vine, inputs);
            vine = r.vine();

            double h = vine.healthFraction();
            assertTrue(h >= 0.0 && h <= 1.0,
                "health out of [0,1] on day " + today.dayOfYear() + ": " + h);
            assertTrue(vine.potentialYieldKg() >= 0.0,
                "negative yield on day " + today.dayOfYear() + ": " + vine.potentialYieldKg());
            assertFalse(Double.isNaN(h), "NaN health on day " + today.dayOfYear());
            assertFalse(Double.isNaN(vine.potentialYieldKg()),
                "NaN yield on day " + today.dayOfYear());
        }
    }

    @Test
    void qualityDropNotMoreThan25PointsBelowBaseline() {
        // Phase-0 no-threat baseline
        WineLot baseline    = YearRunner.run(42L, 12, 290, false);
        // Threat run (default levers: own-roots, no sprays → threats show)
        WineLot withThreats = ThreatYearRunner.run(42L, 12, 290, false);

        double drop = baseline.quality() - withThreats.quality();
        assertTrue(drop <= 25.0,
            "Quality drop must not exceed 25 points below no-threat baseline "
            + "(baseline=" + baseline.quality()
            + " threats=" + withThreats.quality()
            + " drop=" + drop + ")");

        // Threats should not add quality (noble rot may give a small bump, cap at +10)
        assertTrue(withThreats.quality() <= baseline.quality() + 10.0,
            "Threat run should not significantly exceed no-threat quality "
            + "(baseline=" + baseline.quality()
            + " threats=" + withThreats.quality() + ")");
    }

    @Test
    void aromaticMapKeysAreSorted() {
        WineLot b = ThreatYearRunner.run(42L, 12, 290, false);
        String prev = "";
        for (String key : b.aroma().keySet()) {
            assertTrue(key.compareTo(prev) >= 0,
                "Aroma keys must be in sorted order; '" + key + "' follows '" + prev + "'");
            prev = key;
        }
    }

    @Test
    void multipleSeeds_allBottlesPlausible() {
        long[] seeds = {1L, 7L, 42L, 100L, 777L};
        for (long seed : seeds) {
            WineLot b = ThreatYearRunner.run(seed, 12, 290, false);
            assertTrue(b.quality() >= 0.0 && b.quality() <= 100.0,
                "Seed " + seed + ": quality out of range: " + b.quality());
            assertFalse(Double.isNaN(b.quality()), "Seed " + seed + ": quality NaN");
            assertFalse(Double.isNaN(b.abv()),     "Seed " + seed + ": ABV NaN");
            assertTrue(b.volumeL() >= 0.0,         "Seed " + seed + ": negative volume");
            assertNotNull(b.fault(),               "Seed " + seed + ": null fault");
        }
    }

    @Test
    void cumulativeQualityPenaltyStaysInZeroOne() {
        KakhetiWeatherModel model = new KakhetiWeatherModel();
        RngStreams rng = new RngStreams(42L);
        List<DailyWeather> days = model.generateYear(rng, Region.KAKHETI, 1);
        Vintage vintage = model.rollVintage(rng, Region.KAKHETI, 1, days);
        SiteProfile site = new SiteProfile(SoilType.HUMUS_CARBONATE, 12.0, 180.0, 450.0, 0.15, 0.25);
        KakhetiVineSimulator vSim = new KakhetiVineSimulator();
        PruningDecision pruning   = new PruningDecision(12);
        double suitability = com.game.sim.soil.SiteSuitability.score(Variety.SAPERAVI, site);
        VineState vine = new VineState(PhenoStage.DORMANCY, 0.0, 1.0, 0.0, 0.0, 8.0, 3.0, 200.0, 0.0);
        ThreatEngine engine = new ThreatEngine(ThreatRegistry.all());

        for (DailyWeather today : days) {
            vine = vSim.tick(vine, today, site, suitability, pruning);
            DayInputs inputs = new DayInputs(today, vintage.gddSeason(), site,
                    true, 0.40, false, 0.0, 0.0, false, false, false, false, false, 0.0, rng);
            DayResult r = engine.step(vine, inputs);
            vine = r.vine();

            double cp = r.report().cumulativeQualityPenalty01();
            assertTrue(cp >= 0.0 && cp <= 1.0,
                "cumulativeQualityPenalty must be in [0,1] on day "
                + today.dayOfYear() + ": " + cp);
        }
    }
}
