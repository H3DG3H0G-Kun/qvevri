package com.game.travel;

import com.game.character.Character;
import com.game.exception.ApiException;
import com.game.market.TokenHelper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller for LANE TRAVEL — player character movement between Georgian wine regions.
 *
 * <p>All endpoints require {@code Authorization: Bearer <token>} and verify that the
 * requested {@code characterId} belongs to the authenticated account (via
 * {@link TokenHelper#requireOwnedCharacter}).
 *
 * <h2>Endpoints</h2>
 * <ul>
 *   <li>GET  /api/travel/{characterId}          — current location (lazy-create + lazy-arrive)</li>
 *   <li>POST /api/travel/{characterId}/depart   — initiate travel to another region</li>
 * </ul>
 *
 * <p>SecurityConfig already permits {@code /api/travel/**} — no further config needed.
 */
@RestController
@RequestMapping("/api/travel")
public class TravelController {

    private final TokenHelper   tokenHelper;
    private final TravelService travelService;

    public TravelController(TokenHelper tokenHelper, TravelService travelService) {
        this.tokenHelper   = tokenHelper;
        this.travelService = travelService;
    }

    // ── GET /api/travel/{characterId} ────────────────────────────────────────

    /**
     * Returns the character's current location.
     *
     * <p>Lazy-creates the location at the character's homeRegion (SETTLED) on first
     * access. Also lazily resolves arrival if the character was TRAVELLING and the
     * world clock has advanced past {@code arriveDay}.
     *
     * @param characterId character id (path variable)
     * @return 200 with {@link CharacterLocation}
     */
    @GetMapping("/{characterId}")
    public ResponseEntity<CharacterLocation> getLocation(
            @PathVariable Long characterId,
            HttpServletRequest request) {

        Character character = tokenHelper.requireOwnedCharacter(request, characterId);
        CharacterLocation loc = travelService.getLocation(character);
        return ResponseEntity.ok(loc);
    }

    // ── POST /api/travel/{characterId}/depart ────────────────────────────────

    /**
     * Initiates travel from the character's current region to the given destination.
     *
     * <p>Request body: {@code { "toRegion": "KARTLI" }}
     *
     * <p>Business rules:
     * <ul>
     *   <li>Character must be SETTLED → 400 ALREADY_TRAVELLING otherwise.</li>
     *   <li>{@code toRegion} must be a valid Region enum name → 400 if unknown.</li>
     *   <li>{@code toRegion} must differ from the current region → 400 if same.</li>
     *   <li>Wallet must cover 5 GEL travel cost → 400 INSUFFICIENT_FUNDS if not.</li>
     * </ul>
     *
     * @param characterId character id (path variable)
     * @param body        JSON body containing {@code toRegion}
     * @return 200 with updated {@link CharacterLocation} in TRAVELLING state
     */
    @PostMapping("/{characterId}/depart")
    public ResponseEntity<CharacterLocation> depart(
            @PathVariable Long characterId,
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {

        Character character = tokenHelper.requireOwnedCharacter(request, characterId);

        String toRegion = getRequiredString(body, "toRegion");
        CharacterLocation loc = travelService.depart(character, toRegion);
        return ResponseEntity.ok(loc);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private static String getRequiredString(Map<String, Object> body, String key) {
        Object val = body.get(key);
        if (val == null || val.toString().isBlank()) {
            throw ApiException.badRequest("Missing required field: " + key);
        }
        return val.toString().strip();
    }
}
