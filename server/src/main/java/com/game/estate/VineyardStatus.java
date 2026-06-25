package com.game.estate;

/**
 * Life-cycle status of a persistent vineyard.
 * GROWING = actively simulated each season.
 * FALLOW  = not producing (reserved for future multi-season establishment arc).
 */
public enum VineyardStatus {
    GROWING,
    FALLOW
}
