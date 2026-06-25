package com.game.core.weather;

import com.game.core.data.DailyWeather;

/**
 * Growing Degree Day calculations.
 * Base temperature for vine phenology: 10°C (GDD_BASE_C).
 *
 * <p>Frozen seam per SIM-SPEC §3.2.
 */
public final class Gdd {

    /** Base temperature (°C) used for vine GDD accumulation. */
    public static final double GDD_BASE_C = 10.0;

    private Gdd() {}

    /**
     * Daily GDD contribution: {@code max(0, meanTemp - baseC)}.
     *
     * @param w     daily weather record
     * @param baseC base temperature (use {@link #GDD_BASE_C} for vines)
     * @return non-negative degree-day contribution
     */
    public static double daily(DailyWeather w, double baseC) {
        return Math.max(0.0, w.meanTempC() - baseC);
    }
}
