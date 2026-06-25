package com.game.world.clock;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manages the single persistent world clock row and drives autonomous time
 * progression via a @Scheduled ticker.
 *
 * <p><b>Date math</b> (deterministic, no java.time dependency):
 * <pre>
 *   absoluteDay = (year - 1) * 365 + dayOfYear
 *   year        = absoluteDay / 365 + 1
 *   dayOfYear   = absoluteDay % 365          (0..364)
 * </pre>
 *
 * <p><b>Clock rate</b>: {@code world.real-seconds-per-sim-day} controls how
 * many real seconds correspond to one simulation day. The scheduler fires
 * every 5 s, computes whole elapsed sim-days since {@code lastAdvanceEpochMs},
 * and calls {@link #advanceDays(int)} to catch-up (self-corrects after
 * downtime). When {@code realSecondsPerSimDay} is very large (test profile)
 * integer division yields 0 and the clock never auto-advances.
 */
@Service
public class WorldClockService {

    /** Singleton row id. */
    private static final long CLOCK_ID = 1L;

    private final WorldClockRepository repo;

    /**
     * Real seconds that must elapse for one simulation day to pass.
     * Default (application.properties): 30 s → 1 sim-day every 30 s.
     * Test profile: 86400000 s → clock effectively frozen.
     */
    private final long realSecondsPerSimDay;

    // ── Constructor injection ─────────────────────────────────────────────────

    public WorldClockService(
            WorldClockRepository repo,
            @Value("${world.real-seconds-per-sim-day}") long realSecondsPerSimDay) {
        this.repo = repo;
        this.realSecondsPerSimDay = realSecondsPerSimDay;
    }

    // ── Public API (WA2 contract) ─────────────────────────────────────────────

    /**
     * Returns the current absolute day (0-based).
     * {@code absoluteDay = (year - 1) * 365 + dayOfYear}
     */
    @Transactional
    public int currentAbsoluteDay() {
        return (int) loadOrSeed().getCurrentAbsoluteDay();
    }

    /**
     * Returns the current simulation year (1-based).
     * {@code year = absoluteDay / 365 + 1}
     */
    @Transactional
    public int currentYear() {
        long abs = loadOrSeed().getCurrentAbsoluteDay();
        return (int) (abs / 365) + 1;
    }

    /**
     * Returns the current day-of-year within the current simulation year
     * (0-based, range 0..364).
     * {@code dayOfYear = absoluteDay % 365}
     */
    @Transactional
    public int currentDayOfYear() {
        long abs = loadOrSeed().getCurrentAbsoluteDay();
        return (int) (abs % 365);
    }

    /**
     * Advances the clock by {@code n} sim-days (n must be &ge; 0), persists
     * the new value, and records the advance timestamp.
     *
     * @param n number of sim-days to add; ignored if &lt; 1
     */
    @Transactional
    public void advanceDays(int n) {
        if (n < 1) {
            // Still update lastAdvanceEpochMs so the scheduler drift resets
            // only when called with n==0 internally; for external callers
            // (POST /advance) we only update if n > 0 to avoid drift resets.
            return;
        }
        WorldClock clock = loadOrSeed();
        clock.setCurrentAbsoluteDay(clock.getCurrentAbsoluteDay() + n);
        clock.setLastAdvanceEpochMs(System.currentTimeMillis());
        repo.save(clock);
    }

    /**
     * Exposes the configured rate to callers (e.g. the controller DTO).
     */
    public long realSecondsPerSimDay() {
        return realSecondsPerSimDay;
    }

    // ── Scheduler ────────────────────────────────────────────────────────────

    /**
     * Fires every 5 s and auto-advances the world clock by however many whole
     * sim-days of real time have elapsed since the last advance.
     *
     * <p>Uses {@code fixedDelay} (not {@code fixedRate}) so ticks never pile
     * up under load. The division by {@code realSecondsPerSimDay} ensures the
     * clock self-corrects after server downtime without skipping extra ticks.
     *
     * <p>When the test profile sets {@code world.real-seconds-per-sim-day} to
     * a huge number (86400000) the integer division always yields 0 and the
     * scheduler is a no-op — tests control time via POST /api/world/advance.
     */
    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void tick() {
        WorldClock clock = loadOrSeed();
        long nowMs = System.currentTimeMillis();
        long elapsedMs = nowMs - clock.getLastAdvanceEpochMs();
        long elapsedRealSeconds = elapsedMs / 1000L;

        // Guard: realSecondsPerSimDay must be at least 1 to avoid division by zero
        if (realSecondsPerSimDay < 1) {
            return;
        }

        int simDaysElapsed = (int) (elapsedRealSeconds / realSecondsPerSimDay);
        if (simDaysElapsed < 1) {
            return;
        }

        clock.setCurrentAbsoluteDay(clock.getCurrentAbsoluteDay() + simDaysElapsed);
        clock.setLastAdvanceEpochMs(nowMs);
        repo.save(clock);
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    /**
     * Loads the singleton clock row, seeding it if it does not yet exist.
     * Seed: year 1, day 0 → absoluteDay = 0.
     */
    private WorldClock loadOrSeed() {
        return repo.findById(CLOCK_ID).orElseGet(() -> {
            WorldClock seed = new WorldClock(CLOCK_ID, 0L, System.currentTimeMillis());
            return repo.save(seed);
        });
    }
}
