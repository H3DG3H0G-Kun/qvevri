package com.game.econ;

import com.game.core.data.WineLot;
import com.game.core.data.WineStyle;

/**
 * A tradeable unit held in an {@link Inventory}.
 *
 * <p>Records are immutable; all fields are set at construction. Identity is
 * determined by the {@code id} field, which must be assigned deterministically
 * (no UUID/random sources) so that simulation runs reproduce the same IDs.
 *
 * <p>GDD Part 7/8 — Phase 2 economy, econ-core lane.
 *
 * @param id            deterministic identifier for this item instance
 * @param type          commodity category
 * @param quantity      volume or weight in natural units (litres for wine/must,
 *                      kg for grapes)
 * @param quality       hedonic quality score 0..100
 * @param vintageYear   harvest year (0 for non-vintage goods)
 * @param style         wine style; {@code null} for non-wine goods
 * @param appellationOk true if appellation rules are satisfied
 */
public record Item(
        String id,
        ItemType type,
        double quantity,
        double quality,
        int vintageYear,
        WineStyle style,
        boolean appellationOk
) {

    // ── WineLot factory ───────────────────────────────────────────────────────

    /**
     * Build an {@link ItemType#AGED_WINE} Item from a finished {@link WineLot}.
     *
     * <p>The {@code id} is derived deterministically from the lot fields:
     * {@code "lot:" + vintageYear + ":" + style + ":" + (int) quality + ":"
     * + (int)(volumeL*10)}.
     *
     * @param lot the source lot
     * @return a new Item of type {@link ItemType#AGED_WINE}
     */
    public static Item fromBottle(WineLot lot) {
        String id = "lot:" + lot.vintageYear()
                + ":" + lot.style()
                + ":" + (int) lot.quality()
                + ":" + (int) (lot.volumeL() * 10);
        return new Item(
                id,
                ItemType.AGED_WINE,
                lot.volumeL(),
                lot.quality(),
                lot.vintageYear(),
                lot.style(),
                lot.appellationOk()
        );
    }

    // ── Convenience factories ─────────────────────────────────────────────────

    /**
     * Build an Item for raw goods (grapes, must, young wine, brandy) where
     * style and appellation are not applicable.
     *
     * <p>The {@code id} is deterministic:
     * {@code type.name() + ":" + vintageYear + ":" + (int) quantity}.
     *
     * @param type        the raw-goods type
     * @param quantity    amount in natural units
     * @param quality     quality score 0..100
     * @param vintageYear harvest year (0 for non-vintage)
     * @return a new Item with {@code style=null} and {@code appellationOk=false}
     */
    public static Item ofRaw(ItemType type, double quantity, double quality, int vintageYear) {
        String id = type.name() + ":" + vintageYear + ":" + (int) quantity;
        return new Item(id, type, quantity, quality, vintageYear, null, false);
    }

    /**
     * Build a wine Item with explicit appellation status.
     *
     * <p>Convenience factory used by market and test code when the caller does not
     * have a full {@link WineLot}. Style defaults to {@link WineStyle#RED}.
     *
     * <p>Deterministic {@code id}:
     * {@code type.name() + ":" + vintageYear + ":" + (int) quality
     * + (appellationOk ? ":A" : ":N")}.
     *
     * @param quality       quality score 0..100
     * @param vintageYear   harvest year
     * @param type          item type (any WINE-family type)
     * @param appellationOk appellation flag
     * @return a new wine Item
     */
    public static Item ofWine(double quality, int vintageYear, ItemType type, boolean appellationOk) {
        String id = type.name() + ":" + vintageYear + ":" + (int) quality
                + (appellationOk ? ":A" : ":N");
        return new Item(id, type, 1.0, quality, vintageYear, WineStyle.RED, appellationOk);
    }
}
