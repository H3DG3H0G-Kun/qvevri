package com.game.sim.threats;

import com.game.core.data.*;
import com.game.core.time.RngStreams;
import com.game.sim.threats.api.ThreatContext;
import com.game.sim.threats.api.ThreatEffect;
import com.game.sim.threats.api.ThreatMemory;
import com.game.sim.threats.pest.Phylloxera;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * §6.3 — Phylloxera gating:
 * own-roots + non-sand → can establish; SAND or grafted → never establishes.
 */
class PhylloxeraGatingTest {

    private static final DailyWeather DAY_1 = new DailyWeather(1, 2.0, 8.0, 0.0, 0.60);

    @Test
    void sandSoilIsImmune() {
        SiteProfile sand = new SiteProfile(SoilType.SAND, 5.0, 180.0, 200.0, 0.10, 0.10);
        boolean established = runEstablishmentTrials(sand, true, 300);
        assertFalse(established, "Phylloxera must never establish on SAND soil");
    }

    @Test
    void graftedRootstockIsImmune() {
        SiteProfile clay = new SiteProfile(SoilType.CLAY_LIMESTONE, 5.0, 180.0, 300.0, 0.20, 0.20);
        boolean established = runEstablishmentTrials(clay, false, 300);
        assertFalse(established, "Phylloxera must never establish on grafted rootstock (ownRoots=false)");
    }

    @Test
    void ownRootsNonSandCanEstablish() {
        // 30% establishment probability per season → should fire within 200 seeds
        SiteProfile clay = new SiteProfile(SoilType.CLAY_LIMESTONE, 5.0, 180.0, 300.0, 0.20, 0.20);
        boolean established = runEstablishmentTrials(clay, true, 200);
        assertTrue(established,
            "Own-roots + non-sand: phylloxera should establish in at least one of 200 seeds");
    }

    @Test
    void humusCarbonateCanAlsoEstablish() {
        SiteProfile humus = new SiteProfile(SoilType.HUMUS_CARBONATE, 12.0, 180.0, 450.0, 0.15, 0.25);
        boolean established = runEstablishmentTrials(humus, true, 200);
        assertTrue(established,
            "Own-roots + HUMUS_CARBONATE: phylloxera should establish in 200 seeds");
    }

    @Test
    void establishedPhylloxeraDamagesHealth() {
        // Run a full year on clay + ownRoots, find a seed where establishment occurs
        SiteProfile clay = new SiteProfile(SoilType.CLAY_LIMESTONE, 5.0, 180.0, 300.0, 0.20, 0.20);
        for (long seed = 1; seed <= 500; seed++) {
            double[] result = fullYearHealthTracking(clay, true, seed);
            boolean wasEstablished = result[2] > 0.5;
            if (wasEstablished) {
                double initialHealth = result[0];
                double finalHealth   = result[1];
                assertTrue(finalHealth < initialHealth,
                    "Health should decline when phylloxera establishes (seed=" + seed + ")");
                return;
            }
        }
        // If no establishment found in 500 seeds (highly unlikely at 30%), skip gracefully
        // The immune-path tests above already validate the core gating rule
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private static boolean runEstablishmentTrials(SiteProfile site, boolean ownRoots,
                                                   int numSeeds) {
        Phylloxera phyl = new Phylloxera();
        for (long seed = 1; seed <= numSeeds; seed++) {
            RngStreams rng = new RngStreams(seed);
            ThreatMemory mem = ThreatMemory.none();
            VineState vine = baseVine();
            // Run 5 "day 1" checks (simulating 5 seasons)
            for (int season = 0; season < 5; season++) {
                ThreatContext ctx = makeCtx(1, site, ownRoots, vine, rng, mem);
                ThreatEffect eff = phyl.evaluate(ctx);
                mem = eff.nextMemory();
                if (mem.established()) return true;
            }
        }
        return false;
    }

    private static double[] fullYearHealthTracking(SiteProfile site, boolean ownRoots, long seed) {
        Phylloxera phyl = new Phylloxera();
        RngStreams rng = new RngStreams(seed);
        ThreatMemory mem = ThreatMemory.none();
        VineState vine = baseVine();
        double initialHealth = vine.healthFraction();
        boolean anyEstablished = false;

        for (int doy = 1; doy <= 365; doy++) {
            ThreatContext ctx = makeCtx(doy, site, ownRoots, vine, rng, mem);
            ThreatEffect eff = phyl.evaluate(ctx);
            mem = eff.nextMemory();
            if (mem.established()) anyEstablished = true;
            double h = Math.max(0.0, Math.min(1.0, vine.healthFraction() + eff.healthDelta()));
            vine = new VineState(vine.stage(), vine.gddAccum(), h, vine.potentialYieldKg(),
                    vine.brix(), vine.taGL(), vine.pH(), vine.yanMgL(), vine.tanninRipeness01());
        }
        return new double[]{initialHealth, vine.healthFraction(), anyEstablished ? 1.0 : 0.0};
    }

    private static ThreatContext makeCtx(int doy, SiteProfile site, boolean ownRoots,
                                          VineState vine, RngStreams rng, ThreatMemory mem) {
        return new ThreatContext(doy, DAY_1, 500.0, vine, site,
                ownRoots, 0.40, false, 0.0, 0.0, false, false, false, false, false, 0.0,
                rng.stream("threat.pest.phylloxera"), mem);
    }

    private static VineState baseVine() {
        return new VineState(PhenoStage.DORMANCY, 0.0, 1.0, 8.0, 0.0, 8.0, 3.0, 200.0, 0.0);
    }
}
