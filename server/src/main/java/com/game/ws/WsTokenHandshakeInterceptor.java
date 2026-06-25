package com.game.ws;

import java.util.Map;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import com.game.auth.TokenService;
import com.game.auth.UserStore;

/**
 * Validates the bearer token on the WS upgrade request.
 * Token passed as ?token=<value> query parameter.
 * Rejects with HTTP 401 if missing/invalid (no socket is opened) — API.md §6.1.
 */
@Component
public class WsTokenHandshakeInterceptor implements HandshakeInterceptor {

    public static final String ATTR_USER = "wsUser";

    private final TokenService tokenService;

    public WsTokenHandshakeInterceptor(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {

        String query = request.getURI().getQuery();
        String token = extractToken(query);

        Optional<UserStore.UserRecord> user = tokenService.validate(token);
        if (user.isEmpty()) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false; // reject upgrade, no socket opened
        }

        attributes.put(ATTR_USER, user.get());
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request,
                               ServerHttpResponse response,
                               WebSocketHandler wsHandler,
                               Exception exception) {
        // no-op
    }

    private String extractToken(String query) {
        if (query == null) return null;
        for (String param : query.split("&")) {
            if (param.startsWith("token=")) {
                return param.substring(6);
            }
        }
        return null;
    }
}
