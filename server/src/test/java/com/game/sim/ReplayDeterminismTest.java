package com.game.sim;

import com.game.core.data.*;
import com.game.core.time.RngStreams;
import com.game.core.weather.KakhetiWeatherModel;
import com.game.sim.cellar.KineticFermenter;
import com.game.sim.ops.Harvest;
import com.game.sim.region.RegionSiteProfiles;
import com.game.sim.resolve.Resolver;
import com.game.sim.soil.SiteSuitability;
import com.game.sim.vine.KakhetiVineSimulator;
import com.game.sim.vine.VineSimulator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * REGIONS-SPEC §5 — Replay determinism test.
 *
 * <p>Verifies that running the same (seed, region, variety, year) pipeline twice
 * produces byte-identical {@link WineLot} outputs — for both the canonical
 * KAKHETI/SAPERAVI path and a non-Kakheti path (IMERETI/TSOLIKOURI).
 *
 * <p>This test also acts as a regression guard confirming that the KAKHETI/SAPERAVI
 * path through the new parameterised code produces the same result as the original
 * {@link SimTestHelper} pipeline (which uses the default-constructor simulator).
 */
@DisplayName("REGIONS-SPEC §5 — Replay determinism: same inputs → same WineLot")
class ReplayDeterminismTest {

    private static final long SEED     = 42L;
    private static final int  BUD_LOAD = 12;
    private static final int  PICK_DAY = 290;

    // ── Full pipeline runner (mirrors SimTestHelper but with region + variety) ─

    private static WineLot runPipeline(
            long seed, int budLoad, int pickDay,
            Region region, Variety variety) {

        KakhetiWeatherModel weather = new KakhetiWeatherModel();
        VineSimulator vine = new KakhetiVineSimulator(variety);
        KineticFermenter fermenter = new KineticFermenter();

        RngStreams rng = new RngStreams(seed);
        List<DailyWeather> days = weather.generateYear(rng, region, 1);
        Vintage vintage = weather.rollVintage(rng, region, 1, days);

        SiteProfile site = RegionSiteProfiles.of(region);
        PruningDecision pruning = new PruningDecision(budLoad);
        double suitability = SiteSuitability.score(variety, site);

        VineState state = new VineState(
                PhenoStage.DORMANCY, 0.0, 1.0, 0.0, 0.0, 8.0, 3.0, 200.0, 0.0);

        VineState atPick = null;
        for (DailyWeather today : days) {
            state = vine.tick(state, today, site, suitability, pruning);
            if (today.dayOfYear() == pickDay) {
                atPick = state;
            }
        }
        if (atPick == null) atPick = state;

        double volumeL = atPick.potentialYieldKg() * 0.75;
        MustProfile must = Harvest.pick(atPick, volumeL, vintage.year());

        com.game.sim.cellar.CellarResult cellar =
                fermenter.ferment(must, FermentMethod.RED, 25.0, 0.80, rng);

        return Resolver.resolve(variety, FermentMethod.RED, must, cellar, vintage, suitability,
                "Test Bottle");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Kakheti / Saperavi: two identical runs must be byte-identical
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("KAKHETI/SAPERAVI: two runs with same seed are byte-identical")
    void kakhetiSaperapiIsRepeatable() {
        WineLot first  = runPipeline(SEED, BUD_LOAD, PICK_DAY, Region.KAKHETI, Variety.SAPERAVI);
        WineLot second = runPipeline(SEED, BUD_LOAD, PICK_DAY, Region.KAKHETI, Variety.SAPERAVI);

        assertEquals(first, second,
                "Two identical KAKHETI/SAPERAVI runs must produce bit-for-bit equal WineLot");
        assertEquals(first.abv(),     second.abv(),     0.0, "ABV must be identical");
        assertEquals(first.quality(), second.quality(), 0.0, "quality must be identical");
        assertEquals(first.volumeL(), second.volumeL(), 0.0, "volumeL must be identical");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // KAKHETI/SAPERAVI via new parameterised path == SimTestHelper canonical path
    // This is the hard regression guard for the "byte-identical" constraint.
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("New parameterised KAKHETI/SAPERAVI path matches SimTestHelper canonical output")
    void kakhetiSaperapiMatchesCanonical() {
        // Canonical path (original SimTestHelper + default-constructor simulator)
        WineLot canonical = SimTestHelper.runPipeline(
                SEED, BUD_LOAD, PICK_DAY,
                SimServiceLocator.weatherModel(),
                SimServiceLocator.vineSimulator(),
                SimServiceLocator.fermenter());

        // New parameterised path
        WineLot parameterised = runPipeline(SEED, BUD_LOAD, PICK_DAY,
                Region.KAKHETI, Variety.SAPERAVI);

        assertEquals(canonical.abv(),     parameterised.abv(),     0.0,
                "ABV must be identical between canonical and parameterised KAKHETI/SAPERAVI path");
        assertEquals(canonical.quality(), parameterised.quality(), 0.0,
                "quality must be identical");
        assertEquals(canonical.volumeL(), parameterised.volumeL(), 0.0,
                "volumeL must be identical");
        assertEquals(canonical.style(),   parameterised.style(),
                "style must be identical");
        assertEquals(canonical.aroma(),   parameterised.aroma(),
                "aroma map must be identical");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Imereti / Tsolikouri: two runs must also be byte-identical
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("IMERETI/TSOLIKOURI: two runs with same seed are byte-identical")
    void imeretiTsolikouriIsRepeatable() {
        WineLot first  = runPipeline(SEED, BUD_LOAD, PICK_DAY, Region.IMERETI, Variety.TSOLIKOURI);
        WineLot second = runPipeline(SEED, BUD_LOAD, PICK_DAY, Region.IMERETI, Variety.TSOLIKOURI);

        assertEquals(first, second,
                "Two identical IMERETI/TSOLIKOURI runs must produce bit-for-bit equal WineLot");
        assertEquals(first.abv(),     second.abv(),     0.0, "ABV must be identical");
        assertEquals(first.quality(), second.quality(), 0.0, "quality must be identical");
        assertEquals(first.volumeL(), second.volumeL(), 0.0, "volumeL must be identical");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Different regions must produce different outputs (sanity guard)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("KAKHETI/SAPERAVI and IMERETI/TSOLIKOURI produce different WineLot outputs")
    void differentRegionVarietyProduceDifferentOutputs() {
        WineLot kakheti  = runPipeline(SEED, BUD_LOAD, PICK_DAY, Region.KAKHETI, Variety.SAPERAVI);
        WineLot imereti  = runPipeline(SEED, BUD_LOAD, PICK_DAY, Region.IMERETI, Variety.TSOLIKOURI);

        boolean anyDifference =
                kakheti.abv()     != imereti.abv()     ||
                kakheti.quality() != imereti.quality() ||
                kakheti.volumeL() != imereti.volumeL() ||
                !kakheti.aroma().equals(imereti.aroma());

        assertTrue(anyDifference,
                "KAKHETI/SAPERAVI and IMERETI/TSOLIKOURI must produce at least one different WineLot field");
    }

}
