package com.game.goods;

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
 * Integration tests for:
 *   GET  /api/goods/catalog
 *   GET  /api/goods/{characterId}
 *   POST /api/shop/buy
 *   POST /api/shop/sell
 *
 * Covers: catalog returned, buy debits wallet + grants good, insufficient
 * funds → 400, sell credits + decrements, ownership enforced on inventory GET.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class GoodsControllerTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    private String base() {
        return "http://localhost:" + port;
    }

    private static String uniqueName() {
        return "g_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    // ── Test: catalog returns goods ───────────────────────────────────────────

    @Test
    @DisplayName("catalog_returnsAllGoods_noAuth")
    @SuppressWarnings("unchecked")
    void catalog_returnsAllGoods_noAuth() {
        // No auth header — catalog is permitAll
        ResponseEntity<List> resp = rest.getForEntity(
                base() + "/api/goods/catalog",
                List.class);

        assertThat(resp.getStatusCode())
                .as("GET /api/goods/catalog must return 200")
                .isEqualTo(HttpStatus.OK);

        List<?> catalog = resp.getBody();
        assertThat(catalog)
                .as("Catalog must not be empty")
                .isNotNull()
                .isNotEmpty();

        // Spot-check first entry has expected fields
        Map<String, Object> first = (Map<String, Object>) catalog.get(0);
        assertThat(first).containsKey("id");
        assertThat(first).containsKey("category");
        assertThat(first).containsKey("displayName");
        assertThat(first).containsKey("basePrice");
        assertThat(first).containsKey("consumable");
    }

    @Test
    @DisplayName("catalog_containsExpectedGoods")
    @SuppressWarnings("unchecked")
    void catalog_containsExpectedGoods() {
        ResponseEntity<List> resp = rest.getForEntity(
                base() + "/api/goods/catalog",
                List.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<?> catalog = resp.getBody();
        assertThat(catalog).isNotNull();

        // Must have at least 15 goods
        assertThat(catalog.size())
                .as("Catalog must contain at least 15 goods (spec: 15-25)")
                .isGreaterThanOrEqualTo(15);

        // Extract ids
        List<String> ids = catalog.stream()
                .map(o -> (String) ((Map<?, ?>) o).get("id"))
                .toList();

        // Spot-check key goods across every category
        assertThat(ids).contains(
                "qvevri_500l",
                "basket_press",
                "saperavi_cuttings_certified",
                "copper_sulfate",
                "pruning_shears"
        );
    }

    // ── Test: buy debits wallet + grants good ─────────────────────────────────

    @Test
    @DisplayName("buy_debitsWalletAndGrantsGood")
    @SuppressWarnings("unchecked")
    void buy_debitsWalletAndGrantsGood() {
        String token = registerAndGetToken(rest, base());
        Number charId = createCharacter(rest, base(), token, uniqueName());
        long cid = charId.longValue();

        double walletBefore = getWallet(token, cid);  // 100.0 starting wallet

        // Buy 1 pruning_shears (basePrice 45.0)
        Map<String, Object> body = Map.of(
                "characterId", cid,
                "goodTypeId",  "pruning_shears",
                "quantity",    1.0);

        ResponseEntity<Map> resp = rest.postForEntity(
                base() + "/api/shop/buy",
                withToken(body, token),
                Map.class);

        assertThat(resp.getStatusCode())
                .as("POST /api/shop/buy must return 200")
                .isEqualTo(HttpStatus.OK);

        Map<String, Object> buyBody = resp.getBody();
        assertThat(buyBody).isNotNull();
        assertThat(buyBody).containsKey("ownedGood");
        assertThat(buyBody).containsKey("walletGel");

        double walletAfter = ((Number) buyBody.get("walletGel")).doubleValue();
        assertThat(walletAfter)
                .as("Wallet must decrease by basePrice (45.0) after buying pruning_shears")
                .isEqualTo(walletBefore - 45.0);

        // Verify inventory
        ResponseEntity<List> invResp = rest.exchange(
                base() + "/api/goods/" + cid,
                HttpMethod.GET,
                getWithToken(token),
                List.class);
        assertThat(invResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<?> inv = invResp.getBody();
        assertThat(inv).isNotNull().isNotEmpty();

        List<String> goodTypeIds = inv.stream()
                .map(o -> (String) ((Map<?, ?>) o).get("goodTypeId"))
                .toList();
        assertThat(goodTypeIds).contains("pruning_shears");
    }

    // ── Test: insufficient funds → 400 ───────────────────────────────────────

    @Test
    @DisplayName("buy_insufficientFunds_400")
    @SuppressWarnings("unchecked")
    void buy_insufficientFunds_400() {
        String token = registerAndGetToken(rest, base());
        Number charId = createCharacter(rest, base(), token, uniqueName());
        long cid = charId.longValue();

        // qvevri_1000l costs 1150 GEL; character only has 100 GEL starting wallet
        Map<String, Object> body = Map.of(
                "characterId", cid,
                "goodTypeId",  "qvevri_1000l",
                "quantity",    1.0);

        ResponseEntity<Map> resp = rest.postForEntity(
                base() + "/api/shop/buy",
                withToken(body, token),
                Map.class);

        assertThat(resp.getStatusCode())
                .as("POST /api/shop/buy with insufficient funds must return 400")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ── Test: sell credits wallet + decrements ────────────────────────────────

    @Test
    @DisplayName("sell_creditsWalletAndDecrementsGood")
    @SuppressWarnings("unchecked")
    void sell_creditsWalletAndDecrementsGood() {
        String token = registerAndGetToken(rest, base());
        Number charId = createCharacter(rest, base(), token, uniqueName());
        long cid = charId.longValue();

        // Buy 2 units of pruning_shears (basePrice 45.0 each; cost = 90.0)
        Map<String, Object> buyBody = Map.of(
                "characterId", cid,
                "goodTypeId",  "pruning_shears",
                "quantity",    2.0);
        ResponseEntity<Map> buyResp = rest.postForEntity(
                base() + "/api/shop/buy",
                withToken(buyBody, token),
                Map.class);
        assertThat(buyResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        double walletAfterBuy = ((Number) buyResp.getBody().get("walletGel")).doubleValue();

        // Extract ownedGoodId from buy response
        Map<?, ?> ownedGoodMap = (Map<?, ?>) buyResp.getBody().get("ownedGood");
        long ownedGoodId = ((Number) ownedGoodMap.get("id")).longValue();

        // Sell 1 unit back: credit = 0.5 * 45.0 * 1 = 22.5
        Map<String, Object> sellBody = Map.of(
                "characterId", cid,
                "ownedGoodId", ownedGoodId,
                "quantity",    1.0);
        ResponseEntity<Map> sellResp = rest.postForEntity(
                base() + "/api/shop/sell",
                withToken(sellBody, token),
                Map.class);

        assertThat(sellResp.getStatusCode())
                .as("POST /api/shop/sell must return 200")
                .isEqualTo(HttpStatus.OK);

        Map<String, Object> sellRespBody = sellResp.getBody();
        assertThat(sellRespBody).isNotNull();
        assertThat(sellRespBody).containsKey("walletGel");

        double walletAfterSell = ((Number) sellRespBody.get("walletGel")).doubleValue();
        double expectedCredit = 0.5 * 45.0 * 1.0;
        assertThat(walletAfterSell)
                .as("Wallet must increase by 0.5 * basePrice * qty after sell")
                .isEqualTo(walletAfterBuy + expectedCredit);

        // Verify quantity decremented to 1
        ResponseEntity<List> invResp = rest.exchange(
                base() + "/api/goods/" + cid,
                HttpMethod.GET,
                getWithToken(token),
                List.class);
        assertThat(invResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<?> inv = invResp.getBody();
        assertThat(inv).isNotNull().isNotEmpty();
        Map<?, ?> remaining = inv.stream()
                .filter(o -> "pruning_shears".equals(((Map<?, ?>) o).get("goodTypeId")))
                .map(o -> (Map<?, ?>) o)
                .findFirst()
                .orElseThrow(() -> new AssertionError("pruning_shears not found in inventory after sell"));
        double remainingQty = ((Number) remaining.get("quantity")).doubleValue();
        assertThat(remainingQty)
                .as("After selling 1 of 2, quantity must be 1")
                .isEqualTo(1.0);
    }

    // ── Test: ownership enforced on inventory GET ─────────────────────────────

    @Test
    @DisplayName("getInventory_otherAccountCharacter_rejected")
    @SuppressWarnings("unchecked")
    void getInventory_otherAccountCharacter_rejected() {
        // Account A creates a character
        String tokenA = registerAndGetToken(rest, base());
        Number charIdA = createCharacter(rest, base(), tokenA, uniqueName());

        // Account B tries to read Account A's inventory
        String tokenB = registerAndGetToken(rest, base());

        ResponseEntity<Map> resp = rest.exchange(
                base() + "/api/goods/" + charIdA.longValue(),
                HttpMethod.GET,
                getWithToken(tokenB),
                Map.class);

        assertThat(resp.getStatusCode().value())
                .as("GET /api/goods/{characterId} for another account's character must return 404 or 403")
                .isIn(403, 404);
    }

    // ── Test: unknown goodTypeId → 400 ────────────────────────────────────────

    @Test
    @DisplayName("buy_unknownGoodTypeId_400")
    @SuppressWarnings("unchecked")
    void buy_unknownGoodTypeId_400() {
        String token = registerAndGetToken(rest, base());
        Number charId = createCharacter(rest, base(), token, uniqueName());
        long cid = charId.longValue();

        Map<String, Object> body = Map.of(
                "characterId", cid,
                "goodTypeId",  "does_not_exist",
                "quantity",    1.0);

        ResponseEntity<Map> resp = rest.postForEntity(
                base() + "/api/shop/buy",
                withToken(body, token),
                Map.class);

        assertThat(resp.getStatusCode())
                .as("POST /api/shop/buy with unknown goodTypeId must return 400")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ── Wallet helper ─────────────────────────────────────────────────────────

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
        assertThat(body).isNotNull().containsKey("walletGel");
        return ((Number) body.get("walletGel")).doubleValue();
    }
}
