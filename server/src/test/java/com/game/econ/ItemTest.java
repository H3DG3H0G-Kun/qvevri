package com.game.econ;

import com.game.core.data.WineLot;
import com.game.core.data.WineStyle;
import com.game.core.data.Variety;
import com.game.core.data.Fault;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Plain JUnit 5 tests for {@link Item} and {@link Inventory}.
 *
 * <p>No Spring context needed — Item and Inventory are pure domain objects.
 *
 * <h2>Contracts tested</h2>
 * <ul>
 *   <li>{@code Item.fromBottle(WineLot)} — field mapping test</li>
 *   <li>{@code Inventory} add / remove / list consistency test</li>
 * </ul>
 */
@DisplayName("Item and Inventory — domain object contract (econ)")
class ItemTest {

    private static final double TOLERANCE = 0.001;

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** Build a minimal WineLot suitable for round-trip testing. */
    private WineLot buildWineLot(double quality, int vintageYear,
                                  WineStyle style, boolean appellationOk) {
        TreeMap<String, Double> aroma = new TreeMap<>();
        aroma.put("dark-fruit", 0.75);
        aroma.put("spice",      0.50);
        aroma.put("acid",       0.40);
        return new WineLot(
                Variety.SAPERAVI,
                style,
                vintageYear,
                /* volumeL */        6.3,
                /* abv */            13.9,
                quality,
                /* ageabilityYears */ 7.5,
                Fault.NONE,
                aroma,
                appellationOk,
                "Test Bottle"
        );
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    /**
     * itemFromBottle_mapsWineLot
     *
     * <p>{@code Item.fromBottle(WineLot)} must carry over:
     * <ul>
     *   <li>quality (within tolerance)</li>
     *   <li>vintageYear (exact)</li>
     *   <li>style as a string or enum that matches WineStyle.RED</li>
     *   <li>appellationOk (exact boolean)</li>
     * </ul>
     */
    @Test
    @DisplayName("itemFromBottle_mapsWineLot: quality/vintage/style/appellation copied correctly")
    void itemFromBottle_mapsWineLot() {
        WineLot lot = buildWineLot(71.2, 1, WineStyle.RED, false);

        Item item = Item.fromBottle(lot);

        assertThat(item)
                .as("Item.fromBottle must return a non-null Item")
                .isNotNull();

        // quality
        assertThat(item.quality())
                .as("Item.quality must match WineLot.quality (within %.3f)".formatted(TOLERANCE))
                .isCloseTo(lot.quality(), within(TOLERANCE));

        // vintageYear
        assertThat(item.vintageYear())
                .as("Item.vintageYear must match WineLot.vintageYear")
                .isEqualTo(lot.vintageYear());

        // style — contract says string or enum; check it is not null and represents "RED"
        assertThat(item.style())
                .as("Item.style must not be null")
                .isNotNull();
        assertThat(item.style().toString())
                .as("Item.style must represent WineStyle.RED")
                .isEqualToIgnoringCase("RED");

        // appellationOk
        assertThat(item.appellationOk())
                .as("Item.appellationOk must match WineLot.appellationOk")
                .isEqualTo(lot.appellationOk());
    }

    /**
     * itemFromBottle_mapsWineLot_withAppellationTrue
     *
     * <p>Variant: appellationOk=true is preserved when converting a WineLot
     * that satisfies appellation requirements.
     */
    @Test
    @DisplayName("itemFromBottle_mapsWineLot: appellationOk=true preserved")
    void itemFromBottle_appellationTrueMapped() {
        WineLot lot = buildWineLot(80.0, 2, WineStyle.RED, true);

        Item item = Item.fromBottle(lot);

        assertThat(item.appellationOk())
                .as("Item.appellationOk must be true when WineLot.appellationOk is true")
                .isTrue();
    }

    /**
     * inventory_addRemoveList
     *
     * <p>Basic Inventory operations must be consistent and deterministically ordered:
     * <ul>
     *   <li>After add(), contains() returns true</li>
     *   <li>After remove(), contains() returns false</li>
     *   <li>list() returns items in a consistent order (calling twice → same order)</li>
     *   <li>list() returns only the items currently in the inventory</li>
     * </ul>
     */
    @Test
    @DisplayName("inventory_addRemoveList: add/remove/list are consistent and ordered")
    void inventory_addRemoveList() {
        Inventory inv = new Inventory("player-test-999");

        Item item1 = Item.ofWine(70.0, 1, ItemType.WINE, false);
        Item item2 = Item.ofWine(60.0, 2, ItemType.WINE, false);
        Item item3 = Item.ofWine(80.0, 1, ItemType.WINE, true);

        // Initially empty
        assertThat(inv.list())
                .as("Fresh inventory must be empty")
                .isEmpty();

        // Add items
        inv.add(item1);
        inv.add(item2);
        inv.add(item3);

        assertThat(inv.contains(item1)).as("item1 must be present after add").isTrue();
        assertThat(inv.contains(item2)).as("item2 must be present after add").isTrue();
        assertThat(inv.contains(item3)).as("item3 must be present after add").isTrue();
        assertThat(inv.list()).as("list() must contain all three items").hasSize(3);

        // Deterministic ordering: list() called twice must return items in same order
        var listA = inv.list();
        var listB = inv.list();
        assertThat(listA)
                .as("Inventory.list() must return a deterministically ordered snapshot")
                .containsExactlyElementsOf(listB);

        // Remove one item
        inv.remove(item2);
        assertThat(inv.contains(item2))
                .as("item2 must be absent after remove()")
                .isFalse();
        assertThat(inv.contains(item1))
                .as("item1 must still be present after removing item2")
                .isTrue();
        assertThat(inv.contains(item3))
                .as("item3 must still be present after removing item2")
                .isTrue();
        assertThat(inv.list())
                .as("list() must contain exactly item1 and item3 after removing item2")
                .hasSize(2);

        // Remove remaining items
        inv.remove(item1);
        inv.remove(item3);
        assertThat(inv.list())
                .as("Inventory must be empty after removing all items")
                .isEmpty();
    }
}
