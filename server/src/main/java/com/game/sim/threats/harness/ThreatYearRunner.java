package com.game.sim.threats.harness;

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
import com.game.sim.threats.engine.DayInputs;
import com.game.sim.threats.engine.DayResult;
import com.game.sim.threats.engine.ThreatEngine;
import com.game.sim.threats.engine.ThreatRegistry;
import com.game.sim.threats.engine.ThreatReport;
import com.game.sim.vine.KakhetiVineSimulator;
import com.game.sim.vine.VineSimulator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Threat-aware headless harness (SIM-THREATS-SPEC §4 Lane D).
 *
 * <p>Mirrors {@link com.game.sim.harness.YearRunner} exactly but composes the
 * {@link ThreatEngine} into the daily loop per spec §2:
 *
 * <pre>
 *   for each day d:
 *       vine = vineSimulator.tick(vine, weather[d], site, suitability, pruning)
 *       DayResult r = threatEngine.step(vine, dayInputs[d])
 *       vine = r.vine()    // health/yield adjusted
 * </pre>
 *
 * <p>At harvest the cumulative quality penalty is subtracted from
 * {@code MustProfile.fruitHealth01} before fermentation. If the engine's season
 * worst-fault is more severe than the cellar fault, it overrides the resolved
 * {@link WineLot#fault()} (spec §5). The frozen {@link Resolver} is never modified.
 *
 * <p><strong>Default levers:</strong> {@code ownRoots=true}, no sprays, no netting,
 * no ecosystem allies — maximum threat visibility.
 *
 * <p>Run via:
 * <pre>
 *   mvn -q compile exec:java \
 *       -Dexec.mainClass=com.game.sim.threats.harness.ThreatYearRunner \
 *       -Dexec.args="--seed 42 --pickDay 290"
 * </pre>
 */
public final class ThreatYearRunner {

    // ── Defaults ────────────────────────────────────────────────────────────
    private static final long   DEFAULT_SEED     = 42L;
    private static final int    DEFAULT_PICK_DAY = 290;
    private static final int    DEFAULT_BUD_LOAD = 12;
    private static final int    SIM_YEAR         = 1;

    // ── Cellar defaults (mirroring YearRunner) ───────────────────────────────
    private static final FermentMethod METHOD      = FermentMethod.RED;
    private static final double        CELLAR_TEMP = 25.0;
    private static final double        TENDING     = 0.80;
    private static final String        LABEL       = "Glekhi's Threat-Tested Saperavi";

    // ── Fault severity order (spec §4 aggregation) ──────────────────────────
    private static final List<Fault> FAULT_SEVERITY_ORDER = List.of(
            Fault.NONE, Fault.OXIDATION, Fault.REDUCTION_H2S,
            Fault.VOLATILE_ACIDITY, Fault.STUCK_FERMENT);

    private ThreatYearRunner() {}

    // ── Entry point ──────────────────────────────────────────────────────────

    public static void main(String[] args) {
        long seed    = DEFAULT_SEED;
        int  pickDay = DEFAULT_PICK_DAY;
        int  budLoad = DEFAULT_BUD_LOAD;

        for (int i = 0; i + 1 < args.length; i += 2) {
            switch (args[i]) {
                case "--seed"    -> seed    = Long.parseLong(args[i + 1]);
                case "--pickDay" -> pickDay = Integer.parseInt(args[i + 1]);
                case "--budLoad" -> budLoad = Integer.parseInt(args[i + 1]);
                default -> System.err.println("Ignoring unknown arg: " + args[i]);
            }
        }

        WineLot bottle = run(seed, budLoad, pickDay, true);

        System.out.println();
        System.out.println("================ THE BOTTLE (with threats) ================");
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
            if (!aroma.isEmpty()) aroma.append(", ");
            aroma.append(e.getKey()).append("=").append(String.format("%.2f", e.getValue()));
        }
        System.out.printf("  Aroma        : %s%n", aroma);
        System.out.println("===========================================================");
    }

    // ── Core pipeline ────────────────────────────────────────────────────────

    /**
     * Run the full single-year pipeline with threats.  Pure/deterministic from {@code seed}.
     *
     * @param verbose when true, prints the day-log and threat events to stdout
     * @return the resolved {@link WineLot} with all threat effects applied
     */
    public static WineLot run(long seed, int budLoad, int pickDay, boolean verbose) {
        WeatherModel weather = new KakhetiWeatherModel();
        VineSimulator vine   = new KakhetiVineSimulator();
        Fermenter fermenter  = new KineticFermenter();

        RngStreams rng = new RngStreams(seed);
        List<DailyWeather> days = weather.generateYear(rng, Region.KAKHETI, SIM_YEAR);
        Vintage vintage = weather.rollVintage(rng, Region.KAKHETI, SIM_YEAR, days);

        SiteProfile site   = kakhetianSite();
        double suitability = SiteSuitability.score(Variety.SAPERAVI, site);
        PruningDecision pruning = new PruningDecision(budLoad);

        // Default levers: own roots on, no management — threats have maximum visibility
        boolean ownRoots         = true;
        double  canopyOpenness01 = 0.40;
        boolean leafPulled       = false;
        double  copperSpray01    = 0.0;
        double  sulfurSpray01    = 0.0;
        boolean netting          = false;
        boolean guardDog         = false;
        boolean falcons          = false;
        boolean cats             = false;
        boolean ducks            = false;
        double  coverCrop01      = 0.0;

        ThreatEngine engine = new ThreatEngine(ThreatRegistry.all());

        if (verbose) {
            System.out.println("===== QVEVRI THREAT YEAR — ONE SAPERAVI, ONE YEAR =====");
            System.out.printf("  Seed=%d  budLoad=%d  pickDay=%d%n", seed, budLoad, pickDay);
            System.out.printf("  Site : %s slope=%.0f° aspect=%.0f° alt=%.0fm  suitability=%.2f%n",
                    site.soil(), site.slopeDeg(), site.aspectDeg(), site.altitudeM(), suitability);
            System.out.printf("  Vintage: %s  seasonGDD=%.0f  Winkler=%s%n",
                    vintage.patternLabel(), vintage.gddSeason(), vintage.winkler());
            System.out.println("  Levers: ownRoots=true, no sprays, no netting, no allies");
            System.out.println("  --- day log ---");
        }

        VineState state = new VineState(
                PhenoStage.DORMANCY, 0.0, 1.0, 0.0, 0.0, 8.0, 3.0, 200.0, 0.0);
        VineState atPick  = null;
        PhenoStage lastStage = state.stage();
        SimClock clock = new SimClock(SIM_YEAR);

        List<String> threatEventLog = new ArrayList<>();
        Fault worstThreatFault = Fault.NONE;
        boolean killed = false;

        for (int d = 0; d < 365; d++) {
            DailyWeather today = days.get(d);

            // 1. Vine tick (unchanged from YearRunner)
            state = vine.tick(state, today, site, suitability, pruning);

            // 2. Threat engine step — after the vine tick, per spec §2
            DayInputs inputs = new DayInputs(
                    today, vintage.gddSeason(), site,
                    ownRoots, canopyOpenness01, leafPulled,
                    copperSpray01, sulfurSpray01, netting,
                    guardDog, falcons, cats, ducks, coverCrop01,
                    rng);

            DayResult result = engine.step(state, inputs);
            state = result.vine();

            // Track worst fault for the season
            ThreatReport report = result.report();
            if (faultSeverity(report.inducedFault()) > faultSeverity(worstThreatFault)) {
                worstThreatFault = report.inducedFault();
            }
            if (report.dead()) killed = true;

            // Collect and optionally print threat events
            for (String tell : report.activeTells()) {
                String entry = String.format("  day %3d [%-18s]: %s",
                        today.dayOfYear(), state.stage(), tell);
                threatEventLog.add(entry);
                if (verbose) System.out.println(entry);
            }

            // Phenology transitions
            if (verbose && state.stage() != lastStage) {
                System.out.printf(
                    "  day %3d: %-16s  gdd=%.0f  brix=%.1f  ta=%.1f  health=%.2f  yield=%.1fkg%n",
                    today.dayOfYear(), state.stage(), state.gddAccum(),
                    state.brix(), state.taGL(),
                    state.healthFraction(), state.potentialYieldKg());
                lastStage = state.stage();
            }

            if (today.dayOfYear() == pickDay) atPick = state;
            clock.advanceDay();
        }
        if (atPick == null) atPick = state;

        double qualPenalty = engine.cumulativeQualityPenalty();
        if (verbose) {
            System.out.printf("  --- %d threat events logged ---%n", threatEventLog.size());
            System.out.printf("  Cumulative quality penalty : %.3f%n", qualPenalty);
            System.out.printf("  Worst threat fault         : %s  vine dead: %s%n",
                    worstThreatFault, killed);
        }

        // Reduce fruitHealth01 by the cumulative quality penalty (spec §5)
        double volumeL  = Harvest.yieldToVolume(atPick.potentialYieldKg());
        MustProfile raw = Harvest.pick(atPick, volumeL, vintage.year());
        double adjHealth = Math.max(0.0, raw.fruitHealth01() - qualPenalty);
        MustProfile must = new MustProfile(
                raw.volumeL(), raw.brix(), raw.taGL(), raw.pH(),
                raw.yanMgL(), raw.tanninRipeness01(), adjHealth,
                raw.vintageYear());

        if (verbose) {
            System.out.printf("  --- pick (day %d) ---%n", pickDay);
            System.out.printf(
                "  must : vol=%.1fL brix=%.1f ta=%.1f pH=%.2f yan=%.0f tannin=%.2f health=%.2f%n",
                must.volumeL(), must.brix(), must.taGL(), must.pH(),
                must.yanMgL(), must.tanninRipeness01(), must.fruitHealth01());
        }

        CellarResult cellar = fermenter.ferment(must, METHOD, CELLAR_TEMP, TENDING, rng);
        if (verbose) {
            System.out.printf(
                "  cellar: abv=%.2f%% ta=%.1f pH=%.2f fault=%s extraction=%.2f clean=%.2f%n",
                cellar.abv(), cellar.finalTaGL(), cellar.pH(), cellar.fault(),
                cellar.extraction01(), cellar.cleanliness01());
        }

        WineLot resolved = Resolver.resolve(
                Variety.SAPERAVI, METHOD, must, cellar, vintage, suitability, LABEL);

        // Override fault only if threat-induced fault is more severe than cellar's (spec §5)
        Fault finalFault = cellar.fault();
        if (faultSeverity(worstThreatFault) > faultSeverity(finalFault)) {
            finalFault = worstThreatFault;
            if (verbose) {
                System.out.printf("  Threat fault override: %s -> %s%n",
                        cellar.fault(), finalFault);
            }
            // Rebuild WineLot with the threat fault (Resolver is frozen — cannot modify it)
            resolved = new WineLot(
                    resolved.variety(), resolved.style(), resolved.vintageYear(),
                    resolved.volumeL(), resolved.abv(), resolved.quality(),
                    resolved.ageabilityYears(), finalFault,
                    resolved.aroma(), resolved.appellationOk(), resolved.label());
        }

        return resolved;
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private static SiteProfile kakhetianSite() {
        return new SiteProfile(SoilType.HUMUS_CARBONATE, 12.0, 180.0, 450.0, 0.15, 0.25);
    }

    private static int faultSeverity(Fault f) {
        int idx = FAULT_SEVERITY_ORDER.indexOf(f);
        return idx < 0 ? 0 : idx;
    }
}
