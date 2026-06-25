package com.game.world.clock;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * World clock REST endpoints — permitted without auth (/api/world/** is
 * already in SecurityConfig's permitAll list; no SecurityConfig change needed).
 *
 * <ul>
 *   <li>GET  /api/world/clock   — current clock state</li>
 *   <li>POST /api/world/advance — advance N sim-days (dev/test only)</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/world")
public class WorldClockController {

    private final WorldClockService clockService;

    public WorldClockController(WorldClockService clockService) {
        this.clockService = clockService;
    }

    // ── GET /api/world/clock ──────────────────────────────────────────────────

    /**
     * Returns the current world time.
     *
     * <p>Response body:
     * <pre>{@code
     * {
     *   "year":                 1,
     *   "dayOfYear":            0,
     *   "absoluteDay":          0,
     *   "realSecondsPerSimDay": 30
     * }
     * }</pre>
     */
    @GetMapping("/clock")
    public ResponseEntity<ClockResponse> clock() {
        return ResponseEntity.ok(buildResponse());
    }

    // ── POST /api/world/advance ───────────────────────────────────────────────

    /**
     * Advances the world clock by {@code days} sim-days.
     *
     * <p>Request body: {@code { "days": <int> }}<br>
     * Response body: the new clock state (same shape as GET /clock).
     */
    @PostMapping("/advance")
    public ResponseEntity<ClockResponse> advance(@RequestBody AdvanceRequest request) {
        int days = request.getDays();
        if (days < 0) {
            return ResponseEntity.badRequest().build();
        }
        if (days > 0) {
            clockService.advanceDays(days);
        }
        return ResponseEntity.ok(buildResponse());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ClockResponse buildResponse() {
        return new ClockResponse(
                clockService.currentYear(),
                clockService.currentDayOfYear(),
                clockService.currentAbsoluteDay(),
                clockService.realSecondsPerSimDay()
        );
    }

    // ── Nested DTOs ───────────────────────────────────────────────────────────

    /** Response DTO for both GET /clock and POST /advance. */
    public static final class ClockResponse {
        private final int year;
        private final int dayOfYear;
        private final int absoluteDay;
        private final long realSecondsPerSimDay;

        public ClockResponse(int year, int dayOfYear, int absoluteDay, long realSecondsPerSimDay) {
            this.year                 = year;
            this.dayOfYear            = dayOfYear;
            this.absoluteDay          = absoluteDay;
            this.realSecondsPerSimDay = realSecondsPerSimDay;
        }

        public int  getYear()                  { return year; }
        public int  getDayOfYear()             { return dayOfYear; }
        public int  getAbsoluteDay()           { return absoluteDay; }
        public long getRealSecondsPerSimDay()  { return realSecondsPerSimDay; }
    }

    /** Request DTO for POST /advance. */
    public static final class AdvanceRequest {
        private int days;

        public AdvanceRequest() {}

        public AdvanceRequest(int days) { this.days = days; }

        public int getDays()           { return days; }
        public void setDays(int days)  { this.days = days; }
    }
}
