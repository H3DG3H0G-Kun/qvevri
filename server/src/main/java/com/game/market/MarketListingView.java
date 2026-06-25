package com.game.market;

/**
 * Read-only projection returned by GET /api/market.
 *
 * <p>Enriches a {@link MarketListing} with the {@code suggestedPrice} computed
 * by {@link com.game.econ.WinePricer}.
 *
 * @param listing        the raw listing entity
 * @param cellarItem     the item being sold (for display)
 * @param suggestedPrice per-unit price in GEL from WinePricer
 */
public record MarketListingView(
        MarketListing listing,
        CellarItem cellarItem,
        double suggestedPrice
) {}
