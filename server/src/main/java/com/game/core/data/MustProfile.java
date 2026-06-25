package com.game.core.data;

/**
 * The grape must captured at harvest — input to the cellar fermentation.
 * Frozen per SIM-SPEC §2.
 *
 * @param volumeL           volume of must in litres
 * @param brix              sugar at pick in °Bx
 * @param taGL              titratable acidity at pick in g/L
 * @param pH                must pH at pick
 * @param yanMgL            yeast-assimilable nitrogen in mg/L
 * @param tanninRipeness01  tannin ripeness 0 (green) .. 1 (fully ripe)
 * @param fruitHealth01     composite fruit health fraction 0..1
 * @param vintageYear       simulation calendar year of harvest
 */
public record MustProfile(
        double volumeL,
        double brix,
        double taGL,
        double pH,
        double yanMgL,
        double tanninRipeness01,
        double fruitHealth01,
        int vintageYear
) {}
