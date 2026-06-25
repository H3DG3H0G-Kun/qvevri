package com.game.persistence;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.game.dto.PlayerStateDto;
import com.game.dto.Vec3Dto;

/**
 * Throttled persistence: at most 1 DB write per player per 500 ms.
 */
@Service
public class PlayerStatePersistenceService {

    private static final long THROTTLE_MS = 500L;

    private final PlayerStateRepository repository;

    /** key: "playerId:sessionId" -> last flush timestamp */
    private final Map<String, Long> lastWriteMs = new ConcurrentHashMap<>();

    public PlayerStatePersistenceService(PlayerStateRepository repository) {
        this.repository = repository;
    }

    /**
     * Persist or update the player state, subject to the 500 ms throttle.
     */
    public void persist(String playerId, String sessionId, PlayerStateDto state) {
        String key = playerId + ":" + sessionId;
        long now = System.currentTimeMillis();
        Long last = lastWriteMs.get(key);
        if (last != null && (now - last) < THROTTLE_MS) {
            return; // throttled
        }
        lastWriteMs.put(key, now);
        doWrite(playerId, sessionId, state, now);
    }

    /**
     * Force a write without throttle — used on join and disconnect to capture position.
     */
    public void persistImmediate(String playerId, String sessionId, PlayerStateDto state) {
        long now = System.currentTimeMillis();
        lastWriteMs.put(playerId + ":" + sessionId, now);
        doWrite(playerId, sessionId, state, now);
    }

    private void doWrite(String playerId, String sessionId, PlayerStateDto state, long now) {
        PlayerStateId id = new PlayerStateId(playerId, sessionId);
        PlayerStateEntity entity = repository.findById(id).orElse(new PlayerStateEntity());
        entity.setPlayerId(playerId);
        entity.setSessionId(sessionId);
        entity.setDisplayName(state.getDisplayName());
        entity.setX(state.getPosition().getX());
        entity.setY(state.getPosition().getY());
        entity.setZ(state.getPosition().getZ());
        entity.setRotationY(state.getRotationY());
        entity.setUpdatedAt(now);
        repository.save(entity);
    }

    /**
     * Load persisted position for a player in a session, if any.
     */
    public Optional<Vec3Dto> loadPosition(String playerId, String sessionId) {
        return repository.findById(new PlayerStateId(playerId, sessionId))
                .map(e -> new Vec3Dto(e.getX(), e.getY(), e.getZ()));
    }

    /**
     * Load all persisted player states for a session (for the REST /players endpoint).
     */
    public List<PlayerStateDto> loadAll(String sessionId) {
        return repository.findBySessionId(sessionId).stream()
                .map(e -> new PlayerStateDto(
                        e.getPlayerId(),
                        e.getDisplayName(),
                        new Vec3Dto(e.getX(), e.getY(), e.getZ()),
                        e.getRotationY(),
                        e.getUpdatedAt()))
                .toList();
    }
}
