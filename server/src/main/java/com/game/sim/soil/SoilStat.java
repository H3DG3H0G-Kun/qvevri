package com.game.sim.soil;

/**
 * Soil characteristic triple used by the vine simulator and site suitability scorer.
 *
 * <ul>
 *   <li>{@code vigor01}       – how strongly the soil drives canopy/cane vigour (0=low, 1=very high).</li>
 *   <li>{@code waterHolding01} – fraction of applied/rain water retained in the root-zone (0=drains fast, 1=water-logged).</li>
 *   <li>{@code frostBias01}   – modifies frost-damage probability on top of site frostRisk (0=neutral/warm, 1=cold amplifier).</li>
 * </ul>
 *
 * All fields are in [0,1].  This record is part of the §3.3 frozen seam.
 */
public record SoilStat(double vigor01, double waterHolding01, double frostBias01) {

    /** Compact validation: guard against callers passing nonsense. */
    public SoilStat {
        if (vigor01 < 0 || vigor01 > 1)         throw new IllegalArgumentException("vigor01 out of range: " + vigor01);
        if (waterHolding01 < 0 || waterHolding01 > 1) throw new IllegalArgumentException("waterHolding01 out of range: " + waterHolding01);
        if (frostBias01 < 0 || frostBias01 > 1) throw new IllegalArgumentException("frostBias01 out of range: " + frostBias01);
    }
}
