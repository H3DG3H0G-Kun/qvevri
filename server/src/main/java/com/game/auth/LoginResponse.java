package com.game.auth;

public class LoginResponse {

    private String token;
    private String playerId;
    private String displayName;
    private long expiresInSec;

    public LoginResponse(String token, String playerId, String displayName, long expiresInSec) {
        this.token = token;
        this.playerId = playerId;
        this.displayName = displayName;
        this.expiresInSec = expiresInSec;
    }

    public String getToken() {
        return token;
    }

    public String getPlayerId() {
        return playerId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public long getExpiresInSec() {
        return expiresInSec;
    }
}
