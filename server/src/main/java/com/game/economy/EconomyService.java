package com.game.economy;

import com.game.econ.ItemType;
import com.game.econ.WinePricer;
import com.game.exception.ApiException;
import com.game.market.CellarItemRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Pricing engine for the LANE ECONOMY feature.
 *
 * <h2>Formula</h2>
 * <pre>
 *   basePrice      = WinePricer base rate for the ItemType (GEL / natural unit)
 *   supply         = CellarItemRepository.countByItemTypeAndEscrowedFalse(itemType)
 *   supplyFactor   = SUPPLY_SCALE / (SUPPLY_SCALE + supply)
 *                    → equals 1.0 at supply=0; approaches 0 as supply → ∞
 *                    → at supply=SUPPLY_SCALE (50) factor = 0.5
 *   regionalFactor = per-region static multiplier (see REGIONAL_FACTORS table below)
 *   grossPrice     = basePrice × supplyFactor × regionalFactor
 *   fee            = grossPrice × FEE_RATE  (5 %)
 *   netPrice       = grossPrice − fee
 * </pre>
 *
 * <h2>Base prices (from WinePricer — READ-ONLY)</h2>
 * <pre>
 *   GRAPES        = 0.50 GEL/kg
 *   MUST          = 1.20 GEL/L
 *   YOUNG_WINE    = 3.00 GEL/L
 *   AGED_WINE     = 6.00 GEL/L
 *   CHACHA_BRANDY = 8.00 GEL/L
 *   WINE          = 6.00 GEL/L  (alias of AGED_WINE)
 * </pre>
 *
 * <h2>Regional demand multipliers</h2>
 * <pre>
 *   KAKHETI         = 1.00  (baseline; Alazani Valley — production heartland)
 *   KARTLI          = 1.05  (capital region; slight demand premium)
 *   IMERETI         = 0.95  (local market; moderate demand)
 *   RACHA_LECHKHUMI = 1.10  (small high-altitude market; scarcity premium)
 *   SAMEGRELO       = 0.90  (high rainfall; production surplus lowers demand)
 *   GURIA_ADJARA    = 1.08  (subtropical tourist coast; premium demand)
 *   MESKHETI        = 1.12  (remote highland terraces; highest scarcity premium)
 * </pre>
 *
 * <h2>Worked example — WINE in MESKHETI with 10 active listings</h2>
 * <pre>
 *   basePrice      = 6.00
 *   supplyFactor   = 50 / (50 + 10)     = 50/60 ≈ 0.8333
 *   regionalFactor = 1.12
 *   grossPrice     = 6.00 × 0.8333 × 1.12 ≈ 5.60
 *   fee            = 5.60 × 0.05         ≈ 0.28
 *   netPrice       = 5.60 − 0.28         ≈ 5.32
 * </pre>
 */
@Service
@Transactional(readOnly = true)
public class EconomyService {

    // ── Constants ─────────────────────────────────────────────────────────────

    /**
     * Scale denominator for the supply factor.
     * At supply = SUPPLY_SCALE the factor equals exactly 0.5.
     * At supply = 0 the factor equals exactly 1.0.
     */
    public static final double SUPPLY_SCALE = 50.0;

    /**
     * Proportion of grossPrice deducted as a sale fee (money sink).
     * 5 % = 0.05.
     */
    public static final double FEE_RATE = 0.05;

    /**
     * Main wine ItemType used for the price index endpoint.
     */
    public static final String INDEX_ITEM_TYPE = "WINE";

    /**
     * Static regional demand multipliers.
     *
     * <p>These encode relative market demand per region:
     * <ul>
     *   <li>KAKHETI         1.00 — production heartland; neutral demand</li>
     *   <li>KARTLI          1.05 — capital region; slight demand premium</li>
     *   <li>IMERETI         0.95 — local market; moderate below-baseline demand</li>
     *   <li>RACHA_LECHKHUMI 1.10 — small high-altitude market; scarcity premium</li>
     *   <li>SAMEGRELO       0.90 — subtropical surplus; lowers demand</li>
     *   <li>GURIA_ADJARA    1.08 — tourist coastal market; premium demand</li>
     *   <li>MESKHETI        1.12 — remote highland terraces; highest scarcity premium</li>
     * </ul>
     */
    public static final Map<String, Double> REGIONAL_FACTORS = Map.of(
            "KAKHETI",         1.00,
            "KARTLI",          1.05,
            "IMERETI",         0.95,
            "RACHA_LECHKHUMI", 1.10,
            "SAMEGRELO",       0.90,
            "GURIA_ADJARA",    1.08,
            "MESKHETI",        1.12
    );

    // ── Dependencies ──────────────────────────────────────────────────────────

    private final CellarItemRepository cellarItemRepository;
    private final PriceSnapshotRepository snapshotRepository;

    public EconomyService(CellarItemRepository cellarItemRepository,
                          PriceSnapshotRepository snapshotRepository) {
        this.cellarItemRepository = cellarItemRepository;
        this.snapshotRepository   = snapshotRepository;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Compute a full price quote for the given item type and region.
     *
     * @param itemTypeStr  the {@link ItemType} name (case-sensitive)
     * @param regionStr    the Region enum name (case-sensitive)
     * @return             a complete {@link PriceQuote}
     * @throws ApiException 400 BAD_REQUEST if itemTypeStr or regionStr is unknown
     */
    public PriceQuote quote(String itemTypeStr, String regionStr) {
        double basePrice      = resolveBasePrice(itemTypeStr);
        double regionalFactor = resolveRegionalFactor(regionStr);

        long   supply         = cellarItemRepository.countByItemTypeAndEscrowedFalse(itemTypeStr);
        double supplyFactor   = SUPPLY_SCALE / (SUPPLY_SCALE + supply);

        double grossPrice     = basePrice * supplyFactor * regionalFactor;
        double fee            = grossPrice * FEE_RATE;
        double netPrice       = grossPrice - fee;

        return new PriceQuote(basePrice, supplyFactor, regionalFactor,
                              grossPrice, fee, netPrice, supply);
    }

    /**
     * Returns a per-region price index for the main wine item type (WINE).
     * Each entry contains the region name and the grossPrice.
     */
    public List<RegionIndex> index() {
        List<RegionIndex> result = new ArrayList<>();
        for (String region : REGIONAL_FACTORS.keySet()) {
            double gross = quote(INDEX_ITEM_TYPE, region).getGrossPrice();
            result.add(new RegionIndex(region, gross));
        }
        // Sort by region name for deterministic ordering
        result.sort((a, b) -> a.getRegion().compareTo(b.getRegion()));
        return result;
    }

    /**
     * Persists a PriceSnapshot for the given item type and region and returns it.
     *
     * @param itemTypeStr  the {@link ItemType} name
     * @param regionStr    the Region enum name
     * @return the saved {@link PriceSnapshot}
     * @throws ApiException 400 BAD_REQUEST if itemTypeStr or regionStr is unknown
     */
    @Transactional
    public PriceSnapshot snapshot(String itemTypeStr, String regionStr) {
        PriceQuote q       = quote(itemTypeStr, regionStr);
        long simDay        = System.currentTimeMillis(); // wall-clock ms; WorldClockService not wired yet
        PriceSnapshot snap = new PriceSnapshot(itemTypeStr, regionStr,
                                               q.getGrossPrice(), q.getSupplyCount(), simDay);
        return snapshotRepository.save(snap);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Resolves the base price for the given ItemType name from WinePricer constants.
     * WinePricer is not called directly (it requires Item/MarketContext objects) —
     * we read its public static base-rate constants instead (read-only, no modification).
     *
     * @throws ApiException 400 if the itemTypeStr is not a known ItemType
     */
    static double resolveBasePrice(String itemTypeStr) {
        ItemType type;
        try {
            type = ItemType.valueOf(itemTypeStr);
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Unknown itemType: " + itemTypeStr
                    + ". Valid values: GRAPES, MUST, YOUNG_WINE, AGED_WINE, CHACHA_BRANDY, WINE");
        }
        return switch (type) {
            case GRAPES        -> WinePricer.BASE_GRAPES;
            case MUST          -> WinePricer.BASE_MUST;
            case YOUNG_WINE    -> WinePricer.BASE_YOUNG_WINE;
            case AGED_WINE     -> WinePricer.BASE_AGED_WINE;
            case CHACHA_BRANDY -> WinePricer.BASE_CHACHA_BRANDY;
            case WINE          -> WinePricer.BASE_WINE;
        };
    }

    /**
     * Resolves the regional demand factor for the given region name.
     *
     * @throws ApiException 400 if the regionStr is not in the REGIONAL_FACTORS table
     */
    static double resolveRegionalFactor(String regionStr) {
        Double factor = REGIONAL_FACTORS.get(regionStr);
        if (factor == null) {
            throw ApiException.badRequest("Unknown region: " + regionStr
                    + ". Valid values: " + REGIONAL_FACTORS.keySet());
        }
        return factor;
    }
}
