package com.game.market;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link MarketListing}.
 */
@Repository
public interface MarketListingRepository extends JpaRepository<MarketListing, Long> {

    /**
     * All ACTIVE listings — backing query for GET /api/market.
     */
    List<MarketListing> findByStatus(ListingStatus status);

    /**
     * Finds a listing by its CellarItem FK — used to detect duplicate listings.
     */
    Optional<MarketListing> findByCellarItemIdAndStatus(Long cellarItemId, ListingStatus status);
}
