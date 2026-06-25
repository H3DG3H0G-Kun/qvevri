package com.game.prestige;

import com.game.exception.ApiException;
import com.game.market.TokenHelper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST controller for the prestige lane.
 *
 * <p>All endpoints require a valid {@code Authorization: Bearer <token>} header.
 * Character ownership is verified inline via {@link TokenHelper}
 * (which calls {@code AccountTokenService} + {@code CharacterService.getOwned}).
 * Security config already has {@code /api/prestige/**} as permitAll.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>GET  /api/prestige/ladder                  → the full title ladder</li>
 *   <li>GET  /api/prestige/{characterId}            → get (or auto-create) the profile</li>
 *   <li>POST /api/prestige/{characterId}/award      → award prestige, return updated profile</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/prestige")
public class PrestigeController {

    private final TokenHelper     tokenHelper;
    private final PrestigeService prestigeService;

    public PrestigeController(TokenHelper tokenHelper,
                              PrestigeService prestigeService) {
        this.tokenHelper     = tokenHelper;
        this.prestigeService = prestigeService;
    }

    // ── GET /api/prestige/ladder ──────────────────────────────────────────────

    /**
     * Returns the complete title ladder as an ordered list.
     *
     * <p>No auth required — the ladder is public static data.
     *
     * @return 200 with list of {@code { title, threshold }} objects, ascending by threshold
     */
    @GetMapping("/ladder")
    public ResponseEntity<List<Map<String, Object>>> getLadder() {
        List<Map<String, Object>> ladder = TitleLadder.all().stream()
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("title", e.title());
                    m.put("threshold", e.threshold());
                    return m;
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(ladder);
    }

    // ── GET /api/prestige/{characterId} ──────────────────────────────────────

    /**
     * Returns the prestige profile for the given character.
     * If no profile exists yet it is auto-created at prestige=0 / title=GLEKHI.
     *
     * <p>Response shape:
     * <pre>
     * {
     *   "prestige":       &lt;long&gt;,
     *   "title":          &lt;string&gt;,
     *   "nextTitle":      &lt;string | null&gt;,
     *   "prestigeToNext": &lt;long&gt;        // 0 at top
     * }
     * </pre>
     *
     * @param characterId path variable — target character
     * @param request     servlet request (for bearer token extraction)
     * @return 200 with the profile view map
     * @throws ApiException 401 if the token is missing/invalid
     * @throws ApiException 404 if the character is not found or not owned by this account
     */
    @GetMapping("/{characterId}")
    public ResponseEntity<Map<String, Object>> getProfile(
            @PathVariable Long characterId,
            HttpServletRequest request) {

        tokenHelper.requireOwnedCharacter(request, characterId);

        PrestigeProfile profile = prestigeService.getOrCreate(characterId);
        return ResponseEntity.ok(toView(profile));
    }

    // ── POST /api/prestige/{characterId}/award ────────────────────────────────

    /**
     * Awards prestige to the character and returns the updated profile view.
     *
     * <p>Request body: {@code { "amount": <long>, "reason": "<string>" }}.
     * {@code amount} must be &gt; 0 (400 otherwise).
     *
     * @param characterId path variable — target character
     * @param body        JSON body with {@code amount} (long) and optional {@code reason} (String)
     * @param request     servlet request (for bearer token extraction)
     * @return 200 with the updated profile view map
     * @throws ApiException 400 if {@code amount} is missing or &lt;= 0
     * @throws ApiException 401 if the token is missing/invalid
     * @throws ApiException 404 if the character is not found or not owned by this account
     */
    @PostMapping("/{characterId}/award")
    public ResponseEntity<Map<String, Object>> awardPrestige(
            @PathVariable Long characterId,
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {

        tokenHelper.requireOwnedCharacter(request, characterId);

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

        PrestigeProfile updated = prestigeService.awardPrestige(characterId, amount, reason);
        return ResponseEntity.ok(toView(updated));
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Builds the response view map from a profile.
     *
     * <pre>
     * {
     *   "prestige":       &lt;long&gt;,
     *   "title":          &lt;string&gt;,
     *   "nextTitle":      &lt;string | null&gt;,
     *   "prestigeToNext": &lt;long&gt;        // 0 when already at the top title
     * }
     * </pre>
     */
    private Map<String, Object> toView(PrestigeProfile profile) {
        long prestige = profile.getPrestige();
        TitleLadder.Entry next = TitleLadder.nextTitle(prestige);

        Map<String, Object> view = new LinkedHashMap<>();
        view.put("prestige",       prestige);
        view.put("title",          profile.getTitleRank());
        view.put("nextTitle",      next != null ? next.title() : null);
        view.put("prestigeToNext", next != null ? next.threshold() - prestige : 0L);
        return view;
    }
}
