package com.game.sim.threats.pest;

import com.game.core.data.Fault;
import com.game.core.data.SoilType;
import com.game.sim.threats.api.ThreatCategory;
import com.game.sim.threats.api.ThreatContext;
import com.game.sim.threats.api.ThreatEffect;
import com.game.sim.threats.api.ThreatMemory;
import com.game.sim.threats.api.ThreatSource;

/**
 * Phylloxera (Daktulosphaira vitifoliae) — root-feeding louse.
 *
 * <p>Gating rules (per SIM-THREATS-SPEC §4, Lane C):
 * <ul>
 *   <li>SAND soil: immune — louse cannot survive in loose sandy soil.</li>
 *   <li>Grafted vine ({@code ownRoots=false}): safe — American rootstock is
 *       resistant; no establishment, no damage.</li>
 *   <li>Own-roots on any non-SAND soil: phylloxera can establish and is
 *       TERMINAL across seasons via {@link ThreatMemory#yearsActive()} and
 *       {@link ThreatMemory#established()}.</li>
 * </ul>
 *
 * <p>Season model:
 * <ul>
 *   <li>Year 1 ({@code yearsActive == 0} and not yet established): louse
 *       colonises — vine survives but health declines modestly.
 *       {@code established} latches to {@code true} at end of first season
 *       (simulated here as first active day).</li>
 *   <li>Year 2+ ({@code established == true}): accelerating root destruction;
 *       each season the health drain is larger; yield multiplier falls.
 *       {@code killVine} fires once health would reach ≤0 (deferred one tick
 *       so the engine sees the dying vine for one final day).</li>
 * </ul>
 *
 * <p>Memory usage:
 * <ul>
 *   <li>{@code level} — cumulative infection 0..1 (rises each active day).</li>
 *   <li>{@code aux} — unused (0).</li>
 *   <li>{@code ticksActive} — days active this season.</li>
 *   <li>{@code yearsActive} — full seasons with established infestation.</li>
 *   <li>{@code established} — latched once infestation takes hold.</li>
 * </ul>
 */
public final class Phylloxera implements ThreatSource {

    // ---- constants -------------------------------------------------------

    /** Daily health drain in year 1 (before established). Mild. */
    private static final double HEALTH_DRAIN_YEAR1 = 0.0015;

    /** Daily health drain once established (year 2+). Severe. */
    private static final double HEALTH_DRAIN_ESTABLISHED = 0.004;

    /**
     * Extra drain multiplier per additional year beyond the first established
     * year, capped so total drain per day is bounded.
     */
    private static final double DRAIN_ESCALATION_PER_YEAR = 0.0008;

    /** Maximum daily health drain (years 3+). */
    private static final double HEALTH_DRAIN_MAX = 0.012;

    /** Yield multiplier when established. Declines further each year. */
    private static final double YIELD_MULT_ESTABLISHED_BASE = 0.97;

    /** Additional yield reduction per year established. */
    private static final double YIELD_MULT_PER_YEAR = 0.01;

    /** Minimum yield multiplier floor. */
    private static final double YIELD_MULT_MIN = 0.70;

    /** Quality penalty per day once established. */
    private static final double QUALITY_PENALTY_ESTABLISHED = 0.0005;

    /** Quality penalty per day in year 1. */
    private static final double QUALITY_PENALTY_YEAR1 = 0.0001;

    /** Infection level increment per day (feeds memory.level). */
    private static final double LEVEL_INCREMENT_PER_DAY = 0.002;

    // ---- ThreatSource impl -----------------------------------------------

    @Override
    public String id() {
        return "pest.phylloxera";
    }

    @Override
    public ThreatCategory category() {
        return ThreatCategory.PEST;
    }

    @Override
    public ThreatEffect evaluate(ThreatContext ctx) {
        // SAND is immune — louse cannot survive in sandy soil
        if (ctx.site().soil() == SoilType.SAND) {
            return ThreatEffect.none(ThreatMemory.none());
        }
        // Grafted vines are safe
        if (!ctx.ownRoots()) {
            return ThreatEffect.none(ThreatMemory.none());
        }

        // --- Own-roots on susceptible soil: phylloxera is active ---
        ThreatMemory mem = ctx.memory();
        boolean wasEstablished = mem.established();
        int yearsActive = mem.yearsActive();
        double level = Math.min(1.0, mem.level() + LEVEL_INCREMENT_PER_DAY);
        int ticksActive = mem.ticksActive() + 1;

        // Latch established on the first active day
        boolean established = true;

        double healthDrain;
        double yieldMult;
        double qualityPenalty;
        String tell;

        if (!wasEstablished) {
            // Year 1: colonisation phase — vine survives with declining health
            healthDrain = HEALTH_DRAIN_YEAR1;
            yieldMult = 1.0; // yield not yet impacted in establishment year
            qualityPenalty = QUALITY_PENALTY_YEAR1;
            tell = "phylloxera galls on roots — first-season colonisation";
        } else {
            // Year 2+: established infestation escalates
            double extraDrain = DRAIN_ESCALATION_PER_YEAR * Math.max(0, yearsActive - 1);
            healthDrain = Math.min(HEALTH_DRAIN_MAX, HEALTH_DRAIN_ESTABLISHED + extraDrain);
            double yieldMf = Math.max(YIELD_MULT_MIN,
                    YIELD_MULT_ESTABLISHED_BASE - YIELD_MULT_PER_YEAR * yearsActive);
            yieldMult = yieldMf;
            qualityPenalty = QUALITY_PENALTY_ESTABLISHED;
            tell = yearsActive >= 2
                    ? "phylloxera destroying root system — vine decline accelerating"
                    : "phylloxera established — root damage visible";
        }

        // killVine when vine health (after drain) would reach 0 and infestation
        // has been established for ≥2 full seasons
        double projectedHealth = ctx.vine().healthFraction() - healthDrain;
        boolean killVine = (wasEstablished && yearsActive >= 2 && projectedHealth <= 0.0);

        ThreatMemory next = new ThreatMemory(level, 0.0, ticksActive, yearsActive, established);

        return new ThreatEffect(
                -healthDrain,
                yieldMult,
                qualityPenalty,
                Fault.NONE,
                killVine,
                tell,
                next);
    }
}
