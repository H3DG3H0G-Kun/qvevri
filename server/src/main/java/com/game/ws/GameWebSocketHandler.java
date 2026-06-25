package com.game.ws;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.game.auth.UserStore;
import com.game.dto.PlayerStateDto;
import com.game.dto.Vec3Dto;
import com.game.persistence.PlayerStatePersistenceService;
import com.game.session.SessionRegistry;

/**
 * Raw WebSocket handler at /ws/game.
 * No STOMP/SockJS. Text frames, JSON, "type" discriminator.
 *
 * Client -> Server: hello | move | ping
 * Server -> Client: welcome | state | join | leave | pong | error
 */
@Component
public class GameWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(GameWebSocketHandler.class);

    private final ObjectMapper objectMapper;
    private final SessionConnectionRegistry connectionRegistry;
    private final SessionRegistry sessionRegistry;
    private final PlayerStatePersistenceService persistenceService;

    public GameWebSocketHandler(ObjectMapper objectMapper,
                                SessionConnectionRegistry connectionRegistry,
                                SessionRegistry sessionRegistry,
                                PlayerStatePersistenceService persistenceService) {
        this.objectMapper = objectMapper;
        this.connectionRegistry = connectionRegistry;
        this.sessionRegistry = sessionRegistry;
        this.persistenceService = persistenceService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        // Connection accepted; wait for "hello" to bind to a game session.
        log.debug("WS connection established: {}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        JsonNode node;
        try {
            node = objectMapper.readTree(message.getPayload());
        } catch (Exception e) {
            sendError(session, "BAD_REQUEST", "Invalid JSON");
            return;
        }

        String type = node.path("type").asText(null);
        if (type == null) {
            sendError(session, "BAD_REQUEST", "Missing 'type' field");
            return;
        }

        switch (type) {
            case "hello" -> handleHello(session, node);
            case "move"  -> handleMove(session, node);
            case "ping"  -> handlePing(session, node);
            default      -> sendError(session, "BAD_REQUEST", "Unknown message type: " + type);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        SessionConnectionRegistry.ConnectedPlayer player = connectionRegistry.get(session.getId());
        if (player != null) {
            String sessionId = player.getSessionId();
            String playerId = player.getPlayerId();

            // Persist last known position immediately on disconnect
            persistenceService.persistImmediate(playerId, sessionId, player.toDto());

            connectionRegistry.unregister(session.getId());
            sessionRegistry.leave(sessionId, playerId);

            // Broadcast "leave" to remaining players
            broadcastLeave(sessionId, playerId);
            log.info("Player {} left session {}", playerId, sessionId);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.warn("WS transport error for session {}: {}", session.getId(), exception.getMessage());
    }

    // ---- Message handlers ------------------------------------------------

    private void handleHello(WebSocketSession session, JsonNode node) throws IOException {
        // Validate: must have sessionId
        String sessionId = node.path("sessionId").asText(null);
        if (sessionId == null || sessionId.isBlank()) {
            sendError(session, "BAD_REQUEST", "hello.sessionId is required");
            return;
        }

        UserStore.UserRecord user = (UserStore.UserRecord)
                session.getAttributes().get(WsTokenHandshakeInterceptor.ATTR_USER);
        if (user == null) {
            // Should not happen (token validated in handshake) but guard anyway
            session.close(new CloseStatus(4401, "UNAUTHORIZED"));
            return;
        }

        // Check session capacity
        boolean admitted = sessionRegistry.join(sessionId, user.playerId());
        if (!admitted) {
            sendError(session, "SESSION_FULL", "Session " + sessionId + " is full");
            session.close(new CloseStatus(4409, "SESSION_FULL"));
            return;
        }

        // Register in connection registry
        SessionConnectionRegistry.ConnectedPlayer cp =
                new SessionConnectionRegistry.ConnectedPlayer(
                        session, user.playerId(), user.displayName(), sessionId);
        connectionRegistry.register(cp);

        // Resolve spawn
        Vec3Dto spawnPos = persistenceService.loadPosition(user.playerId(), sessionId)
                .orElse(Vec3Dto.zero());

        // Seed the player's position in the registry so they appear in state broadcasts
        cp.updatePosition(spawnPos.getX(), spawnPos.getY(), spawnPos.getZ(), 0.0);

        // Build welcome.players = all others currently connected
        List<PlayerStateDto> others = connectionRegistry.getPlayersInSession(sessionId).stream()
                .filter(p -> !p.getPlayerId().equals(user.playerId()))
                .toList();

        // Send "welcome"
        ObjectNode welcome = objectMapper.createObjectNode();
        welcome.put("type", "welcome");
        welcome.put("self", user.playerId());
        welcome.set("players", objectMapper.valueToTree(others));
        sendJson(session, welcome);

        // Broadcast "join" to other players in the session
        broadcastJoin(sessionId, session.getId(), cp.toDto());

        log.info("Player {} joined session {}, spawn={}", user.playerId(), sessionId, spawnPos);
    }

    private void handleMove(WebSocketSession session, JsonNode node) throws IOException {
        SessionConnectionRegistry.ConnectedPlayer player = connectionRegistry.get(session.getId());
        if (player == null) {
            sendError(session, "BAD_REQUEST", "Send 'hello' before 'move'");
            return;
        }

        // seq is advisory, not echoed (§6.5)
        JsonNode pos = node.path("position");
        double x = pos.path("x").asDouble(0);
        double y = pos.path("y").asDouble(0);
        double z = pos.path("z").asDouble(0);
        double rotationY = node.path("rotationY").asDouble(0);

        player.updatePosition(x, y, z, rotationY);

        // Throttled persistence (at most 1 write/500ms per player)
        persistenceService.persist(player.getPlayerId(), player.getSessionId(), player.toDto());
    }

    private void handlePing(WebSocketSession session, JsonNode node) throws IOException {
        long t = node.path("t").asLong(System.currentTimeMillis());
        ObjectNode pong = objectMapper.createObjectNode();
        pong.put("type", "pong");
        pong.put("t", t);
        sendJson(session, pong);
    }

    // ---- Broadcast helpers -----------------------------------------------

    /**
     * Broadcast "join" delta to all other players in the session.
     */
    private void broadcastJoin(String sessionId, String excludeWsId, PlayerStateDto newPlayer) {
        ObjectNode msg = objectMapper.createObjectNode();
        msg.put("type", "join");
        msg.set("player", objectMapper.valueToTree(newPlayer));
        broadcastToSession(sessionId, excludeWsId, msg);
    }

    /**
     * Broadcast "leave" delta to all remaining players in the session.
     */
    private void broadcastLeave(String sessionId, String playerId) {
        ObjectNode msg = objectMapper.createObjectNode();
        msg.put("type", "leave");
        msg.put("playerId", playerId);
        broadcastToSession(sessionId, null, msg);
    }

    /**
     * Send a JSON state snapshot to a specific session (called by the tick scheduler).
     */
    public void broadcastState(String sessionId) {
        List<PlayerStateDto> players = connectionRegistry.getPlayersInSession(sessionId);
        if (players.isEmpty()) return;

        ObjectNode msg = objectMapper.createObjectNode();
        msg.put("type", "state");
        msg.put("t", System.currentTimeMillis());
        msg.set("players", objectMapper.valueToTree(players));
        broadcastToSession(sessionId, null, msg);
    }

    private void broadcastToSession(String sessionId, String excludeWsId, ObjectNode msg) {
        List<SessionConnectionRegistry.ConnectedPlayer> connected =
                connectionRegistry.getConnectedPlayers(sessionId);
        for (SessionConnectionRegistry.ConnectedPlayer cp : connected) {
            if (excludeWsId != null && cp.getWsSession().getId().equals(excludeWsId)) {
                continue;
            }
            try {
                sendJson(cp.getWsSession(), msg);
            } catch (Exception e) {
                log.warn("Failed to send to {}: {}", cp.getPlayerId(), e.getMessage());
            }
        }
    }

    // ---- Utility ---------------------------------------------------------

    private void sendError(WebSocketSession session, String code, String message) throws IOException {
        ObjectNode err = objectMapper.createObjectNode();
        err.put("type", "error");
        ObjectNode body = err.putObject("error");
        body.put("code", code);
        body.put("message", message);
        sendJson(session, err);
    }

    private void sendJson(WebSocketSession session, ObjectNode node) throws IOException {
        if (session.isOpen()) {
            synchronized (session) {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(node)));
            }
        }
    }

    /** All distinct game sessionIds with at least one connected player. */
    public Set<String> activeSessions() {
        Set<String> result = new HashSet<>();
        for (SessionConnectionRegistry.ConnectedPlayer cp : connectionRegistry.allConnected()) {
            result.add(cp.getSessionId());
        }
        return result;
    }
}
