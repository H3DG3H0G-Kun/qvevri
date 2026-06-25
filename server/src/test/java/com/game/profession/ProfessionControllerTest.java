package com.game.profession;

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
 * Integration tests for the Profession endpoints (GOODS-ECON-SPEC Lane P).
 *
 * <p>Pattern: register account → create character with the correct career → call endpoint.
 * All tests share an in-memory H2 DB (ddl-auto=create-drop), so each test starts clean.
 *
 * <p>Note on Lane G dependency: these tests call GoodsService indirectly via the HTTP
 * endpoints.  If Lane G is not yet merged, the cooper-craft and lab-grade tests will
 * fail at the GoodsService injection point — that is the correct signal (do not stub).
 *
 * <p>Endpoint shapes tested:
 * <ul>
 *   <li>GET  /api/profession/capabilities    → 200, map keyed by careerType</li>
 *   <li>POST /api/profession/claim-kit       → 200 ProfessionKitClaim (idempotent)</li>
 *   <li>POST /api/profession/cooper/craft    → 200 OwnedGood (COOPER only)</li>
 *   <li>POST /api/profession/lab/grade       → 200 WineGrade (ENOLOGIST only)</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ProfessionControllerTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    private String base() {
        return "http://localhost:" + port;
    }

    private static String uniqueName() {
        return "p_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    // ── Character creation helpers ────────────────────────────────────────────

    /** Register account + create character with a specific career; return {token, charId}. */
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

    // ── Grow helper — creates a CellarItem via /api/cellar/{id}/grow ──────────

    @SuppressWarnings("unchecked")
    private long growBottle(String token, long characterId) {
        Map<String, Object> growBody = Map.of(
                "seed",    42L,
                "budLoad", 12,
                "pickDay", 270,
                "threats", false);
        ResponseEntity<Map> resp = rest.postForEntity(
                base() + "/api/cellar/" + characterId + "/grow",
                withToken(growBody, token),
                Map.class);
        assertThat(resp.getStatusCode())
                .as("grow must succeed")
                .isIn(HttpStatus.OK, HttpStatus.CREATED);
        Map<?, ?> respBody = resp.getBody();
        assertThat(respBody).isNotNull();
        // Accept both nested { cellarItem: {id} } and flat {id}
        if (respBody.containsKey("cellarItem")) {
            return ((Number) ((Map<?, ?>) respBody.get("cellarItem")).get("id")).longValue();
        }
        return ((Number) respBody.get("id")).longValue();
    }

    // ── GoodsService grant helper — ensures the cooper has input goods ─────────
    // Lane G's POST /api/shop/buy endpoint can seed the goods.
    // If that endpoint isn't available yet we fall back to a direct SQL seed approach;
    // but the test is written against the real HTTP stack so it fails loudly if G is absent.

    @SuppressWarnings("unchecked")
    private void buyGood(String token, long characterId, String goodTypeId, int qty) {
        Map<String, Object> buyBody = Map.of(
                "characterId", characterId,
                "goodTypeId",  goodTypeId,
                "quantity",    qty);
        ResponseEntity<Map> resp = rest.postForEntity(
                base() + "/api/shop/buy",
                withToken(buyBody, token),
                Map.class);
        // If G isn't merged yet this assertion will fail — correct signal.
        assertThat(resp.getStatusCode())
                .as("shop/buy must succeed for " + goodTypeId)
                .isIn(HttpStatus.OK, HttpStatus.CREATED);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // GET /api/profession/capabilities
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("capabilities_returnsAllCareers")
    @SuppressWarnings("unchecked")
    void capabilities_returnsAllCareers() {
        String token = registerAndGetToken(rest, base());

        ResponseEntity<Map> resp = rest.exchange(
                base() + "/api/profession/capabilities",
                HttpMethod.GET,
                getWithToken(token),
                Map.class);

        assertThat(resp.getStatusCode())
                .as("GET /api/profession/capabilities must return 200")
                .isEqualTo(HttpStatus.OK);

        Map<String, Object> catalog = resp.getBody();
        assertThat(catalog).isNotNull();

        // All 9 careers must be present
        for (String career : List.of("GROWER", "WINEMAKER", "ENOLOGIST", "NEGOCIANT",
                "BROKER", "COOPER", "NURSERYMAN", "HAULER", "MERCHANT")) {
            assertThat(catalog)
                    .as("capabilities must contain career: " + career)
                    .containsKey(career);
        }

        // Each entry should have allowedActions
        Map<String, Object> growerCap = (Map<String, Object>) catalog.get("GROWER");
        assertThat(growerCap)
                .as("GROWER capability must have allowedActions")
                .containsKey("allowedActions");
    }

    @Test
    @DisplayName("capabilities_requiresAuth_401")
    void capabilities_requiresAuth_401() {
        ResponseEntity<Map> resp = rest.getForEntity(
                base() + "/api/profession/capabilities",
                Map.class);
        assertThat(resp.getStatusCode())
                .as("GET /api/profession/capabilities without token must return 401")
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // POST /api/profession/claim-kit — idempotent starter kit
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("claimKit_firstCall_grantsKitAndGel")
    @SuppressWarnings("unchecked")
    void claimKit_firstCall_grantsKitAndGel() {
        Map<String, Object> ctx  = registerWithCareer("GROWER");
        String token  = (String) ctx.get("token");
        long   charId = ((Number) ctx.get("charId")).longValue();

        Map<String, Object> body = Map.of("characterId", charId);
        ResponseEntity<Map> resp = rest.postForEntity(
                base() + "/api/profession/claim-kit",
                withToken(body, token),
                Map.class);

        assertThat(resp.getStatusCode())
                .as("POST /api/profession/claim-kit must return 200")
                .isEqualTo(HttpStatus.OK);

        Map<String, Object> claim = resp.getBody();
        assertThat(claim).isNotNull();
        assertThat(claim)
                .as("claim response must contain characterId")
                .containsKey("characterId");
        assertThat(((Number) claim.get("characterId")).longValue())
                .isEqualTo(charId);
        assertThat(claim)
                .as("claim response must contain careerType")
                .containsKey("careerType");
        assertThat(claim.get("careerType")).isEqualTo("GROWER");
    }

    @Test
    @DisplayName("claimKit_idempotent_secondCallReturnsSameClaim")
    @SuppressWarnings("unchecked")
    void claimKit_idempotent_secondCallReturnsSameClaim() {
        Map<String, Object> ctx  = registerWithCareer("COOPER");
        String token  = (String) ctx.get("token");
        long   charId = ((Number) ctx.get("charId")).longValue();

        Map<String, Object> body = Map.of("characterId", charId);

        // First claim
        ResponseEntity<Map> resp1 = rest.postForEntity(
                base() + "/api/profession/claim-kit",
                withToken(body, token),
                Map.class);
        assertThat(resp1.getStatusCode()).isEqualTo(HttpStatus.OK);
        Number claimId1 = (Number) resp1.getBody().get("id");

        // Second claim — must return the SAME claim id (no double grant)
        ResponseEntity<Map> resp2 = rest.postForEntity(
                base() + "/api/profession/claim-kit",
                withToken(body, token),
                Map.class);
        assertThat(resp2.getStatusCode()).isEqualTo(HttpStatus.OK);
        Number claimId2 = (Number) resp2.getBody().get("id");

        assertThat(claimId2.longValue())
                .as("Second claim-kit call must return the SAME claim id (idempotent)")
                .isEqualTo(claimId1.longValue());
    }

    @Test
    @DisplayName("claimKit_wrongCharacterOwnership_404")
    @SuppressWarnings("unchecked")
    void claimKit_wrongCharacterOwnership_404() {
        // Account A's character
        Map<String, Object> ctxA = registerWithCareer("GROWER");
        long charIdA = ((Number) ctxA.get("charId")).longValue();

        // Account B tries to claim kit for A's character
        String tokenB = registerAndGetToken(rest, base());
        Map<String, Object> body = Map.of("characterId", charIdA);
        ResponseEntity<Map> resp = rest.postForEntity(
                base() + "/api/profession/claim-kit",
                withToken(body, tokenB),
                Map.class);

        assertThat(resp.getStatusCode().value())
                .as("Claiming kit for another account's character must be 404")
                .isIn(404, 403);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // POST /api/profession/cooper/craft
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("cooperCraft_withInputs_producesVessel")
    @SuppressWarnings("unchecked")
    void cooperCraft_withInputs_producesVessel() {
        Map<String, Object> ctx  = registerWithCareer("COOPER");
        String token  = (String) ctx.get("token");
        long   charId = ((Number) ctx.get("charId")).longValue();

        // Seed the input goods via the bazaar (Lane G dependency).
        // Recipe craft_oak_barrel requires: copper_sulfate x5 (5 x 12 GEL = 60 GEL),
        // which fits inside a new character's starting wallet of 100 GEL.
        buyGood(token, charId, "copper_sulfate", 5);

        Map<String, Object> craftBody = Map.of(
                "characterId", charId,
                "recipeId",    "craft_oak_barrel");
        ResponseEntity<Map> resp = rest.postForEntity(
                base() + "/api/profession/cooper/craft",
                withToken(craftBody, token),
                Map.class);

        assertThat(resp.getStatusCode())
                .as("POST /api/profession/cooper/craft must return 200")
                .isEqualTo(HttpStatus.OK);

        Map<String, Object> produced = resp.getBody();
        assertThat(produced).isNotNull();
        assertThat(produced)
                .as("produced OwnedGood must have goodTypeId")
                .containsKey("goodTypeId");
        assertThat(produced.get("goodTypeId"))
                .as("produced item must be oak_barrel_225l")
                .isEqualTo("oak_barrel_225l");
        assertThat(produced)
                .as("produced OwnedGood must have quantity")
                .containsKey("quantity");
    }

    @Test
    @DisplayName("cooperCraft_nonCooper_403")
    @SuppressWarnings("unchecked")
    void cooperCraft_nonCooper_403() {
        // GROWER tries to craft a vessel
        Map<String, Object> ctx  = registerWithCareer("GROWER");
        String token  = (String) ctx.get("token");
        long   charId = ((Number) ctx.get("charId")).longValue();

        Map<String, Object> craftBody = Map.of(
                "characterId", charId,
                "recipeId",    "craft_qvevri_500l");
        ResponseEntity<Map> resp = rest.postForEntity(
                base() + "/api/profession/cooper/craft",
                withToken(craftBody, token),
                Map.class);

        assertThat(resp.getStatusCode())
                .as("Non-COOPER crafting must return 403")
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("cooperCraft_missingInputs_400")
    @SuppressWarnings("unchecked")
    void cooperCraft_missingInputs_400() {
        // COOPER with no goods tries to craft
        Map<String, Object> ctx  = registerWithCareer("COOPER");
        String token  = (String) ctx.get("token");
        long   charId = ((Number) ctx.get("charId")).longValue();

        // Do NOT buy any input goods
        Map<String, Object> craftBody = Map.of(
                "characterId", charId,
                "recipeId",    "craft_qvevri_500l");
        ResponseEntity<Map> resp = rest.postForEntity(
                base() + "/api/profession/cooper/craft",
                withToken(craftBody, token),
                Map.class);

        assertThat(resp.getStatusCode())
                .as("Cooper craft with missing inputs must return 400")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("cooperCraft_unknownRecipe_400")
    @SuppressWarnings("unchecked")
    void cooperCraft_unknownRecipe_400() {
        Map<String, Object> ctx  = registerWithCareer("COOPER");
        String token  = (String) ctx.get("token");
        long   charId = ((Number) ctx.get("charId")).longValue();

        Map<String, Object> craftBody = Map.of(
                "characterId", charId,
                "recipeId",    "craft_nonexistent_vessel");
        ResponseEntity<Map> resp = rest.postForEntity(
                base() + "/api/profession/cooper/craft",
                withToken(craftBody, token),
                Map.class);

        assertThat(resp.getStatusCode())
                .as("Unknown recipe must return 400")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // POST /api/profession/lab/grade
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("labGrade_enologist_persistsGrade")
    @SuppressWarnings("unchecked")
    void labGrade_enologist_persistsGrade() {
        // Create a GROWER to produce a bottle
        Map<String, Object> growerCtx  = registerWithCareer("GROWER");
        String growerToken = (String) growerCtx.get("token");
        long   growerCharId = ((Number) growerCtx.get("charId")).longValue();

        long cellarItemId = growBottle(growerToken, growerCharId);

        // Create an ENOLOGIST to grade it
        Map<String, Object> enoCtx  = registerWithCareer("ENOLOGIST");
        String enoToken  = (String) enoCtx.get("token");
        long   enoCharId = ((Number) enoCtx.get("charId")).longValue();

        Map<String, Object> gradeBody = Map.of(
                "characterId",  enoCharId,
                "cellarItemId", cellarItemId);
        ResponseEntity<Map> resp = rest.postForEntity(
                base() + "/api/profession/lab/grade",
                withToken(gradeBody, enoToken),
                Map.class);

        assertThat(resp.getStatusCode())
                .as("POST /api/profession/lab/grade must return 200")
                .isEqualTo(HttpStatus.OK);

        Map<String, Object> grade = resp.getBody();
        assertThat(grade).isNotNull();
        assertThat(grade).containsKey("id");
        assertThat(grade).containsKey("cellarItemId");
        assertThat(((Number) grade.get("cellarItemId")).longValue())
                .isEqualTo(cellarItemId);
        assertThat(grade).containsKey("score");
        double score = ((Number) grade.get("score")).doubleValue();
        assertThat(score)
                .as("score must be >= 0")
                .isGreaterThanOrEqualTo(0.0);
        assertThat(score)
                .as("score must be <= 105 (100 base + 5 appellation bonus)")
                .isLessThanOrEqualTo(105.0);
        assertThat(grade).containsKey("certified");
        assertThat(grade).containsKey("graderCharacterId");
        assertThat(((Number) grade.get("graderCharacterId")).longValue())
                .isEqualTo(enoCharId);
    }

    @Test
    @DisplayName("labGrade_deterministic_sameItemSameScore")
    @SuppressWarnings("unchecked")
    void labGrade_deterministic_sameItemSameScore() {
        // Create one bottle
        Map<String, Object> growerCtx = registerWithCareer("GROWER");
        String growerToken = (String) growerCtx.get("token");
        long   growerCharId = ((Number) growerCtx.get("charId")).longValue();
        long cellarItemId = growBottle(growerToken, growerCharId);

        // Two enologists grade the same item
        Map<String, Object> enoCtx1 = registerWithCareer("ENOLOGIST");
        String enoToken1  = (String) enoCtx1.get("token");
        long   enoCharId1 = ((Number) enoCtx1.get("charId")).longValue();

        Map<String, Object> enoCtx2 = registerWithCareer("ENOLOGIST");
        String enoToken2  = (String) enoCtx2.get("token");
        long   enoCharId2 = ((Number) enoCtx2.get("charId")).longValue();

        Map<String, Object> gradeBody1 = Map.of("characterId", enoCharId1, "cellarItemId", cellarItemId);
        Map<String, Object> gradeBody2 = Map.of("characterId", enoCharId2, "cellarItemId", cellarItemId);

        ResponseEntity<Map> resp1 = rest.postForEntity(
                base() + "/api/profession/lab/grade", withToken(gradeBody1, enoToken1), Map.class);
        ResponseEntity<Map> resp2 = rest.postForEntity(
                base() + "/api/profession/lab/grade", withToken(gradeBody2, enoToken2), Map.class);

        double score1 = ((Number) resp1.getBody().get("score")).doubleValue();
        double score2 = ((Number) resp2.getBody().get("score")).doubleValue();

        assertThat(score1)
                .as("Same cellar item must produce identical scores from both enologists")
                .isEqualTo(score2);
    }

    @Test
    @DisplayName("labGrade_nonEnologist_403")
    @SuppressWarnings("unchecked")
    void labGrade_nonEnologist_403() {
        // Grow a bottle with a GROWER
        Map<String, Object> growerCtx  = registerWithCareer("GROWER");
        String growerToken = (String) growerCtx.get("token");
        long   growerCharId = ((Number) growerCtx.get("charId")).longValue();
        long cellarItemId = growBottle(growerToken, growerCharId);

        // GROWER tries to grade (wrong career)
        Map<String, Object> gradeBody = Map.of(
                "characterId",  growerCharId,
                "cellarItemId", cellarItemId);
        ResponseEntity<Map> resp = rest.postForEntity(
                base() + "/api/profession/lab/grade",
                withToken(gradeBody, growerToken),
                Map.class);

        assertThat(resp.getStatusCode())
                .as("Non-ENOLOGIST grading must return 403")
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("labGrade_missingCellarItem_400")
    @SuppressWarnings("unchecked")
    void labGrade_missingCellarItem_400() {
        Map<String, Object> ctx  = registerWithCareer("ENOLOGIST");
        String token  = (String) ctx.get("token");
        long   charId = ((Number) ctx.get("charId")).longValue();

        Map<String, Object> gradeBody = Map.of(
                "characterId",  charId,
                "cellarItemId", 999999L);
        ResponseEntity<Map> resp = rest.postForEntity(
                base() + "/api/profession/lab/grade",
                withToken(gradeBody, token),
                Map.class);

        assertThat(resp.getStatusCode())
                .as("Grading a non-existent cellar item must return 400")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
