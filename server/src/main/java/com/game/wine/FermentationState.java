package com.game.wine;

/**
 * Lifecycle states for a {@link com.game.market.CellarItem} that has entered
 * the winemaking-depth fermentation flow (BACKEND-DEPTH-SPEC §6, WINE lane).
 *
 * <p>NULL on the CellarItem column means the item was produced via the instant
 * harvest path and has no winemaking state (default / backwards-compatible path).
 *
 * <ul>
 *   <li>{@link #FERMENTING} — fermentation has been started and is in progress.</li>
 *   <li>{@link #READY} — fermentation period has elapsed; the batch is ready
 *       to bottle but cellar aging continues to improve quality.</li>
 *   <li>{@link #BOTTLED} — explicitly bottled by the player; quality is locked.</li>
 * </ul>
 */
public enum FermentationState {
    FERMENTING,
    READY,
    BOTTLED
}
