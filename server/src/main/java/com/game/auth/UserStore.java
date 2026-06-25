package com.game.auth;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * In-memory user store. Seeded at startup with dev users.
 * No secrets hard-coded: seed credentials are passed in at runtime.
 */
@Component
public class UserStore {

    /** username -> UserRecord */
    private final Map<String, UserRecord> users = new ConcurrentHashMap<>();

    /**
     * Seed a user at startup. Called from TokenService initialisation.
     */
    public void seed(String username, String passwordHash, String playerId, String displayName) {
        users.put(username, new UserRecord(playerId, username, displayName, passwordHash));
    }

    public Optional<UserRecord> find(String username) {
        return Optional.ofNullable(users.get(username));
    }

    /**
     * Register a new user (auto-register path). Stores a bcrypt-hashed password.
     */
    public UserRecord register(String username, String passwordHash, String playerId) {
        UserRecord record = new UserRecord(playerId, username, username, passwordHash);
        users.put(username, record);
        return record;
    }

    public record UserRecord(String playerId, String username, String displayName, String passwordHash) {}
}
