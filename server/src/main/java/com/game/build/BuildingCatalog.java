package com.game.build;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Static catalog of the four estate building types for the QVEVRI economy.
 *
 * <p>IDs are stable strings used as string FKs in {@link Building}.
 * Never rename an id once data is in production.
 *
 * <p><b>Affordability design</b> (characters start at 100 GEL wallet):
 * <ul>
 *   <li><b>COTTAGE (starter)</b>: costGel=30 + 1× cover_crop_seed (catalog price 14 GEL).
 *       Total shop cost 44 GEL — easily within the 100 GEL starting wallet.
 *       Used for all happy-path tests.</li>
 *   <li><b>MARANI</b>: costGel=80 + 2× clay_lining_compound (35 GEL each).
 *       Requires buying inputs first (80 GEL cash needed after 70 GEL goods purchase
 *       — will need a wallet top-up in tests; use COTTAGE for happy path).</li>
 *   <li><b>PRESS_HOUSE</b>: costGel=60 + 1× basket_press.
 *       basket_press costs 320 GEL, so player must have purchased it already;
 *       cash portion is affordable standalone.</li>
 *   <li><b>CELLAR</b>: costGel=70 + 1× oak_barrel_225l.
 *       oak_barrel_225l costs 520 GEL — player must own it already.</li>
 * </ul>
 */
public final class BuildingCatalog {

    private static final Map<String, BuildingType> BY_ID;

    static {
        Map<String, BuildingType> m = new LinkedHashMap<>();

        // ── COTTAGE (starter building — affordable within 100 GEL) ───────────
        // costGel=30, input: 1× cover_crop_seed (14 GEL). Total: 44 GEL.
        m.put("COTTAGE", new BuildingType(
                "COTTAGE",
                "Estate Cottage",
                30.0,
                List.of(new BuildingTypeInput("cover_crop_seed", 1.0)),
                "STORAGE",
                50.0));

        // ── MARANI (traditional Georgian winery) ─────────────────────────────
        // costGel=80, inputs: 2× clay_lining_compound (35 GEL each = 70 GEL).
        m.put("MARANI", new BuildingType(
                "MARANI",
                "Marani (Winery)",
                80.0,
                List.of(new BuildingTypeInput("clay_lining_compound", 2.0)),
                "WINE_QUALITY",
                0.05));

        // ── CELLAR (aging cellar) ─────────────────────────────────────────────
        // costGel=70, inputs: 1× oak_barrel_225l (520 GEL — must already own).
        m.put("CELLAR", new BuildingType(
                "CELLAR",
                "Stone Cellar",
                70.0,
                List.of(new BuildingTypeInput("oak_barrel_225l", 1.0)),
                "AGING_CAP",
                5.0));

        // ── PRESS_HOUSE (extraction building) ────────────────────────────────
        // costGel=60, inputs: 1× basket_press (320 GEL — must already own).
        m.put("PRESS_HOUSE", new BuildingType(
                "PRESS_HOUSE",
                "Press House",
                60.0,
                List.of(new BuildingTypeInput("basket_press", 1.0)),
                "EXTRACTION",
                0.04));

        BY_ID = Collections.unmodifiableMap(m);
    }

    private BuildingCatalog() {}

    /**
     * Returns every BuildingType in catalog insertion order.
     *
     * @return unmodifiable collection of all four building types
     */
    public static Collection<BuildingType> all() {
        return BY_ID.values();
    }

    /**
     * Looks up a BuildingType by stable id (case-sensitive).
     *
     * @param id the building type id (e.g. "MARANI")
     * @return the BuildingType, or {@code null} if not found
     */
    public static BuildingType find(String id) {
        return BY_ID.get(id);
    }
}
