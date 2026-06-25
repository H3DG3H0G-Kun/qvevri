package com.game.build;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link Building}.
 */
@Repository
public interface BuildingRepository extends JpaRepository<Building, Long> {

    /**
     * Returns all buildings owned by the given character.
     * Primary access pattern for GET /api/build/{characterId}.
     */
    List<Building> findByOwnerCharacterId(Long ownerCharacterId);
}
