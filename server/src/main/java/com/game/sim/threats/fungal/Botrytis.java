package com.game.sim.threats.fungal;

import com.game.core.data.Fault;
import com.game.core.data.PhenoStage;
import com.game.sim.threats.api.ThreatCategory;
import com.game.sim.threats.api.ThreatContext;
import com.game.sim.threats.api.ThreatEffect;
import com.game.sim.threats.api.ThreatMemory;
import com.game.sim.threats.api.ThreatSource;

/**
 * Botrytis cinerea — grey rot vs noble rot (Botrytis cinerea on ripe Saperavi).
 *
 * <h3>Two mutually-exclusive outcomes decided each active day:</h3>
 * <ol>
 *   <li><b>Grey rot (pouriture grise):</b> damp, continuously wet conditions on
 *       ripe or over-ripe fruit → berry skin rupture → acetic acid → structural
 *       damage, yield loss, VOLATILE_ACIDITY fault.
 *   <li><b>Noble rot (pouriture noble / Botrytis concentrante):</b> requires the
 *       "humid-morning / sunny-dry-afternoon" pattern that desiccates healthy
 *       intact berries without rupturing them. Concentrates sugars, glycerol, and
 *       flavour complexity. Produces a <em>negative</em> qualityPenalty (bonus)
 *       and a small Brix-proxy bonus via reduced yield volume (concentration).
 *       Requires healthy fruit (vine.healthFraction > NOBLE_MIN_HEALTH) and
 *       intact skins (no prior BlackRot / heavy grey rot — proxied by
 *       mem.aux > GREY_ROT_AUX_THRESHOLD).
 * </ol>
 *
 * <h3>Branching decision (deterministic, driven by ctx.rng):</h3>
 * <pre>
 *   IF stage ≥ VERAISON
 *     AND rain in last N days is HIGH (proxy: today.humidity01 > 0.90 AND rain > 5 mm)
 *     AND skin damage is low (mem.aux < GREY_ROT_AUX_THRESHOLD)
 *     AND vine.healthFraction > NOBLE_MIN_HEALTH
 *     AND noble pattern detected (humidity01 > 0.85 in morning proxy,
 *         AND meanTemp > 18 for drying afternoon)
 *   → NOBLE ROT branch (quality bonus)
 *   ELSE
 *   IF stage ≥ VERAISON AND humidity > 0.80 AND rain > 3 mm
 *   → GREY ROT branch (damage + VOLATILE_ACIDITY)
 * </pre>
 *
 * <h3>Memory mapping:</h3>
 * <ul>
 *   <li>{@code level}      — current infection severity 0..1
 *   <li>{@code aux}        — accumulated grey-rot skin damage (0 = clean; >0.3 = severe)
 *   <li>{@code ticksActive}— consecutive active days
 *   <li>{@code established}— latched true once any Botrytis infection established
 * </ul>
 *
 * <h3>Tell strings:</h3>
 * <ul>
 *   <li>Grey rot: "Botrytis grey rot: brown shrivelled berries, musty smell."
 *   <li>Noble rot: "Noble rot: golden-grey bloom on ripe berries — sugar concentration."
 * </ul>
 */
public final class Botrytis implements ThreatSource {

    // ── Stage gate ─────────────────────────────────────────────────────────────
    // Botrytis on fruit is only meaningful from veraison onward
    // (leaf infections exist earlier but are minor; we model fruit Botrytis)

    // ── Grey rot pressure thresholds ──────────────────────────────────────────
    private static final double GREY_ROT_HUMIDITY_MIN   = 0.80;
    private static final double GREY_ROT_RAIN_MM        = 3.0;
    private static final double GREY_ROT_TEMP_MIN_C     = 15.0;
    private static final double GREY_ROT_TEMP_MAX_C     = 25.0;

    // ── Noble rot preconditions ───────────────────────────────────────────────
    /** High morning humidity: damp mist to start the day's infection cycle. */
    private static final double NOBLE_HUMIDITY_MORNING  = 0.85;
    /** Rain must be present (mist/dew enough but not soaking). */
    private static final double NOBLE_RAIN_MAX_MM       = 12.0; // not too soaking
    private static final double NOBLE_RAIN_MIN_MM       = 0.5;  // some moisture needed
    /** Afternoon must be warm and drying — mean temp proxy. */
    private static final double NOBLE_DRY_TEMP_C        = 18.0;
    /** Vine must be healthy enough for intact skins. */
    private static final double NOBLE_MIN_HEALTH        = 0.55;
    /** If grey rot skin damage (aux) is too high, noble rot cannot concentrate well. */
    private static final double GREY_ROT_AUX_THRESHOLD  = 0.35;
    /** Noble rot probability on a qualifying day (ctx.rng supplies randomness). */
    private static final double NOBLE_ROT_BASE_PROB     = 0.20;

    // ── Spore / level dynamics ────────────────────────────────────────────────
    private static final double SPORE_GAIN_PER_DAY      = 0.06;
    private static final double SPORE_DECAY_RATE        = 0.025;
    private static final double ESTABLISH_THRESHOLD     = 0.15;
    private static final double LEVEL_GAIN_RATE         = 0.045;
    private static final double LEVEL_DECAY_RATE        = 0.025;

    // ── Grey rot damage ───────────────────────────────────────────────────────
    private static final double GREY_MAX_HEALTH_DELTA   = -0.020;
    private static final double GREY_MAX_YIELD_LOSS     = 0.025;
    private static final double GREY_MAX_QUALITY_PENALTY = 0.015;

    // ── Noble rot bonus (negative penalty = quality improvement) ─────────────
    /** Quality bonus at peak noble rot: negative qualityPenalty. */
    private static final double NOBLE_QUALITY_BONUS     = -0.008; // negative = bonus
    /** Volume concentration: slight yield reduction (desiccation). */
    private static final double NOBLE_YIELD_CONCENTRATION = 0.97; // 3% per day

    // ── Counters ──────────────────────────────────────────────────────────────
    private static final double CANOPY_OPEN_EFFICACY    = 0.30;
    private static final double LEAF_PULL_BONUS         = 0.25;
    private static final double COPPER_PARTIAL_EFFICACY = 0.20; // copper helps some

    // ── Tells ─────────────────────────────────────────────────────────────────
    private static final String TELL_GREY_LIGHT   = "Botrytis grey rot beginning: soft brown spots on berry clusters.";
    private static final String TELL_GREY_SEVERE  = "Severe Botrytis grey rot: shrivelled brown berries, musty vinegar smell.";
    private static final String TELL_NOBLE        = "Noble rot (Botrytis concentrante): golden-grey bloom on ripe berries — natural sugar concentration.";

    @Override
    public String id() {
        return "fungal.botrytis";
    }

    @Override
    public ThreatCategory category() {
        return ThreatCategory.FUNGAL;
    }

    @Override
    public ThreatEffect evaluate(ThreatContext ctx) {
        ThreatMemory mem  = ctx.memory();
        PhenoStage stage  = ctx.vine().stage();

        // Outside the fruit-risk window: passive decay only
        if (!isFruitWindow(stage)) {
            double decayedAux   = Math.max(0.0, mem.aux()   - SPORE_DECAY_RATE);
            double decayedLevel = Math.max(0.0, mem.level() - LEVEL_DECAY_RATE);
            ThreatMemory next = new ThreatMemory(
                    decayedLevel, decayedAux, 0, mem.yearsActive(), mem.established());
            return ThreatEffect.none(next);
        }

        double rain     = ctx.today().rainMm();
        double humidity = ctx.today().humidity01();
        double meanTemp = ctx.today().meanTempC();

        // ── Counter efficacy ──────────────────────────────────────────────────
        double counter = counterEfficacy(ctx);

        // ── Check if noble rot conditions are met BEFORE checking grey rot ───
        boolean nobleConditions = checkNobleConditions(
                rain, humidity, meanTemp, mem, ctx.vine().healthFraction());

        if (nobleConditions) {
            // Noble rot: use rng to decide if it triggers this day
            boolean nobleTriggered = ctx.rng().nextDouble() < NOBLE_ROT_BASE_PROB;
            if (nobleTriggered) {
                return buildNobleRotEffect(mem);
            }
        }

        // ── Grey rot pressure ─────────────────────────────────────────────────
        boolean greyConditions = checkGreyConditions(rain, humidity, meanTemp);
        double rawPressure     = greyConditions ? computeGreyPressure(meanTemp) : 0.0;
        double netPressure     = rawPressure * (1.0 - counter);

        // ── Spore load / level ────────────────────────────────────────────────
        double newAux = Math.max(0.0,
                mem.aux() + netPressure * SPORE_GAIN_PER_DAY - SPORE_DECAY_RATE);
        boolean nowEstablished = mem.established() || newAux >= ESTABLISH_THRESHOLD;

        double newLevel;
        if (nowEstablished) {
            newLevel = mem.level() + netPressure * LEVEL_GAIN_RATE
                    - (netPressure < 0.01 ? LEVEL_DECAY_RATE : 0.0);
            newLevel = Math.min(1.0, Math.max(0.0, newLevel));
        } else {
            newLevel = Math.max(0.0, mem.level() - LEVEL_DECAY_RATE);
        }

        int newTicks = newLevel > 0.01 ? mem.ticksActive() + 1 : 0;
        ThreatMemory nextMem = new ThreatMemory(
                newLevel, newAux, newTicks, mem.yearsActive(), nowEstablished);

        if (!nowEstablished || newLevel < 0.01) {
            return ThreatEffect.none(nextMem);
        }

        // ── Grey rot damage ───────────────────────────────────────────────────
        double healthDelta    = newLevel * GREY_MAX_HEALTH_DELTA;
        double yieldMult      = 1.0 - newLevel * GREY_MAX_YIELD_LOSS;
        double qualityPenalty = newLevel * GREY_MAX_QUALITY_PENALTY;
        String tell           = newLevel > 0.45 ? TELL_GREY_SEVERE : TELL_GREY_LIGHT;

        return new ThreatEffect(
                healthDelta, yieldMult, qualityPenalty,
                Fault.VOLATILE_ACIDITY, false, tell, nextMem);
    }

    // ── Noble rot branch ──────────────────────────────────────────────────────

    /**
     * Returns a ThreatEffect with a QUALITY BONUS (negative qualityPenalty)
     * and mild yield concentration. No health damage; no fault.
     */
    private static ThreatEffect buildNobleRotEffect(ThreatMemory mem) {
        // Noble rot does not increase the grey-rot skin-damage counter (aux)
        // It does register as "established" for record-keeping
        ThreatMemory nextMem = new ThreatMemory(
                mem.level(),             // level unchanged (no disease progression)
                mem.aux(),               // aux unchanged (clean skins)
                mem.ticksActive() + 1,
                mem.yearsActive(),
                true);

        return new ThreatEffect(
                0.0,                          // no health damage
                NOBLE_YIELD_CONCENTRATION,    // small volume reduction (concentration)
                NOBLE_QUALITY_BONUS,          // NEGATIVE = quality bonus
                Fault.NONE,
                false,
                TELL_NOBLE,
                nextMem);
    }

    // ── Condition checks ──────────────────────────────────────────────────────

    /**
     * Noble rot requires the classical humid-morning / dry-afternoon diurnal pattern:
     * high humidity (morning dew/mist) + moderate rain (not soaking) + warm drying temps
     * + healthy vine + clean skins.
     */
    private static boolean checkNobleConditions(
            double rain, double humidity, double meanTemp,
            ThreatMemory mem, double healthFraction) {
        return humidity   >= NOBLE_HUMIDITY_MORNING
                && rain   >= NOBLE_RAIN_MIN_MM
                && rain   <= NOBLE_RAIN_MAX_MM
                && meanTemp >= NOBLE_DRY_TEMP_C
                && healthFraction >= NOBLE_MIN_HEALTH
                && mem.aux() < GREY_ROT_AUX_THRESHOLD; // skins still intact
    }

    private static boolean checkGreyConditions(double rain, double humidity, double meanTemp) {
        return humidity  >= GREY_ROT_HUMIDITY_MIN
                && rain  >= GREY_ROT_RAIN_MM
                && meanTemp >= GREY_ROT_TEMP_MIN_C
                && meanTemp <= GREY_ROT_TEMP_MAX_C;
    }

    /**
     * Bell-curve temperature response for grey rot pressure (peak ~20 °C).
     */
    private static double computeGreyPressure(double meanTemp) {
        double opt = (GREY_ROT_TEMP_MIN_C + GREY_ROT_TEMP_MAX_C) / 2.0;
        double half = (GREY_ROT_TEMP_MAX_C - GREY_ROT_TEMP_MIN_C) / 2.0;
        double x    = (meanTemp - opt) / half;
        return Math.max(0.0, 1.0 - x * x);
    }

    private static double counterEfficacy(ThreatContext ctx) {
        double canopy   = ctx.canopyOpenness01() * CANOPY_OPEN_EFFICACY;
        double leafPull = ctx.leafPulled()        ? LEAF_PULL_BONUS : 0.0;
        double copper   = ctx.copperSpray01()    * COPPER_PARTIAL_EFFICACY;
        return Math.min(1.0, canopy + leafPull + copper);
    }

    /** Botrytis on fruit matters from véraison through ripening. */
    private static boolean isFruitWindow(PhenoStage stage) {
        return stage == PhenoStage.VERAISON || stage == PhenoStage.RIPENING;
    }
}
