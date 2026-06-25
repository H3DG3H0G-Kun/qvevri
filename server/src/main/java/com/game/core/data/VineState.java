package com.game.core.data;

/**
 * Immutable snapshot of the vine's physiological state at end of one day.
 * Updated each daily tick by {@code com.game.sim.vine.VineSimulator}.
 * Ripening fields (brix, taGL, pH, yanMgL, tanninRipeness01) are meaningful
 * from VERAISON onward; before that they hold their initial/default values.
 * Frozen per SIM-SPEC §2.
 *
 * @param stage              current phenological stage
 * @param gddAccum           accumulated GDD since budbreak (base 10°C)
 * @param healthFraction     vine health 0..1 (1 = perfectly healthy)
 * @param potentialYieldKg   expected yield in kg, locked progressively
 * @param brix               sugar concentration in °Bx
 * @param taGL               titratable acidity in g/L
 * @param pH                 must pH
 * @param yanMgL             yeast-assimilable nitrogen in mg/L
 * @param tanninRipeness01   tannin ripeness 0 (green) .. 1 (fully ripe)
 */
public record VineState(
        PhenoStage stage,
        double gddAccum,
        double healthFraction,
        double potentialYieldKg,
        double brix,
        double taGL,
        double pH,
        double yanMgL,
        double tanninRipeness01
) {}
