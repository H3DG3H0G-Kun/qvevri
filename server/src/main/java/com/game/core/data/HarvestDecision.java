package com.game.core.data;

/**
 * Player's harvest pick decision — which day of year to harvest.
 * Frozen per SIM-SPEC §2.
 *
 * @param dayOfYear 0-based day to harvest (typically 260..310 for Saperavi in Kakheti)
 */
public record HarvestDecision(int dayOfYear) {}
