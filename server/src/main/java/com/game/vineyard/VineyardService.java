package com.game.vineyard;

import com.game.core.data.*;
import com.game.core.time.RngStreams;
import com.game.core.time.SimClock;
import com.game.core.weather.KakhetiWeatherModel;
import com.game.core.weather.WeatherModel;
import com.game.exception.ApiException;
import com.game.sim.cellar.CellarResult;
import com.game.sim.cellar.Fermenter;
import com.game.sim.cellar.KineticFermenter;
import com.game.sim.ops.Harvest;
import com.game.sim.region.RegionSiteProfiles;
import com.game.sim.resolve.Resolver;
import com.game.sim.soil.SiteSuitability;
import com.game.sim.threats.engine.DayInputs;
import com.game.sim.threats.engine.DayResult;
import com.game.sim.threats.engine.ThreatEngine;
import com.game.sim.threats.engine.ThreatRegistry;
import com.game.sim.threats.engine.ThreatReport;
import com.game.sim.vine.KakhetiVineSimulator;
import com.game.sim.vine.VineSimulator; // kept: used as local variable type for per-request simulator

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Vineyard simulation service — wraps the deterministic sim pipeline and
 * returns a {@link VineyardYearResult} DTO.
 *
 * <p>Mirrors {@link com.game.sim.harness.YearRunner} for the no-threat path and
 * {@link com.game.sim.threats.harness.ThreatYearRunner} for the threat path.
 * No wall-clock or external RNG is used; all randomness comes from the seeded
 * {@link RngStreams}.
 */
@Service
public class VineyardService {

    // ── Cellar constants (mirroring YearRunner) ──────────────────────────────
    private static final FermentMethod FERMENT_METHOD = FermentMethod.RED;
    private static final double        CELLAR_TEMP    = 25.0;
    private static final double        TENDING        = 0.80;
    private static final String        LABEL          = "Glekhi's First Saperavi";

    // ── Fault severity order (for override logic from ThreatYearRunner) ──────
    private static final List<Fault> FAULT_SEVERITY_ORDER = List.of(
            Fault.NONE, Fault.OXIDATION, Fault.REDUCTION_H2S,
            Fault.VOLATILE_ACIDITY, Fault.STUCK_FERMENT);

    // ── Sim year constant ────────────────────────────────────────────────────
    private static final int SIM_YEAR = 1;

    // ── Constructor-injected collaborators (stateless; thread-safe) ──────────
    private final WeatherModel weatherModel;
    private final Fermenter fermenter;

    public VineyardService() {
        this.weatherModel  = new KakhetiWeatherModel();
        this.fermenter     = new KineticFermenter();
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Run a full deterministic simulated year and return the result DTO.
     *
     * @param request validated request (variety/soil already validated as enum names)
     * @return result DTO matching VINEYARD-API §1
     */
    public VineyardYearResult simulate(VineyardRequest request) {
        long   seed    = request.getSeed();
        int    budLoad = request.getBudLoad();
        int    pickDay = request.getPickDay();
        boolean useThreats = request.isThreats();

        // Validate enum values — throw 400 if unknown (caught by GlobalExceptionHandler)
        Region  region  = parseRegion(request.getRegion());
        Variety variety = parseVariety(request.getVariety());
        SoilType soil   = parseSoil(request.getSoil());

        // ── Build seeded RNG ──────────────────────────────────────────────────
        RngStreams rng = new RngStreams(seed);

        // ── Weather & vintage ─────────────────────────────────────────────────
        List<DailyWeather> days = weatherModel.generateYear(rng, region, SIM_YEAR);
        Vintage vintage         = weatherModel.rollVintage(rng, region, SIM_YEAR, days);

        // ── Site — region-canonical geometry, with soil override from request ──
        // For KAKHETI/HUMUS_CARBONATE (the default) this reproduces the original
        // SiteProfile(HUMUS_CARBONATE, 12.0, 180.0, 450.0, 0.15, 0.25) exactly.
        SiteProfile regionSite = RegionSiteProfiles.of(region);
        SiteProfile site = new SiteProfile(soil,
                regionSite.slopeDeg(), regionSite.aspectDeg(), regionSite.altitudeM(),
                regionSite.frostRisk(), regionSite.waterProximity());
        // Use variety-parameterised simulator
        VineSimulator vineSimulatorForRequest = new KakhetiVineSimulator(variety);
        double suitability  = SiteSuitability.score(variety, site);
        PruningDecision pruning = new PruningDecision(budLoad);

        // ── Threat engine (default levers: no management, max threat visibility)
        ThreatEngine threatEngine = useThreats
                ? new ThreatEngine(ThreatRegistry.all())
                : null;

        // ── Threat lever defaults (from ThreatYearRunner) ─────────────────────
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

        // ── Initial vine state (from YearRunner) ──────────────────────────────
        VineState state = new VineState(
                PhenoStage.DORMANCY, 0.0, 1.0, 0.0, 0.0, 8.0, 3.0, 200.0, 0.0);
        VineState atPick  = null;
        PhenoStage lastStage = state.stage();
        SimClock clock = new SimClock(SIM_YEAR);

        // ── Event log ─────────────────────────────────────────────────────────
        List<String> events = new ArrayList<>();

        // ── Fault tracking for threat override ────────────────────────────────
        Fault worstThreatFault = Fault.NONE;

        // ── Main daily loop ───────────────────────────────────────────────────
        for (int d = 0; d < 365; d++) {
            DailyWeather today = days.get(d);

            // 1. Vine tick
            state = vineSimulatorForRequest.tick(state, today, site, suitability, pruning);

            // 2. Threat engine step (after vine tick, per spec §2)
            if (threatEngine != null) {
                DayInputs inputs = new DayInputs(
                        today, vintage.gddSeason(), site,
                        ownRoots, canopyOpenness01, leafPulled,
                        copperSpray01, sulfurSpray01, netting,
                        guardDog, falcons, cats, ducks, coverCrop01,
                        rng);

                DayResult result = threatEngine.step(state, inputs);
                state = result.vine();

                ThreatReport report = result.report();
                if (faultSeverity(report.inducedFault()) > faultSeverity(worstThreatFault)) {
                    worstThreatFault = report.inducedFault();
                }

                // Collect threat tells as events
                for (String tell : report.activeTells()) {
                    events.add("day " + today.dayOfYear() + ": " + tell);
                }
            }

            // 3. Record phenology stage transitions
            if (state.stage() != lastStage) {
                events.add("day " + today.dayOfYear() + ": " + stageName(state.stage()));
                lastStage = state.stage();
            }

            // 4. Capture vine state at pick day
            if (today.dayOfYear() == pickDay) {
                atPick = state;
            }

            clock.advanceDay();
        }

        // Out-of-range pick: use final state
        if (atPick == null) atPick = state;

        // ── Harvest ───────────────────────────────────────────────────────────
        double volumeL  = Harvest.yieldToVolume(atPick.potentialYieldKg());
        MustProfile raw = Harvest.pick(atPick, volumeL, vintage.year());

        MustProfile must;
        if (threatEngine != null) {
            // Apply cumulative quality penalty to fruit health (ThreatYearRunner §5)
            double qualPenalty = threatEngine.cumulativeQualityPenalty();
            double adjHealth   = Math.max(0.0, raw.fruitHealth01() - qualPenalty);
            must = new MustProfile(
                    raw.volumeL(), raw.brix(), raw.taGL(), raw.pH(),
                    raw.yanMgL(), raw.tanninRipeness01(), adjHealth,
                    raw.vintageYear());
        } else {
            must = raw;
        }

        // ── Harvest event ─────────────────────────────────────────────────────
        events.add(String.format("day %d: harvested at %.1f Bx", pickDay, must.brix()));

        // ── Fermentation ──────────────────────────────────────────────────────
        CellarResult cellar = fermenter.ferment(must, FERMENT_METHOD, CELLAR_TEMP, TENDING, rng);

        // ── Resolve bottle ────────────────────────────────────────────────────
        WineLot resolved = Resolver.resolve(variety, FERMENT_METHOD, must, cellar, vintage, suitability, LABEL);

        // ── Threat fault override (spec §5) ───────────────────────────────────
        Fault finalFault = cellar.fault();
        if (threatEngine != null
                && faultSeverity(worstThreatFault) > faultSeverity(finalFault)) {
            finalFault = worstThreatFault;
            resolved = new WineLot(
                    resolved.variety(), resolved.style(), resolved.vintageYear(),
                    resolved.volumeL(), resolved.abv(), resolved.quality(),
                    resolved.ageabilityYears(), finalFault,
                    resolved.aroma(), resolved.appellationOk(), resolved.label());
        }

        // ── Enforce events cap (~40 lines) ────────────────────────────────────
        if (events.size() > 40) {
            events = new ArrayList<>(events.subList(0, 40));
        }

        // ── Map to DTOs ───────────────────────────────────────────────────────
        VintageDto vintageDto = new VintageDto(
                vintage.year(),
                vintage.gddSeason(),
                vintage.winkler().name(),
                vintage.patternLabel());

        MustDto mustDto = new MustDto(
                must.volumeL(),
                must.brix(),
                must.taGL(),
                must.pH(),
                must.yanMgL(),
                must.tanninRipeness01(),
                must.fruitHealth01());

        BottleDto bottleDto = new BottleDto(
                resolved.variety().name(),
                resolved.style().name(),
                resolved.vintageYear(),
                resolved.volumeL(),
                resolved.abv(),
                resolved.quality(),
                resolved.ageabilityYears(),
                resolved.fault().name(),
                resolved.appellationOk(),
                resolved.label(),
                resolved.aroma());   // already a SortedMap — deterministic key order

        return new VineyardYearResult(seed, vintageDto, pickDay, suitability, mustDto, bottleDto, events);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private static Region parseRegion(String name) {
        try {
            return Region.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Unknown region: " + name
                    + ". Valid values: " + java.util.Arrays.toString(Region.values()));
        }
    }

    private static Variety parseVariety(String name) {
        try {
            return Variety.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Unknown variety: " + name
                    + ". Valid values: " + java.util.Arrays.toString(Variety.values()));
        }
    }

    private static SoilType parseSoil(String name) {
        try {
            return SoilType.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Unknown soil: " + name
                    + ". Valid values: " + java.util.Arrays.toString(SoilType.values()));
        }
    }

    private static int faultSeverity(Fault f) {
        int idx = FAULT_SEVERITY_ORDER.indexOf(f);
        return idx < 0 ? 0 : idx;
    }

    /** Convert a PhenoStage to a human-readable event label. */
    private static String stageName(PhenoStage stage) {
        return switch (stage) {
            case BUD_SWELL         -> "bud swell";
            case BUDBREAK          -> "budbreak";
            case SHOOT_GROWTH      -> "shoot growth";
            case FLOWERING         -> "flowering";
            case FRUIT_SET         -> "fruit set";
            case BERRY_DEVELOPMENT -> "berry development";
            case VERAISON          -> "véraison";
            case RIPENING          -> "ripening";
            case HARVESTED         -> "harvested";
            case LEAF_FALL         -> "leaf fall";
            default                -> stage.name().toLowerCase().replace('_', ' ');
        };
    }
}
