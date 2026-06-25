package com.game.festival;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * JPA repository for {@link FestivalParticipation} entities.
 *
 * <p>Primary access pattern: look up whether a (character, festival, year)
 * triplet has already been recorded, for the idempotency guard in
 * {@link FestivalService#participate}.
 */
public interface FestivalParticipationRepository extends JpaRepository<FestivalParticipation, Long> {

    /**
     * Returns the participation record for a specific character + festival +
     * season-year combination. Used by the service to enforce the
     * ALREADY_PARTICIPATED guard.
     *
     * @param characterId the participating character
     * @param festivalId  the stable festival catalog id
     * @param seasonYear  the simulation year
     * @return Optional containing the record, or empty if not found
     */
    Optional<FestivalParticipation> findByCharacterIdAndFestivalIdAndSeasonYear(
            Long characterId, String festivalId, int seasonYear);
}
