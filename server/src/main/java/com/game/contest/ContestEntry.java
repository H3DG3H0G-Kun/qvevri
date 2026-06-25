package com.game.contest;

import jakarta.persistence.*;

/**
 * One character's entry in a {@link Contest}.
 *
 * <p>Table: {@code contest_entries}. Migration: V22__contests.sql.
 *
 * <p>Column naming — H2/SQL reserved-word avoidance:
 * {@code quality_score}  (not quality), {@code contest_id}, {@code cellar_item_id},
 * {@code character_id}, {@code created_at}.
 * {@code placement} is nullable until judging; {@code Integer} (boxed) so JPA
 * maps it as a nullable column.
 */
@Entity
@Table(name = "contest_entries",
       indexes = {
           @Index(name = "idx_contest_entries_contest_id",    columnList = "contest_id"),
           @Index(name = "idx_contest_entries_character_id",  columnList = "character_id")
       })
public class ContestEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** FK → contests.id. */
    @Column(name = "contest_id", nullable = false)
    private Long contestId;

    /** FK → mmo_character.id (the entrant). */
    @Column(name = "character_id", nullable = false)
    private Long characterId;

    /** FK → cellar_items.id (the submitted bottle). */
    @Column(name = "cellar_item_id", nullable = false)
    private Long cellarItemId;

    /**
     * Snapshot of {@code CellarItem.quality} at entry time.
     * Deterministic judging uses this value — post-entry quality changes
     * to the original item do not affect the contest result.
     */
    @Column(name = "quality_score", nullable = false)
    private double qualityScore;

    /**
     * Placement after judging (1 = best). Null until the contest is JUDGED.
     * Boxed {@link Integer} so JPA stores NULL before judging.
     */
    @Column(name = "placement")
    private Integer placement;

    /** Epoch-ms creation timestamp. */
    @Column(name = "created_at", nullable = false)
    private long createdAt;

    // ── Constructors ──────────────────────────────────────────────────────────

    protected ContestEntry() {}

    public ContestEntry(Long contestId, Long characterId, Long cellarItemId, double qualityScore) {
        this.contestId    = contestId;
        this.characterId  = characterId;
        this.cellarItemId = cellarItemId;
        this.qualityScore = qualityScore;
        this.placement    = null;
        this.createdAt    = System.currentTimeMillis();
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public Long getId()            { return id; }
    public Long getContestId()     { return contestId; }
    public Long getCharacterId()   { return characterId; }
    public Long getCellarItemId()  { return cellarItemId; }
    public double getQualityScore(){ return qualityScore; }
    public Integer getPlacement()  { return placement; }
    public long getCreatedAt()     { return createdAt; }

    // ── Setters ───────────────────────────────────────────────────────────────

    public void setPlacement(Integer placement) { this.placement = placement; }
}
