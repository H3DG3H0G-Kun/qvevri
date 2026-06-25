package com.game.sim;

import com.game.core.data.*;
import com.game.core.time.RngStreams;
import com.game.core.weather.KakhetiWeatherModel;
import com.game.estate.Vineyard;
import com.game.estate.VineyardReplayService;
import com.game.estate.VineyardView;
import com.game.sim.cellar.CellarResult;
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

import static org.junit.jupiter.api.Assertions.*;

/**
 * LANE REGIONS — Sim wiring acceptance tests (REGIONS-TRAVEL-CAREERS-SPEC §3).
 *
 * <p>These tests are NEW and do NOT modify any existing test class. They verify:
 * <ol>
 *   <li><b>Different regions, SAME seed+variety → DIFFERENT harvest output.</b>
 *       KAKHETI vs RACHA_LECHKHUMI with SAPERAVI must produce at least one
 *       different field (brix / health / volume) by pick day, because the climate
 *       offsets and site profile of RACHA_LECHKHUMI differ from KAKHETI's baseline.
 *   </li>
 *   <li><b>KAKHETI byte-identical guard.</b>
 *       The KAKHETI + SAPERAVI path through the new parameterised code must produce
 *       exactly the same VineState (brix/health/volume) as the SimTestHelper canonical
 *       path — confirming the zero-offset / canonical-site guarantee.
 *   </li>
 *   <li><b>Determinism within a region.</b>
 *       Two runs of the same (seed, region, variety) tuple must be byte-identical.
 *   </li>
 *   <li><b>Estate path (VineyardReplayService).</b>
 *       Two {@link Vineyard} objects with the same seed+variety but in DIFFERENT regions
 *       produce DIFFERENT {@link VineyardView} outputs; a KAKHETI vineyard produces
 *       the same view on two consecutive calls (determinism).
 *   </li>
 * </ol>
 *
 * <p><b>No existing test is modified or weakened.</b>
 * All assertions are directional or exact-equality guards.
 */
@DisplayName("REGIONS-SPEC §3 — Region sim wiring acceptance tests")
class RegionSimWiringTest {

    // ── Shared test parameters ────────────────────────────────────────────────

    private static final long SEED     = 42L;
    private static final int  BUD_LOAD = 12;

    /**
     * Pick day well into RIPENING for KAKHETI/SAPERAVI (the hottest region).
     * RACHA_LECHKHUMI has a shorter, cooler season so its vine may not reach
     * the same Brix — that is exactly the property we assert.
     */
    private static final int  PICK_DAY = 300;

    // ── Core pipeline helper (region + variety aware) ─────────────────────────

    /**
     * Runs the full vine-year pipeline (weather → vine tick → harvest → ferment → resolve)
     * parameterised by {@code region} and {@code variety}.
     * Mirrors {@link ReplayDeterminismTest#runPipeline} exactly; duplicated here so
     * this test class is self-contained and does not create a test-dependency chain.
     */
    private static WineLot runFullPipeline(
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

        CellarResult cellar =
                fermenter.ferment(must, FermentMethod.RED, 25.0, 0.80, rng);

        return Resolver.resolve(variety, FermentMethod.RED, must, cellar, vintage,
                suitability, "Test Bottle");
    }

    /** Vine state at pick day — no ferment/resolve step (faster for vine-level assertions). */
    private static VineState vineStateAt(
            long seed, int budLoad, int pickDay,
            Region region, Variety variety) {

        KakhetiWeatherModel weather = new KakhetiWeatherModel();
        VineSimulator vine = new KakhetiVineSimulator(variety);

        RngStreams rng = new RngStreams(seed);
        List<DailyWeather> days = weather.generateYear(rng, region, 1);

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
        return atPick != null ? atPick : state;
    }

    // =========================================================================
    // §A — Same variety, different regions → different output
    // =========================================================================

    /**
     * KAKHETI (warm, dry, baseline) vs RACHA_LECHKHUMI (cool, high-altitude,
     * −2.5 °C, steeper site at 700 m) with SAPERAVI must produce at least one
     * different WineLot field.
     *
     * <p>Rationale: RACHA_LECHKHUMI's climate offset of −2.5 °C mean annual
     * temperature and different SiteProfile (steep, 700 m, HUMUS_CARBONATE)
     * change the GDD accumulation rate, phenology timing, and site suitability
     * — all of which flow through the sim. The vine in the cooler, shorter
     * season accumulates less GDD by day 300 → lower Brix and/or different
     * volume/quality.
     */
    @Test
    @DisplayName("Same SAPERAVI seed in KAKHETI vs RACHA_LECHKHUMI produces different VineState")
    void sameVariety_differentRegion_differentVineState() {
        VineState kakheti      = vineStateAt(SEED, BUD_LOAD, PICK_DAY, Region.KAKHETI,         Variety.SAPERAVI);
        VineState rachaLechkh  = vineStateAt(SEED, BUD_LOAD, PICK_DAY, Region.RACHA_LECHKHUMI, Variety.SAPERAVI);

        boolean anyDifference =
                kakheti.brix()             != rachaLechkh.brix()             ||
                kakheti.healthFraction()   != rachaLechkh.healthFraction()   ||
                kakheti.potentialYieldKg() != rachaLechkh.potentialYieldKg() ||
                kakheti.taGL()             != rachaLechkh.taGL();

        assertTrue(anyDifference,
                String.format(
                        "KAKHETI/SAPERAVI (brix=%.2f, health=%.3f, yield=%.3f) and "
                        + "RACHA_LECHKHUMI/SAPERAVI (brix=%.2f, health=%.3f, yield=%.3f) "
                        + "must differ in at least one field: the −2.5°C climate offset "
                        + "and 700 m site must change GDD accumulation and phenology.",
                        kakheti.brix(), kakheti.healthFraction(), kakheti.potentialYieldKg(),
                        rachaLechkh.brix(), rachaLechkh.healthFraction(), rachaLechkh.potentialYieldKg()));
    }

    /**
     * Same SAPERAVI vine at the WineLot level: KAKHETI vs RACHA_LECHKHUMI must
     * differ on at least one of abv / quality / volumeL.
     */
    @Test
    @DisplayName("Same SAPERAVI seed in KAKHETI vs RACHA_LECHKHUMI produces different WineLot")
    void sameVariety_differentRegion_differentWineLot() {
        WineLot kakheti     = runFullPipeline(SEED, BUD_LOAD, PICK_DAY, Region.KAKHETI,         Variety.SAPERAVI);
        WineLot rachaLechkh = runFullPipeline(SEED, BUD_LOAD, PICK_DAY, Region.RACHA_LECHKHUMI, Variety.SAPERAVI);

        boolean anyDifference =
                kakheti.abv()     != rachaLechkh.abv()     ||
                kakheti.quality() != rachaLechkh.quality() ||
                kakheti.volumeL() != rachaLechkh.volumeL();

        assertTrue(anyDifference,
                String.format(
                        "KAKHETI (abv=%.2f, q=%.1f, vol=%.2f) and "
                        + "RACHA_LECHKHUMI (abv=%.2f, q=%.1f, vol=%.2f) "
                        + "must produce at least one different WineLot field for SAPERAVI.",
                        kakheti.abv(), kakheti.quality(), kakheti.volumeL(),
                        rachaLechkh.abv(), rachaLechkh.quality(), rachaLechkh.volumeL()));
    }

    /**
     * KAKHETI (warm, dry) vs IMERETI (−1.8 °C, 1.5× rain prob, higher humidity) with
     * SAPERAVI — a second different-region pair to confirm the wiring is general,
     * not tuned to a single region.
     */
    @Test
    @DisplayName("Same SAPERAVI seed in KAKHETI vs IMERETI produces different VineState")
    void sameVariety_kakhetiVsImereti_differentOutput() {
        VineState kakheti = vineStateAt(SEED, BUD_LOAD, PICK_DAY, Region.KAKHETI, Variety.SAPERAVI);
        VineState imereti = vineStateAt(SEED, BUD_LOAD, PICK_DAY, Region.IMERETI, Variety.SAPERAVI);

        boolean anyDifference =
                kakheti.brix()             != imereti.brix()             ||
                kakheti.healthFraction()   != imereti.healthFraction()   ||
                kakheti.potentialYieldKg() != imereti.potentialYieldKg() ||
                kakheti.taGL()             != imereti.taGL();

        assertTrue(anyDifference,
                String.format(
                        "KAKHETI/SAPERAVI (brix=%.2f, health=%.3f) and "
                        + "IMERETI/SAPERAVI (brix=%.2f, health=%.3f) "
                        + "must differ: the −1.8°C climate offset and higher humidity "
                        + "change weather and phenology.",
                        kakheti.brix(), kakheti.healthFraction(),
                        imereti.brix(), imereti.healthFraction()));
    }

    // =========================================================================
    // §B — KAKHETI byte-identical guard
    // =========================================================================

    /**
     * The KAKHETI + SAPERAVI path through the new parameterised code must match
     * the canonical {@link SimTestHelper} output exactly (brix / health / volume
     * at pick day).
     *
     * <p>This is the hard guarantee from REGIONS-SPEC: "KAKHETI must use the
     * baseline (zero) climate offset AND the exact current canonical site, so
     * every existing sim test produces IDENTICAL output."
     */
    @Test
    @DisplayName("KAKHETI/SAPERAVI parameterised path matches SimTestHelper canonical VineState")
    void kakhetiSaperavi_matchesCanonicalVineState() {
        // Canonical path (SimTestHelper always uses KAKHETI + default-constructor simulator)
        VineState canonical = SimTestHelper.vineStateAtPickDay(
                SEED, BUD_LOAD, PICK_DAY,
                SimServiceLocator.weatherModel(),
                SimServiceLocator.vineSimulator());

        // New parameterised path explicitly names KAKHETI
        VineState parameterised = vineStateAt(SEED, BUD_LOAD, PICK_DAY, Region.KAKHETI, Variety.SAPERAVI);

        assertEquals(canonical.brix(), parameterised.brix(), 0.0,
                "brix must be byte-identical between canonical and parameterised KAKHETI/SAPERAVI paths");
        assertEquals(canonical.healthFraction(), parameterised.healthFraction(), 0.0,
                "healthFraction must be byte-identical");
        assertEquals(canonical.potentialYieldKg(), parameterised.potentialYieldKg(), 0.0,
                "potentialYieldKg must be byte-identical");
        assertEquals(canonical.taGL(), parameterised.taGL(), 0.0,
                "taGL must be byte-identical");
        assertEquals(canonical.gddAccum(), parameterised.gddAccum(), 0.0,
                "gddAccum must be byte-identical");
        assertEquals(canonical.stage(), parameterised.stage(),
                "phenology stage must be identical");
    }

    /**
     * Full WineLot-level byte-identical check for KAKHETI/SAPERAVI.
     */
    @Test
    @DisplayName("KAKHETI/SAPERAVI WineLot matches SimTestHelper canonical output")
    void kakhetiSaperavi_wineLotMatchesCanonical() {
        WineLot canonical = SimTestHelper.runPipeline(
                SEED, BUD_LOAD, PICK_DAY,
                SimServiceLocator.weatherModel(),
                SimServiceLocator.vineSimulator(),
                SimServiceLocator.fermenter());

        WineLot parameterised = runFullPipeline(SEED, BUD_LOAD, PICK_DAY, Region.KAKHETI, Variety.SAPERAVI);

        assertEquals(canonical.abv(), parameterised.abv(), 0.0,
                "ABV must be byte-identical between canonical and parameterised KAKHETI paths");
        assertEquals(canonical.quality(), parameterised.quality(), 0.0,
                "quality must be byte-identical");
        assertEquals(canonical.volumeL(), parameterised.volumeL(), 0.0,
                "volumeL must be byte-identical");
        assertEquals(canonical.style(), parameterised.style(),
                "style must be identical");
    }

    // =========================================================================
    // §C — Determinism within a region
    // =========================================================================

    /**
     * Two runs of RACHA_LECHKHUMI + SAPERAVI with the same seed must be
     * byte-identical — confirming the new region path is also fully deterministic.
     */
    @Test
    @DisplayName("RACHA_LECHKHUMI/SAPERAVI is deterministic: two runs with same seed are identical")
    void rachaLechkhumiSaperavi_isDeterministic() {
        WineLot first  = runFullPipeline(SEED, BUD_LOAD, PICK_DAY, Region.RACHA_LECHKHUMI, Variety.SAPERAVI);
        WineLot second = runFullPipeline(SEED, BUD_LOAD, PICK_DAY, Region.RACHA_LECHKHUMI, Variety.SAPERAVI);

        assertEquals(first, second,
                "Two identical RACHA_LECHKHUMI/SAPERAVI runs must produce bit-for-bit equal WineLot");
        assertEquals(first.abv(),     second.abv(),     0.0, "ABV must be identical");
        assertEquals(first.quality(), second.quality(), 0.0, "quality must be identical");
        assertEquals(first.volumeL(), second.volumeL(), 0.0, "volumeL must be identical");
    }

    /**
     * Two runs of IMERETI + SAPERAVI with the same seed must be byte-identical.
     */
    @Test
    @DisplayName("IMERETI/SAPERAVI is deterministic: two runs with same seed are identical")
    void imeretiSaperavi_isDeterministic() {
        WineLot first  = runFullPipeline(SEED, BUD_LOAD, PICK_DAY, Region.IMERETI, Variety.SAPERAVI);
        WineLot second = runFullPipeline(SEED, BUD_LOAD, PICK_DAY, Region.IMERETI, Variety.SAPERAVI);

        assertEquals(first, second,
                "Two identical IMERETI/SAPERAVI runs must produce bit-for-bit equal WineLot");
        assertEquals(first.abv(),     second.abv(),     0.0, "ABV must be identical");
        assertEquals(first.quality(), second.quality(), 0.0, "quality must be identical");
        assertEquals(first.volumeL(), second.volumeL(), 0.0, "volumeL must be identical");
    }

    // =========================================================================
    // §D — Estate path: VineyardReplayService with different regions
    // =========================================================================

    /**
     * Two {@link Vineyard} entities with the same seed + SAPERAVI but in
     * KAKHETI vs RACHA_LECHKHUMI must produce different {@link VineyardView}
     * outputs via {@link VineyardReplayService#viewAt}.
     *
     * <p>This tests the full estate harvest path end-to-end: Vineyard.region →
     * VineyardReplayService → KakhetiWeatherModel (with region climate offset) →
     * RegionSiteProfiles → KakhetiVineSimulator → VineState → VineyardView.
     */
    @Test
    @DisplayName("Estate path: KAKHETI vs RACHA_LECHKHUMI vineyard produces different VineyardView")
    void estatePath_differentRegion_differentView() {
        VineyardReplayService service = new VineyardReplayService();

        Vineyard kakhetiVineyard      = new Vineyard(1L, Region.KAKHETI,         Variety.SAPERAVI, SEED, BUD_LOAD);
        Vineyard rachaVineyard        = new Vineyard(1L, Region.RACHA_LECHKHUMI, Variety.SAPERAVI, SEED, BUD_LOAD);

        VineyardView kakhetiView = service.viewAt(kakhetiVineyard,      1, PICK_DAY);
        VineyardView rachaView   = service.viewAt(rachaVineyard,        1, PICK_DAY);

        boolean anyDifference =
                kakhetiView.brix()             != rachaView.brix()             ||
                kakhetiView.healthFraction()   != rachaView.healthFraction()   ||
                kakhetiView.estimatedYieldKg() != rachaView.estimatedYieldKg();

        assertTrue(anyDifference,
                String.format(
                        "Estate path: KAKHETI (brix=%.2f, health=%.3f, yield=%.3f) and "
                        + "RACHA_LECHKHUMI (brix=%.2f, health=%.3f, yield=%.3f) "
                        + "must produce different VineyardView for SAPERAVI with the same seed.",
                        kakhetiView.brix(), kakhetiView.healthFraction(), kakhetiView.estimatedYieldKg(),
                        rachaView.brix(),   rachaView.healthFraction(),   rachaView.estimatedYieldKg()));
    }

    /**
     * A KAKHETI vineyard via {@link VineyardReplayService#viewAt} must produce the
     * same brix / healthFraction / potentialYieldKg on two consecutive calls —
     * proving determinism through the estate replay path.
     */
    @Test
    @DisplayName("Estate path: KAKHETI VineyardView is deterministic across two calls")
    void estatePath_kakheti_isDeterministic() {
        VineyardReplayService service = new VineyardReplayService();

        Vineyard vineyard = new Vineyard(42L, Region.KAKHETI, Variety.SAPERAVI, SEED, BUD_LOAD);

        VineyardView first  = service.viewAt(vineyard, 1, PICK_DAY);
        VineyardView second = service.viewAt(vineyard, 1, PICK_DAY);

        assertEquals(first.brix(), second.brix(), 0.0,
                "VineyardView.brix must be identical across two replay calls for KAKHETI");
        assertEquals(first.healthFraction(), second.healthFraction(), 0.0,
                "VineyardView.healthFraction must be identical");
        assertEquals(first.estimatedYieldKg(), second.estimatedYieldKg(), 0.0,
                "VineyardView.estimatedYieldKg must be identical");
        assertEquals(first.stage(), second.stage(),
                "VineyardView.stage must be identical");
    }

    // =========================================================================
    // §E — RegionSiteProfiles canonical values guard
    // =========================================================================

    /**
     * KAKHETI SiteProfile must exactly equal the hardcoded canonical values from
     * {@code YearRunner.kakhetianSite()} and {@code SimTestHelper.kahetianSite()}:
     * HUMUS_CARBONATE, slope=12.0, aspect=180.0, altitude=450.0, frostRisk=0.15,
     * waterProximity=0.25.
     *
     * <p>This is the byte-identical guard at the data level: if anyone changes
     * {@code RegionSiteProfiles.KAKHETI}, this test will immediately catch it.
     */
    @Test
    @DisplayName("RegionSiteProfiles.KAKHETI matches the canonical hardcoded YearRunner/SimTestHelper site")
    void kakhetiSiteProfile_matchesCanonicalHardcoded() {
        SiteProfile actual   = RegionSiteProfiles.of(Region.KAKHETI);
        SiteProfile expected = new SiteProfile(
                SoilType.HUMUS_CARBONATE,
                12.0,   // slopeDeg
                180.0,  // aspectDeg — south-facing
                450.0,  // altitudeM
                0.15,   // frostRisk
                0.25    // waterProximity
        );

        assertEquals(expected, actual,
                "RegionSiteProfiles.KAKHETI must be HUMUS_CARBONATE/12.0/180.0/450.0/0.15/0.25 — "
                + "the exact canonical site hardcoded in YearRunner and SimTestHelper; "
                + "changing this would break byte-identical output for all KAKHETI/SAPERAVI tests.");
    }

    /**
     * All 7 regional site profiles must be non-null — lookup never returns null.
     */
    @Test
    @DisplayName("RegionSiteProfiles.of() returns non-null for all 7 regions")
    void allRegionSiteProfilesNonNull() {
        for (Region r : Region.values()) {
            SiteProfile site = RegionSiteProfiles.of(r);
            assertNotNull(site, "SiteProfile must not be null for region " + r);
            assertNotNull(site.soil(), "SiteProfile.soil must not be null for region " + r);
        }
    }

    /**
     * Non-Kakheti regions must have a different SiteProfile than KAKHETI —
     * confirming they were independently specified.
     */
    @Test
    @DisplayName("Non-KAKHETI site profiles are distinct from the KAKHETI canonical site")
    void nonKakhetiSiteProfiles_areDifferentFromKakheti() {
        SiteProfile kakheti = RegionSiteProfiles.of(Region.KAKHETI);

        for (Region r : Region.values()) {
            if (r == Region.KAKHETI) continue;
            SiteProfile other = RegionSiteProfiles.of(r);
            assertNotEquals(kakheti, other,
                    "Region " + r + " must have a SiteProfile distinct from KAKHETI; "
                    + "each non-baseline region must have its own distinct terroir geometry.");
        }
    }
}
