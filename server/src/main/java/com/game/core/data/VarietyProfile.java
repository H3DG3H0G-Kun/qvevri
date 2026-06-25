package com.game.core.data;

import java.util.List;

/**
 * Per-variety phenology and ripening knobs consumed by {@code KakhetiVineSimulator}.
 *
 * <p>The SAPERAVI entry carries <em>exactly</em> the constants that were previously
 * hardcoded in {@code KakhetiVineSimulator}, guaranteeing byte-identical simulation
 * output for the KAKHETI + SAPERAVI path (REGIONS-SPEC hard constraint).
 *
 * <h2>Field semantics</h2>
 * All GDD thresholds count from budbreak (same base-10 °C convention as the
 * original hardcoded constants).  Deltas for non-Saperavi varieties are expressed
 * relative to the Saperavi baseline where applicable.
 *
 * @param gddToFlowering         GDD from budbreak to start of FLOWERING
 * @param gddToFruitSet          GDD from budbreak to FRUIT_SET (yield locked)
 * @param gddToBerryDev          GDD from budbreak to start of BERRY_DEVELOPMENT
 * @param gddToVeraison          GDD from budbreak to VERAISON (ripening clocks start)
 * @param kgPerBud               baseline kg per bud at balanced load and perfect health
 * @param brixAtVeraison         Brix at the moment of véraison
 * @param brixMax                asymptotic maximum Brix
 * @param brixK                  Brix saturation rate constant (per post-véraison GDD)
 * @param taAtVeraison           titratable acidity at véraison (g/L)
 * @param taFloor                asymptotic minimum TA (g/L)
 * @param taK                    TA decline rate constant (per post-véraison GDD)
 * @param phAtVeraison           pH at véraison
 * @param phRisePerGdd           pH rise per GDD post-véraison
 * @param tanninPerGdd           tannin ripeness gained per GDD post-véraison (0 for whites)
 * @param budLoadBalanced        sweet-spot bud load for this variety (buds/vine)
 * @param white                  true if this is a white (or rosé) variety
 * @param signatureAromas        ordered list of aroma descriptor keys for this variety
 */
public record VarietyProfile(
        double gddToFlowering,
        double gddToFruitSet,
        double gddToBerryDev,
        double gddToVeraison,
        double kgPerBud,
        double brixAtVeraison,
        double brixMax,
        double brixK,
        double taAtVeraison,
        double taFloor,
        double taK,
        double phAtVeraison,
        double phRisePerGdd,
        double tanninPerGdd,
        int    budLoadBalanced,
        boolean white,
        List<String> signatureAromas
) {}
