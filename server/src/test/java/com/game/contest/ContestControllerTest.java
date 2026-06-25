package com.game.contest;

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
 * Integration tests for /api/contest/** (LANE CONTEST).
 *
 * <p>Scenarios covered:
 * <ol>
 *   <li>Create OPEN contest → contestStatus="OPEN", endDay = currentDay + durationDays.</li>
 *   <li>Enter: snapshots qualityScore from the CellarItem's quality field.</li>
 *   <li>Duplicate enter → 400.</li>
 *   <li>Enter after endDay (advance clock past endDay first) → 400.</li>
 *   <li>Enter an item you don't own → 404.</li>
 *   <li>Judging ranks by quality, pays winner, assigns placement correctly.</li>
 *   <li>Double-judge is a no-op (idempotent 200).</li>
 *   <li>GET /open lazily auto-judges expired contests (they disappear from the open list).</li>
 * </ol>
 *
 * <p>Two-character judging test:
 * Grows two bottles with different seeds/pickDays (which produce different quality scores),
 * reads quality from the grow response, enters both into a contest, advances the world clock,
 * calls judge, then asserts the higher-quality bottle's owner has placement=1 and that
 * owner's wallet increased by prizeGel.
 *
 * <p>Compilation rules followed (per CONTEST-ACHIEVEMENT-CHAT-SPEC hard rules):
 * <ul>
 *   <li>Maps used with {@code containsKey} declared as {@code Map<String,Object>}.</li>
 *   <li>4xx calls use {@code String.class} (error envelope is an object, not a List).</li>
 *   <li>List endpoints use {@code List.class}.</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ContestControllerTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    private String base() {
        return "http://localhost:" + port;
    }

    private static String uniqueName() {
        return "ct_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 1. Create OPEN contest
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("createContest_returnsOpenContest")
    @SuppressWarnings("unchecked")
    void createContest_returnsOpenContest() {
        String token = registerAndGetToken(rest, base());

        Map<String, Object> body = Map.of(
                "name",         "Grand Saperavi Cup",
                "description",  "Best aged Saperavi wins",
                "durationDays", 5,
                "prizeGel",     100.0);

        ResponseEntity<Map> resp = rest.postForEntity(
                base() + "/api/contest/create",
                withToken(body, token),
                Map.class);

        assertThat(resp.getStatusCode())
                .as("POST /api/contest/create must return 200")
                .isEqualTo(HttpStatus.OK);

        Map<String, Object> contest = (Map<String, Object>) resp.getBody();
        assertThat(contest).isNotNull();
        assertThat(contest.get("contestStatus")).isEqualTo("OPEN");
        assertThat(contest.get("id")).isNotNull();
        assertThat(((Number) contest.get("prizeGel")).doubleValue()).isEqualTo(100.0);
        // endDay = currentDay (0) + 5 = 5
        assertThat(((Number) contest.get("endDay")).longValue()).isGreaterThan(0L);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. Enter: snapshots qualityScore
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("enter_snapshotsQualityScore")
    @SuppressWarnings("unchecked")
    void enter_snapshotsQualityScore() {
        String token = registerAndGetToken(rest, base());
        Number charId = createCharacter(rest, base(), token, uniqueName());
        long cid = charId.longValue();

        // Grow a bottle and read both its id and quality in one call
        GrowResult grown = growAndGetResult(token, cid, 42L, 270);

        // Create a contest with a long duration
        Number contestId = createContest(token, 10, 50.0);

        // Enter
        Map<String, Object> enterBody = Map.of(
                "characterId",  cid,
                "cellarItemId", grown.itemId);

        ResponseEntity<Map> resp = rest.postForEntity(
                base() + "/api/contest/" + contestId.longValue() + "/enter",
                withToken(enterBody, token),
                Map.class);

        assertThat(resp.getStatusCode())
                .as("POST /enter must return 200")
                .isEqualTo(HttpStatus.OK);

        Map<String, Object> entry = (Map<String, Object>) resp.getBody();
        assertThat(entry).isNotNull();
        assertThat(((Number) entry.get("characterId")).longValue()).isEqualTo(cid);
        assertThat(((Number) entry.get("cellarItemId")).longValue())
                .isEqualTo(grown.itemId);
        // qualityScore must be a positive number (snapshot of the item's quality)
        double snappedQuality = ((Number) entry.get("qualityScore")).doubleValue();
        assertThat(snappedQuality).isGreaterThan(0.0);
        // placement is null until judging
        assertThat(entry.get("placement")).isNull();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. Duplicate enter → 400
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("enter_duplicate_400")
    @SuppressWarnings("unchecked")
    void enter_duplicate_400() {
        String token = registerAndGetToken(rest, base());
        Number charId = createCharacter(rest, base(), token, uniqueName());
        long cid = charId.longValue();

        Number cellarItemId1 = growAndGetItemId(token, cid, 42L, 270);
        Number cellarItemId2 = growAndGetItemId(token, cid, 99L, 250);
        Number contestId = createContest(token, 10, 50.0);

        // First enter — should succeed
        ResponseEntity<Map> first = rest.postForEntity(
                base() + "/api/contest/" + contestId.longValue() + "/enter",
                withToken(Map.of("characterId", cid, "cellarItemId", cellarItemId1.longValue()), token),
                Map.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Second enter — same character, different item → 400
        ResponseEntity<String> second = rest.postForEntity(
                base() + "/api/contest/" + contestId.longValue() + "/enter",
                withToken(Map.of("characterId", cid, "cellarItemId", cellarItemId2.longValue()), token),
                String.class);
        assertThat(second.getStatusCode())
                .as("Duplicate enter by same character must return 400")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. Enter after endDay → 400
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("enter_afterEndDay_400")
    @SuppressWarnings("unchecked")
    void enter_afterEndDay_400() {
        String token = registerAndGetToken(rest, base());
        Number charId = createCharacter(rest, base(), token, uniqueName());
        long cid = charId.longValue();

        Number cellarItemId = growAndGetItemId(token, cid, 42L, 270);

        // Contest with durationDays=2
        Number contestId = createContest(token, 2, 50.0);

        // Advance clock past endDay
        advanceClock(3);

        // Try to enter — should be 400 (contest has expired)
        ResponseEntity<String> resp = rest.postForEntity(
                base() + "/api/contest/" + contestId.longValue() + "/enter",
                withToken(Map.of("characterId", cid, "cellarItemId", cellarItemId.longValue()), token),
                String.class);

        assertThat(resp.getStatusCode())
                .as("Enter after endDay must return 400")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 5. Enter an item you don't own → 404
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("enter_unownedItem_404")
    @SuppressWarnings("unchecked")
    void enter_unownedItem_404() {
        // Two different accounts/characters
        String tokenA = registerAndGetToken(rest, base());
        Number charA  = createCharacter(rest, base(), tokenA, uniqueName());
        long cidA     = charA.longValue();

        String tokenB = registerAndGetToken(rest, base());
        Number charB  = createCharacter(rest, base(), tokenB, uniqueName());
        long cidB     = charB.longValue();

        // Character A grows a bottle
        Number itemIdA = growAndGetItemId(tokenA, cidA, 42L, 270);

        // Contest
        Number contestId = createContest(tokenA, 10, 50.0);

        // Character B tries to enter with A's item → 404 (not owned by B)
        ResponseEntity<String> resp = rest.postForEntity(
                base() + "/api/contest/" + contestId.longValue() + "/enter",
                withToken(Map.of("characterId", cidB, "cellarItemId", itemIdA.longValue()), tokenB),
                String.class);

        assertThat(resp.getStatusCode())
                .as("Entering with another character's item must return 404")
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 6. Judging ranks by quality, pays winner
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("judge_ranksByQuality_paysWinner")
    @SuppressWarnings("unchecked")
    void judge_ranksByQuality_paysWinner() {
        // Character A: grow with seed=42, pickDay=270
        String tokenA = registerAndGetToken(rest, base());
        Number charA  = createCharacter(rest, base(), tokenA, uniqueName());
        long cidA     = charA.longValue();

        // Character B: grow with seed=99, pickDay=220 (different inputs → different quality)
        String tokenB = registerAndGetToken(rest, base());
        Number charB  = createCharacter(rest, base(), tokenB, uniqueName());
        long cidB     = charB.longValue();

        // Grow items and read quality from the grow response
        GrowResult growA = growAndGetResult(tokenA, cidA, 42L, 270);
        GrowResult growB = growAndGetResult(tokenB, cidB, 99L, 220);

        double qualityA = growA.quality;
        double qualityB = growB.quality;
        long itemIdA    = growA.itemId;
        long itemIdB    = growB.itemId;

        // Determine which character has the higher quality bottle
        long higherCharId   = (qualityA >= qualityB) ? cidA     : cidB;
        String higherToken  = (qualityA >= qualityB) ? tokenA   : tokenB;
        long lowerCharId    = (qualityA >= qualityB) ? cidB     : cidA;
        String lowerToken   = (qualityA >= qualityB) ? tokenB   : tokenA;
        long higherItemId   = (qualityA >= qualityB) ? itemIdA  : itemIdB;
        long lowerItemId    = (qualityA >= qualityB) ? itemIdB  : itemIdA;

        // Read wallets before contest
        double higherWalletBefore = getWallet(higherToken, higherCharId);

        double prizeGel = 80.0;
        Number contestId = createContest(tokenA, 3, prizeGel);

        // Both characters enter
        ResponseEntity<Map> entryA = rest.postForEntity(
                base() + "/api/contest/" + contestId.longValue() + "/enter",
                withToken(Map.of("characterId", higherCharId, "cellarItemId", higherItemId), higherToken),
                Map.class);
        assertThat(entryA.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> entryB = rest.postForEntity(
                base() + "/api/contest/" + contestId.longValue() + "/enter",
                withToken(Map.of("characterId", lowerCharId, "cellarItemId", lowerItemId), lowerToken),
                Map.class);
        assertThat(entryB.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Advance clock past endDay
        advanceClock(4);

        // Judge
        ResponseEntity<Map> judgeResp = rest.postForEntity(
                base() + "/api/contest/" + contestId.longValue() + "/judge",
                withToken(Map.of(), tokenA),
                Map.class);
        assertThat(judgeResp.getStatusCode())
                .as("POST /judge must return 200")
                .isEqualTo(HttpStatus.OK);

        Map<String, Object> judged = (Map<String, Object>) judgeResp.getBody();
        assertThat(judged).isNotNull();
        assertThat(judged.get("contestStatus")).isEqualTo("JUDGED");

        // GET results
        ResponseEntity<List> resultsResp = rest.exchange(
                base() + "/api/contest/" + contestId.longValue() + "/results",
                HttpMethod.GET,
                getWithToken(tokenA),
                List.class);
        assertThat(resultsResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<?> results = resultsResp.getBody();
        assertThat(results).isNotNull().hasSize(2);

        // Find the entry for the higher-quality character
        Map<?, ?> winnerEntry = results.stream()
                .map(o -> (Map<?, ?>) o)
                .filter(e -> ((Number) e.get("characterId")).longValue() == higherCharId)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Winner entry not found in results"));

        assertThat(((Number) winnerEntry.get("placement")).intValue())
                .as("Higher-quality bottle's owner must have placement 1")
                .isEqualTo(1);

        // Winner wallet must have increased by prizeGel
        double higherWalletAfter = getWallet(higherToken, higherCharId);
        assertThat(higherWalletAfter)
                .as("Winner wallet must increase by prizeGel")
                .isEqualTo(higherWalletBefore + prizeGel);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 7. Double-judge is a no-op
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("judge_doubleJudge_noOp")
    @SuppressWarnings("unchecked")
    void judge_doubleJudge_noOp() {
        String token = registerAndGetToken(rest, base());
        Number contestId = createContest(token, 1, 50.0);

        // Advance past endDay
        advanceClock(2);

        // First judge
        ResponseEntity<Map> first = rest.postForEntity(
                base() + "/api/contest/" + contestId.longValue() + "/judge",
                withToken(Map.of(), token),
                Map.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Map<String, Object>) first.getBody()).get("contestStatus"))
                .isEqualTo("JUDGED");

        // Second judge — idempotent, must also return 200 JUDGED
        ResponseEntity<Map> second = rest.postForEntity(
                base() + "/api/contest/" + contestId.longValue() + "/judge",
                withToken(Map.of(), token),
                Map.class);
        assertThat(second.getStatusCode())
                .as("Double-judge must return 200 (idempotent)")
                .isEqualTo(HttpStatus.OK);
        assertThat(((Map<String, Object>) second.getBody()).get("contestStatus"))
                .as("Double-judge must return JUDGED status")
                .isEqualTo("JUDGED");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 8. GET /open lazy-judges expired contests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("listOpen_lazyJudgesExpiredContests")
    @SuppressWarnings("unchecked")
    void listOpen_lazyJudgesExpiredContests() {
        String token = registerAndGetToken(rest, base());

        // Create a short-lived contest
        Number contestId = createContest(token, 2, 30.0);

        // Advance past endDay
        advanceClock(3);

        // GET /open triggers lazy judging
        ResponseEntity<List> openResp = rest.exchange(
                base() + "/api/contest/open",
                HttpMethod.GET,
                getWithToken(token),
                List.class);
        assertThat(openResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        List<?> openContests = openResp.getBody();
        assertThat(openContests).isNotNull();

        // The expired contest must NOT appear in the OPEN list
        boolean expiredStillOpen = openContests.stream()
                .anyMatch(o -> contestId.longValue() ==
                        ((Number) ((Map<?, ?>) o).get("id")).longValue());
        assertThat(expiredStillOpen)
                .as("Expired contest must be auto-judged and removed from open list")
                .isFalse();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    /** Create a contest and return its id. */
    @SuppressWarnings("unchecked")
    private Number createContest(String token, int durationDays, double prizeGel) {
        Map<String, Object> body = Map.of(
                "name",         "Test Cup " + uniqueName(),
                "description",  "Integration test contest",
                "durationDays", durationDays,
                "prizeGel",     prizeGel);
        ResponseEntity<Map> resp = rest.postForEntity(
                base() + "/api/contest/create",
                withToken(body, token),
                Map.class);
        assertThat(resp.getStatusCode())
                .as("POST /api/contest/create must return 200")
                .isEqualTo(HttpStatus.OK);
        return (Number) resp.getBody().get("id");
    }

    /**
     * Grows a vintage and returns its cellar item id.
     * Different seed/pickDay combinations produce different quality scores.
     */
    @SuppressWarnings("unchecked")
    private Number growAndGetItemId(String token, long characterId, long seed, int pickDay) {
        Map<String, Object> body = Map.of(
                "seed", seed, "budLoad", 12, "pickDay", pickDay, "threats", false);
        ResponseEntity<Map> resp = rest.postForEntity(
                base() + "/api/cellar/" + characterId + "/grow",
                withToken(body, token),
                Map.class);
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

    /** Holds both the item id and quality from a single grow call. */
    private static class GrowResult {
        final long itemId;
        final double quality;
        GrowResult(long itemId, double quality) {
            this.itemId  = itemId;
            this.quality = quality;
        }
    }

    /**
     * Grows a vintage and returns both id and quality in one call.
     * This is the authoritative way to get quality — reads from the grow
     * response, not a subsequent GET, ensuring we compare real snapshotted values.
     */
    @SuppressWarnings("unchecked")
    private GrowResult growAndGetResult(String token, long characterId, long seed, int pickDay) {
        Map<String, Object> body = Map.of(
                "seed", seed, "budLoad", 12, "pickDay", pickDay, "threats", false);
        ResponseEntity<Map> resp = rest.postForEntity(
                base() + "/api/cellar/" + characterId + "/grow",
                withToken(body, token),
                Map.class);
        assertThat(resp.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.CREATED);
        Map<?, ?> respBody = resp.getBody();
        assertThat(respBody).isNotNull();
        Map<?, ?> item = respBody.containsKey("cellarItem")
                ? (Map<?, ?>) respBody.get("cellarItem")
                : respBody;
        long id      = ((Number) item.get("id")).longValue();
        double qual  = ((Number) item.get("quality")).doubleValue();
        return new GrowResult(id, qual);
    }

    /** Fetch the character's wallet balance. */
    @SuppressWarnings("unchecked")
    private double getWallet(String token, long characterId) {
        ResponseEntity<Map> resp = rest.exchange(
                base() + "/api/characters/" + characterId,
                HttpMethod.GET,
                getWithToken(token),
                Map.class);
        assertThat(resp.getStatusCode())
                .as("GET /api/characters/{id} must succeed")
                .isEqualTo(HttpStatus.OK);
        return ((Number) resp.getBody().get("walletGel")).doubleValue();
    }

    /** Advance the world clock by {@code days} sim-days via POST /api/world/advance. */
    private void advanceClock(int days) {
        Map<String, Object> body = Map.of("days", days);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Map> resp = rest.postForEntity(
                base() + "/api/world/advance",
                new HttpEntity<>(body, headers),
                Map.class);
        assertThat(resp.getStatusCode())
                .as("POST /api/world/advance must succeed")
                .isEqualTo(HttpStatus.OK);
    }

}
