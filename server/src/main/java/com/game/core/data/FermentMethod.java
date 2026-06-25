package com.game.core.data;

/**
 * Winemaking fermentation method. Drives style and cellar kinetics.
 * Frozen per SIM-SPEC §2.
 */
public enum FermentMethod {
    /** Qvevri / extended skin contact → AMBER style. */
    KAKHETIAN,
    /** Partial skin contact → between AMBER and WHITE. */
    IMERETIAN,
    /** Conventional red with cap management → RED style. */
    RED,
    /** Base wine for sparkling. */
    SPARKLING_BASE,
    /** Arrested fermentation → residual sugar retained. */
    SWEET
}
