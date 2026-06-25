package com.game.auction;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Request body for POST /api/auction/create.
 *
 * <p>The seller specifies which character is creating the auction, the kind of
 * asset ("GOODS" or "CELLAR_ITEM"), a reference to the asset, the quantity,
 * the opening bid floor, and how many sim-days the auction should run.
 */
public class CreateAuctionRequest {

    @NotNull
    private Long characterId;

    /** "GOODS" or "CELLAR_ITEM". */
    @NotBlank
    private String kind;

    /**
     * For GOODS: goodTypeId (e.g. "pruning_shears").
     * For CELLAR_ITEM: cellarItemId as a decimal string (e.g. "42").
     */
    @NotBlank
    private String refId;

    /** Units to auction (1.0 for CELLAR_ITEM). */
    @Positive
    private double quantity;

    /** Minimum opening bid in GEL. */
    @Positive
    private double startBidGel;

    /** How many sim-days the auction should stay open. Must be &ge; 1. */
    @Positive
    private int durationDays;

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public Long getCharacterId()      { return characterId; }
    public String getKind()           { return kind; }
    public String getRefId()          { return refId; }
    public double getQuantity()       { return quantity; }
    public double getStartBidGel()    { return startBidGel; }
    public int getDurationDays()      { return durationDays; }

    public void setCharacterId(Long characterId)    { this.characterId = characterId; }
    public void setKind(String kind)                { this.kind = kind; }
    public void setRefId(String refId)              { this.refId = refId; }
    public void setQuantity(double quantity)        { this.quantity = quantity; }
    public void setStartBidGel(double startBidGel) { this.startBidGel = startBidGel; }
    public void setDurationDays(int durationDays)  { this.durationDays = durationDays; }
}
