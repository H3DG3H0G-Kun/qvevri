package com.game.goods;

/**
 * Request body for POST /api/shop/buy.
 */
public class BuyRequest {

    private Long   characterId;
    private String goodTypeId;
    private double quantity;

    public BuyRequest() {}

    public Long   getCharacterId() { return characterId; }
    public String getGoodTypeId()  { return goodTypeId; }
    public double getQuantity()    { return quantity; }

    public void setCharacterId(Long characterId)   { this.characterId = characterId; }
    public void setGoodTypeId(String goodTypeId)   { this.goodTypeId  = goodTypeId; }
    public void setQuantity(double quantity)        { this.quantity    = quantity; }
}
