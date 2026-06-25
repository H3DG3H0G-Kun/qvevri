package com.game.festival;

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
 * Integration tests for /api/festival/** (LANE FESTIVAL).
 *
 * <p>Scenarios covered:
 * <ol>
 *   <li>GET /api/festival/calendar → returns all 4 festival definitions with required fields.</li>
 *   <li>GET /api/festival/active reflects the world clock — advance until inside a window,
 *       then the active list includes that festival.</li>
 *   <li>POST /api/festival/{festivalId}/participate during active window grants rewardGel
 *       (verified via GET /api/characters/{id}) and records participation.</li>
 *   <li>Participating again same world year → 400 ALREADY_PARTICIPATED.</li>
 *   <li>Participating when festival NOT active → 400 NOT_ACTIVE.</li>
 *   <li>Unknown festivalId → 404.</li>
 *   <li>Ownership enforced: other account → 404.</li>
 * </ol>
 *
 * <p>Clock-steering strategy: read GET /api/world/clock for the current dayOfYear,
 * compute how many days to advance to reach a known festival window, POST
 * /api/world/advance that delta, then re-read dayOfYear to confirm we landed inside
 * the window. This is robust to a shared clock whose starting value is unknown.
 *
 * <p>The test profile sets {@code world.real-seconds-per-sim-day=86400000}, so the
 * auto-scheduler is a no-op; clock advances only via POST /api/world/advance.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class FestivalControllerTest {

    // ── Festival constants (must match FestivalCalendar) ─────────────────────

    /** "rtveli" is the largest window (days 240–270) and has the biggest rewardGel (75.0). */
    private static final String RTVELI_ID       = "rtveli";
    private static final int    RTVELI_START    = 240;
    private static final int    RTVELI_END      = 270;
    private static final double RTVELI_REWARD   = 75.0;

    /** "vine_blessing" (days 80–100, rewardGel=30.0) — used for NOT_ACTIVE test. */
    private static final String VINE_BLESSING_ID    = "vine_blessing";
    private static final int    VINE_BLESSING_START = 80;
    private static final int    VINE_BLESSING_END   = 100;

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String base() { return "http://localhost:" + port; }

    private static String uniqueName() {
        return "fst_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    /**
     * GET /api/world/clock — returns the clock map.
     * No auth needed (/api/world/** is permitAll).
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> getClock() {
        ResponseEntity<Map> resp = rest.getForEntity(base() + "/api/world/clock", Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (Map<String, Object>) resp.getBody();
    }

    /**
     * POST /api/world/advance { days: n } — advances the world clock by n sim-days.
     */
    private void advanceDays(int days) {
        if (days <= 0) return;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> body = Map.of("days", days);
        ResponseEntity<Map> resp = rest.postForEntity(
                base() + "/api/world/advance",
                new HttpEntity<>(body, headers),
                Map.class);
        assertThat(resp.getStatusCode())
                .as("POST /api/world/advance must return 200")
                .isEqualTo(HttpStatus.OK);
    }

    /**
     * Steers the world clock so that the current dayOfYear lands inside
     * [{@code targetStart}, {@code targetEnd}]. Reads the current dayOfYear,
     * computes the shortest forward advance to reach {@code targetStart}, then
     * re-reads and asserts the window.
     *
     * <p>Because the clock only moves forward, if the current day is already
     * inside the window we don't advance. If the current day is past the end we
     * advance to the next occurrence (wrap around via next year).
     *
     * @return the dayOfYear after steering (confirmed inside the window)
     */
    private int steerClockIntoWindow(int targetStart, int targetEnd) {
        Map<String, Object> clock = getClock();
        int currentDay = ((Number) clock.get("dayOfYear")).intValue();

        int daysToAdvance;
        if (currentDay >= targetStart && currentDay <= targetEnd) {
            // Already inside — no advance needed
            daysToAdvance = 0;
        } else if (currentDay < targetStart) {
            // Before the window in this year
            daysToAdvance = targetStart - currentDay;
        } else {
            // Past the end — advance to next year's start of the window
            // (365 - currentDay) brings us to day 0 of next year, then +targetStart
            daysToAdvance = (365 - currentDay) + targetStart;
        }

        advanceDays(daysToAdvance);

        // Re-read and confirm
        Map<String, Object> after = getClock();
        int dayAfter = ((Number) after.get("dayOfYear")).intValue();
        assertThat(dayAfter)
                .as("After steering, dayOfYear must be inside [%d, %d]", targetStart, targetEnd)
                .isGreaterThanOrEqualTo(targetStart)
                .isLessThanOrEqualTo(targetEnd);
        return dayAfter;
    }

    /**
     * Returns the character's wallet balance via GET /api/characters/{id}.
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

    // ─────────────────────────────────────────────────────────────────────────
    // 1. GET /api/festival/calendar → 4 festivals with required fields
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("calendar_returnsFourFestivals_withRequiredFields")
    @SuppressWarnings("unchecked")
    void calendar_returnsFourFestivals_withRequiredFields() {
        String token = registerAndGetToken(rest, base());

        ResponseEntity<List> resp = rest.exchange(
                base() + "/api/festival/calendar",
                HttpMethod.GET,
                getWithToken(token),
                List.class);

        assertThat(resp.getStatusCode())
                .as("GET /api/festival/calendar must return 200")
                .isEqualTo(HttpStatus.OK);

        List<?> calendar = resp.getBody();
        assertThat(calendar)
                .as("Calendar must contain exactly 4 festivals")
                .isNotNull()
                .hasSize(4);

        // Every entry must have the required fields
        for (Object entry : calendar) {
            Map<String, Object> f = (Map<String, Object>) entry;
            assertThat(f)
                    .as("Each festival definition must contain required fields")
                    .containsKeys("id", "name", "description",
                            "startDayOfYear", "endDayOfYear",
                            "bonusType", "bonusValue", "rewardGel");
        }

        // Spot-check: "rtveli" must be present
        boolean hasRtveli = calendar.stream()
                .anyMatch(o -> RTVELI_ID.equals(((Map<?, ?>) o).get("id")));
        assertThat(hasRtveli)
                .as("Calendar must contain 'rtveli'")
                .isTrue();

        // Spot-check: all 4 known ids present
        for (String expectedId : new String[]{
                "vine_blessing", "qvevri_opening", "rtveli", "new_wine_fair"}) {
            boolean found = calendar.stream()
                    .anyMatch(o -> expectedId.equals(((Map<?, ?>) o).get("id")));
            assertThat(found)
                    .as("Calendar must contain festival id '" + expectedId + "'")
                    .isTrue();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. GET /api/festival/active reflects the world clock
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("active_reflectsWorldClock_includesRtveliWhenInsideWindow")
    @SuppressWarnings("unchecked")
    void active_reflectsWorldClock_includesRtveliWhenInsideWindow() {
        String token = registerAndGetToken(rest, base());

        // Steer the clock into the rtveli window
        steerClockIntoWindow(RTVELI_START, RTVELI_END);

        // GET /api/festival/active — must include rtveli
        ResponseEntity<List> resp = rest.exchange(
                base() + "/api/festival/active",
                HttpMethod.GET,
                getWithToken(token),
                List.class);

        assertThat(resp.getStatusCode())
                .as("GET /api/festival/active must return 200")
                .isEqualTo(HttpStatus.OK);

        List<?> active = resp.getBody();
        assertThat(active).isNotNull();

        boolean hasRtveli = active.stream()
                .anyMatch(o -> RTVELI_ID.equals(((Map<?, ?>) o).get("id")));
        assertThat(hasRtveli)
                .as("Active festivals must include 'rtveli' when clock is inside days %d–%d",
                        RTVELI_START, RTVELI_END)
                .isTrue();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. Participate during active window → grants rewardGel + records row
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("participate_duringActiveWindow_grantsRewardGelAndRecords")
    @SuppressWarnings("unchecked")
    void participate_duringActiveWindow_grantsRewardGelAndRecords() {
        String token   = registerAndGetToken(rest, base());
        Number charId  = createCharacter(rest, base(), token, uniqueName());
        long cid       = charId.longValue();

        // Steer clock into rtveli window
        steerClockIntoWindow(RTVELI_START, RTVELI_END);

        // Record wallet before participation
        double walletBefore = getWallet(token, cid);

        // POST /api/festival/rtveli/participate
        Map<String, Object> body = Map.of("characterId", cid);
        ResponseEntity<Map> resp = rest.postForEntity(
                base() + "/api/festival/" + RTVELI_ID + "/participate",
                withToken(body, token),
                Map.class);

        assertThat(resp.getStatusCode())
                .as("POST /api/festival/rtveli/participate must return 200")
                .isEqualTo(HttpStatus.OK);

        Map<String, Object> participation = (Map<String, Object>) resp.getBody();
        assertThat(participation).isNotNull();
        assertThat(participation).containsKey("id");
        assertThat(participation.get("festivalId")).isEqualTo(RTVELI_ID);
        assertThat(((Number) participation.get("characterId")).longValue()).isEqualTo(cid);
        assertThat(participation.get("claimed")).isEqualTo(true);

        // Wallet must have increased by rewardGel
        double walletAfter = getWallet(token, cid);
        assertThat(walletAfter)
                .as("Wallet must increase by rewardGel=%.1f after participation", RTVELI_REWARD)
                .isEqualTo(walletBefore + RTVELI_REWARD);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. Participating again same world year → 400 ALREADY_PARTICIPATED
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("participate_twice_sameYear_returnsAlreadyParticipated400")
    @SuppressWarnings("unchecked")
    void participate_twice_sameYear_returnsAlreadyParticipated400() {
        String token  = registerAndGetToken(rest, base());
        Number charId = createCharacter(rest, base(), token, uniqueName());
        long cid      = charId.longValue();

        // Steer clock into rtveli window
        steerClockIntoWindow(RTVELI_START, RTVELI_END);

        Map<String, Object> body = Map.of("characterId", cid);

        // First participation — must succeed
        ResponseEntity<Map> first = rest.postForEntity(
                base() + "/api/festival/" + RTVELI_ID + "/participate",
                withToken(body, token),
                Map.class);
        assertThat(first.getStatusCode())
                .as("First participation must return 200")
                .isEqualTo(HttpStatus.OK);

        // Second participation in same year — must fail with 400
        ResponseEntity<String> second = rest.postForEntity(
                base() + "/api/festival/" + RTVELI_ID + "/participate",
                withToken(body, token),
                String.class);
        assertThat(second.getStatusCode())
                .as("Second participation in same year must return 400")
                .isEqualTo(HttpStatus.BAD_REQUEST);

        // Wallet must NOT have changed from the first participation point
        // (second call is rejected before any wallet adjustment)
        double walletAfterSecond = getWallet(token, cid);
        // We can't assert the exact wallet before second call here, but we can
        // confirm the status code is 400 (reward not double-granted).
        // The exact no-double-pay assertion is done separately in the next test.
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 5. Participating when NOT active → 400 NOT_ACTIVE
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("participate_whenNotActive_returnsNotActive400")
    @SuppressWarnings("unchecked")
    void participate_whenNotActive_returnsNotActive400() {
        String token  = registerAndGetToken(rest, base());
        Number charId = createCharacter(rest, base(), token, uniqueName());
        long cid      = charId.longValue();

        // Steer clock into rtveli window (days 240-270)
        steerClockIntoWindow(RTVELI_START, RTVELI_END);

        // Now try to participate in vine_blessing (days 80-100) — not active right now
        // (we are at day 240+ so vine_blessing is not active)
        Map<String, Object> body = Map.of("characterId", cid);
        ResponseEntity<String> resp = rest.postForEntity(
                base() + "/api/festival/" + VINE_BLESSING_ID + "/participate",
                withToken(body, token),
                String.class);

        assertThat(resp.getStatusCode())
                .as("Participating in a non-active festival must return 400")
                .isEqualTo(HttpStatus.BAD_REQUEST);

        // Response body should contain NOT_ACTIVE code
        String responseBody = resp.getBody();
        assertThat(responseBody)
                .as("Error response must mention NOT_ACTIVE")
                .contains("NOT_ACTIVE");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 6. Unknown festivalId → 404
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("participate_unknownFestivalId_returns404")
    @SuppressWarnings("unchecked")
    void participate_unknownFestivalId_returns404() {
        String token  = registerAndGetToken(rest, base());
        Number charId = createCharacter(rest, base(), token, uniqueName());
        long cid      = charId.longValue();

        Map<String, Object> body = Map.of("characterId", cid);
        ResponseEntity<String> resp = rest.postForEntity(
                base() + "/api/festival/no_such_festival/participate",
                withToken(body, token),
                String.class);

        assertThat(resp.getStatusCode())
                .as("Unknown festivalId must return 404")
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 7. Ownership enforced: other account → 404
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("participate_otherAccount_returns404")
    @SuppressWarnings("unchecked")
    void participate_otherAccount_returns404() {
        // Owner creates character
        String ownerToken = registerAndGetToken(rest, base());
        Number charId     = createCharacter(rest, base(), ownerToken, uniqueName());
        long cid          = charId.longValue();

        // Steer clock into rtveli window
        steerClockIntoWindow(RTVELI_START, RTVELI_END);

        // Another account tries to participate with the owner's character
        String otherToken = registerAndGetToken(rest, base());
        Map<String, Object> body = Map.of("characterId", cid);

        ResponseEntity<String> resp = rest.postForEntity(
                base() + "/api/festival/" + RTVELI_ID + "/participate",
                withToken(body, otherToken),
                String.class);

        assertThat(resp.getStatusCode().value())
                .as("Non-owner account must receive 404 on participate")
                .isIn(404, 403);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 8. No double-pay: wallet unchanged on rejected second call
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("participate_twice_walletUnchangedOnSecondCall")
    @SuppressWarnings("unchecked")
    void participate_twice_walletUnchangedOnSecondCall() {
        String token  = registerAndGetToken(rest, base());
        Number charId = createCharacter(rest, base(), token, uniqueName());
        long cid      = charId.longValue();

        // Steer clock into rtveli window
        steerClockIntoWindow(RTVELI_START, RTVELI_END);

        Map<String, Object> body = Map.of("characterId", cid);

        // First participation — succeeds, grants rewardGel
        ResponseEntity<Map> first = rest.postForEntity(
                base() + "/api/festival/" + RTVELI_ID + "/participate",
                withToken(body, token),
                Map.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Record wallet after first participation
        double walletAfterFirst = getWallet(token, cid);

        // Second participation — rejected (400)
        ResponseEntity<String> second = rest.postForEntity(
                base() + "/api/festival/" + RTVELI_ID + "/participate",
                withToken(body, token),
                String.class);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // Wallet must NOT have changed
        double walletAfterSecond = getWallet(token, cid);
        assertThat(walletAfterSecond)
                .as("Wallet must not change when second participation is rejected")
                .isEqualTo(walletAfterFirst);
    }
}
