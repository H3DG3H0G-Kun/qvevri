package com.game.land;

/**
 * Response body for POST /api/land/parcels.
 * Returns the created parcel and the character's updated wallet balance.
 */
public class BuyParcelResponse {

    private final Parcel parcel;
    private final double newWalletGel;

    public BuyParcelResponse(Parcel parcel, double newWalletGel) {
        this.parcel       = parcel;
        this.newWalletGel = newWalletGel;
    }

    public Parcel getParcel()        { return parcel; }
    public double getNewWalletGel()  { return newWalletGel; }
}
