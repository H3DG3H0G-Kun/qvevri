package com.game.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/**
 * Persists the last known transform for a player in a session.
 * Matches the "player_state" table schema from API.md §5.
 */
@Entity
@Table(name = "player_state")
@IdClass(PlayerStateId.class)
public class PlayerStateEntity {

    @Id
    @Column(name = "player_id", nullable = false)
    private String playerId;

    @Id
    @Column(name = "session_id", nullable = false)
    private String sessionId;

    @Column(name = "display_name")
    private String displayName;

    @Column(name = "x")
    private double x;

    @Column(name = "y")
    private double y;

    @Column(name = "z")
    private double z;

    @Column(name = "rotation_y")
    private double rotationY;

    @Column(name = "updated_at")
    private long updatedAt;

    public PlayerStateEntity() {}

    public String getPlayerId() { return playerId; }
    public void setPlayerId(String playerId) { this.playerId = playerId; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public double getX() { return x; }
    public void setX(double x) { this.x = x; }

    public double getY() { return y; }
    public void setY(double y) { this.y = y; }

    public double getZ() { return z; }
    public void setZ(double z) { this.z = z; }

    public double getRotationY() { return rotationY; }
    public void setRotationY(double rotationY) { this.rotationY = rotationY; }

    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
}
