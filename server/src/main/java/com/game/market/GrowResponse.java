package com.game.market;

import com.game.vineyard.VineyardYearResult;

/**
 * Response for POST /api/cellar/{characterId}/grow.
 *
 * <p>Returns both the persisted {@link CellarItem} (so the client has the DB id)
 * and the raw {@link VineyardYearResult} (vintage detail, events log, etc.).
 *
 * @param cellarItem the newly saved cellar item
 * @param result     the full vineyard simulation result
 */
public record GrowResponse(CellarItem cellarItem, VineyardYearResult result) {}
