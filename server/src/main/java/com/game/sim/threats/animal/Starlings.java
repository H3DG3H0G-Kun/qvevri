package com.game.sim.threats.animal;

import com.game.core.data.Fault;
import com.game.core.data.PhenoStage;
import com.game.sim.threats.api.ThreatCategory;
import com.game.sim.threats.api.ThreatContext;
import com.game.sim.threats.api.ThreatEffect;
import com.game.sim.threats.api.ThreatMemory;
import com.game.sim.threats.api.ThreatSource;

/**
 * Common Starling flock — <em>Sturnus vulgaris</em>.
 *
 * <p>Starlings strip ripe berries in aggressive coordinated flocks. They are
 * attracted by the scent and colour change at veraison and cause severe, rapid
 * yield loss when unchecked. They typically descend in the morning and evening.
 *
 * <p>Gating: only active from {@link PhenoStage#VERAISON} onward (berries must
 * be colour-changed and sugar-rich to attract flocks). No activity before that.
 *
 * <p>Suppressor: {@code falcons} — a trained falconry/hawk programme scatters
 * starling flocks and effectively eliminates their impact on active days.
 * {@code netting} — bird netting blocks direct access to fruit.
 *
 * <p>Model: each day from veraison, there is a probabilistic flock visit.
 * The RNG determines whether a flock arrives on a given day. When a flock
 * arrives it strips a fraction of the remaining yield. Pressure rises as
 * berries ripen (brix > 18 attracts larger flocks). Falcons or netting
 * suppress.
 *
 * <p>Memory: {@code level} = cumulative flock activity index 0..1.
 * {@code ticksActive} = days since veraison.
 */
public final class Starlings implements ThreatSource {

    // ---- constants -------------------------------------------------------

    /** First eligible stage. */
    private static final PhenoStage ONSET_STAGE = PhenoStage.VERAISON;

    /** Base daily probability of a flock visit when no suppressors. */
    private static final double BASE_FLOCK_PROBABILITY = 0.20;

    /** Additional probability when brix is high (ripe berries more attractive). */
    private static final double HIGH_BRIX_BONUS = 0.15;

    /** Brix threshold for high-sugar attractiveness. */
    private static final double HIGH_BRIX_THRESHOLD = 18.0;

    /** Fraction of remaining yield stripped per flock visit at full pressure. */
    private static final double YIELD_STRIP_FRACTION = 0.12;

    /** Health impact per flock day (stress, wound entry points). */
    private static final double HEALTH_DRAIN_FLOCK = 0.005;

    /** Quality penalty per flock day (damaged berries, oxidation). */
    private static final double QUALITY_PENALTY_FLOCK = 0.008;

    /** Falcons completely suppress flock damage (scatter before landing). */
    private static final boolean FALCONS_FULL_SUPPRESS = true;

    /** Netting reduces yield strip fraction. */
    private static final double NETTING_YIELD_PROTECT = 0.90; // 90% reduction

    // ---- ThreatSource impl -----------------------------------------------

    @Override
    public String id() {
        return "animal.starlings";
    }

    @Override
    public ThreatCategory category() {
        return ThreatCategory.ANIMAL;
    }

    @Override
    public ThreatEffect evaluate(ThreatContext ctx) {
        // Only active from veraison onward
        if (ctx.vine().stage().ordinal() < ONSET_STAGE.ordinal()) {
            return ThreatEffect.none(ThreatMemory.none());
        }

        ThreatMemory mem = ctx.memory();
        int ticksActive = mem.ticksActive() + 1;

        // Falcons completely suppress starlings
        if (ctx.falcons()) {
            ThreatMemory next = new ThreatMemory(mem.level(), 0.0, ticksActive,
                    mem.yearsActive(), false);
            return ThreatEffect.none(next);
        }

        // Determine if a flock visits today
        double flockProb = BASE_FLOCK_PROBABILITY;
        if (ctx.vine().brix() >= HIGH_BRIX_THRESHOLD) {
            flockProb += HIGH_BRIX_BONUS;
        }

        boolean flockVisit = ctx.rng().nextDouble() < flockProb;

        if (!flockVisit) {
            ThreatMemory next = new ThreatMemory(mem.level(), 0.0, ticksActive,
                    mem.yearsActive(), false);
            return ThreatEffect.none(next);
        }

        // Flock has arrived — compute damage
        double yieldStripFraction = YIELD_STRIP_FRACTION;
        if (ctx.netting()) {
            yieldStripFraction *= (1.0 - NETTING_YIELD_PROTECT);
        }

        double yieldMult = 1.0 - yieldStripFraction;
        double healthDrain = ctx.netting() ? HEALTH_DRAIN_FLOCK * 0.1 : HEALTH_DRAIN_FLOCK;
        double qualityPenalty = ctx.netting() ? QUALITY_PENALTY_FLOCK * 0.1 : QUALITY_PENALTY_FLOCK;

        double newLevel = Math.min(1.0, mem.level() + yieldStripFraction);
        ThreatMemory next = new ThreatMemory(newLevel, 0.0, ticksActive,
                mem.yearsActive(), true);

        String tell = ctx.netting()
                ? "starling flock on the block — netting held; minor losses"
                : "starling flock on the block — " + Math.round(yieldStripFraction * 100) + "% of fruit stripped";

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
