package com.game.sim.threats.pest;

import com.game.core.data.Fault;
import com.game.core.data.SoilType;
import com.game.sim.threats.api.ThreatCategory;
import com.game.sim.threats.api.ThreatContext;
import com.game.sim.threats.api.ThreatEffect;
import com.game.sim.threats.api.ThreatMemory;
import com.game.sim.threats.api.ThreatSource;

/**
 * Root-knot and dagger nematodes (Meloidogyne spp., Xiphinema index).
 *
 * <p>Nematodes thrive in clay-heavy soils with poor drainage. Active soils:
 * {@link SoilType#HEAVY_CLAY} and {@link SoilType#CLAY_LIMESTONE}. Other soil
 * types are either too free-draining or structurally hostile to nematode movement.
 *
 * <p>Effect: chronic, low-level health and yield drain across the season.
 * Xiphinema index is also a vector for Grapevine Fanleaf Virus, but that
 * coupling is handled by Lane B's virus classes. Here we model only the
 * direct physical root damage.
 *
 * <p>Memory: {@code level} tracks cumulative population density 0..1; rises
 * gradually as roots are damaged and conditions favour reproduction.
 */
public final class Nematode implements ThreatSource {

    // ---- constants -------------------------------------------------------

    /** Soils where nematodes establish (clay, poor drainage). */
    private static final SoilType[] SUSCEPTIBLE_SOILS = {
            SoilType.HEAVY_CLAY,
            SoilType.CLAY_LIMESTONE
    };

    /** Daily level build-up rate. */
    private static final double LEVEL_GROWTH_RATE = 0.0015;

    /** Daily health drain per unit of nematode level. */
    private static final double HEALTH_DRAIN_PER_LEVEL = 0.003;

    /** Yield multiplier per unit of nematode level at peak. */
    private static final double YIELD_PENALTY_PER_LEVEL = 0.04;

    /** Quality penalty per unit of level. */
    private static final double QUALITY_PENALTY_PER_LEVEL = 0.0002;

    /** Warm-soil threshold for daily activity (nematodes are more active in warm soil). */
    private static final double SOIL_TEMP_THRESHOLD_C = 14.0;

    // ---- ThreatSource impl -----------------------------------------------

    @Override
    public String id() {
        return "pest.nematode";
    }

    @Override
    public ThreatCategory category() {
        return ThreatCategory.PEST;
    }

    @Override
    public ThreatEffect evaluate(ThreatContext ctx) {
        if (!isSusceptibleSoil(ctx.site().soil())) {
            return ThreatEffect.none(ThreatMemory.none());
        }

        // Nematodes are dormant in cold soil
        double meanTemp = ctx.today().meanTempC();
        if (meanTemp < SOIL_TEMP_THRESHOLD_C) {
            // Still carry memory forward unchanged
            return ThreatEffect.none(ctx.memory());
        }

        ThreatMemory mem = ctx.memory();
        double level = Math.min(1.0, mem.level() + LEVEL_GROWTH_RATE);
        int ticksActive = mem.ticksActive() + 1;

        double healthDrain = HEALTH_DRAIN_PER_LEVEL * level;
        double yieldMult = Math.max(0.6, 1.0 - YIELD_PENALTY_PER_LEVEL * level);
        double qualityPenalty = QUALITY_PENALTY_PER_LEVEL * level;

        String tell = level > 0.4
                ? "severe root-knot nematode — stunted shoots and reduced canopy"
                : "nematode activity in clay soil — minor feeder-root damage";

        ThreatMemory next = new ThreatMemory(level, 0.0, ticksActive,
                mem.yearsActive(), level > 0.1);

        return new ThreatEffect(
                -healthDrain,
                yieldMult,
                qualityPenalty,
                Fault.NONE,
                false,
                ticksActive % 14 == 0 ? tell : "", // surface tell every 2 weeks
                next);
    }

    // ---- helpers ---------------------------------------------------------

    private static boolean isSusceptibleSoil(SoilType soil) {
        for (SoilType s : SUSCEPTIBLE_SOILS) {
            if (s == soil) return true;
        }
        return false;
    }
}
