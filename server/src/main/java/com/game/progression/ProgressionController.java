package com.game.progression;

import com.game.exception.ApiException;
import com.game.market.TokenHelper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller for the progression lane.
 *
 * <p>All endpoints require a valid {@code Authorization: Bearer <token>} header.
 * Character ownership is verified inline via {@link TokenHelper}
 * (which calls {@code AccountTokenService} + {@code CharacterService.getOwned}).
 * Security config already has {@code /api/progression/**} as permitAll.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>GET  /api/progression/{characterId}        → get (or auto-create) the profile</li>
 *   <li>POST /api/progression/{characterId}/award  → award XP and return updated profile</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/progression")
public class ProgressionController {

    private final TokenHelper        tokenHelper;
    private final ProgressionService progressionService;

    public ProgressionController(TokenHelper tokenHelper,
                                  ProgressionService progressionService) {
        this.tokenHelper        = tokenHelper;
        this.progressionService = progressionService;
    }

    // ── GET /api/progression/{characterId} ────────────────────────────────────

    /**
     * Returns the progression profile for the given character.
     * If no profile exists yet it is auto-created at xp=0 / level=1 / reputation=0.
     *
     * @param characterId path variable — target character
     * @param request     servlet request (for bearer token extraction)
     * @return 200 with the {@link ProgressionProfile}
     * @throws ApiException 401 if the token is missing/invalid
     * @throws ApiException 404 if the character is not found or not owned by this account
     */
    @GetMapping("/{characterId}")
    public ResponseEntity<ProgressionProfile> getProfile(
            @PathVariable Long characterId,
            HttpServletRequest request) {

        // Inline bearer auth + ownership check (throws 401 / 404 on failure)
        tokenHelper.requireOwnedCharacter(request, characterId);

        ProgressionProfile profile = progressionService.getOrCreate(characterId);
        return ResponseEntity.ok(profile);
    }

    // ── POST /api/progression/{characterId}/award ─────────────────────────────

    /**
     * Awards XP to the character and returns the updated profile.
     *
     * <p>Request body: {@code { "amount": <long>, "reason": "<string>" }}.
     * Both fields are required. {@code amount} must be &gt; 0 (400 otherwise).
     *
     * @param characterId path variable — target character
     * @param body        JSON body with {@code amount} (long) and {@code reason} (String)
     * @param request     servlet request (for bearer token extraction)
     * @return 200 with the updated {@link ProgressionProfile}
     * @throws ApiException 400 if {@code amount} is missing or &lt;= 0
     * @throws ApiException 401 if the token is missing/invalid
     * @throws ApiException 404 if the character is not found or not owned by this account
     */
    @PostMapping("/{characterId}/award")
    public ResponseEntity<ProgressionProfile> awardXp(
            @PathVariable Long characterId,
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {

        // Inline bearer auth + ownership check
        tokenHelper.requireOwnedCharacter(request, characterId);

        // Extract and validate amount
        Object rawAmount = body.get("amount");
        if (rawAmount == null) {
            throw ApiException.badRequest("Request body must contain 'amount'");
        }
        long amount;
        try {
            amount = ((Number) rawAmount).longValue();
        } catch (ClassCastException e) {
            throw ApiException.badRequest("'amount' must be a numeric value");
        }

        String reason = body.containsKey("reason") ? String.valueOf(body.get("reason")) : "";

        // Service validates amount > 0 and throws ApiException BAD_REQUEST if not
        ProgressionProfile updated = progressionService.awardXp(characterId, amount, reason);
        return ResponseEntity.ok(updated);
    }
}
