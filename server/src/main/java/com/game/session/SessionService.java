package com.game.session;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.game.dto.PlayerStateDto;
import com.game.dto.Vec3Dto;
import com.game.exception.ApiException;
import com.game.persistence.PlayerStatePersistenceService;
import com.game.ws.SessionConnectionRegistry;

@Service
public class SessionService {

    private final SessionRegistry sessionRegistry;
    private final PlayerStatePersistenceService persistenceService;
    private final SessionConnectionRegistry connectionRegistry;

    public SessionService(SessionRegistry sessionRegistry,
                          PlayerStatePersistenceService persistenceService,
                          SessionConnectionRegistry connectionRegistry) {
        this.sessionRegistry = sessionRegistry;
        this.persistenceService = persistenceService;
        this.connectionRegistry = connectionRegistry;
    }

    /**
     * Join (or create) a session. Returns spawn + current players (excluding self).
     */
    public JoinResponse join(String playerId, String displayName, String sessionId) {
        boolean admitted = sessionRegistry.join(sessionId, playerId);
        if (!admitted) {
            throw ApiException.sessionFull("Session " + sessionId + " is full (cap=" + SessionRegistry.SESSION_CAP + ")");
        }

        // Spawn: persisted position if exists, else origin (§6.2)
        Vec3Dto spawn = persistenceService.loadPosition(playerId, sessionId)
                .orElse(Vec3Dto.zero());

        // Ensure the player has a persisted row (creates one with origin if none exists).
        // This makes the player visible in GET /players immediately after join.
        PlayerStateDto initialState = new PlayerStateDto(
                playerId, displayName, spawn, 0.0, System.currentTimeMillis());
        persistenceService.persistImmediate(playerId, sessionId, initialState);

        // Current players excluding self, from in-memory WS state (authoritative) or DB
        List<PlayerStateDto> others = connectionRegistry.getPlayersInSession(sessionId).stream()
                .filter(p -> !p.getPlayerId().equals(playerId))
                .toList();

        return new JoinResponse(sessionId, spawn, playerId, others);
    }

    /**
     * Get persisted players list for a session (REST /players endpoint).
     */
    public List<PlayerStateDto> getPersistedPlayers(String sessionId) {
        return persistenceService.loadAll(sessionId);
    }

    public Optional<Vec3Dto> getSpawnPosition(String playerId, String sessionId) {
        return persistenceService.loadPosition(playerId, sessionId);
    }
}
