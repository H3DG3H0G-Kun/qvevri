package com.game.trade;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link TradeOffer}.
 */
@Repository
public interface TradeOfferRepository extends JpaRepository<TradeOffer, Long> {

    /**
     * Returns all offers with the given status string.
     * Primary use: {@code findByStatus("OPEN")} for the open marketplace.
     */
    List<TradeOffer> findByStatus(String status);

    /**
     * Returns all offers created by a given seller character (any status).
     * Used by GET /api/trade/offers/mine.
     */
    List<TradeOffer> findBySellerCharacterId(Long sellerCharacterId);
}
