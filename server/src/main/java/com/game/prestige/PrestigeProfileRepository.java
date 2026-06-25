package com.game.prestige;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Spring Data repository for {@link PrestigeProfile}.
 *
 * <p>The only custom finder needed is {@code findByCharacterId}, which
 * {@link PrestigeService} uses for lazy-create (getOrCreate) lookups.
 */
public interface PrestigeProfileRepository extends JpaRepository<PrestigeProfile, Long> {

    /**
     * Returns the prestige profile for the given character, or
     * {@link Optional#empty()} if none exists yet.
     *
     * @param characterId the character whose profile to look up
     * @return an optional wrapping the profile
     */
    Optional<PrestigeProfile> findByCharacterId(Long characterId);
}
