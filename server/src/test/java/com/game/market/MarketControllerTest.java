package com.game.market;

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
 * Integration tests for GET /api/market, POST /api/market/list, POST /api/market/buy
 * (MMO-CORE-SPEC §4 — Market endpoints).
 *
 * Assumptions (noted per spec):
 *  - A "sell side" scenario is set up by: register → create character → grow
 *    → list item on market. The same flow is used for "buy side" with a second
 *    account.
 *  - POST /api/market/list  {characterId, cellarItemId, askPrice} → 200/201 Listing
 *    with at least { id, sellerCharacterId, cellarItemId, askPrice, status }
 *  - POST /api/market/buy   {characterId, listingId}              → 200/201 TradeRecord
 *  - GET  /api/market                                             → active listings []
 *  - Character starts with walletGel = 100.0 (per spec §2).
 *  - Buy transfers item to buyer cellar, credits seller, debits buyer.
 *  - Insufficient funds (buyer wallet < askPrice) → 400.
 *  - Self-buy (buyer == seller character) → 400.
 *  - Listing an item not owned by the character → 400 or 403.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class MarketControllerTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    private String base() {
        return "http://localhost:" + port;
    }

    private static String uniqueCharName() {
        return "mc_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    // ── Internal setup helpers ────────────────────────────────────────────────

    /** Grow a bottle for the given character and return the cellar item id. */
    @SuppressWarnings("unchecked")
    private Number growAndGetItemId(String token, long characterId) {
        Map<String, Object> growBody = Map.of(
                "seed", 42L,
                "budLoad", 12,
                "pickDay", 270,
                "threats", false);
        ResponseEntity<Map> growResp = rest.postForEntity(
                base() + "/api/cellar/" + characterId + "/grow",
                withToken(growBody, token),
                Map.class);
        assertThat(growResp.getStatusCode())
                .as("grow must succeed (200 or 201)")
                .isIn(HttpStatus.OK, HttpStatus.CREATED);

        Map<?, ?> growBody2 = growResp.getBody();
        assertThat(growBody2).isNotNull();

        // Accept both wrapped { cellarItem: { id: ... } } and flat { id: ... }
        if (growBody2.containsKey("cellarItem")) {
            Map<?, ?> item = (Map<?, ?>) growBody2.get("cellarItem");
            return (Number) item.get("id");
        }
        return (Number) growBody2.get("id");
    }

    /** List a cellar item on the market and return the listing id. */
    @SuppressWarnings("unchecked")
    private Number listOnMarket(String token, long characterId,
                                 long cellarItemId, double askPrice) {
        Map<String, Object> listBody = Map.of(
                "characterId", characterId,
                "cellarItemId", cellarItemId,
                "askPrice", askPrice);
        ResponseEntity<Map> listResp = rest.postForEntity(
                base() + "/api/market/list",
                withToken(listBody, token),
                Map.class);
        assertThat(listResp.getStatusCode())
                .as("POST /api/market/list must return 200 or 201")
                .isIn(HttpStatus.OK, HttpStatus.CREATED);

        Map<?, ?> listing = listResp.getBody();
        assertThat(listing).isNotNull();
        return (Number) listing.get("id");
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("listAndBuy_transfersItemAndMoney")
    @SuppressWarnings("unchecked")
    void listAndBuy_transfersItemAndMoney() {
        // --- Seller setup ---
        String sellerToken = registerAndGetToken(rest, base());
        Number sellerCharId = createCharacter(rest, base(), sellerToken, uniqueCharName());
        Number cellarItemId = growAndGetItemId(sellerToken, sellerCharId.longValue());
        double askPrice = 10.0;
        Number listingId = listOnMarket(sellerToken, sellerCharId.longValue(),
                cellarItemId.longValue(), askPrice);

        // --- Buyer setup ---
        String buyerToken = registerAndGetToken(rest, base());
        Number buyerCharId = createCharacter(rest, base(), buyerToken, uniqueCharName());

        // Buyer's wallet before purchase — character starts with 100.0
        double buyerWalletBefore = getWallet(buyerToken, buyerCharId.longValue());

        // --- Buy ---
        Map<String, Object> buyBody = Map.of(
                "characterId", buyerCharId.longValue(),
                "listingId", listingId.longValue());
        ResponseEntity<Map> buyResp = rest.postForEntity(
                base() + "/api/market/buy",
                withToken(buyBody, buyerToken),
                Map.class);
        assertThat(buyResp.getStatusCode())
                .as("POST /api/market/buy must return 200 or 201")
                .isIn(HttpStatus.OK, HttpStatus.CREATED);

        // --- Verify item in buyer's cellar ---
        ResponseEntity<List> buyerCellarResp = rest.exchange(
                base() + "/api/cellar/" + buyerCharId.longValue(),
                HttpMethod.GET,
                getWithToken(buyerToken),
                List.class);
        assertThat(buyerCellarResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<?> buyerCellar = buyerCellarResp.getBody();
        assertThat(buyerCellar)
                .as("Buyer's cellar must contain the purchased item")
                .isNotNull()
                .isNotEmpty();

        // --- Verify buyer wallet decreased ---
        double buyerWalletAfter = getWallet(buyerToken, buyerCharId.longValue());
        assertThat(buyerWalletAfter)
                .as("Buyer's wallet must decrease by askPrice after purchase")
                .isEqualTo(buyerWalletBefore - askPrice);

        // --- Verify seller wallet increased ---
        double sellerWalletAfter = getWallet(sellerToken, sellerCharId.longValue());
        // Seller starts at 100.0; after listing (no wallet change) + sale -> +10.0
        assertThat(sellerWalletAfter)
                .as("Seller's wallet must increase by askPrice after sale")
                .isEqualTo(100.0 + askPrice);
    }

    @Test
    @DisplayName("buy_insufficientFunds_400")
    @SuppressWarnings("unchecked")
    void buy_insufficientFunds_400() {
        // Seller grows and lists at a price higher than the buyer can afford
        String sellerToken = registerAndGetToken(rest, base());
        Number sellerCharId = createCharacter(rest, base(), sellerToken, uniqueCharName());
        Number cellarItemId = growAndGetItemId(sellerToken, sellerCharId.longValue());
        // Character starts with 100.0; ask more than that
        double askPrice = 999.0;
        Number listingId = listOnMarket(sellerToken, sellerCharId.longValue(),
                cellarItemId.longValue(), askPrice);

        // Buyer has only 100.0 starting wallet
        String buyerToken = registerAndGetToken(rest, base());
        Number buyerCharId = createCharacter(rest, base(), buyerToken, uniqueCharName());

        Map<String, Object> buyBody = Map.of(
                "characterId", buyerCharId.longValue(),
                "listingId", listingId.longValue());
        ResponseEntity<Map> resp = rest.postForEntity(
                base() + "/api/market/buy",
                withToken(buyBody, buyerToken),
                Map.class);

        assertThat(resp.getStatusCode())
                .as("POST /api/market/buy with insufficient funds must return 400")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("buy_ownListing_400")
    @SuppressWarnings("unchecked")
    void buy_ownListing_400() {
        // Account grows a bottle, lists it, then tries to buy it with the same character
        String token = registerAndGetToken(rest, base());
        Number charId = createCharacter(rest, base(), token, uniqueCharName());
        Number cellarItemId = growAndGetItemId(token, charId.longValue());
        Number listingId = listOnMarket(token, charId.longValue(),
                cellarItemId.longValue(), 5.0);

        Map<String, Object> buyBody = Map.of(
                "characterId", charId.longValue(),
                "listingId", listingId.longValue());
        ResponseEntity<Map> resp = rest.postForEntity(
                base() + "/api/market/buy",
                withToken(buyBody, token),
                Map.class);

        assertThat(resp.getStatusCode())
                .as("POST /api/market/buy on own listing (self-buy) must return 400")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("list_unownedItem_rejected")
    @SuppressWarnings("unchecked")
    void list_unownedItem_rejected() {
        // Account A grows a cellar item
        String tokenA = registerAndGetToken(rest, base());
        Number charIdA = createCharacter(rest, base(), tokenA, uniqueCharName());
        Number itemIdA = growAndGetItemId(tokenA, charIdA.longValue());

        // Account B creates a character and tries to list Account A's item
        String tokenB = registerAndGetToken(rest, base());
        Number charIdB = createCharacter(rest, base(), tokenB, uniqueCharName());

        Map<String, Object> listBody = Map.of(
                "characterId", charIdB.longValue(),
                "cellarItemId", itemIdA.longValue(),
                "askPrice", 5.0);
        ResponseEntity<Map> resp = rest.postForEntity(
                base() + "/api/market/list",
                withToken(listBody, tokenB),
                Map.class);

        assertThat(resp.getStatusCode().value())
                .as("Listing an item not owned by the character must be rejected (400 or 403)")
                .isIn(400, 403);
    }

    @Test
    @DisplayName("getMarket_returnsActiveListings")
    @SuppressWarnings("unchecked")
    void getMarket_returnsActiveListings() {
        // Create a listing so there is at least one active entry
        String sellerToken = registerAndGetToken(rest, base());
        Number sellerCharId = createCharacter(rest, base(), sellerToken, uniqueCharName());
        Number cellarItemId = growAndGetItemId(sellerToken, sellerCharId.longValue());
        listOnMarket(sellerToken, sellerCharId.longValue(), cellarItemId.longValue(), 8.0);

        // GET /api/market — no specific auth requirement stated; use seller token
        ResponseEntity<List> resp = rest.exchange(
                base() + "/api/market",
                HttpMethod.GET,
                getWithToken(sellerToken),
                List.class);

        assertThat(resp.getStatusCode())
                .as("GET /api/market must return 200")
                .isEqualTo(HttpStatus.OK);

        List<?> listings = resp.getBody();
        assertThat(listings)
                .as("GET /api/market must return a list (possibly with our new listing)")
                .isNotNull();

        // Each MarketListingView has suggestedPrice at top level,
        // and a nested 'listing' object with 'askPrice' (per MarketListingView record shape).
        if (!listings.isEmpty()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> first = (Map<String, Object>) listings.get(0);
            // suggestedPrice is a top-level component of MarketListingView
            assertThat(first).containsKey("suggestedPrice");
            // listing sub-object holds askPrice
            Object listingObj = first.get("listing");
            if (listingObj != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> listingMap = (Map<String, Object>) listingObj;
                assertThat(listingMap).containsKey("askPrice");
            }
        }
    }

    // ── Wallet helper ─────────────────────────────────────────────────────────

    /**
     * Fetch the wallet balance for a character.
     *
     * Assumption: GET /api/characters/{id} returns { walletGel: double, ... }.
     */
    @SuppressWarnings("unchecked")
    private double getWallet(String token, long characterId) {
        ResponseEntity<Map> resp = rest.exchange(
                base() + "/api/characters/" + characterId,
                HttpMethod.GET,
                getWithToken(token),
                Map.class);
        assertThat(resp.getStatusCode())
                .as("GET /api/characters/{id} must succeed to read wallet")
                .isEqualTo(HttpStatus.OK);
        Map<String, Object> body = resp.getBody();
        assertThat(body).isNotNull();
        assertThat(body).containsKey("walletGel");
        return ((Number) body.get("walletGel")).doubleValue();
    }
}
