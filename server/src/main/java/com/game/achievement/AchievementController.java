package com.game.achievement;

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
 * REST controller for the Achievement lane — {@code /api/achievement/**}.
 *
 * <p>All endpoints use inline bearer-token authentication (no Spring Security
 * filter needed — {@code /api/achievement/**} is {@code permitAll} in SecurityConfig
 * per the CONTEST-ACHIEVEMENT-CHAT-SPEC hard rules). Character ownership is
 * verified via {@link CharacterService#getOwned}.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>GET  /api/achievement/catalog                              — all definitions</li>
 *   <li>GET  /api/achievement/{characterId}                        — character's unlocked achievements</li>
 *   <li>POST /api/achievement/{achievementId}/unlock {characterId} — unlock + grant reward</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/achievement")
public class AchievementController {

    private final AccountTokenService   accountTokenService;
    private final CharacterService      characterService;
    private final AchievementService    achievementService;

    public AchievementController(
            AccountTokenService accountTokenService,
            CharacterService characterService,
            AchievementService achievementService) {
        this.accountTokenService = accountTokenService;
        this.characterService    = characterService;
        this.achievementService  = achievementService;
    }

    // ── GET /api/achievement/catalog ──────────────────────────────────────────

    /**
     * Returns all achievement definitions from the static catalog.
     * Auth is validated (token required) but no character ownership check.
     */
    @GetMapping("/catalog")
    public ResponseEntity<Collection<AchievementDefinition>> getCatalog(
            HttpServletRequest request) {
        requireAccountId(request);
        return ResponseEntity.ok(achievementService.getCatalog());
    }

    // ── GET /api/achievement/{characterId} ────────────────────────────────────

    /**
     * Returns all unlocked PlayerAchievements for the given character.
     * The requesting account must own the character.
     */
    @GetMapping("/{characterId}")
    public ResponseEntity<List<PlayerAchievement>> getCharacterAchievements(
            @PathVariable Long characterId,
            HttpServletRequest request) {
        requireOwnedCharacter(request, characterId);
        return ResponseEntity.ok(achievementService.getForCharacter(characterId));
    }

    // ── POST /api/achievement/{achievementId}/unlock ──────────────────────────

    /**
     * Unlocks an achievement for the character and grants the one-time reward.
     *
     * <p>Request body: {@code {"characterId": 5}}
     *
     * <p>Idempotent guard: if already unlocked → 400 ALREADY_UNLOCKED (no double reward).
     *
     * @return 200 with the new {@link PlayerAchievement}
     */
    @PostMapping("/{achievementId}/unlock")
    public ResponseEntity<PlayerAchievement> unlockAchievement(
            @PathVariable String achievementId,
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {

        Long characterId = requireCharacterIdField(body);
        requireOwnedCharacter(request, characterId);
        PlayerAchievement pa = achievementService.unlock(characterId, achievementId);
        return ResponseEntity.ok(pa);
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
     * Extracts and parses the required {@code characterId} field from the request body.
     *
     * @throws ApiException 400 if the field is absent or not a valid number
     */
    private Long requireCharacterIdField(Map<String, Object> body) {
        Object raw = body == null ? null : body.get("characterId");
        if (raw == null) {
            throw ApiException.badRequest("Missing required field: 'characterId'");
        }
        try {
            return ((Number) raw).longValue();
        } catch (ClassCastException e) {
            throw ApiException.badRequest("Field 'characterId' must be a number");
        }
    }
}
