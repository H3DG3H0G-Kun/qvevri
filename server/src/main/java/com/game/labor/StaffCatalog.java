package com.game.labor;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Static catalog of all hireable NPC staff roles for the QVEVRI game.
 *
 * <p>Four Georgian wine-estate roles, each granting a passive benefit while ACTIVE.
 * IDs are stable strings used as FK references in {@link HiredStaff#getStaffTypeId()};
 * never rename an id once data is in production (add a migration instead).
 *
 * <p>Benefit types: YIELD / QUALITY / CRAFT / SALES
 * (string discriminators; deeper pipeline integration is a future pass).
 *
 * <p>All {@code hireCostGel} values are <= 60 GEL so tests that start with a
 * 100 GEL wallet can afford any single hire immediately.
 */
public final class StaffCatalog {

    private static final Map<String, StaffRole> BY_ID;

    static {
        Map<String, StaffRole> m = new LinkedHashMap<>();

        // Vineyard hand — tends the vines, boosts grape yield
        add(m, new StaffRole(
                "vineyard_hand",
                "Vineyard Hand",
                30.0,   // hireCostGel
                5.0,    // dailyWageGel
                "YIELD",
                10.0    // +10% yield modifier
        ));

        // Cellar master — expert fermentation, boosts wine quality
        add(m, new StaffRole(
                "cellar_master",
                "Cellar Master",
                60.0,
                8.0,
                "QUALITY",
                8.0     // +8 quality points
        ));

        // Cooper apprentice — crafts and repairs barrels/qvevri, boosts craft output
        add(m, new StaffRole(
                "cooper_apprentice",
                "Cooper Apprentice",
                40.0,
                6.0,
                "CRAFT",
                5.0     // +5 craft speed modifier
        ));

        // Merchant clerk — handles sales and negotiation, boosts sales revenue
        add(m, new StaffRole(
                "merchant_clerk",
                "Merchant Clerk",
                50.0,
                7.0,
                "SALES",
                12.0    // +12% sales margin modifier
        ));

        BY_ID = Collections.unmodifiableMap(m);
    }

    private StaffCatalog() {}

    private static void add(Map<String, StaffRole> m, StaffRole r) {
        m.put(r.id(), r);
    }

    /**
     * Returns all staff role definitions in catalog insertion order.
     *
     * @return unmodifiable collection of all {@link StaffRole}s
     */
    public static Collection<StaffRole> all() {
        return BY_ID.values();
    }

    /**
     * Looks up a role by its stable id.
     *
     * @param id role id string (e.g. "vineyard_hand")
     * @return the {@link StaffRole}, or {@code null} if not found
     */
    public static StaffRole find(String id) {
        return BY_ID.get(id);
    }
}
