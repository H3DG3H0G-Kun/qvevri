package com.game.contest;

import jakarta.persistence.*;

/**
 * Persistent record of a timed wine competition.
 *
 * <p>Contest lifecycle:
 * <ul>
 *   <li>OPEN    — accepting entries; currentDay &lt; endDay.</li>
 *   <li>JUDGED  — endDay has passed, entries ranked by qualityScore desc,
 *                 placement 1..n assigned, prize awarded to placement-1 winner.
 *                 Idempotent once JUDGED.</li>
 * </ul>
 *
 * <p>Table: {@code contests}. Migration: V22__contests.sql.
 *
 * <p>Column names avoid H2 / SQL reserved words:
 * {@code contest_status} (not status/state), {@code end_day} (not day),
 * {@code prize_gel} (not value/prize).
 *
 * <p>v1 note: prizeGel is NPC-funded — the creator is NOT debited at creation
 * time. This keeps v1 simple; a later pass will introduce funded prizes or
 * entry fees.
 */
@Entity
@Table(name = "contests",
       indexes = {
           @Index(name = "idx_contests_contest_status", columnList = "contest_status")
       })
public class Contest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Human-readable name of the competition. */
    @Column(nullable = false)
    private String name;

    /** Short description of the competition theme or rules. */
    @Column(nullable = false)
    private String description;

    /**
     * Absolute sim-day on which the contest closes.
     * Judging is triggered lazily when {@code currentDay >= endDay}.
     */
    @Column(name = "end_day", nullable = false)
    private long endDay;

    /**
     * Prize awarded to the placement-1 winner in GEL.
     * v1: NPC-funded (creator is not debited).
     */
    @Column(name = "prize_gel", nullable = false)
    private double prizeGel;

    /**
     * Lifecycle state: "OPEN" | "JUDGED".
     * Uses {@code contest_status} to avoid H2 reserved word {@code status}.
     */
    @Column(name = "contest_status", nullable = false)
    private String contestStatus;

    /** Epoch-ms creation timestamp. */
    @Column(name = "created_at", nullable = false)
    private long createdAt;

    // ── Constructors ──────────────────────────────────────────────────────────

    protected Contest() {}

    public Contest(String name, String description, long endDay, double prizeGel) {
        this.name          = name;
        this.description   = description;
        this.endDay        = endDay;
        this.prizeGel      = prizeGel;
        this.contestStatus = "OPEN";
        this.createdAt     = System.currentTimeMillis();
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public Long getId()              { return id; }
    public String getName()          { return name; }
    public String getDescription()   { return description; }
    public long getEndDay()          { return endDay; }
    public double getPrizeGel()      { return prizeGel; }
    public String getContestStatus() { return contestStatus; }
    public long getCreatedAt()       { return createdAt; }

    // ── Setters ───────────────────────────────────────────────────────────────

    public void setContestStatus(String contestStatus) { this.contestStatus = contestStatus; }
}
