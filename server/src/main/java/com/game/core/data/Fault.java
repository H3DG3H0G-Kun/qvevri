package com.game.core.data;

/**
 * Wine fault detected during or after fermentation.
 * Frozen per SIM-SPEC §2.
 */
public enum Fault {
    NONE,
    /** Hydrogen sulphide reduction aroma — low YAN + stressed fermentation. */
    REDUCTION_H2S,
    /** Oxidation — excess oxygen exposure. */
    OXIDATION,
    /** Volatile acidity (acetic acid) — poor hygiene / cap management. */
    VOLATILE_ACIDITY,
    /** Fermentation arrested before dryness — temperature out of range. */
    STUCK_FERMENT
}
