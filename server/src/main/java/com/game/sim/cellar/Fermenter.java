package com.game.sim.cellar;

import com.game.core.data.FermentMethod;
import com.game.core.data.MustProfile;
import com.game.core.time.RngStreams;

/**
 * Contract for deterministic fermentation kinetics modelling.
 *
 * <p>NOT a timer — models the chemical kinetics of sugar consumption, alcohol
 * production, acidity evolution, and fault risk.  Frozen seam per SIM-SPEC §3.6.
 *
 * <p>Implementations must be pure: same inputs (including RNG streams reset to
 * the same seed) produce the same {@link CellarResult}.
 */
public interface Fermenter {

    /**
     * Model fermentation kinetics for the given must under the given conditions.
     *
     * <p>Style temp bands:
     * <ul>
     *   <li>Whites / IMERETIAN / SPARKLING_BASE / SWEET: 7–16°C</li>
     *   <li>Reds / KAKHETIAN: 21–30°C</li>
     *   <li>{'>'} 32°C any style → {@link com.game.core.data.Fault#STUCK_FERMENT}</li>
     * </ul>
     *
     * <p>Key kinetics:
     * <ul>
     *   <li>ABV ≈ startBrix × conversion factor (0.55–0.65 depending on temp/yeast vigour)</li>
     *   <li>Low YAN (&lt;~100 mg/L for reds) → {@link com.game.core.data.Fault#REDUCTION_H2S} risk</li>
     *   <li>Poor cap tending (tending01 low) → lower extraction, VA / oxidation risk</li>
     *   <li>Good practice (tending01 high) → clean, high extraction</li>
     * </ul>
     *
     * @param must        must profile from harvest
     * @param method      fermentation method chosen by the player
     * @param cellarTempC ambient cellar temperature in °C (controls fermentation rate and risk)
     * @param tending01   cap management / cellar hygiene quality 0..1 (1 = exemplary)
     * @param rng         seeded stream factory for small bounded jitter
     * @return complete cellar result
     */
    CellarResult ferment(MustProfile must, FermentMethod method,
                         double cellarTempC, double tending01, RngStreams rng);
}
