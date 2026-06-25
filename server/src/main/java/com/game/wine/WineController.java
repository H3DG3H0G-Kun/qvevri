package com.game.wine;

import com.game.account.AccountTokenService;
import com.game.character.CharacterService;
import com.game.exception.ApiException;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for the winemaking-depth v1 endpoints (BACKEND-DEPTH-SPEC §6,
 * WINE lane).
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code POST /api/wine/ferment/start} — start fermentation on a CellarItem</li>
 *   <li>{@code GET  /api/wine/ferment/{cellarItemId}/status} — current fermentation
 *       status (auto-transitions FERMENTING→READY when the clock has passed)</li>
 *   <li>{@code POST /api/wine/ferment/bottle} — bottle a READY item (locks quality)</li>
 * </ul>
 *
 * <p>All endpoints are {@code permitAll} at the Spring Security level; auth is
 * enforced inline here via {@link AccountTokenService} (bearer token pattern,
 * consistent with other estate/market controllers). SecurityConfig already
 * lists {@code /api/wine/**} as permitAll — this controller must NOT edit it.
 *
 * <p>The existing {@code /api/vineyards/{id}/harvest} endpoint is untouched by
 * this controller.
 */
@RestController
@RequestMapping("/api/wine")
public class WineController {

    private final WineMakingService    wineMakingService;
    private final AccountTokenService  tokenService;
    private final CharacterService     characterService;

    public WineController(WineMakingService wineMakingService,
                          AccountTokenService tokenService,
                          CharacterService characterService) {
        this.wineMakingService = wineMakingService;
        this.tokenService      = tokenService;
        this.characterService  = characterService;
    }

    // ── POST /api/wine/ferment/start ──────────────────────────────────────────

    /**
     * Starts fermentation on an existing CellarItem.
     *
     * <p>Request body: {@link StartFermentRequest}
     * <ul>
     *   <li>{@code characterId} — owning character (required)</li>
     *   <li>{@code cellarItemId} — the CellarItem to ferment (required)</li>
     *   <li>{@code vesselGoodId} — OwnedGood vessel to use (optional; null = no vessel)</li>
     * </ul>
     *
     * @return 200 {@link FermentStatusView} after starting fermentation
     */
    @PostMapping("/ferment/start")
    public ResponseEntity<FermentStatusView> startFermentation(
            @RequestBody StartFermentRequest req,
            HttpServletRequest http) {

        long accountId   = requireAccount(http);
        long characterId = requireCharacterId(req.getCharacterId());
        requireOwnership(characterId, accountId);

        if (req.getCellarItemId() == null) {
            throw ApiException.badRequest("cellarItemId is required");
        }

        FermentStatusView view = wineMakingService.startFermentation(
                req.getCellarItemId(), characterId, req.getVesselGoodId());

        return ResponseEntity.ok(view);
    }

    // ── GET /api/wine/ferment/{cellarItemId}/status ────────────────────────────

    /**
     * Returns the current fermentation status for a CellarItem.
     *
     * <p>Automatically transitions FERMENTING → READY when the world-clock day has
     * passed {@code fermentReadyDay}.
     *
     * @param cellarItemId  path variable — the CellarItem to inspect
     * @param characterId   query parameter — owning character (ownership check)
     */
    @GetMapping("/ferment/{cellarItemId}/status")
    public ResponseEntity<FermentStatusView> getStatus(
            @PathVariable long cellarItemId,
            @RequestParam long characterId,
            HttpServletRequest http) {

        long accountId = requireAccount(http);
        requireOwnership(characterId, accountId);

        FermentStatusView view = wineMakingService.getStatus(cellarItemId, characterId);
        return ResponseEntity.ok(view);
    }

    // ── POST /api/wine/ferment/bottle ──────────────────────────────────────────

    /**
     * Bottles a READY CellarItem, locking its quality at the current aged value.
     *
     * <p>Request body: {@link BottleRequest}
     * <ul>
     *   <li>{@code characterId} — owning character</li>
     *   <li>{@code cellarItemId} — the CellarItem to bottle (must be READY)</li>
     * </ul>
     *
     * @return 200 {@link FermentStatusView} with state=BOTTLED
     */
    @PostMapping("/ferment/bottle")
    public ResponseEntity<FermentStatusView> bottle(
            @RequestBody BottleRequest req,
            HttpServletRequest http) {

        long accountId   = requireAccount(http);
        long characterId = requireCharacterId(req.getCharacterId());
        requireOwnership(characterId, accountId);

        if (req.getCellarItemId() == null) {
            throw ApiException.badRequest("cellarItemId is required");
        }

        FermentStatusView view = wineMakingService.bottle(req.getCellarItemId(), characterId);
        return ResponseEntity.ok(view);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private long requireAccount(HttpServletRequest http) {
        String header = http.getHeader("Authorization");
        String token  = (header != null && header.startsWith("Bearer "))
                ? header.substring(7) : null;
        return tokenService.accountIdFor(token)
                .orElseThrow(() -> new ApiException("UNAUTHORIZED",
                        "Missing or invalid bearer token", HttpStatus.UNAUTHORIZED));
    }

    private long requireCharacterId(Long characterId) {
        if (characterId == null) {
            throw ApiException.badRequest("characterId is required");
        }
        return characterId;
    }

    private void requireOwnership(long characterId, long accountId) {
        characterService.getOwned(characterId, accountId)
                .orElseThrow(() -> ApiException.notFound(
                        "Character " + characterId + " not found for this account"));
    }
}
