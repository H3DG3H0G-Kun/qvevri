package com.game.guild;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data repository for {@link GuildMember} entities.
 */
public interface GuildMemberRepository extends JpaRepository<GuildMember, Long> {

    /**
     * Finds the membership record for the given character, if any.
     * Because a character is in at most one guild, this returns at most one row.
     *
     * @param characterId the character to look up
     * @return the membership record, or empty if not in any guild
     */
    Optional<GuildMember> findByCharacterId(Long characterId);

    /**
     * Returns all members of the given guild.
     *
     * @param guildId the guild id
     * @return list of member records (may be empty)
     */
    List<GuildMember> findByGuildId(Long guildId);
}
