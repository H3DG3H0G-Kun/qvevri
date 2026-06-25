package com.game.sim.vine;

import com.game.core.data.DailyWeather;
import com.game.core.data.PhenoStage;
import com.game.core.data.PruningDecision;
import com.game.core.data.SiteProfile;
import com.game.core.data.Variety;
import com.game.core.data.VarietyProfile;
import com.game.core.data.VarietyProfiles;
import com.game.core.data.VineState;
import com.game.sim.soil.SoilStat;
import com.game.sim.soil.SoilTypes;

/**
 * Deterministic, daily-tick vine simulator parameterised by grape variety.
 *
 * <p>The no-arg constructor defaults to {@link Variety#SAPERAVI}, reproducing
 * the original hardcoded behaviour exactly so all 122 existing tests remain green.
 * Pass a different {@link Variety} (or {@link VarietyProfile}) to the appropriate
 * constructor for multi-variety support.
 *
 * <h2>Phenology state machine</h2>
 *
 * <p>Stages advance in a fixed order determined by temperature and accumulated
 * growing-degree-days (GDD, base {@value #GDD_BASE_C}°C):
 *
 * <pre>
 * DORMANCY
 *   -> BUD_SWELL   : day-of-year >= DOY_BUD_SWELL_START AND mean temp > DORMANCY_BREAK_TEMP_C
 *   -> BUDBREAK     : consecutive days with mean >= BUDBREAK_SUSTAINED_TEMP_C
 *                     (count maintained via gddAccum during pre-budbreak phases)
 *   -> SHOOT_GROWTH : immediately after BUDBREAK
 *   -> FLOWERING    : gddAccum >= profile.gddToFlowering()
 *   -> FRUIT_SET    : gddAccum >= profile.gddToFruitSet()   [yield locked here]
 *   -> BERRY_DEV    : gddAccum >= profile.gddToBerryDev()
 *   -> VERAISON     : gddAccum >= profile.gddToVeraison()
 *   -> RIPENING     : immediately after VERAISON (ripening clocks start)
 *   -> HARVESTED    : triggered externally by HarvestDecision (not in tick)
 * </pre>
 *
 * <h2>Purity constraint</h2>
 *
 * <p>This class is a pure function of its inputs after construction.
 * The {@link VarietyProfile} is immutable and set at construction time.
 * Instantiate once per variety and call {@link #tick} repeatedly.
 */
public final class KakhetiVineSimulator implements VineSimulator {

    // =======================================================================
    // Named constants — fixed across all varieties (biology / physics)
    // =======================================================================

    // --- GDD base (SIM-SPEC §3.2) ------------------------------------------
    /** Growing-degree-day base temperature (°C). */
    public static final double GDD_BASE_C = 10.0;

    // --- Temperature gates for early phenology (universal) ------------------

    /** Minimum day-of-year before bud swell can begin. */
    public static final int DOY_BUD_SWELL_START = 60; // early March

    /** Mean temp threshold to exit DORMANCY into BUD_SWELL. */
    public static final double DORMANCY_BREAK_TEMP_C = 5.0;

    /**
     * Minimum mean temperature required on each of
     * {@value #BUDBREAK_SUSTAINED_DAYS} consecutive days to trigger budbreak.
     */
    public static final double BUDBREAK_SUSTAINED_TEMP_C = 10.0;

    /** Consecutive days ≥ BUDBREAK_SUSTAINED_TEMP_C required to reach BUDBREAK. */
    public static final int BUDBREAK_SUSTAINED_DAYS = 5;

    // --- Bud-load parameters (universal) -----------------------------------

    /** Below this bud-load the vine is underburdened (low yield path). */
    public static final int BUD_LOAD_MIN_FULL  = 8;

    /** Above this bud-load the vine is overburdened (quality penalty starts). */
    public static final int BUD_LOAD_OVERBURDEN_THRESHOLD = 16;

    /** Maximum bud-load before severe overburden penalties kick in hard. */
    public static final int BUD_LOAD_MAX = 30;

    /** Maximum realistic yield per vine (kg). */
    public static final double MAX_YIELD_KG = 8.0;

    /**
     * Health fraction lost per day when severely overburdened
     * (each bud above BUD_LOAD_OVERBURDEN_THRESHOLD contributes this rate).
     */
    public static final double OVERBURDEN_HEALTH_LOSS_PER_BUD_PER_DAY = 0.001;

    // --- YAN (yeast-assimilable nitrogen) -----------------------------------

    /** Baseline YAN at fruit-set (mg/L) for a healthy, balanced vine. */
    public static final double YAN_BASELINE_MGL = 250.0;

    /** YAN penalty fraction per bud above overburden threshold. */
    public static final double YAN_OVERBURDEN_PENALTY_PER_BUD = 0.04; // 4 % per excess bud

    // --- Raisining thresholds (universal backstop) --------------------------

    /**
     * Brix above which the vine enters raisining territory (over-ripening).
     * The penalty is a backstop; the brix value is always capped at
     * {@link VarietyProfile#brixMax()} regardless, so most varieties never reach this.
     */
    public static final double RAISINING_BRIX_THRESHOLD = 25.8;

    /** Health loss per day while Brix exceeds the raisining threshold. */
    public static final double RAISINING_HEALTH_LOSS_PER_DAY = 0.008;

    // --- Overburden Brix slowdown -------------------------------------------

    /**
     * Fraction by which Brix gain per GDD is reduced per bud above
     * BUD_LOAD_OVERBURDEN_THRESHOLD.
     */
    public static final double OVERBURDEN_BRIX_PENALTY_PER_BUD = 0.04;

    // --- Soil vigor effects -------------------------------------------------

    /**
     * Soil vigor01 above this level adds excess vegetative growth, increasing
     * the effective overburden sensitivity.
     */
    public static final double VIGOR_OVERBURDEN_AMPLIFIER_THRESHOLD = 0.65;

    // =======================================================================
    // Legacy Saperavi constants (kept for backwards-compat with tests that
    // reference them directly via KakhetiVineSimulator.CONSTANT_NAME)
    // =======================================================================

    /** @deprecated Use {@link VarietyProfiles#of(Variety)} with SAPERAVI. */
    @Deprecated public static final double GDD_TO_FLOWERING = 200.0;
    /** @deprecated Use {@link VarietyProfiles#of(Variety)} with SAPERAVI. */
    @Deprecated public static final double GDD_TO_FRUIT_SET = 350.0;
    /** @deprecated Use {@link VarietyProfiles#of(Variety)} with SAPERAVI. */
    @Deprecated public static final double GDD_TO_BERRY_DEV = 550.0;
    /** @deprecated Use {@link VarietyProfiles#of(Variety)} with SAPERAVI. */
    @Deprecated public static final double GDD_TO_VERAISON  = 900.0;
    /** @deprecated Use {@link VarietyProfiles#of(Variety)} with SAPERAVI. */
    @Deprecated public static final double KG_PER_BUD          = 0.30;
    /** @deprecated Use {@link VarietyProfiles#of(Variety)} with SAPERAVI. */
    @Deprecated public static final int    BUD_LOAD_BALANCED    = 12;
    /** @deprecated Use {@link VarietyProfiles#of(Variety)} with SAPERAVI. */
    @Deprecated public static final double BRIX_AT_VERAISON     = 8.0;
    /** @deprecated Use {@link VarietyProfiles#of(Variety)} with SAPERAVI. */
    @Deprecated public static final double BRIX_MAX             = 26.0;
    /** @deprecated Use {@link VarietyProfiles#of(Variety)} with SAPERAVI. */
    @Deprecated public static final double BRIX_K               = 0.00564;
    /** @deprecated Use {@link VarietyProfiles#of(Variety)} with SAPERAVI. */
    @Deprecated public static final double TA_AT_VERAISON       = 14.0;
    /** @deprecated Use {@link VarietyProfiles#of(Variety)} with SAPERAVI. */
    @Deprecated public static final double TA_FLOOR             = 4.5;
    /** @deprecated Use {@link VarietyProfiles#of(Variety)} with SAPERAVI. */
    @Deprecated public static final double TA_K                 = 0.00445;
    /** @deprecated Use {@link VarietyProfiles#of(Variety)} with SAPERAVI. */
    @Deprecated public static final double PH_AT_VERAISON       = 2.90;
    /** @deprecated Use {@link VarietyProfiles#of(Variety)} with SAPERAVI. */
    @Deprecated public static final double PH_RISE_PER_GDD      = 0.002;
    /** @deprecated Use {@link VarietyProfiles#of(Variety)} with SAPERAVI. */
    @Deprecated public static final double TANNIN_PER_GDD       = 0.0018;

    // =======================================================================
    // Instance state (immutable after construction)
    // =======================================================================

    /**
     * The variety-specific ripening and phenology profile used by this instance.
     * Defaults to SAPERAVI (byte-identical to the original hardcoded constants).
     */
    private final VarietyProfile profile;

    // =======================================================================
    // Constructors
    // =======================================================================

    /**
     * Default constructor: uses {@link Variety#SAPERAVI}, preserving
     * byte-identical behaviour with the original hardcoded implementation.
     */
    public KakhetiVineSimulator() {
        this.profile = VarietyProfiles.of(Variety.SAPERAVI);
    }

    /**
     * Variety-aware constructor.
     *
     * @param variety the grape variety to simulate; must not be null
     */
    public KakhetiVineSimulator(Variety variety) {
        this.profile = VarietyProfiles.of(variety);
    }

    /**
     * Profile-direct constructor (for testing or future profile injection).
     *
     * @param profile the variety profile to use; must not be null
     */
    public KakhetiVineSimulator(VarietyProfile profile) {
        if (profile == null) throw new IllegalArgumentException("VarietyProfile must not be null");
        this.profile = profile;
    }

    // =======================================================================
    // VineSimulator implementation
    // =======================================================================

    /**
     * {@inheritDoc}
     *
     * <p>Implementation note: all branching is deterministic; no RNG involved.
     * The "warm-days counter" for sustained budbreak detection is encoded in
     * {@code gddAccum} during pre-budbreak stages (DORMANCY, BUD_SWELL).
     * At budbreak it resets to 0.0 and begins accumulating true GDD.
     */
    @Override
    public VineState tick(VineState prev,
                          DailyWeather today,
                          SiteProfile site,
                          double suitability,
                          PruningDecision pruning) {

        // Defensive: clamp suitability to [0,1]
        suitability = clamp01(suitability);

        SoilStat soil   = SoilTypes.profile(site.soil());
        PhenoStage stage = prev.stage();
        int budLoad      = pruning.budLoad();

        return switch (stage) {
            case DORMANCY       -> tickDormancy(prev, today, site, soil, suitability, budLoad);
            case BUD_SWELL      -> tickBudSwell(prev, today, site, soil, suitability, budLoad);
            case BUDBREAK       -> tickBudbreak(prev, today, site, soil, suitability, budLoad);
            case SHOOT_GROWTH   -> tickShootGrowth(prev, today, site, soil, suitability, budLoad);
            case FLOWERING      -> tickFlowering(prev, today, site, soil, suitability, budLoad);
            case FRUIT_SET      -> tickFruitSet(prev, today, site, soil, suitability, budLoad);
            case BERRY_DEVELOPMENT -> tickBerryDev(prev, today, site, soil, suitability, budLoad);
            case VERAISON       -> tickVeraison(prev, today, site, soil, suitability, budLoad);
            case RIPENING       -> tickRipening(prev, today, site, soil, suitability, budLoad);
            case HARVESTED      -> prev; // terminal: no further change
            case LEAF_FALL      -> prev; // post-harvest: no change in this phase
        };
    }

    // =======================================================================
    // Per-stage tick handlers
    // =======================================================================

    /**
     * DORMANCY: vine is dormant.  We watch for early-season warm days.
     *
     * <p>We encode the consecutive-warm-days counter in {@code gddAccum}
     * (it has no agronomic meaning yet).  When the counter reaches
     * {@value #BUDBREAK_SUSTAINED_DAYS} days ≥ DORMANCY_BREAK_TEMP_C
     * AND day-of-year is past DOY_BUD_SWELL_START, advance to BUD_SWELL.
     *
     * <p>Frost during dormancy does not hurt Saperavi materially (handled later).
     */
    private VineState tickDormancy(VineState prev, DailyWeather today,
                                   SiteProfile site, SoilStat soil,
                                   double suitability, int budLoad) {
        if (today.dayOfYear() < DOY_BUD_SWELL_START) {
            // Too early: reset warm-day counter if a cold snap occurs
            double warmCounter = today.meanTempC() >= DORMANCY_BREAK_TEMP_C
                               ? prev.gddAccum() + 1 : 0;
            return copyWith(prev, PhenoStage.DORMANCY, warmCounter,
                            prev.healthFraction(), prev.potentialYieldKg(),
                            prev.brix(), prev.taGL(), prev.pH(),
                            prev.yanMgL(), prev.tanninRipeness01());
        }

        double meanT      = today.meanTempC();
        double warmCounter = meanT >= DORMANCY_BREAK_TEMP_C ? prev.gddAccum() + 1 : 0;

        PhenoStage nextStage = (warmCounter >= BUDBREAK_SUSTAINED_DAYS)
                             ? PhenoStage.BUD_SWELL
                             : PhenoStage.DORMANCY;

        // If advancing to BUD_SWELL, reset the counter to 0 for the next phase
        double nextAccum = (nextStage == PhenoStage.BUD_SWELL) ? 0.0 : warmCounter;

        return copyWith(prev, nextStage, nextAccum,
                        prev.healthFraction(), prev.potentialYieldKg(),
                        prev.brix(), prev.taGL(), prev.pH(),
                        prev.yanMgL(), prev.tanninRipeness01());
    }

    /**
     * BUD_SWELL: buds are swelling; we await sustained warmth for true budbreak.
     *
     * <p>Again we reuse {@code gddAccum} as a consecutive-warm-days counter
     * (resetting to zero if mean temp drops below {@value #BUDBREAK_SUSTAINED_TEMP_C}).
     */
    private VineState tickBudSwell(VineState prev, DailyWeather today,
                                   SiteProfile site, SoilStat soil,
                                   double suitability, int budLoad) {
        double meanT = today.meanTempC();
        double warmCounter = meanT >= BUDBREAK_SUSTAINED_TEMP_C ? prev.gddAccum() + 1 : 0;

        boolean budbreak = warmCounter >= BUDBREAK_SUSTAINED_DAYS;
        PhenoStage nextStage = budbreak ? PhenoStage.BUDBREAK : PhenoStage.BUD_SWELL;

        // If entering BUDBREAK, reset accum to 0 — true GDD starts ticking here
        double nextAccum = budbreak ? 0.0 : warmCounter;

        // Frost damage in late bud-swell (early frost = health hit)
        double health = applyFrostDamage(prev.healthFraction(), today, site, soil, PhenoStage.BUD_SWELL);

        return copyWith(prev, nextStage, nextAccum, health, prev.potentialYieldKg(),
                        prev.brix(), prev.taGL(), prev.pH(), prev.yanMgL(), prev.tanninRipeness01());
    }

    /**
     * BUDBREAK: buds burst.  Immediately advance to SHOOT_GROWTH.
     * We treat budbreak as a single-day transition: tick GDD, then move on.
     */
    private VineState tickBudbreak(VineState prev, DailyWeather today,
                                   SiteProfile site, SoilStat soil,
                                   double suitability, int budLoad) {
        double dailyGdd = dailyGdd(today);
        double gdd      = prev.gddAccum() + dailyGdd;
        double health   = applyFrostDamage(prev.healthFraction(), today, site, soil, PhenoStage.BUDBREAK);

        return copyWith(prev, PhenoStage.SHOOT_GROWTH, gdd, health, prev.potentialYieldKg(),
                        prev.brix(), prev.taGL(), prev.pH(), prev.yanMgL(), prev.tanninRipeness01());
    }

    /**
     * SHOOT_GROWTH: photosynthesis ramps up; GDD accumulates toward flowering.
     * Overburden starts applying a mild health cost here (canopy chaos).
     */
    private VineState tickShootGrowth(VineState prev, DailyWeather today,
                                      SiteProfile site, SoilStat soil,
                                      double suitability, int budLoad) {
        double dailyGdd = dailyGdd(today);
        double gdd      = prev.gddAccum() + dailyGdd;
        double health   = applyFrostDamage(prev.healthFraction(), today, site, soil, PhenoStage.SHOOT_GROWTH);
        health          = applyOverburdenHealthLoss(health, budLoad, soil);

        PhenoStage next = (gdd >= profile.gddToFlowering()) ? PhenoStage.FLOWERING : PhenoStage.SHOOT_GROWTH;

        return copyWith(prev, next, gdd, health, prev.potentialYieldKg(),
                        prev.brix(), prev.taGL(), prev.pH(), prev.yanMgL(), prev.tanninRipeness01());
    }

    /**
     * FLOWERING: delicate stage; frost or overburden damages flower set.
     * Advances to FRUIT_SET when GDD threshold is crossed.
     */
    private VineState tickFlowering(VineState prev, DailyWeather today,
                                    SiteProfile site, SoilStat soil,
                                    double suitability, int budLoad) {
        double dailyGdd = dailyGdd(today);
        double gdd      = prev.gddAccum() + dailyGdd;
        double health   = applyFrostDamage(prev.healthFraction(), today, site, soil, PhenoStage.FLOWERING);
        health          = applyOverburdenHealthLoss(health, budLoad, soil);

        PhenoStage next = (gdd >= profile.gddToFruitSet()) ? PhenoStage.FRUIT_SET : PhenoStage.FLOWERING;

        return copyWith(prev, next, gdd, health, prev.potentialYieldKg(),
                        prev.brix(), prev.taGL(), prev.pH(), prev.yanMgL(), prev.tanninRipeness01());
    }

    /**
     * FRUIT_SET: yield is now locked.
     *
     * <p>potentialYieldKg is set once here (or already set if we arrive mid-stage
     * from a previous partial run — shouldn't happen in a clean simulation but we
     * guard with a zero-check).
     */
    private VineState tickFruitSet(VineState prev, DailyWeather today,
                                   SiteProfile site, SoilStat soil,
                                   double suitability, int budLoad) {
        double dailyGdd = dailyGdd(today);
        double gdd      = prev.gddAccum() + dailyGdd;
        double health   = applyOverburdenHealthLoss(prev.healthFraction(), budLoad, soil);

        // Lock yield on the very day we enter FRUIT_SET (potentialYieldKg was 0 until now)
        double yieldKg  = (prev.potentialYieldKg() <= 0.0)
                        ? computePotentialYield(budLoad, suitability, health)
                        : prev.potentialYieldKg();

        // Lock YAN similarly
        double yan      = (prev.yanMgL() <= 0.0)
                        ? computeYan(budLoad)
                        : prev.yanMgL();

        PhenoStage next = (gdd >= profile.gddToBerryDev()) ? PhenoStage.BERRY_DEVELOPMENT : PhenoStage.FRUIT_SET;

        return copyWith(prev, next, gdd, health, yieldKg,
                        prev.brix(), prev.taGL(), prev.pH(), yan, prev.tanninRipeness01());
    }

    /**
     * BERRY_DEVELOPMENT: green berries accumulate organic acids and sugars slowly.
     * Mostly GDD accumulation toward véraison; health monitoring continues.
     */
    private VineState tickBerryDev(VineState prev, DailyWeather today,
                                   SiteProfile site, SoilStat soil,
                                   double suitability, int budLoad) {
        double dailyGdd = dailyGdd(today);
        double gdd      = prev.gddAccum() + dailyGdd;
        double health   = applyOverburdenHealthLoss(prev.healthFraction(), budLoad, soil);

        PhenoStage next = (gdd >= profile.gddToVeraison()) ? PhenoStage.VERAISON : PhenoStage.BERRY_DEVELOPMENT;

        return copyWith(prev, next, gdd, health, prev.potentialYieldKg(),
                        prev.brix(), prev.taGL(), prev.pH(), prev.yanMgL(), prev.tanninRipeness01());
    }

    /**
     * VERAISON: colour change day — set the ripening-clock initial values
     * from the variety profile and immediately transition to RIPENING.
     */
    private VineState tickVeraison(VineState prev, DailyWeather today,
                                   SiteProfile site, SoilStat soil,
                                   double suitability, int budLoad) {
        double dailyGdd = dailyGdd(today);
        double gdd      = prev.gddAccum() + dailyGdd;

        double health = applyOverburdenHealthLoss(prev.healthFraction(), budLoad, soil);

        // Initialise ripening clocks at véraison from variety profile
        double brix    = profile.brixAtVeraison();
        double ta      = profile.taAtVeraison();
        double pH      = profile.phAtVeraison();
        double tannin  = 0.0;

        return copyWith(prev, PhenoStage.RIPENING, gdd, health, prev.potentialYieldKg(),
                        brix, ta, pH, prev.yanMgL(), tannin);
    }

    /**
     * RIPENING: the four ripening clocks advance with each day's GDD.
     *
     * <p>All rate constants are read from the variety profile.  For SAPERAVI the
     * profile carries the original hardcoded values, so the output is byte-identical
     * to the pre-refactor implementation.
     *
     * <p>Post-véraison GDD is derived from
     * {@code gddAccum - profile.gddToVeraison()} (gddAccum counts from budbreak;
     * {@code gddToVeraison} is the budbreak→véraison threshold).
     */
    private VineState tickRipening(VineState prev, DailyWeather today,
                                   SiteProfile site, SoilStat soil,
                                   double suitability, int budLoad) {
        double dailyGdd = dailyGdd(today);
        double gdd      = prev.gddAccum() + dailyGdd;

        // Post-véraison GDD: total accumulated GDD minus the GDD required to reach véraison.
        double postVeraisonGdd = Math.max(0.0, gdd - profile.gddToVeraison());

        // Overburden factor in [0,1]: 1.0 at or below balanced load.
        double brixRateFactor = brixRateFactor(budLoad);
        double kEff           = profile.brixK() * brixRateFactor;

        double brixMax = profile.brixMax();
        double brixAtV = profile.brixAtVeraison();

        // Asymptotic Brix: saturates toward brixMax, never exceeds it.
        double brix = Math.min(brixMax,
                               brixMax - (brixMax - brixAtV) * Math.exp(-kEff * postVeraisonGdd));

        // Asymptotic TA: declines toward taFloor; GDD-sensitive so warm/cool years differ.
        double taFloor = profile.taFloor();
        double taAtV   = profile.taAtVeraison();
        double ta      = Math.max(taFloor,
                                  taFloor + (taAtV - taFloor) * Math.exp(-profile.taK() * postVeraisonGdd));

        double pH     = Math.min(4.20, prev.pH() + dailyGdd * profile.phRisePerGdd());
        double tannin = Math.min(1.0,  prev.tanninRipeness01() + dailyGdd * profile.tanninPerGdd());

        double health = applyOverburdenHealthLoss(prev.healthFraction(), budLoad, soil);
        // Raisining penalty applies if brix exceeds threshold; brix is already capped at brixMax.
        health        = applyRaisiningPenalty(health, brix);

        return copyWith(prev, PhenoStage.RIPENING, gdd, health, prev.potentialYieldKg(),
                        brix, ta, pH, prev.yanMgL(), tannin);
    }

    // =======================================================================
    // Helper computations
    // =======================================================================

    /** Daily GDD: max(0, meanTemp − GDD_BASE_C). */
    private static double dailyGdd(DailyWeather w) {
        return Math.max(0.0, w.meanTempC() - GDD_BASE_C);
    }

    /**
     * Computes potential yield (kg) at fruit-set.
     *
     * <pre>
     *   raw     = budLoad × KG_PER_BUD
     *   scaled  = raw × suitability × healthFraction
     *   capped  = min(scaled, MAX_YIELD_KG)
     * </pre>
     *
     * <p>For underloaded vines (budLoad < BUD_LOAD_MIN_FULL) there is a gentle
     * downward scaling to reflect fewer fruitful buds.
     */
    /** A living vine still bears a crop when stressed; yield never scales below this health factor. */
    private static final double YIELD_HEALTH_FLOOR = 0.40;

    private double computePotentialYield(int budLoad, double suitability, double health) {
        double raw   = budLoad * profile.kgPerBud();
        // Accumulated minor health loss must not zero the crop — a living vine
        // still fruits. Quality is penalised separately (fruitHealth01 in the
        // resolver), so this floor does not inflate wine quality.
        double yieldHealth = Math.max(health, YIELD_HEALTH_FLOOR);
        double yield = raw * suitability * yieldHealth;
        return Math.min(MAX_YIELD_KG, Math.max(0.0, yield));
    }

    /**
     * Computes YAN at fruit-set.  Declines with overburden (more clusters = less
     * nitrogen per berry).
     */
    private static double computeYan(int budLoad) {
        int excessBuds = Math.max(0, budLoad - BUD_LOAD_OVERBURDEN_THRESHOLD);
        double penalty  = excessBuds * YAN_OVERBURDEN_PENALTY_PER_BUD;
        return Math.max(80.0, YAN_BASELINE_MGL * (1.0 - penalty));
    }

    /**
     * Frost damage to healthFraction: applies when tMin falls below 0°C during
     * sensitive stages (BUD_SWELL, BUDBREAK, SHOOT_GROWTH, FLOWERING).
     *
     * <p>Formula:
     * <pre>
     *   frostExposure = max(0, -tMinC) × (site.frostRisk + soil.frostBias01) / 2
     *   healthLoss    = frostExposure × stageSensitivity
     * </pre>
     *
     * Stage sensitivity:
     * <ul>
     *   <li>BUD_SWELL      0.01 / °C of exposure</li>
     *   <li>BUDBREAK        0.04 / °C (most vulnerable)</li>
     *   <li>SHOOT_GROWTH   0.03 / °C</li>
     *   <li>FLOWERING       0.02 / °C</li>
     * </ul>
     */
    private static double applyFrostDamage(double health, DailyWeather w,
                                           SiteProfile site, SoilStat soil,
                                           PhenoStage stage) {
        if (w.tMinC() >= 0.0) return health; // no frost

        double frostDegrees  = -w.tMinC();
        double combinedRisk  = (site.frostRisk() + soil.frostBias01()) / 2.0;
        double exposure      = frostDegrees * combinedRisk;

        double sensitivity = switch (stage) {
            case BUD_SWELL      -> 0.01;
            case BUDBREAK       -> 0.04;
            case SHOOT_GROWTH   -> 0.03;
            case FLOWERING      -> 0.02;
            default             -> 0.0;
        };

        double loss = exposure * sensitivity;
        return clamp01(health - loss);
    }

    /**
     * Overburden health loss per day: applies when budLoad exceeds
     * {@value #BUD_LOAD_OVERBURDEN_THRESHOLD}.
     *
     * <p>High soil vigor amplifies the effect (excess vigour = harder to manage).
     */
    private static double applyOverburdenHealthLoss(double health, int budLoad, SoilStat soil) {
        int excessBuds = Math.max(0, budLoad - BUD_LOAD_OVERBURDEN_THRESHOLD);
        if (excessBuds == 0) return health;

        double amplifier = (soil.vigor01() > VIGOR_OVERBURDEN_AMPLIFIER_THRESHOLD)
                         ? 1.5 : 1.0;
        double dailyLoss  = excessBuds * OVERBURDEN_HEALTH_LOSS_PER_BUD_PER_DAY * amplifier;
        return clamp01(health - dailyLoss);
    }

    /**
     * Raisining penalty: applied each ripening day while brix exceeds
     * {@value #RAISINING_BRIX_THRESHOLD}.
     */
    private static double applyRaisiningPenalty(double health, double brix) {
        if (brix <= RAISINING_BRIX_THRESHOLD) return health;
        return clamp01(health - RAISINING_HEALTH_LOSS_PER_DAY);
    }

    /**
     * Brix rate factor in [0,1]: 1.0 at or below balanced load; decreases
     * linearly as excess buds accumulate beyond the overburden threshold.
     *
     * <pre>
     *   excess      = max(0, budLoad - BUD_LOAD_OVERBURDEN_THRESHOLD)
     *   rateFactor  = max(0.20, 1.0 - excess × OVERBURDEN_BRIX_PENALTY_PER_BUD)
     * </pre>
     *
     * This factor is applied as a multiplier on BRIX_K in the asymptotic Brix
     * formula, slowing the exponential approach to the ceiling for overburdened
     * vines.  Floor of 0.20 ensures even extreme bud loads retain some ripening.
     */
    private static double brixRateFactor(int budLoad) {
        int excess = Math.max(0, budLoad - BUD_LOAD_OVERBURDEN_THRESHOLD);
        if (excess == 0) return 1.0;
        return Math.max(0.20, 1.0 - excess * OVERBURDEN_BRIX_PENALTY_PER_BUD);
    }

    // =======================================================================
    // Record construction helper
    // =======================================================================

    /**
     * Construct a new {@link VineState} from the given components.
     * Central place so field order changes in the record don't scatter through tick code.
     */
    private static VineState copyWith(VineState prev, PhenoStage stage, double gddAccum,
                                      double health, double yieldKg,
                                      double brix, double ta, double pH,
                                      double yan, double tannin) {
        return new VineState(
            stage,
            gddAccum,
            clamp01(health),
            Math.max(0.0, yieldKg),
            Math.max(0.0, brix),
            Math.max(0.0, ta),
            clamp(pH, 2.5, 4.5),
            Math.max(0.0, yan),
            clamp01(tannin)
        );
    }

    // --- Math utilities -----------------------------------------------------

    private static double clamp01(double v) { return Math.max(0.0, Math.min(1.0, v)); }
    private static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }
}
