package com.game.wine;

/**
 * Deterministic cellar-aging quality model (BACKEND-DEPTH-SPEC §6 winemaking depth).
 *
 * <p>Quality improves over sim-days spent in the cellar with diminishing returns,
 * up to a hard cap.  The formula is:
 * <pre>
 *   agedQuality = baseQuality + maxGain × (1 − e^(−k × agingDays))
 *
 *   where:
 *     agingDays  = currentAbsoluteDay − agingFromDay  (clamped to ≥ 0)
 *     maxGain    = MAX_QUALITY_GAIN  (default 12.0 points)
 *     k          = DECAY_CONSTANT    (default 0.008; controls how fast gains accrue)
 *     cap        = QUALITY_CAP        (100.0 — score never exceeds 100)
 * </pre>
 *
 * <p>At day 0: gain = 0 (base quality unchanged).
 * <br>At day ~200: gain ≈ 10.0 (≈83% of max).
 * <br>At day 500+: gain approaches max_gain asymptotically.
 *
 * <p>All constants are {@code public static final} for test inspection.
 *
 * <p>This class is pure/stateless; it uses only the world-clock absolute day
 * and the two stored values ({@code baseQuality}, {@code agingFromDay}) — no
 * wall clock, no RNG.
 */
public final class AgingModel {

    /** Maximum quality points that aging can add above the base quality. */
    public static final double MAX_QUALITY_GAIN = 12.0;

    /**
     * Exponential decay constant controlling how quickly gains accrue.
     * Higher k = faster early improvement and flatter long tail.
     */
    public static final double DECAY_CONSTANT   = 0.008;

    /** Hard ceiling on the quality score. */
    public static final double QUALITY_CAP      = 100.0;

    private AgingModel() { /* utility class */ }

    /**
     * Computes the quality for a CellarItem aged since {@code agingFromDay},
     * evaluated at {@code currentAbsoluteDay}.
     *
     * <p><b>Gating:</b> if {@code agingFromDay} is {@code null} (instant-harvest
     * path / no fermentation started) this method returns {@code baseQuality}
     * unchanged — the default path is byte-identical.
     *
     * @param baseQuality       quality at the time fermentation completed (0..100)
     * @param agingFromDay      absolute world-clock day aging started, or {@code null}
     * @param currentAbsDay     current world-clock absolute day
     * @return the current quality score (capped at {@link #QUALITY_CAP})
     */
    public static double agedQuality(double baseQuality, Long agingFromDay,
                                     long currentAbsDay) {
        if (agingFromDay == null) {
            return baseQuality; // no aging — instant-harvest path, unchanged
        }
        long agingDays = Math.max(0L, currentAbsDay - agingFromDay);
        double gain = MAX_QUALITY_GAIN * (1.0 - Math.exp(-DECAY_CONSTANT * agingDays));
        return Math.min(QUALITY_CAP, baseQuality + gain);
    }
}
