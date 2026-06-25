package com.game.achievement;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Static catalog of all milestone achievements in the QVEVRI game.
 *
 * <p>Six Georgian wine-flavoured achievements rewarding key career milestones.
 * IDs are stable strings used as FK references in {@link PlayerAchievement};
 * never rename an id once data is in production (add a migration instead).
 *
 * <p>Reward good IDs come from {@link com.game.goods.GoodsCatalog}:
 * saperavi_cuttings_certified, qvevri_300l, copper_sulfate.
 * A null rewardGoodTypeId means no goods reward (GEL only).
 */
public final class AchievementCatalog {

    private static final Map<String, AchievementDefinition> BY_ID;

    static {
        Map<String, AchievementDefinition> m = new LinkedHashMap<>();

        add(m, new AchievementDefinition(
                "first_estate",
                "Lord of the Soil",
                "You have claimed your first parcel of Georgian land. Every great marani "
                + "begins with soil — congratulations on planting your roots in the homeland.",
                50.0,
                "saperavi_cuttings_certified",
                5.0));

        add(m, new AchievementDefinition(
                "first_qvevri",
                "Keeper of the Clay",
                "A qvevri buried in the earth is the soul of Georgian winemaking. "
                + "You have acquired your first clay amphora and honoured the ancient craft.",
                75.0,
                "qvevri_300l",
                1.0));

        add(m, new AchievementDefinition(
                "master_vintner",
                "Master Vintner",
                "Your wines have reached a quality that draws the eyes of the Telavi elite. "
                + "The judges of the Kakheti valley recognise your mastery of the vine.",
                150.0,
                "copper_sulfate",
                10.0));

        add(m, new AchievementDefinition(
                "wealthy_merchant",
                "Golden Wallet",
                "Wealth flows to those who understand the bazaar. You have accumulated "
                + "enough GEL to prove you are a serious player in the Georgian wine trade.",
                200.0,
                null,
                0.0));

        add(m, new AchievementDefinition(
                "guild_founder",
                "Founder of the Wine House",
                "You have gathered fellow vintners under one banner and founded a guild. "
                + "The wine houses of Georgia bow their heads in recognition.",
                100.0,
                "saperavi_cuttings_certified",
                10.0));

        add(m, new AchievementDefinition(
                "globetrotter",
                "Wandering Vintner",
                "You have shipped goods across the seven regions of Georgia — from Kakheti "
                + "to Adjara, your name travels with every amphora on the cart.",
                125.0,
                null,
                0.0));

        BY_ID = Collections.unmodifiableMap(m);
    }

    private AchievementCatalog() {}

    private static void add(Map<String, AchievementDefinition> m, AchievementDefinition a) {
        m.put(a.getId(), a);
    }

    /**
     * Returns all achievement definitions in catalog insertion order.
     *
     * @return unmodifiable collection of all {@link AchievementDefinition}s
     */
    public static Collection<AchievementDefinition> all() {
        return BY_ID.values();
    }

    /**
     * Looks up an achievement by its stable id.
     *
     * @param id achievement id string (e.g. "first_estate")
     * @return the {@link AchievementDefinition}, or {@code null} if not found
     */
    public static AchievementDefinition find(String id) {
        return BY_ID.get(id);
    }
}
