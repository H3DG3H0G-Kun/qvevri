package com.game.econ;

import com.game.profession.WineGrade;

/**
 * Pure, stateless wine-pricing function.
 *
 * <h2>Formula</h2>
 * <pre>
 * price = BASE_RATE[type]
 *       × qualityFactor   = (quality / QUALITY_PIVOT) ^ QUALITY_EXPONENT
 *       × vintageFactor   = min(VINTAGE_MAX, 1 + ageYears × VINTAGE_PER_YEAR)
 *       × appellationFactor = APPELLATION_FACTOR  (if item.appellationOk(), else 1.0)
 *       × scarcityFactor  = 2 × SCARCITY_REF / (SCARCITY_REF + supply)
 * </pre>
 *
 * <p>All constants are {@code public static final} so callers and tests can
 * inspect them. No mutable state; every method is static.
 *
 * <p>GDD Part 7/8 — Phase 2 economy, econ-core lane.
 */
public final class WinePricer {

    private WinePricer() { /* utility class */ }

    // ── Quality factor constants ───────────────────────────────────────────────

    /** Pivot quality that yields a qualityFactor of exactly 1.0. */
    public static final double QUALITY_PIVOT    = 50.0;

    /** Power exponent applied to the normalised quality. */
    public static final double QUALITY_EXPONENT = 1.5;

    // ── Vintage / age factor constants ────────────────────────────────────────

    /** Additional price multiplier earned per year of age. */
    public static final double VINTAGE_PER_YEAR = 0.10;

    /** Hard ceiling on the vintage factor. */
    public static final double VINTAGE_MAX      = 2.0;

    // ── Appellation premium ───────────────────────────────────────────────────

    /** Multiplier applied when {@link Item#appellationOk()} is {@code true}. */
    public static final double APPELLATION_FACTOR = 1.30;

    // ── Scarcity factor constants ─────────────────────────────────────────────

    /**
     * Reference supply level at which the scarcity factor equals 1.0.
     * Above this supply the factor falls below 1.0 (abundant); below it
     * the factor rises above 1.0 (scarce).
     *
     * <p>Formula: {@code 2 × REF / (REF + supply)}.
     */
    public static final double SCARCITY_REF = 100.0;

    // ── Per-type base rates (GEL per litre / natural unit) ───────────────────

    /** Base price for {@link ItemType#GRAPES} (GEL/kg). */
    public static final double BASE_GRAPES       =  0.50;

    /** Base price for {@link ItemType#MUST} (GEL/litre). */
    public static final double BASE_MUST         =  1.20;

    /** Base price for {@link ItemType#YOUNG_WINE} (GEL/litre). */
    public static final double BASE_YOUNG_WINE   =  3.00;

    /** Base price for {@link ItemType#AGED_WINE} (GEL/litre). */
    public static final double BASE_AGED_WINE    =  6.00;

    /** Base price for {@link ItemType#CHACHA_BRANDY} (GEL/litre). */
    public static final double BASE_CHACHA_BRANDY = 8.00;

    /** Base price for {@link ItemType#WINE} (GEL/litre) — alias of AGED_WINE rate. */
    public static final double BASE_WINE         = BASE_AGED_WINE;

    // ── Pricing ───────────────────────────────────────────────────────────────

    /**
     * Compute the per-unit price for {@code item} given current {@code ctx}.
     *
     * <p>The vintage age is computed as {@code currentYear - item.vintageYear()}.
     * Because the economy layer has no live clock, the caller is expected to
     * supply items with a {@code vintageYear} that reflects real elapsed age.
     * When {@code vintageYear <= 0} the age defaults to 0 (no vintage premium).
     *
     * @param item the item to price; must not be {@code null}
     * @param ctx  current market context; must not be {@code null}
     * @return price per natural unit (GEL), always {@code > 0}
     */
    public static double price(Item item, MarketContext ctx) {
        double base           = baseRate(item.type());
        double qualityFactor  = qualityFactor(item.quality());
        double vintageFactor  = vintageFactor(item.vintageYear());
        double appellation    = item.appellationOk() ? APPELLATION_FACTOR : 1.0;
        double scarcity       = scarcityFactor(ctx.comparableSupplyL());

        return base * qualityFactor * vintageFactor * appellation * scarcity;
    }

    // ── Factor helpers (package-visible for unit tests) ───────────────────────

    static double qualityFactor(double quality) {
        return Math.pow(quality / QUALITY_PIVOT, QUALITY_EXPONENT);
    }

    static double vintageFactor(int vintageYear) {
        // Age is measured from year 1 onward; year 0 or negative → no premium.
        int ageYears = vintageYear > 0 ? vintageYear : 0;
        return Math.min(VINTAGE_MAX, 1.0 + ageYears * VINTAGE_PER_YEAR);
    }

    static double scarcityFactor(double supply) {
        return (2.0 * SCARCITY_REF) / (SCARCITY_REF + supply);
    }

    // ── §6 WineGrade quality premium ─────────────────────────────────────────

    /**
     * Multiplier applied when a {@link WineGrade} is certified (score ≥ 85).
     * Absent grade → multiplier is exactly 1.0 (price unchanged).
     * This constant is {@code public static final} so tests can inspect it.
     */
    public static final double GRADE_CERTIFIED_PREMIUM = 1.15;

    /**
     * Overload that accepts an optional certified {@link WineGrade}.
     *
     * <p><b>Gating rule (§6 golden rule):</b>
     * <ul>
     *   <li>{@code grade == null} → calls the base {@link #price(Item, MarketContext)}
     *       unmodified; result is byte-identical to the no-grade path.</li>
     *   <li>{@code grade != null && !grade.isCertified()} → same as null; no premium.</li>
     *   <li>{@code grade != null && grade.isCertified()} → base price ×
     *       {@link #GRADE_CERTIFIED_PREMIUM}.</li>
     * </ul>
     *
     * <p>Existing {@link WinePricerTest} calls only the 2-arg overload and therefore
     * sees zero change; the grade premium is only visible via this 3-arg overload.
     *
     * @param item  the item to price; must not be {@code null}
     * @param ctx   current market context; must not be {@code null}
     * @param grade the WineGrade for this cellar item, or {@code null} if absent
     * @return price per natural unit (GEL), always {@code > 0}
     */
    public static double price(Item item, MarketContext ctx, WineGrade grade) {
        double base = price(item, ctx); // delegate to existing 2-arg method (fully gated)
        if (grade == null || !grade.isCertified()) {
            return base; // absent or uncertified grade → no change whatsoever
        }
        return base * GRADE_CERTIFIED_PREMIUM;
    }

    // ── Base rate lookup ──────────────────────────────────────────────────────

    private static double baseRate(ItemType type) {
        return switch (type) {
            case GRAPES        -> BASE_GRAPES;
            case MUST          -> BASE_MUST;
            case YOUNG_WINE    -> BASE_YOUNG_WINE;
            case AGED_WINE     -> BASE_AGED_WINE;
            case CHACHA_BRANDY -> BASE_CHACHA_BRANDY;
            case WINE          -> BASE_WINE;
        };
    }
}
