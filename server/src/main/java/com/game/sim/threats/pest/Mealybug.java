package com.game.sim.threats.pest;

import com.game.core.data.Fault;
import com.game.core.data.PhenoStage;
import com.game.sim.threats.api.ThreatCategory;
import com.game.sim.threats.api.ThreatContext;
import com.game.sim.threats.api.ThreatEffect;
import com.game.sim.threats.api.ThreatMemory;
import com.game.sim.threats.api.ThreatSource;

/**
 * Grape Mealybug — <em>Planococcus ficus</em> (vine mealybug) and
 * <em>Pseudococcus longispinus</em>.
 *
 * <p>Mealybugs hide under bark and in bunches. They produce honeydew that
 * encourages sooty mould, reducing photosynthesis and contaminating the must
 * with quality-degrading compounds. They are also vectors of Grapevine
 * Leafroll Virus (handled by Lane B's virus classes).
 *
 * <p>Mealybugs thrive in warm, dense canopies. More open canopies ({@code
 * canopyOpenness01}) expose them to predators and desiccation. Ducks reduce
 * their populations.
 *
 * <p>Pressure window: {@link PhenoStage#BERRY_DEVELOPMENT} through
 * {@link PhenoStage#RIPENING} (when they migrate into bunches).
 *
 * <p>Memory: {@code level} = infestation density 0..1.
 */
public final class Mealybug implements ThreatSource {

    // ---- constants -------------------------------------------------------

    /** First phenostage for bunch infestation. */
    private static final PhenoStage ONSET_STAGE = PhenoStage.BERRY_DEVELOPMENT;

    /** Last active stage. */
    private static final PhenoStage FINAL_STAGE = PhenoStage.RIPENING;

    /** Temperature threshold for daily activity. */
    private static final double ACTIVE_TEMP_C = 18.0;

    /** Daily level growth rate in warm, closed-canopy conditions. */
    private static final double LEVEL_GROWTH_BASE = 0.010;

    /** Dense canopy amplification (closed canopy = ideal habitat). */
    private static final double CANOPY_CLOSENESS_AMPLIFIER = 1.5;

    /** Daily level decay when cold or well-managed. */
    private static final double LEVEL_DECAY = 0.006;

    /** Duck suppression. */
    private static final double DUCK_SUPPRESSION = 0.40;

    /** Open canopy suppression: each unit of openness reduces growth. */
    private static final double CANOPY_OPENNESS_SUPPRESSION = 0.50;

    /** Health drain per unit level per day. */
    private static final double HEALTH_DRAIN_PER_LEVEL = 0.0018;

    /** Quality penalty per unit level (honeydew -> sooty mould -> must taint). */
    private static final double QUALITY_PENALTY_PER_LEVEL = 0.0004;

    /** Tell threshold. */
    private static final double TELL_THRESHOLD = 0.25;

    // ---- ThreatSource impl -----------------------------------------------

    @Override
    public String id() {
        return "pest.mealybug";
    }

    @Override
    public ThreatCategory category() {
        return ThreatCategory.PEST;
    }

    @Override
    public ThreatEffect evaluate(ThreatContext ctx) {
        int stageOrd = ctx.vine().stage().ordinal();
        if (stageOrd < ONSET_STAGE.ordinal() || stageOrd > FINAL_STAGE.ordinal()) {
            return ThreatEffect.none(ctx.memory());
        }

        ThreatMemory mem = ctx.memory();
        double level = mem.level();
        int ticksActive = mem.ticksActive();

        double meanTemp = ctx.today().meanTempC();
        double canopyOpenness = ctx.canopyOpenness01();

        if (meanTemp >= ACTIVE_TEMP_C) {
            // Canopy openness suppresses, closeness amplifies
            double canopyFactor = 1.0
                    + CANOPY_CLOSENESS_AMPLIFIER * (1.0 - canopyOpenness)
                    - CANOPY_OPENNESS_SUPPRESSION * canopyOpenness;
            canopyFactor = Math.max(0.1, canopyFactor);

            double suppressionFactor = 1.0 - (ctx.ducks() ? DUCK_SUPPRESSION : 0.0);

            level = Math.min(1.0, level + LEVEL_GROWTH_BASE * canopyFactor * suppressionFactor);
            ticksActive++;
        } else {
            level = Math.max(0.0, level - LEVEL_DECAY);
            ticksActive = level > 0 ? ticksActive : 0;
        }

        if (level < 0.01) {
            ThreatMemory next = new ThreatMemory(0.0, 0.0, 0, mem.yearsActive(), false);
            return ThreatEffect.none(next);
        }

        double healthDrain = HEALTH_DRAIN_PER_LEVEL * level;
        double qualityPenalty = QUALITY_PENALTY_PER_LEVEL * level;
        String tell = (level >= TELL_THRESHOLD && ticksActive % 7 == 1)
                ? "mealybug colonies in bunches — honeydew and sooty mould on clusters"
                : "";

        ThreatMemory next = new ThreatMemory(level, 0.0, ticksActive, mem.yearsActive(), level > 0.1);

        return new ThreatEffect(
                -healthDrain,
                1.0,
                qualityPenalty,
                Fault.NONE,
                false,
                tell,
                next);
    }
}
