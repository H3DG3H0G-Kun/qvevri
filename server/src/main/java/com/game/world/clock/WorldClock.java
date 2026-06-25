package com.game.world.clock;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Single persisted row (id = 1) that tracks the shared world time.
 *
 * <p>absoluteDay = (year - 1) * 365 + dayOfYear, where year >= 1 and
 * dayOfYear is in 0..364. Year 1, day 0 → absoluteDay = 0.
 *
 * <p>Table name: mmo_world_clock (avoids conflict with any reserved keywords).
 */
@Entity
@Table(name = "mmo_world_clock")
public class WorldClock {

    /** Always 1 — there is exactly one world clock. */
    @Id
    private long id;

    /** Monotonically increasing absolute day counter. Starts at 0. */
    @Column(nullable = false)
    private long currentAbsoluteDay;

    /**
     * Epoch-ms timestamp of the last time {@code currentAbsoluteDay} was
     * advanced (or the row was first created). Used by the scheduler to
     * compute catch-up sim-days after a downtime.
     */
    @Column(nullable = false)
    private long lastAdvanceEpochMs;

    // ── Constructors ─────────────────────────────────────────────────────────

    /** JPA no-arg constructor. */
    protected WorldClock() {}

    /** Creates the seed row: year 1, day 0 (absoluteDay = 0). */
    public WorldClock(long id, long currentAbsoluteDay, long lastAdvanceEpochMs) {
        this.id = id;
        this.currentAbsoluteDay = currentAbsoluteDay;
        this.lastAdvanceEpochMs = lastAdvanceEpochMs;
    }

    // ── Getters / Setters ────────────────────────────────────────────────────

    public long getId()                  { return id; }
    public long getCurrentAbsoluteDay()  { return currentAbsoluteDay; }
    public long getLastAdvanceEpochMs()  { return lastAdvanceEpochMs; }

    public void setCurrentAbsoluteDay(long currentAbsoluteDay) {
        this.currentAbsoluteDay = currentAbsoluteDay;
    }

    public void setLastAdvanceEpochMs(long lastAdvanceEpochMs) {
        this.lastAdvanceEpochMs = lastAdvanceEpochMs;
    }
}
