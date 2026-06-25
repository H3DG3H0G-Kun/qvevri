package com.game.travel;

import com.game.account.AccountTestHelper;
import com.game.logistics.GeoUtil;
import com.game.world.Region;
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
 * Integration tests for LANE TRAVEL ({@code /api/travel/**}).
 *
 * <h2>Scenarios</h2>
 * <ol>
 *   <li>GET lazy-creates location at homeRegion (KAKHETI) with status SETTLED.</li>
 *   <li>POST /depart sets TRAVELLING with arriveDay &gt; departDay.</li>
 *   <li>POST /depart while already TRAVELLING → 400 ALREADY_TRAVELLING.</li>
 *   <li>POST /depart to unknown region → 400.</li>
 *   <li>POST /depart to same region → 400.</li>
 *   <li>After advancing clock past arriveDay, GET shows SETTLED at destRegion.</li>
 *   <li>Farther destination (Kakheti→Guria/Adjara) yields strictly more travelDays
 *       than closer destination (Kakheti→Kartli) — verified via GeoUtil directly.</li>
 * </ol>
 *
 * <p>Clock is frozen in test profile (world.real-seconds-per-sim-day=86400000).
 * Time is advanced via POST /api/world/advance.
 *
 * <p>Uses {@link AccountTestHelper}: {@code registerAndGetToken}, {@code createCharacter}
 * (defaults homeRegion KAKHETI), {@code withToken}, {@code getWithToken}.
 * Maps used with {@code containsKey} are declared {@code Map<String,Object>} per spec.
 * 4xx responses are read as {@code String.class}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class TravelControllerTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    private String base() {
        return "http://localhost:" + port;
    }

    private static String uniqueName() {
        return "tv_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    // ── 1. GET lazy-creates at homeRegion SETTLED ─────────────────────────────

    @Test
    @DisplayName("getLocation_lazyCreatesAtHomeRegion_settled")
    @SuppressWarnings("unchecked")
    void getLocation_lazyCreatesAtHomeRegion_settled() {
        String token  = registerAndGetToken(rest, base());
        Number charId = createCharacter(rest, base(), token, uniqueName());
        long cid = charId.longValue();

        ResponseEntity<Map> resp = rest.exchange(
                base() + "/api/travel/" + cid,
                HttpMethod.GET,
                getWithToken(token),
                Map.class);

        assertThat(resp.getStatusCode())
                .as("GET /api/travel/{id} must return 200")
                .isEqualTo(HttpStatus.OK);

        Map<String, Object> loc = (Map<String, Object>) resp.getBody();
        assertThat(loc).isNotNull();
        assertThat(loc).containsKey("characterId");
        assertThat(loc).containsKey("currentRegion");
        assertThat(loc).containsKey("travelStatus");

        assertThat(loc.get("currentRegion"))
                .as("lazy-created location must be at homeRegion KAKHETI")
                .isEqualTo("KAKHETI");
        assertThat(loc.get("travelStatus"))
                .as("newly created location must be SETTLED")
                .isEqualTo("SETTLED");
    }

    // ── 2. Depart sets TRAVELLING with arriveDay > departDay ─────────────────

    @Test
    @DisplayName("depart_setsTravel_withArriveDayAfterDepartDay")
    @SuppressWarnings("unchecked")
    void depart_setsTravel_withArriveDayAfterDepartDay() {
        String token  = registerAndGetToken(rest, base());
        Number charId = createCharacter(rest, base(), token, uniqueName());
        long cid = charId.longValue();

        // Ensure location exists first (idempotent)
        rest.exchange(base() + "/api/travel/" + cid,
                HttpMethod.GET, getWithToken(token), Map.class);

        // Depart to KARTLI
        Map<String, Object> body = Map.of("toRegion", "KARTLI");
        ResponseEntity<Map> resp = rest.postForEntity(
                base() + "/api/travel/" + cid + "/depart",
                withToken(body, token),
                Map.class);

        assertThat(resp.getStatusCode())
                .as("POST /depart must return 200")
                .isEqualTo(HttpStatus.OK);

        Map<String, Object> loc = (Map<String, Object>) resp.getBody();
        assertThat(loc).isNotNull();
        assertThat(loc).containsKey("travelStatus");
        assertThat(loc).containsKey("destRegion");
        assertThat(loc).containsKey("departDay");
        assertThat(loc).containsKey("arriveDay");

        assertThat(loc.get("travelStatus")).isEqualTo("TRAVELLING");
        assertThat(loc.get("destRegion")).isEqualTo("KARTLI");

        long departDay = ((Number) loc.get("departDay")).longValue();
        long arriveDay = ((Number) loc.get("arriveDay")).longValue();

        assertThat(arriveDay)
                .as("arriveDay must be strictly greater than departDay (travel takes >= 1 day)")
                .isGreaterThan(departDay);
    }

    // ── 3. Depart while already TRAVELLING → 400 ─────────────────────────────

    @Test
    @DisplayName("depart_whileAlreadyTravelling_400")
    @SuppressWarnings("unchecked")
    void depart_whileAlreadyTravelling_400() {
        String token  = registerAndGetToken(rest, base());
        Number charId = createCharacter(rest, base(), token, uniqueName());
        long cid = charId.longValue();

        // First depart — must succeed
        Map<String, Object> body1 = Map.of("toRegion", "KARTLI");
        ResponseEntity<Map> first = rest.postForEntity(
                base() + "/api/travel/" + cid + "/depart",
                withToken(body1, token),
                Map.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Second depart while still TRAVELLING — must fail
        Map<String, Object> body2 = Map.of("toRegion", "IMERETI");
        ResponseEntity<String> second = rest.postForEntity(
                base() + "/api/travel/" + cid + "/depart",
                withToken(body2, token),
                String.class);

        assertThat(second.getStatusCode())
                .as("Departing while TRAVELLING must return 400")
                .isEqualTo(HttpStatus.BAD_REQUEST);

        // Verify error body contains ALREADY_TRAVELLING or similar indicator
        String errBody = second.getBody();
        assertThat(errBody)
                .as("Error body must indicate ALREADY_TRAVELLING")
                .containsIgnoringCase("ALREADY_TRAVELLING");
    }

    // ── 4. Depart to unknown region → 400 ────────────────────────────────────

    @Test
    @DisplayName("depart_unknownRegion_400")
    @SuppressWarnings("unchecked")
    void depart_unknownRegion_400() {
        String token  = registerAndGetToken(rest, base());
        Number charId = createCharacter(rest, base(), token, uniqueName());
        long cid = charId.longValue();

        Map<String, Object> body = Map.of("toRegion", "NARNIA");
        ResponseEntity<String> resp = rest.postForEntity(
                base() + "/api/travel/" + cid + "/depart",
                withToken(body, token),
                String.class);

        assertThat(resp.getStatusCode())
                .as("Unknown region must return 400")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ── 5. Depart to same region → 400 ───────────────────────────────────────

    @Test
    @DisplayName("depart_sameRegion_400")
    @SuppressWarnings("unchecked")
    void depart_sameRegion_400() {
        String token  = registerAndGetToken(rest, base());
        Number charId = createCharacter(rest, base(), token, uniqueName());
        long cid = charId.longValue();

        // Character's homeRegion is KAKHETI (AccountTestHelper default)
        Map<String, Object> body = Map.of("toRegion", "KAKHETI");
        ResponseEntity<String> resp = rest.postForEntity(
                base() + "/api/travel/" + cid + "/depart",
                withToken(body, token),
                String.class);

        assertThat(resp.getStatusCode())
                .as("Departing to the same region must return 400")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ── 6. Clock advance past arriveDay → GET shows SETTLED at destRegion ────

    @Test
    @DisplayName("get_afterClockPastArriveDay_showsSettledAtDest")
    @SuppressWarnings("unchecked")
    void get_afterClockPastArriveDay_showsSettledAtDest() {
        String token  = registerAndGetToken(rest, base());
        Number charId = createCharacter(rest, base(), token, uniqueName());
        long cid = charId.longValue();

        // Depart to KARTLI
        Map<String, Object> departBody = Map.of("toRegion", "KARTLI");
        ResponseEntity<Map> departResp = rest.postForEntity(
                base() + "/api/travel/" + cid + "/depart",
                withToken(departBody, token),
                Map.class);
        assertThat(departResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        Map<String, Object> departedLoc = (Map<String, Object>) departResp.getBody();
        assertThat(departedLoc).isNotNull();
        long arriveDay  = ((Number) departedLoc.get("arriveDay")).longValue();
        int  currentDay = currentAbsoluteDay();

        // Advance clock well past arriveDay
        int daysToAdvance = (int) (arriveDay - currentDay) + 1;
        advanceClock(daysToAdvance);

        // GET must now show SETTLED at KARTLI (lazy arrival)
        ResponseEntity<Map> getResp = rest.exchange(
                base() + "/api/travel/" + cid,
                HttpMethod.GET,
                getWithToken(token),
                Map.class);

        assertThat(getResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        Map<String, Object> arrivedLoc = (Map<String, Object>) getResp.getBody();
        assertThat(arrivedLoc).isNotNull();
        assertThat(arrivedLoc.get("travelStatus"))
                .as("After clock passes arriveDay the character must be SETTLED")
                .isEqualTo("SETTLED");
        assertThat(arrivedLoc.get("currentRegion"))
                .as("After arrival, currentRegion must be the destination KARTLI")
                .isEqualTo("KARTLI");
        assertThat(arrivedLoc.get("destRegion"))
                .as("destRegion must be null/absent after arrival")
                .isNull();
    }

    // ── 7. Farther destination → strictly more travelDays (via GeoUtil) ──────

    @Test
    @DisplayName("travelDays_furtherRegion_isStrictlyMore")
    void travelDays_furtherRegion_isStrictlyMore() {
        // Kakheti → Kartli (Telavi → Gori, ~110 km → 3 days)
        int toKartli = GeoUtil.travelDays(Region.KAKHETI, Region.KARTLI);

        // Kakheti → Guria/Adjara (Telavi → Batumi, ~395 km → 10 days) — clearly farther
        int toGuriaAdjara = GeoUtil.travelDays(Region.KAKHETI, Region.GURIA_ADJARA);

        assertThat(toKartli)
                .as("Kakheti→Kartli must take at least 1 day")
                .isGreaterThanOrEqualTo(1);
        assertThat(toGuriaAdjara)
                .as("Kakheti→Guria/Adjara must take at least 1 day")
                .isGreaterThanOrEqualTo(1);
        assertThat(toGuriaAdjara)
                .as("Kakheti→Guria/Adjara must take strictly more days than Kakheti→Kartli")
                .isGreaterThan(toKartli);
    }

    // ── 8. Unauthorized access → 401 ─────────────────────────────────────────

    @Test
    @DisplayName("getLocation_noToken_401")
    void getLocation_noToken_401() {
        ResponseEntity<String> resp = rest.getForEntity(
                base() + "/api/travel/1",
                String.class);

        assertThat(resp.getStatusCode())
                .as("No token must return 401")
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ── 9. Wallet debited by travel cost ─────────────────────────────────────

    @Test
    @DisplayName("depart_deductsWalletByTravelCost")
    @SuppressWarnings("unchecked")
    void depart_deductsWalletByTravelCost() {
        String token  = registerAndGetToken(rest, base());
        Number charId = createCharacter(rest, base(), token, uniqueName());
        long cid = charId.longValue();

        double walletBefore = getWalletGel(token, cid);

        Map<String, Object> body = Map.of("toRegion", "KARTLI");
        ResponseEntity<Map> resp = rest.postForEntity(
                base() + "/api/travel/" + cid + "/depart",
                withToken(body, token),
                Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);

        double walletAfter = getWalletGel(token, cid);
        assertThat(walletAfter)
                .as("Wallet must be reduced by exactly %.1f GEL after departure",
                        TravelService.TRAVEL_COST_GEL)
                .isEqualTo(walletBefore - TravelService.TRAVEL_COST_GEL, org.assertj.core.api.Assertions.within(0.001));
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /** Read the current absolute sim day from the world clock. */
    @SuppressWarnings("unchecked")
    private int currentAbsoluteDay() {
        ResponseEntity<Map> resp = rest.getForEntity(
                base() + "/api/world/clock", Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return ((Number) resp.getBody().get("absoluteDay")).intValue();
    }

    /** Advance the world clock by n sim-days via POST /api/world/advance. */
    @SuppressWarnings("unchecked")
    private void advanceClock(int days) {
        if (days < 1) return;
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

    /** Read the character's current walletGel via GET /api/characters. */
    @SuppressWarnings("unchecked")
    private double getWalletGel(String token, long characterId) {
        ResponseEntity<java.util.List> resp = rest.exchange(
                base() + "/api/characters",
                HttpMethod.GET,
                getWithToken(token),
                java.util.List.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        java.util.List<?> chars = resp.getBody();
        assertThat(chars).isNotNull();
        for (Object c : chars) {
            Map<?, ?> cm = (Map<?, ?>) c;
            if (characterId == ((Number) cm.get("id")).longValue()) {
                return ((Number) cm.get("walletGel")).doubleValue();
            }
        }
        throw new AssertionError("Character " + characterId + " not found in /api/characters");
    }
}
