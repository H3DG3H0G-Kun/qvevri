package com.game.estate;

import com.game.account.AccountTokenService;
import com.game.character.CharacterService;
import com.game.core.data.Region;
import com.game.core.data.Variety;
import com.game.exception.ApiException;
import com.game.market.CellarItem;
import com.game.market.CellarItemRepository;
import com.game.wine.HarvestWinemakingHook;
import com.game.world.clock.WorldClockService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST endpoints for player-owned, world-clock-driven vineyards
 * (WORLD-CLOCK-SPEC §4). Bearer token resolves to an account; the supplied
 * characterId must belong to that account.
 *
 * <p>All endpoints are permitted at the Spring Security level; auth is enforced
 * inline here via {@link AccountTokenService}.
 */
@RestController("estateVineyardController")
@RequestMapping("/api/vineyards")
public class VineyardController {

    private static final int DEFAULT_BUD_LOAD = 12;

    private final VineyardRepository vineyardRepository;
    private final VineyardReplayService replayService;
    private final WorldClockService clock;
    private final CharacterService characterService;
    private final AccountTokenService tokenService;
    private final CellarItemRepository cellarItemRepository;
    private final VineyardActionRepository actionRepository;
    private final HarvestWinemakingHook harvestHook;

    public VineyardController(VineyardRepository vineyardRepository,
                             VineyardReplayService replayService,
                             WorldClockService clock,
                             CharacterService characterService,
                             AccountTokenService tokenService,
                             CellarItemRepository cellarItemRepository,
                             VineyardActionRepository actionRepository,
                             HarvestWinemakingHook harvestHook) {
        this.vineyardRepository = vineyardRepository;
        this.replayService = replayService;
        this.clock = clock;
        this.characterService = characterService;
        this.tokenService = tokenService;
        this.cellarItemRepository = cellarItemRepository;
        this.actionRepository = actionRepository;
        this.harvestHook = harvestHook;
    }

    // ── Plant ───────────────────────────────────────────────────────────────

    @PostMapping
    public ResponseEntity<Vineyard> plant(@Valid @RequestBody CreateVineyardRequest req,
                                          HttpServletRequest http) {
        long accountId = requireAccount(http);
        long characterId = requireCharacterId(req.getCharacterId());
        requireOwnership(characterId, accountId);

        Region region   = parseRegion(req.getRegion());
        Variety variety = parseVariety(req.getVariety());
        long seed = (req.getSeed() != null) ? req.getSeed() : deriveSeed(characterId);
        int budLoad = (req.getBudLoad() != null && req.getBudLoad() > 0)
                ? req.getBudLoad() : DEFAULT_BUD_LOAD;

        // Set plantedYear so the establishment curve activates from the first season.
        // The Vineyard constructor leaves plantedYear null (= mature/existing rows),
        // so we set it post-construct here to avoid breaking the existing constructor.
        Vineyard v = new Vineyard(characterId, region, variety, seed, budLoad);
        v.setPlantedYear(clock.currentYear());

        // §6 gated: apply certified vine stock ownRoots override if the character
        // owns a matching VINE_STOCK good. No stock owned → default ownRoots stands.
        harvestHook.applyVineStock(v, characterId);

        Vineyard saved = vineyardRepository.save(v);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    // ── List (state computed at the current world day) ───────────────────────

    @GetMapping("/{characterId}")
    public List<VineyardView> listForCharacter(@PathVariable long characterId,
                                               HttpServletRequest http) {
        long accountId = requireAccount(http);
        requireOwnership(characterId, accountId);

        int year = clock.currentYear();
        int day  = clock.currentDayOfYear();
        return vineyardRepository.findByOwnerCharacterId(characterId).stream()
                .map(v -> {
                    List<VineyardAction> actions =
                            actionRepository.findByVineyardIdAndYearOrderByDayOfYearAsc(v.getId(), year);
                    return replayService.viewAt(v, year, day, actions);
                })
                .toList();
    }

    // ── Detail ────────────────────────────────────────────────────────────────

    @GetMapping("/detail/{vineyardId}")
    public VineyardView detail(@PathVariable long vineyardId, HttpServletRequest http) {
        long accountId = requireAccount(http);
        Vineyard v = vineyardRepository.findById(vineyardId)
                .orElseThrow(() -> notFound("Vineyard not found: " + vineyardId));
        requireOwnership(v.getOwnerCharacterId(), accountId);
        int year = clock.currentYear();
        int day  = clock.currentDayOfYear();
        List<VineyardAction> actions =
                actionRepository.findByVineyardIdAndYearOrderByDayOfYearAsc(vineyardId, year);
        return replayService.viewAt(v, year, day, actions);
    }

    // ── Management plan ──────────────────────────────────────────────────────

    /**
     * GET /api/vineyards/{vineyardId}/management
     * Returns the current management plan (lever snapshot) for the vineyard.
     * Bearer token → account; vineyard must be owned by a character of that account.
     */
    @GetMapping("/{vineyardId}/management")
    public ManagementPlanDto getManagement(@PathVariable long vineyardId,
                                           HttpServletRequest http) {
        long accountId = requireAccount(http);
        Vineyard v = vineyardRepository.findById(vineyardId)
                .orElseThrow(() -> notFound("Vineyard not found: " + vineyardId));
        requireOwnership(v.getOwnerCharacterId(), accountId);
        return ManagementPlanDto.from(v);
    }

    /**
     * POST /api/vineyards/{vineyardId}/manage
     * Applies a partial management plan update. Any non-null lever field in the
     * request replaces the stored value; null fields are left unchanged.
     * Validates ranges (doubles 0..1; budLoad 1..40) — 400 on violation.
     * Persists and returns the updated VineyardView recomputed at the current world day.
     */
    @PostMapping("/{vineyardId}/manage")
    public VineyardView manage(@PathVariable long vineyardId,
                               @RequestBody ManageVineyardRequest req,
                               HttpServletRequest http) {
        long accountId  = requireAccount(http);
        long characterId = requireCharacterId(req.getCharacterId());
        requireOwnership(characterId, accountId);

        Vineyard v = vineyardRepository.findByIdAndOwnerCharacterId(vineyardId, characterId)
                .orElseThrow(() -> notFound(
                        "Vineyard " + vineyardId + " not found for character " + characterId));

        // ── Validate and apply levers ───────────────────────────────────────
        if (req.getBudLoad() != null) {
            int bl = req.getBudLoad();
            if (bl < 1 || bl > 40) {
                throw new ApiException("BAD_REQUEST",
                        "budLoad must be 1..40, got " + bl, HttpStatus.BAD_REQUEST);
            }
            v.setBudLoad(bl);
        }
        if (req.getOwnRoots() != null)         v.setOwnRoots(req.getOwnRoots());
        if (req.getCanopyOpenness01() != null) {
            double val = req.getCanopyOpenness01();
            if (val < 0.0 || val > 1.0) throw badDouble("canopyOpenness01", val);
            v.setCanopyOpenness01(val);
        }
        if (req.getLeafPulled() != null)       v.setLeafPulled(req.getLeafPulled());
        if (req.getCopperSpray01() != null) {
            double val = req.getCopperSpray01();
            if (val < 0.0 || val > 1.0) throw badDouble("copperSpray01", val);
            v.setCopperSpray01(val);
        }
        if (req.getSulfurSpray01() != null) {
            double val = req.getSulfurSpray01();
            if (val < 0.0 || val > 1.0) throw badDouble("sulfurSpray01", val);
            v.setSulfurSpray01(val);
        }
        if (req.getNetting() != null)          v.setNetting(req.getNetting());
        if (req.getGuardDog() != null)         v.setGuardDog(req.getGuardDog());
        if (req.getFalcons() != null)          v.setFalcons(req.getFalcons());
        if (req.getCats() != null)             v.setCats(req.getCats());
        if (req.getDucks() != null)            v.setDucks(req.getDucks());
        if (req.getCoverCrop01() != null) {
            double val = req.getCoverCrop01();
            if (val < 0.0 || val > 1.0) throw badDouble("coverCrop01", val);
            v.setCoverCrop01(val);
        }

        vineyardRepository.save(v);

        int year = clock.currentYear();
        int day  = clock.currentDayOfYear();
        List<VineyardAction> actions =
                actionRepository.findByVineyardIdAndYearOrderByDayOfYearAsc(vineyardId, year);
        return replayService.viewAt(v, year, day, actions);
    }

    // ── Per-day action ────────────────────────────────────────────────────────

    /**
     * POST /api/vineyards/{id}/action
     * Records a tending action for the current world-clock year and returns the
     * recomputed VineyardView with all actions for that year applied.
     *
     * <p>Auth: same bearer-token + ownership pattern as /manage.
     *
     * <p>Supported actionTypes: EMERGENCY_COPPER_SPRAY, EMERGENCY_SULFUR_SPRAY,
     * EMERGENCY_NETTING. Unknown types are accepted and stored (forward-compatible)
     * but silently ignored during replay.
     */
    @PostMapping("/{vineyardId}/action")
    public VineyardView recordAction(@PathVariable long vineyardId,
                                     @RequestBody VineyardActionRequest req,
                                     HttpServletRequest http) {
        long accountId   = requireAccount(http);
        long characterId = requireCharacterId(req.getCharacterId());
        requireOwnership(characterId, accountId);

        Vineyard v = vineyardRepository.findByIdAndOwnerCharacterId(vineyardId, characterId)
                .orElseThrow(() -> notFound(
                        "Vineyard " + vineyardId + " not found for character " + characterId));

        // Validate inputs
        if (req.getDayOfYear() == null || req.getDayOfYear() < 0 || req.getDayOfYear() > 364) {
            throw new ApiException("BAD_REQUEST",
                    "dayOfYear must be 0..364", HttpStatus.BAD_REQUEST);
        }
        if (req.getActionType() == null || req.getActionType().isBlank()) {
            throw new ApiException("BAD_REQUEST",
                    "actionType is required", HttpStatus.BAD_REQUEST);
        }
        if (req.getValue() == null) {
            throw new ApiException("BAD_REQUEST",
                    "value is required", HttpStatus.BAD_REQUEST);
        }

        int year = clock.currentYear();
        int day  = clock.currentDayOfYear();

        // Persist the action
        VineyardAction action = new VineyardAction(
                vineyardId, year, req.getDayOfYear(),
                req.getActionType(), req.getValue());
        actionRepository.save(action);

        // Load all actions for this (vineyard, year) and replay
        List<VineyardAction> actions =
                actionRepository.findByVineyardIdAndYearOrderByDayOfYearAsc(vineyardId, year);

        return replayService.viewAt(v, year, day, actions);
    }

    // ── Harvest ─────────────────────────────────────────────────────────────

    @PostMapping("/{vineyardId}/harvest")
    public HarvestOutcome harvest(@PathVariable long vineyardId,
                                  @Valid @RequestBody HarvestRequest req,
                                  HttpServletRequest http) {
        long accountId = requireAccount(http);
        long characterId = requireCharacterId(req.getCharacterId());
        requireOwnership(characterId, accountId);

        Vineyard v = vineyardRepository.findByIdAndOwnerCharacterId(vineyardId, characterId)
                .orElseThrow(() -> notFound(
                        "Vineyard " + vineyardId + " not found for character " + characterId));

        int year = clock.currentYear();
        int day  = clock.currentDayOfYear();

        // Load actions for this season so ripeness check + harvest are consistent
        List<VineyardAction> actions =
                actionRepository.findByVineyardIdAndYearOrderByDayOfYearAsc(vineyardId, year);

        VineyardView view = replayService.viewAt(v, year, day, actions);
        if (view.alreadyHarvestedThisYear()) {
            throw new ApiException("ALREADY_HARVESTED",
                    "This vineyard has already been harvested in year " + year,
                    HttpStatus.BAD_REQUEST);
        }
        if (!view.ripe()) {
            throw new ApiException("NOT_RIPE",
                    "The fruit is not ripe yet (stage " + view.stage()
                            + ", brix " + String.format("%.1f", view.brix()) + ")",
                    HttpStatus.BAD_REQUEST);
        }

        HarvestOutcome outcome = replayService.harvest(v, year, day, actions);

        // §6 gated: apply press-tier quality nudge and consume spray goods.
        // Each effect is individually gated — absent equipment → zero delta → same output.
        CellarItem harvested = outcome.cellarItem();
        harvestHook.apply(v, characterId, harvested);

        // Persist the bottle into the owner's cellar.
        CellarItem savedItem = cellarItemRepository.save(harvested);

        // Mark harvested for this season so it can't be double-harvested.
        v.setLastHarvestedYear(year);
        vineyardRepository.save(v);

        return new HarvestOutcome(savedItem, outcome.bottle(), outcome.vineyardView());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private long requireAccount(HttpServletRequest http) {
        String header = http.getHeader("Authorization");
        String token = (header != null && header.startsWith("Bearer "))
                ? header.substring(7) : null;
        return tokenService.accountIdFor(token)
                .orElseThrow(() -> new ApiException("UNAUTHORIZED",
                        "Missing or invalid bearer token", HttpStatus.UNAUTHORIZED));
    }

    private long requireCharacterId(Long characterId) {
        if (characterId == null) {
            throw new ApiException("BAD_REQUEST", "characterId is required", HttpStatus.BAD_REQUEST);
        }
        return characterId;
    }

    /** Verify the character belongs to the authenticated account, else 404. */
    private void requireOwnership(long characterId, long accountId) {
        characterService.getOwned(characterId, accountId)
                .orElseThrow(() -> notFound(
                        "Character " + characterId + " not found for this account"));
    }

    private static ApiException notFound(String msg) {
        return new ApiException("NOT_FOUND", msg, HttpStatus.NOT_FOUND);
    }

    private static ApiException badDouble(String field, double val) {
        return new ApiException("BAD_REQUEST",
                field + " must be 0.0..1.0, got " + val, HttpStatus.BAD_REQUEST);
    }

    private static Region parseRegion(String name) {
        try {
            return Region.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ApiException("BAD_REQUEST", "Unknown region: " + name, HttpStatus.BAD_REQUEST);
        }
    }

    private static Variety parseVariety(String name) {
        try {
            return Variety.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ApiException("BAD_REQUEST", "Unknown variety: " + name, HttpStatus.BAD_REQUEST);
        }
    }

    /** Deterministic-enough seed when the client does not supply one. */
    private static long deriveSeed(long characterId) {
        return (characterId * 0x9E3779B97F4A7C15L) ^ System.nanoTime();
    }
}
