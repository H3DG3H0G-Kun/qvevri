package com.game.sim.soil;

import com.game.core.data.SiteProfile;
import com.game.core.data.Variety;

/**
 * Computes a site-suitability score for a given variety on a given plot.
 *
 * <p>§3.3 contract: {@code score()} returns a value in [0,1].
 *
 * <h2>Saperavi formula (documented below, no magic numbers)</h2>
 *
 * <p>Saperavi is a thick-skinned, late-ripening Georgian red that thrives on
 * humus-carbonate slopes with good air drainage, south/south-east aspect,
 * moderate altitude, and firm structure.  It handles acidity well but struggles
 * with waterlogging or persistent late frost on valley floors.
 *
 * <p>The score is assembled from five independent sub-scores each in [0,1],
 * then combined with documented weights:
 *
 * <pre>
 * score = w_soil * soilScore
 *       + w_slope * slopeScore
 *       + w_aspect * aspectScore
 *       + w_frost * frostScore
 *       + w_vigor * vigorScore
 * </pre>
 *
 * where all weights sum to 1.0.
 *
 * <h3>Sub-score definitions</h3>
 * <ul>
 *   <li><b>soilScore</b>: based on soil vigor01 (optimum ~0.40–0.55 for Saperavi;
 *       penalise extremes) and waterHolding01 (medium preferred; high=rot risk,
 *       very low=drought stress).  Combined as a Gaussian-like penalty around the
 *       target mid-points.</li>
 *   <li><b>slopeScore</b>: slope 8–18° is ideal; flat = poor air/water drainage;
 *       >25° = erosion/access issues.  Piecewise linear peak at ~13°.</li>
 *   <li><b>aspectScore</b>: south-facing (180°) is optimal; east (90°) and
 *       south-east (135°) also good; north (0°/360°) worst.  Cosine of deviation
 *       from 180°.</li>
 *   <li><b>frostScore</b>: Saperavi buds late (risk mitigation) but is still
 *       damaged by late frosts; site.frostRisk + soil.frostBias01 together.
 *       Score = 1 - combinedFrostRisk (clamped 0..1).</li>
 *   <li><b>vigorScore</b>: Saperavi needs moderate vigour; excess (chernozem)
 *       means canopy management issues.  Penalise vigour > VIGOR_TARGET_MAX.</li>
 * </ul>
 */
public final class SiteSuitability {

    private SiteSuitability() {}

    // -----------------------------------------------------------------------
    // Variety-specific constants  (Saperavi)
    // -----------------------------------------------------------------------

    /** Ideal slope range (degrees): below this is flat/poor-drainage. */
    private static final double SLOPE_IDEAL_LOW_DEG  = 8.0;
    /** Centre of optimal slope range. */
    private static final double SLOPE_IDEAL_PEAK_DEG = 13.0;
    /** Upper boundary; steeper than this is disadvantageous. */
    private static final double SLOPE_IDEAL_HIGH_DEG = 25.0;

    /** Best aspect = due south (180°). */
    private static final double ASPECT_IDEAL_DEG = 180.0;

    /** Soil vigor01 sweet-spot for Saperavi: above this is over-vigorous. */
    private static final double VIGOR_TARGET_MIN = 0.30;
    private static final double VIGOR_TARGET_MAX = 0.60;

    /** Soil waterHolding01 sweet-spot: very high = rot risk. */
    private static final double WATER_TARGET_MIN = 0.35;
    private static final double WATER_TARGET_MAX = 0.70;

    /**
     * Frost risk weight: combined site + soil frost bias above this threshold
     * starts penalising aggressively.
     */
    private static final double FROST_COMBINED_DANGER = 0.55;

    // -----------------------------------------------------------------------
    // Component weights (must sum to 1.0)
    // -----------------------------------------------------------------------
    private static final double W_SOIL   = 0.30; // soil physical match
    private static final double W_SLOPE  = 0.20; // topographic drainage
    private static final double W_ASPECT = 0.20; // solar radiation
    private static final double W_FROST  = 0.20; // frost penalty
    private static final double W_VIGOR  = 0.10; // vigour balance

    static {
        assert Math.abs((W_SOIL + W_SLOPE + W_ASPECT + W_FROST + W_VIGOR) - 1.0) < 1e-9
            : "Weights must sum to 1.0";
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Returns a suitability score in [0,1] for the given variety on the given site.
     *
     * <p>Currently only {@link Variety#SAPERAVI} is implemented (Phase 0 scope).
     * Any other variety returns 0.5 (neutral placeholder).
     *
     * @param v    grape variety (non-null)
     * @param site plot geometry and soil (non-null)
     * @return suitability in [0,1]; higher = better match
     */
    public static double score(Variety v, SiteProfile site) {
        if (v == null)    throw new IllegalArgumentException("Variety must not be null");
        if (site == null) throw new IllegalArgumentException("SiteProfile must not be null");

        return switch (v) {
            case SAPERAVI      -> scoreSaperavi(site);
            case RKATSITELI    -> scoreRkatsiteli(site);
            case MTSVANE, KISI -> scoreMtsvaneKisi(site);
            case TSOLIKOURI, TSITSKA -> scoreTsolikouriTsitska(site);
            case CHINURI       -> scoreChinuri(site);
            case ALEKSANDROULI -> scoreAleksandrouli(site);
            case OJALESHI      -> scoreOjaleshi(site);
            case CHKHAVERI     -> scoreChkhaveri(site);
        };
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private static double scoreSaperavi(SiteProfile site) {
        SoilStat soil = SoilTypes.profile(site.soil());

        double soilScore   = soilScore(soil);
        double slopeScore  = slopeScore(site.slopeDeg());
        double aspectScore = aspectScore(site.aspectDeg());
        double frostScore  = frostScore(site.frostRisk(), soil.frostBias01());
        double vigorScore  = vigorScore(soil.vigor01());

        double raw = W_SOIL   * soilScore
                   + W_SLOPE  * slopeScore
                   + W_ASPECT * aspectScore
                   + W_FROST  * frostScore
                   + W_VIGOR  * vigorScore;

        return clamp01(raw);
    }

    // -----------------------------------------------------------------------
    // Variety-specific scoring methods (new varieties)
    // -----------------------------------------------------------------------

    /**
     * Rkatsiteli: prefers humus-carbonate / clay-limestone, moderate altitude,
     * south/south-east aspect. High acid retention favours cooler-leaning sites.
     */
    private static double scoreRkatsiteli(SiteProfile site) {
        SoilStat soil = SoilTypes.profile(site.soil());
        // Rkatsiteli tolerates higher water-holding soils slightly better than Saperavi
        double soilScore   = bandScore(soil.vigor01(),        0.25, 0.55)
                           * 0.5 + bandScore(soil.waterHolding01(), 0.40, 0.75) * 0.5;
        double slopeScore  = slopeScore(site.slopeDeg());
        double aspectScore = aspectScore(site.aspectDeg());
        double frostScore  = clamp01(1.0 - (0.70 * site.frostRisk() + 0.30 * soil.frostBias01()));
        double vigorScore  = bandScore(soil.vigor01(), 0.25, 0.55);
        return clamp01(W_SOIL * soilScore + W_SLOPE * slopeScore
                     + W_ASPECT * aspectScore + W_FROST * frostScore + W_VIGOR * vigorScore);
    }

    /**
     * Mtsvane / Kisi: aromatic whites; prefer south-facing moderate-altitude sites.
     * Slightly more sensitive to frost than Saperavi.
     */
    private static double scoreMtsvaneKisi(SiteProfile site) {
        SoilStat soil = SoilTypes.profile(site.soil());
        double soilScore   = (bandScore(soil.vigor01(), 0.30, 0.55)
                            + bandScore(soil.waterHolding01(), 0.40, 0.70)) / 2.0;
        double slopeScore  = slopeScore(site.slopeDeg());
        double aspectScore = aspectScore(site.aspectDeg());
        double frostScore  = clamp01(1.0 - (0.75 * site.frostRisk() + 0.25 * soil.frostBias01()));
        double vigorScore  = bandScore(soil.vigor01(), 0.30, 0.55);
        return clamp01(W_SOIL * soilScore + W_SLOPE * slopeScore
                     + W_ASPECT * aspectScore + W_FROST * frostScore + W_VIGOR * vigorScore);
    }

    /**
     * Tsolikouri / Tsitska: Imeretian late-acid whites; prefer cooler, wetter sites;
     * moderate altitude, south/south-east aspect.
     */
    private static double scoreTsolikouriTsitska(SiteProfile site) {
        SoilStat soil = SoilTypes.profile(site.soil());
        double soilScore   = (bandScore(soil.vigor01(), 0.30, 0.60)
                            + bandScore(soil.waterHolding01(), 0.45, 0.75)) / 2.0;
        double slopeScore  = slopeScore(site.slopeDeg());
        double aspectScore = aspectScore(site.aspectDeg());
        double frostScore  = clamp01(1.0 - (0.65 * site.frostRisk() + 0.35 * soil.frostBias01()));
        double vigorScore  = bandScore(soil.vigor01(), 0.30, 0.60);
        return clamp01(W_SOIL * soilScore + W_SLOPE * slopeScore
                     + W_ASPECT * aspectScore + W_FROST * frostScore + W_VIGOR * vigorScore);
    }

    /**
     * Chinuri: Kartlian light white; wide site tolerance, prefers well-drained soils.
     */
    private static double scoreChinuri(SiteProfile site) {
        SoilStat soil = SoilTypes.profile(site.soil());
        double soilScore   = (bandScore(soil.vigor01(), 0.30, 0.60)
                            + bandScore(soil.waterHolding01(), 0.30, 0.65)) / 2.0;
        double slopeScore  = slopeScore(site.slopeDeg());
        double aspectScore = aspectScore(site.aspectDeg());
        double frostScore  = clamp01(1.0 - (0.70 * site.frostRisk() + 0.30 * soil.frostBias01()));
        double vigorScore  = bandScore(soil.vigor01(), 0.30, 0.60);
        return clamp01(W_SOIL * soilScore + W_SLOPE * slopeScore
                     + W_ASPECT * aspectScore + W_FROST * frostScore + W_VIGOR * vigorScore);
    }

    /**
     * Aleksandrouli: Racha thin-skinned red; prefers well-drained, high-altitude sites
     * with lower frost risk; moderate-vigour soils.
     */
    private static double scoreAleksandrouli(SiteProfile site) {
        SoilStat soil = SoilTypes.profile(site.soil());
        double soilScore   = (bandScore(soil.vigor01(), 0.25, 0.50)
                            + bandScore(soil.waterHolding01(), 0.35, 0.65)) / 2.0;
        double slopeScore  = slopeScore(site.slopeDeg());
        double aspectScore = aspectScore(site.aspectDeg());
        double frostScore  = clamp01(1.0 - (0.80 * site.frostRisk() + 0.20 * soil.frostBias01()));
        double vigorScore  = bandScore(soil.vigor01(), 0.25, 0.50);
        return clamp01(W_SOIL * soilScore + W_SLOPE * slopeScore
                     + W_ASPECT * aspectScore + W_FROST * frostScore + W_VIGOR * vigorScore);
    }

    /**
     * Ojaleshi: Samegrelo aromatic red; tolerates higher humidity and water-holding;
     * prefers warmer aspects.
     */
    private static double scoreOjaleshi(SiteProfile site) {
        SoilStat soil = SoilTypes.profile(site.soil());
        double soilScore   = (bandScore(soil.vigor01(), 0.35, 0.65)
                            + bandScore(soil.waterHolding01(), 0.45, 0.80)) / 2.0;
        double slopeScore  = slopeScore(site.slopeDeg());
        double aspectScore = aspectScore(site.aspectDeg());
        double frostScore  = clamp01(1.0 - (0.65 * site.frostRisk() + 0.35 * soil.frostBias01()));
        double vigorScore  = bandScore(soil.vigor01(), 0.35, 0.65);
        return clamp01(W_SOIL * soilScore + W_SLOPE * slopeScore
                     + W_ASPECT * aspectScore + W_FROST * frostScore + W_VIGOR * vigorScore);
    }

    /**
     * Chkhaveri: Guria rosé variety; tolerates humid/warm conditions;
     * lower frost sensitivity, moderate vigour preference.
     */
    private static double scoreChkhaveri(SiteProfile site) {
        SoilStat soil = SoilTypes.profile(site.soil());
        double soilScore   = (bandScore(soil.vigor01(), 0.30, 0.55)
                            + bandScore(soil.waterHolding01(), 0.40, 0.75)) / 2.0;
        double slopeScore  = slopeScore(site.slopeDeg());
        double aspectScore = aspectScore(site.aspectDeg());
        double frostScore  = clamp01(1.0 - (0.60 * site.frostRisk() + 0.40 * soil.frostBias01()));
        double vigorScore  = bandScore(soil.vigor01(), 0.30, 0.55);
        return clamp01(W_SOIL * soilScore + W_SLOPE * slopeScore
                     + W_ASPECT * aspectScore + W_FROST * frostScore + W_VIGOR * vigorScore);
    }

    /**
     * Soil match: penalise when vigor01 or waterHolding01 is outside the target band.
     * Uses a tent function: 1.0 at optimum, 0.0 at boundary extremes.
     */
    private static double soilScore(SoilStat soil) {
        double vigorPenalty = bandScore(soil.vigor01(), VIGOR_TARGET_MIN, VIGOR_TARGET_MAX);
        double waterPenalty = bandScore(soil.waterHolding01(), WATER_TARGET_MIN, WATER_TARGET_MAX);
        // Average of both axes, weighted equally
        return (vigorPenalty + waterPenalty) / 2.0;
    }

    /**
     * Returns 1.0 if {@code value} is within [lo, hi], declining linearly
     * outside: 0.0 if {@code value <= lo - margin} or {@code value >= hi + margin}
     * where margin = (hi - lo) / 2.
     */
    private static double bandScore(double value, double lo, double hi) {
        if (value >= lo && value <= hi) return 1.0;
        double margin = (hi - lo) / 2.0;
        if (value < lo) return clamp01((value - (lo - margin)) / margin);
        else            return clamp01(((hi + margin) - value) / margin);
    }

    /**
     * Slope score: piecewise linear, peak 1.0 at SLOPE_IDEAL_PEAK_DEG,
     * falls to 0 below 0° and above 45°.
     */
    private static double slopeScore(double slopeDeg) {
        double s = clamp(slopeDeg, 0, 45);
        if (s <= SLOPE_IDEAL_PEAK_DEG) {
            // 0 at 0°, 1.0 at peak
            return s / SLOPE_IDEAL_PEAK_DEG;
        } else if (s <= SLOPE_IDEAL_HIGH_DEG) {
            // 1.0 at peak, declining to ~0.5 at high
            double range = SLOPE_IDEAL_HIGH_DEG - SLOPE_IDEAL_PEAK_DEG;
            return 1.0 - 0.5 * (s - SLOPE_IDEAL_PEAK_DEG) / range;
        } else {
            // 0.5 at high, 0.0 at 45°
            double range = 45.0 - SLOPE_IDEAL_HIGH_DEG;
            return 0.5 * (1.0 - (s - SLOPE_IDEAL_HIGH_DEG) / range);
        }
    }

    /**
     * Aspect score: cosine of deviation from ideal south (180°), mapped to [0,1].
     * South (0° deviation) = 1.0; North (180° deviation) = 0.0.
     */
    private static double aspectScore(double aspectDeg) {
        double deviationRad = Math.toRadians(Math.abs(aspectDeg - ASPECT_IDEAL_DEG));
        // cos(0)=1 (south), cos(π)=-1 (north); remap to [0,1]
        return (1.0 + Math.cos(deviationRad)) / 2.0;
    }

    /**
     * Frost score: combines site.frostRisk and soil.frostBias01.
     * Weighted combination; above FROST_COMBINED_DANGER the score drops steeply.
     *
     * <p>combinedRisk = 0.7 * frostRisk + 0.3 * frostBias01  (site is dominant signal)
     * <br>frostScore = 1 - combinedRisk  (simple inversion)
     */
    private static double frostScore(double siteFrostRisk, double soilFrostBias01) {
        double combined = 0.70 * siteFrostRisk + 0.30 * soilFrostBias01;
        return clamp01(1.0 - combined);
    }

    /**
     * Vigour score: Saperavi favours moderate vigour.
     * Penalty ramps in linearly above VIGOR_TARGET_MAX and below VIGOR_TARGET_MIN.
     */
    private static double vigorScore(double vigor01) {
        return bandScore(vigor01, VIGOR_TARGET_MIN, VIGOR_TARGET_MAX);
    }

    // -----------------------------------------------------------------------
    // Math utilities
    // -----------------------------------------------------------------------

    private static double clamp01(double v) { return Math.max(0.0, Math.min(1.0, v)); }
    private static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }
}
