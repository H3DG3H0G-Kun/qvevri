package com.game.trade;

/**
 * String constants for {@link TradeOffer#getStatus()}.
 * Stored as plain VARCHAR in the database so the values are stable across
 * refactors; do not rename without a schema migration.
 */
public final class TradeOfferStatus {

    /** The offer is visible on the marketplace and can be accepted or cancelled. */
    public static final String OPEN      = "OPEN";

    /** A buyer accepted the offer; money and item have been transferred. */
    public static final String ACCEPTED  = "ACCEPTED";

    /** The seller cancelled the offer; any reservation has been released. */
    public static final String CANCELLED = "CANCELLED";

    private TradeOfferStatus() {}
}
