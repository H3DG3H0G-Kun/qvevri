package com.game.sim.threats.pest;

import com.game.core.data.Fault;
import com.game.sim.threats.api.ThreatCategory;
import com.game.sim.threats.api.ThreatContext;
import com.game.sim.threats.api.ThreatEffect;
import com.game.sim.threats.api.ThreatMemory;
import com.game.sim.threats.api.ThreatSource;

/**
 * Grapevine Moth — <em>Lobesia botrana</em> (European grapevine moth).
 *
 * <p>Three broods per season, each triggered when accumulated GDD (base 12°C)
 * exceeds a threshold. Thresholds are empirically derived for Central/Eastern
 * European viticulture:
 *
 * <ul>
 *   <li><b>Brood 1</b> (egg-cluster/flower damage): GDD ≥ 120. Larvae feed on
 *       flower clusters and young berries. Damages fruit set.</li>
 *   <li><b>Brood 2</b> (bunch damage): GDD ≥ 520. Larvae web individual
 *       berries, creating entry for botrytis. Quality and yield hit.</li>
 *   <li><b>Brood 3</b> (late bunch damage): GDD ≥ 1047. Further bunch
 *       webbing; creates rot foci. Worst quality impact.</li>
 * </ul>
 *
 * <p>{@code gddSeason} in the context is the season GDD accumulated at base
 * 10°C (vine GDD). The moth base is 12°C, so moth GDD ≈ vine GDD * 0.85 is a
 * reasonable approximation for season-long accumulation differences.
 * We use {@code gddSeason} directly from ctx which is base-10 vine GDD, but
 * thresholds are calibrated to match observed real-world phenology when that
 * is the input (i.e., thresholds stated in terms of vine GDD-equivalent for
 * consistency with the context API).
 *
 * <p>Ducks suppress insect pressure ({@code ducks == true} halves damage).
 *
 * <p>Memory: {@code aux} encodes brood bitmask as an int stored as double
 * (broods completed: bit 0 = brood 1, bit 1 = brood 2, bit 2 = brood 3).
 * {@code level} is current population pressure 0..1.
 */
public final class GrapevineMoth implements ThreatSource {

    // ---- constants: GDD thresholds (vine base-10 equivalent) -------------

    /** GDD threshold triggering Brood 1 (flower cluster damage). */
    static final double BROOD1_GDD = 120.0;

    /** GDD threshold triggering Brood 2 (berry wounding). */
    static final double BROOD2_GDD = 520.0;

    /** GDD threshold triggering Brood 3 (late bunch damage + rot entry). */
    static final double BROOD3_GDD = 1047.0;

    // ---- damage constants ------------------------------------------------

    /** Duration (days) of acute damage window per brood. */
    private static final int BROOD_DAMAGE_WINDOW_DAYS = 10;

    /** Health drain per active day during a brood. */
    private static final double HEALTH_DRAIN_PER_DAY = 0.003;

    /** Yield multiplier applied on the peak brood day (one-time per brood). */
    private static final double BROOD1_YIELD_MULT = 0.97; // fruit-set damage
    private static final double BROOD2_YIELD_MULT = 0.94; // mid-season berry loss
    private static final double BROOD3_YIELD_MULT = 0.92; // late bunch loss

    /** Quality penalty per day during an active brood. */
    private static final double QUALITY_PENALTY_PER_DAY_BROOD1 = 0.0002;
    private static final double QUALITY_PENALTY_PER_DAY_BROOD2 = 0.0004;
    private static final double QUALITY_PENALTY_PER_DAY_BROOD3 = 0.0006;

    /** Duck suppression factor (halves all damage). */
    private static final double DUCK_SUPPRESSION = 0.5;

    // ---- ThreatSource impl -----------------------------------------------

    @Override
    public String id() {
        return "pest.grapevine_moth";
    }

    @Override
    public ThreatCategory category() {
        return ThreatCategory.PEST;
    }

    @Override
    public ThreatEffect evaluate(ThreatContext ctx) {
        double gdd = ctx.gddSeason();
        ThreatMemory mem = ctx.memory();

        // Decode brood bitmask from aux (stored as double integer)
        int broodsDone = (int) mem.aux();
        boolean brood1Done = (broodsDone & 0x1) != 0;
        boolean brood2Done = (broodsDone & 0x2) != 0;
        boolean brood3Done = (broodsDone & 0x4) != 0;

        int ticksActive = mem.ticksActive();
        double level = mem.level();

        // Determine which brood just triggered (if any)
        boolean newBrood1 = !brood1Done && gdd >= BROOD1_GDD;
        boolean newBrood2 = !brood2Done && gdd >= BROOD2_GDD;
        boolean newBrood3 = !brood3Done && gdd >= BROOD3_GDD;

        // If we're within BROOD_DAMAGE_WINDOW_DAYS of a triggered brood, apply damage
        // We track via ticksActive — reset on each new brood trigger by checking order
        boolean inBroodWindow = false;
        int activeBrood = 0;

        if (newBrood3) {
            activeBrood = 3;
            inBroodWindow = true;
            brood3Done = true;
            ticksActive = 1;
            level = 1.0;
            broodsDone |= 0x4;
        } else if (newBrood2) {
            activeBrood = 2;
            inBroodWindow = true;
            brood2Done = true;
            ticksActive = 1;
            level = 1.0;
            broodsDone |= 0x2;
        } else if (newBrood1) {
            activeBrood = 1;
            inBroodWindow = true;
            brood1Done = true;
            ticksActive = 1;
            level = 1.0;
            broodsDone |= 0x1;
        } else {
            // Check if still within an existing active brood window
            // We use ticksActive to track days since brood triggered
            if (level > 0 && ticksActive > 0 && ticksActive <= BROOD_DAMAGE_WINDOW_DAYS) {
                inBroodWindow = true;
                // Determine which brood is current based on which completed last
                if (brood3Done) activeBrood = 3;
                else if (brood2Done) activeBrood = 2;
                else if (brood1Done) activeBrood = 1;
                ticksActive++;
                level = Math.max(0.0, 1.0 - (double)(ticksActive - 1) / BROOD_DAMAGE_WINDOW_DAYS);
            } else {
                // No active brood
                level = 0.0;
                ticksActive = 0;
            }
        }

        if (!inBroodWindow) {
            ThreatMemory next = new ThreatMemory(0.0, broodsDone, 0,
                    mem.yearsActive(), mem.established());
            return ThreatEffect.none(next);
        }

        // Apply damage for active brood
        double suppression = ctx.ducks() ? DUCK_SUPPRESSION : 1.0;

        double healthDrain;
        double yieldMult;
        double qualityPenalty;
        String tell;

        switch (activeBrood) {
            case 1 -> {
                healthDrain = HEALTH_DRAIN_PER_DAY * suppression;
                // Yield hit only on first day of brood
                yieldMult = (ticksActive == 1) ? BROOD1_YIELD_MULT : 1.0;
                qualityPenalty = QUALITY_PENALTY_PER_DAY_BROOD1 * suppression;
                tell = ticksActive == 1
                        ? "grapevine moth brood 1 — larvae webbing flower clusters"
                        : "";
            }
            case 2 -> {
                healthDrain = HEALTH_DRAIN_PER_DAY * suppression;
                yieldMult = (ticksActive == 1) ? BROOD2_YIELD_MULT : 1.0;
                qualityPenalty = QUALITY_PENALTY_PER_DAY_BROOD2 * suppression;
                tell = ticksActive == 1
                        ? "grapevine moth brood 2 — berries webbed and punctured"
                        : "";
            }
            case 3 -> {
                healthDrain = HEALTH_DRAIN_PER_DAY * suppression;
                yieldMult = (ticksActive == 1) ? BROOD3_YIELD_MULT : 1.0;
                qualityPenalty = QUALITY_PENALTY_PER_DAY_BROOD3 * suppression;
                tell = ticksActive == 1
                        ? "grapevine moth brood 3 — late bunch wounding, rot risk elevated"
                        : "";
            }
            default -> {
                healthDrain = 0.0;
                yieldMult = 1.0;
                qualityPenalty = 0.0;
                tell = "";
            }
        }

        ThreatMemory next = new ThreatMemory(level, broodsDone, ticksActive,
                mem.yearsActive(), broodsDone != 0);

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
