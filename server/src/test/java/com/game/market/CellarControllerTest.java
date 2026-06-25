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
 * Integration tests for GET /api/cellar/{characterId} and
 * POST /api/cellar/{characterId}/grow  (MMO-CORE-SPEC §4 — Cellar endpoints).
 *
 * Assumptions (noted per spec):
 *  - POST /api/cellar/{characterId}/grow {seed, budLoad, pickDay, threats}
 *    → grows a vintage via VineyardService, stores a CellarItem, returns
 *      { cellarItem: {...}, vineyardResult: {...} }
 *    Exact field names of the wrapper object are assumed from the spec description;
 *    tests also accept a flat response that contains an "id" field directly.
 *  - GET  /api/cellar/{characterId} → CellarItem[] (non-escrowed only)
 *  - CellarItem has at least: id, characterId, quality, vintageYear fields
 *  - Determinism: same seed → same quality value (exact equality required).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class CellarControllerTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    private String base() {
        return "http://localhost:" + port;
    }

    private static String uniqueCharName() {
        return "ch_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    /** POST grow and return the raw response. */
    @SuppressWarnings("unchecked")
    private ResponseEntity<Map> doGrow(String token, long characterId,
                                        long seed, int budLoad, int pickDay, boolean threats) {
        Map<String, Object> body = Map.of(
                "seed", seed,
                "budLoad", budLoad,
                "pickDay", pickDay,
                "threats", threats);
        return rest.postForEntity(
                base() + "/api/cellar/" + characterId + "/grow",
                withToken(body, token),
                Map.class);
    }

    /** GET /api/cellar/{characterId} and return the list. */
    @SuppressWarnings("unchecked")
    private ResponseEntity<List> getCellar(String token, long characterId) {
        return rest.exchange(
                base() + "/api/cellar/" + characterId,
                HttpMethod.GET,
                getWithToken(token),
                List.class);
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("grow_addsBottleToCellar")
    @SuppressWarnings("unchecked")
    void grow_addsBottleToCellar() {
        String token = registerAndGetToken(rest, base());
        Number charId = createCharacter(rest, base(), token, uniqueCharName());
        long cid = charId.longValue();

        // Cellar should be empty before grow
        ResponseEntity<List> beforeResp = getCellar(token, cid);
        assertThat(beforeResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<?> before = beforeResp.getBody();
        int sizeBefore = before == null ? 0 : before.size();

        // Grow a vintage
        ResponseEntity<Map> growResp = doGrow(token, cid, 42L, 12, 270, false);
        assertThat(growResp.getStatusCode())
                .as("POST /api/cellar/{id}/grow must return 200 or 201")
                .isIn(HttpStatus.OK, HttpStatus.CREATED);

        // Cellar should now contain the new bottle
        ResponseEntity<List> afterResp = getCellar(token, cid);
        assertThat(afterResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<?> after = afterResp.getBody();
        assertThat(after)
                .as("Cellar must contain at least one more item after grow")
                .isNotNull()
                .hasSizeGreaterThan(sizeBefore);
    }

    @Test
    @DisplayName("grow_isDeterministic_sameSeedSameQuality")
    @SuppressWarnings("unchecked")
    void grow_isDeterministic_sameSeedSameQuality() {
        // Use two separate characters so the items don't interfere
        String token1 = registerAndGetToken(rest, base());
        Number charId1 = createCharacter(rest, base(), token1, uniqueCharName());

        String token2 = registerAndGetToken(rest, base());
        Number charId2 = createCharacter(rest, base(), token2, uniqueCharName());

        // Grow with the same seed on both characters
        ResponseEntity<Map> growResp1 = doGrow(token1, charId1.longValue(), 99L, 12, 270, false);
        ResponseEntity<Map> growResp2 = doGrow(token2, charId2.longValue(), 99L, 12, 270, false);

        assertThat(growResp1.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.CREATED);
        assertThat(growResp2.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.CREATED);

        // Extract quality from the response — the spec says the response includes
        // the new CellarItem.  Accept either a nested {"cellarItem": {...}} wrapper
        // or a flat response with "quality" directly.
        double quality1 = extractQuality(growResp1.getBody());
        double quality2 = extractQuality(growResp2.getBody());

        assertThat(quality1)
                .as("Same seed must produce identical quality (MMO-CORE-SPEC §7 determinism; "
                        + "exact equality, no tolerance)")
                .isEqualTo(quality2);
    }

    /**
     * Extract the quality value from a grow response.
     * Supports both:
     *   { "cellarItem": { "quality": 73.4 }, "vineyardResult": {...} }
     * and:
     *   { "quality": 73.4, ... }   (flat CellarItem)
     */
    @SuppressWarnings("unchecked")
    private static double extractQuality(Map<?, ?> body) {
        assertThat(body).as("grow response body must not be null").isNotNull();

        if (body.containsKey("cellarItem")) {
            Map<?, ?> item = (Map<?, ?>) body.get("cellarItem");
            return ((Number) item.get("quality")).doubleValue();
        }
        // Flat response
        Object q = body.get("quality");
        assertThat(q).as("grow response must contain quality field").isNotNull();
        return ((Number) q).doubleValue();
    }
}
