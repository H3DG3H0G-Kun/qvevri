package com.game.tourism;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring Data JPA repository for {@link TourismLedger}.
 */
@Repository
public interface TourismLedgerRepository extends JpaRepository<TourismLedger, Long> {

    /**
     * Returns the tourism ledger for the given character, if present.
     *
     * @param characterId the character to look up
     * @return the ledger, or Optional.empty() if not yet created
     */
    Optional<TourismLedger> findByCharacterId(Long characterId);
}
