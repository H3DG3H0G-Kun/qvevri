package com.game.economy;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link PriceSnapshot}.
 *
 * <p>Primary access: persist on demand (POST /api/economy/snapshot) and retrieve
 * for history by item type + region.
 */
@Repository
public interface PriceSnapshotRepository extends JpaRepository<PriceSnapshot, Long> {

    /** Most-recent snapshots for a given item type and region. */
    java.util.List<PriceSnapshot> findByItemTypeAndRegionOrderByCreatedAtDesc(
            String itemType, String region);
}
