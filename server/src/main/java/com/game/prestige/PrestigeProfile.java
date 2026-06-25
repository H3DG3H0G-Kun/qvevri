package com.game.prestige;

import jakarta.persistence.*;

/**
 * Persistent per-character prestige record.
 *
 * <p>Prestige represents the character's social standing in the QVEVRI world.
 * The current title ({@link #getTitleRank()}) is always the highest entry in
 * {@link TitleLadder} whose threshold is &lt;= the character's total prestige.
 *
 * <p>Table: {@code prestige_profiles}. Migration: V28__prestige_profiles.sql.
 * H2 dev/test uses ddl-auto=update (Flyway disabled in dev/test profile).
 *
 * <p>Column naming avoids H2/SQL reserved words:
 * <ul>
 *   <li>{@code title_rank} — not {@code rank} or {@code title}</li>
 *   <li>{@code updated_at} — epoch-ms wall clock</li>
 *   <li>{@code prestige} — safe in H2</li>
 * </ul>
 */
@Entity
@Table(name = "prestige_profiles",
       indexes = {
           @Index(name = "idx_prestige_profiles_character_id", columnList = "character_id", unique = true)
       })
public class PrestigeProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * FK → mmo_character.id. One profile per character; unique constraint
     * enforced at DB level by the index above.
     */
    @Column(name = "character_id", nullable = false, unique = true)
    private Long characterId;

    /** Total prestige points accumulated. Always &gt;= 0. */
    @Column(nullable = false)
    private long prestige;

    /**
     * Current title name — derived from {@link TitleLadder#titleFor(long)}.
     * Always kept in sync by {@link PrestigeService#awardPrestige}.
     */
    @Column(name = "title_rank", nullable = false, length = 32)
    private String titleRank;

    /** Epoch-ms timestamp of last modification. */
    @Column(name = "updated_at", nullable = false)
    private long updatedAt;

    // ── Constructors ──────────────────────────────────────────────────────────

    /** JPA no-arg constructor. */
    protected PrestigeProfile() {}

    /**
     * Creates a brand-new profile at prestige=0 / title=GLEKHI.
     *
     * @param characterId owning character id
     */
    public PrestigeProfile(Long characterId) {
        this.characterId = characterId;
        this.prestige    = 0L;
        this.titleRank   = TitleLadder.titleFor(0L).title();
        this.updatedAt   = System.currentTimeMillis();
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public Long   getId()          { return id; }
    public Long   getCharacterId() { return characterId; }
    public long   getPrestige()    { return prestige; }
    public String getTitleRank()   { return titleRank; }
    public long   getUpdatedAt()   { return updatedAt; }

    // ── Package-private mutators (used only by PrestigeService) ──────────────

    void setPrestige(long prestige)       { this.prestige  = prestige; }
    void setTitleRank(String titleRank)   { this.titleRank = titleRank; }
    void setUpdatedAt(long ts)            { this.updatedAt = ts; }
}
