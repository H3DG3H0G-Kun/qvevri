package com.game.sim.cellar;

import com.game.core.data.Fault;
import com.game.core.data.FermentMethod;
import com.game.core.data.MustProfile;
import com.game.core.time.RngStreams;

import java.util.random.RandomGenerator;

/**
 * Kinetic fermentation model for Saperavi red wine (and related methods).
 *
 * <h2>Model overview</h2>
 *
 * <p>This class models fermentation as a chemical kinetics process, NOT a
 * countdown timer.  Key steps:
 *
 * <ol>
 *   <li><b>Temperature check:</b> if cellarTempC &gt; {@value #STUCK_TEMP_C} → immediate
 *       {@link Fault#STUCK_FERMENT}; only partial sugar conversion.</li>
 *   <li><b>Temperature efficiency:</b> each fermentation style has an optimal
 *       temperature band.  Deviation from the centre penalises conversion
 *       efficiency and can create fault risk.</li>
 *   <li><b>ABV calculation:</b> {@code ABV = startBrix × conversionFactor},
 *       where {@code conversionFactor} ∈ [0.55, 0.65] depending on temperature
 *       efficiency, health, and any stuck-ferment fraction.</li>
 *   <li><b>Acidity evolution:</b> TA falls slightly during fermentation (malolactic
 *       is not modelled explicitly this phase) and pH rises accordingly.</li>
 *   <li><b>Fault resolution:</b> the single most-severe fault wins.
 *       Priority: STUCK_FERMENT &gt; REDUCTION_H2S &gt; VOLATILE_ACIDITY &gt; OXIDATION &gt; NONE.</li>
 *   <li><b>Extraction:</b> RED/KAKHETIAN methods with good cap tending (tending01)
 *       and warm temperatures drive extraction; other methods extract minimally.</li>
 *   <li><b>Jitter:</b> a small bounded Gaussian jitter (±{@value #JITTER_SCALE}) is
 *       applied to ABV and TA using the named RNG stream, keeping the result
 *       reproducible from the master seed.</li>
 * </ol>
 *
 * <p>No magic numbers — every threshold and coefficient is a named constant.
 */
public final class KineticFermenter implements Fermenter {

    // ── Temperature limits ───────────────────────────────────────────────────

    /** Above this temperature fermentation halts: STUCK_FERMENT fault. */
    static final double STUCK_TEMP_C = 32.0;

    /** Optimal centre for red fermentations (°C). */
    static final double RED_TEMP_CENTRE_C  = 25.0;
    /** Half-band radius for red fermentation (°C). Ideal range 21–30°C → radius 4.5°C. */
    static final double RED_TEMP_RADIUS_C  = 4.5;

    /** Optimal centre for white / cool fermentations (°C). */
    static final double WHITE_TEMP_CENTRE_C = 11.5;
    /** Half-band radius for white fermentation (°C). Ideal range 7–16°C → radius 4.5°C. */
    static final double WHITE_TEMP_RADIUS_C = 4.5;

    // ── ABV conversion factors ───────────────────────────────────────────────

    /**
     * Maximum Brix-to-ABV conversion factor (0.65) at ideal temperature,
     * healthy yeast, complete fermentation.
     */
    static final double ABV_CONVERSION_MAX = 0.58;

    /**
     * Minimum Brix-to-ABV conversion factor at worst-case (but not stuck)
     * temperature conditions.
     */
    static final double ABV_CONVERSION_MIN = 0.55;

    // ── Stuck fermentation ───────────────────────────────────────────────────

    /**
     * Fraction of sugar converted before fermentation halts when stuck.
     * ~40 % conversion → high residual sugar, ~50 % of normal ABV.
     */
    static final double STUCK_CONVERSION_FRACTION = 0.40;

    // ── YAN / reduction risk ─────────────────────────────────────────────────

    /**
     * YAN below this threshold (mg/L) triggers REDUCTION_H2S risk in reds.
     * Saperavi at &lt;100 mg/L is dangerously low for complete fermentation.
     */
    static final double YAN_RED_LOW_THRESHOLD_MGL = 100.0;

    /**
     * YAN below this threshold triggers H2S risk in whites (higher requirement
     * because cool fermentations stress yeast more on nitrogen).
     */
    static final double YAN_WHITE_LOW_THRESHOLD_MGL = 120.0;

    // ── Cap tending / cleanliness thresholds ─────────────────────────────────

    /**
     * tending01 below this triggers elevated VA/oxidation risk in RED fermentations.
     * Good cap management keeps the pomace moist and anaerobic.
     */
    static final double TENDING_POOR_THRESHOLD = 0.40;

    /**
     * tending01 below this compounds poor hygiene into certain VA fault territory.
     */
    static final double TENDING_VERY_POOR_THRESHOLD = 0.20;

    // ── Acidity evolution coefficients ──────────────────────────────────────

    /**
     * Fraction of TA degraded during fermentation at normal conditions.
     * Represents CO₂ scrubbing and minor biological activity.
     */
    static final double TA_FERMENT_LOSS_FRACTION = 0.06;

    /**
     * pH rise during fermentation (acid consumption lifts pH slightly).
     * Applied as a flat addition on top of the must pH.
     */
    static final double PH_FERMENT_RISE = 0.10;

    // ── Extraction parameters (reds only) ────────────────────────────────────

    /**
     * Base extraction for RED/KAKHETIAN when temperature and tending are both ideal.
     */
    static final double EXTRACTION_BASE_RED = 0.80;

    /**
     * Extraction per unit of tending01 above the base contribution.
     * extraction01 = EXTRACTION_BASE_RED × tempFactor × tending01
     */
    static final double EXTRACTION_TENDING_WEIGHT = 0.90;

    /** Extraction for non-red methods (minimal skin contact). */
    static final double EXTRACTION_NON_RED = 0.15;

    // ── Cleanliness parameters ────────────────────────────────────────────────

    /**
     * Base cleanliness for a well-tended fermentation with adequate YAN.
     * Starts at 1.0 and is reduced by each fault-risk factor.
     */
    static final double CLEANLINESS_BASE = 1.00;

    /** Cleanliness penalty for low YAN (H2S risk). */
    static final double CLEANLINESS_LOW_YAN_PENALTY  = 0.25;

    /** Cleanliness penalty for poor cap tending. */
    static final double CLEANLINESS_POOR_TENDING_PENALTY = 0.20;

    /** Cleanliness penalty for very poor cap tending (additional). */
    static final double CLEANLINESS_VERY_POOR_TENDING_PENALTY = 0.25;

    /** Cleanliness penalty for stuck fermentation. */
    static final double CLEANLINESS_STUCK_PENALTY = 0.50;

    // ── Jitter ───────────────────────────────────────────────────────────────

    /** Bounded Gaussian jitter scale on ABV (±% ABV). */
    static final double JITTER_SCALE = 0.15;

    /** RNG stream name for fermentation jitter. */
    private static final String STREAM_FERMENT = "cellar.ferment";

    // ── Fermenter implementation ──────────────────────────────────────────────

    @Override
    public CellarResult ferment(MustProfile must, FermentMethod method,
                                double cellarTempC, double tending01,
                                RngStreams rng) {

        RandomGenerator fermentRng = rng.stream(STREAM_FERMENT);

        // 1. Stuck-ferment check (temperature too high)
        boolean stuck = cellarTempC > STUCK_TEMP_C;

        // 2. Temperature efficiency for the chosen method
        double tempEfficiency = computeTempEfficiency(method, cellarTempC, stuck);

        // 3. ABV
        double abv = computeAbv(must.brix(), tempEfficiency, stuck, fermentRng);

        // 4. Acidity evolution
        double taEvolution = must.taGL() * (1.0 - TA_FERMENT_LOSS_FRACTION * tempEfficiency);
        double pHFinal     = must.pH() + PH_FERMENT_RISE * tempEfficiency;

        // 5. Fault determination
        boolean isRedMethod = isRedMethod(method);
        Fault fault = resolveFault(stuck, must.yanMgL(), tending01, isRedMethod);

        // 6. Extraction
        double extraction = computeExtraction(method, tempEfficiency, tending01, stuck);

        // 7. Cleanliness
        double cleanliness = computeCleanliness(stuck, must.yanMgL(), tending01, isRedMethod);

        // 8. Small bounded jitter on ABV (reproducible — uses named stream)
        double jitter = boundedGaussian(fermentRng) * JITTER_SCALE;
        double abvFinal = Math.max(5.0, Math.min(22.0, abv + jitter));

        return new CellarResult(
                round2(abvFinal),
                round2(Math.max(2.0, taEvolution)),
                round2(clamp(pHFinal, 2.8, 4.5)),
                fault,
                round2(clamp01(extraction)),
                round2(clamp01(cleanliness))
        );
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Temperature efficiency in [0,1]: 1.0 at the ideal centre for this method,
     * declining linearly as temperature deviates from the optimal band.
     * Stuck ferment → 0.0 (no meaningful fermentation).
     */
    private static double computeTempEfficiency(FermentMethod method, double tempC, boolean stuck) {
        if (stuck) return 0.0;
        double centre = isRedMethod(method) ? RED_TEMP_CENTRE_C  : WHITE_TEMP_CENTRE_C;
        double radius = isRedMethod(method) ? RED_TEMP_RADIUS_C  : WHITE_TEMP_RADIUS_C;
        double deviation = Math.abs(tempC - centre);
        // 1.0 within the band; falls linearly to 0 at 2× the radius
        return clamp01(1.0 - (deviation / radius) * 0.5);
    }

    /**
     * ABV: {@code startBrix × conversionFactor}.
     *
     * <p>conversionFactor is interpolated between MIN and MAX based on temperature
     * efficiency.  Stuck fermentation converts only {@link #STUCK_CONVERSION_FRACTION}
     * of the available sugar.
     */
    private static double computeAbv(double startBrix, double tempEfficiency,
                                     boolean stuck, RandomGenerator rng) {
        if (stuck) {
            double partialConversion = startBrix * STUCK_CONVERSION_FRACTION * ABV_CONVERSION_MIN;
            return Math.max(4.0, partialConversion);
        }
        double convFactor = ABV_CONVERSION_MIN
                + tempEfficiency * (ABV_CONVERSION_MAX - ABV_CONVERSION_MIN);
        return startBrix * convFactor;
    }

    /**
     * Fault priority: STUCK > REDUCTION_H2S > VOLATILE_ACIDITY > OXIDATION > NONE.
     */
    private static Fault resolveFault(boolean stuck, double yanMgL, double tending01,
                                      boolean isRedMethod) {
        if (stuck) return Fault.STUCK_FERMENT;

        double yanThreshold = isRedMethod ? YAN_RED_LOW_THRESHOLD_MGL : YAN_WHITE_LOW_THRESHOLD_MGL;
        if (yanMgL < yanThreshold) return Fault.REDUCTION_H2S;

        if (isRedMethod) {
            if (tending01 < TENDING_VERY_POOR_THRESHOLD) return Fault.VOLATILE_ACIDITY;
            if (tending01 < TENDING_POOR_THRESHOLD)       return Fault.OXIDATION;
        }

        return Fault.NONE;
    }

    /**
     * Extraction for reds: driven by temperature (warm = better extraction),
     * tending (cap management), and whether fermentation completed.
     * Non-red methods get a flat low extraction (minimal skin contact).
     */
    private static double computeExtraction(FermentMethod method, double tempEfficiency,
                                            double tending01, boolean stuck) {
        if (!isRedMethod(method)) return EXTRACTION_NON_RED;
        if (stuck) return EXTRACTION_NON_RED; // stuck = no cap management possible

        // Base extraction driven by temp efficiency × tending quality
        return EXTRACTION_BASE_RED * tempEfficiency * (EXTRACTION_TENDING_WEIGHT * tending01
                + (1.0 - EXTRACTION_TENDING_WEIGHT));
    }

    /**
     * Cleanliness: starts at 1.0, reduced by each fault-risk factor.
     * Stuck fermentation imposes the largest penalty.
     */
    private static double computeCleanliness(boolean stuck, double yanMgL,
                                             double tending01, boolean isRedMethod) {
        double c = CLEANLINESS_BASE;

        if (stuck) {
            c -= CLEANLINESS_STUCK_PENALTY;
        } else {
            double yanThreshold = isRedMethod ? YAN_RED_LOW_THRESHOLD_MGL : YAN_WHITE_LOW_THRESHOLD_MGL;
            if (yanMgL < yanThreshold) c -= CLEANLINESS_LOW_YAN_PENALTY;

            if (isRedMethod) {
                if (tending01 < TENDING_POOR_THRESHOLD) {
                    c -= CLEANLINESS_POOR_TENDING_PENALTY;
                }
                if (tending01 < TENDING_VERY_POOR_THRESHOLD) {
                    c -= CLEANLINESS_VERY_POOR_TENDING_PENALTY;
                }
            }
        }
        return clamp01(c);
    }

    /**
     * Returns true for methods that involve red-wine cap management (RED, KAKHETIAN).
     * IMERETIAN is partial but close to amber/orange; treat as white-like for temp band.
     */
    private static boolean isRedMethod(FermentMethod method) {
        return method == FermentMethod.RED || method == FermentMethod.KAKHETIAN;
    }

    /** Bounded Gaussian sample in roughly [-3, 3] via Box-Muller, clamped. */
    private static double boundedGaussian(RandomGenerator rng) {
        double u1 = Math.max(rng.nextDouble(), 1e-10);
        double u2 = rng.nextDouble();
        double z  = Math.sqrt(-2.0 * Math.log(u1)) * Math.cos(2.0 * Math.PI * u2);
        return clamp(z, -2.5, 2.5);
    }

    private static double clamp01(double v) { return Math.max(0.0, Math.min(1.0, v)); }
    private static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }
    private static double round2(double v) { return Math.round(v * 100.0) / 100.0; }
}
