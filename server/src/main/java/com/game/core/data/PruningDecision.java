package com.game.core.data;

/**
 * Player's winter pruning choice — buds retained per vine.
 * Target for Saperavi: ~12 buds (balanced yield and ripening).
 * Frozen per SIM-SPEC §2.
 *
 * @param budLoad number of buds retained per vine (typically 8..20)
 */
public record PruningDecision(int budLoad) {}
