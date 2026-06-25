package com.game.sim.vine;

import com.game.core.data.DailyWeather;
import com.game.core.data.PruningDecision;
import com.game.core.data.SiteProfile;
import com.game.core.data.VineState;

/**
 * One-day phenological tick for a single vine.
 *
 * <p>Contract (SIM-SPEC §3.4): pure function — same inputs always produce
 * the same {@link VineState}.  No side-effects, no mutable state, no RNG.
 */
public interface VineSimulator {

    /**
     * Advance the vine by exactly one simulation day.
     *
     * @param prev      vine state at end of yesterday
     * @param today     weather for the current simulation day
     * @param site      fixed plot characteristics (soil, slope, aspect, …)
     * @param suitability pre-computed site suitability in [0,1] (from {@code SiteSuitability.score()})
     * @param pruning   the player's winter pruning decision (bud load retained)
     * @return          new vine state for end of today
     */
    VineState tick(VineState prev,
                   DailyWeather today,
                   SiteProfile site,
                   double suitability,
                   PruningDecision pruning);
}
