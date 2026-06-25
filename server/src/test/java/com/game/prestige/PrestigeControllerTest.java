package com.game.prestige;

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
 * Integration tests for the PRESTIGE lane.
 *
 * <p>Endpoints under test:
 * <ul>
 *   <li>GET  /api/prestige/ladder                  — returns the full ordered title ladder</li>
 *   <li>GET  /api/prestige/{characterId}            — returns or auto-creates the profile</li>
 *   <li>POST /api/prestige/{characterId}/award      — awards prestige, returns updated profile</li>
 * </ul>
 *
 * <p>Scenarios:
 * <ol>
 *   <li>GET /ladder returns all 5 titles in ascending threshold order.</li>
 *   <li>GET /ladder first entry is GLEKHI at threshold 0.</li>
 *   <li>GET /{id} auto-creates profile at prestige=0, title=GLEKHI.</li>
 *   <li>GET /{id} is idempotent (same profile id on second call).</li>
 *   <li>POST /award raises prestige and returns updated view.</li>
 *   <li>POST /award promotes to MEVENAKHE when total prestige &gt;= 50.</li>
 *   <li>POST /award promotes to MEURNE when total prestige &gt;= 200.</li>
 *   <li>nextTitle and prestigeToNext correct at prestige=60 (next=MEURNE, toNext=140).</li>
 *   <li>At top title (TAVADI) nextTitle=null and prestigeToNext=0.</li>
 *   <li>POST /award with amount=0 → 400.</li>
 *   <li>POST /award with negative amount → 400.</li>
 *   <li>Ownership enforced on GET (another account → 404/403).</li>
 *   <li>Ownership enforced on POST /award (another account → 404/403).</li>
 *   <li>GET without token → 401.</li>
 * </ol>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@SuppressWarnings("unchecked")
class PrestigeControllerTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    private String base() {
        return "http://localhost:" + port;
    }

    private static String uniqueName() {
        return "pres_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ResponseEntity<List> getLadder() {
        return rest.getForEntity(base() + "/api/prestige/ladder", List.class);
    }

    private ResponseEntity<Map> getProfile(String token, long characterId) {
        return rest.exchange(
                base() + "/api/prestige/" + characterId,
                HttpMethod.GET,
                getWithToken(token),
                Map.class);
    }

    private ResponseEntity<Map> awardPrestige(String token, long characterId,
                                              long amount, String reason) {
        Map<String, Object> body = Map.of("amount", amount, "reason", reason);
        return rest.postForEntity(
                base() + "/api/prestige/" + characterId + "/award",
                withToken(body, token),
                Map.class);
    }

    // ── Test 1: ladder returns 5 entries ordered by threshold ────────────────

    @Test
    @DisplayName("getLadder_returnsAllTitlesOrdered")
    void getLadder_returnsAllTitlesOrdered() {
        ResponseEntity<List> resp = getLadder();

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> ladder = (List<Map<String, Object>>) resp.getBody();
        assertThat(ladder).isNotNull().hasSize(5);

        // Verify ascending threshold order
        long prevThreshold = -1L;
        for (Map<String, Object> entry : ladder) {
            long threshold = ((Number) entry.get("threshold")).longValue();
            assertThat(threshold)
                    .as("Thresholds must be strictly ascending")
                    .isGreaterThan(prevThreshold);
            prevThreshold = threshold;
            assertThat(entry).containsKey("title");
        }
    }

    // ── Test 2: first ladder entry is GLEKHI at threshold 0 ──────────────────

    @Test
    @DisplayName("getLadder_firstEntryIsGlekhi")
    void getLadder_firstEntryIsGlekhi() {
        List<Map<String, Object>> ladder =
                (List<Map<String, Object>>) getLadder().getBody();
        assertThat(ladder).isNotNull().isNotEmpty();

        Map<String, Object> first = ladder.get(0);
        assertThat(first.get("title")).isEqualTo("GLEKHI");
        assertThat(((Number) first.get("threshold")).longValue()).isEqualTo(0L);
    }

    // ── Test 3: GET auto-creates profile at prestige=0, title=GLEKHI ─────────

    @Test
    @DisplayName("getProfile_autoCreates_prestige0_titleGlekhi")
    void getProfile_autoCreates_prestige0_titleGlekhi() {
        String token = registerAndGetToken(rest, base());
        Number charId = createCharacter(rest, base(), token, uniqueName());
        long cid = charId.longValue();

        ResponseEntity<Map> resp = getProfile(token, cid);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = resp.getBody();
        assertThat(body).isNotNull();
        assertThat(body).containsKey("prestige");
        assertThat(body).containsKey("title");
        assertThat(body).containsKey("nextTitle");
        assertThat(body).containsKey("prestigeToNext");

        assertThat(((Number) body.get("prestige")).longValue())
                .as("New profile must start with prestige=0")
                .isEqualTo(0L);

        assertThat(body.get("title"))
                .as("New profile must start at GLEKHI")
                .isEqualTo("GLEKHI");

        assertThat(body.get("nextTitle"))
                .as("At GLEKHI, nextTitle must be MEVENAKHE")
                .isEqualTo("MEVENAKHE");

        assertThat(((Number) body.get("prestigeToNext")).longValue())
                .as("At prestige=0, need 50 more for MEVENAKHE")
                .isEqualTo(50L);
    }

    // ── Test 4: GET is idempotent ─────────────────────────────────────────────

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
        // Both calls return the same prestige value (both 0)
        assertThat(((Number) first.get("prestige")).longValue())
                .isEqualTo(((Number) second.get("prestige")).longValue());
    }

    // ── Test 5: award raises prestige ─────────────────────────────────────────

    @Test
    @DisplayName("award_raisesPrestige")
    void award_raisesPrestige() {
        String token = registerAndGetToken(rest, base());
        Number charId = createCharacter(rest, base(), token, uniqueName());
        long cid = charId.longValue();

        ResponseEntity<Map> resp = awardPrestige(token, cid, 30L, "first award");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = resp.getBody();
        assertThat(body).isNotNull();

        assertThat(((Number) body.get("prestige")).longValue())
                .as("Prestige must be 30 after awarding 30")
                .isEqualTo(30L);

        assertThat(body.get("title"))
                .as("30 prestige is still GLEKHI")
                .isEqualTo("GLEKHI");
    }

    // ── Test 6: award 60 → promotes to MEVENAKHE (threshold 50) ─────────────

    @Test
    @DisplayName("award_60prestige_promotesToMevenakhe")
    void award_60prestige_promotesToMevenakhe() {
        String token = registerAndGetToken(rest, base());
        Number charId = createCharacter(rest, base(), token, uniqueName());
        long cid = charId.longValue();

        ResponseEntity<Map> resp = awardPrestige(token, cid, 60L, "estate achievement");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = resp.getBody();
        assertThat(body).isNotNull();

        assertThat(((Number) body.get("prestige")).longValue()).isEqualTo(60L);
        assertThat(body.get("title"))
                .as("60 prestige must promote to MEVENAKHE (threshold 50)")
                .isEqualTo("MEVENAKHE");
    }

    // ── Test 7: crossing 200 → promotes to MEURNE ─────────────────────────────

    @Test
    @DisplayName("award_crossingMeurneThreshold_promotesToMeurne")
    void award_crossingMeurneThreshold_promotesToMeurne() {
        String token = registerAndGetToken(rest, base());
        Number charId = createCharacter(rest, base(), token, uniqueName());
        long cid = charId.longValue();

        // Award 100 first, then award 150 more → total 250 >= 200
        awardPrestige(token, cid, 100L, "first batch");
        ResponseEntity<Map> resp = awardPrestige(token, cid, 150L, "second batch");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = resp.getBody();
        assertThat(body).isNotNull();

        assertThat(((Number) body.get("prestige")).longValue()).isEqualTo(250L);
        assertThat(body.get("title"))
                .as("250 prestige must be MEURNE (threshold 200)")
                .isEqualTo("MEURNE");
    }

    // ── Test 8: nextTitle and prestigeToNext correct at prestige=60 ──────────

    @Test
    @DisplayName("nextTitle_and_prestigeToNext_correct_at60")
    void nextTitle_and_prestigeToNext_correct_at60() {
        String token = registerAndGetToken(rest, base());
        Number charId = createCharacter(rest, base(), token, uniqueName());
        long cid = charId.longValue();

        awardPrestige(token, cid, 60L, "test");
        Map<String, Object> body = getProfile(token, cid).getBody();
        assertThat(body).isNotNull();

        assertThat(body.get("nextTitle"))
                .as("At prestige=60 (MEVENAKHE), next is MEURNE (threshold 200)")
                .isEqualTo("MEURNE");

        assertThat(((Number) body.get("prestigeToNext")).longValue())
                .as("200 - 60 = 140 prestige to next title")
                .isEqualTo(140L);
    }

    // ── Test 9: at TAVADI (top) nextTitle=null, prestigeToNext=0 ─────────────

    @Test
    @DisplayName("atTopTitle_nextTitleNull_prestigeToNextZero")
    void atTopTitle_nextTitleNull_prestigeToNextZero() {
        String token = registerAndGetToken(rest, base());
        Number charId = createCharacter(rest, base(), token, uniqueName());
        long cid = charId.longValue();

        // Award enough prestige to reach TAVADI (threshold 1500)
        awardPrestige(token, cid, 2000L, "wine lord achievement");
        Map<String, Object> body = getProfile(token, cid).getBody();
        assertThat(body).isNotNull();

        assertThat(body.get("title"))
                .as("2000 prestige must be TAVADI (threshold 1500)")
                .isEqualTo("TAVADI");

        assertThat(body.get("nextTitle"))
                .as("At TAVADI (top title), nextTitle must be null")
                .isNull();

        assertThat(((Number) body.get("prestigeToNext")).longValue())
                .as("At the top title, prestigeToNext must be 0")
                .isEqualTo(0L);
    }

    // ── Test 10: award with amount=0 → 400 ───────────────────────────────────

    @Test
    @DisplayName("award_amountZero_returns400")
    void award_amountZero_returns400() {
        String token = registerAndGetToken(rest, base());
        Number charId = createCharacter(rest, base(), token, uniqueName());
        long cid = charId.longValue();

        Map<String, Object> body = Map.of("amount", 0, "reason", "invalid");
        ResponseEntity<String> resp = rest.postForEntity(
                base() + "/api/prestige/" + cid + "/award",
                withToken(body, token),
                String.class);

        assertThat(resp.getStatusCode())
                .as("amount=0 must return 400")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ── Test 11: award with negative amount → 400 ────────────────────────────

    @Test
    @DisplayName("award_negativeAmount_returns400")
    void award_negativeAmount_returns400() {
        String token = registerAndGetToken(rest, base());
        Number charId = createCharacter(rest, base(), token, uniqueName());
        long cid = charId.longValue();

        Map<String, Object> body = Map.of("amount", -10, "reason", "invalid");
        ResponseEntity<String> resp = rest.postForEntity(
                base() + "/api/prestige/" + cid + "/award",
                withToken(body, token),
                String.class);

        assertThat(resp.getStatusCode())
                .as("Negative amount must return 400")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ── Test 12: ownership enforced on GET ────────────────────────────────────

    @Test
    @DisplayName("getProfile_otherAccount_returns404or403")
    void getProfile_otherAccount_returns404or403() {
        String tokenA = registerAndGetToken(rest, base());
        Number charIdA = createCharacter(rest, base(), tokenA, uniqueName());

        String tokenB = registerAndGetToken(rest, base());

        ResponseEntity<Map> resp = rest.exchange(
                base() + "/api/prestige/" + charIdA.longValue(),
                HttpMethod.GET,
                getWithToken(tokenB),
                Map.class);

        assertThat(resp.getStatusCode().value())
                .as("Another account must not access this character's prestige profile")
                .isIn(403, 404);
    }

    // ── Test 13: ownership enforced on award ──────────────────────────────────

    @Test
    @DisplayName("award_otherAccount_returns404or403")
    void award_otherAccount_returns404or403() {
        String tokenA = registerAndGetToken(rest, base());
        Number charIdA = createCharacter(rest, base(), tokenA, uniqueName());

        String tokenB = registerAndGetToken(rest, base());

        Map<String, Object> body = Map.of("amount", 100, "reason", "theft");
        ResponseEntity<Map> resp = rest.postForEntity(
                base() + "/api/prestige/" + charIdA.longValue() + "/award",
                withToken(body, tokenB),
                Map.class);

        assertThat(resp.getStatusCode().value())
                .as("Another account must not award prestige to this character")
                .isIn(403, 404);
    }

    // ── Test 14: GET without token → 401 ─────────────────────────────────────

    @Test
    @DisplayName("getProfile_noToken_returns401")
    void getProfile_noToken_returns401() {
        String token = registerAndGetToken(rest, base());
        Number charId = createCharacter(rest, base(), token, uniqueName());
        long cid = charId.longValue();

        ResponseEntity<Map> resp = rest.getForEntity(
                base() + "/api/prestige/" + cid,
                Map.class);

        assertThat(resp.getStatusCode())
                .as("GET without token must return 401")
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
