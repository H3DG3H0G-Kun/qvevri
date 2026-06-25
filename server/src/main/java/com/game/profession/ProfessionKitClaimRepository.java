package com.game.profession;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring Data JPA repository for {@link ProfessionKitClaim}.
 */
@Repository
public interface ProfessionKitClaimRepository extends JpaRepository<ProfessionKitClaim, Long> {

    /** Checks if a starter-kit claim already exists for the given character. */
    Optional<ProfessionKitClaim> findByCharacterId(Long characterId);
}
