package com.game.tourism;

/**
 * Read-only snapshot of a character's tourism income state.
 *
 * <p>Returned by {@code GET /api/tourism/{characterId}}. The {@code accruedSoFar}
 * field is computed on the fly — income is not yet paid to the wallet.
 */
public final class TourismSnapshot {

    private final long   lastClaimDay;
    private final int    buildingsCount;
    private final double currentRatePerDay;
    private final double accruedSoFar;

    public TourismSnapshot(long lastClaimDay,
                           int buildingsCount,
                           double currentRatePerDay,
                           double accruedSoFar) {
        this.lastClaimDay      = lastClaimDay;
        this.buildingsCount    = buildingsCount;
        this.currentRatePerDay = currentRatePerDay;
        this.accruedSoFar      = accruedSoFar;
    }

    public long   getLastClaimDay()      { return lastClaimDay; }
    public int    getBuildingsCount()    { return buildingsCount; }
    public double getCurrentRatePerDay() { return currentRatePerDay; }
    public double getAccruedSoFar()      { return accruedSoFar; }
}
