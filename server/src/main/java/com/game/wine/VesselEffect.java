package com.game.wine;

import com.game.goods.GoodCategory;
import com.game.goods.GoodType;
import com.game.goods.GoodsCatalog;

/**
 * Computes the style override and quality delta that a chosen VESSEL imparts
 * on a fermenting CellarItem (BACKEND-DEPTH-SPEC §6 "Vessel" effect).
 *
 * <p><b>Gating rule (§6 golden rule):</b> when {@code vesselGoodTypeId} is
 * {@code null} all methods return the unmodified defaults — the existing
 * instant-harvest path is byte-identical.
 *
 * <p>All methods are pure and stateless. No RNG; determinism is preserved.
 *
 * <h2>Effect model (v1)</h2>
 * <pre>
 *   material    | style override | quality delta
 *   ────────────┼────────────────┼──────────────
 *   qvevri      | AMBER          | +3.0
 *   oak         | RED (no change)| +1.5
 *   steel       | WHITE (neutral)| +0.5
 *   (none/null) | unchanged      | 0.0
 * </pre>
 *
 * <p>The style override only applies when a vessel is explicitly selected;
 * the incoming {@code originalStyle} is returned unchanged otherwise.
 */
public final class VesselEffect {

    /** Quality bonus when fermenting in a qvevri (clay). */
    public static final double QVEVRI_QUALITY_DELTA = 3.0;

    /** Quality bonus when fermenting in oak. */
    public static final double OAK_QUALITY_DELTA    = 1.5;

    /** Quality bonus when fermenting in steel. */
    public static final double STEEL_QUALITY_DELTA  = 0.5;

    private VesselEffect() { /* utility class */ }

    /**
     * Returns the style name that should be applied to the CellarItem after
     * fermentation in the given vessel.
     *
     * @param vesselGoodTypeId catalog id of the vessel OwnedGood, or {@code null}
     * @param originalStyle    the style already on the CellarItem
     * @return the resolved style name (unchanged when no vessel is selected)
     */
    public static String resolveStyle(String vesselGoodTypeId, String originalStyle) {
        if (vesselGoodTypeId == null) {
            return originalStyle; // no vessel → unchanged
        }
        GoodType gt = GoodsCatalog.find(vesselGoodTypeId);
        if (gt == null || gt.getCategory() != GoodCategory.VESSEL) {
            return originalStyle; // unknown / non-vessel good → unchanged
        }
        String material = (String) gt.getAttributes().get("material");
        if (material == null) {
            return originalStyle;
        }
        return switch (material.toLowerCase()) {
            case "qvevri" -> "AMBER";
            case "oak"    -> originalStyle; // oak keeps original style
            case "steel"  -> "WHITE";
            default       -> originalStyle;
        };
    }

    /**
     * Returns the quality delta imparted by the vessel.
     * Returns {@code 0.0} when no vessel is selected.
     *
     * @param vesselGoodTypeId catalog id of the vessel, or {@code null}
     * @return quality delta to ADD to the base quality (may be 0.0)
     */
    public static double qualityDelta(String vesselGoodTypeId) {
        if (vesselGoodTypeId == null) {
            return 0.0;
        }
        GoodType gt = GoodsCatalog.find(vesselGoodTypeId);
        if (gt == null || gt.getCategory() != GoodCategory.VESSEL) {
            return 0.0;
        }
        String material = (String) gt.getAttributes().get("material");
        if (material == null) {
            return 0.0;
        }
        return switch (material.toLowerCase()) {
            case "qvevri" -> QVEVRI_QUALITY_DELTA;
            case "oak"    -> OAK_QUALITY_DELTA;
            case "steel"  -> STEEL_QUALITY_DELTA;
            default       -> 0.0;
        };
    }

    /**
     * How many sim-days fermentation takes in the given vessel.
     * Qvevri = 21 days (traditional extended skin-contact); oak = 14 days;
     * steel = 10 days (controlled-temperature fast fermentation).
     * No vessel = 14 days default (mirrors the instant-path's conceptual baseline,
     * but this value is only relevant when fermentation is explicitly started).
     *
     * @param vesselGoodTypeId catalog id of the vessel, or {@code null}
     * @return fermentation duration in sim-days
     */
    public static int fermentationDays(String vesselGoodTypeId) {
        if (vesselGoodTypeId == null) {
            return 14; // default when no vessel specified
        }
        GoodType gt = GoodsCatalog.find(vesselGoodTypeId);
        if (gt == null || gt.getCategory() != GoodCategory.VESSEL) {
            return 14;
        }
        String material = (String) gt.getAttributes().get("material");
        if (material == null) {
            return 14;
        }
        return switch (material.toLowerCase()) {
            case "qvevri" -> 21;
            case "oak"    -> 14;
            case "steel"  -> 10;
            default       -> 14;
        };
    }
}
