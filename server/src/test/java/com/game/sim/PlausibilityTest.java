package com.game.sim;

import com.game.core.data.*;
import com.game.sim.cellar.Fermenter;
import com.game.core.weather.WeatherModel;
import com.game.sim.vine.VineSimulator;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * §5 Acceptance test 5 — Plausibility bounds.
 *
 * <p>Verifies that a standard Saperavi/Kakheti simulation run with sensible
 * inputs produces agronomically plausible output per SIM-SPEC §5.5:
 *
 * <ul>
 *   <li>ABV in the range ~12–15% (§5.5)</li>
 *   <li>quality in [0, 100] (§2)</li>
 *   <li>style = {@link WineStyle#RED} (Saperavi is a red grape, RED fermentation
 *       method, §2 FermentMethod.RED → WineStyle.RED)</li>
 *   <li>no {@code NaN} or negative fields</li>
 *   <li>volumeL &gt; 0</li>
 *   <li>ageabilityYears ≥ 0</li>
 *   <li>fault is not {@code null} (may be {@link Fault#NONE})</li>
 *   <li>aroma map is not {@code null}, keys are sorted (SortedMap contract)</li>
 *   <li>vintageYear = 1 (the simulated year)</li>
 * </ul>
 *
 * <p>These are loose "sanity" bounds rather than precise agronomic targets;
 * they catch implementation defects such as off-by-one year handling,
 * uninitialized fields (NaN), sign errors, or wrong style mapping.
 *
 * <h2>Fixed parameters</h2>
 * seed=42, budLoad=12, pickDay=290 — the canonical §5.1 combination.
 *
 * <h2>Disabled reason</h2>
 * Tests are disabled because L1/L2/L3 implementations are not yet present.
 */
@DisplayName("§5.5 Plausibility: Saperavi/Kakheti defaults produce agronomically sensible WineLot")
class PlausibilityTest {

    private static final long SEED     = 42L;
    private static final int  BUD_LOAD = 12;
    private static final int  PICK_DAY = 290;

    /** ABV lower bound per §5.5: "~12–15%" — using 11.0 as a generous floor. */
    private static final double ABV_MIN = 11.0;

    /** ABV upper bound per §5.5: "~12–15%" — using 16.0 as a generous ceiling. */
    private static final double ABV_MAX = 16.0;

    // -------------------------------------------------------------------------
    // Single pipeline run shared across assertions
    //
    // JUnit 5 doesn't share state across @Test methods by default.  We accept
    // the small overhead of re-running the pipeline per test for isolation;
    // the tests are @Disabled anyway until implementations are available.
    // -------------------------------------------------------------------------

    private WineLot canonical() {
        return SimTestHelper.runPipeline(
                SEED, BUD_LOAD, PICK_DAY,
                SimServiceLocator.weatherModel(),
                SimServiceLocator.vineSimulator(),
                SimServiceLocator.fermenter());
    }

    // -------------------------------------------------------------------------
    // ABV
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("ABV is within ~12-15% (generous bounds 11-16%)")
    void abvIsInPlausibleRange() {
        WineLot lot = canonical();
        assertFalse(Double.isNaN(lot.abv()), "ABV must not be NaN");
        assertTrue(lot.abv() >= ABV_MIN,
                String.format("ABV %.2f%% must be ≥ %.1f%% (spec §5.5)", lot.abv(), ABV_MIN));
        assertTrue(lot.abv() <= ABV_MAX,
                String.format("ABV %.2f%% must be ≤ %.1f%% (spec §5.5)", lot.abv(), ABV_MAX));
    }

    // -------------------------------------------------------------------------
    // Quality
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("quality is in [0, 100]")
    void qualityIsInBounds() {
        WineLot lot = canonical();
        assertFalse(Double.isNaN(lot.quality()), "quality must not be NaN");
        assertTrue(lot.quality() >= 0.0,
                String.format("quality %.1f must be ≥ 0", lot.quality()));
        assertTrue(lot.quality() <= 100.0,
                String.format("quality %.1f must be ≤ 100", lot.quality()));
    }

    // -------------------------------------------------------------------------
    // Style
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("style is WineStyle.RED for Saperavi with FermentMethod.RED")
    void styleIsRed() {
        WineLot lot = canonical();
        assertEquals(WineStyle.RED, lot.style(),
                "Saperavi fermented with FermentMethod.RED must resolve to WineStyle.RED");
    }

    // -------------------------------------------------------------------------
    // No NaN / negatives
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("No NaN in any double field")
    void noNaNInAnyDoubleField() {
        WineLot lot = canonical();
        assertFalse(Double.isNaN(lot.abv()),             "abv must not be NaN");
        assertFalse(Double.isNaN(lot.quality()),         "quality must not be NaN");
        assertFalse(Double.isNaN(lot.volumeL()),         "volumeL must not be NaN");
        assertFalse(Double.isNaN(lot.ageabilityYears()), "ageabilityYears must not be NaN");
        // aroma map values
        if (lot.aroma() != null) {
            for (Map.Entry<String, Double> entry : lot.aroma().entrySet()) {
                assertFalse(Double.isNaN(entry.getValue()),
                        "aroma[\"" + entry.getKey() + "\"] must not be NaN");
            }
        }
    }

    @Test
    @DisplayName("No negative values in numeric fields")
    void noNegativeInAnyField() {
        WineLot lot = canonical();
        assertTrue(lot.abv() >= 0.0,
                String.format("abv=%.4f must not be negative", lot.abv()));
        assertTrue(lot.quality() >= 0.0,
                String.format("quality=%.4f must not be negative", lot.quality()));
        assertTrue(lot.volumeL() >= 0.0,
                String.format("volumeL=%.4f must not be negative", lot.volumeL()));
        assertTrue(lot.ageabilityYears() >= 0.0,
                String.format("ageabilityYears=%.4f must not be negative", lot.ageabilityYears()));
        // Aroma intensities must also be non-negative
        if (lot.aroma() != null) {
            for (Map.Entry<String, Double> entry : lot.aroma().entrySet()) {
                assertTrue(entry.getValue() >= 0.0,
                        String.format("aroma[\"" + entry.getKey() + "\"]=%.4f must not be negative",
                                entry.getValue()));
            }
        }
    }

    // -------------------------------------------------------------------------
    // Volume
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("volumeL > 0 (non-trivial harvest)")
    void volumeIsPositive() {
        WineLot lot = canonical();
        assertTrue(lot.volumeL() > 0.0,
                String.format("volumeL=%.4f must be > 0", lot.volumeL()));
    }

    // -------------------------------------------------------------------------
    // Structural / reference fields
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("fault field is not null")
    void faultIsNotNull() {
        WineLot lot = canonical();
        assertNotNull(lot.fault(), "fault must not be null (may be Fault.NONE)");
    }

    @Test
    @DisplayName("aroma SortedMap is not null and all keys are non-null/non-empty")
    void aromaMapIsValidAndSorted() {
        WineLot lot = canonical();
        assertNotNull(lot.aroma(), "aroma map must not be null");
        for (String key : lot.aroma().keySet()) {
            assertNotNull(key, "aroma map must not contain null keys");
            assertFalse(key.isBlank(), "aroma map must not contain blank keys");
        }
        // SortedMap contract: the iteration order must match natural String ordering
        String prev = null;
        for (String key : lot.aroma().keySet()) {
            if (prev != null) {
                assertTrue(key.compareTo(prev) > 0,
                        "aroma SortedMap keys must be in ascending order; found \""
                                + prev + "\" followed by \"" + key + "\"");
            }
            prev = key;
        }
    }

    @Test
    @DisplayName("vintageYear = 1 (the simulated year)")
    void vintageYearIsCorrect() {
        WineLot lot = canonical();
        assertEquals(1, lot.vintageYear(),
                "vintageYear must equal the simulated year (1)");
    }

    @Test
    @DisplayName("variety is SAPERAVI")
    void varietyIsSaperavi() {
        WineLot lot = canonical();
        assertEquals(Variety.SAPERAVI, lot.variety(),
                "variety must be SAPERAVI for a Saperavi vine simulation");
    }

    @Test
    @DisplayName("ageabilityYears >= 0")
    void ageabilityYearsIsNonNegative() {
        WineLot lot = canonical();
        assertTrue(lot.ageabilityYears() >= 0.0,
                String.format("ageabilityYears=%.2f must be ≥ 0", lot.ageabilityYears()));
    }

    // -------------------------------------------------------------------------
    // MustProfile plausibility (intermediate step)
    // -------------------------------------------------------------------------

    /**
     * Additional plausibility check on the must captured at pick day.
     * The spec defines MustProfile (§2) and Harvest.pick (§3.5); verifying the
     * must values guards against cellar/resolver masking a bad harvest step.
     */
    @Test
    @DisplayName("MustProfile at pick day has plausible Brix (18-28°Bx) and positive volume")
    void mustProfileIsPlausible() {
        WeatherModel weather = SimServiceLocator.weatherModel();
        VineSimulator vine   = SimServiceLocator.vineSimulator();

        VineState atPick = SimTestHelper.vineStateAtPickDay(SEED, BUD_LOAD, PICK_DAY, weather, vine);
        double volumeFromYield = atPick.potentialYieldKg() * 0.75;

        com.game.core.data.MustProfile must =
                com.game.sim.ops.Harvest.pick(atPick, volumeFromYield);

        assertFalse(Double.isNaN(must.brix()),    "must.brix must not be NaN");
        assertFalse(Double.isNaN(must.taGL()),    "must.taGL must not be NaN");
        assertFalse(Double.isNaN(must.volumeL()), "must.volumeL must not be NaN");

        assertTrue(must.volumeL() > 0.0,
                "must.volumeL must be positive");
        assertTrue(must.brix() >= 18.0 && must.brix() <= 28.0,
                String.format("must.brix=%.2f°Bx should be in harvest range 18-28°Bx", must.brix()));
        assertTrue(must.fruitHealth01() >= 0.0 && must.fruitHealth01() <= 1.0,
                String.format("must.fruitHealth01=%.3f must be in [0,1]", must.fruitHealth01()));
    }
}
