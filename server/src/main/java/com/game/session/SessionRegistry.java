package com.game.session;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * Tracks which playerIds are in which sessions.
 * Thread-safe. Sessions are created on demand (auto-create per §6.3).
 */
@Component
public class SessionRegistry {

    public static final int SESSION_CAP = 16;

    /** sessionId -> set of playerIds */
    private final Map<String, Set<String>> sessions = new ConcurrentHashMap<>();

    /**
     * Attempt to join a session. Returns false if at cap.
     * Creates the session if it does not yet exist.
     */
    public boolean join(String sessionId, String playerId) {
        Set<String> members = sessions.computeIfAbsent(sessionId,
                k -> ConcurrentHashMap.newKeySet());
        if (members.contains(playerId)) {
            return true; // idempotent
        }
        if (members.size() >= SESSION_CAP) {
            return false;
        }
        members.add(playerId);
        return true;
    }

    public void leave(String sessionId, String playerId) {
        Set<String> members = sessions.get(sessionId);
        if (members != null) {
            members.remove(playerId);
        }
    }

    public Set<String> members(String sessionId) {
        return sessions.getOrDefault(sessionId, Set.of());
    }

    public boolean exists(String sessionId) {
        return sessions.containsKey(sessionId);
    }
}
