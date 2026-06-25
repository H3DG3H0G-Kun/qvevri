package com.game.sim.threats;

import com.game.core.data.WineLot;
import com.game.sim.threats.harness.ThreatYearRunner;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * §6.1 — Same seed/levers must produce bit-identical results across two runs.
 */
class DeterminismTest {

    @Test
    void sameInputsProduceIdenticalWineLot() {
        WineLot r1 = ThreatYearRunner.run(42L, 12, 290, false);
        WineLot r2 = ThreatYearRunner.run(42L, 12, 290, false);

        assertEquals(r1.quality(),         r2.quality(),         "quality must be identical");
        assertEquals(r1.fault(),           r2.fault(),           "fault must be identical");
        assertEquals(r1.abv(),             r2.abv(),             "ABV must be identical");
        assertEquals(r1.volumeL(),         r2.volumeL(),         "volume must be identical");
        assertEquals(r1.ageabilityYears(), r2.ageabilityYears(), "ageability must be identical");
        assertEquals(r1.aroma(),           r2.aroma(),           "aroma map must be identical");
        assertEquals(r1.style(),           r2.style(),           "style must be identical");
        assertEquals(r1.vintageYear(),     r2.vintageYear(),     "vintage year must be identical");
    }

    @Test
    void differentSeedsProduceDifferentBottles() {
        WineLot r1 = ThreatYearRunner.run(42L,  12, 290, false);
        WineLot r2 = ThreatYearRunner.run(999L, 12, 290, false);

        // At least one field must differ with a different seed
        boolean differ = r1.quality() != r2.quality()
                      || r1.volumeL() != r2.volumeL()
                      || r1.abv()     != r2.abv();
        assertTrue(differ, "Different seeds should produce at least one differing field");
    }

    @Test
    void threeRunsAllIdentical() {
        WineLot r1 = ThreatYearRunner.run(7L, 12, 290, false);
        WineLot r2 = ThreatYearRunner.run(7L, 12, 290, false);
        WineLot r3 = ThreatYearRunner.run(7L, 12, 290, false);

        assertEquals(r1.quality(), r2.quality(), "run1==run2 quality");
        assertEquals(r2.quality(), r3.quality(), "run2==run3 quality");
        assertEquals(r1.aroma(),   r2.aroma(),   "run1==run2 aroma");
        assertEquals(r2.aroma(),   r3.aroma(),   "run2==run3 aroma");
    }
}
