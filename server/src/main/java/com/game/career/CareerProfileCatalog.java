package com.game.career;

import com.game.exception.ApiException;
import com.game.world.CareerType;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Static catalog mapping every {@link CareerType} to its {@link CareerProfile}.
 *
 * <p>Data sourced from docs/CONTENT-BIBLE.md §4 "CAREER IDENTITIES".
 * All 9 careers are represented; each profile sets only the multiplier(s)
 * that correspond to its documented PRO (all others default to 0.0).
 *
 * <p>Multipliers chosen where the CONTENT-BIBLE gives an explicit number;
 * where the Bible names a mechanic without a number, a sensible balanced
 * value was chosen (annotated below):
 * <ul>
 *   <li>Cooper craftDiscountMult = 0.20 — inferred: "crafts vessels below bazaar price",
 *       symmetric with Négociant's 0.20 buy discount</li>
 *   <li>Broker brokerCommissionMult = 0.05 — inferred: modest 5% fee aligns with the
 *       game's existing 5% market fee, so a broker earns a matching take</li>
 *   <li>Nurseryman cuttingMarginMult = 0.25 — inferred: "sells certified cuttings at margin",
 *       25% gives a distinct niche without crowding out vineyard careers</li>
 *   <li>Enologist gradeFeeIncomeMult = 0.08 — inferred: "consulting fees from other players",
 *       8% of graded-wine value rewards expertise without overwhelming own production</li>
 * </ul>
 */
public final class CareerProfileCatalog {

    private static final Map<CareerType, CareerProfile> CATALOG;

    static {
        Map<CareerType, CareerProfile> m = new EnumMap<>(CareerType.class);

        // ── GROWER ─────────────────────────────────────────────────────────────
        // PRO:  +15% yield on owned vineyards (CONTENT-BIBLE exact).
        // CON:  cannot ferment to top quality; weak sell margin.
        m.put(CareerType.GROWER, CareerProfile.forCareer(
                "GROWER",
                "Hands in the soil — reads the vine like scripture.",
                "+15% yield on owned vineyards; early-pick premium; cheaper vine stock.",
                "Cannot ferment to top quality — must sell grapes or partner a Winemaker; weak sell margin."
        ).yieldMult(0.15).build());

        // ── WINEMAKER ──────────────────────────────────────────────────────────
        // PRO:  +10% wine quality from fermentation control (CONTENT-BIBLE exact).
        // CON:  no yield bonus; weak at retail margin.
        m.put(CareerType.WINEMAKER, CareerProfile.forCareer(
                "WINEMAKER",
                "Turns fruit into wine — master of the qvevri.",
                "+10% wine quality from fermentation control; skin-contact and blend bonuses.",
                "No yield bonus (buys grapes dearer or grows fewer); weak at retail margin."
        ).qualityMult(0.10).build());

        // ── ENOLOGIST ──────────────────────────────────────────────────────────
        // PRO:  consulting-fee income from grading + quality-grade premium on own wine.
        //       gradeFeeIncomeMult = 0.08 (inferred; see class javadoc).
        // CON:  tiny own-production scale; income depends on client base.
        m.put(CareerType.ENOLOGIST, CareerProfile.forCareer(
                "ENOLOGIST",
                "The analytical consultant — saves faulted vintages.",
                "Earns consulting fees from other players + quality-grade premium on own wine.",
                "Tiny own-production scale; income depends on a client base and partners."
        ).gradeFeeIncomeMult(0.08).build());

        // ── NEGOCIANT ──────────────────────────────────────────────────────────
        // PRO:  buys finished wine ~20% below market (CONTENT-BIBLE exact).
        //       private-label resale bonus; volume contracts.
        // CON:  cannot produce; pure middleman, exposed to supply.
        m.put(CareerType.NEGOCIANT, CareerProfile.forCareer(
                "NEGOCIANT",
                "Buys bulk cheap, bottles under their own label.",
                "Buys finished wine ~20% below market; private-label resale bonus; volume contracts.",
                "Cannot produce (no vineyard/cellar quality of own) — pure middleman, exposed to supply."
        ).buyDiscountMult(0.20).build());

        // ── BROKER ─────────────────────────────────────────────────────────────
        // PRO:  earns commission on every deal brokered + market-price intelligence.
        //       brokerCommissionMult = 0.05 (inferred; see class javadoc).
        // CON:  owns no inventory; zero income in a dead market.
        m.put(CareerType.BROKER, CareerProfile.forCareer(
                "BROKER",
                "Never owns the wine — owns the deal.",
                "Earns commission on every deal brokered + market-price intelligence (sees true prices).",
                "Owns no inventory — zero income in a dead market; cannot capture asset appreciation."
        ).brokerCommissionMult(0.05).build());

        // ── COOPER ─────────────────────────────────────────────────────────────
        // PRO:  crafts vessels below bazaar price + sells/maintains them.
        //       craftDiscountMult = 0.20 (inferred; see class javadoc).
        // CON:  not a wine producer; income capped by vessel demand.
        m.put(CareerType.COOPER, CareerProfile.forCareer(
                "COOPER",
                "Shapes the clay and the oak — the vessel maker.",
                "Crafts vessels below bazaar price + sells/maintains them; partner fermentation bonus.",
                "Not a wine producer — income capped by vessel demand; weak at grape/wine markets."
        ).craftDiscountMult(0.20).build());

        // ── NURSERYMAN ─────────────────────────────────────────────────────────
        // PRO:  sells certified cuttings at margin; gatekeeper for new vineyards.
        //       cuttingMarginMult = 0.25 (inferred; see class javadoc).
        // CON:  no wine income at all; feast in a planting boom, famine in a mature market.
        m.put(CareerType.NURSERYMAN, CareerProfile.forCareer(
                "NURSERYMAN",
                "Propagates the heritage cuttings every vineyard needs.",
                "Sells certified cuttings at margin; gatekeeper for new vineyards; rare-cultivar premium.",
                "No wine income at all — feast in a planting boom, famine in a mature market."
        ).cuttingMarginMult(0.25).build());

        // ── HAULER ─────────────────────────────────────────────────────────────
        // PRO:  -30% shipping cost on own goods (CONTENT-BIBLE exact).
        //       paid per-shipment for others; refrigerated quality-preservation.
        // CON:  weak at sales/quality; holds inventory but cannot age wine; idle without freight.
        m.put(CareerType.HAULER, CareerProfile.forCareer(
                "HAULER",
                "Moves grape, must, and wine across the valleys.",
                "-30% shipping cost on own goods + paid per-shipment for others; refrigerated quality-preservation.",
                "Weak at sales/quality; holds inventory but cannot age wine; idle without freight."
        ).shippingDiscountMult(0.30).build());

        // ── MERCHANT ───────────────────────────────────────────────────────────
        // PRO:  +20% sell margin (CONTENT-BIBLE exact).
        //       recurring buy-orders; export channel; highest income ceiling.
        // CON:  cannot improve wine quality (no production edge); buys at market, lives on margin.
        m.put(CareerType.MERCHANT, CareerProfile.forCareer(
                "MERCHANT",
                "Relationships with restaurateurs, importers, collectors.",
                "+20% sell margin + recurring buy-orders + export channel; highest income ceiling.",
                "Cannot improve wine quality (no production edge) — buys at market, lives or dies on margin."
        ).sellMarginMult(0.20).build());

        CATALOG = Collections.unmodifiableMap(m);
    }

    private CareerProfileCatalog() {}

    /**
     * Returns all 9 {@link CareerProfile}s as an unmodifiable collection.
     */
    public static Collection<CareerProfile> all() {
        return CATALOG.values();
    }

    /**
     * Returns the {@link CareerProfile} for the given {@link CareerType}.
     *
     * @throws ApiException 400 if the careerType is not in the catalog
     *         (should never happen for the 9 known values)
     */
    public static CareerProfile of(CareerType careerType) {
        CareerProfile p = CATALOG.get(careerType);
        if (p == null) {
            throw ApiException.badRequest("No career profile defined for: " + careerType);
        }
        return p;
    }

    /**
     * String overload — resolves the name and delegates to {@link #of(CareerType)}.
     *
     * @throws ApiException 400 if the name is not a valid CareerType
     */
    public static CareerProfile of(String careerTypeName) {
        try {
            return of(CareerType.valueOf(careerTypeName.toUpperCase()));
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Unknown career type: " + careerTypeName);
        }
    }
}
