package com.game.econ;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * In-memory peer-to-peer market (the "Bazari").
 *
 * <h2>Life-cycle</h2>
 * <ol>
 *   <li>A seller calls {@link #list} to post a {@link Listing}. The item is
 *       removed from the seller's {@link Inventory} and held in escrow by the
 *       Bazari until the listing is exercised or cancelled.</li>
 *   <li>A buyer calls {@link #buy} with the listing ID. The item is
 *       transferred from escrow to the buyer's inventory and a {@link Trade}
 *       is returned.</li>
 * </ol>
 *
 * <h2>Determinism</h2>
 * Listings are stored in a {@link LinkedHashMap} keyed by listing ID so that
 * {@link #browse()} always returns them in insertion order. IDs are monotonic
 * integer counters (no UUID/random).
 *
 * <p>This class is not thread-safe — the game simulation is single-threaded.
 *
 * <p>GDD Part 7/8 — Phase 2 economy, econ-core lane.
 */
public final class Bazari {

    /** Active listings keyed by listing ID; LinkedHashMap preserves insertion order. */
    private final LinkedHashMap<String, Listing> listings = new LinkedHashMap<>();

    /** Monotonic counter used to generate deterministic listing IDs. */
    private int nextId = 1;

    // ── Posting ───────────────────────────────────────────────────────────────

    /**
     * Post a sell offer.
     *
     * <p>The {@code item} is removed from {@code sellerInv} and held in escrow.
     * If the item is not in the seller's inventory an
     * {@link IllegalArgumentException} is thrown and no state is mutated.
     *
     * @param sellerId  the posting player's ID
     * @param sellerInv the seller's inventory (must contain {@code item})
     * @param item      the item to sell
     * @param askPrice  the requested price per natural unit (GEL)
     * @return a non-blank listing ID that the buyer must pass to {@link #buy}
     * @throws IllegalArgumentException if {@code item} is not in {@code sellerInv}
     */
    public String list(String sellerId, Inventory sellerInv, Item item, double askPrice) {
        if (!sellerInv.contains(item)) {
            throw new IllegalArgumentException(
                    "Item '" + item.id() + "' not found in inventory of '" + sellerId + "'");
        }
        sellerInv.remove(item);

        String id = "L" + nextId++;
        listings.put(id, new Listing(id, sellerId, item, askPrice));
        return id;
    }

    // ── Buying ────────────────────────────────────────────────────────────────

    /**
     * Execute a spot purchase.
     *
     * <p>The item is moved from Bazari escrow to {@code buyerInv}. The listing
     * is removed from the active board.
     *
     * @param buyerId   the purchasing player's ID
     * @param buyerInv  the buyer's inventory (receives the item)
     * @param listingId the ID returned by a prior {@link #list} call
     * @return the completed {@link Trade}
     * @throws IllegalArgumentException if no active listing with {@code listingId} exists
     */
    public Trade buy(String buyerId, Inventory buyerInv, String listingId) {
        Listing listing = listings.remove(listingId);
        if (listing == null) {
            throw new IllegalArgumentException(
                    "No active listing with id '" + listingId + "'");
        }
        buyerInv.add(listing.item());
        return new Trade(buyerId, listing.sellerId(), listing.item(), listing.askPrice());
    }

    // ── Browse ────────────────────────────────────────────────────────────────

    /**
     * Return an unmodifiable snapshot of all active listings in insertion order.
     *
     * @return unmodifiable list; never {@code null}
     */
    public List<Listing> browse() {
        return Collections.unmodifiableList(new ArrayList<>(listings.values()));
    }
}
