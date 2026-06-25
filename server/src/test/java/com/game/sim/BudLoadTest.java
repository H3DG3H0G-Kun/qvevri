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
 * §5 Acceptance test 3 — Bud load effects.
 *
 * <p>Verifies that the pruning decision ({@code budLoad}) produces the correct
 * agronomic trade-offs as specified in SIM-SPEC §3.4:
 *
 * <ul>
 *   <li><b>Overburden</b> (high budLoad): vine carries too many clusters;
 *       Brix rises more slowly; harvest Brix is lower; quality penalty applies.</li>
 *   <li><b>Underburden</b> (low budLoad): vine is under-cropped; potential
 *       yield (volume) is very low; quality may be fine but commercial viability
 *       is impacted.</li>
 *   <li><b>Balanced</b> (mid budLoad, Saperavi target ~12): best product of
 *       quality × volume.</li>
 * </ul>
 *
 * <h2>Fixed parameters</h2>
 * <ul>
 *   <li>seed = 42</li>
 *   <li>pickDay = 290 (canonical §5.1 pick day, within the optimal window for
 *       the balanced load)</li>
 *   <li>BUD_LOW = 4 (severely underburdened — strip to almost nothing)</li>
 *   <li>BUD_MID = 12 (spec-stated Saperavi target, §3.4)</li>
 *   <li>BUD_HIGH = 24 (overburdened — double the target)</li>
 * </ul>
 *
 * <h2>Disabled reason</h2>
 * Tests are disabled because L1/L2/L3 implementations are not yet present.
 */
@DisplayName("§5.3 Bud load: overburden/underburden/balanced harvest effects")
class BudLoadTest {

    private static final long SEED     = 42L;
    private static final int  PICK_DAY = 290;

    /** Severely underburdened — very few clusters, low potential yield. */
    private static final int BUD_LOW  = 4;

    /**
     * Spec-stated Saperavi sweet spot (§3.4: "e.g. ~budLoad≈12").
     * This is the reference point for quality×volume optimum.
     */
    private static final int BUD_MID  = 12;

    /**
     * Overburdened — double the target.  At this load the vine should show
     * slower Brix accumulation and a quality penalty.
     */
    private static final int BUD_HIGH = 24;

    // -------------------------------------------------------------------------
    // Overburden assertions
    // -------------------------------------------------------------------------

    /**
     * An overburdened vine must have lower Brix at pick day than the balanced vine.
     * The spec states: "overburdened vine ripens slower (Brix rises more slowly per GDD)".
     */
    @Test
    @DisplayName("Overburden (budLoad=24) produces lower harvest Brix than balanced (budLoad=12)")
    void overburdenLowersHarvestBrix() {
        WeatherModel weather = SimServiceLocator.weatherModel();
        VineSimulator vine   = SimServiceLocator.vineSimulator();

        VineState balanced    = SimTestHelper.vineStateAtPickDay(SEED, BUD_MID,  PICK_DAY, weather, vine);
        VineState overburdened = SimTestHelper.vineStateAtPickDay(SEED, BUD_HIGH, PICK_DAY, weather, vine);

        assertTrue(overburdened.brix() < balanced.brix(),
                String.format("Overburden brix=%.2f must be < balanced brix=%.2f",
                        overburdened.brix(), balanced.brix()));
    }

    /**
     * An overburdened vine must produce lower quality wine than the balanced vine.
     * The spec states: "overburdened vine ... loses quality" (§3.4).
     */
    @Test
    @DisplayName("Overburden (budLoad=24) produces lower quality wine than balanced (budLoad=12)")
    void overburdenLowersWineQuality() {
        WeatherModel weather = SimServiceLocator.weatherModel();
        VineSimulator vine   = SimServiceLocator.vineSimulator();
        Fermenter fermenter  = SimServiceLocator.fermenter();

        WineLot balanced     = SimTestHelper.runPipeline(SEED, BUD_MID,  PICK_DAY, weather, vine, fermenter);
        WineLot overburdened = SimTestHelper.runPipeline(SEED, BUD_HIGH, PICK_DAY, weather, vine, fermenter);

        assertTrue(overburdened.quality() < balanced.quality(),
                String.format("Overburden quality=%.1f must be < balanced quality=%.1f",
                        overburdened.quality(), balanced.quality()));
    }

    // -------------------------------------------------------------------------
    // Underburden assertions
    // -------------------------------------------------------------------------

    /**
     * An underburdened vine must produce lower volume than the balanced vine.
     * The spec states: "underburdened vine gives little yield" (§3.4).
     */
    @Test
    @DisplayName("Underburden (budLoad=4) produces lower volume than balanced (budLoad=12)")
    void underburdenLowersVolume() {
        WeatherModel weather = SimServiceLocator.weatherModel();
        VineSimulator vine   = SimServiceLocator.vineSimulator();
        Fermenter fermenter  = SimServiceLocator.fermenter();

        WineLot balanced      = SimTestHelper.runPipeline(SEED, BUD_MID, PICK_DAY, weather, vine, fermenter);
        WineLot underburdened = SimTestHelper.runPipeline(SEED, BUD_LOW, PICK_DAY, weather, vine, fermenter);

        assertTrue(underburdened.volumeL() < balanced.volumeL(),
                String.format("Underburden volume=%.2fL must be < balanced volume=%.2fL",
                        underburdened.volumeL(), balanced.volumeL()));
    }

    /**
     * Underburden potential yield must also be reflected in a lower
     * {@code potentialYieldKg} in the VineState at pick day.
     * This tests the vine simulator in isolation, before the cellar step.
     */
    @Test
    @DisplayName("Underburden (budLoad=4) VineState.potentialYieldKg < balanced (budLoad=12)")
    void underburdenLowersPotentialYieldKg() {
        WeatherModel weather = SimServiceLocator.weatherModel();
        VineSimulator vine   = SimServiceLocator.vineSimulator();

        VineState balanced     = SimTestHelper.vineStateAtPickDay(SEED, BUD_MID, PICK_DAY, weather, vine);
        VineState underburdened = SimTestHelper.vineStateAtPickDay(SEED, BUD_LOW,  PICK_DAY, weather, vine);

        assertTrue(underburdened.potentialYieldKg() < balanced.potentialYieldKg(),
                String.format("Underburden potentialYieldKg=%.3f must be < balanced potentialYieldKg=%.3f",
                        underburdened.potentialYieldKg(), balanced.potentialYieldKg()));
    }

    // -------------------------------------------------------------------------
    // Balanced sweet-spot: best quality × volume
    // -------------------------------------------------------------------------

    /**
     * The balanced mid-load must produce a higher quality×volume product than
     * either extreme.  This is the core "sweet spot" assertion from §5.3.
     *
     * <p>quality×volume is a dimensionally meaningful proxy for total value
     * (quality score times litres produced).  Neither a very small volume at
     * high quality nor a large volume at low quality should beat the balanced
     * case.
     */
    @Test
    @DisplayName("Balanced budLoad=12 maximises quality×volume over low=4 and high=24")
    void balancedBudLoadMaximisesQualityTimesVolume() {
        WeatherModel weather = SimServiceLocator.weatherModel();
        VineSimulator vine   = SimServiceLocator.vineSimulator();
        Fermenter fermenter  = SimServiceLocator.fermenter();

        WineLot low      = SimTestHelper.runPipeline(SEED, BUD_LOW,  PICK_DAY, weather, vine, fermenter);
        WineLot balanced = SimTestHelper.runPipeline(SEED, BUD_MID,  PICK_DAY, weather, vine, fermenter);
        WineLot high     = SimTestHelper.runPipeline(SEED, BUD_HIGH, PICK_DAY, weather, vine, fermenter);

        double scoreBalanced = balanced.quality() * balanced.volumeL();
        double scoreLow      = low.quality()      * low.volumeL();
        double scoreHigh     = high.quality()     * high.volumeL();

        assertTrue(scoreBalanced > scoreLow,
                String.format("Balanced Q×V=%.1f must beat underburdened Q×V=%.1f",
                        scoreBalanced, scoreLow));
        assertTrue(scoreBalanced > scoreHigh,
                String.format("Balanced Q×V=%.1f must beat overburdened Q×V=%.1f",
                        scoreBalanced, scoreHigh));
    }
}
