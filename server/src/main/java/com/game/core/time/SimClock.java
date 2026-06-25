package com.game.core.time;

import com.game.core.data.GameDate;

/**
 * Fixed 1-day step simulation clock.
 * A year is day-of-year 0..364 (365 days total).
 * No wall-clock time — pure simulation time.
 *
 * <p>Frozen seam per SIM-SPEC §3.1.
 */
public final class SimClock {

    /** Number of simulation days per year. */
    public static final int DAYS_PER_YEAR = 365;

    private final int year;
    private int dayOfYear;

    public SimClock(int year) {
        this.year = year;
        this.dayOfYear = 0;
    }

    /** Returns the current {@link GameDate} without advancing time. */
    public GameDate date() {
        return new GameDate(year, dayOfYear);
    }

    /**
     * Advances the clock by one day and returns the new {@link GameDate}.
     * Day wraps: after day 364 the next call returns day 0 of the same year
     * (the harness should stop before this happens).
     */
    public GameDate advanceDay() {
        dayOfYear = (dayOfYear + 1) % DAYS_PER_YEAR;
        return new GameDate(year, dayOfYear);
    }
}
