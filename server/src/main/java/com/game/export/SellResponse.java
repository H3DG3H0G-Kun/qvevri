package com.game.export;

/**
 * Response for {@code POST /api/export/sell}.
 *
 * <p>Returns the persisted {@link ExportRecord} together with the seller's wallet
 * balance after the net proceeds have been credited.
 *
 * @param record    the newly persisted export record
 * @param walletGel the seller character's new wallet balance (GEL) after crediting net proceeds
 */
public record SellResponse(ExportRecord record, double walletGel) {}
