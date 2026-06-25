package com.game.core.data;

/**
 * Soil classification; stat blocks resolved via {@code com.game.sim.soil.SoilTypes#profile(SoilType)}.
 * Frozen per SIM-SPEC §2.
 */
public enum SoilType {
    HUMUS_CARBONATE,
    BLACK_EARTH,
    ALLUVIAL,
    CLAY_LIMESTONE,
    HEAVY_CLAY,
    SAND,
    VOLCANIC
}
