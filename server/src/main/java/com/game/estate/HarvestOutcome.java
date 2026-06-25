package com.game.estate;

import com.game.core.data.WineLot;
import com.game.market.CellarItem;

/**
 * Result of a successful harvest operation.
 * Returned by POST /api/vineyards/{vineyardId}/harvest.
 *
 * @param cellarItem  the CellarItem created and persisted in the character's cellar
 * @param bottle      the WineLot produced by the full sim pipeline
 * @param vineyardView the vineyard state snapshot at the moment of harvest
 */
public record HarvestOutcome(
        CellarItem cellarItem,
        WineLot bottle,
        VineyardView vineyardView
) {}
