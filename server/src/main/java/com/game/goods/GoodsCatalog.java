package com.game.goods;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Static catalog of all purchasable goods in the QVEVRI bazaar.
 *
 * <p>22 goods across 5 categories with Georgian flavor. IDs are stable strings
 * used as FKs in {@link OwnedGood}; never rename an id once data is in
 * production (add a migration instead).
 *
 * <p>Price rationale (GEL):
 * – Vine stock: cheap per unit; players buy 10-100 at a time.
 * – Vessels: major capital investments (300-2500 GEL); meaningful wallet sink.
 * – Equipment: mid-range one-time purchases (200-1200 GEL).
 * – Inputs: consumable per-season costs (5-80 GEL/unit).
 * – Tools: durable, low-cost (25-90 GEL).
 */
public final class GoodsCatalog {

    private static final Map<String, GoodType> BY_ID;

    static {
        Map<String, GoodType> m = new LinkedHashMap<>();

        // ── VINE STOCK ────────────────────────────────────────────────────────
        add(m, new GoodType(
                "saperavi_cuttings_certified",
                GoodCategory.VINE_STOCK,
                "Saperavi Certified Grafted Cutting",
                18.0, true,
                Map.of("variety", "SAPERAVI", "certified", true, "ownRoots", false)));

        add(m, new GoodType(
                "saperavi_cuttings_own_root",
                GoodCategory.VINE_STOCK,
                "Saperavi Own-Root Cutting",
                9.0, true,
                Map.of("variety", "SAPERAVI", "certified", false, "ownRoots", true)));

        add(m, new GoodType(
                "rkatsiteli_cuttings_certified",
                GoodCategory.VINE_STOCK,
                "Rkatsiteli Certified Grafted Cutting",
                16.0, true,
                Map.of("variety", "RKATSITELI", "certified", true, "ownRoots", false)));

        add(m, new GoodType(
                "rkatsiteli_cuttings_own_root",
                GoodCategory.VINE_STOCK,
                "Rkatsiteli Own-Root Cutting",
                8.0, true,
                Map.of("variety", "RKATSITELI", "certified", false, "ownRoots", true)));

        add(m, new GoodType(
                "tsolikouri_cuttings_certified",
                GoodCategory.VINE_STOCK,
                "Tsolikouri Certified Grafted Cutting",
                20.0, true,
                Map.of("variety", "TSOLIKOURI", "certified", true, "ownRoots", false)));

        // ── VESSEL ────────────────────────────────────────────────────────────
        add(m, new GoodType(
                "qvevri_300l",
                GoodCategory.VESSEL,
                "Qvevri 300 L (Clay Amphora)",
                420.0, false,
                Map.of("capacityL", 300.0, "material", "qvevri")));

        add(m, new GoodType(
                "qvevri_500l",
                GoodCategory.VESSEL,
                "Qvevri 500 L (Clay Amphora)",
                650.0, false,
                Map.of("capacityL", 500.0, "material", "qvevri")));

        add(m, new GoodType(
                "qvevri_1000l",
                GoodCategory.VESSEL,
                "Qvevri 1000 L (Clay Amphora)",
                1150.0, false,
                Map.of("capacityL", 1000.0, "material", "qvevri")));

        add(m, new GoodType(
                "oak_barrel_225l",
                GoodCategory.VESSEL,
                "French Oak Barrel 225 L",
                520.0, false,
                Map.of("capacityL", 225.0, "material", "oak")));

        add(m, new GoodType(
                "steel_tank_500l",
                GoodCategory.VESSEL,
                "Stainless Steel Tank 500 L",
                780.0, false,
                Map.of("capacityL", 500.0, "material", "steel")));

        add(m, new GoodType(
                "steel_tank_2000l",
                GoodCategory.VESSEL,
                "Stainless Steel Tank 2000 L",
                2400.0, false,
                Map.of("capacityL", 2000.0, "material", "steel")));

        // ── EQUIPMENT ─────────────────────────────────────────────────────────
        add(m, new GoodType(
                "basket_press",
                GoodCategory.EQUIPMENT,
                "Traditional Basket Press",
                320.0, false,
                Map.of("qualityTier", 1)));

        add(m, new GoodType(
                "hydraulic_press",
                GoodCategory.EQUIPMENT,
                "Hydraulic Membrane Press",
                1200.0, false,
                Map.of("qualityTier", 3)));

        add(m, new GoodType(
                "crusher_destemmer",
                GoodCategory.EQUIPMENT,
                "Mechanical Crusher-Destemmer",
                750.0, false,
                Map.of("qualityTier", 2)));

        // ── INPUT (consumable) ────────────────────────────────────────────────
        add(m, new GoodType(
                "copper_sulfate",
                GoodCategory.INPUT,
                "Copper Sulfate (Bordeaux Mix, 1 kg)",
                12.0, true,
                Map.of("potency", 0.8)));

        add(m, new GoodType(
                "sulfur_dust",
                GoodCategory.INPUT,
                "Wettable Sulfur Dust (1 kg)",
                8.0, true,
                Map.of("potency", 0.7)));

        add(m, new GoodType(
                "bird_netting",
                GoodCategory.INPUT,
                "Bird Netting (100 m roll)",
                55.0, true,
                Map.of("potency", 1.0)));

        add(m, new GoodType(
                "cover_crop_seed",
                GoodCategory.INPUT,
                "Cover-Crop Seed Mix (1 kg)",
                14.0, true,
                Map.of("potency", 0.6)));

        add(m, new GoodType(
                "clay_lining_compound",
                GoodCategory.INPUT,
                "Qvevri Clay Lining Compound (5 kg)",
                35.0, true,
                Map.of("potency", 0.9)));

        // ── TOOL (durable) ────────────────────────────────────────────────────
        add(m, new GoodType(
                "pruning_shears",
                GoodCategory.TOOL,
                "Felco-Style Pruning Shears",
                45.0, false,
                Map.of()));

        add(m, new GoodType(
                "hoe",
                GoodCategory.TOOL,
                "Traditional Georgian Hoe (Takhuri)",
                25.0, false,
                Map.of()));

        add(m, new GoodType(
                "refractometer",
                GoodCategory.TOOL,
                "Handheld Refractometer (Brix)",
                70.0, false,
                Map.of()));

        add(m, new GoodType(
                "ph_meter",
                GoodCategory.TOOL,
                "Digital pH Meter",
                90.0, false,
                Map.of()));

        BY_ID = Collections.unmodifiableMap(m);
    }

    private GoodsCatalog() {}

    private static void add(Map<String, GoodType> m, GoodType gt) {
        m.put(gt.getId(), gt);
    }

    /** Returns every GoodType in catalog insertion order. */
    public static Collection<GoodType> all() {
        return BY_ID.values();
    }

    /**
     * Looks up a GoodType by stable id.
     *
     * @param id catalog id string (e.g. "qvevri_500l")
     * @return the GoodType, or {@code null} if not found
     */
    public static GoodType find(String id) {
        return BY_ID.get(id);
    }
}
