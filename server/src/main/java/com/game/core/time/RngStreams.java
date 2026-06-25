package com.game.core.time;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.random.RandomGenerator;

/**
 * Deterministic, named stream factory backed by {@link java.util.SplittableRandom}.
 *
 * <p>Each subsystem calls {@link #stream(String)} with a stable name and receives
 * an independent {@link RandomGenerator} seeded deterministically from the master
 * seed + the name's hash.  Evaluation order cannot change results because each
 * stream is isolated and the seed is name-derived, not position-derived.
 *
 * <p>Frozen seam per SIM-SPEC §3.1.
 */
public final class RngStreams {

    private final long masterSeed;
    // Cache so repeated calls for the same name return the *same* stream object
    // (important if a subsystem stores it across ticks).  LinkedHashMap for determinism.
    private final Map<String, RandomGenerator> cache = new LinkedHashMap<>();

    public RngStreams(long masterSeed) {
        this.masterSeed = masterSeed;
    }

    /**
     * Returns a stable, independent {@link RandomGenerator} for the given subsystem
     * name.  Calling this twice with the same name returns the same object.
     *
     * <p>The per-stream seed is derived as {@code masterSeed XOR (name.hashCode() * PRIME)},
     * keeping it stable across JVM runs (String.hashCode is specified as deterministic).
     */
    public RandomGenerator stream(String name) {
        return cache.computeIfAbsent(name, n -> {
            long streamSeed = masterSeed ^ ((long) n.hashCode() * 0x9e3779b97f4a7c15L);
            return new java.util.SplittableRandom(streamSeed);
        });
    }
}
