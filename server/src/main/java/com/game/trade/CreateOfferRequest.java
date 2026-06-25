package com.game.trade;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Request body for POST /api/trade/offers.
 *
 * <p>The seller specifies which character is creating the offer, the kind of
 * asset ("GOODS" or "CELLAR_ITEM"), the reference to the asset, how many units
 * are on offer, and the total price in GEL.
 */
public class CreateOfferRequest {

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
    private String reference;

    @Positive
    private double quantity;

    @Positive
    private double priceGel;

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public Long getCharacterId()   { return characterId; }
    public String getKind()        { return kind; }
    public String getReference()   { return reference; }
    public double getQuantity()    { return quantity; }
    public double getPriceGel()    { return priceGel; }

    public void setCharacterId(Long characterId) { this.characterId = characterId; }
    public void setKind(String kind)             { this.kind = kind; }
    public void setReference(String reference)   { this.reference = reference; }
    public void setQuantity(double quantity)     { this.quantity = quantity; }
    public void setPriceGel(double priceGel)     { this.priceGel = priceGel; }
}
