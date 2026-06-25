package com.game.skill;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Static catalog of all talents in the QVEVRI skill tree.
 *
 * <p>Eight Georgian winemaking / estate talents in two tiers:
 * <ul>
 *   <li>Tier 1 (no prereq): green_thumb, master_palate, shrewd_bargainer,
 *       frugal_logistics, deep_cellar, haggler, vintner_eye</li>
 *   <li>Tier 2 (prereq green_thumb): vine_whisperer</li>
 * </ul>
 *
 * <p>Point costs: 1–3 points each, designed so all 8 talents cost 17 points
 * total — players choose carefully within their 5 starting points.
 *
 * <p>IDs are stable strings. Never rename an id once data is in production;
 * add a migration if a rename is needed.
 */
public final class SkillCatalog {

    private static final Map<String, SkillTalent> BY_ID;

    static {
        Map<String, SkillTalent> m = new LinkedHashMap<>();

        // ── Tier 1 — no prerequisites ──────────────────────────────────────────

        add(m, new SkillTalent(
                "green_thumb",
                "Green Thumb",
                "An intuitive bond with the vine. Your vines yield more fruit per row, "
                + "turning each growing season into a more abundant harvest across all "
                + "parcels in Kakheti and beyond.",
                1,      // 1 point
                null,   // no prereq
                "YIELD",
                0.05    // +5% yield
        ));

        add(m, new SkillTalent(
                "master_palate",
                "Master Palate",
                "Decades of tasting have refined your sensory acuity. You instinctively "
                + "identify and coax the finest aromatic compounds from the must, lifting "
                + "the intrinsic quality of every wine you make.",
                2,      // 2 points
                null,   // no prereq
                "QUALITY",
                0.05    // +5% quality
        ));

        add(m, new SkillTalent(
                "shrewd_bargainer",
                "Shrewd Bargainer",
                "You have a merchant's eye and a trader's tongue. Whether at the bazaar "
                + "or the cellar gate, you consistently command a better price when "
                + "selling your wines and spirits.",
                2,      // 2 points
                null,   // no prereq
                "SELL_MARGIN",
                0.05    // +5% sell margin
        ));

        add(m, new SkillTalent(
                "frugal_logistics",
                "Frugal Logistics",
                "Long years managing estate carters have taught you to consolidate "
                + "shipments and negotiate discounts with regional haulers, cutting the "
                + "cost of moving goods across the Georgian highway network.",
                2,      // 2 points
                null,   // no prereq
                "SHIPPING_DISCOUNT",
                0.10    // 10% shipping cost reduction
        ));

        add(m, new SkillTalent(
                "deep_cellar",
                "Deep Cellar",
                "Your marani is expanded with a vaulted underground cellar. The constant "
                + "cool temperature extends how long wine can be aged before quality "
                + "begins to degrade, adding years to your finest bottles.",
                3,      // 3 points
                null,   // no prereq
                "AGING_CAP",
                3.0     // +3 sim-years of aging capacity
        ));

        add(m, new SkillTalent(
                "haggler",
                "Haggler",
                "You know how to walk away from a deal and mean it. Suppliers and "
                + "nurserymen have learned not to overprice to you — your buying "
                + "costs for vine inputs and goods are reliably lower.",
                2,      // 2 points
                null,   // no prereq
                "BUY_DISCOUNT",
                0.05    // 5% buy price discount
        ));

        add(m, new SkillTalent(
                "vintner_eye",
                "Vintner's Eye",
                "Your palate is so finely calibrated that the quality grades you assign "
                + "in the lab are rarely disputed. Enologist certification bonuses and "
                + "accurate quality scoring come naturally to you.",
                2,      // 2 points
                null,   // no prereq
                "GRADE_ACCURACY",
                0.05    // +5% grade accuracy bonus
        ));

        // ── Tier 2 — prereq: green_thumb ──────────────────────────────────────

        add(m, new SkillTalent(
                "vine_whisperer",
                "Vine Whisperer",
                "Your deep rapport with the vine extends to an almost supernatural "
                + "awareness of vine stress. Threats — fungal, pest, and weather — "
                + "are spotted earlier and resisted more effectively across your estate.",
                3,      // 3 points
                "green_thumb",  // prereq: green_thumb must be learned first
                "THREAT_RESIST",
                0.10    // 10% threat resistance
        ));

        BY_ID = Collections.unmodifiableMap(m);
    }

    private SkillCatalog() {}

    private static void add(Map<String, SkillTalent> m, SkillTalent t) {
        m.put(t.id(), t);
    }

    /**
     * Returns all talents in catalog insertion order.
     *
     * @return unmodifiable collection of all {@link SkillTalent}s
     */
    public static Collection<SkillTalent> all() {
        return BY_ID.values();
    }

    /**
     * Looks up a talent by its stable id.
     *
     * @param id talent id string (e.g. "green_thumb")
     * @return the {@link SkillTalent}, or {@code null} if not found
     */
    public static SkillTalent find(String id) {
        return BY_ID.get(id);
    }
}
