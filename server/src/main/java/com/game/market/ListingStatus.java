package com.game.market;

/**
 * Lifecycle status of a {@link MarketListing}.
 */
public enum ListingStatus {
    /** Listing is open and visible to buyers. */
    ACTIVE,
    /** The item has been purchased. */
    SOLD,
    /** The seller cancelled the listing (item is un-escrowed). */
    CANCELLED
}
