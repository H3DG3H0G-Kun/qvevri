package com.game.sim.soil;

import com.game.core.data.SoilType;

/**
 * Static lookup for per-soil-type agronomic characteristics.
 *
 * <p>Values sourced from GDD Part 5.3 and the frozen SIM-SPEC §3.3.
 * Descriptions follow the canonical order in {@link SoilType}.
 *
 * <h2>Soil profiles (rounded to 0.05 resolution)</h2>
 * <pre>
 * HUMUS_CARBONATE  – structured humus-carbonate (Kakheti slope standard).
 *   Medium vigour, medium-high water retention, low frost bias.
 *   Classic Saperavi terroir: balanced nutrition, cool nights, calcareous buffering.
 *
 * BLACK_EARTH (chernozem) – dark alluvial valley floor.
 *   Very high vigour, high water retention, moderate frost bias (inversion risk).
 *   Over-vigorous; pushes yield but dilutes quality.
 *
 * ALLUVIAL – river-terrace sand/gravel mix.
 *   Medium vigour, low water holding (fast drain), moderate frost bias.
 *   High drainage means irrigation-dependent but avoids waterlogging diseases.
 *
 * CLAY_LIMESTONE – cool, heavy, acid-tolerant.
 *   Low-medium vigour, high water holding, moderate-high frost bias.
 *   Slow to warm; retains acidity; good structure but risky springs.
 *
 * HEAVY_CLAY – very high water retention, frost-trap.
 *   Low vigour (waterlogging stress), very high water holding, high frost bias.
 *
 * SAND – light, warm, low fertility.
 *   Low vigour, very low water holding, low frost bias (drains cold air).
 *
 * VOLCANIC – mineral, well-structured, medium-low vigour.
 *   Low-medium vigour, medium water holding, low frost bias.
 * </pre>
 */
public final class SoilTypes {

    private SoilTypes() {}

    // -----------------------------------------------------------------------
    // Named constants for each statblock — no magic numbers in profile().
    // -----------------------------------------------------------------------

    // HUMUS_CARBONATE: medium vigour 0.45, medium-high water 0.60, frost bias low 0.20
    private static final SoilStat HUMUS_CARBONATE_STAT = new SoilStat(0.45, 0.60, 0.20);

    // BLACK_EARTH: over-vigorous 0.85, high water 0.75, moderate valley-floor frost 0.45
    private static final SoilStat BLACK_EARTH_STAT     = new SoilStat(0.85, 0.75, 0.45);

    // ALLUVIAL: medium 0.50, fast-drain low 0.30, moderate 0.35
    private static final SoilStat ALLUVIAL_STAT        = new SoilStat(0.50, 0.30, 0.35);

    // CLAY_LIMESTONE: low-medium 0.35, high retention 0.70, cool/high-acid moderate-high 0.55
    private static final SoilStat CLAY_LIMESTONE_STAT  = new SoilStat(0.35, 0.70, 0.55);

    // HEAVY_CLAY: very high water/frost, low vigour from waterlogging stress
    private static final SoilStat HEAVY_CLAY_STAT      = new SoilStat(0.25, 0.90, 0.75);

    // SAND: low vigour, very low water, warm/low frost
    private static final SoilStat SAND_STAT            = new SoilStat(0.30, 0.15, 0.15);

    // VOLCANIC: mineral/medium-low vigour, medium water holding, warm rock low frost
    private static final SoilStat VOLCANIC_STAT        = new SoilStat(0.40, 0.50, 0.20);

    /**
     * Returns the {@link SoilStat} for the given {@link SoilType}.
     * This is a pure function: same input always produces the same statblock.
     *
     * @param t soil type (non-null)
     * @return immutable SoilStat
     * @throws IllegalArgumentException if {@code t} is null or unknown
     */
    public static SoilStat profile(SoilType t) {
        if (t == null) throw new IllegalArgumentException("SoilType must not be null");
        return switch (t) {
            case HUMUS_CARBONATE -> HUMUS_CARBONATE_STAT;
            case BLACK_EARTH     -> BLACK_EARTH_STAT;
            case ALLUVIAL        -> ALLUVIAL_STAT;
            case CLAY_LIMESTONE  -> CLAY_LIMESTONE_STAT;
            case HEAVY_CLAY      -> HEAVY_CLAY_STAT;
            case SAND            -> SAND_STAT;
            case VOLCANIC        -> VOLCANIC_STAT;
        };
    }
}
