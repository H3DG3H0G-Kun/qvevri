package com.game.wine;

import com.game.account.AccountTestHelper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;
import java.util.UUID;

import static com.game.account.AccountTestHelper.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for /api/wine/** (LANE WINE — winemaking depth v1).
 *
 * <p>These exercise the gated fermentation flow added alongside (not replacing) the
 * existing instant-harvest path. The default-harvest byte-identical guarantee is
 * covered by the pre-existing VineyardEstateControllerTest harvest assertions, which
 * must stay green; here we only test the NEW opt-in /api/wine endpoints.
 *
 * <p>Default fermentation with no vessel takes 14 sim-days; the test world clock is
 * frozen, so a freshly-started item stays FERMENTING — which makes the status,
 * double-start, and bottle-not-ready assertions deterministic.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class WineControllerTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    private String base() {
        return "http://localhost:" + port;
    }

    private static String uniqueName() {
        return "wn_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    // ── 1. Start fermentation (no vessel) → FERMENTING ────────────────────────

    @Test
    @DisplayName("startFermentation_noVessel_returnsFermenting")
    @SuppressWarnings("unchecked")
    void startFermentation_noVessel_returnsFermenting() {
        String token = registerAndGetToken(rest, base());
        long cid = createCharacter(rest, base(), token, uniqueName()).longValue();
        long itemId = growAndGetItemId(token, cid).longValue();

        Map<String, Object> body = Map.of("characterId", cid, "cellarItemId", itemId);
        ResponseEntity<Map> resp = rest.postForEntity(
                base() + "/api/wine/ferment/start", withToken(body, token), Map.class);

        assertThat(resp.getStatusCode())
                .as("POST /api/wine/ferment/start must return 200")
                .isEqualTo(HttpStatus.OK);
        Map<String, Object> view = resp.getBody();
        assertThat(view).isNotNull();
        assertThat(view.get("fermentationState"))
                .as("state must be FERMENTING right after start")
                .isEqualTo("FERMENTING");
        assertThat(view).containsKey("fermentReadyDay");
    }

    // ── 2. Status after start → 200, still FERMENTING on a frozen clock ───────

    @Test
    @DisplayName("status_afterStart_returnsFermenting")
    @SuppressWarnings("unchecked")
    void status_afterStart_returnsFermenting() {
        String token = registerAndGetToken(rest, base());
        long cid = createCharacter(rest, base(), token, uniqueName()).longValue();
        long itemId = growAndGetItemId(token, cid).longValue();
        startFerment(token, cid, itemId);

        ResponseEntity<Map> resp = rest.exchange(
                base() + "/api/wine/ferment/" + itemId + "/status?characterId=" + cid,
                HttpMethod.GET, getWithToken(token), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> view = resp.getBody();
        assertThat(view).isNotNull();
        assertThat(view.get("fermentationState")).isEqualTo("FERMENTING");
    }

    // ── 3. Missing cellarItemId → 400 ────────────────────────────────────────

    @Test
    @DisplayName("start_missingCellarItemId_400")
    @SuppressWarnings("unchecked")
    void start_missingCellarItemId_400() {
        String token = registerAndGetToken(rest, base());
        long cid = createCharacter(rest, base(), token, uniqueName()).longValue();

        Map<String, Object> body = Map.of("characterId", cid); // no cellarItemId
        ResponseEntity<Map> resp = rest.postForEntity(
                base() + "/api/wine/ferment/start", withToken(body, token), Map.class);

        assertThat(resp.getStatusCode())
                .as("Missing cellarItemId must return 400")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ── 4. Ownership: another account cannot ferment your item → 404 ─────────

    @Test
    @DisplayName("start_otherAccountsItem_404")
    @SuppressWarnings("unchecked")
    void start_otherAccountsItem_404() {
        // Owner A grows an item
        String tokenA = registerAndGetToken(rest, base());
        long cidA = createCharacter(rest, base(), tokenA, uniqueName()).longValue();
        long itemId = growAndGetItemId(tokenA, cidA).longValue();

        // Account B tries to ferment A's item using B's own character
        String tokenB = registerAndGetToken(rest, base());
        long cidB = createCharacter(rest, base(), tokenB, uniqueName()).longValue();

        Map<String, Object> body = Map.of("characterId", cidB, "cellarItemId", itemId);
        ResponseEntity<Map> resp = rest.postForEntity(
                base() + "/api/wine/ferment/start", withToken(body, tokenB), Map.class);

        assertThat(resp.getStatusCode().value())
                .as("Fermenting another account's item must be rejected")
                .isIn(403, 404);
    }

    // ── 5. Double start → 400 ────────────────────────────────────────────────

    @Test
    @DisplayName("doubleStart_rejected_400")
    @SuppressWarnings("unchecked")
    void doubleStart_rejected_400() {
        String token = registerAndGetToken(rest, base());
        long cid = createCharacter(rest, base(), token, uniqueName()).longValue();
        long itemId = growAndGetItemId(token, cid).longValue();
        startFerment(token, cid, itemId);

        Map<String, Object> body = Map.of("characterId", cid, "cellarItemId", itemId);
        ResponseEntity<Map> resp = rest.postForEntity(
                base() + "/api/wine/ferment/start", withToken(body, token), Map.class);

        assertThat(resp.getStatusCode())
                .as("Starting fermentation twice must return 400")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ── 6. Bottle before READY → 400 ─────────────────────────────────────────

    @Test
    @DisplayName("bottle_whenNotReady_400")
    @SuppressWarnings("unchecked")
    void bottle_whenNotReady_400() {
        String token = registerAndGetToken(rest, base());
        long cid = createCharacter(rest, base(), token, uniqueName()).longValue();
        long itemId = growAndGetItemId(token, cid).longValue();
        startFerment(token, cid, itemId); // still FERMENTING (14-day default)

        Map<String, Object> body = Map.of("characterId", cid, "cellarItemId", itemId);
        ResponseEntity<Map> resp = rest.postForEntity(
                base() + "/api/wine/ferment/bottle", withToken(body, token), Map.class);

        assertThat(resp.getStatusCode())
                .as("Bottling before READY must return 400")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private void startFerment(String token, long characterId, long cellarItemId) {
        Map<String, Object> body = Map.of("characterId", characterId, "cellarItemId", cellarItemId);
        ResponseEntity<Map> resp = rest.postForEntity(
                base() + "/api/wine/ferment/start", withToken(body, token), Map.class);
        assertThat(resp.getStatusCode())
                .as("seed: start fermentation must succeed")
                .isEqualTo(HttpStatus.OK);
    }

    /** Grow a vintage via the existing cellar endpoint and return its id. */
    @SuppressWarnings("unchecked")
    private Number growAndGetItemId(String token, long characterId) {
        Map<String, Object> body = Map.of(
                "seed", 42L, "budLoad", 12, "pickDay", 270, "threats", false);
        ResponseEntity<Map> resp = rest.postForEntity(
                base() + "/api/cellar/" + characterId + "/grow",
                withToken(body, token), Map.class);
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
}
