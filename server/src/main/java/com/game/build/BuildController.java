package com.game.build;

import com.game.account.AccountTokenService;
import com.game.character.CharacterService;
import com.game.exception.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * REST controller for the Buildings lane — {@code /api/build/**}.
 *
 * <p>All endpoints use inline bearer-token authentication.
 * {@code /api/build/**} is already {@code permitAll} in SecurityConfig
 * (inline bearer check is performed here, not by the Spring Security filter).
 *
 * <p>Endpoints:
 * <ul>
 *   <li>GET  /api/build/catalog                     — all BuildingTypes</li>
 *   <li>POST /api/build/construct                    — construct a building</li>
 *   <li>GET  /api/build/{characterId}               — character's buildings</li>
 *   <li>GET  /api/build/bonuses/{characterId}        — aggregated bonuses</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/build")
public class BuildController {

    private final AccountTokenService accountTokenService;
    private final CharacterService    characterService;
    private final BuildService        buildService;

    public BuildController(AccountTokenService accountTokenService,
                           CharacterService characterService,
                           BuildService buildService) {
        this.accountTokenService = accountTokenService;
        this.characterService    = characterService;
        this.buildService        = buildService;
    }

    // ── GET /api/build/catalog ────────────────────────────────────────────────

    /**
     * Returns all four static building type definitions.
     * Auth is validated (token required) but no character ownership check.
     */
    @GetMapping("/catalog")
    public ResponseEntity<Collection<BuildingType>> getCatalog(HttpServletRequest request) {
        requireAccountId(request);
        return ResponseEntity.ok(buildService.getCatalog());
    }

    // ── POST /api/build/construct ─────────────────────────────────────────────

    /**
     * Constructs a new estate building for the character.
     *
     * <p>Request body: {@code {characterId, parcelId?, buildingTypeId}}.
     *
     * <p>Transaction order (see {@link BuildService#construct}):
     * pre-check funds → pre-check goods → debit wallet → consume goods →
     * persist building.
     *
     * @return 200 with the persisted {@link Building}
     */
    @PostMapping("/construct")
    public ResponseEntity<Building> construct(
            @RequestBody ConstructRequest req,
            HttpServletRequest request) {

        if (req.getCharacterId() == null) {
            throw ApiException.badRequest("Missing required field: 'characterId'");
        }
        if (req.getBuildingTypeId() == null || req.getBuildingTypeId().isBlank()) {
            throw ApiException.badRequest("Missing required field: 'buildingTypeId'");
        }

        requireOwnedCharacter(request, req.getCharacterId());

        Building building = buildService.construct(
                req.getCharacterId(),
                req.getParcelId(),
                req.getBuildingTypeId());

        return ResponseEntity.ok(building);
    }

    // ── GET /api/build/{characterId} ──────────────────────────────────────────

    /**
     * Returns all buildings owned by the given character.
     * The requesting account must own the character.
     */
    @GetMapping("/{characterId}")
    public ResponseEntity<List<Building>> getBuildings(
            @PathVariable Long characterId,
            HttpServletRequest request) {

        requireOwnedCharacter(request, characterId);
        return ResponseEntity.ok(buildService.getForCharacter(characterId));
    }

    // ── GET /api/build/bonuses/{characterId} ──────────────────────────────────

    /**
     * Returns a map of bonusType → summed bonusValue across the character's
     * buildings. Other systems (wine pipeline, etc.) can query this endpoint
     * to read production bonuses.
     *
     * <p>Example response: {@code {"WINE_QUALITY":0.05,"STORAGE":50.0}}
     */
    @GetMapping("/bonuses/{characterId}")
    public ResponseEntity<Map<String, Double>> getBonuses(
            @PathVariable Long characterId,
            HttpServletRequest request) {

        requireOwnedCharacter(request, characterId);
        return ResponseEntity.ok(buildService.getBonuses(characterId));
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Extracts and validates the bearer token, returning the accountId.
     *
     * @throws ApiException 401 if the Authorization header is missing or invalid
     */
    private Long requireAccountId(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            throw ApiException.unauthorized("Missing or malformed Authorization header");
        }
        String token = header.substring(7).strip();
        return accountTokenService.accountIdFor(token)
                .orElseThrow(() -> ApiException.unauthorized("Invalid or expired token"));
    }

    /**
     * Verifies that {@code characterId} belongs to the authenticated account.
     *
     * @throws ApiException 401 if the token is invalid, 404 if the character is
     *                      not found or not owned by the account
     */
    private void requireOwnedCharacter(HttpServletRequest request, Long characterId) {
        Long accountId = requireAccountId(request);
        characterService.getOwned(characterId, accountId)
                .orElseThrow(() -> ApiException.notFound(
                        "Character " + characterId
                        + " not found or not owned by this account"));
    }
}
