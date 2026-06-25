package com.game.chat;

import com.game.character.Character;
import com.game.exception.ApiException;
import com.game.market.TokenHelper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for lane CHAT — text channels and direct messages.
 *
 * <p>All endpoints use inline bearer auth via {@link TokenHelper}.
 * Character ownership is verified for send and for read on non-GLOBAL channels.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code POST /api/chat/send}              — send a message to a channel</li>
 *   <li>{@code GET  /api/chat/{channel}}         — read messages from a channel</li>
 * </ul>
 *
 * <p>{@code /api/chat/**} is already in SecurityConfig as {@code permitAll}
 * so no Spring Security filter intercepts these routes; auth is handled here.
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final TokenHelper tokenHelper;
    private final ChatService chatService;

    public ChatController(TokenHelper tokenHelper, ChatService chatService) {
        this.tokenHelper = tokenHelper;
        this.chatService = chatService;
    }

    // ── POST /api/chat/send ───────────────────────────────────────────────────

    /**
     * Sends a message to the specified channel.
     *
     * <p>Request body: {@code { "characterId": Long, "channel": String, "body": String }}.
     * Bearer token is validated; {@code characterId} must be owned by the token's account.
     *
     * @return 200 with the persisted {@link ChatMessage}
     * @throws ApiException 400 if body is blank or too long, or REGION mismatch
     * @throws ApiException 401 if token is missing/invalid
     * @throws ApiException 403 if the character is not authorised to post to the channel
     * @throws ApiException 404 if characterId is not found or not owned by this account
     */
    @PostMapping("/send")
    public ResponseEntity<ChatMessage> send(
            @RequestBody Map<String, Object> req,
            HttpServletRequest httpRequest) {

        Long characterId = getLong(req, "characterId");
        String channel   = getString(req, "channel");
        String body      = getString(req, "body");

        Character character = tokenHelper.requireOwnedCharacter(httpRequest, characterId);
        ChatMessage msg = chatService.send(character, channel, body);
        return ResponseEntity.ok(msg);
    }

    // ── GET /api/chat/{channel} ───────────────────────────────────────────────

    /**
     * Returns messages for the channel.
     *
     * <p>Query params:
     * <ul>
     *   <li>{@code characterId} — required for REGION, GUILD, and DM channels;
     *       optional for GLOBAL (any authed character suffices).</li>
     *   <li>{@code sinceId}     — optional; if present, returns only messages with
     *       {@code id > sinceId} in ascending order (polling path).</li>
     * </ul>
     *
     * <p>When {@code sinceId} is absent, returns the newest 50 messages.
     *
     * @return 200 with a {@link List} of {@link ChatMessage}
     * @throws ApiException 401 if token is missing/invalid
     * @throws ApiException 403 if the character is not authorised to read the channel
     * @throws ApiException 404 if characterId is provided but not owned by this account
     */
    @GetMapping("/{channel}")
    public ResponseEntity<List<ChatMessage>> getMessages(
            @PathVariable String channel,
            @RequestParam(required = false) Long characterId,
            @RequestParam(required = false) Long sinceId,
            HttpServletRequest httpRequest) {

        // Always require a valid bearer token; resolve the character if provided.
        Character character;
        if (characterId != null) {
            character = tokenHelper.requireOwnedCharacter(httpRequest, characterId);
        } else {
            // GLOBAL allows any authed token; non-GLOBAL requires characterId (403 returned
            // from ChatService.authorizeRead if character is null and channel != GLOBAL).
            tokenHelper.requireAccountId(httpRequest);
            character = null;
        }

        List<ChatMessage> messages = chatService.getMessages(character, channel, sinceId);
        return ResponseEntity.ok(messages);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private Long getLong(Map<String, Object> req, String key) {
        Object val = req.get(key);
        if (val == null) {
            throw ApiException.badRequest("Missing required field: " + key);
        }
        if (val instanceof Number) {
            return ((Number) val).longValue();
        }
        try {
            return Long.parseLong(val.toString());
        } catch (NumberFormatException e) {
            throw ApiException.badRequest("Field '" + key + "' must be a number");
        }
    }

    private String getString(Map<String, Object> req, String key) {
        Object val = req.get(key);
        if (val == null) {
            throw ApiException.badRequest("Missing required field: " + key);
        }
        return val.toString();
    }
}
