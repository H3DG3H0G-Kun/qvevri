package com.game.research;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Static catalog of all research nodes for the QVEVRI tech tree.
 *
 * <p>Six Georgian winemaking / estate nodes in two tiers:
 * <ul>
 *   <li>Tier 1 (no prereq): improved_pruning, temp_control, rootstock_program, logistics_network</li>
 *   <li>Tier 2 (needs a Tier-1 node first): cold_soak (prereq temp_control), barrel_program (prereq temp_control)</li>
 * </ul>
 *
 * <p>Cost note: at least one Tier-1 node is &le; 50 GEL so the happy-path test
 * is reachable within the default 100 GEL starting wallet.
 * Specifically: {@code improved_pruning} costs 30 GEL.
 *
 * <p>IDs are stable strings. Never rename an id once data is in production;
 * add a migration if a rename is needed.
 */
public final class ResearchCatalog {

    private static final Map<String, ResearchNode> BY_ID;

    static {
        Map<String, ResearchNode> m = new LinkedHashMap<>();

        // ── Tier 1 — no prerequisites ──────────────────────────────────────────

        add(m, new ResearchNode(
                "improved_pruning",
                "Improved Pruning Techniques",
                "Advanced canopy-management training for the vineyard hands of Kakheti. "
                + "Reduces over-cropping risk and boosts berry uniformity, yielding more "
                + "consistent harvests across years.",
                30.0,   // 30 GEL — affordable on starting wallet
                3,      // 3 sim-days
                null,   // no prereq
                "YIELD_BONUS",
                0.08    // +8 % yield
        ));

        add(m, new ResearchNode(
                "temp_control",
                "Temperature Control",
                "Install a basic cooling system in the marani to manage fermentation "
                + "temperature. Cooler, slower ferments preserve aromatic compounds and "
                + "reduce the risk of stuck fermentation.",
                45.0,   // 45 GEL
                4,      // 4 sim-days
                null,   // no prereq
                "FERMENT_SPEED",
                0.15    // 15 % faster fermentation
        ));

        add(m, new ResearchNode(
                "rootstock_program",
                "Rootstock Program",
                "Source certified phylloxera-resistant rootstocks from the Georgian "
                + "National Vine Agency. Grafting onto resistant rootstocks provides "
                + "long-term protection of your estate.",
                60.0,   // 60 GEL
                6,      // 6 sim-days
                null,   // no prereq
                "PHYLLOXERA_RESIST",
                0.50    // 50 % reduction in phylloxera damage
        ));

        add(m, new ResearchNode(
                "logistics_network",
                "Regional Logistics Network",
                "Establish preferred-shipper agreements with carters across the Georgian "
                + "highway network. Volume discounts reduce your shipping cost per "
                + "kilometre substantially.",
                40.0,   // 40 GEL
                3,      // 3 sim-days
                null,   // no prereq
                "SHIPPING_COST_REDUCTION",
                0.20    // 20 % cheaper shipping
        ));

        // ── Tier 2 — prereq: temp_control ─────────────────────────────────────

        add(m, new ResearchNode(
                "cold_soak",
                "Cold Soak Maceration",
                "A pre-fermentation cold soak extracts colour and flavour compounds "
                + "without alcohol, producing wines of exceptional depth and fruit "
                + "intensity. Requires the Temperature Control infrastructure.",
                55.0,   // 55 GEL
                5,      // 5 sim-days
                "temp_control",   // prereq: temp_control must be COMPLETE
                "QUALITY_BONUS",
                0.06    // +6 quality points
        ));

        add(m, new ResearchNode(
                "barrel_program",
                "French Oak Barrel Program",
                "Source a rotation of 225-litre French oak barrels to age your finest "
                + "Saperavi. Barrel aging adds structure, tannin integration, and "
                + "vanilla-spice complexity to your wines.",
                70.0,   // 70 GEL
                7,      // 7 sim-days
                "temp_control",   // prereq: temp_control must be COMPLETE
                "AGING_QUALITY",
                0.10    // +10 quality points from barrel aging
        ));

        BY_ID = Collections.unmodifiableMap(m);
    }

    private ResearchCatalog() {}

    private static void add(Map<String, ResearchNode> m, ResearchNode n) {
        m.put(n.id(), n);
    }

    /**
     * Returns all research nodes in catalog insertion order.
     *
     * @return unmodifiable collection of all {@link ResearchNode}s
     */
    public static Collection<ResearchNode> all() {
        return BY_ID.values();
    }

    /**
     * Looks up a research node by its stable id.
     *
     * @param id node id string (e.g. "improved_pruning")
     * @return the {@link ResearchNode}, or {@code null} if not found
     */
    public static ResearchNode find(String id) {
        return BY_ID.get(id);
    }
}
