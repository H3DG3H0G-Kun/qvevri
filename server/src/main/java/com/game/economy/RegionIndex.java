package com.game.economy;

/**
 * Single entry in the economy price index returned by
 * GET /api/economy/index.
 *
 * <p>Contains the region name and the computed grossPrice for the
 * main wine item type ({@code WINE}) in that region.
 */
public final class RegionIndex {

    private final String region;
    private final double price;

    public RegionIndex(String region, double price) {
        this.region = region;
        this.price  = price;
    }

    public String getRegion() { return region; }
    public double getPrice()  { return price; }
}
