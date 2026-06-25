package com.game.sim.threats.engine;

import com.game.core.data.VineState;

/**
 * Output of one {@link ThreatEngine#step} call.
 *
 * <p>The {@code vine} field is the updated state after all threats have applied
 * their health/yield adjustments.  The {@code report} summarises what fired.
 *
 * @param vine   post-threat vine state (healthFraction and potentialYieldKg adjusted)
 * @param report threat activity summary for this day
 */
public record DayResult(VineState vine, ThreatReport report) {}
