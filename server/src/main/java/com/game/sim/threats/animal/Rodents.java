package com.game.sim.threats.animal;

import com.game.core.data.Fault;
import com.game.core.data.PhenoStage;
import com.game.sim.threats.api.ThreatCategory;
import com.game.sim.threats.api.ThreatContext;
import com.game.sim.threats.api.ThreatEffect;
import com.game.sim.threats.api.ThreatMemory;
import com.game.sim.threats.api.ThreatSource;

/**
 * Rodents — field mice (<em>Apodemus sylvaticus</em>), voles (<em>Microtus
 * arvalis</em>), and rats.
 *
 * <p>Rodents gnaw at vine bark and graft unions particularly under winter
 * mulch and cover crop where they nest. Their peak damage window is during
 * DORMANCY and early spring (BUD_SWELL/BUDBREAK) when they shelter under
 * cover and gnaw bark of the lower trunk and roots. They also consume fallen
 * fruit and can damage ripening clusters near the ground in RIPENING.
 *
 * <p>The gnawing damage is insidious: girdling the trunk disrupts sap flow
 * and can kill or severely weaken a vine over a winter.
 *
 * <p>Suppressor: {@code cats} — barn cats and barn owls (proxied by the
 * {@code cats} flag) are the primary rodent suppressors in a vineyard.
 *
 * <p>Memory: {@code level} = rodent pressure / damage accumulation 0..1;
 * {@code ticksActive} = active gnawing days.
 */
public final class Rodents implements ThreatSource {

    // ---- constants -------------------------------------------------------

    /** Peak active stages (dormancy / early spring gnawing). */
    private static final PhenoStage WINTER_END = PhenoStage.BUDBREAK;

    /** Stage after which rodents shift to fruit/fallen fruit. */
    private static final PhenoStage FRUIT_STAGE = PhenoStage.RIPENING;

    /** Daily base probability of active gnawing in winter/spring. */
    private static final double BASE_GNAW_PROBABILITY_WINTER = 0.18;

    /** Daily base probability of fruit targeting in ripening. */
    private static final double BASE_GNAW_PROBABILITY_RIPENING = 0.10;

    /** Health drain per gnawing day (bark/trunk damage). */
    private static final double HEALTH_DRAIN_WINTER = 0.005;

    /** Health drain per active day in ripening (minor). */
    private static final double HEALTH_DRAIN_RIPENING = 0.002;

    /** Yield fraction lost per ripening-stage event (fallen/gnawed fruit). */
    private static final double YIELD_FRACTION_RIPENING = 0.03;

    /** Quality penalty per event. */
    private static final double QUALITY_PENALTY_PER_EVENT = 0.002;

    // ---- ThreatSource impl -----------------------------------------------

    @Override
    public String id() {
        return "animal.rodents";
    }

    @Override
    public ThreatCategory category() {
        return ThreatCategory.ANIMAL;
    }

    @Override
    public ThreatEffect evaluate(ThreatContext ctx) {
        PhenoStage stage = ctx.vine().stage();
        int stageOrd = stage.ordinal();

        // Active in winter dormancy through budbreak, and again in ripening
        boolean winterWindow = stageOrd <= WINTER_END.ordinal();
        boolean ripeningWindow = stage == FRUIT_STAGE;

        if (!winterWindow && !ripeningWindow) {
            return ThreatEffect.none(ctx.memory());
        }

        ThreatMemory mem = ctx.memory();
        int ticksActive = mem.ticksActive() + 1;

        // Cats (and barn owls) suppress rodents
        if (ctx.cats()) {
            ThreatMemory next = new ThreatMemory(mem.level(), 0.0, ticksActive,
                    mem.yearsActive(), false);
            return ThreatEffect.none(next);
        }

        double gnawProb = winterWindow ? BASE_GNAW_PROBABILITY_WINTER : BASE_GNAW_PROBABILITY_RIPENING;
        boolean active = ctx.rng().nextDouble() < gnawProb;

        if (!active) {
            ThreatMemory next = new ThreatMemory(mem.level(), 0.0, ticksActive,
                    mem.yearsActive(), false);
            return ThreatEffect.none(next);
        }

        // Active gnawing event
        double healthDrain;
        double yieldMult;
        double qualityPenalty;
        String tell;

        if (winterWindow) {
            healthDrain = HEALTH_DRAIN_WINTER;
            yieldMult = 1.0;
            qualityPenalty = QUALITY_PENALTY_PER_EVENT;
            tell = "rodent gnaw marks on trunk bark — vole runs under vine cover";
        } else {
            healthDrain = HEALTH_DRAIN_RIPENING;
            yieldMult = 1.0 - YIELD_FRACTION_RIPENING;
            qualityPenalty = QUALITY_PENALTY_PER_EVENT;
            tell = "mouse damage to low-hanging clusters — gnawed berries on the ground";
        }

        double newLevel = Math.min(1.0, mem.level() + 0.015);
        ThreatMemory next = new ThreatMemory(newLevel, 0.0, ticksActive,
                mem.yearsActive(), true);

        return new ThreatEffect(
                -healthDrain,
                yieldMult,
                qualityPenalty,
                Fault.NONE,
                false,
                tell,
                next);
    }
}
