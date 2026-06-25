package com.game.festival;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Static catalog of all world festival events for the QVEVRI game.
 *
 * <p>Four Georgian wine-culture festivals with stable ids. IDs are used as FK
 * references in {@link FestivalParticipation}; never rename an id once data is
 * in production.
 *
 * <p>Day-of-year windows are 0-based (0..364) and non-wrapping (v1):
 * {@code endDayOfYear >= startDayOfYear}. A day {@code d} is inside the window
 * if {@code startDayOfYear <= d <= endDayOfYear}.
 *
 * <p>Georgian calendar context (approximate mapping onto 0-based day-of-year,
 * where day 0 ≈ 1 January):
 * <ul>
 *   <li>days 80–100  ≈ late March / early April — spring vine blessing</li>
 *   <li>days 130–150 ≈ mid May — qvevri sealing / opening ceremony</li>
 *   <li>days 240–270 ≈ late August / September — Rtveli harvest festival</li>
 *   <li>days 300–320 ≈ late October / early November — new wine fair</li>
 * </ul>
 */
public final class FestivalCalendar {

    private static final Map<String, FestivalDefinition> BY_ID;

    static {
        Map<String, FestivalDefinition> m = new LinkedHashMap<>();

        add(m, new FestivalDefinition(
                "vine_blessing",
                "Vine Blessing",
                "Each spring the priests of Alaverdi Monastery descend into the vineyards "
                + "to bless the new shoots. Viticulturists gather to pray for frost-free nights "
                + "and to exchange cuttings of prized Saperavi clones.",
                80,
                100,
                "YIELD_BOOST",
                0.10,
                30.0));

        add(m, new FestivalDefinition(
                "qvevri_opening",
                "Qvevri Opening",
                "The sealed qvevris from last harvest are opened for the first time in spring. "
                + "Winemakers, merchants, and pilgrims crowd the marani to taste the new amber "
                + "wine as it is ladled up from the clay vessels buried in the earth.",
                130,
                150,
                "QUALITY_BOOST",
                0.05,
                40.0));

        add(m, new FestivalDefinition(
                "rtveli",
                "Rtveli",
                "The great Georgian harvest festival. Families, neighbours, and hired pickers "
                + "flood the vineyards of Kakheti. Traditional songs fill the rows; the tamada "
                + "leads toasts from dusk until dawn. Participation earns the blessing of a "
                + "bountiful rtveli and a stipend from the regional wine council.",
                240,
                270,
                "HARVEST_BONUS",
                0.20,
                75.0));

        add(m, new FestivalDefinition(
                "new_wine_fair",
                "New Wine Fair",
                "After fermentation the young wine is bottled and brought to the Telavi bazaar. "
                + "Tasters, traders, and collectors bid for the best lots. The regional wine "
                + "guild awards a seal of approval to outstanding producers.",
                300,
                320,
                "PRICE_BOOST",
                0.15,
                50.0));

        BY_ID = Collections.unmodifiableMap(m);
    }

    private FestivalCalendar() {}

    private static void add(Map<String, FestivalDefinition> m, FestivalDefinition f) {
        m.put(f.getId(), f);
    }

    /**
     * Returns all festival definitions in catalog insertion order.
     *
     * @return unmodifiable collection of all {@link FestivalDefinition}s
     */
    public static Collection<FestivalDefinition> all() {
        return BY_ID.values();
    }

    /**
     * Returns all festivals whose [{@code startDayOfYear}, {@code endDayOfYear}]
     * window contains {@code dayOfYear}.
     *
     * @param dayOfYear current simulation day-of-year (0..364)
     * @return list of active {@link FestivalDefinition}s (may be empty)
     */
    public static List<FestivalDefinition> active(int dayOfYear) {
        return BY_ID.values().stream()
                .filter(f -> dayOfYear >= f.getStartDayOfYear()
                          && dayOfYear <= f.getEndDayOfYear())
                .collect(Collectors.toList());
    }

    /**
     * Looks up a festival by its stable id.
     *
     * @param id festival id string (e.g. "rtveli")
     * @return the {@link FestivalDefinition}, or {@code null} if not found
     */
    public static FestivalDefinition find(String id) {
        return BY_ID.get(id);
    }
}
