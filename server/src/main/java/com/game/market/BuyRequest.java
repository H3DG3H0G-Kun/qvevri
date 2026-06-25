package com.game.market;

import jakarta.validation.constraints.NotNull;

/**
 * Request body for POST /api/market/buy.
 *
 * @param characterId the buying character's id (must be owned by token's account)
 * @param listingId   the ACTIVE MarketListing to purchase
 */
public class BuyRequest {

    @NotNull
    private Long characterId;

    @NotNull
    private Long listingId;

    public BuyRequest() {}

    public BuyRequest(Long characterId, Long listingId) {
        this.characterId = characterId;
        this.listingId   = listingId;
    }

    public Long getCharacterId() { return characterId; }
    public Long getListingId()   { return listingId; }

    public void setCharacterId(Long id) { this.characterId = id; }
    public void setListingId(Long id)   { this.listingId = id; }
}
