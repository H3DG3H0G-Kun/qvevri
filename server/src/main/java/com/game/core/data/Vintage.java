package com.game.core.data;

/**
 * Whole-season weather summary and vintage classification for a region/year.
 * Frozen per SIM-SPEC §2.
 *
 * @param year         simulation calendar year
 * @param region       wine region
 * @param gddSeason    total GDD (base 10°C) accumulated Apr1–Oct31
 * @param winkler      Winkler heat-summation class derived from {@code gddSeason}
 * @param patternLabel human-readable label, e.g. "warm-dry" or "cool-wet"
 */
public record Vintage(
        int year,
        Region region,
        double gddSeason,
        WinklerClass winkler,
        String patternLabel
) {}
