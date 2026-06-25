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
 * End-to-end proof that aggregated bonuses actually change live outcomes (INTEGRATION PASS):
 *  - SHIPPING_DISCOUNT lowers travel cost (Hauler pays less than Grower).
 *  - QUALITY raises fermented wine quality (Winemaker beats Grower for the same input).
 * A default GROWER has 0.0 for both, so all pre-existing travel/wine tests are unaffected.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class IntegrationWiringTest {

    @LocalServerPort int port;
    @Autowired TestRestTemplate rest;

    private String base() { return "http://localhost:" + port; }
    private static String uniqueName() { return "iw_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8); }

    @Test @DisplayName("hauler_travelsCheaperThanGrower")
    void hauler_travelsCheaperThanGrower() {
        // Grower: shipping discount 0.0 → 5.0 GEL travel cost → wallet 95.0
        String gTok = registerAndGetToken(rest, base());
        long gCid = createCharacter(rest, base(), gTok, uniqueName()).longValue();
        double growerWallet = departAndGetWallet(gTok, gCid);

        // Hauler: shipping discount 0.30 → 3.5 GEL → wallet 96.5
        String hTok = registerAndGetToken(rest, base());
        long hCid = createCharacterWithCareer(hTok, uniqueName(), "HAULER");
        double haulerWallet = departAndGetWallet(hTok, hCid);

        assertThat(haulerWallet)
                .as("Hauler must pay less to travel than a Grower")
                .isGreaterThan(growerWallet);
        assertThat(haulerWallet).isCloseTo(96.5, within(0.01)); // 100 - 5*(1-0.30)
        assertThat(growerWallet).isCloseTo(95.0, within(0.01)); // 100 - 5
    }

    @Test @DisplayName("winemaker_fermentsHigherQualityThanGrower")
    void winemaker_fermentsHigherQualityThanGrower() {
        // Grower (QUALITY 0.0)
        String gTok = registerAndGetToken(rest, base());
        long gCid = createCharacter(rest, base(), gTok, uniqueName()).longValue();
        long gItem = growAndGetItemId(gTok, gCid);
        startFerment(gTok, gCid, gItem);

        // Winemaker (QUALITY +0.10)
        String wTok = registerAndGetToken(rest, base());
        long wCid = createCharacterWithCareer(wTok, uniqueName(), "WINEMAKER");
        long wItem = growAndGetItemId(wTok, wCid);
        startFerment(wTok, wCid, wItem);

        // Both started fermenting (14-day default); advance past ready so completion applies.
        advanceClock(15);

        double growerQ   = fermentQuality(gTok, gCid, gItem);
        double winemakerQ = fermentQuality(wTok, wCid, wItem);

        assertThat(winemakerQ)
                .as("Winemaker's +10% QUALITY must beat a Grower for the same grown bottle")
                .isGreaterThan(growerQ);
    }

    @Test @DisplayName("negociant_buysCheaperThanGrower")
    void negociant_buysCheaperThanGrower() {
        // Grower: BUY_DISCOUNT 0.0 → pays full 25 GEL for a hoe → wallet 75
        String gTok = registerAndGetToken(rest, base());
        long gCid = createCharacter(rest, base(), gTok, uniqueName()).longValue();
        buyGood(gTok, gCid, "hoe", 1.0);
        double growerWallet = getWallet(gTok, gCid);

        // Negociant: BUY_DISCOUNT 0.20 → pays 20 GEL → wallet 80
        String nTok = registerAndGetToken(rest, base());
        long nCid = createCharacterWithCareer(nTok, uniqueName(), "NEGOCIANT");
        buyGood(nTok, nCid, "hoe", 1.0);
        double negociantWallet = getWallet(nTok, nCid);

        assertThat(negociantWallet)
                .as("Negociant must buy cheaper than a Grower")
                .isGreaterThan(growerWallet);
        assertThat(growerWallet).isCloseTo(75.0, within(0.01));    // 100 - 25
        assertThat(negociantWallet).isCloseTo(80.0, within(0.01)); // 100 - 25*0.80
    }

    @Test @DisplayName("grower_harvestsMoreLitresThanMerchant")
    void grower_harvestsMoreLitresThanMerchant() {
        // Same deterministic grow params → identical raw simulated volume.
        // Grower YIELD 0.15 scales it up; Merchant YIELD 0.0 leaves it raw.
        String gTok = registerAndGetToken(rest, base());
        long gCid = createCharacter(rest, base(), gTok, uniqueName()).longValue(); // GROWER
        double growerLitres = growAndGetQuantity(gTok, gCid);

        String mTok = registerAndGetToken(rest, base());
        long mCid = createCharacterWithCareer(mTok, uniqueName(), "MERCHANT");
        double merchantLitres = growAndGetQuantity(mTok, mCid);

        assertThat(growerLitres)
                .as("Grower's +15% YIELD must out-harvest a Merchant for the same vintage")
                .isGreaterThan(merchantLitres);
        assertThat(growerLitres / merchantLitres).isCloseTo(1.15, within(1e-6));
    }

    // ── helpers ──
    @SuppressWarnings("unchecked")
    private double growAndGetQuantity(String token, long cid) {
        Map<String, Object> body = Map.of("seed", 42L, "budLoad", 12, "pickDay", 270, "threats", false);
        ResponseEntity<Map> resp = rest.postForEntity(base() + "/api/cellar/" + cid + "/grow",
                withToken(body, token), Map.class);
        assertThat(resp.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.CREATED);
        Map<?, ?> item = (Map<?, ?>) resp.getBody().get("cellarItem");
        return ((Number) item.get("quantity")).doubleValue();
    }

    @SuppressWarnings("unchecked")
    private void buyGood(String token, long cid, String goodTypeId, double qty) {
        ResponseEntity<Map> resp = rest.postForEntity(base() + "/api/shop/buy",
                withToken(Map.of("characterId", cid, "goodTypeId", goodTypeId, "quantity", qty), token), Map.class);
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

    @SuppressWarnings("unchecked")
    private double departAndGetWallet(String token, long cid) {
        ResponseEntity<Map> dep = rest.postForEntity(base() + "/api/travel/" + cid + "/depart",
                withToken(Map.of("toRegion", "KARTLI"), token), Map.class);
        assertThat(dep.getStatusCode()).isEqualTo(HttpStatus.OK);
        return getWallet(token, cid);
    }

    @SuppressWarnings("unchecked")
    private double getWallet(String token, long cid) {
        ResponseEntity<Map> resp = rest.exchange(base() + "/api/characters/" + cid,
                HttpMethod.GET, getWithToken(token), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return ((Number) resp.getBody().get("walletGel")).doubleValue();
    }

    @SuppressWarnings("unchecked")
    private long growAndGetItemId(String token, long cid) {
        Map<String, Object> body = Map.of("seed", 42L, "budLoad", 12, "pickDay", 270, "threats", false);
        ResponseEntity<Map> resp = rest.postForEntity(base() + "/api/cellar/" + cid + "/grow",
                withToken(body, token), Map.class);
        assertThat(resp.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.CREATED);
        Map<?, ?> r = resp.getBody();
        if (r.containsKey("cellarItem")) {
            return ((Number) ((Map<?, ?>) r.get("cellarItem")).get("id")).longValue();
        }
        return ((Number) r.get("id")).longValue();
    }

    @SuppressWarnings("unchecked")
    private void startFerment(String token, long cid, long itemId) {
        ResponseEntity<Map> resp = rest.postForEntity(base() + "/api/wine/ferment/start",
                withToken(Map.of("characterId", cid, "cellarItemId", itemId), token), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @SuppressWarnings("unchecked")
    private void advanceClock(int days) {
        ResponseEntity<Map> resp = rest.postForEntity(base() + "/api/world/advance",
                new HttpEntity<>(Map.of("days", days)), Map.class);
        assertThat(resp.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.CREATED);
    }

    @SuppressWarnings("unchecked")
    private double fermentQuality(String token, long cid, long itemId) {
        ResponseEntity<Map> resp = rest.exchange(
                base() + "/api/wine/ferment/" + itemId + "/status?characterId=" + cid,
                HttpMethod.GET, getWithToken(token), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return ((Number) resp.getBody().get("currentQuality")).doubleValue();
    }
}
