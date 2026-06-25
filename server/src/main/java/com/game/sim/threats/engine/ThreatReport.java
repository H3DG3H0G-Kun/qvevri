package com.game.sim.threats.engine;

import com.game.core.data.Fault;

import java.util.List;

/**
 * Summary of all threat activity for one day.
 *
 * <p>Returned by {@link ThreatEngine#step}. The {@code cumulativeQualityPenalty01}
 * field reflects the running season total (not just today's increment).
 *
 * @param activeTells               non-empty tell strings from sources that fired today
 * @param totalHealthDelta          sum of all {@code healthDelta} values this day (≤ 0)
 * @param totalYieldDelta           aggregate yield change = (Π multipliers) − 1 (≤ 0)
 * @param cumulativeQualityPenalty01 season-running quality penalty 0..1 (capped at 1)
 * @param inducedFault              most-severe fault induced today, NONE if none
 * @param dead                      true if any source set {@code killVine} this day
 */
public record ThreatReport(
        List<String> activeTells,
        double totalHealthDelta,
        double totalYieldDelta,
        double cumulativeQualityPenalty01,
        Fault inducedFault,
        boolean dead
) {}
