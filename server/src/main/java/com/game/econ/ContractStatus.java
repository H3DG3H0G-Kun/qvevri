package com.game.econ;

/**
 * Lifecycle status of a {@link Contract}.
 *
 * <p>GDD Part 7/8 — Phase 2 economy, econ-core lane.
 */
public enum ContractStatus {
    /** Offer created but not yet countersigned by all parties. */
    PENDING,
    /** All parties have accepted; the contract is in force. */
    ACTIVE,
    /** Contract has been fulfilled and closed. */
    EXERCISED,
    /** One party voided the contract before exercise. */
    CANCELLED
}
