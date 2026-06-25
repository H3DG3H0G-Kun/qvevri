package com.game.auction;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Request body for POST /api/auction/{id}/bid.
 */
public class BidRequest {

    @NotNull
    private Long characterId;

    @Positive
    private double amountGel;

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public Long getCharacterId()     { return characterId; }
    public double getAmountGel()     { return amountGel; }

    public void setCharacterId(Long characterId) { this.characterId = characterId; }
    public void setAmountGel(double amountGel)   { this.amountGel = amountGel; }
}
