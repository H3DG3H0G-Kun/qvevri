package com.game.guild;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Spring Data repository for {@link Guild} entities.
 */
public interface GuildRepository extends JpaRepository<Guild, Long> {

    /**
     * Looks up a guild by its unique display name.
     *
     * @param name the guild name to search for
     * @return the guild if it exists, or empty
     */
    Optional<Guild> findByName(String name);
}
