package com.game.research;

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
 * Integration tests for /api/research/** (LANE RESEARCH).
 *
 * <p>Scenarios covered:
 * <ol>
 *   <li>GET /api/research/catalog → returns all 6 research nodes.</li>
 *   <li>POST /api/research/{nodeId}/start (no-prereq, cheap node) → creates
 *       RESEARCHING row and debits wallet.</li>
 *   <li>POST start with prereq not COMPLETE → 400 PREREQ_NOT_MET.</li>
 *   <li>After POST /api/world/advance past readyDay, GET shows COMPLETE.</li>
 *   <li>Once prereq is COMPLETE, the dependent node can be started.</li>
 *   <li>Double-start → 400.</li>
 *   <li>Insufficient funds → 400.</li>
 *   <li>Ownership enforced: other account → 404.</li>
 * </ol>
 *
 * <p>Uses {@link AccountTestHelper} static helpers and {@code Map<String,Object>}
 * JSON bodies exactly like QuestControllerTest / ContestControllerTest.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ResearchControllerTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    private String base() {
        return "http://localhost:" + port;
    }

    private static String uniqueName() {
        return "res_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 1. GET /api/research/catalog → 6 nodes
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("catalog_returnsSixNodes")
    @SuppressWarnings("unchecked")
    void catalog_returnsSixNodes() {
        String token = registerAndGetToken(rest, base());

        ResponseEntity<List> resp = rest.exchange(
                base() + "/api/research/catalog",
                HttpMethod.GET,
                getWithToken(token),
                List.class);

        assertThat(resp.getStatusCode())
                .as("GET /api/research/catalog must return 200")
                .isEqualTo(HttpStatus.OK);

        List<?> catalog = resp.getBody();
        assertThat(catalog)
                .as("Catalog must contain exactly 6 research nodes")
                .isNotNull()
                .hasSize(6);

        // Every entry must have the required fields
        for (Object entry : catalog) {
            Map<String, Object> node = (Map<String, Object>) entry;
            assertThat(node)
                    .as("Each node must have required fields")
                    .containsKeys("id", "title", "description", "costGel",
                                  "durationDays", "bonusType", "bonusValue");
        }

        // Spot-check: improved_pruning exists as the cheap root node
        boolean hasImprovedPruning = catalog.stream()
                .anyMatch(o -> "improved_pruning".equals(((Map<?, ?>) o).get("id")));
        assertThat(hasImprovedPruning)
                .as("Catalog must contain 'improved_pruning'")
                .isTrue();

        // Spot-check: cold_soak with prereq temp_control exists
        boolean hasColdSoak = catalog.stream()
                .anyMatch(o -> "cold_soak".equals(((Map<?, ?>) o).get("id")));
        assertThat(hasColdSoak)
                .as("Catalog must contain 'cold_soak'")
                .isTrue();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. Start a no-prereq, cheap node → RESEARCHING + wallet debited
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("start_noPrerequNode_createsResearching_debitsWallet")
    @SuppressWarnings("unchecked")
    void start_noPrerequNode_createsResearching_debitsWallet() {
        String token  = registerAndGetToken(rest, base());
        Number charId = createCharacter(rest, base(), token, uniqueName());
        long cid = charId.longValue();

        // improved_pruning: 30 GEL, no prereq — safe within 100 GEL starting wallet
        double walletBefore = getWallet(token, cid);

        Map<String, Object> body = Map.of("characterId", cid);
        ResponseEntity<Map> resp = rest.postForEntity(
                base() + "/api/research/improved_pruning/start",
                withToken(body, token),
                Map.class);

        assertThat(resp.getStatusCode())
                .as("POST /api/research/improved_pruning/start must return 200")
                .isEqualTo(HttpStatus.OK);

        Map<String, Object> pr = (Map<String, Object>) resp.getBody();
        assertThat(pr).isNotNull();
        assertThat(pr.get("nodeId")).isEqualTo("improved_pruning");
        assertThat(pr.get("researchStatus")).isEqualTo("RESEARCHING");
        assertThat(((Number) pr.get("characterId")).longValue()).isEqualTo(cid);
        assertThat(pr.get("readyDay")).isNotNull();

        // Wallet must have decreased by costGel (30.0)
        double walletAfter = getWallet(token, cid);
        assertThat(walletAfter)
                .as("Wallet must decrease by 30.0 GEL after starting improved_pruning")
                .isEqualTo(walletBefore - 30.0);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. Prereq not COMPLETE → 400 PREREQ_NOT_MET
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("start_prereqNotComplete_400_prereqNotMet")
    @SuppressWarnings("unchecked")
    void start_prereqNotComplete_400_prereqNotMet() {
        String token  = registerAndGetToken(rest, base());
        Number charId = createCharacter(rest, base(), token, uniqueName());
        long cid = charId.longValue();

        // cold_soak requires temp_control to be COMPLETE — we haven't started temp_control
        Map<String, Object> body = Map.of("characterId", cid);
        ResponseEntity<String> resp = rest.postForEntity(
                base() + "/api/research/cold_soak/start",
                withToken(body, token),
                String.class);

        assertThat(resp.getStatusCode().value())
                .as("Starting cold_soak without temp_control must return 400")
                .isEqualTo(400);

        assertThat(resp.getBody())
                .as("Response body must contain PREREQ_NOT_MET code")
                .contains("PREREQ_NOT_MET");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. After advancing clock past readyDay, GET shows COMPLETE
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("afterClockAdvance_pastReadyDay_showsComplete")
    @SuppressWarnings("unchecked")
    void afterClockAdvance_pastReadyDay_showsComplete() {
        String token  = registerAndGetToken(rest, base());
        Number charId = createCharacter(rest, base(), token, uniqueName());
        long cid = charId.longValue();

        // Start improved_pruning (3 sim-days duration)
        Map<String, Object> startBody = Map.of("characterId", cid);
        ResponseEntity<Map> startResp = rest.postForEntity(
                base() + "/api/research/improved_pruning/start",
                withToken(startBody, token),
                Map.class);
        assertThat(startResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Confirm it is RESEARCHING
        List<Map<String, Object>> before = getResearchList(token, cid);
        assertThat(before).hasSize(1);
        assertThat(before.get(0).get("researchStatus")).isEqualTo("RESEARCHING");

        // Advance the world clock by 10 days (well past readyDay of 3)
        advanceClock(10);

        // GET should now show COMPLETE (lazy completion on read)
        List<Map<String, Object>> after = getResearchList(token, cid);
        assertThat(after).hasSize(1);
        assertThat(after.get(0).get("researchStatus"))
                .as("After clock advance past readyDay, status must be COMPLETE")
                .isEqualTo("COMPLETE");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 5. Once prereq is COMPLETE, dependent node can start
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("prereqComplete_thenDependentCanStart")
    @SuppressWarnings("unchecked")
    void prereqComplete_thenDependentCanStart() {
        String token  = registerAndGetToken(rest, base());
        Number charId = createCharacter(rest, base(), token, uniqueName());
        long cid = charId.longValue();

        // Start temp_control (45 GEL, 4 days, no prereq)
        Map<String, Object> body = Map.of("characterId", cid);
        ResponseEntity<Map> startResp = rest.postForEntity(
                base() + "/api/research/temp_control/start",
                withToken(body, token),
                Map.class);
        assertThat(startResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Advance clock past temp_control readyDay (4 days)
        advanceClock(5);

        // Trigger lazy completion by reading the list
        List<Map<String, Object>> list = getResearchList(token, cid);
        boolean tempControlComplete = list.stream()
                .anyMatch(r -> "temp_control".equals(r.get("nodeId"))
                            && "COMPLETE".equals(r.get("researchStatus")));
        assertThat(tempControlComplete)
                .as("temp_control must be COMPLETE after clock advance")
                .isTrue();

        // Now cold_soak (prereq temp_control) should be startable
        ResponseEntity<Map> coldSoakResp = rest.postForEntity(
                base() + "/api/research/cold_soak/start",
                withToken(body, token),
                Map.class);
        assertThat(coldSoakResp.getStatusCode())
                .as("cold_soak must start successfully once temp_control is COMPLETE")
                .isEqualTo(HttpStatus.OK);

        Map<String, Object> coldSoakPr = (Map<String, Object>) coldSoakResp.getBody();
        assertThat(coldSoakPr.get("researchStatus")).isEqualTo("RESEARCHING");
        assertThat(coldSoakPr.get("nodeId")).isEqualTo("cold_soak");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 6. Double-start → 400
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("doubleStart_400")
    @SuppressWarnings("unchecked")
    void doubleStart_400() {
        String token  = registerAndGetToken(rest, base());
        Number charId = createCharacter(rest, base(), token, uniqueName());
        long cid = charId.longValue();

        Map<String, Object> body = Map.of("characterId", cid);

        // First start — must succeed
        ResponseEntity<Map> first = rest.postForEntity(
                base() + "/api/research/logistics_network/start",
                withToken(body, token),
                Map.class);
        assertThat(first.getStatusCode())
                .as("First start must return 200")
                .isEqualTo(HttpStatus.OK);

        // Second start — must fail 400
        ResponseEntity<String> second = rest.postForEntity(
                base() + "/api/research/logistics_network/start",
                withToken(body, token),
                String.class);
        assertThat(second.getStatusCode().value())
                .as("Double-start must return 400")
                .isEqualTo(400);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 7. Insufficient funds → 400
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("insufficientFunds_400")
    @SuppressWarnings("unchecked")
    void insufficientFunds_400() {
        String token  = registerAndGetToken(rest, base());
        Number charId = createCharacter(rest, base(), token, uniqueName());
        long cid = charId.longValue();

        // Drain most of the wallet: start rootstock_program (60 GEL)
        // and logistics_network (40 GEL) — total 100 GEL = full starting wallet
        Map<String, Object> body = Map.of("characterId", cid);
        rest.postForEntity(
                base() + "/api/research/rootstock_program/start",
                withToken(body, token),
                Map.class);
        rest.postForEntity(
                base() + "/api/research/logistics_network/start",
                withToken(body, token),
                Map.class);

        // Now try improved_pruning (30 GEL) — wallet should be 0 or insufficient
        ResponseEntity<String> resp = rest.postForEntity(
                base() + "/api/research/improved_pruning/start",
                withToken(body, token),
                String.class);
        assertThat(resp.getStatusCode().value())
                .as("Starting research with insufficient wallet must be rejected (400 or 402)")
                .isIn(400, 402);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 8. Ownership enforced: other account → 404
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("ownership_otherAccount_404")
    @SuppressWarnings("unchecked")
    void ownership_otherAccount_404() {
        // Owner creates character and starts research
        String ownerToken  = registerAndGetToken(rest, base());
        Number charId      = createCharacter(rest, base(), ownerToken, uniqueName());
        long cid = charId.longValue();

        // Another account tries to GET the character's research
        String otherToken = registerAndGetToken(rest, base());
        ResponseEntity<String> getResp = rest.exchange(
                base() + "/api/research/" + cid,
                HttpMethod.GET,
                getWithToken(otherToken),
                String.class);
        assertThat(getResp.getStatusCode().value())
                .as("Non-owner account must receive 404 for GET research")
                .isIn(404, 403);

        // Another account tries to start research for the character
        Map<String, Object> body = Map.of("characterId", cid);
        ResponseEntity<String> startResp = rest.postForEntity(
                base() + "/api/research/improved_pruning/start",
                withToken(body, otherToken),
                String.class);
        assertThat(startResp.getStatusCode().value())
                .as("Non-owner account must receive 404 on start")
                .isIn(404, 403);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * GET /api/research/{characterId} → list of research rows.
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getResearchList(String token, long characterId) {
        ResponseEntity<List> resp = rest.exchange(
                base() + "/api/research/" + characterId,
                HttpMethod.GET,
                getWithToken(token),
                List.class);
        assertThat(resp.getStatusCode())
                .as("GET /api/research/{characterId} must return 200")
                .isEqualTo(HttpStatus.OK);
        return (List<Map<String, Object>>) resp.getBody();
    }

    /**
     * POST /api/world/advance {days} — advances the world clock for deterministic
     * time-based test scenarios. The test profile sets
     * {@code world.real-seconds-per-sim-day=86400000} so the clock is effectively
     * frozen and only advances via this endpoint.
     */
    @SuppressWarnings("unchecked")
    private void advanceClock(int days) {
        Map<String, Object> body = Map.of("days", days);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Map> resp = rest.postForEntity(
                base() + "/api/world/advance",
                new org.springframework.http.HttpEntity<>(body, headers),
                Map.class);
        assertThat(resp.getStatusCode())
                .as("POST /api/world/advance must return 200")
                .isEqualTo(HttpStatus.OK);
    }

    /**
     * Fetch the character's wallet balance via GET /api/characters/{id}.
     */
    @SuppressWarnings("unchecked")
    private double getWallet(String token, long characterId) {
        ResponseEntity<Map> resp = rest.exchange(
                base() + "/api/characters/" + characterId,
                HttpMethod.GET,
                getWithToken(token),
                Map.class);
        assertThat(resp.getStatusCode())
                .as("GET /api/characters/{id} must return 200")
                .isEqualTo(HttpStatus.OK);
        return ((Number) resp.getBody().get("walletGel")).doubleValue();
    }
}
