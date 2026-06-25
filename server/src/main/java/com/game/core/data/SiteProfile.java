package com.game.core.data;

/**
 * Site geometry and soil classification for one vineyard plot.
 * Frozen per SIM-SPEC §2.
 *
 * @param soil           soil type (drives vigor, water-holding, frost bias)
 * @param slopeDeg       slope angle in degrees (0 = flat, 45 = steep)
 * @param aspectDeg      aspect bearing (0=N, 90=E, 180=S, 270=W)
 * @param altitudeM      altitude in metres above sea level
 * @param frostRisk      0..1 (valley floor high, mid-slope low)
 * @param waterProximity 0..1 proximity to water body (humidity buffer)
 */
public record SiteProfile(
        SoilType soil,
        double slopeDeg,
        double aspectDeg,
        double altitudeM,
        double frostRisk,
        double waterProximity
) {}
