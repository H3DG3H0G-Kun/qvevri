package com.game.core.weather;

import com.game.core.data.DailyWeather;
import com.game.core.data.Region;
import com.game.core.data.Vintage;
import com.game.core.time.RngStreams;

import java.util.List;

/**
 * Contract for deterministic synthetic weather generation.
 *
 * <p>Implementations must be pure: {@code generateYear(rng, region, year)} with the
 * same RNG seed always produces the same sequence.  No system clocks or external I/O.
 *
 * <p>Frozen seam per SIM-SPEC §3.2.
 */
public interface WeatherModel {

    /**
     * Generate 365 days of plausible weather for the given region and simulation year.
     * The list is ordered by dayOfYear 0..364.
     *
     * @param rng    seeded stream factory (use stream names prefixed with "weather.")
     * @param region viticulture region
     * @param year   simulation calendar year
     * @return unmodifiable list of 365 {@link DailyWeather} records
     */
    List<DailyWeather> generateYear(RngStreams rng, Region region, int year);

    /**
     * Summarise the season into a {@link Vintage} record.
     * GDD is summed from Apr 1 (day 90) to Oct 31 (day 303), base 10°C.
     * Winkler class boundaries per SIM-SPEC §3.2.
     *
     * @param rng    same factory used for {@link #generateYear}
     * @param region region
     * @param year   simulation year
     * @param days   the 365-day list returned by {@link #generateYear}
     * @return vintage summary
     */
    Vintage rollVintage(RngStreams rng, Region region, int year, List<DailyWeather> days);
}
