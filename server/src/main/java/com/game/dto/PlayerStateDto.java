package com.game.dto;

/**
 * Matches the PlayerState wire shape from API.md §2.
 */
public class PlayerStateDto {

    private String playerId;
    private String displayName;
    private Vec3Dto position;
    private double rotationY;
    private long t;

    public PlayerStateDto() {}

    public PlayerStateDto(String playerId, String displayName, Vec3Dto position, double rotationY, long t) {
        this.playerId = playerId;
        this.displayName = displayName;
        this.position = position;
        this.rotationY = rotationY;
        this.t = t;
    }

    public String getPlayerId() { return playerId; }
    public void setPlayerId(String playerId) { this.playerId = playerId; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public Vec3Dto getPosition() { return position; }
    public void setPosition(Vec3Dto position) { this.position = position; }

    public double getRotationY() { return rotationY; }
    public void setRotationY(double rotationY) { this.rotationY = rotationY; }

    public long getT() { return t; }
    public void setT(long t) { this.t = t; }
}
