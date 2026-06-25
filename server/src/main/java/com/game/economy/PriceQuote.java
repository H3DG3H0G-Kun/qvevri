package com.game.economy;

/**
 * Immutable value object returned by
 * {@link EconomyService#quote(String, String)} and
 * GET /api/economy/price.
 *
 * <p>All monetary values are in GEL.
 *
 * <p>Formula recap:
 * <pre>
 *   grossPrice  = basePrice × supplyFactor × regionalFactor
 *   fee         = grossPrice × FEE_RATE  (currently 0.05)
 *   netPrice    = grossPrice − fee
 * </pre>
 */
public final class PriceQuote {

    private final double basePrice;
    private final double supplyFactor;
    private final double regionalFactor;
    private final double grossPrice;
    private final double fee;
    private final double netPrice;
    private final long   supplyCount;

    public PriceQuote(double basePrice, double supplyFactor, double regionalFactor,
                      double grossPrice, double fee, double netPrice, long supplyCount) {
        this.basePrice      = basePrice;
        this.supplyFactor   = supplyFactor;
        this.regionalFactor = regionalFactor;
        this.grossPrice     = grossPrice;
        this.fee            = fee;
        this.netPrice       = netPrice;
        this.supplyCount    = supplyCount;
    }

    public double getBasePrice()      { return basePrice; }
    public double getSupplyFactor()   { return supplyFactor; }
    public double getRegionalFactor() { return regionalFactor; }
    public double getGrossPrice()     { return grossPrice; }
    public double getFee()            { return fee; }
    public double getNetPrice()       { return netPrice; }
    public long   getSupplyCount()    { return supplyCount; }
}
