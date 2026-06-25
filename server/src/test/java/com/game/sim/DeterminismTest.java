package com.game.sim;

import com.game.core.data.WineLot;
import com.game.sim.cellar.Fermenter;
import com.game.core.weather.WeatherModel;
import com.game.sim.vine.VineSimulator;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * §5 Acceptance test 1 — Determinism.
 *
 * <p>Verifies the reproducibility contract from SIM-SPEC §1:
 * {@code run(masterSeed, inputs)} must return a bit-for-bit equal
 * {@link WineLot} across repeated calls (on the same JVM and across JVMs).
 *
 * <p>Because {@link WineLot} is a Java {@code record} and its {@code aroma}
 * field is a {@link java.util.SortedMap} (sorted for determinism per §2),
 * structural equality via {@code assertEquals} is correct and sufficient —
 * no tolerance is needed for the determinism assertions.  The "different seeds
 * can differ" assertion uses a logical-OR so that any field variation is caught.
 *
 * <h2>Disabled reason</h2>
 * All tests in this class are disabled because the implementations of
 * {@code WeatherModel}, {@code VineSimulator}, and {@code Fermenter} have not
 * yet been delivered by lanes L1/L2/L3.  They will be enabled (or the
 * {@code @Disabled} removed) as part of the first green-build integration
 * milestone.
 */
@DisplayName("§5.1 Determinism: same seed → same WineLot")
class DeterminismTest {

    // §5.1 canonical parameters
    private static final long SEED      = 42L;
    private static final int  BUD_LOAD  = 12;
    private static final int  PICK_DAY  = 290;

    // A second seed that is expected to produce a different vintage.
    // seed=99 chosen arbitrarily; if the impl happens to produce identical
    // results for seeds 42 and 99, the test should be revisited — but that
    // would indicate a degenerate RNG and is itself a defect.
    private static final long SEED_B = 99L;

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    /**
     * Run the full pipeline twice with (seed=42, budLoad=12, pickDay=290) and
     * assert exact structural equality on the resulting {@link WineLot}.
     *
     * <p>Because WineLot is a record, {@code assertEquals} delegates to the
     * compiler-generated {@code equals()} which compares every component field,
     * including the {@code SortedMap<String,Double> aroma}.  No tolerance is
     * appropriate here — bit-for-bit reproducibility is the requirement.
     */
    @Test
    @DisplayName("Two runs with seed=42/budLoad=12/pick=290 produce identical WineLot records")
    void twoRunsWithSameSeedAreIdentical() {
        WeatherModel weather    = SimServiceLocator.weatherModel();
        VineSimulator vine      = SimServiceLocator.vineSimulator();
        Fermenter fermenter     = SimServiceLocator.fermenter();

        WineLot first  = SimTestHelper.runPipeline(SEED, BUD_LOAD, PICK_DAY, weather, vine, fermenter);
        WineLot second = SimTestHelper.runPipeline(SEED, BUD_LOAD, PICK_DAY, weather, vine, fermenter);

        // Exact equality — no tolerance.  WineLot is a record; equals() is structural.
        assertEquals(first, second,
                "Same seed must produce bit-for-bit equal WineLot across two runs");

        // Spot-check individual double fields to surface any partial inequality
        // that might slip past a buggy equals() override (defensive).
        assertEquals(first.abv(),       second.abv(),       0.0,
                "ABV must be identical across runs with the same seed");
        assertEquals(first.quality(),   second.quality(),   0.0,
                "quality must be identical across runs with the same seed");
        assertEquals(first.volumeL(),   second.volumeL(),   0.0,
                "volumeL must be identical across runs with the same seed");
        assertEquals(first.vintageYear(), second.vintageYear(),
                "vintageYear must be identical across runs with the same seed");
    }

    /**
     * Also run a MustProfile-level check: the intermediate must captured at
     * pick day must be identical between two runs with the same seed.
     *
     * <p>This catches non-determinism that is introduced early (e.g. in the
     * weather model or vine simulator) but is accidentally cancelled out later
     * in the cellar/resolver (masking the bug from the WineLot-only assertion).
     */
    @Test
    @DisplayName("Intermediate VineState at pick day is identical across two runs with same seed")
    void intermediateVineStateAtPickIsDeterministic() {
        WeatherModel weather = SimServiceLocator.weatherModel();
        VineSimulator vine   = SimServiceLocator.vineSimulator();

        com.game.core.data.VineState stateA =
                SimTestHelper.vineStateAtPickDay(SEED, BUD_LOAD, PICK_DAY, weather, vine);
        com.game.core.data.VineState stateB =
                SimTestHelper.vineStateAtPickDay(SEED, BUD_LOAD, PICK_DAY, weather, vine);

        assertEquals(stateA, stateB,
                "VineState at pick day must be identical across runs with the same seed");
        assertEquals(stateA.brix(), stateB.brix(), 0.0,
                "brix must be identical across runs (determinism)");
        assertEquals(stateA.taGL(), stateB.taGL(), 0.0,
                "taGL must be identical across runs (determinism)");
    }

    /**
     * Sanity check: two DIFFERENT seeds should (with overwhelming probability)
     * produce a different {@link WineLot}.  This guards against a broken
     * implementation that ignores the seed entirely and always returns the same
     * result (which would also pass the same-seed tests).
     *
     * <p>The assertion is a logical-OR across multiple fields.  If ALL fields
     * happen to collide for seeds 42 and 99, the warning comment in the spec
     * applies: this would indicate a degenerate RNG and is itself a defect.
     */
    @Test
    @DisplayName("Two different seeds can produce different WineLot results")
    void differentSeedsCanProduceDifferentResults() {
        WeatherModel weather = SimServiceLocator.weatherModel();
        VineSimulator vine   = SimServiceLocator.vineSimulator();
        Fermenter fermenter  = SimServiceLocator.fermenter();

        WineLot lotA = SimTestHelper.runPipeline(SEED,   BUD_LOAD, PICK_DAY, weather, vine, fermenter);
        WineLot lotB = SimTestHelper.runPipeline(SEED_B, BUD_LOAD, PICK_DAY, weather, vine, fermenter);

        // At least one field must differ.  Using assertFalse(equals) to check
        // that the two lots are not identical.
        boolean anyDifference =
                lotA.abv()     != lotB.abv()     ||
                lotA.quality() != lotB.quality() ||
                lotA.volumeL() != lotB.volumeL() ||
                !lotA.aroma().equals(lotB.aroma());

        assertTrue(anyDifference,
                "Seeds 42 and 99 should produce at least one differing WineLot field; " +
                "if they are identical the RNG or weather model may be ignoring the seed");
    }
}
