package com.game.world.clock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the world clock endpoints (WA1 lane, WORLD-CLOCK-SPEC §1).
 *
 * <p>The test profile sets {@code world.real-seconds-per-sim-day=86400000},
 * effectively freezing the auto-scheduler. Tests control time exclusively
 * via POST /api/world/advance.
 *
 * <p>All clock endpoints are {@code permitAll} — no Authorization header is needed.
 *
 * Tests:
 * <ul>
 *   <li>getClock_returnsDate           – GET /api/world/clock returns all required fields</li>
 *   <li>advance_incrementsDay          – POST advance 5 → dayOfYear +5, absoluteDay +5</li>
 *   <li>advance_rollsOverYear          – POST advance 365 from a fresh year-start → year+1, dayOfYear wraps</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class WorldClockControllerTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    private String base() {
        return "http://localhost:" + port;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** GET /api/world/clock → raw Map */
    @SuppressWarnings("unchecked")
    private Map<String, Object> getClock() {
        ResponseEntity<Map> resp = rest.getForEntity(base() + "/api/world/clock", Map.class);
        assertThat(resp.getStatusCode())
                .as("GET /api/world/clock must return 200")
                .isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).as("clock response body must not be null").isNotNull();
        return (Map<String, Object>) resp.getBody();
    }

    /** POST /api/world/advance { days: n } → raw Map */
    @SuppressWarnings("unchecked")
    private Map<String, Object> advance(int days) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> body = Map.of("days", days);
        ResponseEntity<Map> resp = rest.postForEntity(
                base() + "/api/world/advance",
                new HttpEntity<>(body, headers),
                Map.class);
        assertThat(resp.getStatusCode())
                .as("POST /api/world/advance must return 200 (days=" + days + ")")
                .isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).as("advance response body must not be null").isNotNull();
        return (Map<String, Object>) resp.getBody();
    }

    /** Read a numeric field from the clock map as int. */
    private int intField(Map<String, Object> clock, String field) {
        Object val = clock.get(field);
        assertThat(val).as("clock response must contain field '" + field + "'").isNotNull();
        return ((Number) val).intValue();
    }

    /**
     * Reset the clock to a known absolute day by reading current value and
     * advancing to the next year boundary if needed.
     *
     * The simplest strategy for year-rollover test: advance to exactly
     * the start of the next year by computing the gap to the next
     * absoluteDay that is a multiple of 365.
     *
     * Returns the absoluteDay at which the clock was left after reset.
     */
    private int resetToYearStart() {
        Map<String, Object> current = getClock();
        int absDay = intField(current, "absoluteDay");
        // How many days to the next multiple of 365 (i.e., start of a new year)?
        // year boundaries: 0, 365, 730, ...
        // nextBoundary = ceil(absDay/365)*365 but if absDay==0 already skip
        int dayOfYear = intField(current, "dayOfYear");
        if (dayOfYear == 0) {
            // Already at a year start
            return absDay;
        }
        int toAdvance = 365 - dayOfYear;
        advance(toAdvance);
        Map<String, Object> after = getClock();
        assertThat(intField(after, "dayOfYear"))
                .as("After resetting, dayOfYear must be 0")
                .isEqualTo(0);
        return intField(after, "absoluteDay");
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    /**
     * getClock_returnsDate
     *
     * GET /api/world/clock must return a 200 with the four documented fields:
     * year (≥1), dayOfYear (0..364), absoluteDay (≥0), realSecondsPerSimDay (≥1).
     *
     * WORLD-CLOCK-SPEC §1.
     */
    @Test
    @DisplayName("getClock_returnsDate")
    void getClock_returnsDate() {
        Map<String, Object> clock = getClock();

        // All four spec fields must be present
        assertThat(clock).as("clock response must contain 'year'").containsKey("year");
        assertThat(clock).as("clock response must contain 'dayOfYear'").containsKey("dayOfYear");
        assertThat(clock).as("clock response must contain 'absoluteDay'").containsKey("absoluteDay");
        assertThat(clock).as("clock response must contain 'realSecondsPerSimDay'").containsKey("realSecondsPerSimDay");

        int year        = intField(clock, "year");
        int dayOfYear   = intField(clock, "dayOfYear");
        int absoluteDay = intField(clock, "absoluteDay");
        long rspsd      = ((Number) clock.get("realSecondsPerSimDay")).longValue();

        assertThat(year).as("year must be >= 1").isGreaterThanOrEqualTo(1);
        assertThat(dayOfYear).as("dayOfYear must be in [0,364]")
                .isGreaterThanOrEqualTo(0).isLessThanOrEqualTo(364);
        assertThat(absoluteDay).as("absoluteDay must be >= 0").isGreaterThanOrEqualTo(0);
        assertThat(rspsd).as("realSecondsPerSimDay must be >= 1").isGreaterThanOrEqualTo(1L);

        // Verify the date math: absoluteDay = (year-1)*365 + dayOfYear
        int expectedAbsolute = (year - 1) * 365 + dayOfYear;
        assertThat(absoluteDay)
                .as("absoluteDay must equal (year-1)*365 + dayOfYear per WORLD-CLOCK-SPEC §1")
                .isEqualTo(expectedAbsolute);
    }

    /**
     * advance_incrementsDay
     *
     * POST /api/world/advance { days: 5 } must increment dayOfYear by 5 and
     * absoluteDay by 5 (within the same year, so no rollover).
     *
     * WORLD-CLOCK-SPEC §1: advanceDays(n).
     */
    @Test
    @DisplayName("advance_incrementsDay")
    void advance_incrementsDay() {
        // Read current state
        Map<String, Object> before = getClock();
        int absBefore  = intField(before, "absoluteDay");
        int dayBefore  = intField(before, "dayOfYear");
        int yearBefore = intField(before, "year");

        // If we're too close to year end, advance to a safe position first
        // (we need at least 5 more days in the current year)
        if (dayBefore > 359) {
            // Jump to next year start
            advance(365 - dayBefore);
            before     = getClock();
            absBefore  = intField(before, "absoluteDay");
            dayBefore  = intField(before, "dayOfYear");
            yearBefore = intField(before, "year");
        }

        // Advance 5 days
        Map<String, Object> after = advance(5);

        int absAfter  = intField(after, "absoluteDay");
        int dayAfter  = intField(after, "dayOfYear");
        int yearAfter = intField(after, "year");

        assertThat(absAfter)
                .as("absoluteDay must increase by 5 after advance(5)")
                .isEqualTo(absBefore + 5);

        // dayOfYear arithmetic: same year case (dayBefore <= 359)
        assertThat(dayAfter)
                .as("dayOfYear must increase by 5 (still same year)")
                .isEqualTo(dayBefore + 5);

        assertThat(yearAfter)
                .as("year must not change for a 5-day advance within the same year")
                .isEqualTo(yearBefore);
    }

    /**
     * advance_rollsOverYear
     *
     * POST /api/world/advance { days: 365 } from a known year-start position
     * must increment year by 1 and leave dayOfYear at 0.
     *
     * absoluteDay rule: absoluteDay = (year-1)*365 + dayOfYear.
     * 365 days from absoluteDay=k where dayOfYear=0:
     *   new absoluteDay = k + 365
     *   new year        = k/365 + 2  (since k was a multiple of 365)
     *   new dayOfYear   = 0
     *
     * WORLD-CLOCK-SPEC §1.
     */
    @Test
    @DisplayName("advance_rollsOverYear")
    void advance_rollsOverYear() {
        // Advance to a clean year-start so we can reason exactly
        int absAtStart = resetToYearStart();
        Map<String, Object> atStart = getClock();
        int yearAtStart = intField(atStart, "year");

        assertThat(intField(atStart, "dayOfYear"))
                .as("sanity: dayOfYear must be 0 at year start")
                .isEqualTo(0);

        // Advance exactly 365 days — should roll exactly to the next year
        Map<String, Object> after = advance(365);

        int absAfter  = intField(after, "absoluteDay");
        int dayAfter  = intField(after, "dayOfYear");
        int yearAfter = intField(after, "year");

        assertThat(absAfter)
                .as("absoluteDay must increase by 365")
                .isEqualTo(absAtStart + 365);

        assertThat(yearAfter)
                .as("year must increment by 1 after advancing 365 days from a year start")
                .isEqualTo(yearAtStart + 1);

        assertThat(dayAfter)
                .as("dayOfYear must wrap to 0 after a full 365-day advance from day 0")
                .isEqualTo(0);
    }
}
