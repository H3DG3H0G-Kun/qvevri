package com.game.sim.threats.animal;

import com.game.core.data.Fault;
import com.game.core.data.PhenoStage;
import com.game.sim.threats.api.ThreatCategory;
import com.game.sim.threats.api.ThreatContext;
import com.game.sim.threats.api.ThreatEffect;
import com.game.sim.threats.api.ThreatMemory;
import com.game.sim.threats.api.ThreatSource;

/**
 * Roe Deer — <em>Capreolus capreolus</em>.
 *
 * <p>Roe deer browse young shoots and leaves, and — from veraison — eat
 * ripening clusters directly from the vine. Their damage profile differs from
 * boar:
 * <ul>
 *   <li>Shoot browsing in spring (from BUDBREAK/SHOOT_GROWTH) reduces canopy
 *       and slows growth — health drain, indirect quality impact.</li>
 *   <li>Cluster browsing from VERAISON — direct yield loss.</li>
 * </ul>
 *
 * <p>They are active across a longer window than boar (spring through harvest)
 * but cause less catastrophic per-event damage. They are browsers, not rooters.
 *
 * <p>Suppressor: {@code guardDog} — a livestock guardian dog deters deer
 * effectively. Deer are wary and will avoid patrolled areas.
 *
 * <p>Memory: {@code level} = browse pressure index 0..1.
 * {@code aux} = 0 (unused).
 */
public final class RoeDeer implements ThreatSource {

    // ---- constants -------------------------------------------------------

    /** Earliest active phenostage (shoot browsing). */
    private static final PhenoStage SPRING_ONSET = PhenoStage.BUDBREAK;

    /** Stage at which cluster browsing begins (higher damage). */
    private static final PhenoStage CLUSTER_ONSET = PhenoStage.VERAISON;

    /** Probability of a browse event on any given day (spring). */
    private static final double BASE_BROWSE_PROBABILITY_SPRING = 0.08;

    /** Probability of a browse event on any given day (veraison/ripening). */
    private static final double BASE_BROWSE_PROBABILITY_RIPENING = 0.12;

    /** Health drain per spring browse event (lost shoots, reduced canopy). */
    private static final double HEALTH_DRAIN_SPRING = 0.004;

    /** Health drain per veraison/ripening browse event. */
    private static final double HEALTH_DRAIN_RIPENING = 0.005;

    /** Yield fraction removed per browse event in ripening. */
    private static final double YIELD_FRACTION_PER_BROWSE = 0.05;

    /** Quality penalty per browse event (damaged bunches, rot entry). */
    private static final double QUALITY_PENALTY_PER_BROWSE = 0.003;

    // ---- ThreatSource impl -----------------------------------------------

    @Override
    public String id() {
        return "animal.roe_deer";
    }

    @Override
    public ThreatCategory category() {
        return ThreatCategory.ANIMAL;
    }

    @Override
    public ThreatEffect evaluate(ThreatContext ctx) {
        int stageOrd = ctx.vine().stage().ordinal();
        if (stageOrd < SPRING_ONSET.ordinal()) {
            return ThreatEffect.none(ThreatMemory.none());
        }

        ThreatMemory mem = ctx.memory();
        int ticksActive = mem.ticksActive() + 1;

        // Guard dog deters deer
        if (ctx.guardDog()) {
            ThreatMemory next = new ThreatMemory(mem.level(), 0.0, ticksActive,
                    mem.yearsActive(), false);
            return ThreatEffect.none(next);
        }

        boolean inRipeningWindow = stageOrd >= CLUSTER_ONSET.ordinal();
        double browseProb = inRipeningWindow
                ? BASE_BROWSE_PROBABILITY_RIPENING
                : BASE_BROWSE_PROBABILITY_SPRING;

        boolean browse = ctx.rng().nextDouble() < browseProb;

        if (!browse) {
            ThreatMemory next = new ThreatMemory(mem.level(), 0.0, ticksActive,
                    mem.yearsActive(), false);
            return ThreatEffect.none(next);
        }

        // Browse event
        double healthDrain;
        double yieldMult;
        double qualityPenalty;
        String tell;

        if (inRipeningWindow) {
            healthDrain = HEALTH_DRAIN_RIPENING;
            yieldMult = 1.0 - YIELD_FRACTION_PER_BROWSE;
            qualityPenalty = QUALITY_PENALTY_PER_BROWSE;
            tell = "roe deer browsing clusters at dusk — fruit stripped from lower canes";
        } else {
            healthDrain = HEALTH_DRAIN_SPRING;
            yieldMult = 1.0; // no direct yield hit in spring
            qualityPenalty = QUALITY_PENALTY_PER_BROWSE * 0.3;
            tell = "roe deer shoot browse — young growth bitten back";
        }

        double newLevel = Math.min(1.0, mem.level() + YIELD_FRACTION_PER_BROWSE);
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
