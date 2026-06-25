package com.game.career;

/**
 * Immutable economic identity descriptor for one career.
 *
 * <p>All numeric multiplier fields use 0.0 as the "not applicable" neutral value.
 * A career only sets the field(s) that correspond to its documented PRO/CON.
 * The integration pass (deferred) will read these values and apply them to
 * market/yield/shipping calculations; this lane only defines and exposes them.
 *
 * <p>Field semantics (all are fractional deltas unless noted):
 * <ul>
 *   <li>{@code sellMarginMult}      — bonus fraction added to sell-side margin (Merchant +0.20)</li>
 *   <li>{@code buyDiscountMult}     — fraction subtracted from buy price (Négociant −0.20)</li>
 *   <li>{@code yieldMult}           — fraction added to vineyard yield (Grower +0.15)</li>
 *   <li>{@code qualityMult}         — fraction added to fermentation quality output (Winemaker +0.10)</li>
 *   <li>{@code shippingDiscountMult}— fraction subtracted from shipping cost (Hauler −0.30)</li>
 *   <li>{@code craftDiscountMult}   — fraction subtracted from vessel craft cost (Cooper −0.20)</li>
 *   <li>{@code brokerCommissionMult}— fraction earned as commission on brokered deal (Broker +0.05)</li>
 *   <li>{@code cuttingMarginMult}   — margin fraction on certified-cutting sales (Nurseryman +0.25)</li>
 *   <li>{@code gradeFeeIncomeMult}  — consulting-fee fraction of graded-wine value (Enologist +0.08)</li>
 * </ul>
 */
public record CareerProfile(
        String careerType,
        String summary,
        String pro,
        String con,
        double sellMarginMult,
        double buyDiscountMult,
        double yieldMult,
        double qualityMult,
        double shippingDiscountMult,
        double craftDiscountMult,
        double brokerCommissionMult,
        double cuttingMarginMult,
        double gradeFeeIncomeMult
) {

    /**
     * Builder-style factory that starts from all-zero multipliers so each
     * career entry only sets the fields it actually uses.
     */
    public static Builder forCareer(String careerType, String summary,
                                    String pro, String con) {
        return new Builder(careerType, summary, pro, con);
    }

    public static final class Builder {
        private final String careerType;
        private final String summary;
        private final String pro;
        private final String con;

        private double sellMarginMult      = 0.0;
        private double buyDiscountMult     = 0.0;
        private double yieldMult           = 0.0;
        private double qualityMult         = 0.0;
        private double shippingDiscountMult = 0.0;
        private double craftDiscountMult   = 0.0;
        private double brokerCommissionMult = 0.0;
        private double cuttingMarginMult   = 0.0;
        private double gradeFeeIncomeMult  = 0.0;

        private Builder(String careerType, String summary, String pro, String con) {
            this.careerType = careerType;
            this.summary    = summary;
            this.pro        = pro;
            this.con        = con;
        }

        public Builder sellMarginMult(double v)       { sellMarginMult = v;       return this; }
        public Builder buyDiscountMult(double v)      { buyDiscountMult = v;      return this; }
        public Builder yieldMult(double v)            { yieldMult = v;            return this; }
        public Builder qualityMult(double v)          { qualityMult = v;          return this; }
        public Builder shippingDiscountMult(double v) { shippingDiscountMult = v; return this; }
        public Builder craftDiscountMult(double v)    { craftDiscountMult = v;    return this; }
        public Builder brokerCommissionMult(double v) { brokerCommissionMult = v; return this; }
        public Builder cuttingMarginMult(double v)    { cuttingMarginMult = v;    return this; }
        public Builder gradeFeeIncomeMult(double v)   { gradeFeeIncomeMult = v;   return this; }

        public CareerProfile build() {
            return new CareerProfile(
                    careerType, summary, pro, con,
                    sellMarginMult, buyDiscountMult, yieldMult, qualityMult,
                    shippingDiscountMult, craftDiscountMult, brokerCommissionMult,
                    cuttingMarginMult, gradeFeeIncomeMult);
        }
    }
}
