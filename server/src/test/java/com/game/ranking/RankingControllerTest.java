package com.game.ranking;

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
 * Integration tests for {@code /api/ranking/**} (LANE RANKING).
 *
 * <p>Scenarios covered:
 * <ol>
 *   <li>WEALTH board orders characters by walletGel descending with correct rankPos.</li>
 *   <li>WEALTH board: missing auth → 401.</li>
 *   <li>/me?board=wealth returns the caller's rank+score; rankPos=0 when absent.</li>
 *   <li>/me ownership enforced — other account's character → 404.</li>
 *   <li>/me with invalid board → 400.</li>
 *   <li>VINTNER board: character with a grown bottle appears; character without does not.</li>
 *   <li>POST /api/ranking/snapshot persists rows; subsequent GET /wealth still works.</li>
 *   <li>snapshot with unknown board → 400.</li>
 *   <li>GUILD board returns an empty or non-empty list without error.</li>
 * </ol>
 *
 * <p>Wallet asymmetry: characters start at 100 GEL. To create a character with a
 * <em>lower</em> wallet we use POST /api/market/buy (via the market pipeline) or
 * POST /api/shop/buy — but the simplest approach without depending on extra lanes
 * is to use POST /api/bank/deposit (subtracts from wallet) or simply compare two
 * characters where one was created first and immediately deposited some GEL into
 * the bank (reducing wallet). Alternatively, we use the BANK deposit endpoint:
 * deposit X GEL from characterA so its wallet falls below 100 GEL, while
 * characterB remains at 100 GEL → characterB should rank above characterA.
 *
 * <p>All response-body maps that call {@code containsKey} are declared
 * {@code Map<String,Object>} per the spec constraint.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class RankingControllerTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    private String base() {
        return "http://localhost:" + port;
    }

    private static String uniqueName() {
        return "rk_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helper: deposit GEL into bank to reduce wallet
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Deposits {@code amount} GEL from the character's wallet into their bank
     * savings account, thereby reducing wallet. Uses the BANK lane endpoint
     * (read-only access from the ranking lane's test perspective).
     */
    @SuppressWarnings("unchecked")
    private void depositToBank(String token, long charId, double amount) {
        Map<String, Object> body = Map.of("characterId", charId, "amountGel", amount);
        ResponseEntity<Map> resp = rest.postForEntity(
                base() + "/api/bank/deposit",
                withToken(body, token),
                Map.class);
        assertThat(resp.getStatusCode())
                .as("Bank deposit must succeed for wallet asymmetry setup")
                .isEqualTo(HttpStatus.OK);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helper: grow a bottle in the cellar for the VINTNER board
    // ─────────────────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private void growBottle(String token, long charId) {
        Map<String, Object> body = Map.of(
                "seed", 42,
                "budLoad", 12,
                "pickDay", 270,
                "threats", false);
        ResponseEntity<Map> resp = rest.postForEntity(
                base() + "/api/cellar/" + charId + "/grow",
                withToken(body, token),
                Map.class);
        assertThat(resp.getStatusCode())
                .as("cellar/grow must succeed for VINTNER board setup")
                .isIn(HttpStatus.OK, HttpStatus.CREATED);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helper: GET a board and return the list
    // ─────────────────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private ResponseEntity<List> getBoard(String token, String boardPath) {
        return rest.exchange(
                base() + "/api/ranking/" + boardPath,
                HttpMethod.GET,
                getWithToken(token),
                List.class);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 1. WEALTH board orders characters by walletGel desc with correct rankPos
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("wealthBoard_orderedByWalletDescending_withCorrectRankPos")
    @SuppressWarnings("unchecked")
    void wealthBoard_orderedByWalletDescending_withCorrectRankPos() {
        // charA starts at 100 GEL; we deposit 50 GEL to reduce to 50 GEL
        String tokenA = registerAndGetToken(rest, base());
        Number charIdA = createCharacter(rest, base(), tokenA, uniqueName());
        long cidA = charIdA.longValue();
        depositToBank(tokenA, cidA, 50.0);  // walletA = 50 GEL

        // charB stays at 100 GEL (no deposit)
        String tokenB = registerAndGetToken(rest, base());
        Number charIdB = createCharacter(rest, base(), tokenB, uniqueName());
        long cidB = charIdB.longValue();    // walletB = 100 GEL

        // Compare via /me so the assertion is robust to the shared test DB size:
        // the global top-20 board may not contain these two characters, but their
        // RELATIVE order (B richer than A) must always hold.
        int rankA = meRankPos(tokenA, "wealth", cidA);
        int rankB = meRankPos(tokenB, "wealth", cidB);

        assertThat(rankB).as("charB (100 GEL) must be ranked on the wealth board").isGreaterThan(0);
        assertThat(rankA).as("charA (50 GEL) must be ranked on the wealth board").isGreaterThan(0);
        assertThat(rankB).as("charB (100 GEL) ranks above charA (50 GEL)").isLessThan(rankA);

        // The board itself is 1-based: its first entry has rankPos 1.
        ResponseEntity<List> resp = getBoard(tokenA, "wealth");
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<?> entries = resp.getBody();
        assertThat(entries).isNotNull().isNotEmpty();
        Map<String, Object> firstEntry = (Map<String, Object>) entries.get(0);
        assertThat(firstEntry.containsKey("rankPos")).isTrue();
        assertThat(((Number) firstEntry.get("rankPos")).intValue()).isEqualTo(1);
    }

    /** Calls GET /api/ranking/me and returns the caller's rankPos for the given board. */
    @SuppressWarnings("unchecked")
    private int meRankPos(String token, String board, long characterId) {
        ResponseEntity<Map> resp = rest.exchange(
                base() + "/api/ranking/me?board=" + board + "&characterId=" + characterId,
                HttpMethod.GET,
                getWithToken(token),
                Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = resp.getBody();
        assertThat(body).isNotNull();
        return ((Number) body.get("rankPos")).intValue();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. WEALTH board: missing auth → 401
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("wealthBoard_noAuth_returns401")
    void wealthBoard_noAuth_returns401() {
        // Use String.class since a 401 returns an error envelope, not a List
        ResponseEntity<String> resp = rest.exchange(
                base() + "/api/ranking/wealth",
                HttpMethod.GET,
                HttpEntity.EMPTY,
                String.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(401);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. /me?board=wealth returns the caller's rank; rankPos=0 when not on board
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("meRank_wealthBoard_returnsCallerPosition")
    @SuppressWarnings("unchecked")
    void meRank_wealthBoard_returnsCallerPosition() {
        String token = registerAndGetToken(rest, base());
        Number charId = createCharacter(rest, base(), token, uniqueName());
        long cid = charId.longValue();

        // The character starts at 100 GEL — should appear on the wealth board
        ResponseEntity<Map> resp = rest.exchange(
                base() + "/api/ranking/me?board=wealth&characterId=" + cid,
                HttpMethod.GET,
                getWithToken(token),
                Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = resp.getBody();
        assertThat(body).isNotNull();
        assertThat(body.containsKey("rankPos")).isTrue();
        assertThat(body.containsKey("score")).isTrue();
        // rankPos > 0 means on the board
        int rankPos = ((Number) body.get("rankPos")).intValue();
        assertThat(rankPos).as("character with 100 GEL should be on wealth board").isGreaterThan(0);
        double score = ((Number) body.get("score")).doubleValue();
        assertThat(score).as("score should be the wallet balance").isGreaterThan(0.0);
    }

    @Test
    @DisplayName("meRank_vintnerBoard_notOnBoard_returnsRankPos0")
    @SuppressWarnings("unchecked")
    void meRank_vintnerBoard_notOnBoard_returnsRankPos0() {
        // A fresh character with no cellar items should not appear on VINTNER board
        String token = registerAndGetToken(rest, base());
        Number charId = createCharacter(rest, base(), token, uniqueName());
        long cid = charId.longValue();

        ResponseEntity<Map> resp = rest.exchange(
                base() + "/api/ranking/me?board=vintner&characterId=" + cid,
                HttpMethod.GET,
                getWithToken(token),
                Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = resp.getBody();
        assertThat(body).isNotNull();
        assertThat(((Number) body.get("rankPos")).intValue())
                .as("character with no cellar items should have rankPos=0")
                .isEqualTo(0);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. /me ownership enforced
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("meRank_ownershipEnforced_otherAccountCharacter_returns404")
    void meRank_ownershipEnforced_otherAccountCharacter_returns404() {
        // charA belongs to accountA
        String tokenA = registerAndGetToken(rest, base());
        Number charIdA = createCharacter(rest, base(), tokenA, uniqueName());

        // tokenB tries to query charA's rank — should fail with 404
        String tokenB = registerAndGetToken(rest, base());
        ResponseEntity<String> resp = rest.exchange(
                base() + "/api/ranking/me?board=wealth&characterId=" + charIdA.longValue(),
                HttpMethod.GET,
                getWithToken(tokenB),
                String.class);
        assertThat(resp.getStatusCode().value())
                .as("other account should not see this character's rank (404)")
                .isEqualTo(404);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 5. /me with invalid board → 400
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("meRank_invalidBoard_returns400")
    void meRank_invalidBoard_returns400() {
        String token = registerAndGetToken(rest, base());
        Number charId = createCharacter(rest, base(), token, uniqueName());

        ResponseEntity<String> resp = rest.exchange(
                base() + "/api/ranking/me?board=bogusboard&characterId=" + charId.longValue(),
                HttpMethod.GET,
                getWithToken(token),
                String.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(400);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 6. VINTNER board: character with a grown bottle appears; others without do not
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("vintnerBoard_characterWithBottleAppearsOnBoard")
    @SuppressWarnings("unchecked")
    void vintnerBoard_characterWithBottleAppearsOnBoard() {
        // charWithBottle: grow a bottle so they appear on VINTNER board
        String tokenWith = registerAndGetToken(rest, base());
        Number charWithId = createCharacter(rest, base(), tokenWith, uniqueName());
        long cidWith = charWithId.longValue();
        growBottle(tokenWith, cidWith);

        // charWithout: no bottle, should not appear on VINTNER board
        String tokenWithout = registerAndGetToken(rest, base());
        Number charWithoutId = createCharacter(rest, base(), tokenWithout, uniqueName());
        long cidWithout = charWithoutId.longValue();

        // charWithBottle must be RANKED on the full VINTNER ranking. Use /me rather than
        // scanning the top-20 board: the shared test DB accumulates >20 bottle-owners
        // (export/contest/trade/winemaker tests all grow bottles), so this character's
        // single bottle may fall outside the truncated board view even though they are
        // legitimately ranked.
        int withRank = meRankPos(tokenWith, "vintner", cidWith);
        assertThat(withRank)
                .as("charWithBottle should be ranked on the VINTNER board")
                .isGreaterThan(0);

        // charWithout owns no bottle, so they can never appear on the VINTNER board
        // (no CellarItem → not in the ranking at all) — robust regardless of DB size.
        ResponseEntity<List> resp = getBoard(tokenWith, "vintner");
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<?> entries = resp.getBody();
        assertThat(entries).isNotNull();
        boolean foundWithout = false;
        for (Object obj : entries) {
            Map<String, Object> entry = (Map<String, Object>) obj;
            long subjectId = ((Number) entry.get("subjectId")).longValue();
            if (subjectId == cidWithout) foundWithout = true;
        }
        assertThat(foundWithout).as("charWithout (no bottle) should NOT be on VINTNER board").isFalse();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 7. POST /api/ranking/snapshot persists rows
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("snapshot_persistsRows_andGetBoardStillWorks")
    @SuppressWarnings("unchecked")
    void snapshot_persistsRows_andGetBoardStillWorks() {
        String token = registerAndGetToken(rest, base());
        Number charId = createCharacter(rest, base(), token, uniqueName());
        // Character at 100 GEL — should appear on WEALTH board

        // POST /api/ranking/snapshot { board: "wealth" }
        Map<String, String> snapshotBody = Map.of("board", "wealth");
        ResponseEntity<List> snapshotResp = rest.postForEntity(
                base() + "/api/ranking/snapshot",
                withToken(snapshotBody, token),
                List.class);
        assertThat(snapshotResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<?> rows = snapshotResp.getBody();
        assertThat(rows).isNotNull().isNotEmpty();

        // Each row should have expected snapshot fields
        Map<String, Object> firstRow = (Map<String, Object>) rows.get(0);
        assertThat(firstRow.containsKey("id")).isTrue();
        assertThat(firstRow.containsKey("board")).isTrue();
        assertThat(firstRow.containsKey("rankPos")).isTrue();
        assertThat(firstRow.containsKey("subjectId")).isTrue();
        assertThat(firstRow.containsKey("score")).isTrue();
        assertThat(firstRow.containsKey("simDay")).isTrue();
        assertThat(firstRow.get("board")).isEqualTo("WEALTH");
        assertThat(((Number) firstRow.get("rankPos")).intValue()).isEqualTo(1);

        // Live GET board still works after snapshot
        ResponseEntity<List> liveResp = getBoard(token, "wealth");
        assertThat(liveResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(liveResp.getBody()).isNotNull().isNotEmpty();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 8. snapshot with unknown board → 400
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("snapshot_unknownBoard_returns400")
    void snapshot_unknownBoard_returns400() {
        String token = registerAndGetToken(rest, base());
        createCharacter(rest, base(), token, uniqueName());

        Map<String, String> body = Map.of("board", "nonexistent");
        ResponseEntity<String> resp = rest.postForEntity(
                base() + "/api/ranking/snapshot",
                withToken(body, token),
                String.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(400);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 9. GUILD board returns a list (empty or non-empty) without error
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("guildBoard_returnsListWithoutError")
    void guildBoard_returnsListWithoutError() {
        String token = registerAndGetToken(rest, base());
        createCharacter(rest, base(), token, uniqueName());

        ResponseEntity<List> resp = getBoard(token, "guild");
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
        // List can be empty if no guilds exist — that's acceptable per spec
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 10. VINTNER board: /me?board=vintner shows rank after growing a bottle
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("meRank_vintnerBoard_afterGrow_returnsRankPos")
    @SuppressWarnings("unchecked")
    void meRank_vintnerBoard_afterGrow_returnsRankPos() {
        String token = registerAndGetToken(rest, base());
        Number charId = createCharacter(rest, base(), token, uniqueName());
        long cid = charId.longValue();

        // Grow a bottle to appear on VINTNER board
        growBottle(token, cid);

        ResponseEntity<Map> resp = rest.exchange(
                base() + "/api/ranking/me?board=vintner&characterId=" + cid,
                HttpMethod.GET,
                getWithToken(token),
                Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = resp.getBody();
        assertThat(body).isNotNull();
        int rankPos = ((Number) body.get("rankPos")).intValue();
        assertThat(rankPos).as("character with a bottle should be on VINTNER board (rankPos > 0)").isGreaterThan(0);
        double score = ((Number) body.get("score")).doubleValue();
        assertThat(score).as("VINTNER score = bottle quality, must be positive").isGreaterThan(0.0);
    }
}
