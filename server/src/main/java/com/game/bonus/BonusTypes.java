package com.game.bonus;

import java.util.List;

/**
 * Canonical bonus-type keys used by {@link BonusService} to aggregate modifiers
 * from all sources (career, skills, and — in later integration steps — research,
 * buildings, labor, festivals).
 *
 * <p>All values are interpreted as FRACTIONAL deltas unless a consumer documents
 * otherwise (e.g. SELL_MARGIN 0.20 = +20%). A character with no contributing
 * sources totals 0.0 for every type, so wiring a bonus into a live action never
 * changes the no-bonus (default) outcome.
 */
public final class BonusTypes {

    private BonusTypes() {}

    public static final String SELL_MARGIN       = "SELL_MARGIN";
    public static final String BUY_DISCOUNT      = "BUY_DISCOUNT";
    public static final String YIELD             = "YIELD";
    public static final String QUALITY           = "QUALITY";
    public static final String SHIPPING_DISCOUNT = "SHIPPING_DISCOUNT";
    public static final String CRAFT_DISCOUNT    = "CRAFT_DISCOUNT";
    public static final String AGING_CAP         = "AGING_CAP";
    public static final String GRADE_ACCURACY    = "GRADE_ACCURACY";
    public static final String THREAT_RESIST     = "THREAT_RESIST";
    public static final String BROKER_COMMISSION = "BROKER_COMMISSION";
    public static final String CUTTING_MARGIN    = "CUTTING_MARGIN";
    public static final String GRADE_FEE_INCOME  = "GRADE_FEE_INCOME";

    /** All canonical bonus types, for full-aggregation reads. */
    public static final List<String> ALL = List.of(
            SELL_MARGIN, BUY_DISCOUNT, YIELD, QUALITY, SHIPPING_DISCOUNT,
            CRAFT_DISCOUNT, AGING_CAP, GRADE_ACCURACY, THREAT_RESIST,
            BROKER_COMMISSION, CUTTING_MARGIN, GRADE_FEE_INCOME);
}
