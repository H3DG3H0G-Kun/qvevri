package com.game.sim.threats.virus;

import com.game.core.data.Fault;
import com.game.core.data.PhenoStage;
import com.game.sim.threats.api.ThreatCategory;
import com.game.sim.threats.api.ThreatContext;
import com.game.sim.threats.api.ThreatEffect;
import com.game.sim.threats.api.ThreatMemory;
import com.game.sim.threats.api.ThreatSource;

/**
 * Grapevine Leafroll-Associated Viruses (GLRaV — primarily GLRaV-1, -3) —
 * the most economically damaging group of grapevine viruses worldwide.
 * Spread by mealybugs and soft scale insects in the field.
 *
 * <h3>Chronic, no-cure model:</h3>
 * <ul>
 *   <li>Once established ({@code memory.established = true}), the virus cannot
 *       be eliminated from the vine. Replanting with certified, virus-free
 *       material is the only solution. Modelled as a permanent latch.
 *   <li>Per-season establishment probability is low ({@code ANNUAL_ESTABLISH_PROB})
 *       and is higher for own-root vines (mealybugs more mobile without rootstock
 *       barrier). Using certified planting stock ({@code ownRoots = false} proxy)
 *       dramatically reduces the chance.
 *   <li>Once established, the virus slowly saps yield and, critically, delays
 *       ripening — Brix accumulation is reduced and tannin ripeness lags
 *       (modelled as a persistent quality penalty and yieldMultiplier < 1).
 *   <li>Damage accumulates with {@code memory.yearsActive}.
 * </ul>
 *
 * <h3>Symptoms (distinctive for Saperavi / red varieties):</h3>
 * <ul>
 *   <li>Leaf margins roll downward ("leafroll").
 *   <li>Leaves turn red/purple prematurely in autumn while veins stay green.
 *   <li>Delayed, uneven ripening — berries remain high-acid, low-sugar.
 *   <li>Yield 20–40% below healthy vines over time.
 * </ul>
 *
 * <h3>Memory mapping:</h3>
 * <ul>
 *   <li>{@code level}      — chronic severity 0..1 (rises with yearsActive)
 *   <li>{@code aux}        — within-season establishment sentinel (0 or 1)
 *   <li>{@code ticksActive}— days with active symptoms this season
 *   <li>{@code yearsActive}— seasons since establishment (key damage driver)
 *   <li>{@code established}— permanent latch
 * </ul>
 *
 * <h3>Tell:</h3>
 * "Leaves rolling downward, red between green veins — Grapevine Leafroll Virus."
 */
public final class LeafrollVirus implements ThreatSource {

    // ── Establishment ─────────────────────────────────────────────────────────
    /**
     * Seasonal establishment check occurs early-mid season when mealybugs
     * are mobile (warm weather, late spring).
     */
    private static final int    SEASON_CHECK_DOY         = 120;
    /**
     * Per-season probability of virus transmission from a nearby infected source
     * via mealybug vector. Low but non-trivial — real vineyards see ~5–15% per year
     * in infected regions. We use a conservative 6%.
     */
    private static final double ANNUAL_ESTABLISH_PROB     = 0.06;
    /**
     * Grafted / certified stock reduces establishment risk. Certified nurseries
     * test for GLRaV; grafted vines also benefit from delayed mealybug colonisation.
     */
    private static final double CERTIFIED_STOCK_REDUCTION = 0.70;

    // ── Damage ramp (per year of infection) ──────────────────────────────────
    /** Yield loss per year (cumulative, slow). */
    private static final double YIELD_LOSS_PER_YEAR       = 0.035;
    /**
     * Quality penalty per year — leafroll notably delays ripeness so quality
     * suffers even when yield is adequate.
     */
    private static final double QUALITY_PENALTY_PER_YEAR  = 0.010;
    /** Daily health drain from systemic virus load. */
    private static final double HEALTH_DELTA_PER_YEAR     = -0.0004;
    /** Caps. */
    private static final double MAX_YIELD_LOSS            = 0.60;
    private static final double MAX_QUALITY_PENALTY       = 0.35;

    // ── Severity ramp ─────────────────────────────────────────────────────────
    private static final double LEVEL_PER_YEAR            = 0.09;

    // ── Within-season sentinel stored in aux ─────────────────────────────────
    private static final double AUX_CHECKED               = 1.0;

    // ── Tells ─────────────────────────────────────────────────────────────────
    private static final String TELL_EARLY  =
            "Leaf margins rolling downward, reddish discolouration — early Leafroll Virus symptoms.";
    private static final String TELL_SEVERE =
            "Severe Leafroll Virus: leaves cupped red/purple with green veins, delayed uneven ripening.";

    @Override
    public String id() {
        return "virus.leafroll";
    }

    @Override
    public ThreatCategory category() {
        return ThreatCategory.VIRUS;
    }

    @Override
    public ThreatEffect evaluate(ThreatContext ctx) {
        ThreatMemory mem = ctx.memory();

        // ── Attempt establishment once per season ─────────────────────────────
        if (!mem.established()) {
            return attemptEstablishment(ctx, mem);
        }

        // ── Established: chronic damage ───────────────────────────────────────
        return applyChronicDamage(ctx, mem);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ThreatEffect attemptEstablishment(ThreatContext ctx, ThreatMemory mem) {
        int doy = ctx.dayOfYear();

        // Reset sentinel at new year
        if (doy == 0) {
            return ThreatEffect.none(new ThreatMemory(0, 0, 0, mem.yearsActive(), false));
        }

        // Window: not too early (mealybugs need warmth) and not past the check point
        if (doy > SEASON_CHECK_DOY) {
            return ThreatEffect.none(mem);
        }
        if (mem.aux() >= AUX_CHECKED) {
            return ThreatEffect.none(mem); // already checked this season
        }

        // Mealybug activity scales with warmth (proxy: mean temp ≥ 18°C in mid-spring)
        double meanTemp = ctx.today().meanTempC();
        if (meanTemp < 18.0) {
            // Not warm enough yet; don't mark as checked so we try again when warm
            return ThreatEffect.none(mem);
        }

        double prob = ANNUAL_ESTABLISH_PROB;
        if (!ctx.ownRoots()) {
            prob *= (1.0 - CERTIFIED_STOCK_REDUCTION);
        }

        boolean established = ctx.rng().nextDouble() < prob;

        if (!established) {
            ThreatMemory next = new ThreatMemory(0, AUX_CHECKED, 0, mem.yearsActive(), false);
            return ThreatEffect.none(next);
        }

        ThreatMemory next = new ThreatMemory(
                LEVEL_PER_YEAR,
                AUX_CHECKED,
                1,
                0,      // yearsActive starts at 0; engine increments at season end
                true);
        return new ThreatEffect(
                -0.004, 1.0, 0.002,
                Fault.NONE, false,
                "Mealybug-transmitted Leafroll Virus newly established — watch for delayed ripening.",
                next);
    }

    private ThreatEffect applyChronicDamage(ThreatContext ctx, ThreatMemory mem) {
        PhenoStage stage = ctx.vine().stage();

        double aux = mem.aux();
        if (ctx.dayOfYear() == 0) {
            aux = 0.0; // clear sentinel each new year
        }

        // Leafroll symptoms most visible from BERRY_DEVELOPMENT through RIPENING
        boolean activePhase = (stage == PhenoStage.BERRY_DEVELOPMENT
                || stage == PhenoStage.VERAISON
                || stage == PhenoStage.RIPENING
                || stage == PhenoStage.SHOOT_GROWTH
                || stage == PhenoStage.FLOWERING
                || stage == PhenoStage.FRUIT_SET);

        int years    = mem.yearsActive();
        double newLevel = Math.min(1.0, years * LEVEL_PER_YEAR + 0.09);
        int newTicks = activePhase ? mem.ticksActive() + 1 : mem.ticksActive();

        ThreatMemory nextMem = new ThreatMemory(newLevel, aux, newTicks, years, true);

        if (!activePhase) {
            return ThreatEffect.none(nextMem);
        }

        double yieldLoss      = Math.min(MAX_YIELD_LOSS,      years * YIELD_LOSS_PER_YEAR);
        double qualityPenalty = Math.min(MAX_QUALITY_PENALTY, years * QUALITY_PENALTY_PER_YEAR);
        double healthDelta    = years * HEALTH_DELTA_PER_YEAR;

        String tell = newLevel > 0.40 ? TELL_SEVERE : TELL_EARLY;

        return new ThreatEffect(
                healthDelta,
                1.0 - yieldLoss,
                qualityPenalty,
                Fault.NONE,
                false,
                tell,
                nextMem);
    }
}
