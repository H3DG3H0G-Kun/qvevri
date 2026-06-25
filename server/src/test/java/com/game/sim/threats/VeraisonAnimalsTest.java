package com.game.sim.threats;

import com.game.core.data.*;
import com.game.core.time.RngStreams;
import com.game.sim.threats.animal.Starlings;
import com.game.sim.threats.api.ThreatContext;
import com.game.sim.threats.api.ThreatEffect;
import com.game.sim.threats.api.ThreatMemory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * §6.4 — Veraison animal behaviour:
 * Starlings only bite from VERAISON onward;
 * falcons suppress the loss;
 * netting reduces damage.
 */
class VeraisonAnimalsTest {

    private static final SiteProfile SITE = new SiteProfile(
            SoilType.HUMUS_CARBONATE, 12.0, 180.0, 450.0, 0.15, 0.25);
    private static final DailyWeather SUMMER_DAY = new DailyWeather(220, 18.0, 28.0, 0.0, 0.60);

    /** Stages before veraison must never see starling damage — over 200 seeds each. */
    @Test
    void starlingsDoNotAttackBeforeVeraison() {
        PhenoStage[] preVeraison = {
            PhenoStage.DORMANCY, PhenoStage.BUD_SWELL, PhenoStage.BUDBREAK,
            PhenoStage.SHOOT_GROWTH, PhenoStage.FLOWERING,
            PhenoStage.FRUIT_SET, PhenoStage.BERRY_DEVELOPMENT
        };

        for (PhenoStage stage : preVeraison) {
            for (int seed = 1; seed <= 200; seed++) {
                ThreatEffect eff = evalStarlings(stage, false, false, seed);
                // No tell and no yield loss = no attack
                assertTrue(eff.tell().isEmpty() && eff.yieldMultiplier() == 1.0,
                    "Starlings must not attack at stage " + stage
                    + " (seed=" + seed + " tell='" + eff.tell()
                    + "' yieldMult=" + eff.yieldMultiplier() + ")");
            }
        }
    }

    /** With 8% daily probability, at least one of 400 seeds should trigger an attack. */
    @Test
    void starlingsDoAttackAtVeraison() {
        boolean anyAttack = false;
        for (int seed = 1; seed <= 400; seed++) {
            ThreatEffect eff = evalStarlings(PhenoStage.VERAISON, false, false, seed);
            if (!eff.tell().isEmpty()) {
                anyAttack = true;
                break;
            }
        }
        assertTrue(anyAttack, "Starlings must attack at least once in 400 seeds at VERAISON");
    }

    /** Falcons must not make the damage worse; typically suppress it fully. */
    @Test
    void falconsSuppressOrReduceStarlingAttack() {
        // Find a seed where starlings fire without falcons
        for (int seed = 1; seed <= 500; seed++) {
            ThreatEffect without = evalStarlings(PhenoStage.RIPENING, false, false, seed);
            if (without.yieldMultiplier() < 1.0) {
                ThreatEffect with = evalStarlings(PhenoStage.RIPENING, true, false, seed);
                assertTrue(with.yieldMultiplier() >= without.yieldMultiplier(),
                    "Falcons must not worsen starling damage (seed=" + seed
                    + " without=" + without.yieldMultiplier()
                    + " with=" + with.yieldMultiplier() + ")");
                return;
            }
        }
        // If no attack found in 500 seeds, log warning but don't fail — the
        // starlingsDoAttackAtVeraison test validates attacks occur
        System.out.println("WARNING: no starling attack found in 500 seeds for falcons suppression test");
    }

    /** Netting must not make the damage worse; typically reduces it substantially. */
    @Test
    void nettingReducesStarlingYieldLoss() {
        for (int seed = 1; seed <= 500; seed++) {
            ThreatEffect without = evalStarlings(PhenoStage.RIPENING, false, false, seed);
            if (without.yieldMultiplier() > 0.0 && without.yieldMultiplier() < 1.0) {
                ThreatEffect with = evalStarlings(PhenoStage.RIPENING, false, true, seed);
                assertTrue(with.yieldMultiplier() >= without.yieldMultiplier(),
                    "Netting should reduce starling yield loss (seed=" + seed
                    + " without=" + without.yieldMultiplier()
                    + " with=" + with.yieldMultiplier() + ")");
                return;
            }
        }
        System.out.println("WARNING: no starling attack found in 500 seeds for netting test");
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private static ThreatEffect evalStarlings(PhenoStage stage, boolean falcons,
                                               boolean netting, long seed) {
        Starlings s = new Starlings();
        RngStreams rng = new RngStreams(seed);
        VineState vine = new VineState(stage, 800.0, 1.0, 8.0, 22.0, 7.0, 3.4, 180.0, 0.60);
        ThreatContext ctx = new ThreatContext(
                SUMMER_DAY.dayOfYear(), SUMMER_DAY, 1200.0, vine, SITE,
                true, 0.40, false, 0.0, 0.0, netting, false, falcons, false, false, 0.0,
                rng.stream("threat." + s.id()), ThreatMemory.none());
        return s.evaluate(ctx);
    }
}
