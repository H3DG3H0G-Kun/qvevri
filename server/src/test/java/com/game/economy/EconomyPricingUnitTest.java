package com.game.economy;

import com.game.econ.WinePricer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Pure unit tests for the EconomyService pricing formulas.
 * No Spring context is needed — all tested methods are static helpers.
 *
 * <p>Covers:
 * <ol>
 *   <li>Supply factor strictly decreases as supply rises.</li>
 *   <li>Regional factor changes grossPrice across regions.</li>
 *   <li>Fee = grossPrice × FEE_RATE and netPrice = gross − fee.</li>
 *   <li>Base price constants match WinePricer (read-only guard).</li>
 *   <li>supplyFactor = 1.0 at zero supply.</li>
 * </ol>
 */
class EconomyPricingUnitTest {

    private static final double EPSILON = 1e-9;

    // ─────────────────────────────────────────────────────────────────────────
    // 1. Supply factor strictly decreases as supply rises
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("supplyFactor_strictlyDecreasesAsSupplyRises")
    void supplyFactor_strictlyDecreasesAsSupplyRises() {
        // Factor formula: SUPPLY_SCALE / (SUPPLY_SCALE + supply)
        double s0   = EconomyService.SUPPLY_SCALE / (EconomyService.SUPPLY_SCALE + 0);
        double s10  = EconomyService.SUPPLY_SCALE / (EconomyService.SUPPLY_SCALE + 10);
        double s50  = EconomyService.SUPPLY_SCALE / (EconomyService.SUPPLY_SCALE + 50);
        double s200 = EconomyService.SUPPLY_SCALE / (EconomyService.SUPPLY_SCALE + 200);

        assertThat(s0).as("factor at supply=0 must be 1.0 exactly")
                .isCloseTo(1.0, within(EPSILON));

        assertThat(s10).as("supply=10 < supply=50 → factor must be strictly higher")
                .isGreaterThan(s50);

        assertThat(s50).as("factor at supply=SCALE must be exactly 0.5")
                .isCloseTo(0.5, within(EPSILON));

        assertThat(s50).as("supply=50 < supply=200 → factor must be strictly higher")
                .isGreaterThan(s200);

        assertThat(s200).as("supply factor must remain positive")
                .isGreaterThan(0.0);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. Regional factor changes grossPrice across regions
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("regionalFactor_changesGrossAcrossRegions")
    void regionalFactor_changesGrossAcrossRegions() {
        // MESKHETI (1.12) must yield higher grossPrice than SAMEGRELO (0.90)
        // for the same base price and supply (zero here).
        double base          = WinePricer.BASE_WINE;
        double supplyFactor  = 1.0;  // supply=0

        double factorMeskheti  = EconomyService.resolveRegionalFactor("MESKHETI");
        double factorSamegrelo = EconomyService.resolveRegionalFactor("SAMEGRELO");
        double factorKakheti   = EconomyService.resolveRegionalFactor("KAKHETI");

        double grossMeskheti  = base * supplyFactor * factorMeskheti;
        double grossSamegrelo = base * supplyFactor * factorSamegrelo;
        double grossKakheti   = base * supplyFactor * factorKakheti;

        assertThat(grossMeskheti).as("MESKHETI (1.12) > KAKHETI (1.00)")
                .isGreaterThan(grossKakheti);

        assertThat(grossKakheti).as("KAKHETI (1.00) > SAMEGRELO (0.90)")
                .isGreaterThan(grossSamegrelo);

        // Verify exact values
        assertThat(factorKakheti).as("KAKHETI regional factor must be 1.00")
                .isCloseTo(1.00, within(EPSILON));
        assertThat(factorMeskheti).as("MESKHETI regional factor must be 1.12")
                .isCloseTo(1.12, within(EPSILON));
        assertThat(factorSamegrelo).as("SAMEGRELO regional factor must be 0.90")
                .isCloseTo(0.90, within(EPSILON));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. Fee = grossPrice × FEE_RATE and netPrice = gross − fee
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("feeAndNetPrice_computedCorrectly")
    void feeAndNetPrice_computedCorrectly() {
        // Worked example: WINE, KAKHETI, supply=0
        // basePrice=6.00, supplyFactor=1.0, regionalFactor=1.00
        // grossPrice=6.00, fee=0.30, netPrice=5.70
        double base          = WinePricer.BASE_WINE;             // 6.00
        double supplyFactor  = EconomyService.SUPPLY_SCALE /
                               (EconomyService.SUPPLY_SCALE + 0); // 1.0
        double regionalFactor= EconomyService.resolveRegionalFactor("KAKHETI"); // 1.00

        double grossPrice = base * supplyFactor * regionalFactor;
        double fee        = grossPrice * EconomyService.FEE_RATE;
        double netPrice   = grossPrice - fee;

        assertThat(grossPrice).as("grossPrice must be 6.00")
                .isCloseTo(6.00, within(EPSILON));

        assertThat(fee).as("fee must be 5% of grossPrice")
                .isCloseTo(grossPrice * 0.05, within(EPSILON));

        assertThat(netPrice).as("netPrice must be gross − fee = 5.70")
                .isCloseTo(5.70, within(EPSILON));

        // Invariant: netPrice = gross * (1 - FEE_RATE)
        assertThat(netPrice).as("netPrice invariant: gross × (1 − FEE_RATE)")
                .isCloseTo(grossPrice * (1.0 - EconomyService.FEE_RATE), within(EPSILON));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. Base price constants match WinePricer (read-only verification)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("resolveBasePrice_matchesWinePricerConstants")
    void resolveBasePrice_matchesWinePricerConstants() {
        assertThat(EconomyService.resolveBasePrice("GRAPES"))
                .isCloseTo(WinePricer.BASE_GRAPES, within(EPSILON));
        assertThat(EconomyService.resolveBasePrice("MUST"))
                .isCloseTo(WinePricer.BASE_MUST, within(EPSILON));
        assertThat(EconomyService.resolveBasePrice("YOUNG_WINE"))
                .isCloseTo(WinePricer.BASE_YOUNG_WINE, within(EPSILON));
        assertThat(EconomyService.resolveBasePrice("AGED_WINE"))
                .isCloseTo(WinePricer.BASE_AGED_WINE, within(EPSILON));
        assertThat(EconomyService.resolveBasePrice("CHACHA_BRANDY"))
                .isCloseTo(WinePricer.BASE_CHACHA_BRANDY, within(EPSILON));
        assertThat(EconomyService.resolveBasePrice("WINE"))
                .isCloseTo(WinePricer.BASE_WINE, within(EPSILON));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 5. Unknown itemType / region → ApiException (not NPE/crash)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("unknownItemType_throwsApiException")
    void unknownItemType_throwsApiException() {
        org.junit.jupiter.api.Assertions.assertThrows(
                com.game.exception.ApiException.class,
                () -> EconomyService.resolveBasePrice("UNKNOWN_TYPE"),
                "resolveBasePrice with unknown type must throw ApiException");
    }

    @Test
    @DisplayName("unknownRegion_throwsApiException")
    void unknownRegion_throwsApiException() {
        org.junit.jupiter.api.Assertions.assertThrows(
                com.game.exception.ApiException.class,
                () -> EconomyService.resolveRegionalFactor("ATLANTIS"),
                "resolveRegionalFactor with unknown region must throw ApiException");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 6. Worked example from Javadoc — WINE in MESKHETI, supply=10
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("workedExample_WINE_MESKHETI_supply10")
    void workedExample_WINE_MESKHETI_supply10() {
        double base            = WinePricer.BASE_WINE;                         // 6.00
        double supplyFactor    = EconomyService.SUPPLY_SCALE /
                                 (EconomyService.SUPPLY_SCALE + 10);           // 50/60 ≈ 0.8333
        double regionalFactor  = EconomyService.resolveRegionalFactor("MESKHETI"); // 1.12
        double grossPrice      = base * supplyFactor * regionalFactor;         // ≈ 5.60
        double fee             = grossPrice * EconomyService.FEE_RATE;         // ≈ 0.28
        double netPrice        = grossPrice - fee;                             // ≈ 5.32

        assertThat(grossPrice).isCloseTo(5.60, within(0.01));
        assertThat(fee).isCloseTo(0.28, within(0.01));
        assertThat(netPrice).isCloseTo(5.32, within(0.01));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 7. Higher supply must yield strictly lower grossPrice (direct function)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("moreSupply_strictlyLowerGrossPrice")
    void moreSupply_strictlyLowerGrossPrice() {
        double base           = WinePricer.BASE_WINE;
        double regionalFactor = EconomyService.resolveRegionalFactor("KAKHETI");

        double gross5  = base * (EconomyService.SUPPLY_SCALE / (EconomyService.SUPPLY_SCALE + 5))  * regionalFactor;
        double gross20 = base * (EconomyService.SUPPLY_SCALE / (EconomyService.SUPPLY_SCALE + 20)) * regionalFactor;
        double gross80 = base * (EconomyService.SUPPLY_SCALE / (EconomyService.SUPPLY_SCALE + 80)) * regionalFactor;

        assertThat(gross5).as("supply=5 must be more expensive than supply=20")
                .isGreaterThan(gross20);
        assertThat(gross20).as("supply=20 must be more expensive than supply=80")
                .isGreaterThan(gross80);
    }
}
