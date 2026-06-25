package com.game.tourism;

import com.game.account.AccountTokenService;
import com.game.character.CharacterService;
import com.game.exception.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller for the Tourism lane — {@code /api/tourism/**}.
 *
 * <p>All endpoints use inline bearer-token authentication (extract from
 * {@code Authorization: Bearer <token>} header, resolve via
 * {@link AccountTokenService}, verify character ownership via
 * {@link CharacterService#getOwned}). {@code /api/tourism/**} is already
 * {@code permitAll} in {@code SecurityConfig}.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>GET  /api/tourism/{characterId}        — income snapshot (accrued but not paid)</li>
 *   <li>POST /api/tourism/{characterId}/claim  — pay accrued income to wallet</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/tourism")
public class TourismController {

    private final AccountTokenService accountTokenService;
    private final CharacterService    characterService;
    private final TourismService      tourismService;

    public TourismController(AccountTokenService accountTokenService,
                              CharacterService characterService,
                              TourismService tourismService) {
        this.accountTokenService = accountTokenService;
        this.characterService    = characterService;
        this.tourismService      = tourismService;
    }

    // ── GET /api/tourism/{characterId} ────────────────────────────────────────

    /**
     * Returns the current tourism income snapshot for the character.
     * Lazy-creates a ledger if none exists (accruedSoFar will be 0 until sim-days pass).
     *
     * <p>Response body:
     * <pre>{@code
     * {
     *   "lastClaimDay":      0,
     *   "buildingsCount":    2,
     *   "currentRatePerDay": 4.0,
     *   "accruedSoFar":      40.0
     * }
     * }</pre>
     *
     * @return 200 with {@link TourismSnapshot}
     */
    @GetMapping("/{characterId}")
    public ResponseEntity<TourismSnapshot> getSnapshot(
            @PathVariable Long characterId,
            HttpServletRequest request) {

        requireOwnedCharacter(request, characterId);
        return ResponseEntity.ok(tourismService.getSnapshot(characterId));
    }

    // ── POST /api/tourism/{characterId}/claim ─────────────────────────────────

    /**
     * Claims all accrued tourism income, crediting the character's wallet.
     *
     * <p>Request body: {@code {}} (empty JSON object).
     *
     * <p>Response body:
     * <pre>{@code
     * {
     *   "paid":         40.0,
     *   "walletGel":   140.0,
     *   "lastClaimDay": 10
     * }
     * }</pre>
     *
     * @return 200 with {@link TourismClaimResult}
     */
    @PostMapping("/{characterId}/claim")
    public ResponseEntity<TourismClaimResult> claim(
            @PathVariable Long characterId,
            @RequestBody(required = false) Map<String, Object> body,
            HttpServletRequest request) {

        requireOwnedCharacter(request, characterId);
        return ResponseEntity.ok(tourismService.claim(characterId));
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
     *                      not found or not owned by this account
     */
    private void requireOwnedCharacter(HttpServletRequest request, Long characterId) {
        Long accountId = requireAccountId(request);
        characterService.getOwned(characterId, accountId)
                .orElseThrow(() -> ApiException.notFound(
                        "Character " + characterId
                        + " not found or not owned by this account"));
    }
}
