package com.game.core.weather;

import com.game.core.data.DailyWeather;
import com.game.core.data.Region;
import com.game.core.data.RegionClimate;
import com.game.core.data.RegionClimates;
import com.game.core.data.Vintage;
import com.game.core.data.WinklerClass;
import com.game.core.time.RngStreams;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * Deterministic synthetic weather generator, parameterised by region via
 * {@link RegionClimate}.
 *
 * <h2>Temperature model</h2>
 * Mean daily temperature follows a cosine seasonal curve:
 * <pre>
 *   meanBase = (MEAN_ANNUAL + climate.meanAnnualOffset())
 *            + (MEAN_AMP + climate.meanAmpOffset())
 *            * cos(2π (doy - PEAK_DOY) / 365)
 * </pre>
 * For KAKHETI the offsets are both 0.0, so the formula reduces to the
 * original hardcoded expression — byte-identical output guaranteed.
 *
 * <h2>Precipitation model</h2>
 * Per-day Bernoulli rain probability and exponential event mean are scaled
 * by {@code climate.rainProbMultiplier()} and {@code climate.rainAmountMultiplier()}.
 * For KAKHETI both multipliers are 1.0 — no change to existing output.
 *
 * <h2>Determinism</h2>
 * All randomness is drawn from {@link RngStreams} named sub-streams whose names
 * incorporate the {@code region} and {@code year}, keeping different (region, year)
 * pairs fully isolated. No wall-clock time, no Math.random, no HashMap iteration.
 *
 * <p>Implements {@link WeatherModel} per SIM-SPEC §3.2 and REGIONS-SPEC §3.
 */
public final class KakhetiWeatherModel implements WeatherModel {

    // ── Kakheti baseline constants (unchanged from original) ─────────────────
    /**
     * Annual mean temperature (°C) at ~400 m altitude (Telavi / Alazani Valley area).
     * Calibrated so that noiseless-baseline GDD Apr1–Oct31 ≈ 1873 °C·d → Winkler III.
     */
    private static final double MEAN_ANNUAL  = 13.0;
    /**
     * Half-amplitude of the seasonal cosine (°C).
     * summer peak = MEAN_ANNUAL + MEAN_AMP = 24°C; winter trough = 2°C.
     */
    private static final double MEAN_AMP     = 11.0;
    /** Day-of-year where temperature peaks (mid-July = day 196, 0-based). */
    private static final double PEAK_DOY     = 196.0;
    /**
     * Base diurnal range (tMax − tMin, °C).
     */
    private static final double DIURNAL_BASE = 15.0;

    // ── Monthly precipitation parameters (0=Jan..11=Dec) ─────────────────────
    /**
     * Kakheti-baseline probability that any given day has measurable rainfall.
     * Regional multipliers from {@link RegionClimate#rainProbMultiplier()} scale these.
     */
    private static final double[] MONTHLY_RAIN_PROB = {
            0.22, // Jan
            0.22, // Feb
            0.28, // Mar
            0.30, // Apr
            0.28, // May
            0.20, // Jun
            0.10, // Jul  very dry
            0.08, // Aug  very dry
            0.15, // Sep
            0.25, // Oct
            0.26, // Nov
            0.22  // Dec
    };

    /**
     * Kakheti-baseline mean rain per event (mm), used as the exponential-distribution mean.
     * Regional multipliers from {@link RegionClimate#rainAmountMultiplier()} scale these.
     */
    private static final double[] MONTHLY_RAIN_MEAN_MM = {
             8.0, // Jan
             8.0, // Feb
            10.0, // Mar
            14.0, // Apr
            14.0, // May
            10.0, // Jun
             6.0, // Jul
             5.0, // Aug
             9.0, // Sep
            12.0, // Oct
            10.0, // Nov
             8.0  // Dec
    };

    // ── Vintage warmth offset parameters ─────────────────────────────────────
    /**
     * Kakheti-baseline standard deviation (°C) of the per-vintage warmth offset.
     * {@link RegionClimate#vintageWarmthStddevOffset()} is added to this before sampling.
     */
    private static final double VINTAGE_WARMTH_STDDEV_C = 1.5;

    // ── Humidity parameters ───────────────────────────────────────────────────
    /** Kakheti summer baseline relative humidity. Regional offsets are added. */
    private static final double HUMIDITY_SUMMER = 0.55;
    /** Kakheti winter baseline relative humidity. Regional offsets are added. */
    private static final double HUMIDITY_WINTER = 0.75;

    // ── Winkler window (SIM-SPEC §3.2) ───────────────────────────────────────
    /** Apr 1 as 0-based day-of-year (31+28+31 = 90). */
    static final int WINKLER_START_DAY = 90;
    /**
     * Oct 31 as 0-based day-of-year (inclusive).
     */
    static final int WINKLER_END_DAY = 303;

    // ── Typical Kakheti growing-season rain threshold ─────────────────────────
    /** Growing-season rain below this (mm) is labelled "dry". */
    private static final double DRY_SEASON_RAIN_MM = 200.0;

    /** Midpoint of Winkler class III (1670–1940) used to classify "warm" vs "cool". */
    private static final double CLASS_III_MID = 1805.0;

    // ── Stateless singleton ───────────────────────────────────────────────────
    /** Shared stateless instance (no mutable state). */
    public static final KakhetiWeatherModel INSTANCE = new KakhetiWeatherModel();

    public KakhetiWeatherModel() {}

    // ── WeatherModel ─────────────────────────────────────────────────────────

    /**
     * {@inheritDoc}
     *
     * <p>Climate knobs are read from {@link RegionClimates#of(Region)}.
     * For {@link Region#KAKHETI} all offsets are 0 and multipliers are 1,
     * producing byte-identical output to the original hardcoded form.
     *
     * <p>Stream names embed {@code region.name()} and {@code year} so each
     * (seed, region, year) triple has its own independent random walks.
     */
    @Override
    public List<DailyWeather> generateYear(RngStreams rng, Region region, int year) {
        RegionClimate climate = RegionClimates.of(region);

        // Effective seasonal parameters (KAKHETI: offsets = 0 → identical to original)
        double effectiveMeanAnnual  = MEAN_ANNUAL + climate.meanAnnualOffset();
        double effectiveMeanAmp     = MEAN_AMP    + climate.meanAmpOffset();
        double effectiveVintageStddev = VINTAGE_WARMTH_STDDEV_C
                                       + climate.vintageWarmthStddevOffset();
        double effectiveHumiditySummer = HUMIDITY_SUMMER + climate.humiditySummerOffset();
        double effectiveHumidityWinter = HUMIDITY_WINTER + climate.humidityWinterOffset();
        double rainProbMult   = climate.rainProbMultiplier();
        double rainAmountMult = climate.rainAmountMultiplier();

        String prefix   = "weather." + region.name() + "." + year + ".";
        RandomGenerator tempRng    = rng.stream(prefix + "temp");
        RandomGenerator rainRng    = rng.stream(prefix + "rain");
        RandomGenerator humRng     = rng.stream(prefix + "hum");
        RandomGenerator vintageRng = rng.stream(prefix + "vintage");

        // ── Per-(masterSeed, region, year) vintage warmth offset ──────────────
        final double vintageWarmthOffsetC =
                sampleGaussian(vintageRng, 0.0, effectiveVintageStddev);

        List<DailyWeather> days = new ArrayList<>(365);

        for (int doy = 0; doy < 365; doy++) {
            // ── Temperature ───────────────────────────────────────────────
            double meanBase  = meanBaseTemp(doy, effectiveMeanAnnual, effectiveMeanAmp);
            double meanNoise = sampleGaussian(tempRng, 0.0, 2.0);
            double meanTemp  = meanBase + meanNoise + vintageWarmthOffsetC;

            double diurnal = Math.max(4.0, DIURNAL_BASE + sampleGaussian(tempRng, 0.0, 0.8));
            double tMinC   = meanTemp - diurnal / 2.0;
            double tMaxC   = meanTemp + diurnal / 2.0;

            // ── Precipitation ─────────────────────────────────────────────
            int    month       = dayOfYearToMonth(doy);
            double rainProb    = MONTHLY_RAIN_PROB[month]    * rainProbMult;
            double rainMeanMm  = MONTHLY_RAIN_MEAN_MM[month] * rainAmountMult;
            double rainMm;
            if (rainRng.nextDouble() < rainProb) {
                double u = rainRng.nextDouble();
                if (u <= 0.0) u = 1e-9;
                rainMm = Math.min(-rainMeanMm * Math.log(u), 60.0);
            } else {
                rainMm = 0.0;
                // Consume one double to keep stream position the same regardless of branch —
                // determinism requires identical consumption when rain event is skipped too.
                rainRng.nextDouble();
            }

            // ── Humidity ──────────────────────────────────────────────────
            // Cosine: +1 in summer → low humidity (effectiveHumiditySummer);
            //         -1 in winter → high humidity (effectiveHumidityWinter)
            double cosVal    = Math.cos(2.0 * Math.PI * (doy - PEAK_DOY) / 365.0);
            double humBase   = effectiveHumiditySummer
                             + (effectiveHumidityWinter - effectiveHumiditySummer)
                             * (0.5 - 0.5 * cosVal);
            double humNoise  = sampleGaussian(humRng, 0.0, 0.04);
            double rainBoost = (rainMm > 0.0) ? 0.05 : 0.0;
            double humidity01 = clamp01(humBase + humNoise + rainBoost);

            days.add(new DailyWeather(doy, tMinC, tMaxC, rainMm, humidity01));
        }

        return Collections.unmodifiableList(days);
    }

    /**
     * {@inheritDoc}
     *
     * <p>GDD is summed over days {@value #WINKLER_START_DAY} to {@value #WINKLER_END_DAY}
     * (Apr 1 to Oct 31, inclusive), base 10°C.
     */
    @Override
    public Vintage rollVintage(RngStreams rng, Region region, int year, List<DailyWeather> days) {
        double gddSeason  = 0.0;
        double seasonRain = 0.0;

        for (DailyWeather day : days) {
            int doy = day.dayOfYear();
            if (doy >= WINKLER_START_DAY && doy <= WINKLER_END_DAY) {
                gddSeason  += Gdd.daily(day, Gdd.GDD_BASE_C);
                seasonRain += day.rainMm();
            }
        }

        WinklerClass winkler = classifyWinkler(gddSeason);

        // "warm" if above class-III midpoint (~1805 °C·d); "cool" otherwise.
        // "dry"  if growing-season total rain < 200 mm; "wet" otherwise.
        String tempLabel = (gddSeason >= CLASS_III_MID) ? "warm" : "cool";
        String rainLabel = (seasonRain < DRY_SEASON_RAIN_MM) ? "dry" : "wet";
        String pattern   = tempLabel + "-" + rainLabel;

        return new Vintage(year, region, gddSeason, winkler, pattern);
    }

    // ── Package-visible helpers (accessible to tests) ─────────────────────────

    /**
     * Cosine seasonal mean baseline temperature (no noise) using Kakheti defaults.
     * Used by tests that invoke the single-argument variant.
     *
     * @param doy 0-based day of year (0..364)
     * @return baseline mean temperature in °C (Kakheti parameters)
     */
    static double meanBaseTemp(int doy) {
        return meanBaseTemp(doy, MEAN_ANNUAL, MEAN_AMP);
    }

    /**
     * Cosine seasonal mean baseline temperature (no noise), parameterised.
     *
     * @param doy          0-based day of year (0..364)
     * @param meanAnnual   effective annual mean (°C)
     * @param meanAmp      effective seasonal half-amplitude (°C)
     * @return baseline mean temperature in °C
     */
    static double meanBaseTemp(int doy, double meanAnnual, double meanAmp) {
        return meanAnnual + meanAmp * Math.cos(2.0 * Math.PI * (doy - PEAK_DOY) / 365.0);
    }

    /**
     * Classify a season GDD into a {@link WinklerClass}.
     * Thresholds per SIM-SPEC §3.2:
     * I &lt;1390, II 1390–1670, III 1670–1940, IV 1940–2220, V &gt;2220.
     */
    static WinklerClass classifyWinkler(double gdd) {
        if (gdd < 1390.0) return WinklerClass.I;
        if (gdd < 1670.0) return WinklerClass.II;
        if (gdd < 1940.0) return WinklerClass.III;
        if (gdd < 2220.0) return WinklerClass.IV;
        return WinklerClass.V;
    }

    // ── Private utilities ─────────────────────────────────────────────────────

    /**
     * Box-Muller Gaussian sample from a uniform {@link RandomGenerator}.
     * Consumes exactly 2 doubles per call — stream position is predictable.
     */
    private static double sampleGaussian(RandomGenerator rng, double mean, double stdDev) {
        double u1 = rng.nextDouble();
        double u2 = rng.nextDouble();
        if (u1 <= 0.0) u1 = 1e-15;
        double z = Math.sqrt(-2.0 * Math.log(u1)) * Math.cos(2.0 * Math.PI * u2);
        return mean + stdDev * z;
    }

    /**
     * Convert a 0-based day-of-year to a 0-based calendar month (0=Jan..11=Dec)
     * for a non-leap 365-day year.
     */
    private static int dayOfYearToMonth(int doy) {
        // Month start days (0-based): Jan=0, Feb=31, Mar=59, Apr=90, ...
        int[] monthStart = {0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334};
        for (int m = 0; m < 11; m++) {
            if (doy < monthStart[m + 1]) {
                return m;
            }
        }
        return 11; // December
    }

    /** Clamp {@code v} to [0.0, 1.0]. */
    private static double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }
}
