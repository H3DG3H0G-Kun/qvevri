package com.game.trade;

import jakarta.validation.constraints.NotNull;

/**
 * Request body for POST /api/trade/offers/{offerId}/accept.
 */
public class AcceptOfferRequest {

    @NotNull
    private Long characterId;

    public Long getCharacterId() { return characterId; }
    public void setCharacterId(Long characterId) { this.characterId = characterId; }
}
