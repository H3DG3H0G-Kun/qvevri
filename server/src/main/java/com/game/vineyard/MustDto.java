package com.game.vineyard;

/**
 * Must chemistry sub-object in {@link VineyardYearResult}.
 * Matches VINEYARD-API §1 "must" shape.
 *
 * @param volumeL           juice volume in litres
 * @param brix              sugar at pick in °Bx
 * @param taGL              titratable acidity in g/L
 * @param pH                must pH
 * @param yanMgL            yeast-assimilable nitrogen in mg/L
 * @param tanninRipeness01  tannin ripeness 0..1
 * @param fruitHealth01     composite fruit health fraction 0..1
 */
public record MustDto(
        double volumeL,
        double brix,
        double taGL,
        double pH,
        double yanMgL,
        double tanninRipeness01,
        double fruitHealth01
) {}
