package com.game.progression;

import jakarta.persistence.*;

/**
 * Persistent per-character XP, level, and reputation record.
 *
 * <p>Level curve (deterministic): {@code xpLevel = floor(sqrt(xp / 100.0)) + 1}
 * <ul>
 *   <li>xp=0   → level 1  (floor(sqrt(0))   + 1 = 1)</li>
 *   <li>xp=100 → level 2  (floor(sqrt(1.0))  + 1 = 2)</li>
 *   <li>xp=400 → level 3  (floor(sqrt(4.0))  + 1 = 3)</li>
 *   <li>xp=900 → level 4  (floor(sqrt(9.0))  + 1 = 4)</li>
 *   <li>xp=2500 → level 6 (floor(sqrt(25.0)) + 1 = 6)</li>
 * </ul>
 *
 * <p>Table: {@code progression_profiles}. Migration: V9__progression.sql.
 * H2 dev/test uses ddl-auto=update (Flyway disabled in dev/test profile).
 */
@Entity
@Table(name = "progression_profiles",
       indexes = {
           @Index(name = "idx_progression_profiles_character_id", columnList = "character_id", unique = true)
       })
public class ProgressionProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * FK → mmo_character.id. One profile per character; unique constraint enforced
     * at DB level by the index above.
     */
    @Column(name = "character_id", nullable = false, unique = true)
    private Long characterId;

    /** Total experience points accumulated. Always >= 0. */
    @Column(nullable = false)
    private long xp;

    /**
     * Derived level — always kept in sync by {@link ProgressionService#awardXp}.
     * Formula: floor(sqrt(xp / 100.0)) + 1. Minimum value: 1.
     */
    @Column(name = "xp_level", nullable = false)
    private int xpLevel;

    /**
     * Reputation score — can be negative (disreputable) or positive.
     * Adjusted via {@link ProgressionService#adjustReputation}.
     */
    @Column(nullable = false)
    private int reputation;

    /** Epoch-ms timestamp of last modification. */
    @Column(name = "updated_at", nullable = false)
    private long updatedAt;

    // ── Constructors ──────────────────────────────────────────────────────────

    /** JPA no-arg constructor. */
    protected ProgressionProfile() {}

    /**
     * Creates a brand-new profile at xp=0 / level=1 / reputation=0.
     *
     * @param characterId owning character id
     */
    public ProgressionProfile(Long characterId) {
        this.characterId = characterId;
        this.xp          = 0L;
        this.xpLevel     = 1;
        this.reputation  = 0;
        this.updatedAt   = System.currentTimeMillis();
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public Long getId()          { return id; }
    public Long getCharacterId() { return characterId; }
    public long getXp()          { return xp; }
    public int  getXpLevel()     { return xpLevel; }
    public int  getReputation()  { return reputation; }
    public long getUpdatedAt()   { return updatedAt; }

    // ── Package-private mutators (used only by ProgressionService) ────────────

    void setXp(long xp)             { this.xp = xp; }
    void setXpLevel(int xpLevel)    { this.xpLevel = xpLevel; }
    void setReputation(int rep)     { this.reputation = rep; }
    void setUpdatedAt(long ts)      { this.updatedAt = ts; }
}
