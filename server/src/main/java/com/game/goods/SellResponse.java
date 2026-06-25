package com.game.goods;

/**
 * Response body for POST /api/shop/sell.
 */
public class SellResponse {

    private final double walletGel;

    public SellResponse(double walletGel) {
        this.walletGel = walletGel;
    }

    public double getWalletGel() { return walletGel; }
}
