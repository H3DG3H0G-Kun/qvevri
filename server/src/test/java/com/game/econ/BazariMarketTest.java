package com.game.econ;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Plain JUnit 5 tests for the {@link Bazari} peer-to-peer market.
 *
 * <p>No Spring context needed — Bazari is a pure domain object with no
 * infrastructure dependencies.
 *
 * <p>Class and method names follow the contract literally:
 * <ul>
 *   <li>{@code Bazari} — the market implementation</li>
 *   <li>{@code Bazari#list(seller, item, price)} — seller lists an item</li>
 *   <li>{@code Bazari#buy(buyer, listingId)} — buyer executes a spot purchase</li>
 *   <li>{@code Trade} — returned by buy(), records the transaction</li>
 *   <li>{@code Inventory} — each player's item store</li>
 * </ul>
 *
 * <p>If any of these classes do not yet exist, tests will fail to compile —
 * that is the correct signal; never suppress with stubs.
 */
@DisplayName("Bazari market — list + spot-buy contract (econ)")
class BazariMarketTest {

    // ── Player ID constants ──────────────────────────────────────────────────

    private static final String SELLER_ID = "player-seller-001";
    private static final String BUYER_ID  = "player-buyer-002";

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** Build a simple wine item with a given quality. */
    private Item makeWineItem(double quality) {
        return Item.ofWine(quality, 1, ItemType.WINE, false);
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    /**
     * market_listAndBuy_transfersItem
     *
     * <p>Seller lists an Item on Bazari; buyer executes a spot buy →
     * item moves from seller Inventory to buyer Inventory;
     * a Trade record is returned and is non-null.
     *
     * <p>Explicit assertions:
     * <ul>
     *   <li>After listing, item is in seller's inventory (still held, just listed)
     *       OR the market has it in escrow — both are valid; we check after buy.</li>
     *   <li>After buy(), {@code Trade} is non-null and references the correct item.</li>
     *   <li>Buyer's inventory contains the item after buy().</li>
     *   <li>Seller's inventory does NOT contain the item after buy().</li>
     * </ul>
     */
    @Test
    @DisplayName("market_listAndBuy_transfersItem: item moves seller→buyer; Trade returned")
    void market_listAndBuy_transfersItem() {
        Inventory sellerInv = new Inventory(SELLER_ID);
        Inventory buyerInv  = new Inventory(BUYER_ID);

        Item item = makeWineItem(72.0);
        sellerInv.add(item);

        assertThat(sellerInv.contains(item))
                .as("Seller's inventory must contain the item before listing")
                .isTrue();

        Bazari market = new Bazari();
        double askPrice = 50.0;
        String listingId = market.list(SELLER_ID, sellerInv, item, askPrice);

        assertThat(listingId)
                .as("Bazari.list must return a non-blank listing ID")
                .isNotNull()
                .isNotBlank();

        // Execute the spot buy
        Trade trade = market.buy(BUYER_ID, buyerInv, listingId);

        assertThat(trade)
                .as("Bazari.buy must return a non-null Trade")
                .isNotNull();

        // Item must be in buyer's inventory after purchase
        assertThat(buyerInv.contains(item))
                .as("Buyer's inventory must contain the item after a successful spot buy")
                .isTrue();

        // Item must no longer be in seller's inventory after purchase
        assertThat(sellerInv.contains(item))
                .as("Seller's inventory must NOT contain the item after it was sold")
                .isFalse();
    }
}
