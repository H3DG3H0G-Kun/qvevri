package com.game.session;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.game.auth.UserStore;
import com.game.exception.ApiException;

@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @PostMapping("/join")
    public ResponseEntity<JoinResponse> join(
            @AuthenticationPrincipal UserStore.UserRecord user,
            @RequestBody(required = false) JoinRequest request) {

        if (user == null) {
            throw ApiException.unauthorized("Authentication required");
        }

        if (request == null) {
            request = new JoinRequest();
        }

        JoinResponse response = sessionService.join(user.playerId(), user.displayName(), request.getSessionId());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{sessionId}/players")
    public ResponseEntity<PlayersResponse> getPlayers(
            @AuthenticationPrincipal UserStore.UserRecord user,
            @PathVariable String sessionId) {

        if (user == null) {
            throw ApiException.unauthorized("Authentication required");
        }

        return ResponseEntity.ok(new PlayersResponse(sessionService.getPersistedPlayers(sessionId)));
    }
}
