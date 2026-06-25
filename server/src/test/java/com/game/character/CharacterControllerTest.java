package com.game.character;

import com.game.account.AccountTestHelper;
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
import java.util.UUID;

import static com.game.account.AccountTestHelper.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for POST/GET /api/characters (MMO-CORE-SPEC §4 — Characters).
 *
 * Assumptions:
 *  - POST /api/characters {name, careerType, homeRegion} → 201 Character
 *    with at least { id, accountId, name, careerType, homeRegion }
 *  - GET  /api/characters → Character[] for the authenticated account only
 *  - GET  /api/characters/{id} → Character or 404/403 if the character belongs
 *    to a different account (spec says 404; either 404 or 403 is accepted)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class CharacterControllerTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    private String base() {
        return "http://localhost:" + port;
    }

    private static String uniqueName() {
        return "char_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("create_withCareerAndRegion_returnsCharacter")
    @SuppressWarnings("unchecked")
    void create_withCareerAndRegion_returnsCharacter() {
        String token = registerAndGetToken(rest, base());

        String name = uniqueName();
        Map<String, String> body = Map.of(
                "name", name,
                "careerType", "GROWER",
                "homeRegion", "KAKHETI");

        ResponseEntity<Map> resp = rest.postForEntity(
                base() + "/api/characters",
                withToken(body, token),
                Map.class);

        assertThat(resp.getStatusCode())
                .as("POST /api/characters must return 201 Created")
                .isEqualTo(HttpStatus.CREATED);

        Map<String, Object> character = resp.getBody();
        assertThat(character).isNotNull();
        assertThat(character).containsKey("id");
        assertThat(character.get("id")).isNotNull();
        assertThat(character).containsKey("name");
        assertThat(character.get("name")).isEqualTo(name);
        assertThat(character).containsKey("careerType");
        assertThat(character.get("careerType")).isEqualTo("GROWER");
        assertThat(character).containsKey("homeRegion");
        assertThat(character.get("homeRegion")).isEqualTo("KAKHETI");
    }

    @Test
    @DisplayName("list_returnsOnlyOwnCharacters")
    @SuppressWarnings("unchecked")
    void list_returnsOnlyOwnCharacters() {
        // Account A: create two characters
        String tokenA = registerAndGetToken(rest, base());
        String nameA1 = uniqueName();
        String nameA2 = uniqueName();
        createCharacter(rest, base(), tokenA, nameA1);
        createCharacter(rest, base(), tokenA, nameA2);

        // Account B: create one character
        String tokenB = registerAndGetToken(rest, base());
        String nameB1 = uniqueName();
        createCharacter(rest, base(), tokenB, nameB1);

        // Account A should only see its own two characters
        ResponseEntity<List> respA = rest.exchange(
                base() + "/api/characters",
                HttpMethod.GET,
                getWithToken(tokenA),
                List.class);

        assertThat(respA.getStatusCode())
                .as("GET /api/characters must return 200")
                .isEqualTo(HttpStatus.OK);

        List<?> charactersA = respA.getBody();
        assertThat(charactersA).isNotNull();

        List<String> namesA = charactersA.stream()
                .map(c -> (String) ((Map<?, ?>) c).get("name"))
                .toList();
        assertThat(namesA)
                .as("Account A must see its own characters")
                .contains(nameA1, nameA2);
        assertThat(namesA)
                .as("Account A must NOT see Account B's character")
                .doesNotContain(nameB1);

        // Account B should only see its own character
        ResponseEntity<List> respB = rest.exchange(
                base() + "/api/characters",
                HttpMethod.GET,
                getWithToken(tokenB),
                List.class);

        assertThat(respB.getStatusCode()).isEqualTo(HttpStatus.OK);

        List<?> charactersB = respB.getBody();
        assertThat(charactersB).isNotNull();

        List<String> namesB = charactersB.stream()
                .map(c -> (String) ((Map<?, ?>) c).get("name"))
                .toList();
        assertThat(namesB)
                .as("Account B must see its own character")
                .contains(nameB1);
        assertThat(namesB)
                .as("Account B must NOT see Account A's characters")
                .doesNotContain(nameA1, nameA2);
    }

    @Test
    @DisplayName("get_otherAccountsCharacter_404or403")
    @SuppressWarnings("unchecked")
    void get_otherAccountsCharacter_404or403() {
        // Account A creates a character
        String tokenA = registerAndGetToken(rest, base());
        Number charIdA = createCharacter(rest, base(), tokenA, uniqueName());

        // Account B tries to fetch Account A's character by id
        String tokenB = registerAndGetToken(rest, base());
        ResponseEntity<Map> resp = rest.exchange(
                base() + "/api/characters/" + charIdA.longValue(),
                HttpMethod.GET,
                getWithToken(tokenB),
                Map.class);

        // Spec says 404; 403 is also acceptable per the task description
        assertThat(resp.getStatusCode().value())
                .as("GET /api/characters/{id} for another account's character must be 404 or 403")
                .isIn(403, 404);
    }

    @Test
    @DisplayName("get_ownCharacter_200")
    @SuppressWarnings("unchecked")
    void get_ownCharacter_200() {
        String token = registerAndGetToken(rest, base());
        String name = uniqueName();
        Number charId = createCharacter(rest, base(), token, name);

        ResponseEntity<Map> resp = rest.exchange(
                base() + "/api/characters/" + charId.longValue(),
                HttpMethod.GET,
                getWithToken(token),
                Map.class);

        assertThat(resp.getStatusCode())
                .as("GET /api/characters/{id} for own character must return 200")
                .isEqualTo(HttpStatus.OK);

        Map<?, ?> body = resp.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("name")).isEqualTo(name);
    }

    @Test
    @DisplayName("create_noAuth_401")
    void create_noAuth_401() {
        Map<String, String> body = Map.of(
                "name", uniqueName(),
                "careerType", "GROWER",
                "homeRegion", "KAKHETI");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Map> resp = rest.postForEntity(
                base() + "/api/characters",
                new HttpEntity<>(body, headers),
                Map.class);

        assertThat(resp.getStatusCode())
                .as("POST /api/characters without auth must return 401")
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
