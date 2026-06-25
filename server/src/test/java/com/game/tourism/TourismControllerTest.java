package com.game.tourism;

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

/**
 * Integration tests for /api/tourism/** (LANE TOURISM).
 *
 * <p>Income constants: BASE_PER_DAY=2.0, PER_BUILDING=1.0.
 * Characters start with 100.0 GEL. Building a COTTAGE costs 30 GEL cash +
 * 1× cover_crop_seed (14 GEL). After buying the seed (wallet: 100→86) and
 * constructing a COTTAGE (wallet: 86→56), the character owns 1 building:
 *   ratePerDay = 2.0 + 1.0×1 = 3.0 GEL/day.
 *
 * <p>Scenarios covered:
 * <ol>
 *   <li>GET /api/tourism/{characterId} lazy-creates a ledger (buildingsCount=0,
 *       ratePerDay=2.0, accruedSoFar=0.0).</li>
 *   <li>After constructing a COTTAGE, currentRatePerDay rises by PER_BUILDING.</li>
 *   <li>After advancing the clock N days, accruedSoFar = N × ratePerDay.</li>
 *   <li>POST /api/tourism/{characterId}/claim pays accrued income (wallet increases)
 *       and resets accrued to ~0.</li>
 *   <li>A second immediate claim pays ~0 GEL.</li>
 *   <li>Ownership enforced: another account → 404.</li>
 * </ol>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class TourismControllerTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    private String base() {
        return "http://localhost:" + port;
    }

    private static String uniqueName() {
        return "tour_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 1. GET lazy-creates ledger (no buildings → buildingsCount=0, rate=2.0)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getSnapshot_lazyCreatesLedger_zeroBuildingsBaseRate")
    @SuppressWarnings("unchecked")
    void getSnapshot_lazyCreatesLedger_zeroBuildingsBaseRate() {
        String token  = registerAndGetToken(rest, base());
        Number charId = createCharacter(rest, base(), token, uniqueName());
        long   cid    = charId.longValue();

        ResponseEntity<Map> resp = rest.exchange(
                base() + "/api/tourism/" + cid,
                HttpMethod.GET,
                getWithToken(token),
                Map.class);

        assertThat(resp.getStatusCode())
                .as("GET /api/tourism/{characterId} must return 200")
                .isEqualTo(HttpStatus.OK);

        Map<String, Object> body = resp.getBody();
        assertThat(body)
                .as("Response body must not be null")
                .isNotNull()
                .containsKey("lastClaimDay")
                .containsKey("buildingsCount")
                .containsKey("currentRatePerDay")
                .containsKey("accruedSoFar");

        assertThat(((Number) body.get("buildingsCount")).intValue())
                .as("Fresh character has 0 buildings")
                .isEqualTo(0);

        assertThat(((Number) body.get("currentRatePerDay")).doubleValue())
                .as("Rate with 0 buildings = BASE_PER_DAY = 2.0")
                .isEqualTo(TourismService.BASE_PER_DAY);

        assertThat(((Number) body.get("accruedSoFar")).doubleValue())
                .as("No days have passed since ledger creation — accruedSoFar must be 0")
                .isEqualTo(0.0);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. Building a COTTAGE raises currentRatePerDay by PER_BUILDING
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getSnapshot_afterBuilding_rateRisesByPerBuilding")
    @SuppressWarnings("unchecked")
    void getSnapshot_afterBuilding_rateRisesByPerBuilding() {
        String token  = registerAndGetToken(rest, base());
        Number charId = createCharacter(rest, base(), token, uniqueName());
        long   cid    = charId.longValue();

        // Baseline: rate = 2.0 (no buildings)
        double baseRate = getSnapshotRate(token, cid);
        assertThat(baseRate).as("Base rate must be 2.0").isEqualTo(TourismService.BASE_PER_DAY);

        // Construct 1 COTTAGE (buy seed first)
        buyCoverCropSeed(token, cid);
        constructCottage(token, cid);

        // Rate must now be 2.0 + 1.0×1 = 3.0
        double rateAfter = getSnapshotRate(token, cid);
        assertThat(rateAfter)
                .as("Rate after 1 COTTAGE = BASE + PER_BUILDING×1 = 3.0")
                .isEqualTo(TourismService.BASE_PER_DAY + TourismService.PER_BUILDING * 1.0);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. After advancing N days, accruedSoFar = N × ratePerDay
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getSnapshot_afterAdvancingClock_accruedMatchesFormula")
    @SuppressWarnings("unchecked")
    void getSnapshot_afterAdvancingClock_accruedMatchesFormula() {
        String token  = registerAndGetToken(rest, base());
        Number charId = createCharacter(rest, base(), token, uniqueName());
        long   cid    = charId.longValue();

        // Lazy-create ledger (0 buildings → rate = BASE_PER_DAY = 2.0)
        getSnapshotRate(token, cid);

        // Advance the clock 5 days
        int daysToAdvance = 5;
        advanceClock(daysToAdvance);

        // accrued = 5 × 2.0 (no buildings) = 10.0
        double expectedAccrued = daysToAdvance * TourismService.BASE_PER_DAY;

        Map<String, Object> snapshot = getSnapshot(token, cid);
        double accrued        = ((Number) snapshot.get("accruedSoFar")).doubleValue();
        int    buildingsCount = ((Number) snapshot.get("buildingsCount")).intValue();

        assertThat(buildingsCount)
                .as("No buildings were constructed")
                .isEqualTo(0);

        assertThat(accrued)
                .as("accruedSoFar must equal N × BASE_PER_DAY = " + expectedAccrued)
                .isEqualTo(expectedAccrued);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. POST claim pays accrued income and resets accrued to ~0
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("claim_paysAccruedIncome_resetsAccrued")
    @SuppressWarnings("unchecked")
    void claim_paysAccruedIncome_resetsAccrued() {
        String token  = registerAndGetToken(rest, base());
        Number charId = createCharacter(rest, base(), token, uniqueName());
        long   cid    = charId.longValue();

        // Lazy-create ledger at current day
        getSnapshotRate(token, cid);

        // Build 1 COTTAGE so income is a distinct rate
        buyCoverCropSeed(token, cid);
        constructCottage(token, cid);

        // Advance 7 days: accrued = 7 × 3.0 = 21.0
        int days = 7;
        advanceClock(days);

        double walletBefore = getWallet(token, cid);
        double expectedPaid = days * (TourismService.BASE_PER_DAY + TourismService.PER_BUILDING * 1.0);

        // Claim
        ResponseEntity<Map> claimResp = rest.postForEntity(
                base() + "/api/tourism/" + cid + "/claim",
                withToken(Map.of(), token),
                Map.class);

        assertThat(claimResp.getStatusCode())
                .as("POST /api/tourism/{characterId}/claim must return 200")
                .isEqualTo(HttpStatus.OK);

        Map<String, Object> claimBody = claimResp.getBody();
        assertThat(claimBody)
                .as("Claim response body must not be null")
                .isNotNull()
                .containsKey("paid")
                .containsKey("walletGel")
                .containsKey("lastClaimDay");

        double paid      = ((Number) claimBody.get("paid")).doubleValue();
        double walletGel = ((Number) claimBody.get("walletGel")).doubleValue();

        assertThat(paid)
                .as("paid must equal days × ratePerDay = " + expectedPaid)
                .isEqualTo(expectedPaid);

        assertThat(walletGel)
                .as("walletGel after claim must equal walletBefore + paid")
                .isEqualTo(walletBefore + paid);

        // Verify accrued is now ~0 (same day as claim)
        double accruedAfter = ((Number) getSnapshot(token, cid).get("accruedSoFar")).doubleValue();
        assertThat(accruedAfter)
                .as("accruedSoFar immediately after claim must be 0 (same day)")
                .isEqualTo(0.0);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 5. Second immediate claim pays ~0
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("claim_secondImmediateClaim_paysZero")
    @SuppressWarnings("unchecked")
    void claim_secondImmediateClaim_paysZero() {
        String token  = registerAndGetToken(rest, base());
        Number charId = createCharacter(rest, base(), token, uniqueName());
        long   cid    = charId.longValue();

        // Lazy-create ledger and advance clock so first claim has something
        getSnapshotRate(token, cid);
        advanceClock(3);

        // First claim
        rest.postForEntity(
                base() + "/api/tourism/" + cid + "/claim",
                withToken(Map.of(), token),
                Map.class);

        // Second immediate claim — no days have passed since first claim
        ResponseEntity<Map> secondClaim = rest.postForEntity(
                base() + "/api/tourism/" + cid + "/claim",
                withToken(Map.of(), token),
                Map.class);

        assertThat(secondClaim.getStatusCode())
                .as("Second immediate claim must still return 200")
                .isEqualTo(HttpStatus.OK);

        Map<String, Object> body = secondClaim.getBody();
        assertThat(body).isNotNull();
        double paid = ((Number) body.get("paid")).doubleValue();
        assertThat(paid)
                .as("Second immediate claim paid must be 0.0 (no days passed)")
                .isEqualTo(0.0);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 6. Ownership enforced — other account → 404
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getSnapshot_otherAccount_404")
    @SuppressWarnings("unchecked")
    void getSnapshot_otherAccount_404() {
        // Account A owns the character
        String tokenA = registerAndGetToken(rest, base());
        Number charIdA = createCharacter(rest, base(), tokenA, uniqueName());

        // Account B tries to access Account A's tourism data
        String tokenB = registerAndGetToken(rest, base());

        ResponseEntity<String> resp = rest.exchange(
                base() + "/api/tourism/" + charIdA.longValue(),
                HttpMethod.GET,
                getWithToken(tokenB),
                String.class);

        assertThat(resp.getStatusCode().value())
                .as("GET /api/tourism/{characterId} for another account must return 404")
                .isIn(403, 404);
    }

    @Test
    @DisplayName("claim_otherAccount_404")
    @SuppressWarnings("unchecked")
    void claim_otherAccount_404() {
        // Account A owns the character
        String tokenA = registerAndGetToken(rest, base());
        Number charIdA = createCharacter(rest, base(), tokenA, uniqueName());

        // Account B tries to claim Account A's tourism income
        String tokenB = registerAndGetToken(rest, base());

        ResponseEntity<String> resp = rest.postForEntity(
                base() + "/api/tourism/" + charIdA.longValue() + "/claim",
                withToken(Map.of(), tokenB),
                String.class);

        assertThat(resp.getStatusCode().value())
                .as("POST /api/tourism/{characterId}/claim for another account must return 404")
                .isIn(403, 404);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helper methods
    // ─────────────────────────────────────────────────────────────────────────

    /** GET /api/tourism/{characterId} and return the full snapshot map. */
    @SuppressWarnings("unchecked")
    private Map<String, Object> getSnapshot(String token, long cid) {
        ResponseEntity<Map> resp = rest.exchange(
                base() + "/api/tourism/" + cid,
                HttpMethod.GET,
                getWithToken(token),
                Map.class);
        assertThat(resp.getStatusCode())
                .as("GET /api/tourism/{cid} must return 200")
                .isEqualTo(HttpStatus.OK);
        return resp.getBody();
    }

    /** Convenience: extract currentRatePerDay from snapshot. */
    @SuppressWarnings("unchecked")
    private double getSnapshotRate(String token, long cid) {
        return ((Number) getSnapshot(token, cid).get("currentRatePerDay")).doubleValue();
    }

    /** POST /api/world/advance to move the sim clock forward by {@code days}. */
    private void advanceClock(int days) {
        rest.postForEntity(
                base() + "/api/world/advance",
                Map.of("days", days),
                Map.class);
    }

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
                .as("Buying cover_crop_seed must succeed")
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
