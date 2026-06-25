package com.game.export;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Static catalog of the four supported foreign export markets.
 *
 * <h2>Markets</h2>
 * <pre>
 * ID                   priceMultiplier  tariffRate  net multiplier (approx)
 * russia               1.30             0.10        1.17×
 * byzantium            1.50             0.15        1.275×
 * persia               1.20             0.08        1.104×
 * poland_lithuania     1.15             0.06        1.081×
 * </pre>
 *
 * <p>Ordering note: byzantium is the highest-paying market (net multiplier 1.275×)
 * while poland_lithuania is the lowest-tariff but also lowest-priced option.
 * This spread ensures the spec test "byzantium pays strictly more than poland_lithuania" holds.
 *
 * <p>All state is static and immutable; no Spring bean is required.
 */
public final class ForeignMarketCatalog {

    private ForeignMarketCatalog() { /* utility class */ }

    /** All four supported foreign markets, in stable declaration order. */
    private static final List<ForeignMarket> MARKETS = List.of(
            new ForeignMarket(
                    "russia",
                    "Russian Empire",
                    1.30,
                    0.10,
                    "Large aristocratic demand for premium Georgian wine; moderate tariffs"),
            new ForeignMarket(
                    "byzantium",
                    "Byzantine Remnants",
                    1.50,
                    0.15,
                    "Refined palate, highest prices; steep customs duties on imports"),
            new ForeignMarket(
                    "persia",
                    "Persian Safavid Markets",
                    1.20,
                    0.08,
                    "Steady merchant demand; competitive pricing, low tariff regime"),
            new ForeignMarket(
                    "poland_lithuania",
                    "Polish–Lithuanian Commonwealth",
                    1.15,
                    0.06,
                    "Niche noble market; lowest tariffs but modest price premium")
    );

    /** Fast id→market lookup map, built once at class-load time. */
    private static final Map<String, ForeignMarket> BY_ID = MARKETS.stream()
            .collect(Collectors.toUnmodifiableMap(ForeignMarket::id, Function.identity()));

    /**
     * Returns an unmodifiable view of all four markets.
     *
     * @return all markets in declaration order
     */
    public static List<ForeignMarket> all() {
        return MARKETS;
    }

    /**
     * Looks up a market by its string id.
     *
     * @param id market identifier (e.g. "russia", "byzantium")
     * @return the matching {@link ForeignMarket}, or {@link Optional#empty()} if unknown
     */
    public static Optional<ForeignMarket> find(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(BY_ID.get(id.toLowerCase()));
    }
}
