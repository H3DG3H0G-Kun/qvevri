package com.game.sim.threats.api;

import com.game.core.data.Fault;

/**
 * One source's contribution for a single day. The engine aggregates these across
 * all sources, then applies the result to the vine.
 *
 * @param healthDelta      added to {@code VineState.healthFraction} (&lt;=0 damages, clamped to [0,1])
 * @param yieldMultiplier  multiplies {@code potentialYieldKg} (1.0 = no effect, &lt;1 = loss)
 * @param qualityPenalty01 added to a running 0..1 quality penalty (capped at 1 by the engine)
 * @param inducedFault     a fault/taint to force (e.g. rot-&gt;VOLATILE_ACIDITY); {@link Fault#NONE} if none
 * @param killVine         true if this threat kills the vine outright this tick
 * @param tell             human-readable symptom string for the day-log/UI ("" if no visible sign)
 * @param nextMemory       this source's updated {@link ThreatMemory} for the next day
 */
public record ThreatEffect(double healthDelta,
                           double yieldMultiplier,
                           double qualityPenalty01,
                           Fault inducedFault,
                           boolean killVine,
                           String tell,
                           ThreatMemory nextMemory) {

    /** A neutral effect (no damage) that simply carries memory forward. */
    public static ThreatEffect none(ThreatMemory mem) {
        return new ThreatEffect(0.0, 1.0, 0.0, Fault.NONE, false, "", mem);
    }
}
