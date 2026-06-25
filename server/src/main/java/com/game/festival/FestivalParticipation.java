package com.game.festival;

import jakarta.persistence.*;

/**
 * Persistent record of a single character's participation in a festival event.
 *
 * <p>Table: {@code festival_participations}. Migration: V19__festival_participations.sql.
 *
 * <p>One row per (characterId, festivalId, seasonYear) combination — enforced by
 * a unique index on those three columns. The service checks for an existing row
 * before inserting and throws ALREADY_PARTICIPATED (400) on a duplicate attempt.
 *
 * <p>Column names avoid H2/SQL reserved words:
 * <ul>
 *   <li>{@code season_year} (not {@code year})</li>
 *   <li>{@code festival_id} (not {@code status})</li>
 *   <li>{@code created_at} epoch-ms long</li>
 * </ul>
 */
@Entity
@Table(name = "festival_participations",
       indexes = {
           @Index(name = "idx_festival_participations_lookup",
                  columnList = "character_id, festival_id, season_year")
       })
public class FestivalParticipation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** FK → mmo_character.id — the participating character. */
    @Column(name = "character_id", nullable = false)
    private Long characterId;

    /** Stable reference to a {@link FestivalDefinition} in {@link FestivalCalendar}. */
    @Column(name = "festival_id", nullable = false)
    private String festivalId;

    /**
     * The simulation year in which the participation occurred.
     * Column named {@code season_year} to avoid H2 reserved word {@code year}.
     */
    @Column(name = "season_year", nullable = false)
    private int seasonYear;

    /**
     * Always {@code true} once the row is created — the reward has been granted
     * and the participation has been claimed. Kept for future extensibility (e.g.
     * multi-step claim flows).
     */
    @Column(name = "claimed", nullable = false)
    private boolean claimed;

    /** Epoch-ms timestamp when this participation was recorded. */
    @Column(name = "created_at", nullable = false)
    private long createdAt;

    // ── Constructors ──────────────────────────────────────────────────────────

    /** Required by JPA. */
    protected FestivalParticipation() {}

    /**
     * Creates a new claimed participation record.
     *
     * @param characterId the participating character
     * @param festivalId  stable catalog festival id
     * @param seasonYear  simulation year of participation
     */
    public FestivalParticipation(Long characterId, String festivalId, int seasonYear) {
        this.characterId = characterId;
        this.festivalId  = festivalId;
        this.seasonYear  = seasonYear;
        this.claimed     = true;
        this.createdAt   = System.currentTimeMillis();
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public Long   getId()          { return id; }
    public Long   getCharacterId() { return characterId; }
    public String getFestivalId()  { return festivalId; }
    public int    getSeasonYear()  { return seasonYear; }
    public boolean isClaimed()     { return claimed; }
    public long   getCreatedAt()   { return createdAt; }
}
