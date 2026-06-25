package com.game.achievement;

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
 * Integration tests for /api/achievement/** (LANE ACHIEVEMENT).
 *
 * <p>Scenarios covered:
 * <ol>
 *   <li>GET /api/achievement/catalog → returns all 6 achievement definitions with required fields.</li>
 *   <li>POST /api/achievement/{id}/unlock → records PlayerAchievement + grants rewardGel
 *       (verified via GET /api/characters/{id} wallet before/after).</li>
 *   <li>Double unlock → 400 ALREADY_UNLOCKED + wallet unchanged on 2nd call.</li>
 *   <li>GET /api/achievement/{characterId} → reflects unlocked achievements.</li>
 *   <li>Unknown achievementId → 404.</li>
 *   <li>Ownership enforced: other account → 404/403.</li>
 * </ol>
 *
 * <p>Uses {@link AccountTestHelper} static helpers and {@code Map<String,Object>} JSON
 * bodies per the CONTEST-ACHIEVEMENT-CHAT-SPEC compile-safety rules. For 4xx endpoints
 * the body is read with {@code String.class}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AchievementControllerTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    private String base() { return "http://localhost:" + port; }

    private static String uniqueName() {
        return "ach_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 1. GET /api/achievement/catalog → 6 achievements with required fields
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("catalog_returnsSixAchievements_withRequiredFields")
    @SuppressWarnings("unchecked")
    void catalog_returnsSixAchievements_withRequiredFields() {
        String token = registerAndGetToken(rest, base());

        ResponseEntity<List> resp = rest.exchange(
                base() + "/api/achievement/catalog",
                HttpMethod.GET,
                getWithToken(token),
                List.class);

        assertThat(resp.getStatusCode())
                .as("GET /api/achievement/catalog must return 200")
                .isEqualTo(HttpStatus.OK);

        List<?> catalog = resp.getBody();
        assertThat(catalog)
                .as("Catalog must contain exactly 6 achievements")
                .isNotNull()
                .hasSize(6);

        // Every entry must have the required fields
        for (Object entry : catalog) {
            Map<String, Object> a = (Map<String, Object>) entry;
            assertThat(a)
                    .as("Each achievement definition must contain required fields")
                    .containsKeys("id", "title", "description", "rewardGel");
        }

        // Spot-check: all 6 known ids must be present
        for (String expectedId : new String[]{
                "first_estate", "first_qvevri", "master_vintner",
                "wealthy_merchant", "guild_founder", "globetrotter"}) {
            boolean found = catalog.stream()
                    .anyMatch(o -> expectedId.equals(((Map<?, ?>) o).get("id")));
            assertThat(found)
                    .as("Catalog must contain achievement id '" + expectedId + "'")
                    .isTrue();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. POST unlock → records PlayerAchievement + grants rewardGel
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("unlock_recordsAchievement_andGrantsRewardGel")
    @SuppressWarnings("unchecked")
    void unlock_recordsAchievement_andGrantsRewardGel() {
        String token  = registerAndGetToken(rest, base());
        Number charId = createCharacter(rest, base(), token, uniqueName());
        long cid      = charId.longValue();

        // "wealthy_merchant" has rewardGel=200.0, no goods reward
        String achievementId = "wealthy_merchant";
        double expectedReward = 200.0;

        double walletBefore = getWallet(token, cid);

        // POST /api/achievement/wealthy_merchant/unlock
        Map<String, Object> body = Map.of("characterId", cid);
        ResponseEntity<Map> resp = rest.postForEntity(
                base() + "/api/achievement/" + achievementId + "/unlock",
                withToken(body, token),
                Map.class);

        assertThat(resp.getStatusCode())
                .as("POST /api/achievement/{id}/unlock must return 200")
                .isEqualTo(HttpStatus.OK);

        Map<String, Object> pa = (Map<String, Object>) resp.getBody();
        assertThat(pa).isNotNull();
        assertThat(pa.get("achievementId")).isEqualTo(achievementId);
        assertThat(((Number) pa.get("characterId")).longValue()).isEqualTo(cid);
        assertThat(pa).containsKey("id");
        assertThat(pa).containsKey("unlockedDay");
        assertThat(pa).containsKey("createdAt");

        // Wallet must have increased by rewardGel
        double walletAfter = getWallet(token, cid);
        assertThat(walletAfter)
                .as("Wallet must increase by rewardGel=%.1f after unlock", expectedReward)
                .isEqualTo(walletBefore + expectedReward);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. Double unlock → 400 ALREADY_UNLOCKED + wallet unchanged
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("doubleUnlock_returnsAlreadyUnlocked400_walletUnchanged")
    @SuppressWarnings("unchecked")
    void doubleUnlock_returnsAlreadyUnlocked400_walletUnchanged() {
        String token  = registerAndGetToken(rest, base());
        Number charId = createCharacter(rest, base(), token, uniqueName());
        long cid      = charId.longValue();

        // "globetrotter" has rewardGel=125.0, no goods reward
        String achievementId = "globetrotter";

        Map<String, Object> body = Map.of("characterId", cid);

        // First unlock — must succeed
        ResponseEntity<Map> first = rest.postForEntity(
                base() + "/api/achievement/" + achievementId + "/unlock",
                withToken(body, token),
                Map.class);
        assertThat(first.getStatusCode())
                .as("First unlock must return 200")
                .isEqualTo(HttpStatus.OK);

        // Record wallet after first unlock
        double walletAfterFirst = getWallet(token, cid);

        // Second unlock — must fail with 400
        ResponseEntity<String> second = rest.postForEntity(
                base() + "/api/achievement/" + achievementId + "/unlock",
                withToken(body, token),
                String.class);
        assertThat(second.getStatusCode())
                .as("Double unlock must return 400")
                .isEqualTo(HttpStatus.BAD_REQUEST);

        // Error body should reference ALREADY_UNLOCKED
        assertThat(second.getBody())
                .as("Error response must mention ALREADY_UNLOCKED")
                .contains("ALREADY_UNLOCKED");

        // Wallet must NOT have changed
        double walletAfterSecond = getWallet(token, cid);
        assertThat(walletAfterSecond)
                .as("Wallet must not change when second unlock is rejected")
                .isEqualTo(walletAfterFirst);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. GET /api/achievement/{characterId} reflects unlocked achievements
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getCharacterAchievements_reflectsUnlocked")
    @SuppressWarnings("unchecked")
    void getCharacterAchievements_reflectsUnlocked() {
        String token  = registerAndGetToken(rest, base());
        Number charId = createCharacter(rest, base(), token, uniqueName());
        long cid      = charId.longValue();

        // Initially no achievements
        ResponseEntity<List> emptyResp = rest.exchange(
                base() + "/api/achievement/" + cid,
                HttpMethod.GET,
                getWithToken(token),
                List.class);
        assertThat(emptyResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(emptyResp.getBody())
                .as("Character should start with no achievements")
                .isEmpty();

        // Unlock two achievements
        for (String achievementId : new String[]{"first_estate", "first_qvevri"}) {
            Map<String, Object> body = Map.of("characterId", cid);
            ResponseEntity<Map> unlockResp = rest.postForEntity(
                    base() + "/api/achievement/" + achievementId + "/unlock",
                    withToken(body, token),
                    Map.class);
            assertThat(unlockResp.getStatusCode())
                    .as("Unlock of " + achievementId + " must return 200")
                    .isEqualTo(HttpStatus.OK);
        }

        // GET list must now contain both achievements
        ResponseEntity<List> listResp = rest.exchange(
                base() + "/api/achievement/" + cid,
                HttpMethod.GET,
                getWithToken(token),
                List.class);
        assertThat(listResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        List<?> achievements = listResp.getBody();
        assertThat(achievements)
                .as("Character should now have 2 achievements")
                .hasSize(2);

        // Both returned rows must have the expected achievementId values
        boolean hasFirst = achievements.stream()
                .anyMatch(o -> "first_estate".equals(((Map<?, ?>) o).get("achievementId")));
        boolean hasQvevri = achievements.stream()
                .anyMatch(o -> "first_qvevri".equals(((Map<?, ?>) o).get("achievementId")));
        assertThat(hasFirst).as("List must contain 'first_estate'").isTrue();
        assertThat(hasQvevri).as("List must contain 'first_qvevri'").isTrue();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 5. Unknown achievementId → 404
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("unlock_unknownAchievementId_returns404")
    @SuppressWarnings("unchecked")
    void unlock_unknownAchievementId_returns404() {
        String token  = registerAndGetToken(rest, base());
        Number charId = createCharacter(rest, base(), token, uniqueName());
        long cid      = charId.longValue();

        Map<String, Object> body = Map.of("characterId", cid);
        ResponseEntity<String> resp = rest.postForEntity(
                base() + "/api/achievement/no_such_achievement/unlock",
                withToken(body, token),
                String.class);

        assertThat(resp.getStatusCode())
                .as("Unknown achievementId must return 404")
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 6. Ownership enforced: other account → 404/403
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("ownership_otherAccount_returns404or403")
    @SuppressWarnings("unchecked")
    void ownership_otherAccount_returns404or403() {
        // Owner creates character
        String ownerToken = registerAndGetToken(rest, base());
        Number charId     = createCharacter(rest, base(), ownerToken, uniqueName());
        long cid          = charId.longValue();

        // Another account tries to list achievements for the owner's character
        String otherToken = registerAndGetToken(rest, base());
        ResponseEntity<String> getResp = rest.exchange(
                base() + "/api/achievement/" + cid,
                HttpMethod.GET,
                getWithToken(otherToken),
                String.class);
        assertThat(getResp.getStatusCode().value())
                .as("Non-owner account must receive 404 on GET achievements")
                .isIn(404, 403);

        // Another account tries to unlock for the owner's character
        Map<String, Object> body = Map.of("characterId", cid);
        ResponseEntity<String> unlockResp = rest.postForEntity(
                base() + "/api/achievement/master_vintner/unlock",
                withToken(body, otherToken),
                String.class);
        assertThat(unlockResp.getStatusCode().value())
                .as("Non-owner account must receive 404/403 on unlock")
                .isIn(404, 403);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

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
