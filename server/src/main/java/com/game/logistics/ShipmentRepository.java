package com.game.logistics;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link Shipment}.
 */
@Repository
public interface ShipmentRepository extends JpaRepository<Shipment, Long> {

    /**
     * Returns all shipments (any status) for the given owning character.
     * Used by GET /api/logistics/{characterId}.
     */
    List<Shipment> findByOwnerCharacterId(Long ownerCharacterId);
}
