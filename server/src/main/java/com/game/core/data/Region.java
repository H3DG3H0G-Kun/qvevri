package com.game.core.data;

/**
 * Georgian wine-growing regions, ordered by GDD (warmest to coolest).
 * All seven map to a {@link RegionClimate} via {@link RegionClimates#of(Region)}.
 */
public enum Region {
    /** Alazani Valley — warmest, continental-dry; baseline for all sim constants. */
    KAKHETI,
    /** Kartli — slightly cooler and drier than Kakheti, continental. */
    KARTLI,
    /** Imereti — cooler, wetter, higher humidity; elevated fungal pressure. */
    IMERETI,
    /** Racha-Lechkhumi — cool, high-altitude, short season. */
    RACHA_LECHKHUMI,
    /** Samegrelo — warm, very humid, high rainfall. */
    SAMEGRELO,
    /** Guria / Adjara — warmest-wet, subtropical, highest humidity. */
    GURIA_ADJARA,
    /** Meskheti — cool, high-altitude, terraced, short season. */
    MESKHETI
}
