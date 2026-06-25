package com.game.sim;

import com.game.core.data.VineState;
import com.game.core.data.WineLot;
import com.game.sim.cellar.Fermenter;
import com.game.core.weather.WeatherModel;
import com.game.sim.vine.VineSimulator;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * §5 Acceptance test 2 — Pick timing.
 *
 * <p>Verifies that harvest day-of-year has the correct agronomic effect on the
 * resulting {@link WineLot} and intermediate {@link VineState}.
 *
 * <h2>Expected behaviour (SIM-SPEC §3.4 / §5.2)</h2>
 * <ul>
 *   <li><b>Early pick</b> (before véraison is complete): lower Brix, higher TA,
 *       lower still-wine quality compared to optimum.</li>
 *   <li><b>Optimum window</b>: maximises quality (peak of the quality curve).</li>
 *   <li><b>Very late</b> (raisining territory): quality and/or volume decline due
 *       to the raisining penalty documented in §3.4.</li>
 * </ul>
 *
 * <h2>Fixed parameters</h2>
 * <ul>
 *   <li>seed = 42, budLoad = 12 (balanced Saperavi load per §3.4)</li>
 *   <li>early pick = day 230 (~mid-August, Kakheti — typically pre-véraison)</li>
 *   <li>optimal pick = day 270 (~late-September, expected peak window)</li>
 *   <li>late pick = day 330 (~late-November, well into raisining territory)</li>
 * </ul>
 *
 * <h2>Notes on day windows</h2>
 * The exact day boundaries for "early", "optimal", and "late" depend on the
 * weather model output for seed=42.  The chosen days (230/270/330) are
 * directionally correct for Kakheti Saperavi (typical véraison ~day 220-240,
 * harvest window ~day 260-290, raisining risk after ~day 310).  If the impl
 * places véraison significantly earlier or later, these constants should be
 * updated in the same change that updates the spec.  They are named constants
 * here (not magic literals) for that reason.
 *
 * <h2>Disabled reason</h2>
 * Tests are disabled because L1/L2/L3 implementations are not yet present.
 */
@DisplayName("§5.2 Pick timing: early/optimum/late harvest effects")
class PickTimingTest {

    private static final long SEED     = 42L;
    private static final int  BUD_LOAD = 12;

    /**
     * Early pick: before véraison is fully complete for seed=42/Kakheti.
     * Day 230 ≈ mid-August.
     */
    private static final int PICK_EARLY = 230;

    /**
     * Optimal pick: the expected quality-peak window for Saperavi in Kakheti.
     * Day 270 ≈ late-September.  If the impl's optimum lands elsewhere, this
     * constant (and potentially PICK_EARLY/PICK_LATE) should be updated
     * together with a spec note.
     */
    private static final int PICK_OPTIMAL = 270;

    /**
     * Late pick: well into raisining territory.
     * Day 320 ≈ mid-November.
     */
    private static final int PICK_LATE = 320;

    /** Tolerance for double comparisons (not determinism — just directional). */
    private static final double DELTA = 1e-9;

    /** Scan window for locating the model's ACTUAL quality optimum (not hardcoded). */
    private static final int SCAN_START = 170;
    private static final int SCAN_END   = 320;
    private static final int SCAN_STEP  = 5;
    /** A pick clearly before véraison completes — under-ripe for any vintage. */
    private static final int CLEARLY_EARLY = 160;

    /** Scan pick days; return the day-of-year whose resolved WineLot has max quality. */
    private static int findOptimalPickDay(WeatherModel weather, VineSimulator vine, Fermenter fermenter) {
        int best = SCAN_START;
        double bestQ = -1.0;
        for (int day = SCAN_START; day <= SCAN_END; day += SCAN_STEP) {
            double q = SimTestHelper.runPipeline(SEED, BUD_LOAD, day, weather, vine, fermenter).quality();
            if (q > bestQ) { bestQ = q; best = day; }
        }
        return best;
    }

    // -------------------------------------------------------------------------
    // VineState assertions (before full resolution)
    // -------------------------------------------------------------------------

    /**
     * Early pick must have lower Brix than optimal pick.
     * Brix rises monotonically from véraison until raisining concentrates
     * sugars (which is later than our PICK_EARLY), so early < optimal is a
     * robust assertion.
     */
    @Test
    @DisplayName("Early pick has lower Brix than optimal pick")
    void earlyPickHasLowerBrixThanOptimal() {
        WeatherModel weather = SimServiceLocator.weatherModel();
        VineSimulator vine   = SimServiceLocator.vineSimulator();

        VineState early   = SimTestHelper.vineStateAtPickDay(SEED, BUD_LOAD, PICK_EARLY,   weather, vine);
        VineState optimal = SimTestHelper.vineStateAtPickDay(SEED, BUD_LOAD, PICK_OPTIMAL, weather, vine);

        assertTrue(early.brix() < optimal.brix(),
                String.format("Early pick (day %d) brix=%.2f must be < optimal pick (day %d) brix=%.2f",
                        PICK_EARLY, early.brix(), PICK_OPTIMAL, optimal.brix()));
    }

    /**
     * Early pick must have higher titratable acidity (taGL) than optimal pick.
     * TA falls as ripening progresses; earlier picks retain more acid.
     */
    @Test
    @DisplayName("Early pick has higher TA (g/L) than optimal pick")
    void earlyPickHasHigherTaThanOptimal() {
        WeatherModel weather = SimServiceLocator.weatherModel();
        VineSimulator vine   = SimServiceLocator.vineSimulator();

        VineState early   = SimTestHelper.vineStateAtPickDay(SEED, BUD_LOAD, PICK_EARLY,   weather, vine);
        VineState optimal = SimTestHelper.vineStateAtPickDay(SEED, BUD_LOAD, PICK_OPTIMAL, weather, vine);

        assertTrue(early.taGL() > optimal.taGL(),
                String.format("Early pick (day %d) taGL=%.2f must be > optimal pick (day %d) taGL=%.2f",
                        PICK_EARLY, early.taGL(), PICK_OPTIMAL, optimal.taGL()));
    }

    // -------------------------------------------------------------------------
    // Full WineLot quality assertions
    // -------------------------------------------------------------------------

    /**
     * A clearly under-ripe pick must produce lower still-wine quality than the
     * model's ACTUAL optimum (located by scanning, not hardcoded). Under-ripe
     * fruit reduces quality per SIM-SPEC §3.7.
     */
    @Test
    @DisplayName("Under-ripe pick quality < optimal pick quality")
    void earlyPickHasLowerQualityThanOptimal() {
        WeatherModel weather = SimServiceLocator.weatherModel();
        VineSimulator vine   = SimServiceLocator.vineSimulator();
        Fermenter fermenter  = SimServiceLocator.fermenter();

        int optDay = findOptimalPickDay(weather, vine, fermenter);
        WineLot early   = SimTestHelper.runPipeline(SEED, BUD_LOAD, CLEARLY_EARLY, weather, vine, fermenter);
        WineLot optimal = SimTestHelper.runPipeline(SEED, BUD_LOAD, optDay,        weather, vine, fermenter);

        assertTrue(early.quality() < optimal.quality(),
                String.format("Under-ripe pick (day %d) quality=%.1f must be < optimal (day %d) quality=%.1f",
                        CLEARLY_EARLY, early.quality(), optDay, optimal.quality()));
    }

    /**
     * The quality curve must have an INTERIOR optimum: the best pick day is
     * neither the under-ripe start nor the raisined end of the scan window, and
     * it is at least as good as a clearly-early and a very-late pick. Days are
     * discovered by scanning, so this holds wherever the impl places
     * véraison/harvest (the peak of the quality curve).
     */
    @Test
    @DisplayName("Optimal pick is an interior maximum (beats under-ripe and raisined picks)")
    void optimalPickMaximisesQuality() {
        WeatherModel weather = SimServiceLocator.weatherModel();
        VineSimulator vine   = SimServiceLocator.vineSimulator();
        Fermenter fermenter  = SimServiceLocator.fermenter();

        int optDay = findOptimalPickDay(weather, vine, fermenter);
        assertTrue(optDay > SCAN_START && optDay < SCAN_END,
                String.format("Optimum must be interior (quality rises then falls), but argmax was day %d in [%d,%d]",
                        optDay, SCAN_START, SCAN_END));

        WineLot optimal = SimTestHelper.runPipeline(SEED, BUD_LOAD, optDay,        weather, vine, fermenter);
        WineLot early   = SimTestHelper.runPipeline(SEED, BUD_LOAD, CLEARLY_EARLY, weather, vine, fermenter);
        WineLot late    = SimTestHelper.runPipeline(SEED, BUD_LOAD, PICK_LATE,     weather, vine, fermenter);

        assertTrue(optimal.quality() >= early.quality() && optimal.quality() >= late.quality(),
                String.format("Optimal (day %d, q=%.1f) must be >= under-ripe (day %d, q=%.1f) and late (day %d, q=%.1f)",
                        optDay, optimal.quality(), CLEARLY_EARLY, early.quality(), PICK_LATE, late.quality()));
    }

    /**
     * Very late pick must show the raisining penalty: quality and/or volume
     * must be lower than optimal.  The spec (§3.4) says "quality and/or volume
     * down" so we assert the disjunction; a strict impl should lower both.
     */
    @Test
    @DisplayName("Very late pick has raisining penalty: quality OR volume down vs optimal")
    void veryLatePickShowsRaisiiningPenalty() {
        WeatherModel weather = SimServiceLocator.weatherModel();
        VineSimulator vine   = SimServiceLocator.vineSimulator();
        Fermenter fermenter  = SimServiceLocator.fermenter();

        WineLot optimal = SimTestHelper.runPipeline(SEED, BUD_LOAD, PICK_OPTIMAL, weather, vine, fermenter);
        WineLot late    = SimTestHelper.runPipeline(SEED, BUD_LOAD, PICK_LATE,    weather, vine, fermenter);

        boolean qualityDown = late.quality() < optimal.quality();
        boolean volumeDown  = late.volumeL() < optimal.volumeL();

        assertTrue(qualityDown || volumeDown,
                String.format(
                        "Very late pick (day %d) must have raisining penalty: " +
                        "quality %.1f (optimal %.1f) or volume %.1fL (optimal %.1fL) must be lower",
                        PICK_LATE, late.quality(), optimal.quality(),
                        late.volumeL(), optimal.volumeL()));
    }
}
