package com.game.estate;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link Vineyard}.
 */
@Repository
public interface VineyardRepository extends JpaRepository<Vineyard, Long> {

    /**
     * Returns all vineyards owned by the given character.
     * Used by GET /api/vineyards/{characterId}.
     */
    List<Vineyard> findByOwnerCharacterId(Long ownerCharacterId);

    /**
     * Returns a vineyard only if owned by the given character.
     * Used for ownership guard on detail + harvest endpoints.
     */
    Optional<Vineyard> findByIdAndOwnerCharacterId(Long id, Long ownerCharacterId);
}
