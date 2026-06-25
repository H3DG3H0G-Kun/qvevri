package com.game.build;

/**
 * One (goodTypeId, qty) input-cost entry for a {@link BuildingType}.
 *
 * <p>All goodTypeIds here are present in {@link com.game.goods.GoodsCatalog}.
 */
public final class BuildingTypeInput {

    private final String goodTypeId;
    private final double qty;

    public BuildingTypeInput(String goodTypeId, double qty) {
        this.goodTypeId = goodTypeId;
        this.qty        = qty;
    }

    public String getGoodTypeId() { return goodTypeId; }
    public double getQty()        { return qty; }
}
