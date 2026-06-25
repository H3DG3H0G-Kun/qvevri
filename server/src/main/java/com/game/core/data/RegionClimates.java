package com.game.core.data;

/**
 * Static lookup table: {@link Region} → {@link RegionClimate}.
 *
 * <p>KAKHETI carries the zero-offset / unit-multiplier baseline so that
 * {@code KakhetiWeatherModel} produces <em>byte-identical</em> output to its
 * pre-refactor form.  All other regions are expressed as deltas relative to
 * that baseline, per REGIONS-SPEC §2.
 *
 * <h2>Calibration notes (REGIONS-SPEC §2)</h2>
 * <ul>
 *   <li>KARTLI: ~1–1.5 °C cooler mean, slightly drier, more continental
 *       (bigger amplitude), lower humidity.</li>
 *   <li>IMERETI: ~1.5–2 °C cooler, significantly wetter (1.5× rain prob,
 *       1.3× event amount), much higher humidity — key fungal-pressure region.</li>
 *   <li>RACHA_LECHKHUMI: ~2.5 °C cooler, moderately wetter, more year-to-year
 *       variability (higher σ) due to short high-altitude season.</li>
 *   <li>SAMEGRELO: ~0.5 °C warmer than Kakheti, very high rainfall and humidity
 *       (subtropical influence from the Black Sea coast).</li>
 *   <li>GURIA_ADJARA: ~1 °C warmer, wettest of all, highest humidity; true
 *       subtropical coastal climate.</li>
 *   <li>MESKHETI: ~2–3 °C cooler, high-altitude terraced vineyards, moderately
 *       wetter, high vintage variability.</li>
 * </ul>
 */
public final class RegionClimates {

    private RegionClimates() {}

    // ── Precomputed entries ───────────────────────────────────────────────────

    /** Kakheti: the unmodified baseline. */
    private static final RegionClimate KAKHETI =
            RegionClimate.kakhetiBaseline();

    /**
     * Kartli: slightly cooler (−1.2 °C), drier (0.85× rain prob, 0.9× amount),
     * more continental (amp +0.5), lower humidity (−0.04 summer, −0.03 winter).
     */
    private static final RegionClimate KARTLI = new RegionClimate(
            -1.2,   // meanAnnualOffset
             0.5,   // meanAmpOffset — more continental
             0.0,   // vintageWarmthStddevOffset
             0.85,  // rainProbMultiplier — drier
             0.90,  // rainAmountMultiplier
            -0.04,  // humiditySummerOffset
            -0.03   // humidityWinterOffset
    );

    /**
     * Imereti: cooler (−1.8 °C), significantly wetter (1.5× rain prob, 1.3× amount),
     * higher humidity (+0.08 summer, +0.06 winter); elevated fungal pressure.
     */
    private static final RegionClimate IMERETI = new RegionClimate(
            -1.8,   // meanAnnualOffset
            -0.3,   // meanAmpOffset — slightly maritime dampening
             0.0,   // vintageWarmthStddevOffset
             1.50,  // rainProbMultiplier — wetter
             1.30,  // rainAmountMultiplier
             0.08,  // humiditySummerOffset
             0.06   // humidityWinterOffset
    );

    /**
     * Racha-Lechkhumi: cool high-altitude (−2.5 °C), moderately wetter (1.2× prob,
     * 1.15× amount), higher vintage variability (+0.3 °C σ).
     */
    private static final RegionClimate RACHA_LECHKHUMI = new RegionClimate(
            -2.5,   // meanAnnualOffset
             0.3,   // meanAmpOffset — more continental mountains
             0.3,   // vintageWarmthStddevOffset — greater year-to-year spread
             1.20,  // rainProbMultiplier
             1.15,  // rainAmountMultiplier
             0.04,  // humiditySummerOffset
             0.03   // humidityWinterOffset
    );

    /**
     * Samegrelo: marginally warmer (+0.5 °C), very humid (1.6× rain prob,
     * 1.4× amount, +0.10 summer humidity, +0.07 winter humidity).
     */
    private static final RegionClimate SAMEGRELO = new RegionClimate(
             0.5,   // meanAnnualOffset — marginally warmer
            -0.5,   // meanAmpOffset — maritime dampening
             0.0,   // vintageWarmthStddevOffset
             1.60,  // rainProbMultiplier
             1.40,  // rainAmountMultiplier
             0.10,  // humiditySummerOffset
             0.07   // humidityWinterOffset
    );

    /**
     * Guria/Adjara: warmest-wet (+1.0 °C), subtropical; highest rain and
     * humidity (+0.13 summer, +0.09 winter).
     */
    private static final RegionClimate GURIA_ADJARA = new RegionClimate(
             1.0,   // meanAnnualOffset
            -0.8,   // meanAmpOffset — maritime coastal
             0.0,   // vintageWarmthStddevOffset
             1.80,  // rainProbMultiplier — wettest
             1.55,  // rainAmountMultiplier
             0.13,  // humiditySummerOffset
             0.09   // humidityWinterOffset
    );

    /**
     * Meskheti: cool high-altitude (−2.2 °C), terraced vineyards; moderate
     * extra rainfall, higher vintage variability (+0.25 °C σ).
     */
    private static final RegionClimate MESKHETI = new RegionClimate(
            -2.2,   // meanAnnualOffset
             0.2,   // meanAmpOffset — continental mountain pattern
             0.25,  // vintageWarmthStddevOffset
             1.15,  // rainProbMultiplier
             1.10,  // rainAmountMultiplier
             0.03,  // humiditySummerOffset
             0.03   // humidityWinterOffset
    );

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Returns the {@link RegionClimate} for the given region.
     * Never returns {@code null}; unknown/future regions fall back to the
     * Kakheti baseline so no code paths NPE.
     *
     * @param region the wine region (non-null)
     * @return immutable climate record
     */
    public static RegionClimate of(Region region) {
        if (region == null) return KAKHETI;
        return switch (region) {
            case KAKHETI         -> KAKHETI;
            case KARTLI          -> KARTLI;
            case IMERETI         -> IMERETI;
            case RACHA_LECHKHUMI -> RACHA_LECHKHUMI;
            case SAMEGRELO       -> SAMEGRELO;
            case GURIA_ADJARA    -> GURIA_ADJARA;
            case MESKHETI        -> MESKHETI;
        };
    }
}
