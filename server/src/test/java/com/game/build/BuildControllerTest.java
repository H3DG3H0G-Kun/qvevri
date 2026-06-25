package com.game.build;

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
 * Integration tests for /api/build/** (LANE BUILDINGS).
 *
 * <p>Affordability note: Characters start with 100 GEL. The happy-path tests
 * use <b>COTTAGE</b> (costGel=30, input: 1× cover_crop_seed at catalog price 14 GEL).
 * Total purchase required: buy 1× cover_crop_seed for 14 GEL → wallet becomes 86 GEL,
 * then COTTAGE costs 30 GEL → wallet becomes 56 GEL after construction. All within budget.
 *
 * <p>Scenarios covered:
 * <ol>
 *   <li>GET /api/build/catalog → returns 4 building types with expected fields.</li>
 *   <li>POST /api/build/construct (happy-path COTTAGE) — debits wallet AND consumes input good.</li>
 *   <li>POST /api/build/construct — insufficient funds → 400.</li>
 *   <li>POST /api/build/construct — missing input goods → 400.</li>
 *   <li>GET /api/build/{characterId} — lists the character's buildings.</li>
 *   <li>GET /api/build/bonuses/{characterId} — aggregates bonuses; two same-type buildings sum.</li>
 *   <li>Ownership enforced: other account → 404.</li>
 * </ol>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class BuildControllerTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    private String base() {
        return "http://localhost:" + port;
    }

    private static String uniqueName() {
        return "bld_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 1. GET /api/build/catalog → 4 building types
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("catalog_returnsFourBuildingTypes")
    @SuppressWarnings("unchecked")
    void catalog_returnsFourBuildingTypes() {
        String token = registerAndGetToken(rest, base());

        ResponseEntity<List> resp = rest.exchange(
                base() + "/api/build/catalog",
                HttpMethod.GET,
                getWithToken(token),
                List.class);

        assertThat(resp.getStatusCode())
                .as("GET /api/build/catalog must return 200")
                .isEqualTo(HttpStatus.OK);

        List<?> catalog = resp.getBody();
        assertThat(catalog)
                .as("Catalog must contain exactly 4 building types")
                .isNotNull()
                .hasSize(4);

        // Spot-check fields on the first entry
        Map<String, Object> first = (Map<String, Object>) catalog.get(0);
        assertThat(first)
                .containsKey("id")
                .containsKey("displayName")
                .containsKey("costGel")
                .containsKey("inputs")
                .containsKey("bonusType")
                .containsKey("bonusValue");
    }

    @Test
    @DisplayName("catalog_containsExpectedBuildingTypeIds")
    @SuppressWarnings("unchecked")
    void catalog_containsExpectedBuildingTypeIds() {
        String token = registerAndGetToken(rest, base());

        ResponseEntity<List> resp = rest.exchange(
                base() + "/api/build/catalog",
                HttpMethod.GET,
                getWithToken(token),
                List.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<?> catalog = resp.getBody();
        assertThat(catalog).isNotNull();

        List<String> ids = catalog.stream()
                .map(o -> (String) ((Map<?, ?>) o).get("id"))
                .toList();

        assertThat(ids).contains("COTTAGE", "MARANI", "CELLAR", "PRESS_HOUSE");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. Construct COTTAGE (happy-path) — debits wallet + consumes input good
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Happy-path: COTTAGE costs 30 GEL cash + 1× cover_crop_seed (catalog price 14 GEL).
     * Setup: buy 1× cover_crop_seed (wallet: 100 → 86), then construct COTTAGE
     * (wallet: 86 → 56; cover_crop_seed consumed).
     */
    @Test
    @DisplayName("construct_cottage_debitsWalletAndConsumesGoods")
    @SuppressWarnings("unchecked")
    void construct_cottage_debitsWalletAndConsumesGoods() {
        String token = registerAndGetToken(rest, base());
        Number charId = createCharacter(rest, base(), token, uniqueName());
        long cid = charId.longValue();

        // Step A: buy 1× cover_crop_seed (14 GEL) → wallet should be 86 GEL
        Map<String, Object> buyBody = Map.of(
                "characterId", cid,
                "goodTypeId",  "cover_crop_seed",
                "quantity",    1.0);
        ResponseEntity<Map> buyResp = rest.postForEntity(
                base() + "/api/shop/buy",
                withToken(buyBody, token),
                Map.class);
        assertThat(buyResp.getStatusCode())
                .as("Buying cover_crop_seed must succeed (200)")
                .isEqualTo(HttpStatus.OK);
        double walletAfterBuy = ((Number) buyResp.getBody().get("walletGel")).doubleValue();
        assertThat(walletAfterBuy).as("Wallet after buying 14 GEL good").isEqualTo(86.0);

        // Step B: construct COTTAGE (costGel=30, input: 1× cover_crop_seed)
        Map<String, Object> constructBody = Map.of(
                "characterId",    cid,
                "buildingTypeId", "COTTAGE");
        ResponseEntity<Map> constructResp = rest.postForEntity(
                base() + "/api/build/construct",
                withToken(constructBody, token),
                Map.class);

        assertThat(constructResp.getStatusCode())
                .as("POST /api/build/construct must return 200")
                .isEqualTo(HttpStatus.OK);

        Map<String, Object> building = constructResp.getBody();
        assertThat(building).isNotNull();
        assertThat(building).containsKey("id");
        assertThat((String) building.get("buildingTypeId"))
                .as("buildingTypeId must be COTTAGE")
                .isEqualTo("COTTAGE");
        assertThat(((Number) building.get("buildingLevel")).intValue())
                .as("buildingLevel defaults to 1")
                .isEqualTo(1);
        assertThat(((Number) building.get("ownerCharacterId")).longValue())
                .as("ownerCharacterId must be the character")
                .isEqualTo(cid);

        // Step C: verify wallet was debited by costGel=30
        double walletAfterConstruct = getWallet(token, cid);
        assertThat(walletAfterConstruct)
                .as("Wallet must decrease by costGel=30 after COTTAGE construction")
                .isEqualTo(walletAfterBuy - 30.0); // 86 - 30 = 56

        // Step D: verify cover_crop_seed was consumed (quantity now 0 → row deleted)
        ResponseEntity<List> invResp = rest.exchange(
                base() + "/api/goods/" + cid,
                HttpMethod.GET,
                getWithToken(token),
                List.class);
        assertThat(invResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<?> inv = invResp.getBody();
        // The cover_crop_seed row should be gone (quantity reached 0, row deleted)
        boolean hasSeed = inv != null && inv.stream()
                .anyMatch(o -> "cover_crop_seed".equals(((Map<?, ?>) o).get("goodTypeId")));
        assertThat(hasSeed)
                .as("cover_crop_seed must be consumed (row deleted after construct)")
                .isFalse();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. Construct with insufficient funds → 400
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * MARANI costs 80 GEL cash + 2× clay_lining_compound (35 GEL each = 70 GEL).
     * A fresh character has 100 GEL. Even if they have the goods, the wallet is only
     * 100 GEL, so buying the 2 clay compounds (70 GEL total) leaves only 30 GEL — not
     * enough for the 80 GEL cash component. We test by buying the goods first (70 GEL)
     * leaving 30 GEL, then trying to construct MARANI (needs 80 GEL) → 400.
     */
    @Test
    @DisplayName("construct_insufficientFunds_400")
    @SuppressWarnings("unchecked")
    void construct_insufficientFunds_400() {
        String token = registerAndGetToken(rest, base());
        Number charId = createCharacter(rest, base(), token, uniqueName());
        long cid = charId.longValue();

        // Buy 2× clay_lining_compound (35 GEL each = 70 GEL total) → wallet becomes 30 GEL
        Map<String, Object> buyBody = Map.of(
                "characterId", cid,
                "goodTypeId",  "clay_lining_compound",
                "quantity",    2.0);
        ResponseEntity<Map> buyResp = rest.postForEntity(
                base() + "/api/shop/buy",
                withToken(buyBody, token),
                Map.class);
        assertThat(buyResp.getStatusCode())
                .as("Buying clay_lining_compound must succeed")
                .isEqualTo(HttpStatus.OK);
        // Verify wallet is now 30 GEL
        double walletAfterBuy = ((Number) buyResp.getBody().get("walletGel")).doubleValue();
        assertThat(walletAfterBuy).as("Wallet after buying 70 GEL of goods").isEqualTo(30.0);

        // Now try MARANI (costGel=80) with only 30 GEL in wallet → should be 400
        Map<String, Object> constructBody = Map.of(
                "characterId",    cid,
                "buildingTypeId", "MARANI");
        ResponseEntity<Map> resp = rest.postForEntity(
                base() + "/api/build/construct",
                withToken(constructBody, token),
                Map.class);

        assertThat(resp.getStatusCode())
                .as("POST /api/build/construct with insufficient funds must return 400")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. Construct with missing input goods → 400
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Attempt to construct COTTAGE without buying the required cover_crop_seed first.
     * Character has enough wallet (100 GEL > 30 GEL costGel) but zero goods → MISSING_GOODS → 400.
     */
    @Test
    @DisplayName("construct_missingInputGoods_400")
    @SuppressWarnings("unchecked")
    void construct_missingInputGoods_400() {
        String token = registerAndGetToken(rest, base());
        Number charId = createCharacter(rest, base(), token, uniqueName());
        long cid = charId.longValue();

        // Do NOT buy cover_crop_seed — character has none
        Map<String, Object> constructBody = Map.of(
                "characterId",    cid,
                "buildingTypeId", "COTTAGE");
        ResponseEntity<Map> resp = rest.postForEntity(
                base() + "/api/build/construct",
                withToken(constructBody, token),
                Map.class);

        assertThat(resp.getStatusCode())
                .as("POST /api/build/construct with missing input goods must return 400")
                .isEqualTo(HttpStatus.BAD_REQUEST);

        // Confirm wallet was NOT debited (no partial mutation)
        double walletAfter = getWallet(token, cid);
        assertThat(walletAfter)
                .as("Wallet must not be debited when goods check fails (pre-check safety)")
                .isEqualTo(100.0);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 5. GET /api/build/{characterId} — list buildings
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("listBuildings_returnsConstructedBuilding")
    @SuppressWarnings("unchecked")
    void listBuildings_returnsConstructedBuilding() {
        String token = registerAndGetToken(rest, base());
        Number charId = createCharacter(rest, base(), token, uniqueName());
        long cid = charId.longValue();

        // Seed goods and construct
        buyCoverCropSeed(token, cid);
        constructCottage(token, cid);

        // List buildings
        ResponseEntity<List> resp = rest.exchange(
                base() + "/api/build/" + cid,
                HttpMethod.GET,
                getWithToken(token),
                List.class);

        assertThat(resp.getStatusCode())
                .as("GET /api/build/{characterId} must return 200")
                .isEqualTo(HttpStatus.OK);
        List<?> buildings = resp.getBody();
        assertThat(buildings)
                .as("Character must have exactly 1 building after construct")
                .isNotNull()
                .hasSize(1);

        Map<?, ?> b = (Map<?, ?>) buildings.get(0);
        assertThat((String) b.get("buildingTypeId")).isEqualTo("COTTAGE");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 6. GET /api/build/bonuses/{characterId} — aggregate bonuses
    //    Two buildings of same bonusType must SUM
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Construct two COTTAGE buildings (bonusType=STORAGE, bonusValue=50 each).
     * The bonuses endpoint must return STORAGE → 100.0.
     *
     * <p>To build the second COTTAGE we need another cover_crop_seed and 30 more GEL.
     * After the first construct, wallet is 56 GEL (86 - 30). We buy another seed (14 GEL)
     * leaving 42 GEL → enough for the second COTTAGE (30 GEL).
     */
    @Test
    @DisplayName("bonuses_twoCottages_sumStorageBonus")
    @SuppressWarnings("unchecked")
    void bonuses_twoCottages_sumStorageBonus() {
        String token = registerAndGetToken(rest, base());
        Number charId = createCharacter(rest, base(), token, uniqueName());
        long cid = charId.longValue();

        // First COTTAGE: buy seed (14 GEL) → construct (30 GEL). Wallet: 100→86→56
        buyCoverCropSeed(token, cid);
        constructCottage(token, cid);

        // Second COTTAGE: buy another seed (14 GEL) → construct (30 GEL). Wallet: 56→42→12
        buyCoverCropSeed(token, cid);
        constructCottage(token, cid);

        // Verify bonuses aggregate
        ResponseEntity<Map> resp = rest.exchange(
                base() + "/api/build/bonuses/" + cid,
                HttpMethod.GET,
                getWithToken(token),
                Map.class);

        assertThat(resp.getStatusCode())
                .as("GET /api/build/bonuses/{characterId} must return 200")
                .isEqualTo(HttpStatus.OK);

        Map<String, Object> bonuses = resp.getBody();
        assertThat(bonuses)
                .as("Bonuses map must not be null")
                .isNotNull();

        assertThat(bonuses).containsKey("STORAGE");
        double storageBonus = ((Number) bonuses.get("STORAGE")).doubleValue();
        assertThat(storageBonus)
                .as("Two COTTAGEs (bonusValue=50 each) must sum to 100")
                .isEqualTo(100.0);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 7. Ownership enforced
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("listBuildings_otherAccountCharacter_404")
    @SuppressWarnings("unchecked")
    void listBuildings_otherAccountCharacter_404() {
        // Account A creates a character
        String tokenA = registerAndGetToken(rest, base());
        Number charIdA = createCharacter(rest, base(), tokenA, uniqueName());

        // Account B tries to list Account A's buildings
        String tokenB = registerAndGetToken(rest, base());

        ResponseEntity<Map> resp = rest.exchange(
                base() + "/api/build/" + charIdA.longValue(),
                HttpMethod.GET,
                getWithToken(tokenB),
                Map.class);

        assertThat(resp.getStatusCode().value())
                .as("GET /api/build/{characterId} for another account's character must return 404")
                .isIn(403, 404);
    }

    @Test
    @DisplayName("construct_otherAccountCharacter_404")
    @SuppressWarnings("unchecked")
    void construct_otherAccountCharacter_404() {
        String tokenA = registerAndGetToken(rest, base());
        Number charIdA = createCharacter(rest, base(), tokenA, uniqueName());

        String tokenB = registerAndGetToken(rest, base());

        Map<String, Object> body = Map.of(
                "characterId",    charIdA.longValue(),
                "buildingTypeId", "COTTAGE");

        ResponseEntity<Map> resp = rest.postForEntity(
                base() + "/api/build/construct",
                withToken(body, tokenB),
                Map.class);

        assertThat(resp.getStatusCode().value())
                .as("POST /api/build/construct for another account's character must return 404")
                .isIn(403, 404);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helper methods
    // ─────────────────────────────────────────────────────────────────────────

    /** Buy 1× cover_crop_seed (14 GEL) for the character. Asserts 200. */
    @SuppressWarnings("unchecked")
    private void buyCoverCropSeed(String token, long cid) {
        Map<String, Object> body = Map.of(
                "characterId", cid,
                "goodTypeId",  "cover_crop_seed",
                "quantity",    1.0);
        ResponseEntity<Map> resp = rest.postForEntity(
                base() + "/api/shop/buy",
                withToken(body, token),
                Map.class);
        assertThat(resp.getStatusCode())
                .as("Seeding cover_crop_seed must succeed")
                .isEqualTo(HttpStatus.OK);
    }

    /** Construct a COTTAGE building. Asserts 200. */
    @SuppressWarnings("unchecked")
    private void constructCottage(String token, long cid) {
        Map<String, Object> body = Map.of(
                "characterId",    cid,
                "buildingTypeId", "COTTAGE");
        ResponseEntity<Map> resp = rest.postForEntity(
                base() + "/api/build/construct",
                withToken(body, token),
                Map.class);
        assertThat(resp.getStatusCode())
                .as("Constructing COTTAGE must succeed")
                .isEqualTo(HttpStatus.OK);
    }

    /** Read the wallet balance for the character. */
    @SuppressWarnings("unchecked")
    private double getWallet(String token, long characterId) {
        ResponseEntity<Map> resp = rest.exchange(
                base() + "/api/characters/" + characterId,
                HttpMethod.GET,
                getWithToken(token),
                Map.class);
        assertThat(resp.getStatusCode())
                .as("GET /api/characters/{id} must succeed to read wallet")
                .isEqualTo(HttpStatus.OK);
        return ((Number) resp.getBody().get("walletGel")).doubleValue();
    }
}
