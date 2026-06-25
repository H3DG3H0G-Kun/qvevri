package com.game.progression;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Spring Data repository for {@link ProgressionProfile}.
 *
 * <p>The only custom finder needed is {@code findByCharacterId}, which the
 * {@link ProgressionService} uses for lazy-create (getOrCreate) lookups.
 */
public interface ProgressionProfileRepository extends JpaRepository<ProgressionProfile, Long> {

    /**
     * Returns the profile for the given character, or {@link Optional#empty()}
     * if none exists yet (first access will create one via the service).
     *
     * @param characterId the character whose profile to look up
     * @return an optional wrapping the profile
     */
    Optional<ProgressionProfile> findByCharacterId(Long characterId);
}
