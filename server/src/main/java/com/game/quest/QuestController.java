package com.game.quest;

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
 * REST controller for the Quest lane — {@code /api/quests/**}.
 *
 * <p>All endpoints use inline bearer-token authentication (no Spring Security
 * filter needed — {@code /api/quests/**} is {@code permitAll} in SecurityConfig).
 * Character ownership is verified via {@link CharacterService#getOwned}.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>GET  /api/quests/catalog                         — all quest definitions</li>
 *   <li>GET  /api/quests/{characterId}                   — character's PlayerQuest list</li>
 *   <li>POST /api/quests/{characterId}/accept  {questId} — accept a quest</li>
 *   <li>POST /api/quests/{characterId}/complete {questId} — complete and claim reward</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/quests")
public class QuestController {

    private final AccountTokenService accountTokenService;
    private final CharacterService    characterService;
    private final QuestService        questService;

    public QuestController(AccountTokenService accountTokenService,
                           CharacterService characterService,
                           QuestService questService) {
        this.accountTokenService = accountTokenService;
        this.characterService    = characterService;
        this.questService        = questService;
    }

    // ── GET /api/quests/catalog ───────────────────────────────────────────────

    /**
     * Returns all quest definitions from the static catalog.
     * Auth is accepted (token validated) but no character ownership check.
     */
    @GetMapping("/catalog")
    public ResponseEntity<Collection<QuestDefinition>> getCatalog(HttpServletRequest request) {
        requireAccountId(request);
        return ResponseEntity.ok(questService.getCatalog());
    }

    // ── GET /api/quests/{characterId} ─────────────────────────────────────────

    /**
     * Returns all PlayerQuests for the given character.
     * The requesting account must own the character.
     */
    @GetMapping("/{characterId}")
    public ResponseEntity<List<PlayerQuest>> getCharacterQuests(
            @PathVariable Long characterId,
            HttpServletRequest request) {

        requireOwnedCharacter(request, characterId);
        return ResponseEntity.ok(questService.getForCharacter(characterId));
    }

    // ── POST /api/quests/{characterId}/accept ─────────────────────────────────

    /**
     * Accepts a quest for the character.
     *
     * <p>Request body: {@code {"questId": "first_vine"}}
     *
     * @return 200 with the new ACTIVE {@link PlayerQuest}
     */
    @PostMapping("/{characterId}/accept")
    public ResponseEntity<PlayerQuest> acceptQuest(
            @PathVariable Long characterId,
            @RequestBody Map<String, String> body,
            HttpServletRequest request) {

        requireOwnedCharacter(request, characterId);
        String questId = requireField(body, "questId");
        PlayerQuest pq = questService.accept(characterId, questId);
        return ResponseEntity.ok(pq);
    }

    // ── POST /api/quests/{characterId}/complete ───────────────────────────────

    /**
     * Completes a quest and grants the reward.
     *
     * <p>Request body: {@code {"questId": "first_vine"}}
     *
     * <p>Idempotent guard: if the quest is already COMPLETED the service throws
     * 400 rather than re-granting the reward.
     *
     * @return 200 with the COMPLETED {@link PlayerQuest}
     */
    @PostMapping("/{characterId}/complete")
    public ResponseEntity<PlayerQuest> completeQuest(
            @PathVariable Long characterId,
            @RequestBody Map<String, String> body,
            HttpServletRequest request) {

        requireOwnedCharacter(request, characterId);
        String questId = requireField(body, "questId");
        PlayerQuest pq = questService.complete(characterId, questId);
        return ResponseEntity.ok(pq);
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
     * Extracts a required string field from the request body map.
     *
     * @throws ApiException 400 if the field is absent or blank
     */
    private String requireField(Map<String, String> body, String field) {
        String value = body == null ? null : body.get(field);
        if (value == null || value.isBlank()) {
            throw ApiException.badRequest("Missing required field: '" + field + "'");
        }
        return value.strip();
    }
}
