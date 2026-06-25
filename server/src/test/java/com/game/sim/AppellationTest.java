package com.game.sim;

import com.game.core.data.*;
import com.game.core.time.RngStreams;
import com.game.core.weather.KakhetiWeatherModel;
import com.game.sim.cellar.CellarResult;
import com.game.sim.cellar.KineticFermenter;
import com.game.sim.ops.Harvest;
import com.game.sim.region.RegionSiteProfiles;
import com.game.sim.resolve.AppellationRules;
import com.game.sim.resolve.Resolver;
import com.game.sim.soil.SiteSuitability;
import com.game.sim.vine.KakhetiVineSimulator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Appellation + terroir-fit tests (FEAT/appellation lane).
 *
 * <h2>Hard constraint</h2>
 * The KAKHETI + SAPERAVI path must be unchanged (same quality direction,
 * appellation = true, style = RED).  No existing test is weakened.
 *
 * <h2>Assertions (direction only — no magic numbers)</h2>
 * <ol>
 *   <li>SAPERAVI in KAKHETI → appellationOk = true, style = RED</li>
 *   <li>TSOLIKOURI in IMERETI → appellationOk = true, style = WHITE</li>
 *   <li>Off-terroir SAPERAVI in MESKHETI → appellationOk = false AND lower
 *       quality than the same variety in KAKHETI (terroir penalty)</li>
 *   <li>Off-terroir TSOLIKOURI in GURIA_ADJARA → appellationOk = false AND lower
 *       quality than TSOLIKOURI in IMERETI</li>
 *   <li>{@link AppellationRules#terroirFit} returns 1.0 for KAKHETI/SAPERAVI</li>
 *   <li>White variety (TSOLIKOURI) via RED ferment method → WHITE style</li>
 *   <li>Red variety (SAPERAVI) via RED ferment method → RED style (unchanged)</li>
 *   <li>Any variety via KAKHETIAN method → AMBER style</li>
 * </ol>
 */
@DisplayName("Appellation + terroir-fit — region+variety aware (FEAT lane)")
class AppellationTest {

    private static final long SEED     = 42L;
    private static final int  BUD_LOAD = 12;
    private static final int  PICK_DAY = 300;

    // ── Pipeline helper ──────────────────────────────────────────────────────

    private WineLot runPipeline(Region region, Variety variety, FermentMethod method) {
        KakhetiWeatherModel weather = new KakhetiWeatherModel();
        KakhetiVineSimulator vine   = new KakhetiVineSimulator(variety);
        KineticFermenter fermenter  = new KineticFermenter();

        RngStreams rng = new RngStreams(SEED);
        List<DailyWeather> days = weather.generateYear(rng, region, 1);
        Vintage vintage         = weather.rollVintage(rng, region, 1, days);

        SiteProfile site        = RegionSiteProfiles.of(region);
        PruningDecision pruning = new PruningDecision(BUD_LOAD);
        double suitability      = SiteSuitability.score(variety, site);

        VineState state = new VineState(
                PhenoStage.DORMANCY, 0.0, 1.0, 0.0, 0.0, 8.0, 3.0, 200.0, 0.0);
        VineState atPick = null;

        for (DailyWeather today : days) {
            state = vine.tick(state, today, site, suitability, pruning);
            if (today.dayOfYear() == PICK_DAY) {
                atPick = state;
            }
        }
        if (atPick == null) atPick = state;

        double volumeL    = Harvest.yieldToVolume(atPick.potentialYieldKg());
        MustProfile must  = Harvest.pick(atPick, volumeL, vintage.year());
        CellarResult cellar = fermenter.ferment(must, method, 25.0, 0.80, rng);

        return Resolver.resolve(variety, method, must, cellar, vintage, suitability, "Test");
    }

    // ── Test 1: SAPERAVI in KAKHETI — appellation true, style RED ────────────

    @Test
    @DisplayName("KAKHETI/SAPERAVI → appellationOk=true, style=RED (Phase-0 path unchanged)")
    void kakhetiSaperavi_appellationTrue_styleRed() {
        WineLot lot = runPipeline(Region.KAKHETI, Variety.SAPERAVI, FermentMethod.RED);

        assertTrue(lot.appellationOk(),
                "KAKHETI + SAPERAVI must satisfy appellation rules");
        assertEquals(WineStyle.RED, lot.style(),
                "KAKHETI + SAPERAVI + RED method must produce RED style");
    }

    // ── Test 2: TSOLIKOURI in IMERETI — appellation true, style WHITE ─────────

    @Test
    @DisplayName("IMERETI/TSOLIKOURI → appellationOk=true, style=WHITE")
    void imeretiTsolikouri_appellationTrue_styleWhite() {
        WineLot lot = runPipeline(Region.IMERETI, Variety.TSOLIKOURI, FermentMethod.RED);

        assertTrue(lot.appellationOk(),
                "IMERETI + TSOLIKOURI must satisfy appellation rules");
        assertEquals(WineStyle.WHITE, lot.style(),
                "White variety TSOLIKOURI with RED fermentation method must produce WHITE style");
    }

    // ── Test 3: Off-terroir SAPERAVI in MESKHETI — appellation false + lower Q ─

    @Test
    @DisplayName("Off-terroir MESKHETI/SAPERAVI → appellationOk=false AND lower quality than KAKHETI")
    void meskhetiSaperavi_offTerroirPenalty() {
        WineLot inKakheti  = runPipeline(Region.KAKHETI,  Variety.SAPERAVI, FermentMethod.RED);
        WineLot inMeskheti = runPipeline(Region.MESKHETI, Variety.SAPERAVI, FermentMethod.RED);

        assertFalse(inMeskheti.appellationOk(),
                "MESKHETI + SAPERAVI must NOT satisfy appellation rules (off-terroir)");
        assertTrue(inKakheti.quality() > inMeskheti.quality(),
                String.format(
                        "KAKHETI/SAPERAVI quality=%.1f must exceed MESKHETI/SAPERAVI quality=%.1f "
                        + "(terroir penalty for heat-loving variety in cool short-season region)",
                        inKakheti.quality(), inMeskheti.quality()));
    }

    // ── Test 4: Off-terroir TSOLIKOURI in GURIA_ADJARA — lower Q than IMERETI ─

    @Test
    @DisplayName("Off-terroir GURIA_ADJARA/TSOLIKOURI → appellationOk=false AND lower quality than IMERETI")
    void guriaAdjara_tsolikouri_offTerroirPenalty() {
        WineLot inImereti      = runPipeline(Region.IMERETI,     Variety.TSOLIKOURI, FermentMethod.RED);
        WineLot inGuriaAdjara  = runPipeline(Region.GURIA_ADJARA, Variety.TSOLIKOURI, FermentMethod.RED);

        assertFalse(inGuriaAdjara.appellationOk(),
                "GURIA_ADJARA + TSOLIKOURI must NOT satisfy appellation rules");
        assertTrue(inImereti.quality() > inGuriaAdjara.quality(),
                String.format(
                        "IMERETI/TSOLIKOURI quality=%.1f must exceed GURIA_ADJARA/TSOLIKOURI quality=%.1f "
                        + "(terroir penalty for cool-climate variety in subtropical region)",
                        inImereti.quality(), inGuriaAdjara.quality()));
    }

    // ── Test 5: KAKHETI/SAPERAVI terroir fit is exactly 1.0 ─────────────────

    @Test
    @DisplayName("AppellationRules.terroirFit(KAKHETI, SAPERAVI) = 1.0 (Phase-0 baseline preserved)")
    void kakhetiSaperavi_terroirFitIsOne() {
        double fit = AppellationRules.terroirFit(Region.KAKHETI, Variety.SAPERAVI);
        assertEquals(AppellationRules.FIT_SIGNATURE, fit, 1e-9,
                "KAKHETI/SAPERAVI terroir fit must be exactly 1.0 (FIT_SIGNATURE)");
    }

    // ── Test 6: White variety via RED method → WHITE style ───────────────────

    @Test
    @DisplayName("shouldOverrideToWhite: white variety + RED method → true")
    void whiteVariety_redMethod_overridesToWhite() {
        assertTrue(AppellationRules.shouldOverrideToWhite(Variety.TSOLIKOURI, FermentMethod.RED));
        assertTrue(AppellationRules.shouldOverrideToWhite(Variety.RKATSITELI, FermentMethod.RED));
        assertTrue(AppellationRules.shouldOverrideToWhite(Variety.TSITSKA,    FermentMethod.RED));
        assertTrue(AppellationRules.shouldOverrideToWhite(Variety.CHINURI,    FermentMethod.RED));
        assertTrue(AppellationRules.shouldOverrideToWhite(Variety.CHKHAVERI,  FermentMethod.RED));
    }

    // ── Test 7: Red variety via RED method → NOT overridden (RED stays RED) ──

    @Test
    @DisplayName("shouldOverrideToWhite: red variety + RED method → false (SAPERAVI stays RED)")
    void redVariety_redMethod_noOverride() {
        assertFalse(AppellationRules.shouldOverrideToWhite(Variety.SAPERAVI,      FermentMethod.RED));
        assertFalse(AppellationRules.shouldOverrideToWhite(Variety.ALEKSANDROULI, FermentMethod.RED));
        assertFalse(AppellationRules.shouldOverrideToWhite(Variety.OJALESHI,      FermentMethod.RED));
    }

    // ── Test 8: Any variety via KAKHETIAN → AMBER (existing rule unchanged) ──

    @Test
    @DisplayName("KAKHETIAN method → AMBER style for both red and white varieties")
    void kakhetianMethod_alwaysAmber() {
        WineLot sapAmber  = runPipeline(Region.KAKHETI, Variety.SAPERAVI,   FermentMethod.KAKHETIAN);
        WineLot rkaAmber  = runPipeline(Region.KAKHETI, Variety.RKATSITELI, FermentMethod.KAKHETIAN);

        assertEquals(WineStyle.AMBER, sapAmber.style(),
                "SAPERAVI + KAKHETIAN method must produce AMBER (Qvevri skin contact)");
        assertEquals(WineStyle.AMBER, rkaAmber.style(),
                "RKATSITELI + KAKHETIAN method must produce AMBER");
    }

    // ── Test 9: Appellation rules table — spot checks ────────────────────────

    @Test
    @DisplayName("Appellation rules spot checks — correct PDO mappings")
    void appellationRules_spotChecks() {
        // Correct home-region pairings → true
        assertTrue(AppellationRules.appellationOk(Region.KAKHETI,         Variety.SAPERAVI,      FermentMethod.RED));
        assertTrue(AppellationRules.appellationOk(Region.KAKHETI,         Variety.RKATSITELI,    FermentMethod.RED));
        assertTrue(AppellationRules.appellationOk(Region.KARTLI,          Variety.RKATSITELI,    FermentMethod.RED));
        assertTrue(AppellationRules.appellationOk(Region.IMERETI,         Variety.TSOLIKOURI,    FermentMethod.RED));
        assertTrue(AppellationRules.appellationOk(Region.IMERETI,         Variety.TSITSKA,       FermentMethod.RED));
        assertTrue(AppellationRules.appellationOk(Region.RACHA_LECHKHUMI, Variety.ALEKSANDROULI, FermentMethod.RED));
        assertTrue(AppellationRules.appellationOk(Region.SAMEGRELO,       Variety.OJALESHI,      FermentMethod.RED));
        assertTrue(AppellationRules.appellationOk(Region.GURIA_ADJARA,    Variety.CHKHAVERI,     FermentMethod.RED));
        assertTrue(AppellationRules.appellationOk(Region.KARTLI,          Variety.CHINURI,       FermentMethod.RED));

        // Off-appellation cross-plantings → false
        assertFalse(AppellationRules.appellationOk(Region.IMERETI,  Variety.SAPERAVI,   FermentMethod.RED));
        assertFalse(AppellationRules.appellationOk(Region.KAKHETI,  Variety.TSOLIKOURI, FermentMethod.RED));
        assertFalse(AppellationRules.appellationOk(Region.MESKHETI, Variety.SAPERAVI,   FermentMethod.RED));
        assertFalse(AppellationRules.appellationOk(Region.SAMEGRELO,Variety.CHINURI,    FermentMethod.RED));
    }
}
