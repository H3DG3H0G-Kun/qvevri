package com.game.profession;

import com.game.account.AccountTokenService;
import com.game.account.BearerTokenSupport;
import com.game.character.Character;
import com.game.character.CharacterService;
import com.game.exception.ApiException;
import com.game.goods.OwnedGood;
import com.game.world.CareerType;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller for profession endpoints.
 *
 * <p>All endpoints use inline bearer → accountId resolution (same pattern as
 * {@link com.game.market.MarketController}).  SecurityConfig already permits
 * {@code /api/profession/**} — no changes needed there.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>GET  /api/profession/capabilities        — career capability catalog (no char required)</li>
 *   <li>POST /api/profession/claim-kit           — idempotent starter-kit grant</li>
 *   <li>POST /api/profession/cooper/craft        — COOPER craft a vessel</li>
 *   <li>POST /api/profession/lab/grade           — ENOLOGIST grade a bottle</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/profession")
public class ProfessionController {

    private final AccountTokenService accountTokenService;
    private final CharacterService    characterService;
    private final ProfessionService   professionService;

    public ProfessionController(
            AccountTokenService accountTokenService,
            CharacterService characterService,
            ProfessionService professionService) {
        this.accountTokenService = accountTokenService;
        this.characterService    = characterService;
        this.professionService   = professionService;
    }

    // ── GET /api/profession/capabilities ─────────────────────────────────────

    /**
     * Returns the full career → capabilities catalog.
     *
     * <p>No character ownership check; a valid bearer token is sufficient.
     */
    @GetMapping("/capabilities")
    public ResponseEntity<Map<CareerType, CareerCapability.Capability>> capabilities(
            HttpServletRequest request) {
        requireAccountId(request);
        return ResponseEntity.ok(professionService.capabilities());
    }

    // ── POST /api/profession/claim-kit ────────────────────────────────────────

    /**
     * Idempotently grants the caller's character its career starter kit.
     *
     * <p>Body: {@code { "characterId": <long> }}
     */
    @PostMapping("/claim-kit")
    public ResponseEntity<ProfessionKitClaim> claimKit(
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {
        Character character = requireOwnedCharacter(request, body);
        ProfessionKitClaim claim = professionService.claimStarterKit(character);
        return ResponseEntity.ok(claim);
    }

    // ── POST /api/profession/cooper/craft ─────────────────────────────────────

    /**
     * Cooper crafts a vessel from raw input goods.
     *
     * <p>Body: {@code { "characterId": <long>, "recipeId": "<string>" }}
     */
    @PostMapping("/cooper/craft")
    public ResponseEntity<OwnedGood> cooperCraft(
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {
        Character character = requireOwnedCharacter(request, body);
        String recipeId = requireString(body, "recipeId");
        OwnedGood produced = professionService.cooperCraft(character, recipeId);
        return ResponseEntity.ok(produced);
    }

    // ── POST /api/profession/lab/grade ────────────────────────────────────────

    /**
     * Enologist grades a cellar item and persists the result.
     *
     * <p>Body: {@code { "characterId": <long>, "cellarItemId": <long> }}
     */
    @PostMapping("/lab/grade")
    public ResponseEntity<WineGrade> labGrade(
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {
        Character character = requireOwnedCharacter(request, body);
        Long cellarItemId = requireLong(body, "cellarItemId");
        WineGrade grade = professionService.labGrade(character, cellarItemId);
        return ResponseEntity.ok(grade);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Long requireAccountId(HttpServletRequest request) {
        return BearerTokenSupport.resolveAccountId(request, accountTokenService)
                .orElseThrow(() -> ApiException.unauthorized(
                        "Missing or invalid Authorization header"));
    }

    private Character requireOwnedCharacter(HttpServletRequest request,
                                             Map<String, Object> body) {
        Long accountId    = requireAccountId(request);
        Long characterId  = requireLong(body, "characterId");
        return characterService.getOwned(characterId, accountId)
                .orElseThrow(() -> ApiException.notFound(
                        "Character " + characterId
                        + " not found or not owned by this account"));
    }

    private static Long requireLong(Map<String, Object> body, String field) {
        Object raw = body.get(field);
        if (raw == null) {
            throw ApiException.badRequest("Missing required field: " + field);
        }
        if (raw instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(raw.toString());
        } catch (NumberFormatException e) {
            throw ApiException.badRequest("Field '" + field + "' must be a number");
        }
    }

    private static String requireString(Map<String, Object> body, String field) {
        Object raw = body.get(field);
        if (raw == null || raw.toString().isBlank()) {
            throw ApiException.badRequest("Missing required field: " + field);
        }
        return raw.toString().strip();
    }
}
