package com.game.core.data;

/**
 * Per-region climate knobs consumed by the weather model.
 *
 * <p>All values are expressed as <em>offsets or multipliers relative to the
 * Kakheti (HUMUS_CARBONATE Alazani Valley) baseline</em> — except for the
 * Kakheti entry itself, which carries zero-offsets / unit multipliers so
 * that the weather model produces <em>exactly</em> the same numbers it
 * produced before this refactor.
 *
 * <h2>Field semantics</h2>
 * <ul>
 *   <li>{@code meanAnnualOffset} — added to the baseline {@code MEAN_ANNUAL}
 *       constant (13.0 °C for Kakheti) before the cosine seasonal curve is
 *       applied.  Negative = cooler region.</li>
 *   <li>{@code meanAmpOffset} — added to the seasonal cosine half-amplitude
 *       ({@code MEAN_AMP} = 11.0 °C for Kakheti). A more continental climate
 *       has a larger amplitude; maritime climates have a smaller one.</li>
 *   <li>{@code vintageWarmthStddevOffset} — added to the per-vintage warmth
 *       standard deviation ({@code VINTAGE_WARMTH_STDDEV_C} = 1.5 °C for
 *       Kakheti).  High-altitude short-season regions carry more vintage
 *       variability.</li>
 *   <li>{@code rainProbMultiplier} — multiplied into every month's rain-day
 *       probability.  Values &gt; 1.0 = wetter; &lt; 1.0 = drier.</li>
 *   <li>{@code rainAmountMultiplier} — multiplied into the exponential rain
 *       event mean (mm per event).</li>
 *   <li>{@code humiditySummerOffset} — added to the summer-baseline relative
 *       humidity ({@code HUMIDITY_SUMMER} = 0.55 for Kakheti).</li>
 *   <li>{@code humidityWinterOffset} — added to the winter-baseline relative
 *       humidity ({@code HUMIDITY_WINTER} = 0.75 for Kakheti).</li>
 * </ul>
 *
 * <p>Frozen per REGIONS-SPEC §2.
 *
 * @param meanAnnualOffset          °C offset on top of the 13.0 °C Kakheti baseline
 * @param meanAmpOffset             °C offset on seasonal cosine amplitude (base 11.0)
 * @param vintageWarmthStddevOffset °C offset on per-vintage warmth σ (base 1.5)
 * @param rainProbMultiplier        multiplier on monthly rain-day probability
 * @param rainAmountMultiplier      multiplier on mean rain per event (mm)
 * @param humiditySummerOffset      offset on summer humidity (base 0.55)
 * @param humidityWinterOffset      offset on winter humidity (base 0.75)
 */
public record RegionClimate(
        double meanAnnualOffset,
        double meanAmpOffset,
        double vintageWarmthStddevOffset,
        double rainProbMultiplier,
        double rainAmountMultiplier,
        double humiditySummerOffset,
        double humidityWinterOffset
) {
    /**
     * Convenience factory: the Kakheti baseline (all zero-offsets, unit multipliers).
     * Guaranteed to reproduce the original hardcoded output of {@code KakhetiWeatherModel}
     * without any floating-point delta.
     */
    public static RegionClimate kakhetiBaseline() {
        return new RegionClimate(0.0, 0.0, 0.0, 1.0, 1.0, 0.0, 0.0);
    }
}
