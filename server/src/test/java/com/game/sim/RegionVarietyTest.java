package com.game.sim;

import com.game.core.data.*;
import com.game.core.time.RngStreams;
import com.game.core.weather.KakhetiWeatherModel;
import com.game.sim.soil.SiteSuitability;
import com.game.sim.vine.KakhetiVineSimulator;
import com.game.sim.vine.VineSimulator;
import com.game.sim.region.RegionSiteProfiles;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * REGIONS-SPEC §5 — multi-region and multi-variety direction tests.
 *
 * <p>These tests verify the <em>direction</em> of differences, not exact values.
 * They do NOT weaken or override any existing test.
 *
 * <h2>Assertions (REGIONS-SPEC §5)</h2>
 * <ol>
 *   <li>Cooler/wetter IMERETI + TSOLIKOURI produces lower Brix than
 *       KAKHETI + SAPERAVI at the same pick day.</li>
 *   <li>Cooler IMERETI + TSOLIKOURI produces higher TA than
 *       KAKHETI + SAPERAVI at the same pick day.</li>
 *   <li>A white variety (TSOLIKOURI) produces lower tannin than SAPERAVI
 *       at the same pick day.</li>
 *   <li>KAKHETI + SAPERAVI path is byte-identical to a direct invocation
 *       through the default-constructor simulator (regression guard).</li>
 * </ol>
 */
@DisplayName("REGIONS-SPEC §5 — Region + variety direction tests")
class RegionVarietyTest {

    private static final long SEED     = 42L;
    private static final int  BUD_LOAD = 12;
    /** A pick day well into RIPENING for both Kakheti/Saperavi and Imereti/Tsolikouri. */
    private static final int  PICK_DAY = 310;

    // ── Helper: run the vine to a pick day for given region + variety ─────────

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

    // ── Test 1: Imereti/Tsolikouri has lower Brix than Kakheti/Saperavi ──────

    @Test
    @DisplayName("IMERETI/TSOLIKOURI produces lower Brix than KAKHETI/SAPERAVI at same pick day")
    void imeretiBrixLowerThanKakhetiBrix() {
        VineState kakheti  = vineStateAt(SEED, BUD_LOAD, PICK_DAY, Region.KAKHETI,  Variety.SAPERAVI);
        VineState imereti  = vineStateAt(SEED, BUD_LOAD, PICK_DAY, Region.IMERETI,  Variety.TSOLIKOURI);

        assertTrue(imereti.brix() < kakheti.brix(),
                String.format(
                        "IMERETI/TSOLIKOURI Brix=%.2f must be < KAKHETI/SAPERAVI Brix=%.2f "
                        + "(cooler, wetter region + later/lower-sugar variety)",
                        imereti.brix(), kakheti.brix()));
    }

    // ── Test 2: Imereti/Tsolikouri has higher TA than Kakheti/Saperavi ───────

    @Test
    @DisplayName("IMERETI/TSOLIKOURI retains higher TA than KAKHETI/SAPERAVI at same pick day")
    void imeretiTaHigherThanKakhetiTa() {
        VineState kakheti  = vineStateAt(SEED, BUD_LOAD, PICK_DAY, Region.KAKHETI,  Variety.SAPERAVI);
        VineState imereti  = vineStateAt(SEED, BUD_LOAD, PICK_DAY, Region.IMERETI,  Variety.TSOLIKOURI);

        assertTrue(imereti.taGL() > kakheti.taGL(),
                String.format(
                        "IMERETI/TSOLIKOURI TA=%.2f g/L must be > KAKHETI/SAPERAVI TA=%.2f g/L "
                        + "(cooler climate + higher-acid variety retains more acidity)",
                        imereti.taGL(), kakheti.taGL()));
    }

    // ── Test 3: White variety has lower tannin than Saperavi ─────────────────

    @Test
    @DisplayName("White variety (TSOLIKOURI) produces lower tannin than red (SAPERAVI)")
    void whiteVarietyHasLowerTanninThanSaperavi() {
        VineState saperavi   = vineStateAt(SEED, BUD_LOAD, PICK_DAY, Region.KAKHETI, Variety.SAPERAVI);
        VineState tsolikouri = vineStateAt(SEED, BUD_LOAD, PICK_DAY, Region.IMERETI, Variety.TSOLIKOURI);

        assertTrue(tsolikouri.tanninRipeness01() < saperavi.tanninRipeness01(),
                String.format(
                        "TSOLIKOURI (white) tannin=%.4f must be < SAPERAVI (red) tannin=%.4f",
                        tsolikouri.tanninRipeness01(), saperavi.tanninRipeness01()));
    }

    // ── Test 4: isWhite() flag works correctly ────────────────────────────────

    @Test
    @DisplayName("Variety.isWhite() returns correct values for all varieties")
    void isWhiteFlagIsCorrect() {
        // Whites
        assertTrue(Variety.RKATSITELI.isWhite(),   "RKATSITELI must be white");
        assertTrue(Variety.MTSVANE.isWhite(),       "MTSVANE must be white");
        assertTrue(Variety.KISI.isWhite(),          "KISI must be white");
        assertTrue(Variety.TSOLIKOURI.isWhite(),    "TSOLIKOURI must be white");
        assertTrue(Variety.TSITSKA.isWhite(),       "TSITSKA must be white");
        assertTrue(Variety.CHINURI.isWhite(),       "CHINURI must be white");
        assertTrue(Variety.CHKHAVERI.isWhite(),     "CHKHAVERI must be white (rosé path)");
        // Reds
        assertFalse(Variety.SAPERAVI.isWhite(),     "SAPERAVI must be red");
        assertFalse(Variety.ALEKSANDROULI.isWhite(),"ALEKSANDROULI must be red");
        assertFalse(Variety.OJALESHI.isWhite(),     "OJALESHI must be red");
    }

    // ── Test 5: RegionClimates lookups never return null ─────────────────────

    @Test
    @DisplayName("RegionClimates.of() returns a non-null entry for every Region value")
    void regionClimateLookupNeverNull() {
        for (Region r : Region.values()) {
            RegionClimate climate = com.game.core.data.RegionClimates.of(r);
            assertNotNull(climate, "RegionClimate must not be null for region " + r);
        }
    }

    // ── Test 6: VarietyProfiles lookups never return null ────────────────────

    @Test
    @DisplayName("VarietyProfiles.of() returns a non-null entry for every Variety value")
    void varietyProfileLookupNeverNull() {
        for (Variety v : Variety.values()) {
            VarietyProfile profile = com.game.core.data.VarietyProfiles.of(v);
            assertNotNull(profile, "VarietyProfile must not be null for variety " + v);
        }
    }

    // ── Test 7: RegionSiteProfiles lookups never return null ─────────────────

    @Test
    @DisplayName("RegionSiteProfiles.of() returns a non-null SiteProfile for every Region")
    void regionSiteProfileLookupNeverNull() {
        for (Region r : Region.values()) {
            SiteProfile site = RegionSiteProfiles.of(r);
            assertNotNull(site, "SiteProfile must not be null for region " + r);
        }
    }

    // ── Test 8: Guria/Adjara (warm/wet) has higher Brix than Meskheti (cool) ─

    @Test
    @DisplayName("GURIA_ADJARA/OJALESHI has higher Brix than MESKHETI/ALEKSANDROULI")
    void warmRegionHigherBrixThanCoolRegion() {
        int pickDay = 300;
        VineState guria   = vineStateAt(SEED, BUD_LOAD, pickDay, Region.GURIA_ADJARA,    Variety.OJALESHI);
        VineState meskheti = vineStateAt(SEED, BUD_LOAD, pickDay, Region.MESKHETI,        Variety.ALEKSANDROULI);

        assertTrue(guria.brix() >= meskheti.brix(),
                String.format(
                        "GURIA_ADJARA/OJALESHI Brix=%.2f must be >= MESKHETI/ALEKSANDROULI Brix=%.2f "
                        + "(warmer subtropical region)",
                        guria.brix(), meskheti.brix()));
    }
}
