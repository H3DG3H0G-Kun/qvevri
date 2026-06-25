package com.game.core.data;

/**
 * One day of sampled weather at a plot (Tier-C field data).
 * Frozen per SIM-SPEC §2.
 *
 * @param dayOfYear  0-based day index (0..364)
 * @param tMinC      daily minimum temperature in °C
 * @param tMaxC      daily maximum temperature in °C
 * @param rainMm     precipitation in mm
 * @param humidity01 relative humidity 0..1
 */
public record DailyWeather(
        int dayOfYear,
        double tMinC,
        double tMaxC,
        double rainMm,
        double humidity01
) {
    /** Simple arithmetic mean of min/max (same approach used in GDD.daily). */
    public double meanTempC() {
        return (tMinC + tMaxC) / 2.0;
    }
}
