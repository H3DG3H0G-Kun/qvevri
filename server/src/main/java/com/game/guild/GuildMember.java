package com.game.guild;

import jakarta.persistence.*;

/**
 * Persistent record of a character's membership in a {@link Guild}.
 *
 * <p>Table: {@code guild_members}. A character may belong to at most one guild
 * at any time (enforced by the UNIQUE index on {@code character_id}).
 *
 * <p>Column {@code guild_role} stores the member's role: {@code "FOUNDER"} or
 * {@code "MEMBER"}. The word "role" itself is not reserved in standard SQL but
 * is avoided in column position to prevent ambiguity; {@code guild_role} is
 * unambiguous.
 */
@Entity
@Table(name = "guild_members",
       indexes = @Index(name = "idx_guild_members_character_id",
                        columnList = "character_id",
                        unique = true))
public class GuildMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** FK → guilds.id. */
    @Column(name = "guild_id", nullable = false)
    private Long guildId;

    /**
     * FK → mmo_character.id. UNIQUE — a character is in at most one guild.
     */
    @Column(name = "character_id", nullable = false, unique = true)
    private Long characterId;

    /**
     * Role within the guild: {@code "FOUNDER"} or {@code "MEMBER"}.
     * Column named {@code guild_role} (not {@code role}) to avoid the SQL
     * reserved word and H2 parse ambiguity.
     */
    @Column(name = "guild_role", nullable = false)
    private String guildRole;

    /** Wall-clock epoch-ms timestamp of when the character joined. */
    @Column(name = "joined_at", nullable = false)
    private long joinedAt;

    /** Required by JPA. */
    protected GuildMember() {}

    public GuildMember(Long guildId, Long characterId, String guildRole, long joinedAt) {
        this.guildId     = guildId;
        this.characterId = characterId;
        this.guildRole   = guildRole;
        this.joinedAt    = joinedAt;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public Long   getId()          { return id; }
    public Long   getGuildId()     { return guildId; }
    public Long   getCharacterId() { return characterId; }
    public String getGuildRole()   { return guildRole; }
    public long   getJoinedAt()    { return joinedAt; }
}
