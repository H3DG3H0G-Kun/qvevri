package com.game.vineyard;

import java.util.Map;

/**
 * Resolved bottle sub-object in {@link VineyardYearResult}.
 * Matches VINEYARD-API §1 "bottle" shape.
 *
 * @param variety          grape variety name
 * @param style            wine style name
 * @param vintageYear      harvest year
 * @param volumeL          volume in litres
 * @param abv              alcohol by volume (e.g. 13.9 for 13.9 %)
 * @param quality          hedonic quality score 0..100
 * @param ageabilityYears  estimated years of positive ageing potential
 * @param fault            dominant fault name ("NONE" if clean)
 * @param appellationOk    true if appellation rules satisfied
 * @param label            generated label string
 * @param aroma            sorted aroma descriptor map (keys sorted for determinism)
 */
public record BottleDto(
        String variety,
        String style,
        int vintageYear,
        double volumeL,
        double abv,
        double quality,
        double ageabilityYears,
        String fault,
        boolean appellationOk,
        String label,
        Map<String, Double> aroma
) {}
