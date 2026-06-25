package com.game.account;

import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Shared helper for MMO integration tests that need a registered account and/or
 * a created character.
 *
 * Used by: AccountControllerTest, CharacterControllerTest, CellarControllerTest,
 * MarketControllerTest.
 *
 * Assumptions:
 *  - POST /api/account/register {email, username, password} → 201 {accountId, token}
 *  - POST /api/characters {name, careerType, homeRegion}    → 201 {id, ...}
 *    with Authorization: Bearer <token>
 */
public final class AccountTestHelper {

    private AccountTestHelper() { /* utility */ }

    /** Generate a guaranteed-unique username. */
    public static String randomUsername() {
        return "u_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    /**
     * Register a fresh account and return its token.
     *
     * @param rest the TestRestTemplate
     * @param base base URL including port (e.g. "http://localhost:54321")
     * @return bearer token for the new account
     */
    @SuppressWarnings("unchecked")
    public static String registerAndGetToken(TestRestTemplate rest, String base) {
        String username = randomUsername();
        Map<String, String> body = Map.of(
                "email", username + "@test.com",
                "username", username,
                "password", "pass1234");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Map> resp = rest.postForEntity(
                base + "/api/account/register",
                new HttpEntity<>(body, headers),
                Map.class);
        assertThat(resp.getStatusCode())
                .as("Helper: registration must succeed (201) for username " + username)
                .isEqualTo(HttpStatus.CREATED);
        return (String) resp.getBody().get("token");
    }

    /**
     * Register a fresh account and return { "token": ..., "accountId": ... }.
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> registerAccount(TestRestTemplate rest, String base) {
        String username = randomUsername();
        Map<String, String> body = Map.of(
                "email", username + "@test.com",
                "username", username,
                "password", "pass1234");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Map> resp = rest.postForEntity(
                base + "/api/account/register",
                new HttpEntity<>(body, headers),
                Map.class);
        assertThat(resp.getStatusCode())
                .as("Helper: registration must succeed for username " + username)
                .isEqualTo(HttpStatus.CREATED);
        return (Map<String, Object>) resp.getBody();
    }

    /**
     * Create a character for the given token and return the character id.
     *
     * @param rest      the TestRestTemplate
     * @param base      base URL
     * @param token     bearer token of the owning account
     * @param name      character name (must be unique within the account)
     * @return character id (Long serialised as Number in JSON)
     */
    @SuppressWarnings("unchecked")
    public static Number createCharacter(TestRestTemplate rest, String base,
                                         String token, String name) {
        Map<String, String> body = Map.of(
                "name", name,
                "careerType", "GROWER",
                "homeRegion", "KAKHETI");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        ResponseEntity<Map> resp = rest.postForEntity(
                base + "/api/characters",
                new HttpEntity<>(body, headers),
                Map.class);
        assertThat(resp.getStatusCode())
                .as("Helper: character creation must return 201 for name " + name)
                .isEqualTo(HttpStatus.CREATED);
        return (Number) resp.getBody().get("id");
    }

    /** Build an HttpEntity with a JSON body and Bearer token. */
    public static <T> HttpEntity<T> withToken(T body, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return new HttpEntity<>(body, headers);
    }

    /** Build an HttpEntity with only the Bearer token (no body; for GET requests). */
    public static HttpEntity<Void> getWithToken(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return new HttpEntity<>(headers);
    }
}
