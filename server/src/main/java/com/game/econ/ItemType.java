package com.game.econ;

/**
 * Tradeable item categories in the economy.
 *
 * <p>GRAPES, MUST, YOUNG_WINE, AGED_WINE and CHACHA_BRANDY are the five
 * production-stage goods. WINE is a convenience alias for AGED_WINE used
 * in market listings where the age distinction is not required.
 *
 * <p>GDD Part 7/8 — Phase 2 economy, econ-core lane.
 */
public enum ItemType {
    GRAPES,
    MUST,
    YOUNG_WINE,
    AGED_WINE,
    CHACHA_BRANDY,
    /** Convenience alias: finished bottled wine without age-stage distinction. */
    WINE
}
