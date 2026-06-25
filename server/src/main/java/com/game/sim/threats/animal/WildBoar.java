package com.game.sim.threats.animal;

import com.game.core.data.Fault;
import com.game.core.data.PhenoStage;
import com.game.sim.threats.api.ThreatCategory;
import com.game.sim.threats.api.ThreatContext;
import com.game.sim.threats.api.ThreatEffect;
import com.game.sim.threats.api.ThreatMemory;
import com.game.sim.threats.api.ThreatSource;

/**
 * Wild Boar — <em>Sus scrofa</em>.
 *
 * <p>Wild boar conduct nocturnal raids on ripening bunches, consuming fruit and
 * — critically — rooting up the soil around vine bases. This causes:
 * <ul>
 *   <li>Direct fruit loss from consumption (yield multiplier).</li>
 *   <li>Root disturbance and soil compaction/erosion (health drain).</li>
 *   <li>Quality degradation from damaged fruit left on the ground (quality
 *       penalty + rot risk).</li>
 * </ul>
 *
 * <p>Gating: only active from {@link PhenoStage#VERAISON} onward (attracted
 * by the smell of ripening berries). Boar activity peaks in RIPENING.
 *
 * <p>Suppressor: {@code guardDog} — a Georgian Shepherd (Nagazi) or similar
 * livestock guardian dog. Effective at deterring boar from night raids.
 *
 * <p>Model: nightly raid probability; boar are nocturnal so low temp or heavy
 * rain can suppress. Guard dog eliminates raids.
 *
 * <p>Memory: {@code level} = cumulative damage index 0..1;
 * {@code ticksActive} = raid days this season.
 */
public final class WildBoar implements ThreatSource {

    // ---- constants -------------------------------------------------------

    /** First eligible phenostage. */
    private static final PhenoStage ONSET_STAGE = PhenoStage.VERAISON;

    /** Base nightly raid probability. */
    private static final double BASE_RAID_PROBABILITY = 0.15;

    /** Extra probability in full ripening (sweeter fruit). */
    private static final double RIPENING_BONUS = 0.10;

    /** Fraction of yield consumed per raid. */
    private static final double YIELD_CONSUMED_FRACTION = 0.08;

    /** Additional yield loss from trampling and root disturbance. */
    private static final double YIELD_TRAMPLE_FRACTION = 0.03;

    /** Health drain per raid from root disturbance/erosion. */
    private static final double HEALTH_DRAIN_PER_RAID = 0.008;

    /** Quality penalty per raid from damaged/soiled fruit. */
    private static final double QUALITY_PENALTY_PER_RAID = 0.006;

    // ---- ThreatSource impl -----------------------------------------------

    @Override
    public String id() {
        return "animal.wild_boar";
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
        int ticksActive = mem.ticksActive() + 1;

        // Guard dog deters boar raids entirely
        if (ctx.guardDog()) {
            ThreatMemory next = new ThreatMemory(mem.level(), 0.0, ticksActive,
                    mem.yearsActive(), false);
            return ThreatEffect.none(next);
        }

        // Raid probability
        double raidProb = BASE_RAID_PROBABILITY;
        if (ctx.vine().stage() == PhenoStage.RIPENING) {
            raidProb += RIPENING_BONUS;
        }

        boolean raid = ctx.rng().nextDouble() < raidProb;

        if (!raid) {
            ThreatMemory next = new ThreatMemory(mem.level(), 0.0, ticksActive,
                    mem.yearsActive(), false);
            return ThreatEffect.none(next);
        }

        // Raid occurred
        double totalYieldLoss = YIELD_CONSUMED_FRACTION + YIELD_TRAMPLE_FRACTION;
        double yieldMult = 1.0 - totalYieldLoss;
        double newLevel = Math.min(1.0, mem.level() + totalYieldLoss);

        ThreatMemory next = new ThreatMemory(newLevel, 0.0, ticksActive,
                mem.yearsActive(), true);

        return new ThreatEffect(
                -HEALTH_DRAIN_PER_RAID,
                yieldMult,
                QUALITY_PENALTY_PER_RAID,
                Fault.NONE,
                false,
                "boar tracks at dawn — night raid; fruit consumed and soil rooted up",
                next);
    }
}
