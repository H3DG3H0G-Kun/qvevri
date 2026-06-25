package com.game.trade;

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
 * Integration tests for /api/trade/** (LANE TRADE).
 *
 * <p>Scenarios covered:
 * <ol>
 *   <li>Create a GOODS offer → appears in GET /api/trade/offers (marketplace).</li>
 *   <li>Accept a GOODS offer: GEL moves both ways AND goods ownership transfers.</li>
 *   <li>Create a CELLAR_ITEM offer → accept reassigns CellarItem ownership.</li>
 *   <li>Cannot accept your own offer → 400.</li>
 *   <li>Cannot accept an already-accepted offer → 409.</li>
 *   <li>Insufficient funds → 400.</li>
 *   <li>Cancel an OPEN offer → blocks a later accept (400/404/409).</li>
 *   <li>GET /api/trade/offers/mine returns only that seller's offers.</li>
 * </ol>
 *
 * <p>Uses {@link AccountTestHelper} static helpers and Map&lt;String,Object&gt; bodies
 * exactly like the existing MarketControllerTest pattern.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class TradeControllerTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    private String base() {
        return "http://localhost:" + port;
    }

    private static String uniqueName() {
        return "td_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 1. Create GOODS offer + list open marketplace
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("createGoodsOffer_appearsInOpenMarketplace")
    @SuppressWarnings("unchecked")
    void createGoodsOffer_appearsInOpenMarketplace() {
        String token = registerAndGetToken(rest, base());
        Number charId = createCharacter(rest, base(), token, uniqueName());
        long cid = charId.longValue();

        // Seed some pruning_shears (basePrice 45 GEL; character starts at 100 GEL)
        buyGoods(token, cid, "pruning_shears", 2.0);

        // Create a GOODS offer for 1 unit
        Map<String, Object> offerBody = Map.of(
                "characterId", cid,
                "kind",        "GOODS",
                "reference",   "pruning_shears",
                "quantity",    1.0,
                "priceGel",    30.0);
        ResponseEntity<Map> createResp = rest.postForEntity(
                base() + "/api/trade/offers",
                withToken(offerBody, token),
                Map.class);

        assertThat(createResp.getStatusCode())
                .as("POST /api/trade/offers must return 200")
                .isEqualTo(HttpStatus.OK);

        Map<?, ?> offer = createResp.getBody();
        assertThat(offer).isNotNull();
        assertThat(offer.get("status")).isEqualTo("OPEN");
        assertThat(offer.get("kind")).isEqualTo("GOODS");

        Number offerId = (Number) offer.get("id");
        assertThat(offerId).isNotNull();

        // Must appear in GET /api/trade/offers
        ResponseEntity<List> listResp = rest.exchange(
                base() + "/api/trade/offers",
                HttpMethod.GET,
                getWithToken(token),
                List.class);
        assertThat(listResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        List<?> openOffers = listResp.getBody();
        assertThat(openOffers).isNotNull();

        boolean found = openOffers.stream()
                .anyMatch(o -> offerId.longValue() ==
                        ((Number) ((Map<?, ?>) o).get("id")).longValue());
        assertThat(found)
                .as("The newly created offer must appear in GET /api/trade/offers")
                .isTrue();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. Accept GOODS offer → GEL moves both ways + goods transfer
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("acceptGoodsOffer_transfersMoneyAndGoods")
    @SuppressWarnings("unchecked")
    void acceptGoodsOffer_transfersMoneyAndGoods() {
        // Seller
        String sellerToken = registerAndGetToken(rest, base());
        Number sellerCharId = createCharacter(rest, base(), sellerToken, uniqueName());
        long scid = sellerCharId.longValue();

        // Seed 2 pruning_shears (45 GEL each = 90 total; wallet becomes 10 after)
        buyGoods(sellerToken, scid, "pruning_shears", 2.0);
        double sellerWalletAfterBuy = getWallet(sellerToken, scid);

        // Create offer for 1 unit at 20 GEL
        double offerPrice = 20.0;
        Number offerId = createGoodsOffer(sellerToken, scid, "pruning_shears", 1.0, offerPrice);

        // Buyer
        String buyerToken = registerAndGetToken(rest, base());
        Number buyerCharId = createCharacter(rest, base(), buyerToken, uniqueName());
        long bcid = buyerCharId.longValue();
        double buyerWalletBefore = getWallet(buyerToken, bcid); // 100.0

        // Accept
        Map<String, Object> acceptBody = Map.of("characterId", bcid);
        ResponseEntity<Map> acceptResp = rest.postForEntity(
                base() + "/api/trade/offers/" + offerId.longValue() + "/accept",
                withToken(acceptBody, buyerToken),
                Map.class);

        assertThat(acceptResp.getStatusCode())
                .as("POST /accept must return 200")
                .isEqualTo(HttpStatus.OK);

        Map<String, Object> acceptResult = acceptResp.getBody();
        assertThat(acceptResult).isNotNull();
        assertThat(acceptResult).containsKey("offer");
        assertThat(acceptResult).containsKey("buyerWalletGel");

        Map<?, ?> settledOffer = (Map<?, ?>) acceptResult.get("offer");
        assertThat(settledOffer.get("status")).isEqualTo("ACCEPTED");
        assertThat(((Number) settledOffer.get("buyerCharacterId")).longValue()).isEqualTo(bcid);

        // Buyer wallet decreased
        double buyerWalletAfter = ((Number) acceptResult.get("buyerWalletGel")).doubleValue();
        assertThat(buyerWalletAfter)
                .as("Buyer wallet must decrease by priceGel")
                .isEqualTo(buyerWalletBefore - offerPrice);

        // Seller wallet increased
        double sellerWalletAfter = getWallet(sellerToken, scid);
        assertThat(sellerWalletAfter)
                .as("Seller wallet must increase by priceGel")
                .isEqualTo(sellerWalletAfterBuy + offerPrice);

        // Buyer now owns pruning_shears
        List<?> buyerInv = getInventory(buyerToken, bcid);
        boolean buyerOwnsGood = buyerInv.stream()
                .anyMatch(o -> "pruning_shears".equals(((Map<?, ?>) o).get("goodTypeId")));
        assertThat(buyerOwnsGood)
                .as("Buyer must own pruning_shears after accepting GOODS offer")
                .isTrue();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. Accept CELLAR_ITEM offer → reassigns CellarItem ownership
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("acceptCellarItemOffer_reassignsOwnership")
    @SuppressWarnings("unchecked")
    void acceptCellarItemOffer_reassignsOwnership() {
        // Seller grows a bottle
        String sellerToken = registerAndGetToken(rest, base());
        Number sellerCharId = createCharacter(rest, base(), sellerToken, uniqueName());
        long scid = sellerCharId.longValue();
        Number cellarItemId = growAndGetItemId(sellerToken, scid);

        // Create a CELLAR_ITEM offer
        double offerPrice = 15.0;
        Number offerId = createCellarItemOffer(sellerToken, scid, cellarItemId.longValue(), offerPrice);

        // Buyer
        String buyerToken = registerAndGetToken(rest, base());
        Number buyerCharId = createCharacter(rest, base(), buyerToken, uniqueName());
        long bcid = buyerCharId.longValue();

        // Accept
        Map<String, Object> acceptBody = Map.of("characterId", bcid);
        ResponseEntity<Map> acceptResp = rest.postForEntity(
                base() + "/api/trade/offers/" + offerId.longValue() + "/accept",
                withToken(acceptBody, buyerToken),
                Map.class);

        assertThat(acceptResp.getStatusCode())
                .as("Accept CELLAR_ITEM offer must return 200")
                .isEqualTo(HttpStatus.OK);

        // Buyer's cellar must contain the item
        ResponseEntity<List> buyerCellarResp = rest.exchange(
                base() + "/api/cellar/" + bcid,
                HttpMethod.GET,
                getWithToken(buyerToken),
                List.class);
        assertThat(buyerCellarResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<?> buyerCellar = buyerCellarResp.getBody();
        assertThat(buyerCellar)
                .as("Buyer's cellar must contain the transferred item")
                .isNotNull()
                .isNotEmpty();

        boolean found = buyerCellar.stream()
                .anyMatch(o -> cellarItemId.longValue() ==
                        ((Number) ((Map<?, ?>) o).get("id")).longValue());
        assertThat(found)
                .as("The specific cellar item id must appear in the buyer's cellar")
                .isTrue();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. Cannot accept your own offer → 400
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("accept_ownOffer_400")
    @SuppressWarnings("unchecked")
    void accept_ownOffer_400() {
        String token = registerAndGetToken(rest, base());
        Number charId = createCharacter(rest, base(), token, uniqueName());
        long cid = charId.longValue();

        buyGoods(token, cid, "pruning_shears", 1.0);
        Number offerId = createGoodsOffer(token, cid, "pruning_shears", 1.0, 10.0);

        Map<String, Object> acceptBody = Map.of("characterId", cid);
        ResponseEntity<Map> resp = rest.postForEntity(
                base() + "/api/trade/offers/" + offerId.longValue() + "/accept",
                withToken(acceptBody, token),
                Map.class);

        assertThat(resp.getStatusCode())
                .as("Accepting your own offer must return 400")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 5. Cannot accept an already-accepted offer → 409
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("accept_alreadyAcceptedOffer_409")
    @SuppressWarnings("unchecked")
    void accept_alreadyAcceptedOffer_409() {
        // Seller
        String sellerToken = registerAndGetToken(rest, base());
        Number sellerCharId = createCharacter(rest, base(), sellerToken, uniqueName());
        long scid = sellerCharId.longValue();
        buyGoods(sellerToken, scid, "pruning_shears", 2.0);
        Number offerId = createGoodsOffer(sellerToken, scid, "pruning_shears", 1.0, 5.0);

        // First buyer accepts
        String buyer1Token = registerAndGetToken(rest, base());
        Number buyer1CharId = createCharacter(rest, base(), buyer1Token, uniqueName());
        Map<String, Object> accept1Body = Map.of("characterId", buyer1CharId.longValue());
        ResponseEntity<Map> accept1Resp = rest.postForEntity(
                base() + "/api/trade/offers/" + offerId.longValue() + "/accept",
                withToken(accept1Body, buyer1Token),
                Map.class);
        assertThat(accept1Resp.getStatusCode())
                .as("First accept must succeed")
                .isEqualTo(HttpStatus.OK);

        // Second buyer tries to accept the same offer
        String buyer2Token = registerAndGetToken(rest, base());
        Number buyer2CharId = createCharacter(rest, base(), buyer2Token, uniqueName());
        Map<String, Object> accept2Body = Map.of("characterId", buyer2CharId.longValue());
        ResponseEntity<Map> accept2Resp = rest.postForEntity(
                base() + "/api/trade/offers/" + offerId.longValue() + "/accept",
                withToken(accept2Body, buyer2Token),
                Map.class);

        assertThat(accept2Resp.getStatusCode())
                .as("Accepting an already-accepted offer must return 409")
                .isEqualTo(HttpStatus.CONFLICT);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 6. Insufficient funds → 400
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("accept_insufficientFunds_400")
    @SuppressWarnings("unchecked")
    void accept_insufficientFunds_400() {
        // Seller
        String sellerToken = registerAndGetToken(rest, base());
        Number sellerCharId = createCharacter(rest, base(), sellerToken, uniqueName());
        long scid = sellerCharId.longValue();
        buyGoods(sellerToken, scid, "pruning_shears", 1.0);
        // Offer at 999 GEL — buyer only has 100 GEL
        Number offerId = createGoodsOffer(sellerToken, scid, "pruning_shears", 1.0, 999.0);

        String buyerToken = registerAndGetToken(rest, base());
        Number buyerCharId = createCharacter(rest, base(), buyerToken, uniqueName());
        Map<String, Object> acceptBody = Map.of("characterId", buyerCharId.longValue());

        ResponseEntity<Map> resp = rest.postForEntity(
                base() + "/api/trade/offers/" + offerId.longValue() + "/accept",
                withToken(acceptBody, buyerToken),
                Map.class);

        assertThat(resp.getStatusCode())
                .as("Accepting with insufficient funds must return 400")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 7. Cancel blocks later accept
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("cancel_blocksLaterAccept")
    @SuppressWarnings("unchecked")
    void cancel_blocksLaterAccept() {
        // Seller
        String sellerToken = registerAndGetToken(rest, base());
        Number sellerCharId = createCharacter(rest, base(), sellerToken, uniqueName());
        long scid = sellerCharId.longValue();
        buyGoods(sellerToken, scid, "pruning_shears", 1.0);
        Number offerId = createGoodsOffer(sellerToken, scid, "pruning_shears", 1.0, 10.0);

        // Cancel
        Map<String, Object> cancelBody = Map.of("characterId", scid);
        ResponseEntity<Map> cancelResp = rest.postForEntity(
                base() + "/api/trade/offers/" + offerId.longValue() + "/cancel",
                withToken(cancelBody, sellerToken),
                Map.class);
        assertThat(cancelResp.getStatusCode())
                .as("Cancel must return 200")
                .isEqualTo(HttpStatus.OK);
        assertThat(cancelResp.getBody().get("status"))
                .as("Offer status must be CANCELLED after cancel")
                .isEqualTo("CANCELLED");

        // Buyer tries to accept the cancelled offer
        String buyerToken = registerAndGetToken(rest, base());
        Number buyerCharId = createCharacter(rest, base(), buyerToken, uniqueName());
        Map<String, Object> acceptBody = Map.of("characterId", buyerCharId.longValue());
        ResponseEntity<Map> acceptResp = rest.postForEntity(
                base() + "/api/trade/offers/" + offerId.longValue() + "/accept",
                withToken(acceptBody, buyerToken),
                Map.class);

        assertThat(acceptResp.getStatusCode().value())
                .as("Accepting a CANCELLED offer must be rejected (400 or 409)")
                .isIn(400, 409);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 8. GET /api/trade/offers/mine returns only the seller's offers
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("listMyOffers_returnsOnlySellerOffers")
    @SuppressWarnings("unchecked")
    void listMyOffers_returnsOnlySellerOffers() {
        // Seller A creates 2 offers
        String tokenA = registerAndGetToken(rest, base());
        Number charIdA = createCharacter(rest, base(), tokenA, uniqueName());
        long cida = charIdA.longValue();
        buyGoods(tokenA, cida, "pruning_shears", 2.0);
        Number offerA1 = createGoodsOffer(tokenA, cida, "pruning_shears", 1.0, 10.0);
        Number offerA2 = createGoodsOffer(tokenA, cida, "pruning_shears", 1.0, 12.0);

        // Seller B creates 1 offer
        String tokenB = registerAndGetToken(rest, base());
        Number charIdB = createCharacter(rest, base(), tokenB, uniqueName());
        long cidb = charIdB.longValue();
        buyGoods(tokenB, cidb, "hoe", 1.0);
        createGoodsOffer(tokenB, cidb, "hoe", 1.0, 8.0);

        // GET seller A's offers
        ResponseEntity<List> mineResp = rest.exchange(
                base() + "/api/trade/offers/mine?characterId=" + cida,
                HttpMethod.GET,
                getWithToken(tokenA),
                List.class);
        assertThat(mineResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<?> mine = mineResp.getBody();
        assertThat(mine).isNotNull();

        // Must include both A offers
        List<Long> myIds = mine.stream()
                .map(o -> ((Number) ((Map<?, ?>) o).get("id")).longValue())
                .toList();
        assertThat(myIds).contains(offerA1.longValue(), offerA2.longValue());

        // Must NOT include seller B's character offers
        boolean allBelongToA = mine.stream().allMatch(o ->
                cida == ((Number) ((Map<?, ?>) o).get("sellerCharacterId")).longValue());
        assertThat(allBelongToA)
                .as("GET /api/trade/offers/mine must only return the querying seller's offers")
                .isTrue();
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

    /** Create a GOODS trade offer and return its id. */
    @SuppressWarnings("unchecked")
    private Number createGoodsOffer(String token, long characterId,
                                    String goodTypeId, double qty, double priceGel) {
        Map<String, Object> body = Map.of(
                "characterId", characterId,
                "kind",        "GOODS",
                "reference",   goodTypeId,
                "quantity",    qty,
                "priceGel",    priceGel);
        ResponseEntity<Map> resp = rest.postForEntity(
                base() + "/api/trade/offers",
                withToken(body, token),
                Map.class);
        assertThat(resp.getStatusCode())
                .as("POST /api/trade/offers (GOODS) must return 200")
                .isEqualTo(HttpStatus.OK);
        return (Number) resp.getBody().get("id");
    }

    /** Create a CELLAR_ITEM trade offer and return its id. */
    @SuppressWarnings("unchecked")
    private Number createCellarItemOffer(String token, long characterId,
                                         long cellarItemId, double priceGel) {
        Map<String, Object> body = Map.of(
                "characterId", characterId,
                "kind",        "CELLAR_ITEM",
                "reference",   String.valueOf(cellarItemId),
                "quantity",    1.0,
                "priceGel",    priceGel);
        ResponseEntity<Map> resp = rest.postForEntity(
                base() + "/api/trade/offers",
                withToken(body, token),
                Map.class);
        assertThat(resp.getStatusCode())
                .as("POST /api/trade/offers (CELLAR_ITEM) must return 200")
                .isEqualTo(HttpStatus.OK);
        return (Number) resp.getBody().get("id");
    }

    /** Grow a vintage and return the cellar item id. */
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
}
