package com.game.sim.cellar;

import com.game.core.data.Fault;

/**
 * Outcome of one fermentation run.
 *
 * <p>Frozen seam per SIM-SPEC §3.6.
 *
 * @param abv           alcohol by volume (%) at the end of fermentation
 * @param finalTaGL     titratable acidity in the finished wine (g/L)
 * @param pH            final wine pH
 * @param fault         dominant fault detected (NONE if clean)
 * @param extraction01  colour/phenolic extraction score 0..1 (reds only; ~0 for whites)
 * @param cleanliness01 aromatic cleanliness score 0..1 (1 = fault-free, clean ferment)
 */
public record CellarResult(
        double abv,
        double finalTaGL,
        double pH,
        Fault fault,
        double extraction01,
        double cleanliness01
) {}
