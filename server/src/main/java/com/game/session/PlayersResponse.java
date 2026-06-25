package com.game.session;

import java.util.List;

import com.game.dto.PlayerStateDto;

public class PlayersResponse {

    private List<PlayerStateDto> players;

    public PlayersResponse(List<PlayerStateDto> players) {
        this.players = players;
    }

    public List<PlayerStateDto> getPlayers() {
        return players;
    }
}
