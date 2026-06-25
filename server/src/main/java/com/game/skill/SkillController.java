package com.game.skill;

import com.game.account.AccountTokenService;
import com.game.character.CharacterService;
import com.game.exception.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.Map;

/**
 * REST controller for the Skill lane — {@code /api/skill/**}.
 *
 * <p>All endpoints use inline bearer-token authentication (no Spring Security
 * filter: {@code /api/skill/**} is {@code permitAll} in SecurityConfig).
 * Character ownership is verified via {@link CharacterService#getOwned}.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>GET  /api/skill/catalog                — all static talent definitions</li>
 *   <li>GET  /api/skill/{characterId}          — character's skill profile (lazy-create)</li>
 *   <li>POST /api/skill/{skillId}/learn        — learn a talent for a character</li>
 *   <li>POST /api/skill/respec                 — wipe all learned talents + free points</li>
 *   <li>GET  /api/skill/bonuses/{characterId}  — aggregated bonusType → bonusValue map</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/skill")
public class SkillController {

    private final AccountTokenService accountTokenService;
    private final CharacterService    characterService;
    private final SkillService        skillService;

    public SkillController(AccountTokenService accountTokenService,
                           CharacterService characterService,
                           SkillService skillService) {
        this.accountTokenService = accountTokenService;
        this.characterService    = characterService;
        this.skillService        = skillService;
    }

    // ── GET /api/skill/catalog ────────────────────────────────────────────────

    /**
     * Returns all talents from the static catalog.
     * Auth is validated but no character ownership check is required.
     */
    @GetMapping("/catalog")
    public ResponseEntity<Collection<SkillTalent>> getCatalog(HttpServletRequest request) {
        requireAccountId(request);
        return ResponseEntity.ok(skillService.getCatalog());
    }

    // ── GET /api/skill/{characterId} ──────────────────────────────────────────

    /**
     * Returns the skill profile for the given character, lazy-creating it at
     * totalPoints=5, spentPoints=0 on first access.
     *
     * @param characterId the owning character
     * @return 200 with the SkillProfileView
     */
    @GetMapping("/{characterId}")
    public ResponseEntity<SkillProfileView> getProfile(
            @PathVariable Long characterId,
            HttpServletRequest request) {

        requireOwnedCharacter(request, characterId);
        return ResponseEntity.ok(skillService.getProfile(characterId));
    }

    // ── POST /api/skill/{skillId}/learn ───────────────────────────────────────

    /**
     * Learns the given talent for the specified character.
     *
     * <p>Request body: {@code {"characterId": 5}}
     *
     * <p>Error cases:
     * <ul>
     *   <li>404 if skillId is unknown</li>
     *   <li>400 ALREADY_LEARNED if the talent has already been learned</li>
     *   <li>400 PREREQ_NOT_MET if the prereq talent has not been learned</li>
     *   <li>400 INSUFFICIENT_POINTS if availablePoints &lt; cost</li>
     * </ul>
     *
     * @return 200 with the updated SkillProfileView
     */
    @PostMapping("/{skillId}/learn")
    public ResponseEntity<SkillProfileView> learn(
            @PathVariable String skillId,
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {

        Long characterId = requireLongField(body, "characterId");
        requireOwnedCharacter(request, characterId);
        return ResponseEntity.ok(skillService.learn(characterId, skillId));
    }

    // ── POST /api/skill/respec ────────────────────────────────────────────────

    /**
     * Resets all learned talents for the character and frees all spent points.
     * v1 is free; a GEL cost can be added later.
     *
     * <p>Request body: {@code {"characterId": 5}}
     *
     * @return 200 with the updated SkillProfileView (empty learned list, spentPoints=0)
     */
    @PostMapping("/respec")
    public ResponseEntity<SkillProfileView> respec(
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {

        Long characterId = requireLongField(body, "characterId");
        requireOwnedCharacter(request, characterId);
        return ResponseEntity.ok(skillService.respec(characterId));
    }

    // ── GET /api/skill/bonuses/{characterId} ──────────────────────────────────

    /**
     * Returns a map of bonusType → summed bonusValue across all talents
     * learned by the given character.
     *
     * @param characterId the owning character
     * @return 200 with the bonus aggregation map
     */
    @GetMapping("/bonuses/{characterId}")
    public ResponseEntity<Map<String, Double>> getBonuses(
            @PathVariable Long characterId,
            HttpServletRequest request) {

        requireOwnedCharacter(request, characterId);
        return ResponseEntity.ok(skillService.getBonuses(characterId));
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
