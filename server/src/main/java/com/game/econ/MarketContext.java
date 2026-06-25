package com.game.econ;

/**
 * Snapshot of market conditions used by {@link WinePricer}.
 *
 * <p>All fields are immutable; pass a new context each pricing tick.
 *
 * @param comparableSupplyL comparable supply in the same category (litres or
 *                          natural units); used in the scarcity factor.
 * @param appellationOk     whether appellation status should contribute a
 *                          premium. When {@code true} the appellation factor
 *                          ({@link WinePricer#APPELLATION_FACTOR}) is applied.
 *
 * <p>GDD Part 7/8 — Phase 2 economy, econ-core lane.
 */
public record MarketContext(double comparableSupplyL, boolean appellationOk) {

    /**
     * Convenience constructor accepting an {@code int} supply value.
     * Delegates to the canonical {@code double} constructor.
     *
     * @param supply       comparable supply (int, converted to double)
     * @param appellationOk appellation flag
     */
    public MarketContext(int supply, boolean appellationOk) {
        this((double) supply, appellationOk);
    }
}
