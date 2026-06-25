package com.game.goods;

/**
 * Response body for POST /api/shop/buy.
 *
 * <p>Returns the granted/updated {@link OwnedGood} and the character's
 * new wallet balance so the client can update the HUD in a single round-trip.
 */
public class BuyResponse {

    private final OwnedGood ownedGood;
    private final double    walletGel;

    public BuyResponse(OwnedGood ownedGood, double walletGel) {
        this.ownedGood = ownedGood;
        this.walletGel = walletGel;
    }

    public OwnedGood getOwnedGood() { return ownedGood; }
    public double    getWalletGel() { return walletGel; }
}
