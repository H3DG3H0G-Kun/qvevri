package com.game.guild;

import com.game.character.CharacterService;
import com.game.exception.ApiException;
import com.game.market.TokenHelper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller for the LANE GUILDS feature — wine-house cooperatives.
 *
 * <p>All endpoints require {@code Authorization: Bearer <token>} and verify
 * that the acting {@code characterId} is owned by the authenticated account,
 * using the shared {@link TokenHelper} (inline bearer-auth pattern, no Spring
 * Security rules — {@code /api/guild/**} is already {@code permitAll} in
 * SecurityConfig).
 *
 * <p>Endpoints:
 * <ul>
 *   <li>POST /api/guild/create                     — create guild + FOUNDER membership</li>
 *   <li>POST /api/guild/{guildId}/join              — add MEMBER</li>
 *   <li>POST /api/guild/{guildId}/leave             — remove membership</li>
 *   <li>GET  /api/guild/{guildId}                   — guild + member list</li>
 *   <li>POST /api/guild/{guildId}/deposit           — wallet → treasury</li>
 *   <li>POST /api/guild/{guildId}/withdraw          — treasury → wallet (FOUNDER only)</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/guild")
public class GuildController {

    private final TokenHelper    tokenHelper;
    private final GuildService   guildService;

    public GuildController(TokenHelper tokenHelper,
                           GuildService guildService) {
        this.tokenHelper  = tokenHelper;
        this.guildService = guildService;
    }

    // ── POST /api/guild/create ────────────────────────────────────────────────

    /**
     * Creates a new guild and records the acting character as FOUNDER.
     *
     * <p>Request body: {@code { "characterId": 5, "name": "House Giorgi" }}
     *
     * @return 200 with the created {@link Guild}
     */
    @PostMapping("/create")
    public ResponseEntity<Guild> createGuild(
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {

        long characterId = extractLong(body, "characterId");
        String name = extractString(body, "name");

        tokenHelper.requireOwnedCharacter(request, characterId);

        Guild guild = guildService.createGuild(characterId, name);
        return ResponseEntity.ok(guild);
    }

    // ── POST /api/guild/{guildId}/join ────────────────────────────────────────

    /**
     * Adds the acting character as a MEMBER of the given guild.
     *
     * <p>Request body: {@code { "characterId": 7 }}
     *
     * @return 200 with the created {@link GuildMember}
     */
    @PostMapping("/{guildId}/join")
    public ResponseEntity<GuildMember> joinGuild(
            @PathVariable Long guildId,
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {

        long characterId = extractLong(body, "characterId");

        tokenHelper.requireOwnedCharacter(request, characterId);

        GuildMember member = guildService.joinGuild(guildId, characterId);
        return ResponseEntity.ok(member);
    }

    // ── POST /api/guild/{guildId}/leave ───────────────────────────────────────

    /**
     * Removes the acting character from the guild.
     *
     * <p>Request body: {@code { "characterId": 7 }}
     *
     * @return 200 with empty body on success
     */
    @PostMapping("/{guildId}/leave")
    public ResponseEntity<Void> leaveGuild(
            @PathVariable Long guildId,
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {

        long characterId = extractLong(body, "characterId");

        tokenHelper.requireOwnedCharacter(request, characterId);

        guildService.leaveGuild(guildId, characterId);
        return ResponseEntity.ok().build();
    }

    // ── GET /api/guild/{guildId} ──────────────────────────────────────────────

    /**
     * Returns the guild header plus its member list.
     *
     * @return 200 with a {@link GuildView}
     */
    @GetMapping("/{guildId}")
    public ResponseEntity<GuildView> getGuild(
            @PathVariable Long guildId,
            HttpServletRequest request) {

        // Any authenticated user may view a guild
        tokenHelper.requireAccountId(request);

        GuildView view = guildService.getGuild(guildId);
        return ResponseEntity.ok(view);
    }

    // ── POST /api/guild/{guildId}/deposit ─────────────────────────────────────

    /**
     * Moves GEL from the member's wallet into the guild treasury.
     *
     * <p>Request body: {@code { "characterId": 7, "amountGel": 50.0 }}
     *
     * @return 200 with the updated {@link Guild}
     */
    @PostMapping("/{guildId}/deposit")
    public ResponseEntity<Guild> deposit(
            @PathVariable Long guildId,
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {

        long   characterId = extractLong(body, "characterId");
        double amountGel   = extractDouble(body, "amountGel");

        tokenHelper.requireOwnedCharacter(request, characterId);

        Guild guild = guildService.deposit(guildId, characterId, amountGel);
        return ResponseEntity.ok(guild);
    }

    // ── POST /api/guild/{guildId}/withdraw ────────────────────────────────────

    /**
     * Moves GEL from the guild treasury into the FOUNDER's wallet.
     *
     * <p>Request body: {@code { "characterId": 5, "amountGel": 20.0 }}
     *
     * @return 200 with the updated {@link Guild}
     */
    @PostMapping("/{guildId}/withdraw")
    public ResponseEntity<Guild> withdraw(
            @PathVariable Long guildId,
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {

        long   characterId = extractLong(body, "characterId");
        double amountGel   = extractDouble(body, "amountGel");

        tokenHelper.requireOwnedCharacter(request, characterId);

        Guild guild = guildService.withdraw(guildId, characterId, amountGel);
        return ResponseEntity.ok(guild);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private long extractLong(Map<String, Object> body, String key) {
        Object val = body.get(key);
        if (val == null) {
            throw ApiException.badRequest("Missing required field: " + key);
        }
        if (val instanceof Number) {
            return ((Number) val).longValue();
        }
        try {
            return Long.parseLong(val.toString());
        } catch (NumberFormatException e) {
            throw ApiException.badRequest("Field " + key + " must be a number");
        }
    }

    private double extractDouble(Map<String, Object> body, String key) {
        Object val = body.get(key);
        if (val == null) {
            throw ApiException.badRequest("Missing required field: " + key);
        }
        if (val instanceof Number) {
            return ((Number) val).doubleValue();
        }
        try {
            return Double.parseDouble(val.toString());
        } catch (NumberFormatException e) {
            throw ApiException.badRequest("Field " + key + " must be a number");
        }
    }

    private String extractString(Map<String, Object> body, String key) {
        Object val = body.get(key);
        if (val == null || val.toString().isBlank()) {
            throw ApiException.badRequest("Missing required field: " + key);
        }
        return val.toString().strip();
    }
}
