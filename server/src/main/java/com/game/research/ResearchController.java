package com.game.research;

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
 * REST controller for the Research lane — {@code /api/research/**}.
 *
 * <p>All endpoints use inline bearer-token authentication (no Spring Security
 * filter: {@code /api/research/**} is {@code permitAll} in SecurityConfig).
 * Character ownership is verified via {@link CharacterService#getOwned}.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>GET  /api/research/catalog           — all static research nodes</li>
 *   <li>GET  /api/research/{characterId}     — character's PlayerResearch list
 *                                              (with lazy completion)</li>
 *   <li>POST /api/research/{nodeId}/start    — start research for a character</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/research")
public class ResearchController {

    private final AccountTokenService accountTokenService;
    private final CharacterService    characterService;
    private final ResearchService     researchService;

    public ResearchController(AccountTokenService accountTokenService,
                              CharacterService characterService,
                              ResearchService researchService) {
        this.accountTokenService = accountTokenService;
        this.characterService    = characterService;
        this.researchService     = researchService;
    }

    // ── GET /api/research/catalog ─────────────────────────────────────────────

    /**
     * Returns all research nodes from the static catalog.
     * Auth is validated but no character ownership check is required.
     */
    @GetMapping("/catalog")
    public ResponseEntity<Collection<ResearchNode>> getCatalog(HttpServletRequest request) {
        requireAccountId(request);
        return ResponseEntity.ok(researchService.getCatalog());
    }

    // ── GET /api/research/{characterId} ───────────────────────────────────────

    /**
     * Returns all PlayerResearch rows for the given character, with lazy completion.
     *
     * @param characterId the owning character
     * @return 200 with the list of PlayerResearch rows
     */
    @GetMapping("/{characterId}")
    public ResponseEntity<List<PlayerResearch>> getCharacterResearch(
            @PathVariable Long characterId,
            HttpServletRequest request) {

        requireOwnedCharacter(request, characterId);
        return ResponseEntity.ok(researchService.getForCharacter(characterId));
    }

    // ── POST /api/research/{nodeId}/start ─────────────────────────────────────

    /**
     * Starts research on the given node for the specified character.
     *
     * <p>Request body: {@code {"characterId": 5}}
     *
     * <p>Error cases:
     * <ul>
     *   <li>404 if nodeId is unknown</li>
     *   <li>400 if already started or complete</li>
     *   <li>400 PREREQ_NOT_MET if the prereq is not COMPLETE</li>
     *   <li>400 INSUFFICIENT_FUNDS if wallet is too low</li>
     * </ul>
     *
     * @return 200 with the new RESEARCHING PlayerResearch row
     */
    @PostMapping("/{nodeId}/start")
    public ResponseEntity<PlayerResearch> startResearch(
            @PathVariable String nodeId,
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {

        Long characterId = requireLongField(body, "characterId");
        requireOwnedCharacter(request, characterId);
        PlayerResearch pr = researchService.startResearch(characterId, nodeId);
        return ResponseEntity.ok(pr);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

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
     * Extracts a required Long field from the request body map.
     *
     * @throws ApiException 400 if the field is absent or not a number
     */
    private Long requireLongField(Map<String, Object> body, String field) {
        Object raw = body == null ? null : body.get(field);
        if (raw == null) {
            throw ApiException.badRequest("Missing required field: '" + field + "'");
        }
        if (raw instanceof Number) {
            return ((Number) raw).longValue();
        }
        throw ApiException.badRequest("Field '" + field + "' must be a number");
    }
}
