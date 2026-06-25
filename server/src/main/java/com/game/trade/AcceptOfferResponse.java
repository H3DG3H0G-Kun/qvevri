package com.game.trade;

/**
 * Response body for a successful POST /api/trade/offers/{offerId}/accept.
 *
 * <p>Returns the accepted offer (status=ACCEPTED, buyerCharacterId set) together
 * with the buyer's new wallet balance so the client can update the UI in one
 * round-trip.
 */
public class AcceptOfferResponse {

    private final TradeOffer offer;
    private final double     buyerWalletGel;

    public AcceptOfferResponse(TradeOffer offer, double buyerWalletGel) {
        this.offer          = offer;
        this.buyerWalletGel = buyerWalletGel;
    }

    public TradeOffer getOffer()          { return offer; }
    public double getBuyerWalletGel()     { return buyerWalletGel; }
}
