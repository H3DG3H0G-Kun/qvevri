package com.game.econ;

/**
 * An open sell-offer posted on {@link Bazari}.
 *
 * <p>Immutable value object. The {@code id} is assigned by {@link Bazari}
 * and is deterministic (sequential counter, no random source).
 *
 * @param id       unique listing identifier within this Bazari instance
 * @param sellerId the player who posted the offer
 * @param item     the item being offered
 * @param askPrice requested price per natural unit (GEL)
 *
 * <p>GDD Part 7/8 — Phase 2 economy, econ-core lane.
 */
public record Listing(
        String   id,
        String   sellerId,
        Item     item,
        double   askPrice
) {}
