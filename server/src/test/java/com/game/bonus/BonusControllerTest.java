package com.game.bonus;

import com.game.account.AccountTestHelper;
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

import static com.game.account.AccountTestHelper.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Integration tests for /api/bonus/** (INTEGRATION PASS — central aggregator).
 * Aggregates career + skills + buildings + completed research + active staff,
 * each normalized to its canonical unit. Default test character is a GROWER
 * (yieldMult 0.15, sellMarginMult 0.0).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class BonusControllerTest {

    @LocalServerPort int port;
    @Autowired TestRestTemplate rest;

    private String base() { return "http://localhost:" + port; }
    private static String uniqueName() { return "bn_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8); }

    @Test @DisplayName("grower_hasCareerYield_noSellMargin")
    @SuppressWarnings("unchecked")
    void grower_hasCareerYield_noSellMargin() {
        String token = registerAndGetToken(rest, base());
        long cid = createCharacter(rest, base(), token, uniqueName()).longValue(); // GROWER
        Map<String, Object> b = bonuses(token, cid);
        assertThat(((Number) b.get("YIELD")).doubleValue()).isCloseTo(0.15, within(1e-9));
        assertThat(((Number) b.get("SELL_MARGIN")).doubleValue()).isCloseTo(0.0, within(1e-9));
    }

    @Test @DisplayName("careerPlusSkill_yieldStacks")
    @SuppressWarnings("unchecked")
    void careerPlusSkill_yieldStacks() {
        String token = registerAndGetToken(rest, base());
        long cid = createCharacter(rest, base(), token, uniqueName()).longValue(); // GROWER (0.15)
        // green_thumb skill adds YIELD 0.05
        learnSkill(token, cid, "green_thumb");
        Map<String, Object> b = bonuses(token, cid);
        assertThat(((Number) b.get("YIELD")).doubleValue())
                .as("career 0.15 + skill 0.05 = 0.20")
                .isCloseTo(0.20, within(1e-9));
    }

    @Test @DisplayName("skill_addsSellMargin")
    @SuppressWarnings("unchecked")
    void skill_addsSellMargin() {
        String token = registerAndGetToken(rest, base());
        long cid = createCharacter(rest, base(), token, uniqueName()).longValue(); // GROWER (0.0 sell)
        learnSkill(token, cid, "shrewd_bargainer"); // SELL_MARGIN 0.05
        Map<String, Object> b = bonuses(token, cid);
        assertThat(((Number) b.get("SELL_MARGIN")).doubleValue()).isCloseTo(0.05, within(1e-9));
    }

    @Test @DisplayName("merchant_hasSellMargin")
    @SuppressWarnings("unchecked")
    void merchant_hasSellMargin() {
        String token = registerAndGetToken(rest, base());
        long cid = createCharacterWithCareer(token, uniqueName(), "MERCHANT");
        Map<String, Object> b = bonuses(token, cid);
        assertThat(((Number) b.get("SELL_MARGIN")).doubleValue())
                .as("Merchant career sell margin")
                .isCloseTo(0.20, within(1e-9));
    }

    @Test @DisplayName("completedResearch_addsYield")
    @SuppressWarnings("unchecked")
    void completedResearch_addsYield() {
        String token = registerAndGetToken(rest, base());
        long cid = createCharacter(rest, base(), token, uniqueName()).longValue(); // GROWER (0.15)
        // improved_pruning: 30 GEL, 3 days, YIELD_BONUS 0.08
        startResearch(token, cid, "improved_pruning");
        advanceClock(3);                  // let it complete
        Map<String, Object> b = bonuses(token, cid);
        assertThat(((Number) b.get("YIELD")).doubleValue())
                .as("career 0.15 + completed research 0.08 = 0.23")
                .isCloseTo(0.23, within(1e-9));
    }

    @Test @DisplayName("inProgressResearch_doesNotCountYet")
    @SuppressWarnings("unchecked")
    void inProgressResearch_doesNotCountYet() {
        String token = registerAndGetToken(rest, base());
        long cid = createCharacter(rest, base(), token, uniqueName()).longValue();
        startResearch(token, cid, "improved_pruning"); // RESEARCHING, not COMPLETE
        Map<String, Object> b = bonuses(token, cid);
        assertThat(((Number) b.get("YIELD")).doubleValue())
                .as("only career yield counts until research completes")
                .isCloseTo(0.15, within(1e-9));
    }

    @Test @DisplayName("merchantClerk_addsSellMargin")
    @SuppressWarnings("unchecked")
    void merchantClerk_addsSellMargin() {
        String token = registerAndGetToken(rest, base());
        long cid = createCharacter(rest, base(), token, uniqueName()).longValue(); // GROWER (0.0 sell)
        hireStaff(token, cid, "merchant_clerk"); // SALES 12 (% ) → SELL_MARGIN 0.12
        Map<String, Object> b = bonuses(token, cid);
        assertThat(((Number) b.get("SELL_MARGIN")).doubleValue())
                .as("merchant clerk +12% sales → 0.12 SELL_MARGIN")
                .isCloseTo(0.12, within(1e-9));
    }

    @Test @DisplayName("vineyardHand_stacksYieldWithCareer")
    @SuppressWarnings("unchecked")
    void vineyardHand_stacksYieldWithCareer() {
        String token = registerAndGetToken(rest, base());
        long cid = createCharacter(rest, base(), token, uniqueName()).longValue(); // GROWER (0.15)
        hireStaff(token, cid, "vineyard_hand"); // YIELD 10 (%) → 0.10
        Map<String, Object> b = bonuses(token, cid);
        assertThat(((Number) b.get("YIELD")).doubleValue())
                .as("career 0.15 + staff 0.10 = 0.25")
                .isCloseTo(0.25, within(1e-9));
    }

    @Test @DisplayName("ownership_otherAccount_404")
    void ownership_otherAccount_404() {
        String tokenA = registerAndGetToken(rest, base());
        long cidA = createCharacter(rest, base(), tokenA, uniqueName()).longValue();
        String tokenB = registerAndGetToken(rest, base());
        ResponseEntity<String> resp = rest.exchange(base() + "/api/bonus/" + cidA,
                HttpMethod.GET, getWithToken(tokenB), String.class);
        assertThat(resp.getStatusCode().value()).isIn(403, 404);
    }

    // ── helpers ──
    @SuppressWarnings("unchecked")
    private Map<String, Object> bonuses(String token, long cid) {
        ResponseEntity<Map> resp = rest.exchange(base() + "/api/bonus/" + cid,
                HttpMethod.GET, getWithToken(token), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return resp.getBody();
    }

    private void startResearch(String token, long cid, String nodeId) {
        ResponseEntity<Map> resp = rest.postForEntity(base() + "/api/research/" + nodeId + "/start",
                withToken(Map.of("characterId", cid), token), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private void hireStaff(String token, long cid, String staffTypeId) {
        ResponseEntity<Map> resp = rest.postForEntity(base() + "/api/labor/hire",
                withToken(Map.of("characterId", cid, "staffTypeId", staffTypeId), token), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private void advanceClock(int days) {
        ResponseEntity<Map> resp = rest.postForEntity(base() + "/api/world/advance",
                new HttpEntity<>(Map.of("days", days)), Map.class);
        assertThat(resp.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.CREATED);
    }

    private void learnSkill(String token, long cid, String skillId) {
        ResponseEntity<Map> resp = rest.postForEntity(base() + "/api/skill/" + skillId + "/learn",
                withToken(Map.of("characterId", cid), token), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @SuppressWarnings("unchecked")
    private long createCharacterWithCareer(String token, String name, String career) {
        Map<String, String> body = Map.of("name", name, "careerType", career, "homeRegion", "KAKHETI");
        ResponseEntity<Map> resp = rest.postForEntity(base() + "/api/characters",
                withToken(body, token), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return ((Number) resp.getBody().get("id")).longValue();
    }
}
