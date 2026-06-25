package com.game.career;

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
 * Integration tests for /api/career/** (LANE CAREERS).
 *
 * <p>Scenarios covered:
 * <ol>
 *   <li>GET /api/career/catalog → 9 profiles, one per CareerType.</li>
 *   <li>Catalog spot-check: MERCHANT has sellMarginMult=0.20; GROWER has yieldMult=0.15.</li>
 *   <li>GET /api/career/{characterId} → returns matching CareerProfile for their careerType.</li>
 *   <li>Ownership: another account's character → 404.</li>
 *   <li>No token on catalog → 401.</li>
 *   <li>No token on character endpoint → 401.</li>
 * </ol>
 *
 * <p>Character creation with a SPECIFIC careerType mirrors ProfessionControllerTest:
 * POST /api/characters with {name, careerType, homeRegion} directly (not the default helper),
 * so each test controls which career the character has.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class CareerControllerTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    private String base() {
        return "http://localhost:" + port;
    }

    private static String uniqueName() {
        return "car_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    /**
     * Register a fresh account and create a character with the given careerType.
     * Returns a map with "token" (String) and "charId" (long).
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> registerWithCareer(String careerType) {
        String token = registerAndGetToken(rest, base());

        Map<String, String> body = Map.of(
                "name",       uniqueName(),
                "careerType", careerType,
                "homeRegion", "KAKHETI");

        ResponseEntity<Map> resp = rest.postForEntity(
                base() + "/api/characters",
                withToken(body, token),
                Map.class);

        assertThat(resp.getStatusCode())
                .as("character creation (" + careerType + ") must return 201")
                .isEqualTo(HttpStatus.CREATED);

        long charId = ((Number) resp.getBody().get("id")).longValue();
        return Map.of("token", token, "charId", charId);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 1. GET /api/career/catalog → 9 profiles, one per CareerType
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("catalog_returnsExactly9Profiles")
    @SuppressWarnings("unchecked")
    void catalog_returnsExactly9Profiles() {
        String token = registerAndGetToken(rest, base());

        ResponseEntity<List> resp = rest.exchange(
                base() + "/api/career/catalog",
                HttpMethod.GET,
                getWithToken(token),
                List.class);

        assertThat(resp.getStatusCode())
                .as("GET /api/career/catalog must return 200")
                .isEqualTo(HttpStatus.OK);

        List<?> catalog = resp.getBody();
        assertThat(catalog)
                .as("Catalog must contain exactly 9 career profiles")
                .isNotNull()
                .hasSize(9);

        // All 9 careerType names must be present
        List<String> expectedCareers = List.of(
                "GROWER", "WINEMAKER", "ENOLOGIST", "NEGOCIANT",
                "BROKER", "COOPER", "NURSERYMAN", "HAULER", "MERCHANT");

        List<String> actualNames = catalog.stream()
                .map(o -> (String) ((Map<String, Object>) o).get("careerType"))
                .toList();

        for (String career : expectedCareers) {
            assertThat(actualNames)
                    .as("Catalog must contain careerType: " + career)
                    .contains(career);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. Catalog spot-checks from the CONTENT-BIBLE
    //    MERCHANT sellMarginMult = 0.20
    //    GROWER   yieldMult      = 0.15
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("catalog_merchant_hasSellMarginMult_0_20")
    @SuppressWarnings("unchecked")
    void catalog_merchant_hasSellMarginMult_0_20() {
        String token = registerAndGetToken(rest, base());

        ResponseEntity<List> resp = rest.exchange(
                base() + "/api/career/catalog",
                HttpMethod.GET,
                getWithToken(token),
                List.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);

        List<?> catalog = resp.getBody();
        assertThat(catalog).isNotNull();

        Map<String, Object> merchant = catalog.stream()
                .map(o -> (Map<String, Object>) o)
                .filter(m -> "MERCHANT".equals(m.get("careerType")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("MERCHANT not found in catalog"));

        assertThat(merchant).containsKey("sellMarginMult");
        double sellMarginMult = ((Number) merchant.get("sellMarginMult")).doubleValue();
        assertThat(sellMarginMult)
                .as("MERCHANT sellMarginMult must be 0.20 per CONTENT-BIBLE")
                .isEqualTo(0.20);

        // MERCHANT does NOT boost yield
        double yieldMult = ((Number) merchant.get("yieldMult")).doubleValue();
        assertThat(yieldMult)
                .as("MERCHANT yieldMult must be 0.0 (neutral default)")
                .isEqualTo(0.0);
    }

    @Test
    @DisplayName("catalog_grower_hasYieldMult_0_15")
    @SuppressWarnings("unchecked")
    void catalog_grower_hasYieldMult_0_15() {
        String token = registerAndGetToken(rest, base());

        ResponseEntity<List> resp = rest.exchange(
                base() + "/api/career/catalog",
                HttpMethod.GET,
                getWithToken(token),
                List.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);

        List<?> catalog = resp.getBody();
        assertThat(catalog).isNotNull();

        Map<String, Object> grower = catalog.stream()
                .map(o -> (Map<String, Object>) o)
                .filter(m -> "GROWER".equals(m.get("careerType")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("GROWER not found in catalog"));

        assertThat(grower).containsKey("yieldMult");
        double yieldMult = ((Number) grower.get("yieldMult")).doubleValue();
        assertThat(yieldMult)
                .as("GROWER yieldMult must be 0.15 per CONTENT-BIBLE")
                .isEqualTo(0.15);

        // GROWER does NOT boost sell margin
        double sellMarginMult = ((Number) grower.get("sellMarginMult")).doubleValue();
        assertThat(sellMarginMult)
                .as("GROWER sellMarginMult must be 0.0 (neutral default)")
                .isEqualTo(0.0);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. GET /api/career/{characterId} → returns the profile matching careerType
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("characterEndpoint_returnsMatchingProfile_forMerchant")
    @SuppressWarnings("unchecked")
    void characterEndpoint_returnsMatchingProfile_forMerchant() {
        Map<String, Object> ctx = registerWithCareer("MERCHANT");
        String token = (String) ctx.get("token");
        long charId  = ((Number) ctx.get("charId")).longValue();

        ResponseEntity<Map> resp = rest.exchange(
                base() + "/api/career/" + charId,
                HttpMethod.GET,
                getWithToken(token),
                Map.class);

        assertThat(resp.getStatusCode())
                .as("GET /api/career/{characterId} must return 200")
                .isEqualTo(HttpStatus.OK);

        Map<String, Object> profile = resp.getBody();
        assertThat(profile).isNotNull();
        assertThat(profile).containsKey("careerType");
        assertThat(profile.get("careerType"))
                .as("Profile careerType must be MERCHANT")
                .isEqualTo("MERCHANT");

        assertThat(profile).containsKey("sellMarginMult");
        double sellMarginMult = ((Number) profile.get("sellMarginMult")).doubleValue();
        assertThat(sellMarginMult)
                .as("MERCHANT sellMarginMult must be 0.20")
                .isEqualTo(0.20);

        assertThat(profile).containsKey("summary");
        assertThat(profile).containsKey("pro");
        assertThat(profile).containsKey("con");
    }

    @Test
    @DisplayName("characterEndpoint_returnsMatchingProfile_forHauler")
    @SuppressWarnings("unchecked")
    void characterEndpoint_returnsMatchingProfile_forHauler() {
        Map<String, Object> ctx = registerWithCareer("HAULER");
        String token = (String) ctx.get("token");
        long charId  = ((Number) ctx.get("charId")).longValue();

        ResponseEntity<Map> resp = rest.exchange(
                base() + "/api/career/" + charId,
                HttpMethod.GET,
                getWithToken(token),
                Map.class);

        assertThat(resp.getStatusCode())
                .as("GET /api/career/{characterId} for HAULER must return 200")
                .isEqualTo(HttpStatus.OK);

        Map<String, Object> profile = resp.getBody();
        assertThat(profile).isNotNull();
        assertThat(profile.get("careerType")).isEqualTo("HAULER");

        double shippingDiscountMult = ((Number) profile.get("shippingDiscountMult")).doubleValue();
        assertThat(shippingDiscountMult)
                .as("HAULER shippingDiscountMult must be 0.30")
                .isEqualTo(0.30);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. Ownership: another account → 404
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("ownership_otherAccount_404")
    @SuppressWarnings("unchecked")
    void ownership_otherAccount_404() {
        // Owner creates character
        Map<String, Object> ownerCtx = registerWithCareer("GROWER");
        long charId = ((Number) ownerCtx.get("charId")).longValue();

        // Completely different account attempts to read that character's career profile
        String otherToken = registerAndGetToken(rest, base());

        ResponseEntity<String> resp = rest.exchange(
                base() + "/api/career/" + charId,
                HttpMethod.GET,
                getWithToken(otherToken),
                String.class);

        assertThat(resp.getStatusCode().value())
                .as("Non-owner must receive 404 or 403 for another account's character career")
                .isIn(404, 403);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 5. No token on catalog → 401
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("catalog_noToken_401")
    void catalog_noToken_401() {
        ResponseEntity<String> resp = rest.getForEntity(
                base() + "/api/career/catalog",
                String.class);

        assertThat(resp.getStatusCode())
                .as("GET /api/career/catalog without token must return 401")
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 6. No token on character endpoint → 401
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("characterEndpoint_noToken_401")
    void characterEndpoint_noToken_401() {
        ResponseEntity<String> resp = rest.getForEntity(
                base() + "/api/career/99999",
                String.class);

        assertThat(resp.getStatusCode())
                .as("GET /api/career/{characterId} without token must return 401")
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
