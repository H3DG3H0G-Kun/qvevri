package com.game.session;

import java.util.List;

import com.game.dto.PlayerStateDto;
import com.game.dto.Vec3Dto;

public class JoinResponse {

    private String sessionId;
    private Vec3Dto spawn;
    private String self;
    private List<PlayerStateDto> players;

    public JoinResponse(String sessionId, Vec3Dto spawn, String self, List<PlayerStateDto> players) {
        this.sessionId = sessionId;
        this.spawn = spawn;
        this.self = self;
        this.players = players;
    }

    public String getSessionId() { return sessionId; }
    public Vec3Dto getSpawn() { return spawn; }
    public String getSelf() { return self; }
    public List<PlayerStateDto> getPlayers() { return players; }
}
