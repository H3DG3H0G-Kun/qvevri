package com.game.goods;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link OwnedGood}.
 */
@Repository
public interface OwnedGoodRepository extends JpaRepository<OwnedGood, Long> {

    /**
     * All goods owned by a character (used by GET /api/goods/{characterId}).
     */
    List<OwnedGood> findByCharacterId(Long characterId);

    /**
     * Lookup an existing stack for stacking/decrement operations.
     * Returns the single row for (characterId, goodTypeId), if present.
     */
    Optional<OwnedGood> findByCharacterIdAndGoodTypeId(Long characterId, String goodTypeId);
}
