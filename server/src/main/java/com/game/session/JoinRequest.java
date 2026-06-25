package com.game.session;

public class JoinRequest {

    private String sessionId = "lobby";

    public String getSessionId() {
        return sessionId == null || sessionId.isBlank() ? "lobby" : sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }
}
