package com.game.sim.harness;

import com.game.core.data.*;
import com.game.core.time.RngStreams;
import com.game.core.time.SimClock;
import com.game.core.weather.KakhetiWeatherModel;
import com.game.core.weather.WeatherModel;
import com.game.sim.cellar.CellarResult;
import com.game.sim.cellar.Fermenter;
import com.game.sim.cellar.KineticFermenter;
import com.game.sim.ops.Harvest;
import com.game.sim.resolve.Resolver;
import com.game.sim.soil.SiteSuitability;
import com.game.sim.vine.KakhetiVineSimulator;
import com.game.sim.vine.VineSimulator;

import java.util.List;
import java.util.Map;

/**
 * Headless text harness for the QVEVRI simulation core (SIM-SPEC §3.8).
 *
 * <p>Runs ONE Saperavi vine on ONE canonical Kakheti plot through ONE simulated
 * year and prints the resulting bottle plus a short day-log (phenology
 * transitions, must chemistry at pick, vintage class). Fully deterministic from
 * the master seed.
 *
 * <p>Run via Gradle (no Spring context needed):
 * <pre>
 *   ./gradlew runSim --args="--seed 42 --budLoad 12 --pickDay 290"
 * </pre>
 * or directly: {@code java com.game.sim.harness.YearRunner --seed 42 ...}
 */
public final class YearRunner {

    // ── Defaults (SIM-SPEC §3.8) ────────────────────────────────────────────
    private static final long DEFAULT_SEED     = 42L;
    private static final int  DEFAULT_BUD_LOAD = 12;   // Saperavi balanced sweet-spot
    private static final int  DEFAULT_PICK_DAY = 290;  // ~mid-Oct, optimal Saperavi window
    private static final int  SIM_YEAR         = 1;

    // ── Cellar defaults (RED, sensible cellar) ──────────────────────────────
    private static final FermentMethod METHOD       = FermentMethod.RED;
    private static final double        CELLAR_TEMP  = 25.0; // within 21–30 °C red band
    private static final double        TENDING      = 0.80; // good cap management
    private static final String        LABEL        = "Glekhi's First Saperavi";

    // ── Canonical Kakheti site (HUMUS_CARBONATE, S-facing slope, mid altitude)
    private static SiteProfile kakhetianSite() {
        return new SiteProfile(SoilType.HUMUS_CARBONATE, 12.0, 180.0, 450.0, 0.15, 0.25);
    }

    private YearRunner() {}

    public static void main(String[] args) {
        long seed   = DEFAULT_SEED;
        int  budLoad = DEFAULT_BUD_LOAD;
        int  pickDay = DEFAULT_PICK_DAY;

        for (int i = 0; i + 1 < args.length; i += 2) {
            switch (args[i]) {
                case "--seed"    -> seed    = Long.parseLong(args[i + 1]);
                case "--budLoad" -> budLoad = Integer.parseInt(args[i + 1]);
                case "--pickDay" -> pickDay = Integer.parseInt(args[i + 1]);
                default -> System.err.println("Ignoring unknown arg: " + args[i]);
            }
        }

        WineLot bottle = run(seed, budLoad, pickDay, true);

        System.out.println();
        System.out.println("================ THE BOTTLE ================");
        System.out.printf("  Label        : %s%n", bottle.label());
        System.out.printf("  Variety/Style: %s / %s%n", bottle.variety(), bottle.style());
        System.out.printf("  Vintage      : year %d%n", bottle.vintageYear());
        System.out.printf("  Volume       : %.1f L%n", bottle.volumeL());
        System.out.printf("  ABV          : %.2f %%%n", bottle.abv());
        System.out.printf("  Quality      : %.1f / 100%n", bottle.quality());
        System.out.printf("  Ageability   : %.1f years%n", bottle.ageabilityYears());
        System.out.printf("  Fault        : %s%n", bottle.fault());
        System.out.printf("  Appellation  : %s%n", bottle.appellationOk() ? "yes" : "no");
        StringBuilder aroma = new StringBuilder();
        for (Map.Entry<String, Double> e : bottle.aroma().entrySet()) {
            if (aroma.length() > 0) aroma.append(", ");
            aroma.append(e.getKey()).append("=").append(String.format("%.2f", e.getValue()));
        }
        System.out.printf("  Aroma        : %s%n", aroma);
        System.out.println("===========================================");
    }

    /**
     * Run the full single-year pipeline. Pure/deterministic from {@code seed}.
     *
     * @param verbose when true, prints the day-log to stdout
     * @return the resolved {@link WineLot}
     */
    public static WineLot run(long seed, int budLoad, int pickDay, boolean verbose) {
        WeatherModel weather = new KakhetiWeatherModel();
        VineSimulator vine   = new KakhetiVineSimulator();
        Fermenter fermenter  = new KineticFermenter();

        RngStreams rng = new RngStreams(seed);
        List<DailyWeather> days = weather.generateYear(rng, Region.KAKHETI, SIM_YEAR);
        Vintage vintage = weather.rollVintage(rng, Region.KAKHETI, SIM_YEAR, days);

        SiteProfile site = kakhetianSite();
        double suitability = SiteSuitability.score(Variety.SAPERAVI, site);
        PruningDecision pruning = new PruningDecision(budLoad);

        if (verbose) {
            System.out.println("=========== QVEVRI — ONE SAPERAVI, ONE YEAR ===========");
            System.out.printf("  Seed=%d  budLoad=%d  pickDay=%d%n", seed, budLoad, pickDay);
            System.out.printf("  Site : %s slope=%.0f° aspect=%.0f° alt=%.0fm  suitability=%.2f%n",
                    site.soil(), site.slopeDeg(), site.aspectDeg(), site.altitudeM(), suitability);
            System.out.printf("  Vintage: %s  seasonGDD=%.0f  Winkler=%s%n",
                    vintage.patternLabel(), vintage.gddSeason(), vintage.winkler());
            System.out.println("  --- phenology log ---");
        }

        VineState state = new VineState(
                PhenoStage.DORMANCY, 0.0, 1.0, 0.0, 0.0, 8.0, 3.0, 200.0, 0.0);
        VineState atPick = null;
        PhenoStage lastStage = state.stage();
        SimClock clock = new SimClock(SIM_YEAR);

        for (int d = 0; d < 365; d++) {
            DailyWeather today = days.get(d);
            state = vine.tick(state, today, site, suitability, pruning);
            if (verbose && state.stage() != lastStage) {
                System.out.printf("  day %3d: %-16s  gdd=%.0f  brix=%.1f  ta=%.1f%n",
                        today.dayOfYear(), state.stage(), state.gddAccum(),
                        state.brix(), state.taGL());
                lastStage = state.stage();
            }
            if (today.dayOfYear() == pickDay) {
                atPick = state;
            }
            clock.advanceDay();
        }
        if (atPick == null) atPick = state; // out-of-range pick: surface real final state

        double volumeL = Harvest.yieldToVolume(atPick.potentialYieldKg());
        MustProfile must = Harvest.pick(atPick, volumeL, vintage.year());

        if (verbose) {
            System.out.printf("  --- pick (day %d) ---%n", pickDay);
            System.out.printf("  must : vol=%.1fL brix=%.1f ta=%.1f pH=%.2f yan=%.0f tannin=%.2f health=%.2f%n",
                    must.volumeL(), must.brix(), must.taGL(), must.pH(),
                    must.yanMgL(), must.tanninRipeness01(), must.fruitHealth01());
        }

        CellarResult cellar = fermenter.ferment(must, METHOD, CELLAR_TEMP, TENDING, rng);
        if (verbose) {
            System.out.printf("  cellar: abv=%.2f%% ta=%.1f pH=%.2f fault=%s extraction=%.2f clean=%.2f%n",
                    cellar.abv(), cellar.finalTaGL(), cellar.pH(), cellar.fault(),
                    cellar.extraction01(), cellar.cleanliness01());
        }

        return Resolver.resolve(Variety.SAPERAVI, METHOD, must, cellar, vintage, suitability, LABEL);
    }
}
