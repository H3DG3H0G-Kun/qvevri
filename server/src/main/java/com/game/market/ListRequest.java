package com.game.market;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Request body for POST /api/market/list.
 *
 * @param characterId   the selling character's id (must be owned by token's account)
 * @param cellarItemId  the CellarItem to list (must be owned by characterId, not escrowed)
 * @param askPrice      the seller's asking price in GEL (must be positive)
 */
public class ListRequest {

    @NotNull
    private Long characterId;

    @NotNull
    private Long cellarItemId;

    @Positive
    private double askPrice;

    public ListRequest() {}

    public ListRequest(Long characterId, Long cellarItemId, double askPrice) {
        this.characterId  = characterId;
        this.cellarItemId = cellarItemId;
        this.askPrice     = askPrice;
    }

    public Long getCharacterId()  { return characterId; }
    public Long getCellarItemId() { return cellarItemId; }
    public double getAskPrice()   { return askPrice; }

    public void setCharacterId(Long id)   { this.characterId = id; }
    public void setCellarItemId(Long id)  { this.cellarItemId = id; }
    public void setAskPrice(double p)     { this.askPrice = p; }
}
