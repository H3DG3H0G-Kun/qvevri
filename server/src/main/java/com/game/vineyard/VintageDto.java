package com.game.vineyard;

/**
 * Vintage summary sub-object in {@link VineyardYearResult}.
 * Matches VINEYARD-API §1 "vintage" shape.
 *
 * @param year       simulation calendar year
 * @param gddSeason  total GDD (base 10 °C) accumulated Apr1–Oct31
 * @param winkler    Winkler class label ("I".."V")
 * @param pattern    human-readable vintage pattern, e.g. "warm-dry"
 */
public record VintageDto(
        int year,
        double gddSeason,
        String winkler,
        String pattern
) {}
