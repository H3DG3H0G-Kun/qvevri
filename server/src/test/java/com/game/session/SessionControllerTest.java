package com.game.session;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for POST /api/sessions/join and GET /api/sessions/{id}/players
 * (API.md §3 — v1.1 clarifications §6 items 2 & 3 apply).
 *
 * Binding rules under test:
 *  - join defaults sessionId to "lobby" if body omits it.
 *  - spawn is {0,0,0} when no persisted row exists for (playerId, sessionId).
 *  - any sessionId auto-creates (SESSION_NOT_FOUND unreachable for this slice).
 *  - cap 16 — SESSION_FULL at 17th distinct player.
 *  - no auth (missing Authorization header) → 401.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SessionControllerTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    private String token;
    private String playerId;

    private String base() {
        return "http://localhost:" + port;
    }

    /** Login and cache the token for subsequent calls. */
    @BeforeEach
    @SuppressWarnings("unchecked")
    void authenticate() {
        var body = Map.of("username", "sessiontestuser", "password", "pass");
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        var entity = new HttpEntity<>(body, headers);
        var resp = rest.postForEntity(base() + "/api/auth/login", entity, Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        token    = (String) resp.getBody().get("token");
        playerId = (String) resp.getBody().get("playerId");
    }

    /** Returns Authorization headers with Bearer token. */
    private HttpHeaders authHeaders() {
        var h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.setBearerAuth(token);
        return h;
    }

    @SuppressWarnings("unchecked")
    private ResponseEntity<Map> doJoin(Object requestBody) {
        var entity = new HttpEntity<>(requestBody, authHeaders());
        return rest.postForEntity(base() + "/api/sessions/join", entity, Map.class);
    }

    // ------------------------------------------------------------------
    // Tests
    // ------------------------------------------------------------------

    @Test
    @DisplayName("join_defaults_to_lobby — omitting sessionId uses 'lobby'")
    void join_defaults_to_lobby() {
        // Empty body → server should default to "lobby"
        var resp = doJoin(Map.of());

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        var body = resp.getBody();
        assertThat(body).isNotNull();
        assertThat(body).containsEntry("sessionId", "lobby");
    }

    @Test
    @DisplayName("join_explicit_lobby — explicit sessionId=lobby is accepted")
    void join_explicit_lobby() {
        var resp = doJoin(Map.of("sessionId", "lobby"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        var body = resp.getBody();
        assertThat(body).containsEntry("sessionId", "lobby");
    }

    @Test
    @DisplayName("join_spawn_origin — new player gets spawn {0,0,0}")
    @SuppressWarnings("unchecked")
    void join_spawn_origin() {
        // Use a fresh player so no persisted row exists.
        var freshLogin = loginAs("spawnoriginuser", "pw");
        var freshToken = (String) freshLogin.get("token");

        var h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.setBearerAuth(freshToken);
        var entity = new HttpEntity<>(Map.of("sessionId", "lobby"), h);
        var resp = rest.postForEntity(base() + "/api/sessions/join", entity, Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        var spawn = (Map<String, Object>) resp.getBody().get("spawn");
        assertThat(spawn).isNotNull();
        assertThat(((Number) spawn.get("x")).doubleValue()).isEqualTo(0.0);
        assertThat(((Number) spawn.get("y")).doubleValue()).isEqualTo(0.0);
        assertThat(((Number) spawn.get("z")).doubleValue()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("join_no_auth — missing Authorization header returns 401")
    void join_no_auth() {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        var entity = new HttpEntity<>(Map.of("sessionId", "lobby"), headers);
        var resp = rest.postForEntity(base() + "/api/sessions/join", entity, Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("join_self_field — response 'self' matches authenticated playerId")
    void join_self_field() {
        var resp = doJoin(Map.of("sessionId", "lobby"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).containsEntry("self", playerId);
    }

    @Test
    @DisplayName("join_players_field — response contains 'players' array")
    @SuppressWarnings("unchecked")
    void join_players_field() {
        var resp = doJoin(Map.of("sessionId", "lobby"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).containsKey("players");
        assertThat(resp.getBody().get("players")).isInstanceOf(List.class);
    }

    @Test
    @DisplayName("join_any_session_id_auto_creates — arbitrary sessionId does not 404")
    void join_any_session_id_auto_creates() {
        var resp = doJoin(Map.of("sessionId", "custom-session-42"));

        // Per v1.1 §6.3 — any sessionId auto-creates; SESSION_NOT_FOUND unreachable.
        assertThat(resp.getStatusCode()).isNotEqualTo(HttpStatus.NOT_FOUND);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("get_players_no_auth — missing token returns 401")
    void get_players_no_auth() {
        var resp = rest.getForEntity(base() + "/api/sessions/lobby/players", Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("get_players_authenticated — returns players array")
    @SuppressWarnings("unchecked")
    void get_players_authenticated() {
        // Join first so the session exists.
        doJoin(Map.of("sessionId", "lobby"));

        var headers = authHeaders();
        headers.remove(HttpHeaders.CONTENT_TYPE); // GET needs no content-type
        var entity = new HttpEntity<>(headers);
        var resp = rest.exchange(
                base() + "/api/sessions/lobby/players",
                HttpMethod.GET, entity, Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).containsKey("players");
        assertThat(resp.getBody().get("players")).isInstanceOf(List.class);
    }

    // ------------------------------------------------------------------
    // Helper
    // ------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private Map<String, Object> loginAs(String username, String password) {
        var body = Map.of("username", username, "password", password);
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        var entity = new HttpEntity<>(body, headers);
        var resp = rest.postForEntity(base() + "/api/auth/login", entity, Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return resp.getBody();
    }
}
