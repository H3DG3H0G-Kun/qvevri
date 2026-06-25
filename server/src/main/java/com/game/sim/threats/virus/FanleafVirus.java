package com.game.sim.threats.virus;

import com.game.core.data.Fault;
import com.game.core.data.PhenoStage;
import com.game.sim.threats.api.ThreatCategory;
import com.game.sim.threats.api.ThreatContext;
import com.game.sim.threats.api.ThreatEffect;
import com.game.sim.threats.api.ThreatMemory;
import com.game.sim.threats.api.ThreatSource;

/**
 * Grapevine Fanleaf Virus (GFLV) — transmitted by the dagger nematode
 * (Xiphinema index) which persists in soil for decades. One of the most
 * destructive grapevine viruses.
 *
 * <h3>Chronic, no-cure model:</h3>
 * <ul>
 *   <li>Once established ({@code memory.established = true}), the virus cannot
 *       be removed from an existing vine — the ONLY fix is replanting with
 *       virus-free, certified planting stock. Modelled as a permanent latch.
 *   <li>Per-year establishment chance is low ({@code ANNUAL_ESTABLISH_PROB}).
 *       Establishment is attempted once per "season start" window (early spring,
 *       proxied by dayOfYear ≤ {@code SEASON_START_DOY}). Rewards using clean
 *       planting stock (assumed if {@code ownRoots = false} in the context, as
 *       grafted stock from certified nurseries is routinely tested).
 *   <li>Once established the virus slowly saps yield and retards ripening each
 *       season. Damage accumulates with {@code memory.yearsActive}.
 * </ul>
 *
 * <h3>Symptoms:</h3>
 * <ul>
 *   <li>Fan-shaped leaf distortion ("fanleaf"), yellow mosaic or vein-banding.
 *   <li>Delayed budbreak, shortened internodes, double nodes.
 *   <li>Yield reduced 30–80% in severe cases; reduced ripeness (Brix/tannins).
 * </ul>
 *
 * <h3>Memory mapping:</h3>
 * <ul>
 *   <li>{@code level}      — chronic severity 0..1 (rises with yearsActive)
 *   <li>{@code aux}        — not used (0)
 *   <li>{@code ticksActive}— days with active symptom expression this season
 *   <li>{@code yearsActive}— seasons since establishment (key driver of damage)
 *   <li>{@code established}— permanent latch
 * </ul>
 *
 * <h3>Tell:</h3>
 * "Fan-shaped leaf distortion + yellow mosaic — Grapevine Fanleaf Virus (GFLV)."
 */
public final class FanleafVirus implements ThreatSource {

    // ── Establishment ─────────────────────────────────────────────────────────
    /**
     * Day-of-year by which the seasonal "can it establish this year?" check runs.
     * Nematode feeding is most active in spring when soils warm.
     */
    private static final int    SEASON_START_DOY         = 90;
    /**
     * Per-season (not per-day) probability that virus transfers to a clean vine
     * via nematode feeding. Low — reflects real-world slow spread.
     */
    private static final double ANNUAL_ESTABLISH_PROB     = 0.04;
    /**
     * Certified/grafted stock (ownRoots=false) dramatically reduces establishment
     * risk because nurseries screen for GFLV.
     */
    private static final double CERTIFIED_STOCK_REDUCTION = 0.80;

    // ── Damage ramp (function of yearsActive) ────────────────────────────────
    /** Yield multiplier loss per year of infection (accumulates). */
    private static final double YIELD_LOSS_PER_YEAR      = 0.045;
    /** Quality penalty per year of infection. */
    private static final double QUALITY_PENALTY_PER_YEAR  = 0.008;
    /** Health drain per day from active virus once established. */
    private static final double HEALTH_DELTA_PER_YEAR     = -0.0005;
    /** Caps on accumulated multiplier loss. */
    private static final double MAX_YIELD_LOSS            = 0.75;
    private static final double MAX_QUALITY_PENALTY       = 0.30;

    // ── Level (severity) ramp ─────────────────────────────────────────────────
    private static final double LEVEL_PER_YEAR            = 0.10;

    // ── Establishment-attempt flag: use aux as a within-year sentinel ────────
    /** aux = 1.0 once the seasonal establishment check has been performed. */
    private static final double AUX_CHECKED               = 1.0;

    // ── Tells ─────────────────────────────────────────────────────────────────
    private static final String TELL_EARLY   = "Fan-shaped leaf distortion — early Grapevine Fanleaf Virus (GFLV) symptoms.";
    private static final String TELL_SEVERE  = "Severe GFLV: yellow mosaic, distorted shoots, significant yield loss.";

    @Override
    public String id() {
        return "virus.fanleaf";
    }

    @Override
    public ThreatCategory category() {
        return ThreatCategory.VIRUS;
    }

    @Override
    public ThreatEffect evaluate(ThreatContext ctx) {
        ThreatMemory mem  = ctx.memory();
        boolean established = mem.established();

        // ── Attempt establishment once per season (early spring check) ────────
        if (!established) {
            return attemptEstablishment(ctx, mem);
        }

        // ── Established: chronic damage ───────────────────────────────────────
        return applyChronicDamage(ctx, mem);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ThreatEffect attemptEstablishment(ThreatContext ctx, ThreatMemory mem) {
        int doy = ctx.dayOfYear();

        // Reset the within-year check sentinel at season start (doy == 0)
        if (doy == 0) {
            return ThreatEffect.none(new ThreatMemory(0, 0, 0, mem.yearsActive(), false));
        }

        // Only attempt once per season, at the spring window
        if (doy > SEASON_START_DOY) {
            return ThreatEffect.none(mem); // window passed; try again next year
        }
        if (mem.aux() >= AUX_CHECKED) {
            return ThreatEffect.none(mem); // already attempted this season
        }

        // Mark attempt as done (aux = 1.0)
        double prob = ANNUAL_ESTABLISH_PROB;
        // Grafted / certified stock reduces risk
        if (!ctx.ownRoots()) {
            prob *= (1.0 - CERTIFIED_STOCK_REDUCTION);
        }

        boolean established = ctx.rng().nextDouble() < prob;

        if (!established) {
            ThreatMemory next = new ThreatMemory(0, AUX_CHECKED, 0, mem.yearsActive(), false);
            return ThreatEffect.none(next);
        }

        ThreatMemory next = new ThreatMemory(
                LEVEL_PER_YEAR, // initial low severity
                AUX_CHECKED,
                1,
                0,              // yearsActive starts at 0 (engine bumps at season end)
                true);
        return new ThreatEffect(
                -0.005, 1.0, 0.003,
                Fault.NONE, false,
                "Nematode transmission detected — Grapevine Fanleaf Virus newly established.",
                next);
    }

    private ThreatEffect applyChronicDamage(ThreatContext ctx, ThreatMemory mem) {
        PhenoStage stage = ctx.vine().stage();

        // Reset the within-year sentinel each new season
        double aux = mem.aux();
        if (ctx.dayOfYear() == 0) {
            aux = 0.0; // new year: clear sentinel (established stays true)
        }

        // Virus symptoms are visible from budbreak through leaf fall
        boolean activePhase = (stage != PhenoStage.DORMANCY && stage != PhenoStage.BUD_SWELL);

        int years = mem.yearsActive(); // incremented at season end by engine

        // Severity level ramps up with years
        double newLevel = Math.min(1.0, years * LEVEL_PER_YEAR + 0.10);
        int newTicks    = activePhase ? mem.ticksActive() + 1 : mem.ticksActive();

        ThreatMemory nextMem = new ThreatMemory(newLevel, aux, newTicks, years, true);

        if (!activePhase) {
            return ThreatEffect.none(nextMem);
        }

        double yieldLoss     = Math.min(MAX_YIELD_LOSS,    years * YIELD_LOSS_PER_YEAR);
        double qualityPenalty = Math.min(MAX_QUALITY_PENALTY, years * QUALITY_PENALTY_PER_YEAR);
        double healthDelta   = years * HEALTH_DELTA_PER_YEAR;

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
