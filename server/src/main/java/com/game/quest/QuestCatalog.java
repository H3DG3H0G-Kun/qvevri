package com.game.quest;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Static catalog of all quests for the QVEVRI game.
 *
 * <p>A tiered progression arc of 29 Georgian wine-flavoured quests with NPC
 * givers, mapped to the five phases of {@code docs/CONTENT-BIBLE.md}
 * (Glekhi → Smallholder → Estate Owner → Wine House → Tavadi). IDs are stable
 * strings used as FK references in {@link PlayerQuest}; never rename an id
 * once data is in production (add a migration instead). The original five quest
 * ids (first_vine, first_harvest, craft_first_qvevri, sell_first_bottles,
 * visit_marani) are kept intact and lead the catalog.
 *
 * <p>Objective types (string enum discriminators; in v1 objectives are
 * auto-satisfied on complete, so these act as client-facing labels):
 * PLANT_VINE / SELL_BOTTLES / CRAFT_VESSEL / HARVEST / VISIT, plus the
 * additional progression labels FERMENT / SHIP / WIN_CONTEST / BUILD /
 * RESEARCH / HIRE / TRADE / FOUND_GUILD / SPONSOR. Deeper objective tracking
 * is a future integration pass.
 *
 * <p>All reward good IDs are verified against {@link com.game.goods.GoodsCatalog}.
 * NPC giver names are consistent with the NPC roster in docs/CONTENT-BIBLE.md.
 *
 * <p>Reward design: rewards rise with phase (Glekhi 25–60 GEL → Tavadi 500–600
 * GEL), totalling ~3,800 GEL across the arc to bootstrap a peasant past the
 * first qvevri without a feel-bad wall (see CONTENT-BIBLE §3 economy balance).
 */
public final class QuestCatalog {

    private static final Map<String, QuestDefinition> BY_ID;

    static {
        Map<String, QuestDefinition> m = new LinkedHashMap<>();

        add(m, new QuestDefinition(
                "first_vine",
                "The First Vine",
                "Tamada Giorgi says every great wine starts with a single vine. "
                + "Plant your first Saperavi cutting in the red soil of Kakheti to prove "
                + "you are serious about the craft.",
                "Tamada Giorgi",
                "PLANT_VINE",
                1,
                25.0,
                "saperavi_cuttings_certified",
                3.0));

        add(m, new QuestDefinition(
                "first_harvest",
                "Harvest of the Alazan Valley",
                "The vines are heavy and the air smells of ripe Saperavi. "
                + "Nino the Viticulturist asks you to complete your first harvest "
                + "before the autumn rains arrive.",
                "Nino the Viticulturist",
                "HARVEST",
                1,
                40.0,
                "copper_sulfate",
                5.0));

        add(m, new QuestDefinition(
                "craft_first_qvevri",
                "A Vessel Worthy of the Wine",
                "The old potter Davit says no Georgian wine is complete without "
                + "a proper qvevri buried in the marani. Craft your first clay vessel "
                + "to honour the ancient tradition.",
                "Potter Davit",
                "CRAFT_VESSEL",
                1,
                60.0,
                "qvevri_300l",
                1.0));

        add(m, new QuestDefinition(
                "sell_first_bottles",
                "Find Your First Buyer",
                "Merchant Tamar at the Telavi bazaar will buy three bottles of your "
                + "Saperavi. Prove the market wants what you are making.",
                "Merchant Tamar",
                "SELL_BOTTLES",
                3,
                50.0,
                "pruning_shears",
                1.0));

        add(m, new QuestDefinition(
                "visit_marani",
                "Pilgrimage to the Old Marani",
                "Elder Ketevan says the spirits of the valley only reveal their secrets "
                + "to those who visit the ancestral marani in Tsinandali. "
                + "Travel there and pay your respects.",
                "Elder Ketevan",
                "VISIT",
                1,
                30.0,
                "bird_netting",
                2.0));

        // ═══════════════════════════════════════════════════════════════════════
        // PHASE I — GLEKHI (the Peasant). Bootstrap the player past the first
        // qvevri. Givers: Tamada Giorgi, Nino, Davit, Tamar, Ketevan.
        // ═══════════════════════════════════════════════════════════════════════

        add(m, new QuestDefinition(
                "block_of_ten",
                "A Block of Ten",
                "One vine proves nothing, Nino says. Plant ten Saperavi cuttings in a proper "
                + "row and you have the beginnings of a vineyard — and the beginnings of a "
                + "winemaker the valley might one day remember.",
                "Nino the Viticulturist",
                "PLANT_VINE",
                10,
                45.0,
                "saperavi_cuttings_own_root",
                5.0));

        add(m, new QuestDefinition(
                "guard_the_grapes",
                "Guard the Ripening Grapes",
                "The starlings have found your fruit. Tamada Giorgi laughs that a glekhi who "
                + "cannot keep the birds off his vines will never keep wine in his marani. "
                + "String the netting and protect this year's harvest.",
                "Tamada Giorgi",
                "HARVEST",
                1,
                40.0,
                "bird_netting",
                1.0));

        add(m, new QuestDefinition(
                "the_first_pressing",
                "The First Pressing",
                "Davit has lent you his old basket press for a day. Crush and press your "
                + "harvest into must — the moment grapes stop being fruit and start becoming "
                + "wine. Mind your hands; the press has no mercy.",
                "Potter Davit",
                "FERMENT",
                1,
                55.0,
                "clay_lining_compound",
                3.0));

        add(m, new QuestDefinition(
                "honest_weight",
                "An Honest Weight",
                "Merchant Tamar will buy five more bottles — but only if you weigh and price "
                + "them honestly. Sell her five bottles at a fair hand and she will open doors "
                + "no haggling ever could.",
                "Merchant Tamar",
                "SELL_BOTTLES",
                5,
                60.0,
                "hoe",
                1.0));

        add(m, new QuestDefinition(
                "blessing_of_the_vines",
                "Blessing of the Vines",
                "Elder Ketevan calls you to the spring festival, when the valley blesses the "
                + "young shoots against frost and rot. Walk to the old marani for the Vine "
                + "Blessing and begin the season as the ancestors did.",
                "Elder Ketevan",
                "VISIT",
                1,
                35.0,
                "copper_sulfate",
                4.0));

        // ═══════════════════════════════════════════════════════════════════════
        // PHASE II — SMALLHOLDER. Make your own wine; first vessel, contest,
        // research, staff. Givers: Levan, Davit, Tamar, Nino, Giorgi.
        // ═══════════════════════════════════════════════════════════════════════

        add(m, new QuestDefinition(
                "fill_the_qvevri",
                "Fill the Qvevri",
                "Davit says an empty qvevri is a sad thing and a full one a proud one. "
                + "Ferment a full vessel of your own wine on the skins, Kakhetian-style, "
                + "and let the clay do what it has done for eight thousand years.",
                "Potter Davit",
                "FERMENT",
                1,
                90.0,
                "qvevri_500l",
                1.0));

        add(m, new QuestDefinition(
                "measure_twice",
                "Measure Twice, Pour Once",
                "Levan the enologist offers to teach you the numbers behind the wine. Bring "
                + "him a young vintage to grade and learn to read Brix and acid like a man "
                + "reads weather. Science and tradition need not quarrel.",
                "Levan the Enologist",
                "RESEARCH",
                1,
                85.0,
                "refractometer",
                1.0));

        add(m, new QuestDefinition(
                "the_village_contest",
                "The Village Contest",
                "Nino dares you to enter the Telavi village contest. Put one of your bottles "
                + "before the judges and learn where you truly stand — humbling or otherwise. "
                + "A wine unjudged is a wine half-made.",
                "Nino the Viticulturist",
                "WIN_CONTEST",
                1,
                100.0,
                "sulfur_dust",
                3.0));

        add(m, new QuestDefinition(
                "hire_a_hand",
                "Hire a Hand",
                "Tamada Giorgi says no man brings in a real harvest alone. Take on a vineyard "
                + "hand for the season — wages are a weight, but two backs lift what one cannot. "
                + "This is how a smallholder becomes an estate.",
                "Tamada Giorgi",
                "HIRE",
                1,
                80.0,
                "pruning_shears",
                1.0));

        add(m, new QuestDefinition(
                "a_second_vessel",
                "A Second Vessel",
                "One qvevri makes one wine. Merchant Tamar, eyeing your growth, suggests a "
                + "steel tank for a fresher style to sit beside your amber. Acquire a second "
                + "vessel and double the kinds of wine your name can carry.",
                "Merchant Tamar",
                "CRAFT_VESSEL",
                1,
                95.0,
                "steel_tank_500l",
                1.0));

        add(m, new QuestDefinition(
                "a_proper_marani",
                "A Proper Marani",
                "Elder Ketevan reminds you that wine is made in the vineyard but kept in the "
                + "marani. Build yourself a proper cellar so your vintages can rest in the cool "
                + "dark, as every serious house keeps them.",
                "Elder Ketevan",
                "BUILD",
                1,
                110.0,
                "clay_lining_compound",
                5.0));

        // ═══════════════════════════════════════════════════════════════════════
        // PHASE III — ESTATE OWNER. A named estate; press-house, logistics,
        // regions, guild. Givers: Maro, Davitashvili, Beso, Sandro, Levan.
        // ═══════════════════════════════════════════════════════════════════════

        add(m, new QuestDefinition(
                "westward_to_imereti",
                "Westward to Imereti",
                "Maro of the Wet Hills invites you to try the harder country. Travel to Imereti, "
                + "where Tsolikouri is born and the mildew is patient, and see whether your hands "
                + "are ready for a terroir that does not forgive.",
                "Maro of the Wet Hills",
                "VISIT",
                1,
                130.0,
                "copper_sulfate",
                8.0));

        add(m, new QuestDefinition(
                "plant_the_west",
                "Plant the West",
                "Tsolikouri belongs in Imeretian soil, Maro insists, and pays you back in gold "
                + "for the trouble. Establish a block of certified Tsolikouri in the wet hills "
                + "and earn the appellation the easy regions never can.",
                "Maro of the Wet Hills",
                "PLANT_VINE",
                10,
                150.0,
                "tsolikouri_cuttings_certified",
                10.0));

        add(m, new QuestDefinition(
                "the_press_house",
                "The Press-House",
                "Sandro the négociant will only buy from a house that can press at scale. "
                + "Build a press-house on your estate — the heart of a real winery — and turn "
                + "a smallholding into a name buyers seek out.",
                "Sandro the Négociant",
                "BUILD",
                1,
                170.0,
                "basket_press",
                1.0));

        add(m, new QuestDefinition(
                "ship_to_the_capital",
                "Ship to the Capital",
                "There is no market like Tbilisi, says Sandro, and no profit without the "
                + "hauler's road. Ship a consignment of your wine across the regions to the "
                + "capital bazaar and learn that distance is a cost — and an opportunity.",
                "Sandro the Négociant",
                "SHIP",
                1,
                160.0,
                "bird_netting",
                2.0));

        add(m, new QuestDefinition(
                "the_regional_contest",
                "The Regional Contest",
                "Levan enters you in the regional contest, where estates and not villages "
                + "compete. Place among the judged and your wine's reputation will travel "
                + "further than any hauler could carry it.",
                "Levan the Enologist",
                "WIN_CONTEST",
                1,
                200.0,
                "ph_meter",
                1.0));

        add(m, new QuestDefinition(
                "brotherhood_of_the_vine",
                "Brotherhood of the Vine",
                "Davitashvili the highlander says a lone estate is a candle in the wind, but a "
                + "guild is a hearth. Found or join a guild of vintners and pool what no single "
                + "marani can hold alone.",
                "Davitashvili the Highlander",
                "FOUND_GUILD",
                1,
                180.0,
                "oak_barrel_225l",
                1.0));

        // ═══════════════════════════════════════════════════════════════════════
        // PHASE IV — WINE HOUSE. A brand at volume. Givers: Sandro, Davitashvili,
        // Beso, Eka, Tamar.
        // ═══════════════════════════════════════════════════════════════════════

        add(m, new QuestDefinition(
                "the_negociants_label",
                "The Négociant's Label",
                "Sandro proposes a deal that smells of money: sell him a bulk lot to bottle "
                + "under his label, or buy his and bottle under yours. Close a volume trade and "
                + "learn that the trick is never selling — it is buying.",
                "Sandro the Négociant",
                "TRADE",
                1,
                250.0,
                "steel_tank_2000l",
                1.0));

        add(m, new QuestDefinition(
                "the_mountain_prestige",
                "The Mountain's Prestige",
                "Davitashvili offers you a vintage of his scarce semi-sweet Aleksandrouli to "
                + "carry under your house. Win an auction for a prestige lot and put your name "
                + "beside wine the lowlands cannot make at any price.",
                "Davitashvili the Highlander",
                "WIN_CONTEST",
                1,
                300.0,
                "oak_barrel_225l",
                2.0));

        add(m, new QuestDefinition(
                "the_disease_gauntlet",
                "The Disease Gauntlet",
                "Beso of the swamp-vines wagers you cannot bring an Ojaleshi vintage through a "
                + "Samegrelo season alive. Survive the worst disease pressure in the country and "
                + "claim a rare red almost no other house can offer.",
                "Beso of the Swamp-Vines",
                "HARVEST",
                1,
                280.0,
                "sulfur_dust",
                10.0));

        add(m, new QuestDefinition(
                "the_coast_cellar_door",
                "The Coast's Cellar Door",
                "Eka of the coast says the Black Sea drinks light wine by the carafe and pays "
                + "by the carafe. Open your house to the tourist trade on the Adjaran shore and "
                + "let the visitors become your most loyal market.",
                "Eka of the Coast",
                "SPONSOR",
                1,
                260.0,
                "qvevri_1000l",
                1.0));

        // ═══════════════════════════════════════════════════════════════════════
        // PHASE V — TAVADI (the Wine Lord). Deploy wealth; patronage, legacy.
        // Givers: Vakhtang, Giorgi, Ketevan, Sandro.
        // ═══════════════════════════════════════════════════════════════════════

        add(m, new QuestDefinition(
                "the_meskheti_revival",
                "The Meskheti Revival",
                "Vakhtang the pioneer calls you to the cold high south, where the ancient "
                + "wineries are ruins and the competitors are ghosts. Bury your jars where monks "
                + "buried theirs and revive a terroir eight centuries forgotten.",
                "Vakhtang the Pioneer",
                "BUILD",
                1,
                450.0,
                "qvevri_1000l",
                1.0));

        add(m, new QuestDefinition(
                "patron_of_the_rtveli",
                "Patron of the Rtveli",
                "Tamada Giorgi, grey now and proud of you, asks the once-glekhi to do what the "
                + "great houses do: sponsor the Rtveli harvest festival for the whole valley. "
                + "Wealth in this country is spent generously, or it is wasted.",
                "Tamada Giorgi",
                "SPONSOR",
                1,
                500.0,
                "oak_barrel_225l",
                2.0));

        add(m, new QuestDefinition(
                "the_pilgrims_marani",
                "The Pilgrim's Marani",
                "Elder Ketevan, at the end of her years, names your marani worthy of pilgrimage "
                + "— a cellar strangers will walk a day to drink from, as she once promised you. "
                + "Complete the legacy of the house you built from a single vine.",
                "Elder Ketevan",
                "WIN_CONTEST",
                1,
                600.0,
                "qvevri_1000l",
                2.0));

        BY_ID = Collections.unmodifiableMap(m);
    }

    private QuestCatalog() {}

    private static void add(Map<String, QuestDefinition> m, QuestDefinition q) {
        m.put(q.getId(), q);
    }

    /**
     * Returns all quest definitions in catalog insertion order.
     *
     * @return unmodifiable collection of all {@link QuestDefinition}s
     */
    public static Collection<QuestDefinition> all() {
        return BY_ID.values();
    }

    /**
     * Looks up a quest by its stable id.
     *
     * @param id quest id string (e.g. "first_vine")
     * @return the {@link QuestDefinition}, or {@code null} if not found
     */
    public static QuestDefinition find(String id) {
        return BY_ID.get(id);
    }
}
