package com.game.progression;

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
 * Integration tests for the PROGRESSION lane.
 *
 * <p>Endpoints under test:
 * <ul>
 *   <li>GET  /api/progression/{characterId}       — returns or auto-creates the profile</li>
 *   <li>POST /api/progression/{characterId}/award — awards XP, returns updated profile</li>
 * </ul>
 *
 * <p>Scenarios:
 * <ol>
 *   <li>GET auto-creates a profile at level=1, xp=0, reputation=0.</li>
 *   <li>POST /award increases xp and returns updated profile.</li>
 *   <li>XP award bumps xpLevel at the 100-XP threshold (xp=100 → level 2).</li>
 *   <li>XP award bumps xpLevel at the 400-XP threshold (xp=400 → level 3).</li>
 *   <li>POST /award with amount=0 → 400.</li>
 *   <li>POST /award with amount=-5 → 400.</li>
 *   <li>GET with another account's token → 404/403 (ownership enforced).</li>
 *   <li>POST /award with another account's token → 404/403.</li>
 *   <li>GET without a token → 401.</li>
 * </ol>
 *
 * <p>Uses {@link AccountTestHelper} static helpers and {@code Map<String,Object>}
 * JSON bodies so {@code containsKey(String)} compiles cleanly with AssertJ.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@SuppressWarnings("unchecked")
class ProgressionControllerTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    private String base() {
        return "http://localhost:" + port;
    }

    private static String uniqueName() {
        return "prog_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** GET /api/progression/{characterId} with a bearer token. */
    private ResponseEntity<Map> getProfile(String token, long characterId) {
        return rest.exchange(
                base() + "/api/progression/" + characterId,
                HttpMethod.GET,
                getWithToken(token),
                Map.class);
    }

    /** POST /api/progression/{characterId}/award with a bearer token. */
    private ResponseEntity<Map> awardXp(String token, long characterId,
                                         long amount, String reason) {
        Map<String, Object> body = Map.of("amount", amount, "reason", reason);
        return rest.postForEntity(
                base() + "/api/progression/" + characterId + "/award",
                withToken(body, token),
                Map.class);
    }

    // ── Test 1: GET auto-creates profile at level 1, xp 0 ────────────────────

    @Test
    @DisplayName("getProfile_autoCreates_level1_xp0")
    void getProfile_autoCreates_level1_xp0() {
        String token = registerAndGetToken(rest, base());
        Number charId = createCharacter(rest, base(), token, uniqueName());
        long cid = charId.longValue();

        ResponseEntity<Map> resp = getProfile(token, cid);

        assertThat(resp.getStatusCode())
                .as("GET /api/progression/{id} must return 200")
                .isEqualTo(HttpStatus.OK);

        Map<String, Object> body = resp.getBody();
        assertThat(body).isNotNull();
        assertThat(body).containsKey("id");
        assertThat(body).containsKey("characterId");
        assertThat(body).containsKey("xp");
        assertThat(body).containsKey("xpLevel");
        assertThat(body).containsKey("reputation");
        assertThat(body).containsKey("updatedAt");

        assertThat(((Number) body.get("xp")).longValue())
                .as("New profile must start with xp=0")
                .isEqualTo(0L);

        assertThat(((Number) body.get("xpLevel")).intValue())
                .as("New profile must start at level 1")
                .isEqualTo(1);

        assertThat(((Number) body.get("reputation")).intValue())
                .as("New profile must start with reputation=0")
                .isEqualTo(0);
    }

    // ── Test 2: GET is idempotent (second call returns same profile) ──────────

    @Test
    @DisplayName("getProfile_idempotent_samePk")
    void getProfile_idempotent_samePk() {
        String token = registerAndGetToken(rest, base());
        Number charId = createCharacter(rest, base(), token, uniqueName());
        long cid = charId.longValue();

        Map<String, Object> first  = getProfile(token, cid).getBody();
        Map<String, Object> second = getProfile(token, cid).getBody();

        assertThat(first).isNotNull();
        assertThat(second).isNotNull();
        assertThat(((Number) first.get("id")).longValue())
                .as("Both GET calls must return the same profile id")
                .isEqualTo(((Number) second.get("id")).longValue());
    }

    // ── Test 3: award increases xp ────────────────────────────────────────────

    @Test
    @DisplayName("award_increasesXp")
    void award_increasesXp() {
        String token = registerAndGetToken(rest, base());
        Number charId = createCharacter(rest, base(), token, uniqueName());
        long cid = charId.longValue();

        ResponseEntity<Map> resp = awardXp(token, cid, 50L, "test award");

        assertThat(resp.getStatusCode())
                .as("POST /award must return 200")
                .isEqualTo(HttpStatus.OK);

        Map<String, Object> body = resp.getBody();
        assertThat(body).isNotNull();
        assertThat(body).containsKey("xp");
        assertThat(body).containsKey("xpLevel");

        assertThat(((Number) body.get("xp")).longValue())
                .as("xp must be 50 after awarding 50")
                .isEqualTo(50L);

        // 50 xp → level 1 (floor(sqrt(0.5)) + 1 = 0+1 = 1)
        assertThat(((Number) body.get("xpLevel")).intValue())
                .as("xp=50 is still level 1")
                .isEqualTo(1);
    }

    // ── Test 4: award bumps level at 100-xp threshold ─────────────────────────

    @Test
    @DisplayName("award_bumpLevel_at100Xp")
    void award_bumpLevel_at100Xp() {
        String token = registerAndGetToken(rest, base());
        Number charId = createCharacter(rest, base(), token, uniqueName());
        long cid = charId.longValue();

        // Award exactly 100 xp in one shot
        ResponseEntity<Map> resp = awardXp(token, cid, 100L, "level-up test");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = resp.getBody();
        assertThat(body).isNotNull();

        assertThat(((Number) body.get("xp")).longValue())
                .as("xp must be 100")
                .isEqualTo(100L);

        // floor(sqrt(100 / 100.0)) + 1 = floor(1.0) + 1 = 2
        assertThat(((Number) body.get("xpLevel")).intValue())
                .as("xp=100 must give level 2")
                .isEqualTo(2);
    }

    // ── Test 5: award bumps level at 400-xp threshold ─────────────────────────

    @Test
    @DisplayName("award_bumpLevel_at400Xp")
    void award_bumpLevel_at400Xp() {
        String token = registerAndGetToken(rest, base());
        Number charId = createCharacter(rest, base(), token, uniqueName());
        long cid = charId.longValue();

        // Two awards: 100 + 300 = 400 total
        awardXp(token, cid, 100L, "first 100");
        ResponseEntity<Map> resp = awardXp(token, cid, 300L, "to 400");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = resp.getBody();
        assertThat(body).isNotNull();

        assertThat(((Number) body.get("xp")).longValue())
                .as("xp must be 400")
                .isEqualTo(400L);

        // floor(sqrt(400 / 100.0)) + 1 = floor(2.0) + 1 = 3
        assertThat(((Number) body.get("xpLevel")).intValue())
                .as("xp=400 must give level 3")
                .isEqualTo(3);
    }

    // ── Test 6: award with amount=0 → 400 ────────────────────────────────────

    @Test
    @DisplayName("award_amountZero_returns400")
    void award_amountZero_returns400() {
        String token = registerAndGetToken(rest, base());
        Number charId = createCharacter(rest, base(), token, uniqueName());
        long cid = charId.longValue();

        Map<String, Object> body = Map.of("amount", 0, "reason", "invalid");
        ResponseEntity<Map> resp = rest.postForEntity(
                base() + "/api/progression/" + cid + "/award",
                withToken(body, token),
                Map.class);

        assertThat(resp.getStatusCode())
                .as("amount=0 must return 400")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ── Test 7: award with negative amount → 400 ─────────────────────────────

    @Test
    @DisplayName("award_negativeAmount_returns400")
    void award_negativeAmount_returns400() {
        String token = registerAndGetToken(rest, base());
        Number charId = createCharacter(rest, base(), token, uniqueName());
        long cid = charId.longValue();

        Map<String, Object> body = Map.of("amount", -5, "reason", "invalid");
        ResponseEntity<Map> resp = rest.postForEntity(
                base() + "/api/progression/" + cid + "/award",
                withToken(body, token),
                Map.class);

        assertThat(resp.getStatusCode())
                .as("negative amount must return 400")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ── Test 8: ownership enforced on GET ────────────────────────────────────

    @Test
    @DisplayName("getProfile_otherAccount_returns404or403")
    void getProfile_otherAccount_returns404or403() {
        // Account A creates a character
        String tokenA = registerAndGetToken(rest, base());
        Number charIdA = createCharacter(rest, base(), tokenA, uniqueName());

        // Account B tries to read Account A's profile
        String tokenB = registerAndGetToken(rest, base());

        ResponseEntity<Map> resp = rest.exchange(
                base() + "/api/progression/" + charIdA.longValue(),
                HttpMethod.GET,
                getWithToken(tokenB),
                Map.class);

        assertThat(resp.getStatusCode().value())
                .as("Another account must not access this character's profile")
                .isIn(403, 404);
    }

    // ── Test 9: ownership enforced on award ──────────────────────────────────

    @Test
    @DisplayName("award_otherAccount_returns404or403")
    void award_otherAccount_returns404or403() {
        // Account A creates a character
        String tokenA = registerAndGetToken(rest, base());
        Number charIdA = createCharacter(rest, base(), tokenA, uniqueName());

        // Account B tries to award XP to Account A's character
        String tokenB = registerAndGetToken(rest, base());

        Map<String, Object> body = Map.of("amount", 100, "reason", "theft");
        ResponseEntity<Map> resp = rest.postForEntity(
                base() + "/api/progression/" + charIdA.longValue() + "/award",
                withToken(body, tokenB),
                Map.class);

        assertThat(resp.getStatusCode().value())
                .as("Another account must not award XP to this character")
                .isIn(403, 404);
    }

    // ── Test 10: GET without token → 401 ─────────────────────────────────────

    @Test
    @DisplayName("getProfile_noToken_returns401")
    void getProfile_noToken_returns401() {
        String token = registerAndGetToken(rest, base());
        Number charId = createCharacter(rest, base(), token, uniqueName());
        long cid = charId.longValue();

        // Request with no authorization header
        ResponseEntity<Map> resp = rest.getForEntity(
                base() + "/api/progression/" + cid,
                Map.class);

        assertThat(resp.getStatusCode())
                .as("GET without token must return 401")
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ── Test 11: cumulative xp persists across calls ─────────────────────────

    @Test
    @DisplayName("award_cumulative_xpPersistsAcrossCalls")
    void award_cumulative_xpPersistsAcrossCalls() {
        String token = registerAndGetToken(rest, base());
        Number charId = createCharacter(rest, base(), token, uniqueName());
        long cid = charId.longValue();

        awardXp(token, cid, 50L, "first");
        awardXp(token, cid, 50L, "second");

        // GET to confirm persisted total
        Map<String, Object> profile = getProfile(token, cid).getBody();
        assertThat(profile).isNotNull();
        assertThat(((Number) profile.get("xp")).longValue())
                .as("Cumulative xp after two 50-xp awards must be 100")
                .isEqualTo(100L);

        // xp=100 → level 2
        assertThat(((Number) profile.get("xpLevel")).intValue())
                .as("Level must be 2 after 100 cumulative xp")
                .isEqualTo(2);
    }
}
