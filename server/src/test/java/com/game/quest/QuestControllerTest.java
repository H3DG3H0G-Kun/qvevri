package com.game.quest;

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
 * Integration tests for /api/quests/** (LANE QUEST).
 *
 * <p>Scenarios covered:
 * <ol>
 *   <li>GET /api/quests/catalog → returns all 29 quest definitions.</li>
 *   <li>POST /api/quests/{characterId}/accept → creates an ACTIVE PlayerQuest.</li>
 *   <li>Double-accept → 400.</li>
 *   <li>POST /api/quests/{characterId}/complete → flips to COMPLETED, wallet increases
 *       by rewardGel.</li>
 *   <li>Re-complete does NOT double-pay (wallet unchanged on 2nd call → 400).</li>
 *   <li>Ownership enforced: other account → 404.</li>
 * </ol>
 *
 * <p>Uses {@link AccountTestHelper} static helpers and Map&lt;String,Object&gt; / Map&lt;String,String&gt;
 * JSON bodies exactly like the existing TradeControllerTest pattern.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class QuestControllerTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    private String base() {
        return "http://localhost:" + port;
    }

    private static String uniqueName() {
        return "qst_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 1. GET /api/quests/catalog → 29 quests (full progression arc)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("catalog_returnsAllQuests")
    @SuppressWarnings("unchecked")
    void catalog_returnsAllQuests() {
        String token = registerAndGetToken(rest, base());

        ResponseEntity<List> resp = rest.exchange(
                base() + "/api/quests/catalog",
                HttpMethod.GET,
                getWithToken(token),
                List.class);

        assertThat(resp.getStatusCode())
                .as("GET /api/quests/catalog must return 200")
                .isEqualTo(HttpStatus.OK);

        List<?> catalog = resp.getBody();
        assertThat(catalog)
                .as("Catalog must contain exactly 29 quests (full progression arc)")
                .isNotNull()
                .hasSize(29);

        // Spot-check: every entry has required fields
        for (Object entry : catalog) {
            Map<String, Object> q = (Map<String, Object>) entry;
            assertThat(q).containsKeys("id", "title", "description",
                    "giverNpc", "objectiveType", "objectiveCount",
                    "rewardGel");
        }

        // Spot-check first quest id
        boolean hasFirstVine = catalog.stream()
                .anyMatch(o -> "first_vine".equals(((Map<?, ?>) o).get("id")));
        assertThat(hasFirstVine)
                .as("Catalog must contain 'first_vine'")
                .isTrue();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. Accept → ACTIVE PlayerQuest
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("accept_createsActivePlayerQuest")
    @SuppressWarnings("unchecked")
    void accept_createsActivePlayerQuest() {
        String token  = registerAndGetToken(rest, base());
        Number charId = createCharacter(rest, base(), token, uniqueName());
        long cid = charId.longValue();

        Map<String, String> body = Map.of("questId", "first_vine");
        ResponseEntity<Map> resp = rest.postForEntity(
                base() + "/api/quests/" + cid + "/accept",
                withToken(body, token),
                Map.class);

        assertThat(resp.getStatusCode())
                .as("POST /api/quests/{id}/accept must return 200")
                .isEqualTo(HttpStatus.OK);

        Map<?, ?> pq = resp.getBody();
        assertThat(pq).isNotNull();
        assertThat(pq.get("questId")).isEqualTo("first_vine");
        assertThat(pq.get("questStatus")).isEqualTo("ACTIVE");
        assertThat(((Number) pq.get("characterId")).longValue()).isEqualTo(cid);
        assertThat(pq.get("completedAt")).isNull();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. Double-accept → 400
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("accept_twice_400")
    @SuppressWarnings("unchecked")
    void accept_twice_400() {
        String token  = registerAndGetToken(rest, base());
        Number charId = createCharacter(rest, base(), token, uniqueName());
        long cid = charId.longValue();

        Map<String, String> body = Map.of("questId", "first_harvest");

        // First accept — must succeed
        ResponseEntity<Map> first = rest.postForEntity(
                base() + "/api/quests/" + cid + "/accept",
                withToken(body, token),
                Map.class);
        assertThat(first.getStatusCode())
                .as("First accept must return 200")
                .isEqualTo(HttpStatus.OK);

        // Second accept — must fail
        ResponseEntity<Map> second = rest.postForEntity(
                base() + "/api/quests/" + cid + "/accept",
                withToken(body, token),
                Map.class);
        assertThat(second.getStatusCode())
                .as("Double-accept must return 400")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. Complete → COMPLETED + wallet increases by rewardGel
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("complete_flipsToCompleted_andIncreasesWallet")
    @SuppressWarnings("unchecked")
    void complete_flipsToCompleted_andIncreasesWallet() {
        String token  = registerAndGetToken(rest, base());
        Number charId = createCharacter(rest, base(), token, uniqueName());
        long cid = charId.longValue();

        // "sell_first_bottles" has rewardGel=50.0
        String questId  = "sell_first_bottles";
        double expected = 50.0;

        // Accept first
        acceptQuest(token, cid, questId);

        // Record wallet BEFORE complete
        double walletBefore = getWallet(token, cid);

        // Complete
        Map<String, String> body = Map.of("questId", questId);
        ResponseEntity<Map> resp = rest.postForEntity(
                base() + "/api/quests/" + cid + "/complete",
                withToken(body, token),
                Map.class);

        assertThat(resp.getStatusCode())
                .as("POST /api/quests/{id}/complete must return 200")
                .isEqualTo(HttpStatus.OK);

        Map<?, ?> pq = resp.getBody();
        assertThat(pq).isNotNull();
        assertThat(pq.get("questStatus"))
                .as("Quest status must be COMPLETED")
                .isEqualTo("COMPLETED");
        assertThat(pq.get("completedAt"))
                .as("completedAt must be populated")
                .isNotNull();

        // Wallet must have increased by rewardGel
        double walletAfter = getWallet(token, cid);
        assertThat(walletAfter)
                .as("Wallet must increase by rewardGel=" + expected + " after completion")
                .isEqualTo(walletBefore + expected);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 5. Re-complete does NOT double-pay (wallet unchanged on 2nd call → 400)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("reComplete_doesNotDoubleReward_returns400")
    @SuppressWarnings("unchecked")
    void reComplete_doesNotDoubleReward_returns400() {
        String token  = registerAndGetToken(rest, base());
        Number charId = createCharacter(rest, base(), token, uniqueName());
        long cid = charId.longValue();

        String questId = "visit_marani"; // rewardGel=30.0

        // Accept + first complete (must succeed)
        acceptQuest(token, cid, questId);
        completeQuestExpect200(token, cid, questId);

        // Record wallet after first complete
        double walletAfterFirst = getWallet(token, cid);

        // Second complete — must return 400
        Map<String, String> body = Map.of("questId", questId);
        ResponseEntity<Map> second = rest.postForEntity(
                base() + "/api/quests/" + cid + "/complete",
                withToken(body, token),
                Map.class);

        assertThat(second.getStatusCode())
                .as("Re-completing an already COMPLETED quest must return 400")
                .isEqualTo(HttpStatus.BAD_REQUEST);

        // Wallet must NOT have changed
        double walletAfterSecond = getWallet(token, cid);
        assertThat(walletAfterSecond)
                .as("Wallet must not change on rejected re-complete")
                .isEqualTo(walletAfterFirst);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 6. Ownership: other account → 404
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("ownership_otherAccount_404")
    @SuppressWarnings("unchecked")
    void ownership_otherAccount_404() {
        // Owner creates character
        String ownerToken  = registerAndGetToken(rest, base());
        Number charId      = createCharacter(rest, base(), ownerToken, uniqueName());
        long cid = charId.longValue();

        // Accept with owner (succeeds)
        acceptQuest(ownerToken, cid, "craft_first_qvevri");

        // Another account tries to GET the character's quests
        String otherToken = registerAndGetToken(rest, base());
        ResponseEntity<Map> resp = rest.exchange(
                base() + "/api/quests/" + cid,
                HttpMethod.GET,
                getWithToken(otherToken),
                Map.class);

        assertThat(resp.getStatusCode().value())
                .as("Non-owner account must receive 404 for character quests")
                .isIn(404, 403);

        // Another account tries to complete
        Map<String, String> body = Map.of("questId", "craft_first_qvevri");
        ResponseEntity<Map> completeResp = rest.postForEntity(
                base() + "/api/quests/" + cid + "/complete",
                withToken(body, otherToken),
                Map.class);

        assertThat(completeResp.getStatusCode().value())
                .as("Non-owner account must receive 404/403 on complete")
                .isIn(404, 403);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    /** Accept a quest and assert 200. */
    @SuppressWarnings("unchecked")
    private void acceptQuest(String token, long characterId, String questId) {
        Map<String, String> body = Map.of("questId", questId);
        ResponseEntity<Map> resp = rest.postForEntity(
                base() + "/api/quests/" + characterId + "/accept",
                withToken(body, token),
                Map.class);
        assertThat(resp.getStatusCode())
                .as("accept must return 200 for questId=" + questId)
                .isEqualTo(HttpStatus.OK);
    }

    /** Complete a quest and assert 200. */
    @SuppressWarnings("unchecked")
    private void completeQuestExpect200(String token, long characterId, String questId) {
        Map<String, String> body = Map.of("questId", questId);
        ResponseEntity<Map> resp = rest.postForEntity(
                base() + "/api/quests/" + characterId + "/complete",
                withToken(body, token),
                Map.class);
        assertThat(resp.getStatusCode())
                .as("complete must return 200 for questId=" + questId)
                .isEqualTo(HttpStatus.OK);
    }

    /**
     * Fetch the character's wallet balance via GET /api/characters/{id}.
     * Uses the same pattern as TradeControllerTest.getWallet().
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
