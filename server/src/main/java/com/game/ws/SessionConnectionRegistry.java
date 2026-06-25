package com.game.ws;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import com.game.dto.PlayerStateDto;
import com.game.dto.Vec3Dto;

/**
 * Tracks the live WebSocket connections and in-memory player state for each session.
 */
@Component
public class SessionConnectionRegistry {

    /**
     * Represents one connected player's current known state.
     */
    public static class ConnectedPlayer {
        private final WebSocketSession wsSession;
        private final String playerId;
        private final String displayName;
        private final String sessionId;

        private volatile double x = 0;
        private volatile double y = 0;
        private volatile double z = 0;
        private volatile double rotationY = 0;
        private volatile long updatedAt;

        public ConnectedPlayer(WebSocketSession wsSession, String playerId,
                               String displayName, String sessionId) {
            this.wsSession = wsSession;
            this.playerId = playerId;
            this.displayName = displayName;
            this.sessionId = sessionId;
            this.updatedAt = System.currentTimeMillis();
        }

        public WebSocketSession getWsSession() { return wsSession; }
        public String getPlayerId() { return playerId; }
        public String getDisplayName() { return displayName; }
        public String getSessionId() { return sessionId; }

        public synchronized void updatePosition(double x, double y, double z, double rotationY) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.rotationY = rotationY;
            this.updatedAt = System.currentTimeMillis();
        }

        public synchronized PlayerStateDto toDto() {
            return new PlayerStateDto(playerId, displayName,
                    new Vec3Dto(x, y, z), rotationY, updatedAt);
        }
    }

    /** wsSessionId -> ConnectedPlayer */
    private final Map<String, ConnectedPlayer> byWsSession = new ConcurrentHashMap<>();

    /** sessionId -> set of wsSessionIds (concurrent) */
    private final Map<String, Set<String>> byGameSession = new ConcurrentHashMap<>();

    public void register(ConnectedPlayer player) {
        byWsSession.put(player.getWsSession().getId(), player);
        byGameSession.computeIfAbsent(player.getSessionId(),
                k -> ConcurrentHashMap.newKeySet()).add(player.getWsSession().getId());
    }

    public void unregister(String wsSessionId) {
        ConnectedPlayer player = byWsSession.remove(wsSessionId);
        if (player != null) {
            Set<String> set = byGameSession.get(player.getSessionId());
            if (set != null) {
                set.remove(wsSessionId);
            }
        }
    }

    public ConnectedPlayer get(String wsSessionId) {
        return byWsSession.get(wsSessionId);
    }

    /** All WS sessions in a game session (including the caller). */
    public List<ConnectedPlayer> getConnectedPlayers(String sessionId) {
        Set<String> wsIds = byGameSession.getOrDefault(sessionId, Set.of());
        List<ConnectedPlayer> result = new ArrayList<>();
        for (String id : wsIds) {
            ConnectedPlayer cp = byWsSession.get(id);
            if (cp != null) result.add(cp);
        }
        return result;
    }

    /** All live player DTOs in a game session. */
    public List<PlayerStateDto> getPlayersInSession(String sessionId) {
        return getConnectedPlayers(sessionId).stream()
                .map(ConnectedPlayer::toDto)
                .toList();
    }

    public Collection<ConnectedPlayer> allConnected() {
        return byWsSession.values();
    }
}
