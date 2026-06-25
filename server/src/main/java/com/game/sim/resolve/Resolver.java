package com.game.sim.resolve;

import com.game.core.data.Fault;
import com.game.core.data.FermentMethod;
import com.game.core.data.MustProfile;
import com.game.core.data.Variety;
import com.game.core.data.Vintage;
import com.game.core.data.WineLot;
import com.game.core.data.WineStyle;
import com.game.sim.cellar.CellarResult;

import java.util.TreeMap;

/**
 * Combines vine + cellar data into a finished {@link WineLot}.
 *
 * <p>Frozen seam per SIM-SPEC §3.7 (GDD Part 5.11).
 *
 * <h2>Quality formula</h2>
 *
 * <pre>
 * quality = (fruitScore × W_FRUIT
 *          + ripenessBalance × W_BALANCE
 *          + extraction × W_EXTRACT
 *          + cleanliness × W_CLEAN)
 *          × faultPenalty
 *          × 100
 * </pre>
 *
 * All component scores are in [0,1]; result clamped to [0, 100].
 *
 * <h2>Ripeness balance (Saperavi reds)</h2>
 *
 * <p>Optimal Saperavi pick window: brix ~23–25 °Bx, TA ~6.5–8 g/L,
 * tanninRipeness01 ≥ 0.55.  Each axis penalises deviation from its ideal band.
 *
 * <h2>Ageability</h2>
 *
 * <p>Driven by tannin ripeness, acidity, and pH.  Saperavi is naturally long-lived;
 * high tannin + firm acid = 10+ years, poor structure = 2–3 years.
 *
 * <h2>Aroma</h2>
 *
 * <p>Three descriptors ("dark-fruit", "spice", "acid") whose intensities are derived
 * from variety + ripeness.  Keys are in a {@link java.util.TreeMap} for sorted,
 * deterministic iteration — no HashMap.
 */
public final class Resolver {

    // ── Quality component weights (must sum to 1.0) ──────────────────────────

    /** Weight of fruit health score in quality. */
    private static final double W_FRUIT   = 0.30;
    /** Weight of ripeness balance in quality. */
    private static final double W_BALANCE = 0.30;
    /** Weight of extraction score in quality. */
    private static final double W_EXTRACT = 0.20;
    /** Weight of cleanliness score in quality. */
    private static final double W_CLEAN   = 0.20;

    static {
        assert Math.abs((W_FRUIT + W_BALANCE + W_EXTRACT + W_CLEAN) - 1.0) < 1e-9
                : "Quality weights must sum to 1.0";
    }

    // ── Fault quality penalties (multiplicative) ─────────────────────────────

    /** Quality multiplier when wine is fault-free. */
    private static final double FAULT_NONE_MULTIPLIER          = 1.00;
    /** Quality multiplier for OXIDATION (low but salvageable). */
    private static final double FAULT_OXIDATION_MULTIPLIER     = 0.70;
    /** Quality multiplier for VOLATILE_ACIDITY (vinegar taint, severe). */
    private static final double FAULT_VA_MULTIPLIER            = 0.55;
    /** Quality multiplier for REDUCTION_H2S (sulphur taint, significant). */
    private static final double FAULT_H2S_MULTIPLIER           = 0.65;
    /** Quality multiplier for STUCK_FERMENT (incomplete; sweet/unstable). */
    private static final double FAULT_STUCK_MULTIPLIER         = 0.40;

    // ── Ripeness balance thresholds (Saperavi) ───────────────────────────────

    /** Ideal minimum Brix at pick. */
    private static final double BRIX_IDEAL_LOW  = 23.0;
    /** Ideal centre Brix. */
    private static final double BRIX_IDEAL_MID  = 24.5;
    /** Ideal maximum Brix. */
    private static final double BRIX_IDEAL_HIGH = 26.0;

    /** Ideal TA at pick (g/L) — floor; below = over-ripe and flat. */
    private static final double TA_IDEAL_LOW    = 6.0;
    /** Ideal TA at pick (g/L) — ceiling; above = underripe and harsh. */
    private static final double TA_IDEAL_HIGH   = 8.5;
    /** TA ideal centre. */
    private static final double TA_IDEAL_MID    = 7.25;

    /** Tannin ripeness minimum for top quality reds. */
    private static final double TANNIN_IDEAL_MIN = 0.55;

    // ── Ageability parameters (years) ────────────────────────────────────────

    /** Maximum ageability for a perfectly structured Saperavi. */
    private static final double AGE_MAX_YEARS   = 20.0;
    /** Minimum ageability (anything is drinkable for at least this long). */
    private static final double AGE_MIN_YEARS   = 2.0;
    /** Weight of tannin in ageability calculation. */
    private static final double AGE_W_TANNIN    = 0.45;
    /** Weight of acid (TA) in ageability calculation. */
    private static final double AGE_W_ACID      = 0.35;
    /** Weight of pH (lower = better for ageing) in ageability calculation. */
    private static final double AGE_W_PH        = 0.20;

    /** TA reference for maximum acid ageability contribution (g/L). */
    private static final double AGE_TA_HIGH_REF  = 8.0;
    /** TA below which acid contribution drops off (g/L). */
    private static final double AGE_TA_LOW_REF   = 4.0;
    /** pH below which pH contribution is maximum. */
    private static final double AGE_PH_LOW_REF   = 3.20;
    /** pH above which pH contribution is zero. */
    private static final double AGE_PH_HIGH_REF  = 3.80;

    // ── Aroma intensity parameters (Saperavi) ────────────────────────────────

    /** Maximum dark-fruit intensity at optimal ripeness. */
    private static final double DARK_FRUIT_BASE  = 0.85;
    /** Spice intensity scaling (tannin-driven). */
    private static final double SPICE_BASE        = 0.70;
    /** Acid aroma intensity (acid-driven). */
    private static final double ACID_NOTE_BASE    = 0.60;

    private Resolver() {}

    /**
     * Combine vine and cellar outputs into a finished {@link WineLot}.
     *
     * <p>Region is read from {@code vintage.region()} — no additional wiring is
     * needed at the call site.  For KAKHETI + SAPERAVI + RED the output is
     * mathematically identical to Phase 0 (terroir fit = 1.0, style = RED,
     * appellation = true now replaces the always-false Phase-0 stub).
     *
     * @param variety     grape variety
     * @param method      fermentation method used
     * @param must        must profile from harvest
     * @param cellar      fermentation result
     * @param vintage     season summary (carries the region)
     * @param suitability site suitability score 0..1 (from SiteSuitability.score)
     * @param label       player-assigned label string
     * @return the completed wine lot
     */
    public static WineLot resolve(
            Variety variety,
            FermentMethod method,
            MustProfile must,
            CellarResult cellar,
            Vintage vintage,
            double suitability,
            String label) {

        // ── Region (derived from Vintage — no call-site wiring needed) ────────
        com.game.core.data.Region region = vintage != null ? vintage.region() : null;

        // ── Style: base from fermentation method; override to WHITE for white
        //    varieties fermented via the RED method (conventional white vinification)
        WineStyle style = styleFromMethod(method, variety);

        // ── Terroir fit: quality multiplier ∈ (0,1]; 1.0 for KAKHETI/SAPERAVI ─
        double terroirFit = AppellationRules.terroirFit(region, variety);

        // ── Quality components ────────────────────────────────────────────────
        double fruitScore      = must.fruitHealth01() * suitability;
        double ripenessBalance = computeRipenessBalance(must, style);
        double extractionScore = cellar.extraction01();
        double cleanlinessScore= cellar.cleanliness01();

        double faultPenalty = faultMultiplier(cellar.fault());

        // terroirFit is applied after the weighted sum and fault penalty so the
        // KAKHETI/SAPERAVI path (terroirFit=1.0) is numerically unchanged.
        double rawQuality = (fruitScore   * W_FRUIT
                           + ripenessBalance * W_BALANCE
                           + extractionScore * W_EXTRACT
                           + cleanlinessScore * W_CLEAN)
                           * faultPenalty
                           * terroirFit;

        double quality = clamp(rawQuality * 100.0, 0.0, 100.0);

        // ── Ageability ────────────────────────────────────────────────────────
        double ageabilityYears = computeAgeability(must, cellar);

        // ── Aroma map (sorted for determinism) ───────────────────────────────
        TreeMap<String, Double> aroma = buildAroma(variety, must, style);

        // ── Appellation: region+variety+method aware ──────────────────────────
        boolean appellationOk = AppellationRules.appellationOk(region, variety, method);

        return new WineLot(
                variety,
                style,
                must.vintageYear(),
                must.volumeL(),
                cellar.abv(),
                round1(quality),
                round1(ageabilityYears),
                cellar.fault(),
                aroma,
                appellationOk,
                label
        );
    }

    // ── Style from method + variety ───────────────────────────────────────────

    /**
     * Map fermentation method (and variety colour) to wine style.
     *
     * <p>Rules:
     * <ul>
     *   <li>KAKHETIAN → AMBER (extended skin contact, regardless of variety colour)</li>
     *   <li>IMERETIAN → AMBER (partial skin contact)</li>
     *   <li>SPARKLING_BASE → SPARKLING_BASE</li>
     *   <li>SWEET → SWEET</li>
     *   <li>RED + white variety → WHITE (conventional white vinification, no skin contact)</li>
     *   <li>RED + red variety → RED (standard red — the SAPERAVI/KAKHETI path)</li>
     * </ul>
     *
     * <p>The SAPERAVI (non-white) + RED path returns {@link WineStyle#RED}, unchanged.
     *
     * @param method  fermentation method
     * @param variety grape variety (used for white-path override)
     * @return resolved wine style
     */
    static WineStyle styleFromMethod(FermentMethod method, Variety variety) {
        return switch (method) {
            case KAKHETIAN      -> WineStyle.AMBER;           // extended skin contact
            case IMERETIAN      -> WineStyle.AMBER;           // partial skin
            case SPARKLING_BASE -> WineStyle.SPARKLING_BASE;
            case SWEET          -> WineStyle.SWEET;
            case RED            ->
                    // White variety via RED method = conventional white vinification → WHITE
                    // Non-white (Saperavi, Aleksandrouli, Ojaleshi) → RED (unchanged)
                    AppellationRules.shouldOverrideToWhite(variety, method)
                            ? WineStyle.WHITE
                            : WineStyle.RED;
        };
    }

    /**
     * Overload retained for backward compatibility with tests and internal call sites
     * that do not pass a variety.  Defaults to non-white behaviour (RED method → RED style).
     *
     * @param method fermentation method
     * @return resolved wine style (variety assumed non-white)
     * @deprecated Prefer {@link #styleFromMethod(FermentMethod, Variety)}.
     */
    @Deprecated
    static WineStyle styleFromMethod(FermentMethod method) {
        return styleFromMethod(method, null); // null variety → not white → RED for RED method
    }

    // ── Ripeness balance ─────────────────────────────────────────────────────

    /**
     * Ripeness balance score for reds: penalises deviation from the ideal Brix/TA/tannin window.
     *
     * <p>Each axis contributes 1.0 at optimum, falling off symmetrically outside the ideal band.
     * The combined score is the geometric mean of all three axes (forces all to be decent).
     *
     * <p>For non-red styles (minimal tannin consideration), tannin ripeness is not penalised.
     */
    private static double computeRipenessBalance(MustProfile must, WineStyle style) {
        double brixScore   = triangleScore(must.brix(),  BRIX_IDEAL_LOW, BRIX_IDEAL_MID, BRIX_IDEAL_HIGH);
        double taScore     = triangleScore(must.taGL(),  TA_IDEAL_LOW,   TA_IDEAL_MID,   TA_IDEAL_HIGH);

        if (style == WineStyle.RED || style == WineStyle.AMBER) {
            // Tannin: score rises linearly from 0 at 0.0 to 1.0 at TANNIN_IDEAL_MIN, stays 1.0 above
            double tanninScore = clamp01(must.tanninRipeness01() / TANNIN_IDEAL_MIN);
            // Geometric mean of three scores
            return Math.cbrt(brixScore * taScore * tanninScore);
        }

        // For whites / sparkling / sweet: only Brix and TA matter
        return Math.sqrt(brixScore * taScore);
    }

    /**
     * Triangle function: 1.0 at mid, 0.0 at lo and hi, linear interpolation.
     * Values outside [lo, hi] return 0.
     */
    private static double triangleScore(double v, double lo, double mid, double hi) {
        if (v <= lo || v >= hi) return 0.0;
        if (v <= mid) return (v - lo) / (mid - lo);
        return (hi - v) / (hi - mid);
    }

    // ── Fault multiplier ─────────────────────────────────────────────────────

    private static double faultMultiplier(Fault fault) {
        return switch (fault) {
            case NONE            -> FAULT_NONE_MULTIPLIER;
            case OXIDATION       -> FAULT_OXIDATION_MULTIPLIER;
            case VOLATILE_ACIDITY -> FAULT_VA_MULTIPLIER;
            case REDUCTION_H2S   -> FAULT_H2S_MULTIPLIER;
            case STUCK_FERMENT   -> FAULT_STUCK_MULTIPLIER;
        };
    }

    // ── Ageability ───────────────────────────────────────────────────────────

    /**
     * Estimate ageability in years.
     *
     * <p>Three components (tannin, acid, pH) each contribute 0..1; their weighted
     * sum is scaled to [AGE_MIN_YEARS, AGE_MAX_YEARS].
     *
     * <p>Faults substantially cut ageability (a stuck wine won't age gracefully).
     */
    private static double computeAgeability(MustProfile must, CellarResult cellar) {
        // Tannin: 0 at 0, 1 at 1.0
        double tanninContrib = clamp01(must.tanninRipeness01());

        // Acid: 1.0 at TA_HIGH_REF g/L, 0 at TA_LOW_REF g/L
        double taRange       = AGE_TA_HIGH_REF - AGE_TA_LOW_REF;
        double acidContrib   = clamp01((cellar.finalTaGL() - AGE_TA_LOW_REF) / taRange);

        // pH: lower pH → better. 1.0 at PH_LOW_REF or below; 0 at PH_HIGH_REF or above
        double phRange       = AGE_PH_HIGH_REF - AGE_PH_LOW_REF;
        double phContrib     = clamp01((AGE_PH_HIGH_REF - cellar.pH()) / phRange);

        double combined = tanninContrib * AGE_W_TANNIN
                        + acidContrib   * AGE_W_ACID
                        + phContrib     * AGE_W_PH;

        double raw = AGE_MIN_YEARS + combined * (AGE_MAX_YEARS - AGE_MIN_YEARS);

        // Fault penalty: faulted wines don't age well
        double faultFactor = switch (cellar.fault()) {
            case NONE            -> 1.00;
            case OXIDATION       -> 0.60;
            case VOLATILE_ACIDITY -> 0.40;
            case REDUCTION_H2S   -> 0.75;
            case STUCK_FERMENT   -> 0.50;
        };

        return Math.max(AGE_MIN_YEARS, raw * faultFactor);
    }

    // ── Aroma ─────────────────────────────────────────────────────────────────

    /**
     * Build the aroma descriptor map.
     *
     * <p>Keys are alphabetically sorted by using a {@link TreeMap}.
     * Intensity values are 0..1.
     *
     * <p>Three descriptors:
     * <ul>
     *   <li>"acid"       — inversely proportional to TA (retained acidity = fresh/bright)</li>
     *   <li>"dark-fruit" — core Saperavi character, highest at optimal ripeness</li>
     *   <li>"spice"      — tannin/structure-derived note</li>
     * </ul>
     */
    private static TreeMap<String, Double> buildAroma(
            Variety variety, MustProfile must, WineStyle style) {

        TreeMap<String, Double> aroma = new TreeMap<>();

        // Dark-fruit: peaks at brix ~24.5, declines at extremes (very green or raisined)
        double darkFruitRipeness = triangleScore(must.brix(), 18.0, BRIX_IDEAL_MID, 30.0);
        double darkFruit = DARK_FRUIT_BASE * darkFruitRipeness * must.fruitHealth01();
        aroma.put("dark-fruit", round2(clamp01(darkFruit)));

        // Spice: tannin-driven; Saperavi's distinctive peppery/inky note
        double spice = SPICE_BASE * must.tanninRipeness01() * must.fruitHealth01();
        aroma.put("spice", round2(clamp01(spice)));

        // Acid: high TA → prominent acid note (freshness/brightness descriptor)
        // Normalise TA: 0 g/L = 0, 10 g/L = 1.0
        double acidIntensity = ACID_NOTE_BASE * clamp01(must.taGL() / 10.0);
        aroma.put("acid", round2(clamp01(acidIntensity)));

        return aroma;
    }

    // ── Math utilities ────────────────────────────────────────────────────────

    private static double clamp01(double v) { return Math.max(0.0, Math.min(1.0, v)); }
    private static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }
    private static double round1(double v) { return Math.round(v * 10.0) / 10.0; }
    private static double round2(double v) { return Math.round(v * 100.0) / 100.0; }
}
