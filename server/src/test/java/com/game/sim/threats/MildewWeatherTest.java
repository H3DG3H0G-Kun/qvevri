package com.game.sim.threats;

import com.game.core.data.*;
import com.game.core.time.RngStreams;
import com.game.core.weather.KakhetiWeatherModel;
import com.game.sim.threats.api.ThreatContext;
import com.game.sim.threats.api.ThreatEffect;
import com.game.sim.threats.api.ThreatMemory;
import com.game.sim.threats.engine.*;
import com.game.sim.threats.fungal.DownyMildew;
import com.game.sim.vine.KakhetiVineSimulator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * §6.2 — Mildew responds to weather; copper spray measurably reduces pressure.
 */
class MildewWeatherTest {

    private static final SiteProfile SITE = new SiteProfile(
            SoilType.HUMUS_CARBONATE, 12.0, 180.0, 450.0, 0.15, 0.25);

    /** A wet/humid day should accumulate more downy mildew than a dry/hot day. */
    @Test
    void downyMildewHigherOnWetDay() {
        // Use a pre-existing pressure level so both days start equally
        ThreatMemory startMem = new ThreatMemory(0.1, 0.0, 5, 0, false);
        RngStreams rng = new RngStreams(42L);
        VineState vine = shootGrowthVine();

        DailyWeather wetDay = new DailyWeather(120, 14.0, 21.0, 18.0, 0.85);
        DailyWeather dryDay = new DailyWeather(120, 20.0, 34.0, 0.0,  0.35);

        double wetLevel = evalDowny(wetDay, vine, rng, 0.0, startMem);
        double dryLevel = evalDowny(dryDay, vine, rng, 0.0, startMem);

        assertTrue(wetLevel > dryLevel,
            "Downy mildew should be higher on wet/humid day (wet=" + wetLevel
            + " dry=" + dryLevel + ")");
    }

    /** Copper spray at 1.0 should reduce downy mildew compared to no spray. */
    @Test
    void copperSprayReducesDownyMildew() {
        ThreatMemory startMem = new ThreatMemory(0.30, 0.0, 10, 0, true);
        DailyWeather wetDay   = new DailyWeather(120, 14.0, 21.0, 18.0, 0.85);
        RngStreams rng         = new RngStreams(42L);
        VineState vine         = shootGrowthVine();

        double noCopper  = evalDowny(wetDay, vine, rng, 0.0, startMem);
        double withCopper = evalDowny(wetDay, vine, rng, 1.0, startMem);

        assertTrue(withCopper < noCopper,
            "Copper spray should reduce downy mildew level (noCopper=" + noCopper
            + " withCopper=" + withCopper + ")");
    }

    /** Full wet-year pipeline should produce >= quality penalty vs a dry-year pipeline. */
    @Test
    void wetYearYieldsHigherCumulativePenalty() {
        long wetSeed  = findSeedByPattern(true);
        long drySeed  = findSeedByPattern(false);

        double wetPenalty = seasonCumulativePenalty(wetSeed);
        double dryPenalty = seasonCumulativePenalty(drySeed);

        assertTrue(wetPenalty >= dryPenalty,
            "Wet year should produce >= cumulative quality penalty vs dry year "
            + "(wet=" + wetPenalty + " dry=" + dryPenalty + ")");
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private static double evalDowny(DailyWeather day, VineState vine,
                                    RngStreams rng, double copper, ThreatMemory mem) {
        DownyMildew dm = new DownyMildew();
        ThreatContext ctx = new ThreatContext(
                day.dayOfYear(), day, 800.0, vine, SITE,
                true, 0.40, false, copper, 0.0, false, false, false, false, false, 0.0,
                rng.stream("threat." + dm.id()), mem);
        return dm.evaluate(ctx).nextMemory().level();
    }

    private static VineState shootGrowthVine() {
        return new VineState(PhenoStage.SHOOT_GROWTH, 200.0, 1.0, 8.0, 0.0, 8.0, 3.2, 200.0, 0.0);
    }

    private static long findSeedByPattern(boolean wantWet) {
        KakhetiWeatherModel model = new KakhetiWeatherModel();
        for (long seed = 1; seed < 500; seed++) {
            RngStreams rng = new RngStreams(seed);
            List<DailyWeather> days = model.generateYear(rng, Region.KAKHETI, 1);
            Vintage v = model.rollVintage(rng, Region.KAKHETI, 1, days);
            if (v.patternLabel().contains("wet") == wantWet) return seed;
        }
        return wantWet ? 2L : 1L;
    }

    private static double seasonCumulativePenalty(long seed) {
        KakhetiWeatherModel model = new KakhetiWeatherModel();
        RngStreams rng = new RngStreams(seed);
        List<DailyWeather> days = model.generateYear(rng, Region.KAKHETI, 1);
        Vintage vintage = model.rollVintage(rng, Region.KAKHETI, 1, days);

        KakhetiVineSimulator vineSimulator = new KakhetiVineSimulator();
        PruningDecision pruning = new PruningDecision(12);
        double suitability = com.game.sim.soil.SiteSuitability.score(Variety.SAPERAVI, SITE);
        VineState vine = new VineState(PhenoStage.DORMANCY, 0.0, 1.0, 0.0, 0.0, 8.0, 3.0, 200.0, 0.0);

        ThreatEngine engine = new ThreatEngine(ThreatRegistry.all());

        for (int d = 0; d < 365; d++) {
            DailyWeather today = days.get(d);
            vine = vineSimulator.tick(vine, today, SITE, suitability, pruning);
            DayInputs inputs = new DayInputs(today, vintage.gddSeason(), SITE,
                    true, 0.40, false, 0.0, 0.0, false, false, false, false, false, 0.0, rng);
            vine = engine.step(vine, inputs).vine();
        }
        return engine.cumulativeQualityPenalty();
    }
}
