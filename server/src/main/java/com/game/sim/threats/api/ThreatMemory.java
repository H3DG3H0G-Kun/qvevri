package com.game.sim.threats.api;

/**
 * Per-source cross-day state. The {@link ThreatSource} is otherwise pure; all of
 * its persistence (infection level, brood counters, years infected, whether a
 * chronic/terminal condition has established) lives here and is threaded by the
 * engine from one day to the next.
 *
 * <p>Fields are generic on purpose so every source can reuse the same record:
 * <ul>
 *   <li>{@code level} — primary intensity 0..1 (e.g. infection severity).</li>
 *   <li>{@code aux} — secondary scalar (e.g. spore load, brood progress).</li>
 *   <li>{@code ticksActive} — days the threat has been active this season.</li>
 *   <li>{@code yearsActive} — seasons active (for chronic/terminal threats).</li>
 *   <li>{@code established} — latched flag (e.g. phylloxera/virus took hold).</li>
 * </ul>
 */
public record ThreatMemory(double level, double aux, int ticksActive,
                           int yearsActive, boolean established) {

    /** Neutral starting memory: nothing active. */
    public static ThreatMemory none() {
        return new ThreatMemory(0.0, 0.0, 0, 0, false);
    }
}
