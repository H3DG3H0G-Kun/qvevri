package com.game.world;

import java.util.List;

/**
 * Static catalog of region and career data, populated from GDD Part 3 (regions)
 * and GDD Part 8 (careers). No database storage needed — these are design constants.
 *
 * NOTE: Only Kakheti/Saperavi is fully simulated today. Other regions are selectable
 * and stored; the vineyard sim defaults to Kakheti behaviour for them (future work).
 */
public final class WorldCatalog {

    private WorldCatalog() {}

    /** All seven Georgian wine regions from GDD Part 3. */
    public static final List<RegionInfo> REGIONS = List.of(
            new RegionInfo(
                    Region.KAKHETI,
                    "Kakheti",
                    "Warm continental; hot dry summers, cold winters; ~1 873 GDD (Winkler III)",
                    "Rkatsiteli, Saperavi",
                    "Traditional Kakhetian method: 4–6 months skin contact in qvevri; "
                            + "full extraction of tannin, colour and phenolics. "
                            + "European-style (no-skin) also practised. FULLY SIMULATED.",
                    41.92, 45.47   // Telavi, Kakheti
            ),
            new RegionInfo(
                    Region.KARTLI,
                    "Kartli",
                    "Transitional continental; cooler than Kakheti due to altitude; "
                            + "moderate rainfall with some spring frost risk",
                    "Chinuri, Goruli Mtsvane, Shavkapito",
                    "Sparkling wine heartland; fresh European style favoured; "
                            + "some qvevri use. Vineyard sim defaults to Kakheti behaviour.",
                    41.98, 44.11   // Gori, Shida Kartli
            ),
            new RegionInfo(
                    Region.IMERETI,
                    "Imereti",
                    "Temperate oceanic; cooler and wetter than Kakheti; "
                            + "higher fungal pressure; moderate GDD",
                    "Tsolikouri, Tsitska, Krakhuna",
                    "Imeretian method: 10–30 days partial skin contact (chacha included); "
                            + "gentler extraction than Kakhetian. Vineyard sim defaults to Kakheti behaviour.",
                    42.27, 42.70   // Kutaisi, Imereti
            ),
            new RegionInfo(
                    Region.RACHA_LECHKHUMI,
                    "Racha-Lechkhumi",
                    "Mountain microclimate; high altitude valleys; warm summers, "
                            + "cold winters; naturally low yields",
                    "Alexandrouli, Mujuretuli",
                    "Famous for naturally semi-sweet Khvanchkara; late harvest style; "
                            + "residual sugar from arrested fermentation. "
                            + "Vineyard sim defaults to Kakheti behaviour.",
                    42.52, 43.15   // Ambrolauri, Racha-Lechkhumi
            ),
            new RegionInfo(
                    Region.SAMEGRELO,
                    "Samegrelo",
                    "Humid subtropical; high rainfall; warm year-round; "
                            + "significant disease pressure",
                    "Ojaleshi",
                    "Rich, full-bodied reds; some qvevri use. "
                            + "Vineyard sim defaults to Kakheti behaviour.",
                    42.51, 41.87   // Zugdidi, Samegrelo
            ),
            new RegionInfo(
                    Region.GURIA_ADJARA,
                    "Guria & Adjara",
                    "Black Sea coastal; mild maritime climate; wet with high humidity; "
                            + "frost-rare",
                    "Chkhaveri, Jani",
                    "Light reds and rosés; low-alcohol style from Chkhaveri. "
                            + "Vineyard sim defaults to Kakheti behaviour.",
                    41.64, 41.64   // Batumi, Adjara (midpoint for combined Guria/Adjara region)
            ),
            new RegionInfo(
                    Region.MESKHETI,
                    "Meskheti",
                    "High-altitude southern region; cool with cold winters; "
                            + "ancient winemaking traditions recently revived",
                    "Meskhuri Mtsvane, Tavkveri",
                    "Revival of ancient qvevri traditions; elegant aromatic whites; "
                            + "Vineyard sim defaults to Kakheti behaviour.",
                    41.64, 42.98   // Akhaltsikhe, Samtskhe-Javakheti
            )
    );

    /** All nine career types from GDD Part 8. */
    public static final List<CareerInfo> CAREERS = List.of(
            new CareerInfo(
                    CareerType.GROWER,
                    "Grower",
                    "Masters the vineyard: pruning, soil management, canopy control and harvest timing. "
                            + "Unlocks advanced soil profiles, vine stress bonuses and early-pick premiums. "
                            + "Primary producer — sell grapes or use them directly."
            ),
            new CareerInfo(
                    CareerType.WINEMAKER,
                    "Winemaker",
                    "Turns grapes into wine: fermentation control, qvevri management and blending. "
                            + "Unlocks extended maceration options, skin-contact bonuses and style upgrades. "
                            + "May buy grapes from Growers or grow their own."
            ),
            new CareerInfo(
                    CareerType.ENOLOGIST,
                    "Enologist",
                    "Specialist consultant who maximises quality scores through analytical intervention: "
                            + "correcting must chemistry, managing faults and advising on timing. "
                            + "Earns consulting fees; can partner with Winemakers for shared quality uplift."
            ),
            new CareerInfo(
                    CareerType.NEGOCIANT,
                    "Négociant",
                    "Buys finished wine in bulk, blends and bottles under their own label. "
                            + "Unlocks appellation blending, private-label bonuses and volume trade contracts. "
                            + "Bridge between production and market."
            ),
            new CareerInfo(
                    CareerType.BROKER,
                    "Broker",
                    "Facilitates deals between producers and buyers for a commission. "
                            + "Unlocks market-price intelligence, exclusive listing access and arbitrage routes. "
                            + "Does not own wine — earns on volume of deals closed."
            ),
            new CareerInfo(
                    CareerType.COOPER,
                    "Cooper",
                    "Crafts and repairs qvevri, barrels and oak vessels. "
                            + "Unlocks vessel quality tiers that grant fermentation bonuses to partner Winemakers. "
                            + "Earns from vessel sales and maintenance contracts."
            ),
            new CareerInfo(
                    CareerType.NURSERYMAN,
                    "Nurseryman",
                    "Propagates certified vine cuttings from heritage Georgian varieties. "
                            + "Unlocks rootstock quality upgrades, phylloxera-resistant stock and rare cultivars. "
                            + "Sells planting material; critical gatekeeper for new vineyards."
            ),
            new CareerInfo(
                    CareerType.HAULER,
                    "Hauler",
                    "Moves grapes, must and wine between producers and markets. "
                            + "Unlocks refrigerated transport (quality preservation bonus) and bulk contracts. "
                            + "Earns per-shipment fees; can hold inventory briefly but not age wine."
            ),
            new CareerInfo(
                    CareerType.MERCHANT,
                    "Merchant",
                    "Retail and export specialist: builds relationships with restaurateurs, "
                            + "importers and collectors. Unlocks prestige channels, export licences and "
                            + "recurring buy orders. Earns on sell-through margin; highest income ceiling."
            )
    );
}
