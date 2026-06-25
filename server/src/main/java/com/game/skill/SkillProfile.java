package com.game.skill;

import jakarta.persistence.*;

/**
 * Persistent record of a character's skill point pool.
 *
 * <p>Table: {@code skill_profiles}. Migration: V27__skills.sql.
 *
 * <p>Lazy-created on first access with {@code totalPoints = 5}, {@code spentPoints = 0}.
 * Earning additional points by leveling ties into the progression lane (deferred).
 *
 * <p>Column-name notes (H2 reserved-word avoidance):
 * <ul>
 *   <li>{@code total_points}  — avoids the reserved word {@code points}.</li>
 *   <li>{@code spent_points}  — avoids the reserved word {@code points}.</li>
 *   <li>{@code created_at}    — epoch-ms wall-clock timestamp.</li>
 * </ul>
 */
@Entity
@Table(name = "skill_profiles",
       uniqueConstraints = {
           @UniqueConstraint(name = "uq_skill_profiles_character_id",
                             columnNames = {"character_id"})
       },
       indexes = {
           @Index(name = "idx_skill_profiles_character_id", columnList = "character_id")
       })
public class SkillProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** FK → mmo_character.id — the owning character. */
    @Column(name = "character_id", nullable = false)
    private Long characterId;

    /**
     * Total skill points granted to this character.
     * Starts at 5 (STARTING_POINTS). Additional points come from leveling (deferred).
     * Column {@code total_points} avoids H2 reserved word {@code points}.
     */
    @Column(name = "total_points", nullable = false)
    private int totalPoints;

    /**
     * Skill points already spent on learned talents.
     * Column {@code spent_points} avoids H2 reserved word {@code points}.
     */
    @Column(name = "spent_points", nullable = false)
    private int spentPoints;

    /** Epoch-ms wall-clock timestamp of row creation. */
    @Column(name = "created_at", nullable = false)
    private long createdAt;

    // ── Constructors ──────────────────────────────────────────────────────────

    /** Required by JPA. */
    protected SkillProfile() {}

    /**
     * Creates a new SkillProfile with the given starting point pool.
     *
     * @param characterId  owning character
     * @param totalPoints  starting total point grant (typically 5)
     */
    public SkillProfile(Long characterId, int totalPoints) {
        this.characterId  = characterId;
        this.totalPoints  = totalPoints;
        this.spentPoints  = 0;
        this.createdAt    = System.currentTimeMillis();
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public Long getId()          { return id; }
    public Long getCharacterId() { return characterId; }
    public int  getTotalPoints() { return totalPoints; }
    public int  getSpentPoints() { return spentPoints; }
    public long getCreatedAt()   { return createdAt; }

    // ── Setters ───────────────────────────────────────────────────────────────

    public void setTotalPoints(int totalPoints)   { this.totalPoints  = totalPoints; }
    public void setSpentPoints(int spentPoints)   { this.spentPoints  = spentPoints; }
}
