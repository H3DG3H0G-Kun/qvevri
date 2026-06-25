package com.game.core.data;

/**
 * Phenological stage of the vine. Progresses through the season in order.
 * Frozen per SIM-SPEC §2.
 */
public enum PhenoStage {
    DORMANCY,
    BUD_SWELL,
    BUDBREAK,
    SHOOT_GROWTH,
    FLOWERING,
    FRUIT_SET,
    BERRY_DEVELOPMENT,
    VERAISON,
    RIPENING,
    HARVESTED,
    LEAF_FALL
}
