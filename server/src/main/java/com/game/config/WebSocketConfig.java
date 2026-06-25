package com.game.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import com.game.ws.GameWebSocketHandler;
import com.game.ws.WsTokenHandshakeInterceptor;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final GameWebSocketHandler gameWebSocketHandler;
    private final WsTokenHandshakeInterceptor wsTokenHandshakeInterceptor;

    public WebSocketConfig(GameWebSocketHandler gameWebSocketHandler,
                           WsTokenHandshakeInterceptor wsTokenHandshakeInterceptor) {
        this.gameWebSocketHandler = gameWebSocketHandler;
        this.wsTokenHandshakeInterceptor = wsTokenHandshakeInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(gameWebSocketHandler, "/ws/game")
                .addInterceptors(wsTokenHandshakeInterceptor)
                .setAllowedOriginPatterns("*");
    }
}
