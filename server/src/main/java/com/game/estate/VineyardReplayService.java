package com.game.estate;

import com.game.core.data.*;
import com.game.core.time.RngStreams;
import com.game.core.weather.KakhetiWeatherModel;
import com.game.econ.ItemType;
import com.game.market.CellarItem;
import com.game.sim.cellar.CellarResult;
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

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Deterministic replay service for persistent vineyards.
 *
 * <p>Mirrors the per-day composition of {@link com.game.vineyard.VineyardService} and
 * the {@code ThreatYearRunner} harness exactly, but only runs from day 0 to the
 * requested {@code dayOfYear} instead of the full 365-day year.
 *
 * <p>Because the sim is fully deterministic from (seed, region, year), the same
 * {@code (vineyard, year, dayOfYear)} triple always produces the same {@link VineyardView}.
 * No vine state is ever persisted; only the vineyard config is stored.
 *
 * <p>All collaborators are stateless and thread-safe (per Spring idiom).
 */
@Service
public class VineyardReplayService {

    // ── Cellar constants (mirror VineyardService / YearRunner) ───────────────
    private static final FermentMethod FERMENT_METHOD = FermentMethod.RED;
    private static final double        CELLAR_TEMP    = 25.0;
    private static final double        TENDING        = 0.80;
    private static final String        LABEL          = "Glekhi's First Saperavi";

    /** Brix threshold for the 'ripe' flag (WORLD-CLOCK-SPEC §3). */
    private static final double RIPE_BRIX_THRESHOLD = 22.0;

    /** How many recent events to keep in the VineyardView (spec: last ~8). */
    private static final int RECENT_EVENTS_WINDOW = 8;

    // ── Fault severity order (mirrors VineyardService) ────────────────────────
    private static final List<Fault> FAULT_SEVERITY_ORDER = List.of(
            Fault.NONE, Fault.OXIDATION, Fault.REDUCTION_H2S,
            Fault.VOLATILE_ACIDITY, Fault.STUCK_FERMENT);

    // ── Stateless collaborators ───────────────────────────────────────────────
    private final KakhetiWeatherModel weatherModel;
    private final KineticFermenter fermenter;

    public VineyardReplayService() {
        this.weatherModel  = new KakhetiWeatherModel();
        this.fermenter     = new KineticFermenter();
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Replay the vineyard simulation from day 0 to {@code dayOfYear} (inclusive)
     * for the given world-clock year and return the current state as a view DTO.
     * Uses no per-day actions (empty list).
     *
     * <p>Pure and deterministic: same inputs always produce the same output.
     *
     * @param v         the persistent vineyard config
     * @param year      world-clock year (1-based)
     * @param dayOfYear day within the year (0..364)
     * @return a snapshot of the vine state on the requested day
     */
    public VineyardView viewAt(Vineyard v, int year, int dayOfYear) {
        return viewAt(v, year, dayOfYear, Collections.emptyList());
    }

    /**
     * Replay the vineyard simulation from day 0 to {@code dayOfYear} (inclusive),
     * applying the given {@code actions} causally during the loop. An action on
     * day D overrides the relevant lever for all days >= D.
     *
     * <p>Empty {@code actions} list produces output identical to
     * {@link #viewAt(Vineyard, int, int)} (byte-identical with the no-action path).
     *
     * @param v         the persistent vineyard config
     * @param year      world-clock year (1-based)
     * @param dayOfYear day within the year (0..364)
     * @param actions   per-day tending actions for this (vineyard, year); may be empty
     * @return a snapshot of the vine state on the requested day
     */
    public VineyardView viewAt(Vineyard v, int year, int dayOfYear,
                                List<VineyardAction> actions) {
        ReplayResult result = replay(v, year, dayOfYear, actions);
        VineState state = result.finalState();

        boolean ripe = (state.stage() == PhenoStage.RIPENING)
                && (state.brix() >= RIPE_BRIX_THRESHOLD);
        boolean alreadyHarvested = (v.getLastHarvestedYear() == year);

        // Keep only the last RECENT_EVENTS_WINDOW entries
        List<String> events = result.events();
        if (events.size() > RECENT_EVENTS_WINDOW) {
            events = new ArrayList<>(events.subList(events.size() - RECENT_EVENTS_WINDOW, events.size()));
        }

        // ── Age / establishment yield multiplier ───────────────────────────────
        // Applied AFTER the sim computes potentialYieldKg so the sim itself is
        // untouched. For null plantedYear (legacy rows / tests) multiplier = 1.0
        // exactly, preserving byte-identical output for the mature default case.
        double yieldKg = state.potentialYieldKg();
        if (v.getPlantedYear() != null) {
            int vineAge = year - v.getPlantedYear() + 1; // age in years (year 1 = first season)
            double ageMult = establishmentMultiplier(vineAge);
            yieldKg = yieldKg * ageMult;
        }

        return new VineyardView(
                v.getId(),
                v.getOwnerCharacterId(),
                v.getRegion(),
                v.getVariety(),
                year,
                dayOfYear,
                state.stage(),
                state.brix(),
                state.taGL(),
                state.pH(),
                state.healthFraction(),
                yieldKg,
                ripe,
                alreadyHarvested,
                List.copyOf(events)
        );
    }

    /**
     * Replay to the current day, apply Harvest.pick + ferment + resolve and
     * return the resulting {@link HarvestOutcome}.
     *
     * <p>This method does NOT save the CellarItem — the controller is responsible
     * for persisting it (and updating {@code lastHarvestedYear}).
     *
     * @param v         the persistent vineyard config
     * @param year      world-clock year (1-based)
     * @param dayOfYear day within the year (0..364)
     * @return the full harvest outcome including the WineLot
     */
    public HarvestOutcome harvest(Vineyard v, int year, int dayOfYear) {
        return harvest(v, year, dayOfYear, Collections.emptyList());
    }

    /**
     * Replay to the current day (with per-day actions applied), apply Harvest.pick +
     * ferment + resolve and return the resulting {@link HarvestOutcome}.
     *
     * @param v         the persistent vineyard config
     * @param year      world-clock year (1-based)
     * @param dayOfYear day within the year (0..364)
     * @param actions   per-day tending actions for this (vineyard, year); may be empty
     * @return the full harvest outcome including the WineLot
     */
    public HarvestOutcome harvest(Vineyard v, int year, int dayOfYear,
                                   List<VineyardAction> actions) {
        ReplayResult result = replay(v, year, dayOfYear, actions);
        VineState atPick = result.finalState();

        // ── Age / establishment yield multiplier (harvest path) ───────────────
        // Same guard as viewAt: null plantedYear → mature → multiplier 1.0 exactly.
        double yieldKgForHarvest = atPick.potentialYieldKg();
        if (v.getPlantedYear() != null) {
            int vineAge = year - v.getPlantedYear() + 1;
            yieldKgForHarvest = yieldKgForHarvest * establishmentMultiplier(vineAge);
        }

        // ── Harvest ───────────────────────────────────────────────────────────
        double volumeL = Harvest.yieldToVolume(yieldKgForHarvest);
        MustProfile raw = Harvest.pick(atPick, volumeL, year);

        // ── Apply cumulative quality penalty from threat engine ────────────────
        ThreatEngine threatEngine = result.threatEngine();
        MustProfile must;
        if (threatEngine != null) {
            double qualPenalty = threatEngine.cumulativeQualityPenalty();
            double adjHealth   = Math.max(0.0, raw.fruitHealth01() - qualPenalty);
            must = new MustProfile(
                    raw.volumeL(), raw.brix(), raw.taGL(), raw.pH(),
                    raw.yanMgL(), raw.tanninRipeness01(), adjHealth,
                    raw.vintageYear());
        } else {
            must = raw;
        }

        // ── Fermentation ──────────────────────────────────────────────────────
        RngStreams rng = new RngStreams(v.getSeed());
        // Re-advance the RNG to the same state as replay used (re-seeded fresh for harvest)
        // Per spec §0: the ferment uses the same master seed; the named stream "cellar.ferment"
        // is isolated, so a fresh RngStreams from the same seed is deterministic.
        CellarResult cellar = fermenter.ferment(must, FERMENT_METHOD, CELLAR_TEMP, TENDING, rng);

        // ── Resolve bottle ────────────────────────────────────────────────────
        SiteProfile site = RegionSiteProfiles.of(v.getRegion());
        double suitability = SiteSuitability.score(v.getVariety(), site);

        // ── Threat fault override (mirrors VineyardService §5) ────────────────
        Fault worstThreatFault = result.worstThreatFault();
        Fault finalFault = cellar.fault();
        WineLot resolved = Resolver.resolve(
                v.getVariety(), FERMENT_METHOD, must, cellar, result.vintage(), suitability, LABEL);

        if (threatEngine != null
                && faultSeverity(worstThreatFault) > faultSeverity(finalFault)) {
            finalFault = worstThreatFault;
            resolved = new WineLot(
                    resolved.variety(), resolved.style(), resolved.vintageYear(),
                    resolved.volumeL(), resolved.abv(), resolved.quality(),
                    resolved.ageabilityYears(), finalFault,
                    resolved.aroma(), resolved.appellationOk(), resolved.label());
        }

        // ── Build CellarItem (not yet persisted) ──────────────────────────────
        CellarItem cellarItem = new CellarItem(
                v.getOwnerCharacterId(),
                ItemType.AGED_WINE.name(),
                resolved.volumeL(),
                resolved.quality(),
                resolved.vintageYear(),
                resolved.style().name(),
                resolved.appellationOk(),
                resolved.label()
        );

        // ── View at harvest day ───────────────────────────────────────────────
        VineyardView view = viewAt(v, year, dayOfYear, actions);

        return new HarvestOutcome(cellarItem, resolved, view);
    }

    // ── Private: core replay loop ─────────────────────────────────────────────

    /**
     * Run the daily sim loop from day 0 up to and including {@code targetDay},
     * applying per-day {@code actions} causally (action on day D overrides the
     * relevant lever for all days >= D). An empty action list is equivalent to
     * the old signature — output is byte-identical for the no-action path.
     *
     * <p>Mirrors VineyardService's main loop exactly. Actions are pre-sorted by
     * dayOfYear (the repository query guarantees this; callers in tests must also
     * sort or pass already-sorted lists).
     */
    private ReplayResult replay(Vineyard v, int year, int targetDay,
                                 List<VineyardAction> actions) {
        long seed    = v.getSeed();
        int  budLoad = v.getBudLoad();
        Region region   = v.getRegion()   != null ? v.getRegion()   : Region.KAKHETI;
        Variety variety = v.getVariety()  != null ? v.getVariety()  : Variety.SAPERAVI;

        // ── RNG ───────────────────────────────────────────────────────────────
        RngStreams rng = new RngStreams(seed);

        // ── Weather ───────────────────────────────────────────────────────────
        List<DailyWeather> days = weatherModel.generateYear(rng, region, year);
        Vintage vintage         = weatherModel.rollVintage(rng, region, year, days);

        // ── Site / suitability ────────────────────────────────────────────────
        SiteProfile site    = RegionSiteProfiles.of(region);
        KakhetiVineSimulator vineSimulator = new KakhetiVineSimulator(variety);
        double suitability  = SiteSuitability.score(variety, site);
        PruningDecision pruning = new PruningDecision(budLoad);

        // ── Threat engine (always-on for owned vineyards per spec §3) ────────
        ThreatEngine threatEngine = new ThreatEngine(ThreatRegistry.all());

        // ── Threat levers — read from the vineyard's management plan ─────────
        // (MANAGE-SPEC §4: stop using hardcoded defaults; read from entity.
        //  Default field values on Vineyard match the old hardcoded constants,
        //  so the default-lever path remains byte-identical.)
        // These are the season-baseline values; per-day actions may override them
        // from the action's dayOfYear onwards (GOODS-ECON-SPEC LANE-M).
        boolean ownRoots         = v.isOwnRoots();
        double  canopyOpenness01 = v.getCanopyOpenness01();
        boolean leafPulled       = v.isLeafPulled();
        double  copperSpray01    = v.getCopperSpray01();
        double  sulfurSpray01    = v.getSulfurSpray01();
        boolean netting          = v.isNetting();
        boolean guardDog         = v.isGuardDog();
        boolean falcons          = v.isFalcons();
        boolean cats             = v.isCats();
        boolean ducks            = v.isDucks();
        double  coverCrop01      = v.getCoverCrop01();

        // ── Per-day action cursor (sorted by dayOfYear ascending) ─────────────
        // We walk through the sorted action list in parallel with the day loop.
        // When we reach a day >= action.dayOfYear we apply its override and
        // advance the cursor — purely forward, no backtracking.
        int actionCursor = 0;
        int actionCount  = (actions != null) ? actions.size() : 0;

        // ── Initial vine state (mirrors VineyardService / YearRunner) ─────────
        VineState state = new VineState(
                PhenoStage.DORMANCY, 0.0, 1.0, 0.0, 0.0, 8.0, 3.0, 200.0, 0.0);
        PhenoStage lastStage = state.stage();

        List<String> events         = new ArrayList<>();
        Fault worstThreatFault      = Fault.NONE;

        // ── Daily loop: 0..targetDay ──────────────────────────────────────────
        int limit = Math.min(targetDay, 364);
        for (int d = 0; d <= limit; d++) {
            DailyWeather today = days.get(d);

            // ── Apply any actions whose dayOfYear <= d (causal override) ───────
            // Actions are sorted ascending by dayOfYear; once applied they persist
            // for all subsequent days in this loop (causal, deterministic).
            // Empty action list = zero iterations = byte-identical to no-action path.
            while (actionCursor < actionCount) {
                VineyardAction action = actions.get(actionCursor);
                if (action.getDayOfYear() > d) break; // not yet
                switch (action.getActionType()) {
                    case "EMERGENCY_COPPER_SPRAY" ->
                            copperSpray01 = Math.max(0.0, Math.min(1.0, action.getValue()));
                    case "EMERGENCY_SULFUR_SPRAY" ->
                            sulfurSpray01 = Math.max(0.0, Math.min(1.0, action.getValue()));
                    case "EMERGENCY_NETTING"      ->
                            netting = action.getValue() >= 0.5;
                    // Unknown action types are silently skipped (forward-compatible).
                    default -> { /* no-op */ }
                }
                actionCursor++;
            }

            // 1. Vine tick
            state = vineSimulator.tick(state, today, site, suitability, pruning);

            // 2. Threat engine step (after vine tick, per spec §2)
            DayInputs inputs = new DayInputs(
                    today, vintage.gddSeason(), site,
                    ownRoots, canopyOpenness01, leafPulled,
                    copperSpray01, sulfurSpray01, netting,
                    guardDog, falcons, cats, ducks, coverCrop01,
                    rng);
            DayResult dayResult = threatEngine.step(state, inputs);
            state = dayResult.vine();

            ThreatReport report = dayResult.report();
            if (faultSeverity(report.inducedFault()) > faultSeverity(worstThreatFault)) {
                worstThreatFault = report.inducedFault();
            }

            // Collect threat tells
            for (String tell : report.activeTells()) {
                events.add("day " + today.dayOfYear() + ": " + tell);
            }

            // 3. Phenology stage transitions
            if (state.stage() != lastStage) {
                events.add("day " + today.dayOfYear() + ": " + stageName(state.stage()));
                lastStage = state.stage();
            }
        }

        return new ReplayResult(state, events, threatEngine, worstThreatFault, vintage);
    }

    // ── Establishment / age yield curve ───────────────────────────────────────

    /**
     * Returns the yield multiplier for a vine of the given age (years since
     * planting, 1-based: year-1 = first season).
     *
     * <p>Spec (GOODS-ECON-SPEC LANE-M):
     * <ul>
     *   <li>Year 1  → ~0.30 (young vine, low root establishment)</li>
     *   <li>Year 2  → ~0.65 (growing vigour)</li>
     *   <li>Years 3–25 → 1.0 (full production — mature default)</li>
     *   <li>Years 26+ → gentle decline (older vines reduce yield)</li>
     * </ul>
     *
     * <p>CRITICAL: when {@code plantedYear} is {@code null} (the default for all
     * existing rows and test constructions) this method is never called — the
     * multiplier is implicitly 1.0. The mature default path is therefore
     * byte-identical to the pre-multiseason behaviour.
     *
     * <p>For {@code vineAge} in [3, 25] the return value is exactly {@code 1.0}
     * so that plantedYear-set vineyards which have reached maturity also remain
     * byte-identical to unset ones.
     *
     * @param vineAge years since planting (1 = first season)
     * @return yield multiplier in (0, 1]
     */
    static double establishmentMultiplier(int vineAge) {
        if (vineAge <= 0) return 0.10; // defensive: shouldn't happen
        if (vineAge == 1) return 0.30;
        if (vineAge == 2) return 0.65;
        if (vineAge <= 25) return 1.0; // full production — the mature case
        // Gentle decline after peak maturity; very old vines retain ~70%+
        // Formula: 1.0 - 0.01 * (age - 25) clamped to 0.70
        double decline = 1.0 - 0.01 * (vineAge - 25);
        return Math.max(0.70, decline);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private static int faultSeverity(Fault f) {
        int idx = FAULT_SEVERITY_ORDER.indexOf(f);
        return idx < 0 ? 0 : idx;
    }

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

    // ── Private result carrier ─────────────────────────────────────────────────

    /**
     * Internal result of a replay run.
     *
     * @param finalState       vine state at the last simulated day
     * @param events           all event strings collected during the replay
     * @param threatEngine     the (stateful) threat engine after the replay
     * @param worstThreatFault worst induced fault seen during the replay
     * @param vintage          the season vintage summary
     */
    private record ReplayResult(
            VineState finalState,
            List<String> events,
            ThreatEngine threatEngine,
            Fault worstThreatFault,
            Vintage vintage
    ) {}
}
