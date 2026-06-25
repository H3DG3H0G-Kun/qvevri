package com.game.ws;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Broadcasts the state snapshot to all active sessions at ~10/s.
 * fixedDelay=100ms ensures ~10 ticks per second.
 */
@Component
public class GameTickScheduler {

    private final GameWebSocketHandler webSocketHandler;

    public GameTickScheduler(GameWebSocketHandler webSocketHandler) {
        this.webSocketHandler = webSocketHandler;
    }

    @Scheduled(fixedDelay = 100)
    public void tick() {
        for (String sessionId : webSocketHandler.activeSessions()) {
            webSocketHandler.broadcastState(sessionId);
        }
    }
}
