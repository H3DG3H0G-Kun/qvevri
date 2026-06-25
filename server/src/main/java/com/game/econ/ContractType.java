package com.game.econ;

/**
 * Economic contract types.
 *
 * <ul>
 *   <li>{@link #SPOT} — immediate exchange; the only type exercised in Phase 2.</li>
 *   <li>{@link #SEASONAL_SUPPLY} — forward supply agreement (reserved for later).</li>
 *   <li>{@link #WAGE} — labour contract (reserved for later).</li>
 * </ul>
 *
 * <p>GDD Part 7/8 — Phase 2 economy, econ-core lane.
 */
public enum ContractType {
    SPOT,
    SEASONAL_SUPPLY,
    WAGE
}
