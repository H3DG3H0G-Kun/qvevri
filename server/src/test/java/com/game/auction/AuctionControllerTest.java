package com.game.auction;

import com.game.account.AccountTestHelper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.game.account.AccountTestHelper.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for /api/auction/** (LANE AUCTION).
 *
 * <p>Scenarios covered:
 * <ol>
 *   <li>Create a CELLAR_ITEM auction → item is escrowed (no longer in seller's cellar).</li>
 *   <li>Create a GOODS auction → goods are reserved (decremented from seller's inventory).</li>
 *   <li>Bid raises currentBidGel on the auction.</li>
 *   <li>Too-low bid → 400.</li>
 *   <li>Cannot bid on your own auction → 400.</li>
 *   <li>Settle after advancing the clock past endDay → seller paid, item transferred to winner.</li>
 *   <li>No-bid auction settles by returning the item to seller (GOODS grant-back).</li>
 *   <li>Double-settle is a no-op (returns SETTLED auction without error).</li>
 * </ol>
 *
 * <p>Uses {@link AccountTestHelper} static helpers and {@code Map<String,Object>}
 * bodies exactly like the TradeControllerTest pattern.
 *
 * <p>IMPORTANT compilation rules followed:
 * <ul>
 *   <li>Response maps called with {@code containsKey} are typed as
 *       {@code Map<String,Object>} (not {@code Map<?,?>}) to avoid the
 *       generics-capture compile error.</li>
 *   <li>List endpoints are deserialized with {@code List.class}.</li>
 *   <li>4xx-returning calls use {@code String.class} (error object, not a Map/List).</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AuctionControllerTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    private String base() {
        return "http://localhost:" + port;
    }

    private static String uniqueName() {
        return "au_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 1. Create CELLAR_ITEM auction → item is escrowed
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("createCellarItemAuction_escrowsItem")
    @SuppressWarnings("unchecked")
    void createCellarItemAuction_escrowsItem() {
        String token = registerAndGetToken(rest, base());
        Number charId = createCharacter(rest, base(), token, uniqueName());
        long cid = charId.longValue();

        // Grow a cellar item to auction
        Number cellarItemId = growAndGetItemId(token, cid);

        // Create the auction
        Map<String, Object> body = Map.of(
                "characterId",  cid,
                "kind",         "CELLAR_ITEM",
                "refId",        String.valueOf(cellarItemId.longValue()),
                "quantity",     1.0,
                "startBidGel",  10.0,
                "durationDays", 5);

        ResponseEntity<Map> resp = rest.postForEntity(
                base() + "/api/auction/create",
                withToken(body, token),
                Map.class);

        assertThat(resp.getStatusCode())
                .as("POST /api/auction/create must return 200")
                .isEqualTo(HttpStatus.OK);

        Map<String, Object> auction = (Map<String, Object>) resp.getBody();
        assertThat(auction).isNotNull();
        assertThat(auction.get("auctionStatus")).isEqualTo("OPEN");
        assertThat(auction.get("kind")).isEqualTo("CELLAR_ITEM");
        assertThat(auction.get("id")).isNotNull();

        // The item must no longer appear in the seller's cellar (it is escrowed)
        ResponseEntity<List> cellarResp = rest.exchange(
                base() + "/api/cellar/" + cid,
                HttpMethod.GET,
                getWithToken(token),
                List.class);
        assertThat(cellarResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<?> cellar = cellarResp.getBody();
        assertThat(cellar).isNotNull();

        boolean itemVisible = cellar.stream()
                .anyMatch(o -> cellarItemId.longValue() ==
                        ((Number) ((Map<?, ?>) o).get("id")).longValue());
        assertThat(itemVisible)
                .as("Escrowed cellar item must NOT appear in seller's open cellar")
                .isFalse();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. Create GOODS auction → goods reserved (decremented from inventory)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("createGoodsAuction_reservesGoods")
    @SuppressWarnings("unchecked")
    void createGoodsAuction_reservesGoods() {
        String token = registerAndGetToken(rest, base());
        Number charId = createCharacter(rest, base(), token, uniqueName());
        long cid = charId.longValue();

        // Buy 2 pruning_shears; wallet 100 GEL, cost 45 each = 10 left after
        buyGoods(token, cid, "pruning_shears", 2.0);

        // Auction 1 unit
        Map<String, Object> body = Map.of(
                "characterId",  cid,
                "kind",         "GOODS",
                "refId",        "pruning_shears",
                "quantity",     1.0,
                "startBidGel",  20.0,
                "durationDays", 3);

        ResponseEntity<Map> resp = rest.postForEntity(
                base() + "/api/auction/create",
                withToken(body, token),
                Map.class);

        assertThat(resp.getStatusCode())
                .as("POST /api/auction/create (GOODS) must return 200")
                .isEqualTo(HttpStatus.OK);

        Map<String, Object> auction = (Map<String, Object>) resp.getBody();
        assertThat(auction).isNotNull();
        assertThat(auction.get("auctionStatus")).isEqualTo("OPEN");

        // Seller's inventory should now show only 1 unit (1 was reserved)
        List<?> inv = getInventory(token, cid);
        double remaining = inv.stream()
                .filter(o -> "pruning_shears".equals(((Map<?, ?>) o).get("goodTypeId")))
                .mapToDouble(o -> ((Number) ((Map<?, ?>) o).get("quantity")).doubleValue())
                .sum();
        assertThat(remaining)
                .as("Seller should have 1 pruning_shears remaining after reserving 1")
                .isEqualTo(1.0);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. Bid raises currentBidGel
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("bid_raisesCurrentBid")
    @SuppressWarnings("unchecked")
    void bid_raisesCurrentBid() {
        // Seller
        String sellerToken = registerAndGetToken(rest, base());
        Number sellerCharId = createCharacter(rest, base(), sellerToken, uniqueName());
        long scid = sellerCharId.longValue();
        buyGoods(sellerToken, scid, "pruning_shears", 1.0);

        Number auctionId = createGoodsAuction(sellerToken, scid, "pruning_shears", 1.0, 5.0, 10);

        // Bidder
        String bidderToken = registerAndGetToken(rest, base());
        Number bidderCharId = createCharacter(rest, base(), bidderToken, uniqueName());
        long bcid = bidderCharId.longValue();

        // Place a bid at 20 GEL (>= startBid of 5)
        Map<String, Object> bidBody = Map.of("characterId", bcid, "amountGel", 20.0);
        ResponseEntity<Map> bidResp = rest.postForEntity(
                base() + "/api/auction/" + auctionId.longValue() + "/bid",
                withToken(bidBody, bidderToken),
                Map.class);

        assertThat(bidResp.getStatusCode())
                .as("POST /bid must return 200")
                .isEqualTo(HttpStatus.OK);

        Map<String, Object> updatedAuction = (Map<String, Object>) bidResp.getBody();
        assertThat(updatedAuction).isNotNull();
        assertThat(((Number) updatedAuction.get("currentBidGel")).doubleValue())
                .as("currentBidGel must be updated to the bid amount")
                .isEqualTo(20.0);
        assertThat(((Number) updatedAuction.get("highBidderCharacterId")).longValue())
                .as("highBidderCharacterId must be set to the bidder")
                .isEqualTo(bcid);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. Too-low bid → 400
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("bid_tooLow_400")
    @SuppressWarnings("unchecked")
    void bid_tooLow_400() {
        // Seller
        String sellerToken = registerAndGetToken(rest, base());
        Number sellerCharId = createCharacter(rest, base(), sellerToken, uniqueName());
        long scid = sellerCharId.longValue();
        buyGoods(sellerToken, scid, "pruning_shears", 1.0);
        Number auctionId = createGoodsAuction(sellerToken, scid, "pruning_shears", 1.0, 50.0, 10);

        // Bidder
        String bidderToken = registerAndGetToken(rest, base());
        Number bidderCharId = createCharacter(rest, base(), bidderToken, uniqueName());
        long bcid = bidderCharId.longValue();

        // Bid below startBidGel (50)
        Map<String, Object> bidBody = Map.of("characterId", bcid, "amountGel", 5.0);
        ResponseEntity<String> resp = rest.postForEntity(
                base() + "/api/auction/" + auctionId.longValue() + "/bid",
                withToken(bidBody, bidderToken),
                String.class);

        assertThat(resp.getStatusCode())
                .as("Bid below startBidGel must return 400")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 5. Cannot bid on your own auction → 400
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("bid_ownAuction_400")
    @SuppressWarnings("unchecked")
    void bid_ownAuction_400() {
        String token = registerAndGetToken(rest, base());
        Number charId = createCharacter(rest, base(), token, uniqueName());
        long cid = charId.longValue();

        buyGoods(token, cid, "pruning_shears", 1.0);
        Number auctionId = createGoodsAuction(token, cid, "pruning_shears", 1.0, 10.0, 10);

        Map<String, Object> bidBody = Map.of("characterId", cid, "amountGel", 15.0);
        ResponseEntity<String> resp = rest.postForEntity(
                base() + "/api/auction/" + auctionId.longValue() + "/bid",
                withToken(bidBody, token),
                String.class);

        assertThat(resp.getStatusCode())
                .as("Self-bid must return 400")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 6. Settle after advancing clock → seller paid, item to winner
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("settle_afterEndDay_paysSellerTransfersItem")
    @SuppressWarnings("unchecked")
    void settle_afterEndDay_paysSellerTransfersItem() {
        // Seller
        String sellerToken = registerAndGetToken(rest, base());
        Number sellerCharId = createCharacter(rest, base(), sellerToken, uniqueName());
        long scid = sellerCharId.longValue();
        buyGoods(sellerToken, scid, "pruning_shears", 1.0);
        double sellerWalletBefore = getWallet(sellerToken, scid);

        // Auction: duration 3 days
        Number auctionId = createGoodsAuction(sellerToken, scid, "pruning_shears", 1.0, 10.0, 3);

        // Bidder
        String bidderToken = registerAndGetToken(rest, base());
        Number bidderCharId = createCharacter(rest, base(), bidderToken, uniqueName());
        long bcid = bidderCharId.longValue();
        double bidderWalletBefore = getWallet(bidderToken, bcid); // 100 GEL

        double bidAmount = 25.0;
        Map<String, Object> bidBody = Map.of("characterId", bcid, "amountGel", bidAmount);
        ResponseEntity<Map> bidResp = rest.postForEntity(
                base() + "/api/auction/" + auctionId.longValue() + "/bid",
                withToken(bidBody, bidderToken),
                Map.class);
        assertThat(bidResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Advance clock past endDay (auction endDay = currentDay + 3)
        advanceClock(4);

        // Explicit settle (any character can trigger it; use seller for convenience)
        Map<String, Object> settleBody = Map.of("characterId", scid);
        ResponseEntity<Map> settleResp = rest.postForEntity(
                base() + "/api/auction/" + auctionId.longValue() + "/settle",
                withToken(settleBody, sellerToken),
                Map.class);

        assertThat(settleResp.getStatusCode())
                .as("POST /settle must return 200")
                .isEqualTo(HttpStatus.OK);

        Map<String, Object> settled = (Map<String, Object>) settleResp.getBody();
        assertThat(settled).isNotNull();
        assertThat(settled.get("auctionStatus")).isEqualTo("SETTLED");

        // Seller wallet must have increased by bid amount
        double sellerWalletAfter = getWallet(sellerToken, scid);
        assertThat(sellerWalletAfter)
                .as("Seller wallet must increase by winning bid")
                .isEqualTo(sellerWalletBefore + bidAmount); // sellerWalletBefore already post-shop-buy

        // Bidder wallet must have decreased by bid amount
        double bidderWalletAfter = getWallet(bidderToken, bcid);
        assertThat(bidderWalletAfter)
                .as("Bidder wallet must decrease by winning bid")
                .isEqualTo(bidderWalletBefore - bidAmount);

        // Winner must own pruning_shears
        List<?> winnerInv = getInventory(bidderToken, bcid);
        boolean hasGoods = winnerInv.stream()
                .anyMatch(o -> "pruning_shears".equals(((Map<?, ?>) o).get("goodTypeId")));
        assertThat(hasGoods)
                .as("Winner must own pruning_shears after settlement")
                .isTrue();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 7. No-bid settle → item returned to seller
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("settle_noBid_returnsItemToSeller")
    @SuppressWarnings("unchecked")
    void settle_noBid_returnsItemToSeller() {
        String sellerToken = registerAndGetToken(rest, base());
        Number sellerCharId = createCharacter(rest, base(), sellerToken, uniqueName());
        long scid = sellerCharId.longValue();
        buyGoods(sellerToken, scid, "hoe", 1.0);

        // Auction 1 hoe; no bids
        Number auctionId = createGoodsAuction(sellerToken, scid, "hoe", 1.0, 50.0, 2);

        // Seller inventory should be empty now (goods reserved)
        List<?> invBefore = getInventory(sellerToken, scid);
        boolean hasHoeBefore = invBefore.stream()
                .anyMatch(o -> "hoe".equals(((Map<?, ?>) o).get("goodTypeId")));
        assertThat(hasHoeBefore)
                .as("Hoe must be reserved (not visible) after auction creation")
                .isFalse();

        // Advance past endDay
        advanceClock(3);

        // Settle (no bids)
        Map<String, Object> settleBody = Map.of("characterId", scid);
        ResponseEntity<Map> settleResp = rest.postForEntity(
                base() + "/api/auction/" + auctionId.longValue() + "/settle",
                withToken(settleBody, sellerToken),
                Map.class);

        assertThat(settleResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> settled = (Map<String, Object>) settleResp.getBody();
        assertThat(settled).isNotNull();
        assertThat(settled.get("auctionStatus")).isEqualTo("SETTLED");
        assertThat(settled.get("highBidderCharacterId")).isNull();

        // Seller gets the hoe back
        List<?> invAfter = getInventory(sellerToken, scid);
        boolean hasHoeAfter = invAfter.stream()
                .anyMatch(o -> "hoe".equals(((Map<?, ?>) o).get("goodTypeId")));
        assertThat(hasHoeAfter)
                .as("Hoe must be returned to seller after no-bid settlement")
                .isTrue();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 8. Double-settle is a no-op
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("settle_doubleSettle_noOp")
    @SuppressWarnings("unchecked")
    void settle_doubleSettle_noOp() {
        String sellerToken = registerAndGetToken(rest, base());
        Number sellerCharId = createCharacter(rest, base(), sellerToken, uniqueName());
        long scid = sellerCharId.longValue();
        buyGoods(sellerToken, scid, "hoe", 1.0);
        Number auctionId = createGoodsAuction(sellerToken, scid, "hoe", 1.0, 5.0, 1);

        advanceClock(2);

        Map<String, Object> settleBody = Map.of("characterId", scid);

        // First settle — should succeed
        ResponseEntity<Map> first = rest.postForEntity(
                base() + "/api/auction/" + auctionId.longValue() + "/settle",
                withToken(settleBody, sellerToken),
                Map.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Map<String, Object>) first.getBody()).get("auctionStatus")).isEqualTo("SETTLED");

        // Second settle — idempotent, must also return 200 with SETTLED
        ResponseEntity<Map> second = rest.postForEntity(
                base() + "/api/auction/" + auctionId.longValue() + "/settle",
                withToken(settleBody, sellerToken),
                Map.class);
        assertThat(second.getStatusCode())
                .as("Double-settle must return 200 (idempotent)")
                .isEqualTo(HttpStatus.OK);
        assertThat(((Map<String, Object>) second.getBody()).get("auctionStatus"))
                .as("Double-settle must return SETTLED status")
                .isEqualTo("SETTLED");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 9. GET /api/auction/open lazy-settles expired auctions
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("listOpen_lazySettlesExpiredAuctions")
    @SuppressWarnings("unchecked")
    void listOpen_lazySettlesExpiredAuctions() {
        String sellerToken = registerAndGetToken(rest, base());
        Number sellerCharId = createCharacter(rest, base(), sellerToken, uniqueName());
        long scid = sellerCharId.longValue();
        buyGoods(sellerToken, scid, "pruning_shears", 1.0);
        Number auctionId = createGoodsAuction(sellerToken, scid, "pruning_shears", 1.0, 10.0, 2);

        // Advance past endDay
        advanceClock(3);

        // GET /open triggers lazy settlement
        ResponseEntity<List> openResp = rest.exchange(
                base() + "/api/auction/open",
                HttpMethod.GET,
                getWithToken(sellerToken),
                List.class);
        assertThat(openResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        List<?> openAuctions = openResp.getBody();
        assertThat(openAuctions).isNotNull();

        // The expired auction must NOT appear in the OPEN list
        boolean expiredStillOpen = openAuctions.stream()
                .anyMatch(o -> auctionId.longValue() ==
                        ((Number) ((Map<?, ?>) o).get("id")).longValue());
        assertThat(expiredStillOpen)
                .as("Expired auction must be auto-settled and removed from open list")
                .isFalse();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 10. CELLAR_ITEM: settle transfers ownership to winner, clears escrow
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("settle_cellarItem_transfersOwnership")
    @SuppressWarnings("unchecked")
    void settle_cellarItem_transfersOwnership() {
        // Seller grows a bottle
        String sellerToken = registerAndGetToken(rest, base());
        Number sellerCharId = createCharacter(rest, base(), sellerToken, uniqueName());
        long scid = sellerCharId.longValue();
        Number cellarItemId = growAndGetItemId(sellerToken, scid);

        // Auction it for 3 days
        Map<String, Object> createBody = Map.of(
                "characterId",  scid,
                "kind",         "CELLAR_ITEM",
                "refId",        String.valueOf(cellarItemId.longValue()),
                "quantity",     1.0,
                "startBidGel",  10.0,
                "durationDays", 3);
        ResponseEntity<Map> createResp = rest.postForEntity(
                base() + "/api/auction/create",
                withToken(createBody, sellerToken),
                Map.class);
        assertThat(createResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Number auctionId = (Number) createResp.getBody().get("id");

        // Bidder
        String bidderToken = registerAndGetToken(rest, base());
        Number bidderCharId = createCharacter(rest, base(), bidderToken, uniqueName());
        long bcid = bidderCharId.longValue();

        Map<String, Object> bidBody = Map.of("characterId", bcid, "amountGel", 30.0);
        ResponseEntity<Map> bidResp = rest.postForEntity(
                base() + "/api/auction/" + auctionId.longValue() + "/bid",
                withToken(bidBody, bidderToken),
                Map.class);
        assertThat(bidResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Advance clock past endDay
        advanceClock(4);

        // Settle
        Map<String, Object> settleBody = Map.of("characterId", scid);
        ResponseEntity<Map> settleResp = rest.postForEntity(
                base() + "/api/auction/" + auctionId.longValue() + "/settle",
                withToken(settleBody, sellerToken),
                Map.class);
        assertThat(settleResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Map<String, Object>) settleResp.getBody()).get("auctionStatus"))
                .isEqualTo("SETTLED");

        // Winner's cellar must contain the item (escrowed=false, now owned by winner)
        ResponseEntity<List> winnerCellar = rest.exchange(
                base() + "/api/cellar/" + bcid,
                HttpMethod.GET,
                getWithToken(bidderToken),
                List.class);
        assertThat(winnerCellar.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<?> wCellar = winnerCellar.getBody();
        assertThat(wCellar).isNotNull().isNotEmpty();
        boolean found = wCellar.stream()
                .anyMatch(o -> cellarItemId.longValue() ==
                        ((Number) ((Map<?, ?>) o).get("id")).longValue());
        assertThat(found)
                .as("CellarItem must appear in winner's cellar after settlement")
                .isTrue();

        // Seller's cellar must NOT contain the item
        ResponseEntity<List> sellerCellar = rest.exchange(
                base() + "/api/cellar/" + scid,
                HttpMethod.GET,
                getWithToken(sellerToken),
                List.class);
        List<?> sCellar = sellerCellar.getBody();
        assertThat(sCellar).isNotNull();
        boolean stillWithSeller = sCellar.stream()
                .anyMatch(o -> cellarItemId.longValue() ==
                        ((Number) ((Map<?, ?>) o).get("id")).longValue());
        assertThat(stillWithSeller)
                .as("CellarItem must NOT remain in seller's cellar after settlement")
                .isFalse();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    /** Buy goods from the NPC shop to seed the seller's inventory. */
    @SuppressWarnings("unchecked")
    private void buyGoods(String token, long characterId, String goodTypeId, double qty) {
        Map<String, Object> body = Map.of(
                "characterId", characterId,
                "goodTypeId",  goodTypeId,
                "quantity",    qty);
        ResponseEntity<Map> resp = rest.postForEntity(
                base() + "/api/shop/buy",
                withToken(body, token),
                Map.class);
        assertThat(resp.getStatusCode())
                .as("Shop buy must succeed for seeding test data")
                .isEqualTo(HttpStatus.OK);
    }

    /** Create a GOODS auction and return its id. */
    @SuppressWarnings("unchecked")
    private Number createGoodsAuction(String token, long characterId,
                                      String goodTypeId, double qty,
                                      double startBidGel, int durationDays) {
        Map<String, Object> body = Map.of(
                "characterId",  characterId,
                "kind",         "GOODS",
                "refId",        goodTypeId,
                "quantity",     qty,
                "startBidGel",  startBidGel,
                "durationDays", durationDays);
        ResponseEntity<Map> resp = rest.postForEntity(
                base() + "/api/auction/create",
                withToken(body, token),
                Map.class);
        assertThat(resp.getStatusCode())
                .as("POST /api/auction/create (GOODS) must return 200")
                .isEqualTo(HttpStatus.OK);
        return (Number) resp.getBody().get("id");
    }

    /** Grow a vintage and return the cellar item id (mirrors TradeControllerTest). */
    @SuppressWarnings("unchecked")
    private Number growAndGetItemId(String token, long characterId) {
        Map<String, Object> body = Map.of(
                "seed", 42L, "budLoad", 12, "pickDay", 270, "threats", false);
        ResponseEntity<Map> resp = rest.postForEntity(
                base() + "/api/cellar/" + characterId + "/grow",
                withToken(body, token),
                Map.class);
        assertThat(resp.getStatusCode())
                .as("grow must succeed")
                .isIn(HttpStatus.OK, HttpStatus.CREATED);
        Map<?, ?> respBody = resp.getBody();
        assertThat(respBody).isNotNull();
        if (respBody.containsKey("cellarItem")) {
            return (Number) ((Map<?, ?>) respBody.get("cellarItem")).get("id");
        }
        return (Number) respBody.get("id");
    }

    /** Fetch the character's wallet balance. */
    @SuppressWarnings("unchecked")
    private double getWallet(String token, long characterId) {
        ResponseEntity<Map> resp = rest.exchange(
                base() + "/api/characters/" + characterId,
                HttpMethod.GET,
                getWithToken(token),
                Map.class);
        assertThat(resp.getStatusCode())
                .as("GET /api/characters/{id} must succeed")
                .isEqualTo(HttpStatus.OK);
        return ((Number) resp.getBody().get("walletGel")).doubleValue();
    }

    /** Fetch the character's goods inventory. */
    @SuppressWarnings("unchecked")
    private List<?> getInventory(String token, long characterId) {
        ResponseEntity<List> resp = rest.exchange(
                base() + "/api/goods/" + characterId,
                HttpMethod.GET,
                getWithToken(token),
                List.class);
        assertThat(resp.getStatusCode())
                .as("GET /api/goods/{characterId} must return 200")
                .isEqualTo(HttpStatus.OK);
        return resp.getBody();
    }

    /** Advance the world clock by {@code days} sim-days via POST /api/world/advance. */
    private void advanceClock(int days) {
        Map<String, Object> body = Map.of("days", days);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Map> resp = rest.postForEntity(
                base() + "/api/world/advance",
                new HttpEntity<>(body, headers),
                Map.class);
        assertThat(resp.getStatusCode())
                .as("POST /api/world/advance must succeed")
                .isEqualTo(HttpStatus.OK);
    }
}
