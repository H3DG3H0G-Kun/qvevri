package com.game.skill;

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
 * Integration tests for /api/skill/** (SKILL lane).
 * Talents: green_thumb(1,YIELD), master_palate(2,QUALITY), shrewd_bargainer(2),
 * frugal_logistics(2), deep_cellar(3), haggler(2), vintner_eye(2),
 * vine_whisperer(3, prereq green_thumb). Starting points = 5.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SkillControllerTest {

    @LocalServerPort int port;
    @Autowired TestRestTemplate rest;

    private String base() { return "http://localhost:" + port; }
    private static String uniqueName() { return "sk_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8); }

    private long newChar() {
        String token = registerAndGetToken(rest, base());
        long cid = createCharacter(rest, base(), token, uniqueName()).longValue();
        return cid;
    }

    @Test @DisplayName("catalog_returnsEightTalents")
    @SuppressWarnings("unchecked")
    void catalog_returnsEightTalents() {
        String token = registerAndGetToken(rest, base());
        ResponseEntity<List> resp = rest.exchange(base() + "/api/skill/catalog",
                HttpMethod.GET, getWithToken(token), List.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull().hasSize(8);
    }

    @Test @DisplayName("profile_lazyCreates_fivePoints")
    @SuppressWarnings("unchecked")
    void profile_lazyCreates_fivePoints() {
        String token = registerAndGetToken(rest, base());
        long cid = createCharacter(rest, base(), token, uniqueName()).longValue();
        Map<String, Object> p = profile(token, cid);
        assertThat(((Number) p.get("totalPoints")).intValue()).isEqualTo(5);
        assertThat(((Number) p.get("availablePoints")).intValue()).isEqualTo(5);
        assertThat((List<?>) p.get("learned")).isEmpty();
    }

    @Test @DisplayName("learn_spendsPoints_andRecords")
    @SuppressWarnings("unchecked")
    void learn_spendsPoints_andRecords() {
        String token = registerAndGetToken(rest, base());
        long cid = createCharacter(rest, base(), token, uniqueName()).longValue();
        Map<String, Object> after = learn(token, cid, "green_thumb", HttpStatus.OK);
        assertThat(((Number) after.get("spentPoints")).intValue()).isEqualTo(1);
        assertThat(((Number) after.get("availablePoints")).intValue()).isEqualTo(4);
        assertThat((List<?>) after.get("learned")).hasSize(1);
    }

    @Test @DisplayName("prereq_notMet_400")
    void prereq_notMet_400() {
        String token = registerAndGetToken(rest, base());
        long cid = createCharacter(rest, base(), token, uniqueName()).longValue();
        // vine_whisperer needs green_thumb first
        ResponseEntity<String> resp = rest.postForEntity(
                base() + "/api/skill/vine_whisperer/learn",
                withToken(Map.of("characterId", cid), token), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test @DisplayName("insufficientPoints_400")
    void insufficientPoints_400() {
        String token = registerAndGetToken(rest, base());
        long cid = createCharacter(rest, base(), token, uniqueName()).longValue();
        learn(token, cid, "deep_cellar", HttpStatus.OK);     // 3
        learn(token, cid, "master_palate", HttpStatus.OK);   // +2 = 5 spent, 0 left
        ResponseEntity<String> resp = rest.postForEntity(
                base() + "/api/skill/green_thumb/learn",
                withToken(Map.of("characterId", cid), token), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test @DisplayName("doubleLearn_400")
    void doubleLearn_400() {
        String token = registerAndGetToken(rest, base());
        long cid = createCharacter(rest, base(), token, uniqueName()).longValue();
        learn(token, cid, "green_thumb", HttpStatus.OK);
        ResponseEntity<String> resp = rest.postForEntity(
                base() + "/api/skill/green_thumb/learn",
                withToken(Map.of("characterId", cid), token), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test @DisplayName("respec_clearsLearned_freesPoints")
    @SuppressWarnings("unchecked")
    void respec_clearsLearned_freesPoints() {
        String token = registerAndGetToken(rest, base());
        long cid = createCharacter(rest, base(), token, uniqueName()).longValue();
        learn(token, cid, "green_thumb", HttpStatus.OK);
        learn(token, cid, "haggler", HttpStatus.OK);
        ResponseEntity<Map> resp = rest.postForEntity(
                base() + "/api/skill/respec",
                withToken(Map.of("characterId", cid), token), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> p = resp.getBody();
        assertThat(((Number) p.get("spentPoints")).intValue()).isEqualTo(0);
        assertThat(((Number) p.get("availablePoints")).intValue()).isEqualTo(5);
        assertThat((List<?>) p.get("learned")).isEmpty();
    }

    @Test @DisplayName("bonuses_aggregateLearned")
    @SuppressWarnings("unchecked")
    void bonuses_aggregateLearned() {
        String token = registerAndGetToken(rest, base());
        long cid = createCharacter(rest, base(), token, uniqueName()).longValue();
        learn(token, cid, "green_thumb", HttpStatus.OK);   // YIELD 0.05
        learn(token, cid, "master_palate", HttpStatus.OK); // QUALITY 0.05
        ResponseEntity<Map> resp = rest.exchange(
                base() + "/api/skill/bonuses/" + cid,
                HttpMethod.GET, getWithToken(token), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> b = resp.getBody();
        assertThat(b).containsKey("YIELD").containsKey("QUALITY");
        assertThat(((Number) b.get("YIELD")).doubleValue()).isEqualTo(0.05);
    }

    @Test @DisplayName("ownership_otherAccount_404")
    void ownership_otherAccount_404() {
        String tokenA = registerAndGetToken(rest, base());
        long cidA = createCharacter(rest, base(), tokenA, uniqueName()).longValue();
        String tokenB = registerAndGetToken(rest, base());
        ResponseEntity<String> resp = rest.exchange(
                base() + "/api/skill/" + cidA,
                HttpMethod.GET, getWithToken(tokenB), String.class);
        assertThat(resp.getStatusCode().value()).isIn(403, 404);
    }

    // ── helpers ──
    @SuppressWarnings("unchecked")
    private Map<String, Object> profile(String token, long cid) {
        ResponseEntity<Map> resp = rest.exchange(base() + "/api/skill/" + cid,
                HttpMethod.GET, getWithToken(token), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return resp.getBody();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> learn(String token, long cid, String skillId, HttpStatus expect) {
        ResponseEntity<Map> resp = rest.postForEntity(
                base() + "/api/skill/" + skillId + "/learn",
                withToken(Map.of("characterId", cid), token), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(expect);
        return resp.getBody();
    }
}
