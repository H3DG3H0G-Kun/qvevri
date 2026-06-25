package com.game.goods;

/**
 * Request body for POST /api/shop/sell.
 */
public class SellRequest {

    private Long   characterId;
    private Long   ownedGoodId;
    private double quantity;

    public SellRequest() {}

    public Long   getCharacterId()  { return characterId; }
    public Long   getOwnedGoodId()  { return ownedGoodId; }
    public double getQuantity()     { return quantity; }

    public void setCharacterId(Long characterId) { this.characterId = characterId; }
    public void setOwnedGoodId(Long ownedGoodId) { this.ownedGoodId = ownedGoodId; }
    public void setQuantity(double quantity)      { this.quantity    = quantity; }
}
