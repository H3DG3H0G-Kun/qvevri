package com.game.guild;

import jakarta.persistence.*;

/**
 * Persistent record of a player wine-house guild.
 *
 * <p>Table: {@code guilds}. Column names avoid H2/SQL reserved words
 * (no {@code value}, {@code status}, {@code level}, {@code year}).
 *
 * <p>A guild is created by one character (the FOUNDER) and holds a shared
 * treasury denominated in GEL.
 */
@Entity
@Table(name = "guilds",
       indexes = @Index(name = "idx_guilds_name", columnList = "guild_name", unique = true))
public class Guild {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Display name of the guild. Unique across all guilds.
     * Column named {@code guild_name} to avoid potential reserved-word conflicts
     * on some H2/Postgres configurations; {@code name} is also fine in H2 but
     * the prefix adds clarity.
     */
    @Column(name = "guild_name", nullable = false, unique = true)
    private String name;

    /** FK → mmo_character.id of the founder. */
    @Column(name = "founder_character_id", nullable = false)
    private Long founderCharacterId;

    /**
     * Shared GEL treasury. Named {@code treasury_gel} to avoid the H2
     * reserved word {@code balance}.
     */
    @Column(name = "treasury_gel", nullable = false)
    private double treasuryGel = 0.0;

    /** Wall-clock epoch-ms timestamp of guild creation. */
    @Column(name = "created_at", nullable = false)
    private long createdAt;

    /** Required by JPA. */
    protected Guild() {}

    public Guild(String name, Long founderCharacterId, long createdAt) {
        this.name                = name;
        this.founderCharacterId  = founderCharacterId;
        this.treasuryGel         = 0.0;
        this.createdAt           = createdAt;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public Long   getId()                 { return id; }
    public String getName()               { return name; }
    public Long   getFounderCharacterId() { return founderCharacterId; }
    public double getTreasuryGel()        { return treasuryGel; }
    public long   getCreatedAt()          { return createdAt; }

    // ── Setters (treasury mutations only) ─────────────────────────────────────

    public void setTreasuryGel(double treasuryGel) {
        this.treasuryGel = treasuryGel;
    }
}
