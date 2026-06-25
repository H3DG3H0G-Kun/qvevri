package com.game.econ;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Plain JUnit 5 tests for {@link WinePricer#price}.
 *
 * <p>No Spring context needed — WinePricer is a pure pricing function with
 * no infrastructure dependencies.
 *
 * <p>Tests are coded against the literal contract names; if WinePricer or
 * MarketContext do not yet exist these tests will fail to compile — that is
 * the correct signal and must not be papered over by stubs.
 *
 * <h2>Tolerance</h2>
 * Double comparisons use a tolerance of 0.001 except where exact equality
 * is contractually required (no such case in pricing — all are ordinal
 * comparisons).
 */
@DisplayName("WinePricer — pricing contract (econ)")
class WinePricerTest {

    private static final double TOLERANCE = 0.001;

    // ── Shared baseline MarketContext (moderate supply, no appellation premium) ──

    /**
     * Build a baseline {@link MarketContext} with controlled variables.
     * Supply is set to a moderate value (100 units) unless overridden.
     * Appellation is false unless overridden.
     */
    private MarketContext baseContext(int supply) {
        return new MarketContext(supply, false);
    }

    private MarketContext baseContextAppellation(int supply, boolean appellationOk) {
        return new MarketContext(supply, appellationOk);
    }

    // ── Item factory helpers ───────────────────────────────────────────────────

    /** Build an Item with the given quality (same vintage year + appellation for isolation). */
    private Item wineItem(double quality, boolean appellationOk) {
        return Item.ofWine(quality, /* vintageYear */ 1, ItemType.WINE, appellationOk);
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    /**
     * pricing_higherQualityCostsMore
     *
     * <p>WinePricer.price must rise strictly with Item quality, all else equal.
     * Spec: quality 70 priced higher than quality 55 (same vintage, same scarcity).
     */
    @Test
    @DisplayName("pricing_higherQualityCostsMore: q=70 priced higher than q=55 all else equal")
    void pricing_higherQualityCostsMore() {
        Item itemHigh = wineItem(70.0, false);
        Item itemLow  = wineItem(55.0, false);

        MarketContext ctx = baseContext(100);

        double priceHigh = WinePricer.price(itemHigh, ctx);
        double priceLow  = WinePricer.price(itemLow,  ctx);

        assertThat(priceHigh)
                .as("WinePricer.price(quality=70) must be strictly greater than price(quality=55) "
                        + "— got high=%.4f low=%.4f".formatted(priceHigh, priceLow))
                .isGreaterThan(priceLow + TOLERANCE);
    }

    /**
     * pricing_appellationPremium
     *
     * <p>appellationOk=true must price higher than appellationOk=false, all else equal.
     * Spec: appellation premium is a positive uplift on the final price.
     */
    @Test
    @DisplayName("pricing_appellationPremium: appellationOk=true > appellationOk=false")
    void pricing_appellationPremium() {
        double quality = 65.0;
        Item itemWithAppellation    = wineItem(quality, true);
        Item itemWithoutAppellation = wineItem(quality, false);

        MarketContext ctx = baseContext(100);

        double priceWith    = WinePricer.price(itemWithAppellation,    ctx);
        double priceWithout = WinePricer.price(itemWithoutAppellation, ctx);

        assertThat(priceWith)
                .as("WinePricer.price with appellationOk=true must exceed price with appellationOk=false "
                        + "— got with=%.4f without=%.4f".formatted(priceWith, priceWithout))
                .isGreaterThan(priceWithout + TOLERANCE);
    }

    /**
     * pricing_scarcityRaisesPrice
     *
     * <p>Lower supply in MarketContext must yield a higher price, all else equal.
     * Spec: scarcity (lower supply) raises price.
     * Uses supply=10 (scarce) vs supply=200 (abundant).
     */
    @Test
    @DisplayName("pricing_scarcityRaisesPrice: lower supply -> higher price")
    void pricing_scarcityRaisesPrice() {
        Item item = wineItem(65.0, false);

        MarketContext scarce    = baseContext(10);
        MarketContext abundant  = baseContext(200);

        double priceScarce   = WinePricer.price(item, scarce);
        double priceAbundant = WinePricer.price(item, abundant);

        assertThat(priceScarce)
                .as("WinePricer.price with low supply (10) must exceed price with high supply (200) "
                        + "— got scarce=%.4f abundant=%.4f".formatted(priceScarce, priceAbundant))
                .isGreaterThan(priceAbundant + TOLERANCE);
    }
}
