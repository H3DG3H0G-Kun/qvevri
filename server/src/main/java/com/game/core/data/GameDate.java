package com.game.core.data;

/**
 * A calendar position inside the simulation.
 * {@code dayOfYear} is 0-based (0..364 for a 365-day sim year).
 * Frozen per SIM-SPEC §2.
 */
public record GameDate(int year, int dayOfYear) {}
