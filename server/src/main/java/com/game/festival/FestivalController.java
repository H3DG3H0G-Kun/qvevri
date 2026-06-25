package com.game.festival;

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
 * REST controller for the Festival lane — {@code /api/festival/**}.
 *
 * <p>All endpoints use inline bearer-token authentication (no Spring Security
 * filter needed — {@code /api/festival/**} is already {@code permitAll} in
 * SecurityConfig). Character ownership is verified via
 * {@link CharacterService#getOwned}.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>GET  /api/festival/calendar                      — all festival definitions</li>
 *   <li>GET  /api/festival/active                        — festivals active right now</li>
 *   <li>POST /api/festival/{festivalId}/participate      — participate + claim reward</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/festival")
public class FestivalController {

    private final AccountTokenService accountTokenService;
    private final CharacterService    characterService;
    private final FestivalService     festivalService;

    public FestivalController(AccountTokenService accountTokenService,
                              CharacterService characterService,
                              FestivalService festivalService) {
        this.accountTokenService = accountTokenService;
        this.characterService    = characterService;
        this.festivalService     = festivalService;
    }

    // ── GET /api/festival/calendar ────────────────────────────────────────────

    /**
     * Returns all festival definitions from the static calendar.
     * Auth is accepted (token validated) but no character ownership check.
     */
    @GetMapping("/calendar")
    public ResponseEntity<Collection<FestivalDefinition>> getCalendar(
            HttpServletRequest request) {
        requireAccountId(request);
        return ResponseEntity.ok(festivalService.getCalendar());
    }

    // ── GET /api/festival/active ──────────────────────────────────────────────

    /**
     * Returns festivals whose day-of-year window contains the current world day.
     * Resolved lazily from WorldClockService — no scheduler involved.
     */
    @GetMapping("/active")
    public ResponseEntity<List<FestivalDefinition>> getActive(HttpServletRequest request) {
        requireAccountId(request);
        return ResponseEntity.ok(festivalService.getActive());
    }

    // ── POST /api/festival/{festivalId}/participate ───────────────────────────

    /**
     * Records participation and grants the reward GEL to the character.
     *
     * <p>Request body: {@code {"characterId": <long>}}
     *
     * <p>Returns 200 with the created {@link FestivalParticipation}:
     * <ul>
     *   <li>400 NOT_ACTIVE if the festival window does not contain the current day.</li>
     *   <li>404 NOT_FOUND if the festivalId is unknown.</li>
     *   <li>400 ALREADY_PARTICIPATED if the character already participated this year.</li>
     *   <li>404 if the character is not owned by the authenticated account.</li>
     * </ul>
     */
    @PostMapping("/{festivalId}/participate")
    public ResponseEntity<FestivalParticipation> participate(
            @PathVariable String festivalId,
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {

        Long characterId = requireCharacterId(body);
        requireOwnedCharacter(request, characterId);
        FestivalParticipation participation = festivalService.participate(characterId, festivalId);
        return ResponseEntity.ok(participation);
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    /**
     * Extracts and validates the bearer token, returning the accountId.
     *
     * @throws ApiException 401 if the header is missing or invalid
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
     * @throws ApiException 401 if token invalid, 404 if character not found or not owned
     */
    private void requireOwnedCharacter(HttpServletRequest request, Long characterId) {
        Long accountId = requireAccountId(request);
        characterService.getOwned(characterId, accountId)
                .orElseThrow(() -> ApiException.notFound(
                        "Character " + characterId
                        + " not found or not owned by this account"));
    }

    /**
     * Extracts the required {@code characterId} (long) from the request body map.
     *
     * @throws ApiException 400 if the field is absent or not a number
     */
    private Long requireCharacterId(Map<String, Object> body) {
        if (body == null || !body.containsKey("characterId")) {
            throw ApiException.badRequest("Missing required field: 'characterId'");
        }
        Object raw = body.get("characterId");
        if (!(raw instanceof Number)) {
            throw ApiException.badRequest("Field 'characterId' must be a numeric id");
        }
        return ((Number) raw).longValue();
    }
}
