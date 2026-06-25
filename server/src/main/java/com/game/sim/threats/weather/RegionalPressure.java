package com.game.sim.threats.weather;

import com.game.core.data.DailyWeather;

/**
 * Static helper: daily fungal-pressure index for the Kakheti region.
 *
 * <p>Computes a single {@code 0..1} pressure value from the three primary
 * epidemiological drivers of grapevine fungal disease: temperature, humidity,
 * and rainfall. This is <em>not</em> a {@code ThreatSource}; it is a shared
 * utility that Lane B (fungal threats) may import to avoid duplicating the
 * weather-based component of their pressure models.
 *
 * <h3>Formula</h3>
 * <pre>
 *   // Temperature bell curve: peak pressure in the 18-26 °C range for downy
 *   // mildew; most fungals are suppressed at very low or very high temps.
 *   tempFactor = bell(meanTemp, PEAK_TEMP_C, TEMP_HALFWIDTH_C)
 *
 *   // Humidity factor: linear from LOW_HUMIDITY_THRESHOLD to 1.0
 *   humidityFactor = clamp01((humidity01 - LOW_HUMIDITY_THRESHOLD)
 *                            / (1.0 - LOW_HUMIDITY_THRESHOLD))
 *
 *   // Rain factor: logarithmic — even a little rain matters, but there are
 *   // diminishing returns above RAIN_SATURATION_MM
 *   rainFactor = clamp01(log1p(rainMm) / log1p(RAIN_SATURATION_MM))
 *
 *   pressure = tempFactor * TEMP_WEIGHT
 *            + humidityFactor * HUMIDITY_WEIGHT
 *            + rainFactor * RAIN_WEIGHT
 *   pressure = clamp01(pressure)
 * </pre>
 * Weights sum to 1.0. This makes the result directly comparable across weather
 * contexts and usable as a 0..1 daily input to any fungal-threat model.
 *
 * <h3>Design notes</h3>
 * <ul>
 *   <li>All arithmetic is pure / stateless — no RNG, no cross-day memory.</li>
 *   <li>Constants are exposed as {@code public static final} so Lane B implementations
 *       can reference them when building their own extended models.</li>
 *   <li>Not a {@code ThreatSource}; do not register it in {@code ThreatRegistry}.</li>
 * </ul>
 */
public final class RegionalPressure {

    // -------------------------------------------------------------------------
    // Temperature bell-curve parameters
    // -------------------------------------------------------------------------

    /** Mean temperature at which fungal pressure peaks (degrees C). Downy mildew optimum. */
    public static final double PEAK_TEMP_C            = 22.0;

    /**
     * Half-width of the temperature bell (degrees C). Pressure falls to ~0.607 at
     * PEAK +/- HALFWIDTH and approaches zero well outside this range.
     */
    public static final double TEMP_HALFWIDTH_C       = 8.0;

    // -------------------------------------------------------------------------
    // Humidity parameters
    // -------------------------------------------------------------------------

    /**
     * Humidity below which fungal pressure is essentially zero (0..1 scale).
     * Spore germination requires a minimum leaf-wetness period.
     */
    public static final double LOW_HUMIDITY_THRESHOLD = 0.55;

    // -------------------------------------------------------------------------
    // Rain parameters
    // -------------------------------------------------------------------------

    /**
     * Rain above which further rainfall adds negligible incremental pressure (mm).
     * Splash dispersal is saturated past this point.
     */
    public static final double RAIN_SATURATION_MM     = 25.0;

    // -------------------------------------------------------------------------
    // Weights (must sum to 1.0)
    // -------------------------------------------------------------------------

    /** Contribution of temperature to the composite pressure index. */
    public static final double TEMP_WEIGHT            = 0.35;

    /** Contribution of humidity to the composite pressure index. */
    public static final double HUMIDITY_WEIGHT        = 0.40;

    /** Contribution of rainfall to the composite pressure index. */
    public static final double RAIN_WEIGHT            = 0.25;

    // Weight-sum guard (caught at class-load time)
    static {
        double sum = TEMP_WEIGHT + HUMIDITY_WEIGHT + RAIN_WEIGHT;
        if (Math.abs(sum - 1.0) > 1e-9) {
            throw new ExceptionInInitializerError(
                    "RegionalPressure weights must sum to 1.0 but sum to " + sum);
        }
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Compute the daily fungal-pressure index for the given weather.
     *
     * @param w the day's weather (tMinC, tMaxC, rainMm, humidity01)
     * @return fungal pressure in [0, 1]; higher = more conducive to fungal disease
     */
    public static double daily(DailyWeather w) {
        return compute(w.meanTempC(), w.humidity01(), w.rainMm());
    }

    /**
     * Compute the daily fungal-pressure index from raw fields.
     * Provided so Lane B implementations can call this without constructing a
     * full {@link DailyWeather} record in unit tests.
     *
     * @param meanTempC  mean daily temperature in degrees C
     * @param humidity01 relative humidity 0..1
     * @param rainMm     precipitation in mm
     * @return fungal pressure in [0, 1]
     */
    public static double compute(double meanTempC, double humidity01, double rainMm) {
        double tempFactor     = bellCurve(meanTempC, PEAK_TEMP_C, TEMP_HALFWIDTH_C);
        double humidityFactor = clamp01(
                (humidity01 - LOW_HUMIDITY_THRESHOLD) / (1.0 - LOW_HUMIDITY_THRESHOLD));
        double rainFactor     = clamp01(
                Math.log1p(rainMm) / Math.log1p(RAIN_SATURATION_MM));

        double pressure = tempFactor     * TEMP_WEIGHT
                        + humidityFactor * HUMIDITY_WEIGHT
                        + rainFactor     * RAIN_WEIGHT;

        return clamp01(pressure);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Gaussian bell curve normalised to 1 at the peak.
     *
     * @param x         input value
     * @param peak      value of x at which output = 1
     * @param halfWidth standard deviation (sigma)
     */
    private static double bellCurve(double x, double peak, double halfWidth) {
        double z = (x - peak) / halfWidth;
        return Math.exp(-0.5 * z * z);
    }

    private static double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    /** Utility class — no instances. */
    private RegionalPressure() {}
}
