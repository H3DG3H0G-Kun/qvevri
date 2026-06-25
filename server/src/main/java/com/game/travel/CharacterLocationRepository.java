package com.game.travel;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Spring Data repository for {@link CharacterLocation}.
 *
 * <p>Primary access pattern: look up a character's location by their id.
 * The {@code character_id} column has a UNIQUE constraint so this query
 * returns at most one row.
 */
public interface CharacterLocationRepository extends JpaRepository<CharacterLocation, Long> {

    /**
     * Finds a character's location record, if one exists.
     *
     * @param characterId the character to look up
     * @return Optional containing the location, or empty if not yet seeded
     */
    Optional<CharacterLocation> findByCharacterId(Long characterId);
}
