package com.game.sim.threats.api;

import com.game.core.data.DailyWeather;
import com.game.core.data.SiteProfile;
import com.game.core.data.VineState;

import java.util.random.RandomGenerator;

/**
 * Everything a {@link ThreatSource} needs to decide its effect for one day.
 * The engine builds a fresh context per source per day, injecting that source's
 * own RNG stream ({@code rng}) and prior {@link ThreatMemory} ({@code memory}).
 *
 * <p>The management + ecosystem fields are the player's levers (defaulted by the
 * harness this phase): canopy/spray/netting counters and animal-ally suppressors.
 *
 * @param ownRoots        true = vine on its own roots (phylloxera-vulnerable);
 *                        false = grafted onto American rootstock (phylloxera-safe)
 * @param canopyOpenness01 0..1, higher = more airflow/sun (less fungal, more sunburn)
 * @param leafPulled      leaves pulled around bunches (airflow vs sunburn tradeoff)
 * @param copperSpray01   0..1 downy-mildew counter
 * @param sulfurSpray01   0..1 powdery-mildew counter
 * @param netting         hail/bird netting present
 * @param guardDog        Georgian Shepherd / Nagazi — suppresses boar/deer
 * @param falcons         falcons/hawks — scatter starlings
 * @param cats            cats/barn owls — suppress rodents
 * @param ducks           ducks/chickens — eat insects + fallen fruit
 * @param coverCrop01     0..1 cover crop — beneficials suppress mites/leafhoppers
 */
public record ThreatContext(int dayOfYear,
                            DailyWeather today,
                            double gddSeason,
                            VineState vine,
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
                            RandomGenerator rng,
                            ThreatMemory memory) {}
