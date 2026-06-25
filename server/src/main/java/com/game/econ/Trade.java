package com.game.econ;

/**
 * Record of a completed spot transaction on {@link Bazari}.
 *
 * <p>Immutable; created and returned by {@link Bazari#buy}.
 *
 * @param buyerId  the player who purchased the item
 * @param sellerId the player who sold the item
 * @param item     the item that changed hands
 * @param price    the agreed price per natural unit (GEL); equals the listing
 *                 ask price for a SPOT contract
 *
 * <p>GDD Part 7/8 — Phase 2 economy, econ-core lane.
 */
public record Trade(
        String buyerId,
        String sellerId,
        Item   item,
        double price
) {}
