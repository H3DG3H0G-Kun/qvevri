package com.game.vineyard;

import java.util.List;

/**
 * Top-level response for POST /api/vineyard/simulate.
 * Matches VINEYARD-API §1 "VineyardYearResult" shape exactly.
 *
 * @param seed        the seed used for this run (echoed back for client caching)
 * @param vintage     vintage summary (year, gdd, winkler class, pattern)
 * @param pickDay     the day of year that was harvested
 * @param suitability site suitability score 0..1
 * @param must        must chemistry at pick
 * @param bottle      resolved wine bottle
 * @param events      ordered human-readable event log (phenology transitions + threat tells)
 */
public record VineyardYearResult(
        long seed,
        VintageDto vintage,
        int pickDay,
        double suitability,
        MustDto must,
        BottleDto bottle,
        List<String> events
) {}
