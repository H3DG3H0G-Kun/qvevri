package com.game.sim.threats.engine;

import com.game.core.data.DailyWeather;
import com.game.core.data.SiteProfile;
import com.game.core.time.RngStreams;

/**
 * All inputs needed by {@link ThreatEngine#step} for one day.
 *
 * <p>Lever fields correspond one-to-one with {@link com.game.sim.threats.api.ThreatContext}
 * levers. The harness sets defaults (ownRoots=true, no sprays/netting, no allies).
 *
 * @param today           weather for this day
 * @param gddSeason       accumulated GDD this season (base 10 °C, from the Vintage)
 * @param site            plot soil + geometry
 * @param ownRoots        vine on own roots (phylloxera-vulnerable if true)
 * @param canopyOpenness01 0..1 canopy management level (higher = more airflow)
 * @param leafPulled      leaf removal around bunches
 * @param copperSpray01   0..1 copper spray intensity (downy mildew counter)
 * @param sulfurSpray01   0..1 sulfur spray intensity (powdery mildew counter)
 * @param netting         hail/bird netting present
 * @param guardDog        guard dog present (boar/deer deterrent)
 * @param falcons         falcons/hawks present (starling deterrent)
 * @param cats            cats present (rodent suppression)
 * @param ducks           ducks present (insect suppression)
 * @param coverCrop01     0..1 cover crop intensity (mite/leafhopper suppression)
 * @param rng             master RNG factory; engine derives per-source streams via
 *                        {@code rng.stream("threat." + sourceId)}
 */
public record DayInputs(
        DailyWeather today,
        double gddSeason,
        SiteProfile site,
        boolean ownRoots,
        double canopyOpenness01,
        boolean leafPulled,
        double copperSpray01,
        double sulfurSpray01,
        boolean netting,
        boolean guardDog,
        boolean falcons,
        boolean cats,
        boolean ducks,
        double coverCrop01,
        RngStreams rng
) {}
