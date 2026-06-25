package com.game.achievement;

import jakarta.persistence.*;

/**
 * Persistent record of a single character's unlocked achievement.
 *
 * <p>Table: {@code player_achievements}. Migration: V21__player_achievements.sql.
 *
 * <p>A unique constraint on (character_id, achievement_id) enforces the
 * idempotent unlock guard at the database level.
 *
 * <p>Column names avoid H2/SQL reserved words:
 * {@code achievement_id}, {@code unlocked_day}, {@code created_at} are all safe.
 * Avoided: {@code value}, {@code year}, {@code level}, {@code status}, {@code rank}.
 */
@Entity
@Table(
    name = "player_achievements",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_player_achievements_char_ach",
            columnNames = {"character_id", "achievement_id"})
    },
    indexes = {
        @Index(name = "idx_player_achievements_character_id", columnList = "character_id")
    }
)
public class PlayerAchievement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** FK → mmo_character.id — the owning character. */
    @Column(name = "character_id", nullable = false)
    private Long characterId;

    /** Stable reference to an {@link AchievementDefinition} in {@link AchievementCatalog}. */
    @Column(name = "achievement_id", nullable = false)
    private String achievementId;

    /**
     * Sim-day (absolute) when the achievement was unlocked.
     * Sourced from {@link com.game.world.clock.WorldClockService#currentAbsoluteDay()}.
     */
    @Column(name = "unlocked_day", nullable = false)
    private long unlockedDay;

    /** Epoch-ms wall-clock timestamp when the row was persisted. */
    @Column(name = "created_at", nullable = false)
    private long createdAt;

    // ── Constructors ──────────────────────────────────────────────────────────

    /** Required by JPA. */
    protected PlayerAchievement() {}

    /**
     * Creates a new unlocked PlayerAchievement.
     *
     * @param characterId   the owning character
     * @param achievementId stable catalog achievement id
     * @param unlockedDay   absolute sim-day from the world clock
     */
    public PlayerAchievement(Long characterId, String achievementId, long unlockedDay) {
        this.characterId   = characterId;
        this.achievementId = achievementId;
        this.unlockedDay   = unlockedDay;
        this.createdAt     = System.currentTimeMillis();
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public Long   getId()            { return id; }
    public Long   getCharacterId()   { return characterId; }
    public String getAchievementId() { return achievementId; }
    public long   getUnlockedDay()   { return unlockedDay; }
    public long   getCreatedAt()     { return createdAt; }
}
