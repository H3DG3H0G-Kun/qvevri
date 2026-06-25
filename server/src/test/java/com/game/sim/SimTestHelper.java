package com.game.sim;

import com.game.core.data.*;
import com.game.core.time.RngStreams;
import com.game.core.time.SimClock;
import com.game.core.weather.WeatherModel;
import com.game.sim.cellar.CellarResult;
import com.game.sim.cellar.Fermenter;
import com.game.sim.ops.Harvest;
import com.game.sim.resolve.Resolver;
import com.game.sim.vine.VineSimulator;

import java.util.List;

/**
 * Shared pipeline runner used by all §5 acceptance tests.
 *
 * <p>This class wires together the frozen §3 module seams (WeatherModel,
 * VineSimulator, Fermenter, Resolver) into a single deterministic
 * {@code runPipeline()} call so each test can configure exactly what it needs
 * to vary without duplicating the orchestration loop.
 *
 * <p>Caller must supply a concrete {@link WeatherModel} and
 * {@link VineSimulator} and {@link Fermenter}; the Saperavi/Kakheti
 * defaults are provided via factory methods for convenience.
 *
 * <h2>Pipeline steps (mirrors YearRunner §3.8)</h2>
 * <ol>
 *   <li>Build {@link RngStreams} from {@code masterSeed}.</li>
 *   <li>Generate Kakheti weather year via {@link WeatherModel}.</li>
 *   <li>Roll vintage from the generated days.</li>
 *   <li>Tick the vine day-by-day (day 0..364) applying the pruning decision;
 *       capture VineState at {@code pickDay}.</li>
 *   <li>Harvest: {@link Harvest#pick} on that VineState.</li>
 *   <li>Ferment: {@link Fermenter#ferment} with RED method, 25°C, tending=0.8.</li>
 *   <li>Resolve: {@link Resolver#resolve} → {@link WineLot}.</li>
 * </ol>
 *
 * <p>The {@code siteProfile()} factory returns the canonical Kakheti
 * HUMUS_CARBONATE slope profile from GDD §3.3.
 */
public final class SimTestHelper {

    // -------------------------------------------------------------------------
    // Canonical Kakheti site (HUMUS_CARBONATE, S-facing slope, mid altitude)
    // -------------------------------------------------------------------------
    public static SiteProfile kahetianSite() {
        return new SiteProfile(
                SoilType.HUMUS_CARBONATE,
                12.0,   // slopeDeg – moderate slope
                180.0,  // aspectDeg – south-facing
                450.0,  // altitudeM
                0.15,   // frostRisk – slope drains cold air, low risk
                0.25    // waterProximity
        );
    }

    // -------------------------------------------------------------------------
    // Fermentation constants for all tests (RED style, sensible cellar)
    // -------------------------------------------------------------------------
    public static final FermentMethod  DEFAULT_METHOD      = FermentMethod.RED;
    public static final double         DEFAULT_CELLAR_TEMP = 25.0; // °C, within 21-30 red band
    public static final double         DEFAULT_TENDING     = 0.80; // cap management quality

    // -------------------------------------------------------------------------
    // Core pipeline
    // -------------------------------------------------------------------------

    /**
     * Full single-year pipeline.
     *
     * @param masterSeed   seed for the RNG stream factory
     * @param budLoad      buds retained per vine (PruningDecision)
     * @param pickDay      day-of-year to harvest (0..364)
     * @param weather      WeatherModel implementation
     * @param vine         VineSimulator implementation
     * @param fermenter    Fermenter implementation
     * @return the fully resolved {@link WineLot}
     */
    public static WineLot runPipeline(
            long masterSeed,
            int budLoad,
            int pickDay,
            WeatherModel weather,
            VineSimulator vine,
            Fermenter fermenter) {

        // Step 1: seeded RNG
        RngStreams rng = new RngStreams(masterSeed);

        // Step 2: generate weather year
        List<DailyWeather> days = weather.generateYear(rng, Region.KAKHETI, 1);

        // Step 3: vintage roll
        Vintage vintage = weather.rollVintage(rng, Region.KAKHETI, 1, days);

        // Step 4: vine simulation
        SiteProfile site = kahetianSite();
        PruningDecision pruning = new PruningDecision(budLoad);

        // Initial dormancy state — zero-everything (day 0, before any GDD)
        VineState state = new VineState(
                PhenoStage.DORMANCY,
                0.0,   // gddAccum
                1.0,   // healthFraction – starts fully healthy
                0.0,   // potentialYieldKg – not yet determined
                0.0,   // brix
                8.0,   // taGL – starting acidity placeholder (pre-véraison)
                3.0,   // pH
                200.0, // yanMgL
                0.0    // tanninRipeness01
        );

        // Suitability is pure/static — safe to compute once
        double suitability = com.game.sim.soil.SiteSuitability.score(Variety.SAPERAVI, site);

        VineState atPick = null;
        SimClock clock = new SimClock(1);

        for (int d = 0; d < 365; d++) {
            // Use pre-generated day list (index d, day-of-year matches list position)
            DailyWeather today = days.get(d);
            state = vine.tick(state, today, site, suitability, pruning);
            if (today.dayOfYear() == pickDay) {
                atPick = state;
            }
            clock.advanceDay();
        }

        // If pickDay was never seen in the day list (impl bug or out-of-range input),
        // fall back to the final state so downstream assertions surface the real problem.
        if (atPick == null) {
            atPick = state;
        }

        // Step 5: harvest
        // potentialYieldKg from final VineState, scaled to volume at ~0.75 l/kg
        double volumeFromYield = atPick.potentialYieldKg() * 0.75;
        MustProfile must = Harvest.pick(atPick, volumeFromYield, vintage.year());

        // Step 6: ferment
        CellarResult cellar = fermenter.ferment(must, DEFAULT_METHOD, DEFAULT_CELLAR_TEMP,
                DEFAULT_TENDING, rng);

        // Step 7: resolve
        return Resolver.resolve(
                Variety.SAPERAVI,
                DEFAULT_METHOD,
                must,
                cellar,
                vintage,
                suitability,
                "Test Bottle"
        );
    }

    /**
     * Convenience overload for tests that just need {@link VineState} at a
     * specific pick day (e.g. PickTimingTest needs to inspect brix/TA before
     * full resolution).
     */
    public static VineState vineStateAtPickDay(
            long masterSeed,
            int budLoad,
            int pickDay,
            WeatherModel weather,
            VineSimulator vine) {

        RngStreams rng = new RngStreams(masterSeed);
        List<DailyWeather> days = weather.generateYear(rng, Region.KAKHETI, 1);

        SiteProfile site = kahetianSite();
        PruningDecision pruning = new PruningDecision(budLoad);
        double suitability = com.game.sim.soil.SiteSuitability.score(Variety.SAPERAVI, site);

        VineState state = new VineState(
                PhenoStage.DORMANCY,
                0.0, 1.0, 0.0, 0.0, 8.0, 3.0, 200.0, 0.0
        );

        VineState atPick = null;
        for (DailyWeather today : days) {
            state = vine.tick(state, today, site, suitability, pruning);
            if (today.dayOfYear() == pickDay) {
                atPick = state;
            }
        }
        // Fall back to final state if pickDay not found in list.
        return atPick != null ? atPick : state;
    }

    private SimTestHelper() {}
}
