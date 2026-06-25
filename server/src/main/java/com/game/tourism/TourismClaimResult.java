package com.game.tourism;

/**
 * Result of a successful tourism income claim.
 *
 * <p>Returned by {@code POST /api/tourism/{characterId}/claim}.
 */
public final class TourismClaimResult {

    private final double paid;
    private final double walletGel;
    private final long   lastClaimDay;

    public TourismClaimResult(double paid, double walletGel, long lastClaimDay) {
        this.paid         = paid;
        this.walletGel    = walletGel;
        this.lastClaimDay = lastClaimDay;
    }

    /** Amount credited to the wallet this claim (0.0 if no days have passed). */
    public double getPaid()         { return paid; }

    /** Wallet balance immediately after the claim. */
    public double getWalletGel()    { return walletGel; }

    /** The new {@code lastClaimDay} watermark (equals current absolute day). */
    public long   getLastClaimDay() { return lastClaimDay; }
}
