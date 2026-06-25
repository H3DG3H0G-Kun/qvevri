package com.game.account;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for POST /api/account/register and POST /api/account/login
 * (MMO-CORE-SPEC §4 — Account endpoints).
 *
 * All tests use random usernames to avoid cross-test collisions in the shared
 * Spring context / in-memory H2 database.
 *
 * Assumptions (noted per spec):
 *  - register returns 201 with body { accountId, token }.
 *  - login returns 200 with body { accountId, token }.
 *  - duplicate username on register returns 400.
 *  - wrong password on login returns 401 with error.code INVALID_CREDENTIALS.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AccountControllerTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    // ── Helpers ──────────────────────────────────────────────────────────────

    private String base() {
        return "http://localhost:" + port;
    }

    /** Generate a fresh unique username so tests never collide. */
    private static String randomUsername() {
        return "u_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    @SuppressWarnings("unchecked")
    private ResponseEntity<Map> doRegister(String email, String username, String password) {
        var body = Map.of("email", email, "username", username, "password", password);
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return rest.postForEntity(base() + "/api/account/register",
                new HttpEntity<>(body, headers), Map.class);
    }

    @SuppressWarnings("unchecked")
    private ResponseEntity<Map> doLogin(String username, String password) {
        var body = Map.of("username", username, "password", password);
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return rest.postForEntity(base() + "/api/account/login",
                new HttpEntity<>(body, headers), Map.class);
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("register_returns201_withToken")
    void register_returns201_withToken() {
        String username = randomUsername();
        ResponseEntity<Map> resp = doRegister(username + "@example.com", username, "password1");

        assertThat(resp.getStatusCode())
                .as("POST /api/account/register must return 201 Created")
                .isEqualTo(HttpStatus.CREATED);

        Map<String, Object> body = resp.getBody();
        assertThat(body).as("Response body must not be null").isNotNull();
        assertThat(body).containsKey("accountId");
        assertThat(body.get("accountId")).isNotNull();
        assertThat(body).containsKey("token");
        assertThat((String) body.get("token")).isNotBlank();
    }

    @Test
    @DisplayName("login_afterRegister_succeeds")
    void login_afterRegister_succeeds() {
        String username = randomUsername();
        String password = "p@ssw0rd!";

        // Register first
        ResponseEntity<Map> reg = doRegister(username + "@example.com", username, password);
        assertThat(reg.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Then login
        ResponseEntity<Map> login = doLogin(username, password);
        assertThat(login.getStatusCode())
                .as("POST /api/account/login after valid registration must return 200")
                .isEqualTo(HttpStatus.OK);

        Map<String, Object> body = login.getBody();
        assertThat(body).isNotNull();
        assertThat(body).containsKey("accountId");
        assertThat(body.get("accountId")).isNotNull();
        assertThat(body).containsKey("token");
        assertThat((String) body.get("token")).isNotBlank();
    }

    @Test
    @DisplayName("login_badPassword_401")
    void login_badPassword_401() {
        String username = randomUsername();

        // Register with a known password
        doRegister(username + "@example.com", username, "correctPassword");

        // Attempt login with wrong password
        ResponseEntity<Map> resp = doLogin(username, "wrongPassword");

        assertThat(resp.getStatusCode())
                .as("POST /api/account/login with wrong password must return 401")
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        Map<String, Object> body = resp.getBody();
        assertThat(body).isNotNull();
        assertThat(body).containsKey("error");

        @SuppressWarnings("unchecked")
        Map<String, Object> error = (Map<String, Object>) body.get("error");
        assertThat(error).containsKey("code");
        assertThat(error.get("code")).isEqualTo("INVALID_CREDENTIALS");
    }

    @Test
    @DisplayName("register_duplicateUsername_400")
    void register_duplicateUsername_400() {
        String username = randomUsername();

        // First registration succeeds
        ResponseEntity<Map> first = doRegister(username + "@a.com", username, "pass1");
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Second registration with the same username must be rejected
        ResponseEntity<Map> second = doRegister(username + "2@b.com", username, "pass2");
        assertThat(second.getStatusCode())
                .as("Registering with a duplicate username must return 400")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
