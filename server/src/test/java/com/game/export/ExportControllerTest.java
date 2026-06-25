package com.game.export;

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
import static org.assertj.core.api.Assertions.within;

/**
 * Integration tests for /api/export/** (EXPORT lane).
 * Pricing: net = quality × 0.5 × qty × priceMultiplier × (1 − tariffRate).
 * russia 1.30/0.10, byzantium 1.50/0.15, persia 1.20/0.08, poland_lithuania 1.15/0.06.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ExportControllerTest {

    @LocalServerPort int port;
    @Autowired TestRestTemplate rest;

    private String base() { return "http://localhost:" + port; }
    private static String uniqueName() { return "ex_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8); }

    @Test @DisplayName("markets_returnsFour")
    @SuppressWarnings("unchecked")
    void markets_returnsFour() {
        String token = registerAndGetToken(rest, base());
        ResponseEntity<List> resp = rest.exchange(base() + "/api/export/markets",
                HttpMethod.GET, getWithToken(token), List.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull().hasSize(4);
    }

    @Test @DisplayName("sell_creditsNet_andRecords")
    @SuppressWarnings("unchecked")
    void sell_creditsNet_andRecords() {
        String token = registerAndGetToken(rest, base());
        long cid = createCharacter(rest, base(), token, uniqueName()).longValue();
        long itemId = growAndGetItemId(token, cid);
        Map<String, Object> item = getItem(token, cid, itemId);
        double quality = ((Number) item.get("quality")).doubleValue();

        double qty = 1.0;
        double walletBefore = getWallet(token, cid);
        double expectedNet = quality * 0.5 * qty * 1.30 * (1.0 - 0.10); // russia

        Map<String, Object> body = Map.of(
                "characterId", cid, "foreignMarketId", "russia", "cellarItemId", itemId, "quantity", qty);
        ResponseEntity<Map> resp = rest.postForEntity(base() + "/api/export/sell",
                withToken(body, token), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> r = resp.getBody();
        Map<String, Object> record = (Map<String, Object>) r.get("record");
        assertThat(((Number) record.get("netGel")).doubleValue()).isCloseTo(expectedNet, within(0.01));
        assertThat(((Number) r.get("walletGel")).doubleValue()).isCloseTo(walletBefore + expectedNet, within(0.01));
    }

    @Test @DisplayName("byzantium_paysMoreThanPoland")
    @SuppressWarnings("unchecked")
    void byzantium_paysMoreThanPoland() {
        String token = registerAndGetToken(rest, base());
        long cid = createCharacter(rest, base(), token, uniqueName()).longValue();
        long item1 = growAndGetItemId(token, cid);
        long item2 = growAndGetItemId(token, cid);

        double byzNet = sellNet(token, cid, "byzantium", item1, 1.0);
        double polNet = sellNet(token, cid, "poland_lithuania", item2, 1.0);
        assertThat(byzNet).as("Byzantium must pay more than Poland-Lithuania for the same bottle")
                .isGreaterThan(polNet);
    }

    @Test @DisplayName("sellMoreThanOwned_400")
    void sellMoreThanOwned_400() {
        String token = registerAndGetToken(rest, base());
        long cid = createCharacter(rest, base(), token, uniqueName()).longValue();
        long itemId = growAndGetItemId(token, cid);
        Map<String, Object> body = Map.of(
                "characterId", cid, "foreignMarketId", "russia", "cellarItemId", itemId, "quantity", 1_000_000.0);
        ResponseEntity<String> resp = rest.postForEntity(base() + "/api/export/sell",
                withToken(body, token), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test @DisplayName("sellUnownedItem_404")
    void sellUnownedItem_404() {
        String tokenA = registerAndGetToken(rest, base());
        long cidA = createCharacter(rest, base(), tokenA, uniqueName()).longValue();
        long itemA = growAndGetItemId(tokenA, cidA);

        String tokenB = registerAndGetToken(rest, base());
        long cidB = createCharacter(rest, base(), tokenB, uniqueName()).longValue();
        Map<String, Object> body = Map.of(
                "characterId", cidB, "foreignMarketId", "russia", "cellarItemId", itemA, "quantity", 1.0);
        ResponseEntity<String> resp = rest.postForEntity(base() + "/api/export/sell",
                withToken(body, tokenB), String.class);
        assertThat(resp.getStatusCode().value()).isIn(403, 404);
    }

    @Test @DisplayName("merchantSell_appliesSellMargin")
    @SuppressWarnings("unchecked")
    void merchantSell_appliesSellMargin() {
        String token = registerAndGetToken(rest, base());
        long cid = createCharacterWithCareer(token, uniqueName(), "MERCHANT"); // sellMargin 0.20
        long itemId = growAndGetItemId(token, cid);
        double quality = ((Number) getItem(token, cid, itemId).get("quality")).doubleValue();

        double qty = 1.0;
        double expectedGross = quality * 0.5 * qty * 1.30 * 1.20; // russia 1.30, +20% margin
        double expectedNet   = expectedGross * (1.0 - 0.10);      // russia tariff 0.10

        Map<String, Object> body = Map.of(
                "characterId", cid, "foreignMarketId", "russia", "cellarItemId", itemId, "quantity", qty);
        ResponseEntity<Map> resp = rest.postForEntity(base() + "/api/export/sell",
                withToken(body, token), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> record = (Map<String, Object>) resp.getBody().get("record");
        assertThat(((Number) record.get("netGel")).doubleValue())
                .as("Merchant +20% sell margin must raise the export net")
                .isCloseTo(expectedNet, within(0.01));
    }

    // ── helpers ──
    @SuppressWarnings("unchecked")
    private long createCharacterWithCareer(String token, String name, String career) {
        Map<String, String> body = Map.of("name", name, "careerType", career, "homeRegion", "KAKHETI");
        ResponseEntity<Map> resp = rest.postForEntity(base() + "/api/characters",
                withToken(body, token), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return ((Number) resp.getBody().get("id")).longValue();
    }

    @SuppressWarnings("unchecked")
    private double sellNet(String token, long cid, String market, long itemId, double qty) {
        Map<String, Object> body = Map.of(
                "characterId", cid, "foreignMarketId", market, "cellarItemId", itemId, "quantity", qty);
        ResponseEntity<Map> resp = rest.postForEntity(base() + "/api/export/sell",
                withToken(body, token), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> record = (Map<String, Object>) resp.getBody().get("record");
        return ((Number) record.get("netGel")).doubleValue();
    }

    @SuppressWarnings("unchecked")
    private long growAndGetItemId(String token, long cid) {
        Map<String, Object> body = Map.of("seed", 42L, "budLoad", 12, "pickDay", 270, "threats", false);
        ResponseEntity<Map> resp = rest.postForEntity(base() + "/api/cellar/" + cid + "/grow",
                withToken(body, token), Map.class);
        assertThat(resp.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.CREATED);
        Map<?, ?> r = resp.getBody();
        if (r.containsKey("cellarItem")) {
            return ((Number) ((Map<?, ?>) r.get("cellarItem")).get("id")).longValue();
        }
        return ((Number) r.get("id")).longValue();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getItem(String token, long cid, long itemId) {
        ResponseEntity<List> resp = rest.exchange(base() + "/api/cellar/" + cid,
                HttpMethod.GET, getWithToken(token), List.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        for (Object o : resp.getBody()) {
            Map<String, Object> m = (Map<String, Object>) o;
            if (((Number) m.get("id")).longValue() == itemId) return m;
        }
        throw new AssertionError("grown item " + itemId + " not found in cellar");
    }

    @SuppressWarnings("unchecked")
    private double getWallet(String token, long cid) {
        ResponseEntity<Map> resp = rest.exchange(base() + "/api/characters/" + cid,
                HttpMethod.GET, getWithToken(token), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return ((Number) resp.getBody().get("walletGel")).doubleValue();
    }
}
