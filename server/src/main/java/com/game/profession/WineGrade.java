package com.game.profession;

import jakarta.persistence.*;

/**
 * Persists an enologist's grade/certification for a {@link com.game.market.CellarItem}.
 *
 * <p>Table: {@code wine_grades}.
 *
 * <p>Score algorithm (deterministic, no RNG):
 * <ol>
 *   <li>Base: {@code CellarItem.quality} clamped to [0..100].</li>
 *   <li>Appellation bonus: +5 if {@code appellationOk}.</li>
 *   <li>Certified: {@code score >= 85}.</li>
 * </ol>
 *
 * <p>WinePricer integration for certified premium is a §6 deferred follow-up.
 */
@Entity
@Table(name = "wine_grades")
public class WineGrade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** FK → cellar_items.id — the bottle/batch being graded. */
    @Column(name = "cellar_item_id", nullable = false)
    private Long cellarItemId;

    /** FK → mmo_character.id — the ENOLOGIST who issued the grade. */
    @Column(name = "grader_character_id", nullable = false)
    private Long graderCharacterId;

    /** Computed quality score 0..105 (up to +5 appellation bonus). */
    @Column(name = "score", nullable = false)
    private double score;

    /** True when {@code score >= 85}. */
    @Column(name = "certified", nullable = false)
    private boolean certified;

    /** Epoch-ms creation timestamp. */
    @Column(name = "created_at", nullable = false)
    private long createdAt;

    protected WineGrade() {}

    public WineGrade(Long cellarItemId, Long graderCharacterId, double score, boolean certified) {
        this.cellarItemId       = cellarItemId;
        this.graderCharacterId  = graderCharacterId;
        this.score              = score;
        this.certified          = certified;
        this.createdAt          = System.currentTimeMillis();
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public Long getId()                  { return id; }
    public Long getCellarItemId()        { return cellarItemId; }
    public Long getGraderCharacterId()   { return graderCharacterId; }
    public double getScore()             { return score; }
    public boolean isCertified()         { return certified; }
    public long getCreatedAt()           { return createdAt; }
}
