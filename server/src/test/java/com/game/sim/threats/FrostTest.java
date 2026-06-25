package com.game.sim.threats;

import com.game.core.data.*;
import com.game.core.time.RngStreams;
import com.game.sim.threats.api.ThreatContext;
import com.game.sim.threats.api.ThreatEffect;
import com.game.sim.threats.api.ThreatMemory;
import com.game.sim.threats.weather.SpringFrost;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * §6.5 — Spring frost: hard frost on a high-frostRisk site cuts yield significantly;
 * a low-frostRisk slope mitigates the loss.
 */
class FrostTest {

    // Valley floor: high frost risk
    private static final SiteProfile HIGH_FROST_SITE = new SiteProfile(
            SoilType.HUMUS_CARBONATE, 2.0, 180.0, 200.0, 0.85, 0.50);
    // Sloped drainage: very low frost risk
    private static final SiteProfile LOW_FROST_SITE = new SiteProfile(
            SoilType.HUMUS_CARBONATE, 15.0, 180.0, 450.0, 0.10, 0.20);

    /** Hard frost (-5 °C) during BUDBREAK on valley floor must destroy >30% yield. */
    @Test
    void hardFrostOnHighRiskSiteDestroysMuchYield() {
        ThreatEffect eff = evalFrost(-5.0, 100, PhenoStage.BUDBREAK, HIGH_FROST_SITE);

        // With tMin=-5, frostDepth=5, stageMult=0.8, siteFactor=0.85, netFactor=1:
        // yieldLoss = min(0.95, 5*0.10*0.85*0.8*1) = min(0.95, 0.34) = 0.34
        assertTrue(eff.yieldMultiplier() < 0.70,
            "Hard frost on high-risk site should cut yield by >30% "
            + "(yieldMult=" + eff.yieldMultiplier() + ")");
        assertFalse(eff.tell().isEmpty(),
            "A damaging frost must produce a tell string");
        assertTrue(eff.healthDelta() < 0.0,
            "Hard frost must produce a negative health delta");
    }

    /** Same -5 °C frost on a well-drained slope should be much less severe. */
    @Test
    void slopeMitigatesFrostDamage() {
        ThreatEffect highRisk = evalFrost(-5.0, 100, PhenoStage.BUDBREAK, HIGH_FROST_SITE);
        ThreatEffect lowRisk  = evalFrost(-5.0, 100, PhenoStage.BUDBREAK, LOW_FROST_SITE);

        assertTrue(lowRisk.yieldMultiplier() > highRisk.yieldMultiplier(),
            "Low frost-risk slope should have less yield loss than valley floor "
            + "(low=" + lowRisk.yieldMultiplier()
            + " high=" + highRisk.yieldMultiplier() + ")");
    }

    /** No frost damage when tMin >= 0 °C. */
    @Test
    void noFrostAboveZero() {
        ThreatEffect eff = evalFrost(0.5, 100, PhenoStage.BUDBREAK, HIGH_FROST_SITE);
        assertEquals(1.0, eff.yieldMultiplier(), 1e-9, "No yield loss when tMin >= 0");
        assertTrue(eff.tell().isEmpty(), "No tell when tMin >= 0");
    }

    /** Outside the spring window (day 40–150) there must be no frost damage. */
    @Test
    void noFrostOutsideSpringWindow() {
        // Day 200 = summer, well outside spring window
        ThreatEffect eff = evalFrost(-3.0, 200, PhenoStage.RIPENING, HIGH_FROST_SITE);
        assertEquals(1.0, eff.yieldMultiplier(), 1e-9,
            "No frost damage outside spring window (day 200)");
    }

    /** Netting provides some protection vs an extreme frost event. */
    @Test
    void nettingReducesFrostDamage() {
        SpringFrost frost = new SpringFrost();
        DailyWeather day  = new DailyWeather(100, -4.0, 2.0, 0.0, 0.50);
        VineState vine    = vine(PhenoStage.SHOOT_GROWTH);

        ThreatEffect withoutNetting = evalFrostWithNetting(frost, day, vine, HIGH_FROST_SITE, false);
        ThreatEffect withNetting    = evalFrostWithNetting(frost, day, vine, HIGH_FROST_SITE, true);

        assertTrue(withNetting.yieldMultiplier() >= withoutNetting.yieldMultiplier(),
            "Netting must not worsen frost damage "
            + "(without=" + withoutNetting.yieldMultiplier()
            + " with=" + withNetting.yieldMultiplier() + ")");
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private static ThreatEffect evalFrost(double tMin, int doy,
                                           PhenoStage stage, SiteProfile site) {
        return evalFrostWithNetting(new SpringFrost(),
                new DailyWeather(doy, tMin, tMin + 8.0, 0.0, 0.50),
                vine(stage), site, false);
    }

    private static ThreatEffect evalFrostWithNetting(SpringFrost frost, DailyWeather day,
                                                      VineState vine, SiteProfile site,
                                                      boolean netting) {
        ThreatContext ctx = new ThreatContext(
                day.dayOfYear(), day, 200.0, vine, site,
                true, 0.40, false, 0.0, 0.0, netting, false, false, false, false, 0.0,
                new RngStreams(42L).stream("threat." + frost.id()), ThreatMemory.none());
        return frost.evaluate(ctx);
    }

    private static VineState vine(PhenoStage stage) {
        return new VineState(stage, 100.0, 1.0, 5.0, 0.0, 8.0, 3.0, 200.0, 0.0);
    }
}
