package com.game.land;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link Parcel}.
 */
@Repository
public interface ParcelRepository extends JpaRepository<Parcel, Long> {

    /**
     * Returns all parcels owned by the given character.
     * Used by GET /api/land/{characterId}.
     */
    List<Parcel> findByOwnerCharacterId(Long ownerCharacterId);

    /**
     * Returns a parcel only if it is owned by the given character.
     * Used for the ownership guard on the detail and attach-vineyard endpoints.
     */
    Optional<Parcel> findByIdAndOwnerCharacterId(Long id, Long ownerCharacterId);
}
