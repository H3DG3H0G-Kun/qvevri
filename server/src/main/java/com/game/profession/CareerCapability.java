package com.game.profession;

import com.game.world.CareerType;

import java.util.List;
import java.util.Map;

/**
 * Static catalog mapping each {@link CareerType} to its allowed profession actions
 * and one-time starter kit (goods + optional GEL bonus).
 *
 * <p>This is a pure data/value object; the {@link ProfessionService} enforces
 * idempotency when granting starter kits.
 */
public final class CareerCapability {

    // ── Inner types ───────────────────────────────────────────────────────────

    /** A (goodTypeId, quantity) pair for starter-kit goods. */
    public record KitItem(String goodTypeId, double qty) {}

    /** Full capability descriptor for one career. */
    public record Capability(
            CareerType career,
            List<String> allowedActions,
            List<KitItem> starterGoods,
            double starterGel
    ) {}

    // ── Static catalog ────────────────────────────────────────────────────────

    /**
     * The full career-capability map.
     *
     * <p>Good-type IDs must match {@link com.game.goods.GoodsCatalog} (Lane G).
     * The following IDs are used, all verified against the Lane G catalog:
     * <ul>
     *   <li>pruning_shears, hoe, copper_sulfate, sulfur_dust, cover_crop_seed, bird_netting</li>
     *   <li>saperavi_cuttings_certified, rkatsiteli_cuttings_own_root</li>
     *   <li>qvevri_300l, steel_tank_500l, basket_press, clay_lining_compound</li>
     * </ul>
     */
    private static final Map<CareerType, Capability> CATALOG = Map.of(
        CareerType.GROWER, new Capability(
            CareerType.GROWER,
            List.of("GROW_VINEYARD", "APPLY_ACTION", "CLAIM_KIT"),
            List.of(
                new KitItem("pruning_shears", 1),
                new KitItem("hoe", 1),
                new KitItem("copper_sulfate", 2),
                new KitItem("cover_crop_seed", 1)
            ),
            20.0
        ),
        CareerType.WINEMAKER, new Capability(
            CareerType.WINEMAKER,
            List.of("FERMENT", "BLEND", "BOTTLE", "CLAIM_KIT"),
            List.of(
                new KitItem("steel_tank_500l", 1),
                new KitItem("sulfur_dust", 2)
            ),
            25.0
        ),
        CareerType.ENOLOGIST, new Capability(
            CareerType.ENOLOGIST,
            List.of("LAB_GRADE", "CERTIFY", "CLAIM_KIT"),
            List.of(
                new KitItem("sulfur_dust", 1),
                new KitItem("refractometer", 1)
            ),
            30.0
        ),
        CareerType.NEGOCIANT, new Capability(
            CareerType.NEGOCIANT,
            List.of("BULK_TRADE", "BROKER_LOT", "CLAIM_KIT"),
            List.of(),
            50.0
        ),
        CareerType.BROKER, new Capability(
            CareerType.BROKER,
            List.of("MEDIATE_TRADE", "LIST_MARKET", "CLAIM_KIT"),
            List.of(),
            40.0
        ),
        CareerType.COOPER, new Capability(
            CareerType.COOPER,
            List.of("CRAFT_VESSEL", "REPAIR_VESSEL", "CLAIM_KIT"),
            List.of(
                new KitItem("clay_lining_compound", 5),
                new KitItem("hoe", 1)
            ),
            15.0
        ),
        CareerType.NURSERYMAN, new Capability(
            CareerType.NURSERYMAN,
            List.of("PROPAGATE_VINES", "SELL_CUTTINGS", "CLAIM_KIT"),
            List.of(
                new KitItem("saperavi_cuttings_certified", 5),
                new KitItem("rkatsiteli_cuttings_own_root", 5),
                new KitItem("pruning_shears", 1)
            ),
            10.0
        ),
        CareerType.HAULER, new Capability(
            CareerType.HAULER,
            List.of("TRANSPORT_GOODS", "CLAIM_KIT"),
            List.of(
                new KitItem("bird_netting", 1)
            ),
            35.0
        ),
        CareerType.MERCHANT, new Capability(
            CareerType.MERCHANT,
            List.of("RETAIL_WINE", "EXPORT_WINE", "CLAIM_KIT"),
            List.of(),
            60.0
        )
    );

    private CareerCapability() {}

    /** Returns the full capability map (unmodifiable). */
    public static Map<CareerType, Capability> catalog() {
        return CATALOG;
    }

    /** Returns the capability for the given career, or throws if unknown. */
    public static Capability of(CareerType career) {
        Capability c = CATALOG.get(career);
        if (c == null) {
            throw new IllegalArgumentException("No capability defined for career: " + career);
        }
        return c;
    }
}
