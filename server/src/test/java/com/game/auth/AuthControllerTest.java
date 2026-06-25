package com.game.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for POST /api/auth/login (API.md §3).
 *
 * Uses @SpringBootTest(RANDOM_PORT) + TestRestTemplate so the full Spring
 * Security filter chain is exercised.  DB is in-memory H2 via @ActiveProfiles("test").
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AuthControllerTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    // ------------------------------------------------------------------
    // Helper
    // ------------------------------------------------------------------

    private String base() {
        return "http://localhost:" + port;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> postLogin(String username, String password) {
        var body = Map.of("username", username, "password", password);
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        var entity = new HttpEntity<>(body, headers);
        ResponseEntity<Map> resp = rest.postForEntity(base() + "/api/auth/login", entity, Map.class);
        // Store status for assertions via a thread-local workaround isn't needed —
        // we return the map and let each test re-call as needed.
        return resp.getBody();
    }

    /** Re-usable version that also returns the status code. */
    @SuppressWarnings("unchecked")
    private ResponseEntity<Map> postLoginRaw(String username, String password) {
        var body = Map.of("username", username, "password", password);
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        var entity = new HttpEntity<>(body, headers);
        return rest.postForEntity(base() + "/api/auth/login", entity, Map.class);
    }

    // ------------------------------------------------------------------
    // Tests
    // ------------------------------------------------------------------

    @Test
    @DisplayName("login_valid — 200 OK, returns token + playerId + displayName + expiresInSec")
    void login_valid() {
        // auto-register is enabled in test profile; any new user is created
        var resp = postLoginRaw("testuser", "secret");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);

        var body = resp.getBody();
        assertThat(body).isNotNull();
        assertThat(body).containsKey("token");
        assertThat(body.get("token")).isNotNull();
        assertThat((String) body.get("token")).isNotBlank();

        assertThat(body).containsKey("playerId");
        assertThat((String) body.get("playerId")).isNotBlank();

        assertThat(body).containsKey("displayName");
        assertThat(body.get("displayName")).isEqualTo("testuser");

        assertThat(body).containsKey("expiresInSec");
        assertThat((Integer) body.get("expiresInSec")).isPositive();
    }

    @Test
    @DisplayName("login_invalid — 401 INVALID_CREDENTIALS for wrong password")
    void login_invalid() {
        // First register the user with a known password.
        postLoginRaw("badpassuser", "correctpass");

        // Then try with wrong password.
        var resp = postLoginRaw("badpassuser", "wrongpass");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        var body = resp.getBody();
        assertThat(body).isNotNull();
        assertThat(body).containsKey("error");

        @SuppressWarnings("unchecked")
        var error = (Map<String, Object>) body.get("error");
        assertThat(error).containsEntry("code", "INVALID_CREDENTIALS");
        assertThat(error).containsKey("message");
    }

    @Test
    @DisplayName("login_invalid_missing_fields — 400 or 401 for empty body fields")
    void login_invalid_missing_fields() {
        // Empty username/password — server should not return 200.
        var resp = postLoginRaw("", "");
        assertThat(resp.getStatusCode().value()).isGreaterThanOrEqualTo(400);
    }

    @Test
    @DisplayName("login_repeated_valid — same credentials return consistent playerId")
    void login_repeated_valid() {
        postLoginRaw("stableuser", "mypass"); // register
        var first  = postLoginRaw("stableuser", "mypass");
        var second = postLoginRaw("stableuser", "mypass");

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);

        var id1 = first.getBody().get("playerId");
        var id2 = second.getBody().get("playerId");
        assertThat(id1).isEqualTo(id2);
    }
}
