package com.game.persistence;

import java.io.Serializable;
import java.util.Objects;

public class PlayerStateId implements Serializable {

    private String playerId;
    private String sessionId;

    public PlayerStateId() {}

    public PlayerStateId(String playerId, String sessionId) {
        this.playerId = playerId;
        this.sessionId = sessionId;
    }

    public String getPlayerId() { return playerId; }
    public void setPlayerId(String playerId) { this.playerId = playerId; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PlayerStateId)) return false;
        PlayerStateId that = (PlayerStateId) o;
        return Objects.equals(playerId, that.playerId) && Objects.equals(sessionId, that.sessionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(playerId, sessionId);
    }
}
