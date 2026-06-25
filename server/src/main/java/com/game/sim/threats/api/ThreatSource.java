package com.game.sim.threats.api;

/**
 * A single named hazard (one disease, pest, animal, or weather event) per
 * GDD Part 5.6 / 5.7. Implementations are PURE with respect to the supplied
 * {@link ThreatContext}: same context (including its RNG stream and prior
 * {@link ThreatMemory}) must yield the same {@link ThreatEffect}.
 *
 * <p>Cross-day state lives only in {@code ThreatMemory}; never in instance fields.
 */
public interface ThreatSource {

    /** Stable, unique, lowercase-dotted id, e.g. {@code "fungal.downy"}. */
    String id();

    ThreatCategory category();

    /** Decide this source's contribution for the day described by {@code ctx}. */
    ThreatEffect evaluate(ThreatContext ctx);
}
