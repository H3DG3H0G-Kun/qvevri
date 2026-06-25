package com.game.auction;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link Auction}.
 */
@Repository
public interface AuctionRepository extends JpaRepository<Auction, Long> {

    /**
     * Returns all auctions with the given lifecycle status string.
     * Primary use: {@code findByAuctionStatus("OPEN")} for the open marketplace.
     */
    List<Auction> findByAuctionStatus(String auctionStatus);

    /**
     * Returns all auctions created by a given seller character (any status).
     * Used by GET /api/auction/mine.
     */
    List<Auction> findBySellerCharacterId(Long sellerCharacterId);
}
