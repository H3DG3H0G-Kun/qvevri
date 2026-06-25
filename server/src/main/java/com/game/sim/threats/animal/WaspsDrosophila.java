package com.game.sim.threats.animal;

import com.game.core.data.Fault;
import com.game.core.data.PhenoStage;
import com.game.sim.threats.api.ThreatCategory;
import com.game.sim.threats.api.ThreatContext;
import com.game.sim.threats.api.ThreatEffect;
import com.game.sim.threats.api.ThreatMemory;
import com.game.sim.threats.api.ThreatSource;

/**
 * Wasps and Spotted Wing Drosophila (SWD) — combined model.
 *
 * <p>Both organisms arrive after berries burst or crack (usually from other
 * damage — bird pecks, moth wounds, botrytis splits). They share a common
 * arrival trigger: broken berry skin from any cause raises local sugar and
 * yeast/bacteria exposure, attracting wasps (<em>Vespula</em> spp.) and SWD
 * (<em>Drosophila suzukii</em>).
 *
 * <p>In this model the trigger is probabilistic once stage reaches RIPENING
 * and is amplified by already-damaged fruit (health below threshold). The
 * primary impact is rapid spread of acetic acid bacteria (Acetobacter) through
 * the damaged berries, inducing a {@link Fault#VOLATILE_ACIDITY} risk.
 *
 * <p>Gating: only active from {@link PhenoStage#VERAISON} onward.
 * Pressure rises steeply in RIPENING when sugar content peaks.
 *
 * <p>Suppressor: ducks reduce SWD/wasp populations around the vine base.
 *
 * <p>Induced fault: {@code VOLATILE_ACIDITY} when activity is high enough
 * (level > VA_FAULT_THRESHOLD). The fault propagates into the must.
 *
 * <p>Memory: {@code level} = infestation/acidity risk 0..1.
 */
public final class WaspsDrosophila implements ThreatSource {

    // ---- constants -------------------------------------------------------

    /** First eligible stage. */
    private static final PhenoStage ONSET_STAGE = PhenoStage.VERAISON;

    /** Base daily probability of wasp/SWD arrival at veraison. */
    private static final double BASE_ARRIVAL_PROB_VERAISON = 0.12;

    /** Elevated probability once in full ripening. */
    private static final double BASE_ARRIVAL_PROB_RIPENING = 0.22;

    /** Extra probability when vine health is already damaged (existing wounds). */
    private static final double DAMAGED_VINE_BONUS = 0.10;

    /** Vine health fraction below which "burst berry" bonus applies. */
    private static final double DAMAGE_HEALTH_THRESHOLD = 0.75;

    /** Level increment per active day. */
    private static final double LEVEL_INCREMENT = 0.018;

    /** Level decay on cool days (wasps/SWD less active below threshold). */
    private static final double COOL_DECAY = 0.008;

    /** Temperature below which activity drops. */
    private static final double ACTIVITY_TEMP_C = 16.0;

    /** Health drain per active day (berry damage). */
    private static final double HEALTH_DRAIN_PER_LEVEL = 0.003;

    /** Yield multiplier loss per active day. */
    private static final double YIELD_LOSS_PER_LEVEL = 0.005;

    /** Quality penalty per unit level per day. */
    private static final double QUALITY_PENALTY_PER_LEVEL = 0.005;

    /** Level threshold above which VOLATILE_ACIDITY fault is induced. */
    private static final double VA_FAULT_THRESHOLD = 0.45;

    /** Duck suppression factor. */
    private static final double DUCK_SUPPRESSION = 0.45;

    // ---- ThreatSource impl -----------------------------------------------

    @Override
    public String id() {
        return "animal.wasps_drosophila";
    }

    @Override
    public ThreatCategory category() {
        return ThreatCategory.ANIMAL;
    }

    @Override
    public ThreatEffect evaluate(ThreatContext ctx) {
        if (ctx.vine().stage().ordinal() < ONSET_STAGE.ordinal()) {
            return ThreatEffect.none(ThreatMemory.none());
        }

        ThreatMemory mem = ctx.memory();
        double level = mem.level();
        int ticksActive = mem.ticksActive();

        double meanTemp = ctx.today().meanTempC();
        boolean isRipening = ctx.vine().stage() == PhenoStage.RIPENING;
        boolean vineHealthLow = ctx.vine().healthFraction() < DAMAGE_HEALTH_THRESHOLD;

        double suppressionFactor = 1.0 - (ctx.ducks() ? DUCK_SUPPRESSION : 0.0);

        if (meanTemp >= ACTIVITY_TEMP_C) {
            double arrivalProb = isRipening
                    ? BASE_ARRIVAL_PROB_RIPENING
                    : BASE_ARRIVAL_PROB_VERAISON;
            if (vineHealthLow) {
                arrivalProb += DAMAGED_VINE_BONUS;
            }

            boolean arrives = ctx.rng().nextDouble() < arrivalProb;
            if (arrives) {
                level = Math.min(1.0, level + LEVEL_INCREMENT * suppressionFactor);
                ticksActive++;
            }
        } else {
            // Cool weather reduces activity
            level = Math.max(0.0, level - COOL_DECAY);
        }

        if (level < 0.01) {
            ThreatMemory next = new ThreatMemory(0.0, 0.0, 0, mem.yearsActive(), false);
            return ThreatEffect.none(next);
        }

        double healthDrain = HEALTH_DRAIN_PER_LEVEL * level;
        double yieldMult = Math.max(0.5, 1.0 - YIELD_LOSS_PER_LEVEL * level);
        double qualityPenalty = QUALITY_PENALTY_PER_LEVEL * level;

        // Volatile acidity fault when infestation level crosses threshold
        Fault inducedFault = level >= VA_FAULT_THRESHOLD
                ? Fault.VOLATILE_ACIDITY
                : Fault.NONE;

        String tell = "";
        if (ticksActive % 5 == 1) {
            tell = level >= VA_FAULT_THRESHOLD
                    ? "wasp/SWD infestation spreading rot — acetic-acid smell in bunches"
                    : "wasp and drosophila activity — berries punctured, fermentation starting in cluster";
        }

        ThreatMemory next = new ThreatMemory(level, 0.0, ticksActive,
                mem.yearsActive(), level > 0.2);

        return new ThreatEffect(
                -healthDrain,
                yieldMult,
                qualityPenalty,
                inducedFault,
                false,
                tell,
                next);
    }
}
