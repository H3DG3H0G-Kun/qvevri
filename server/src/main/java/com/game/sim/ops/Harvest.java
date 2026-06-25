package com.game.sim.ops;

import com.game.core.data.MustProfile;
import com.game.core.data.VineState;

/**
 * Harvest operations — capturing the grape must at pick day.
 *
 * <p>Frozen seam per SIM-SPEC §3.5.
 */
public final class Harvest {

    /**
     * Litres of juice per kilogram of Saperavi grapes.
     * Saperavi is thick-skinned; typical press yield is 65–70 % by weight.
     * At standard grape density ~1.07 kg/L: 0.70 kg juice / kg grape.
     */
    static final double JUICE_LITRES_PER_KG = 0.70;

    private Harvest() {}

    /**
     * Capture the grape must at the chosen pick day from the vine's final state.
     *
     * <p>All chemistry values are read directly from the vine state snapshot —
     * the vine simulator has already advanced the ripening clocks to this day.
     * Volume is derived from the vine's potential yield in kg multiplied by
     * the juice extraction factor.
     *
     * @param atPick         vine state on the pick day (after the last daily tick)
     * @param volumeFromYield juice volume in litres (potentialYieldKg * JUICE_LITRES_PER_KG)
     * @return {@link MustProfile} capturing the must at harvest
     */
    public static MustProfile pick(VineState atPick, double volumeFromYield) {
        return new MustProfile(
                volumeFromYield,
                atPick.brix(),
                atPick.taGL(),
                atPick.pH(),
                atPick.yanMgL(),
                atPick.tanninRipeness01(),
                atPick.healthFraction(),
                // vintageYear is not in VineState — caller passes it via the harness;
                // we use 0 as a placeholder sentinel; YearRunner overrides via its own
                // vintageYear-aware MustProfile construction.
                // NOTE: this overload is used by YearRunner which passes volumeFromYield
                // already computed; vintageYear is embedded by the harness wrapper.
                0
        );
    }

    /**
     * Capture the grape must at pick, including the vintage year.
     *
     * <p>This is the primary method used by {@code YearRunner}: the harness knows
     * the simulation year and passes it here so the {@link MustProfile} is complete.
     *
     * @param atPick      vine state on the pick day
     * @param volumeL     juice volume in litres
     * @param vintageYear simulation calendar year of the harvest
     * @return complete {@link MustProfile}
     */
    public static MustProfile pick(VineState atPick, double volumeL, int vintageYear) {
        return new MustProfile(
                volumeL,
                atPick.brix(),
                atPick.taGL(),
                atPick.pH(),
                atPick.yanMgL(),
                atPick.tanninRipeness01(),
                atPick.healthFraction(),
                vintageYear
        );
    }

    /**
     * Convenience: compute juice volume from yield weight using the standard
     * juice-extraction factor.
     *
     * @param yieldKg potentialYieldKg from the vine state
     * @return volume in litres
     */
    public static double yieldToVolume(double yieldKg) {
        return yieldKg * JUICE_LITRES_PER_KG;
    }
}
