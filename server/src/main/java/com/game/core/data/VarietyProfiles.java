package com.game.core.data;

import java.util.List;

/**
 * Static lookup table: {@link Variety} → {@link VarietyProfile}.
 *
 * <p><strong>SAPERAVI carries the <em>exact</em> constant values previously
 * hardcoded in {@code KakhetiVineSimulator}.</strong>  No rounding, no delta —
 * the literal {@code double} literals below are copied verbatim from that class.
 * This guarantees that the KAKHETI + SAPERAVI sim path produces byte-identical
 * output before and after the refactor (REGIONS-SPEC hard constraint).
 *
 * <h2>Other variety calibration (relative to Saperavi)</h2>
 * <ul>
 *   <li>RKATSITELI: later ripening (+50 GDD to véraison), very high acid,
 *       no tannin, white style.</li>
 *   <li>MTSVANE: aromatic, moderate acid, floral aromas, white.</li>
 *   <li>KISI: aromatic, moderate-high acid, stone-fruit + floral, white.</li>
 *   <li>TSOLIKOURI: late + high acid (Imeretian classic), white.</li>
 *   <li>TSITSKA: very late, highest acid, fine-bubble potential, white.</li>
 *   <li>CHINURI: light, high-acid Kartlian white, early-ish.</li>
 *   <li>ALEKSANDROULI: thin-skinned Racha red; earlier, semi-sweet-friendly
 *       (lower Brix ceiling, lower tannin than Saperavi).</li>
 *   <li>OJALESHI: aromatic Samegrelo red; moderate tannin, high residual-sugar
 *       potential.</li>
 *   <li>CHKHAVERI: rosé/light-red Guria; lower tannin, white-flag for style.</li>
 * </ul>
 */
public final class VarietyProfiles {

    private VarietyProfiles() {}

    // =========================================================================
    // RED VARIETIES
    // =========================================================================

    /**
     * SAPERAVI — values copied verbatim from {@code KakhetiVineSimulator} constants.
     * Changing any literal here will break the 122 existing tests.
     */
    private static final VarietyProfile SAPERAVI = new VarietyProfile(
            200.0,                      // gddToFlowering    (KakhetiVineSimulator.GDD_TO_FLOWERING)
            350.0,                      // gddToFruitSet     (KakhetiVineSimulator.GDD_TO_FRUIT_SET)
            550.0,                      // gddToBerryDev     (KakhetiVineSimulator.GDD_TO_BERRY_DEV)
            900.0,                      // gddToVeraison     (KakhetiVineSimulator.GDD_TO_VERAISON)
            0.30,                       // kgPerBud          (KakhetiVineSimulator.KG_PER_BUD)
            8.0,                        // brixAtVeraison    (KakhetiVineSimulator.BRIX_AT_VERAISON)
            26.0,                       // brixMax           (KakhetiVineSimulator.BRIX_MAX)
            0.00564,                    // brixK             (KakhetiVineSimulator.BRIX_K)
            14.0,                       // taAtVeraison      (KakhetiVineSimulator.TA_AT_VERAISON)
            4.5,                        // taFloor           (KakhetiVineSimulator.TA_FLOOR)
            0.00445,                    // taK               (KakhetiVineSimulator.TA_K)
            2.90,                       // phAtVeraison      (KakhetiVineSimulator.PH_AT_VERAISON)
            0.002,                      // phRisePerGdd      (KakhetiVineSimulator.PH_RISE_PER_GDD)
            0.0018,                     // tanninPerGdd      (KakhetiVineSimulator.TANNIN_PER_GDD)
            12,                         // budLoadBalanced   (KakhetiVineSimulator.BUD_LOAD_BALANCED)
            false,                      // white
            List.of("dark-fruit", "spice", "acid")  // signatureAromas
    );

    /**
     * ALEKSANDROULI — thin-skinned Racha red; earlier, semi-sweet-friendly.
     * Lower Brix ceiling, lower tannin, slightly earlier phenology.
     */
    private static final VarietyProfile ALEKSANDROULI = new VarietyProfile(
            185.0,   // gddToFlowering — slightly earlier
            330.0,   // gddToFruitSet
            510.0,   // gddToBerryDev
            820.0,   // gddToVeraison — earlier than Saperavi
            0.28,    // kgPerBud
            7.5,     // brixAtVeraison
            24.0,    // brixMax — lower ceiling (semi-sweet style)
            0.00580, // brixK — slightly faster approach
            12.5,    // taAtVeraison
            4.8,     // taFloor — retains a bit more acid
            0.00420, // taK
            2.95,    // phAtVeraison
            0.0019,  // phRisePerGdd
            0.0012,  // tanninPerGdd — lower tannin (thin-skinned)
            10,      // budLoadBalanced
            false,   // white
            List.of("red-fruit", "spice", "acid")
    );

    /**
     * OJALESHI — aromatic Samegrelo red; moderate tannin, floral, high sugar potential.
     */
    private static final VarietyProfile OJALESHI = new VarietyProfile(
            195.0,   // gddToFlowering
            345.0,   // gddToFruitSet
            540.0,   // gddToBerryDev
            880.0,   // gddToVeraison
            0.32,    // kgPerBud — generous yield
            8.5,     // brixAtVeraison
            27.0,    // brixMax — high sugar
            0.00555, // brixK
            13.0,    // taAtVeraison
            4.2,     // taFloor — a bit lower acid floor
            0.00460, // taK
            2.88,    // phAtVeraison
            0.0021,  // phRisePerGdd
            0.0014,  // tanninPerGdd — moderate tannin
            12,      // budLoadBalanced
            false,   // white
            List.of("dark-fruit", "floral", "spice")
    );

    /**
     * CHKHAVERI — rosé/light-red Guria variety; low tannin, white-classified
     * for default style routing (light skin contact or direct press).
     */
    private static final VarietyProfile CHKHAVERI = new VarietyProfile(
            190.0,   // gddToFlowering
            335.0,   // gddToFruitSet
            525.0,   // gddToBerryDev
            850.0,   // gddToVeraison
            0.27,    // kgPerBud
            7.8,     // brixAtVeraison
            23.5,    // brixMax — lower ceiling for rosé style
            0.00575, // brixK
            12.0,    // taAtVeraison
            4.6,     // taFloor
            0.00435, // taK
            2.92,    // phAtVeraison
            0.0018,  // phRisePerGdd
            0.0005,  // tanninPerGdd — very low tannin (rosé extraction)
            10,      // budLoadBalanced
            true,    // white (rosé-path in sim)
            List.of("red-fruit", "floral", "acid")
    );

    // =========================================================================
    // WHITE VARIETIES
    // =========================================================================

    /**
     * RKATSITELI — workhorse Georgian white; late ripening, very high acid.
     * Higher GDD requirements, high TA, lower tannin (zero).
     */
    private static final VarietyProfile RKATSITELI = new VarietyProfile(
            205.0,   // gddToFlowering
            360.0,   // gddToFruitSet
            565.0,   // gddToBerryDev
            950.0,   // gddToVeraison — late variety
            0.32,    // kgPerBud — generous yield
            7.5,     // brixAtVeraison
            24.5,    // brixMax
            0.00520, // brixK — slower approach (high acid whites ripen slowly)
            15.0,    // taAtVeraison — very high acid
            5.5,     // taFloor — retains a lot of acid
            0.00400, // taK — TA declines slowly
            2.85,    // phAtVeraison
            0.0018,  // phRisePerGdd
            0.0002,  // tanninPerGdd — minimal tannin for white
            12,      // budLoadBalanced
            true,    // white
            List.of("citrus", "green-apple", "acid")
    );

    /**
     * MTSVANE — aromatic white; moderate acid, floral/citrus character.
     */
    private static final VarietyProfile MTSVANE = new VarietyProfile(
            195.0,   // gddToFlowering
            345.0,   // gddToFruitSet
            540.0,   // gddToBerryDev
            890.0,   // gddToVeraison
            0.29,    // kgPerBud
            8.0,     // brixAtVeraison
            25.0,    // brixMax
            0.00545, // brixK
            13.5,    // taAtVeraison
            4.8,     // taFloor
            0.00430, // taK
            2.88,    // phAtVeraison
            0.0019,  // phRisePerGdd
            0.0002,  // tanninPerGdd
            11,      // budLoadBalanced
            true,    // white
            List.of("citrus", "floral", "acid")
    );

    /**
     * KISI — aromatic white; moderate-high acid, stone-fruit and floral.
     */
    private static final VarietyProfile KISI = new VarietyProfile(
            193.0,   // gddToFlowering
            342.0,   // gddToFruitSet
            535.0,   // gddToBerryDev
            875.0,   // gddToVeraison
            0.28,    // kgPerBud
            8.2,     // brixAtVeraison
            25.5,    // brixMax
            0.00550, // brixK
            13.8,    // taAtVeraison
            5.0,     // taFloor
            0.00425, // taK
            2.87,    // phAtVeraison
            0.0019,  // phRisePerGdd
            0.0002,  // tanninPerGdd
            11,      // budLoadBalanced
            true,    // white
            List.of("stone-fruit", "floral", "acid")
    );

    /**
     * TSOLIKOURI — late, high-acid Imeretian white; needs good GDD accumulation.
     */
    private static final VarietyProfile TSOLIKOURI = new VarietyProfile(
            210.0,   // gddToFlowering — late
            365.0,   // gddToFruitSet
            575.0,   // gddToBerryDev
            960.0,   // gddToVeraison — very late
            0.30,    // kgPerBud
            7.2,     // brixAtVeraison — starts with lower sugar
            24.0,    // brixMax — moderate ceiling
            0.00505, // brixK
            15.5,    // taAtVeraison — highest TA
            5.8,     // taFloor — retains very high acid
            0.00390, // taK — very slow TA decline
            2.82,    // phAtVeraison
            0.0017,  // phRisePerGdd
            0.0002,  // tanninPerGdd
            12,      // budLoadBalanced
            true,    // white
            List.of("citrus", "green-apple", "acid")
    );

    /**
     * TSITSKA — very late, highest acid potential, natural fine-bubble base.
     */
    private static final VarietyProfile TSITSKA = new VarietyProfile(
            215.0,   // gddToFlowering — very late
            375.0,   // gddToFruitSet
            585.0,   // gddToBerryDev
            980.0,   // gddToVeraison — latest of all
            0.28,    // kgPerBud
            7.0,     // brixAtVeraison
            23.5,    // brixMax
            0.00495, // brixK
            16.0,    // taAtVeraison — extreme acid
            6.0,     // taFloor
            0.00380, // taK
            2.80,    // phAtVeraison
            0.0016,  // phRisePerGdd
            0.0002,  // tanninPerGdd
            12,      // budLoadBalanced
            true,    // white
            List.of("citrus", "mineral", "acid")
    );

    /**
     * CHINURI — light, high-acid Kartlian white; earlier than Rkatsiteli.
     */
    private static final VarietyProfile CHINURI = new VarietyProfile(
            198.0,   // gddToFlowering
            348.0,   // gddToFruitSet
            548.0,   // gddToBerryDev
            910.0,   // gddToVeraison — moderate-late
            0.30,    // kgPerBud
            7.8,     // brixAtVeraison
            24.0,    // brixMax
            0.00530, // brixK
            14.5,    // taAtVeraison
            5.2,     // taFloor
            0.00415, // taK
            2.86,    // phAtVeraison
            0.0018,  // phRisePerGdd
            0.0002,  // tanninPerGdd
            11,      // budLoadBalanced
            true,    // white
            List.of("citrus", "green-apple", "acid")
    );

    // =========================================================================
    // Fallback
    // =========================================================================

    /**
     * Generic fallback profile used for any variety not yet explicitly tuned.
     * Based on Saperavi mid-values with no-tannin (white-safe defaults).
     * Prevents NPE / 400s for future varieties added to the enum before calibration.
     */
    private static final VarietyProfile FALLBACK = new VarietyProfile(
            200.0, 350.0, 550.0, 900.0,
            0.30,
            8.0, 25.0, 0.00540,
            13.0, 5.0, 0.00430,
            2.90, 0.0019,
            0.0010,   // mild tannin — safe for both colours
            12,
            false,
            List.of("fruit", "acid")
    );

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Returns the {@link VarietyProfile} for the given variety.
     * Never returns {@code null}; unrecognised varieties fall back to
     * {@link #FALLBACK} so no code paths NPE.
     *
     * @param variety the grape variety (may be null — returns FALLBACK)
     * @return immutable profile record
     */
    public static VarietyProfile of(Variety variety) {
        if (variety == null) return FALLBACK;
        return switch (variety) {
            case SAPERAVI      -> SAPERAVI;
            case ALEKSANDROULI -> ALEKSANDROULI;
            case OJALESHI      -> OJALESHI;
            case CHKHAVERI     -> CHKHAVERI;
            case RKATSITELI    -> RKATSITELI;
            case MTSVANE       -> MTSVANE;
            case KISI          -> KISI;
            case TSOLIKOURI    -> TSOLIKOURI;
            case TSITSKA       -> TSITSKA;
            case CHINURI       -> CHINURI;
        };
    }
}
