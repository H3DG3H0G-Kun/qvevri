package com.game.market;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link CellarItem}.
 */
@Repository
public interface CellarItemRepository extends JpaRepository<CellarItem, Long> {

    /**
     * Returns all non-escrowed items in a character's cellar.
     * Used by GET /api/cellar/{characterId}.
     */
    List<CellarItem> findByCharacterIdAndEscrowedFalse(Long characterId);

    /**
     * Returns all items (including escrowed) owned by a character.
     * Used for ownership verification before listing.
     */
    List<CellarItem> findByCharacterId(Long characterId);

    /**
     * Returns an item only if owned by the given character — used for
     * ownership guard on POST /api/market/list.
     */
    Optional<CellarItem> findByIdAndCharacterId(Long id, Long characterId);

    /**
     * Count of active (non-escrowed) AGED_WINE items across all characters;
     * used to compute market supply for WinePricer.
     */
    long countByItemTypeAndEscrowedFalse(String itemType);
}
