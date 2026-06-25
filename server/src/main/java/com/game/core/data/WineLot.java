package com.game.core.data;

import java.util.SortedMap;

/**
 * The final wine product from one season.
 * {@code aroma} keys are sorted (TreeMap) for determinism — never use unsorted map here.
 * {@code quality} is 0..100.
 * Frozen per SIM-SPEC §2.
 *
 * @param variety          grape variety
 * @param style            wine style (RED for Saperavi/RED method in Phase 0)
 * @param vintageYear      harvest year
 * @param volumeL          volume in litres
 * @param abv              alcohol by volume (e.g. 13.5 for 13.5%)
 * @param quality          hedonic quality score 0..100
 * @param ageabilityYears  estimated years of positive ageing potential
 * @param fault            dominant fault detected (NONE if clean)
 * @param aroma            sorted descriptor → intensity map (0..1 per key)
 * @param appellationOk    true if appellation rules satisfied (always false Phase 0)
 * @param label            player-assigned or generated label string
 */
public record WineLot(
        Variety variety,
        WineStyle style,
        int vintageYear,
        double volumeL,
        double abv,
        double quality,
        double ageabilityYears,
        Fault fault,
        SortedMap<String, Double> aroma,
        boolean appellationOk,
        String label
) {}
