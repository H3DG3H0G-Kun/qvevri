package com.game.skill;

import jakarta.persistence.*;

/**
 * Persistent record of a single talent learned by a character.
 *
 * <p>Table: {@code learned_skills}. Migration: V27__skills.sql.
 *
 * <p>A (characterId, skillId) pair is unique — a talent cannot be learned twice.
 *
 * <p>Column-name notes (H2 reserved-word avoidance):
 * <ul>
 *   <li>{@code skill_id}    — avoids the reserved word {@code id} as prefix collision;
 *                             "skill_id" is safe in both H2 and Postgres.</li>
 *   <li>{@code learned_at}  — epoch-ms wall-clock timestamp; "learned_at" is safe in H2.</li>
 * </ul>
 */
@Entity
@Table(name = "learned_skills",
       uniqueConstraints = {
           @UniqueConstraint(name = "uq_learned_skills_char_skill",
                             columnNames = {"character_id", "skill_id"})
       },
       indexes = {
           @Index(name = "idx_learned_skills_character_id", columnList = "character_id")
       })
public class LearnedSkill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** FK → mmo_character.id — the owning character. */
    @Column(name = "character_id", nullable = false)
    private Long characterId;

    /** Stable reference to a {@link SkillTalent} in {@link SkillCatalog}. */
    @Column(name = "skill_id", nullable = false)
    private String skillId;

    /**
     * Epoch-ms wall-clock timestamp when the talent was learned.
     * Column {@code learned_at} is H2-safe.
     */
    @Column(name = "learned_at", nullable = false)
    private long learnedAt;

    // ── Constructors ──────────────────────────────────────────────────────────

    /** Required by JPA. */
    protected LearnedSkill() {}

    /**
     * Creates a new learned-skill record stamped at the current wall-clock time.
     *
     * @param characterId owning character
     * @param skillId     stable catalog talent id
     */
    public LearnedSkill(Long characterId, String skillId) {
        this.characterId = characterId;
        this.skillId     = skillId;
        this.learnedAt   = System.currentTimeMillis();
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public Long   getId()          { return id; }
    public Long   getCharacterId() { return characterId; }
    public String getSkillId()     { return skillId; }
    public long   getLearnedAt()   { return learnedAt; }
}
