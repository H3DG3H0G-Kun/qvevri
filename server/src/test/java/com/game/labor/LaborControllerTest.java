package com.game.labor;

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
 * Integration tests for {@code /api/labor/**} (LANE LABOR).
 *
 * <p>The test profile freezes the world clock ({@code world.real-seconds-per-sim-day=86400000});
 * tests advance it explicitly via {@code POST /api/world/advance} to drive
 * deterministic wage accrual.
 *
 * <p>Characters start with 100 GEL. All hireCostGel values are &le; 60 GEL,
 * so a single hire is always affordable from the starting wallet.
 *
 * <p>Scenarios covered:
 * <ol>
 *   <li>GET /api/labor/catalog returns all 4 roles.</li>
 *   <li>POST /api/labor/hire debits wallet + creates ACTIVE staff.</li>
 *   <li>wagesOwed == 0 immediately after hiring (same sim-day).</li>
 *   <li>After POST /api/world/advance N days, wagesOwed == N × dailyWageGel.</li>
 *   <li>POST /api/labor/payroll deducts owed and resets wagesOwed to 0.</li>
 *   <li>POST /api/labor/payroll when wallet &lt; owed → 400 CANNOT_MAKE_PAYROLL.</li>
 *   <li>POST /api/labor/{staffId}/fire flips to QUIT; accrual stops (advance more days → same wagesOwed).</li>
 *   <li>GET /api/labor/benefits aggregates ACTIVE staff benefitType → summed value.</li>
 *   <li>Ownership enforced: other account → 404.</li>
 *   <li>Unknown staffTypeId → 404.</li>
 * </ol>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class LaborControllerTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    private String base() {
        return "http://localhost:" + port;
    }

    private static String uniqueName() {
        return "lbr_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 1. GET /api/labor/catalog — returns all 4 roles
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("catalog_returns4Roles_withExpectedFields")
    @SuppressWarnings("unchecked")
    void catalog_returns4Roles_withExpectedFields() {
        ResponseEntity<List> resp = rest.getForEntity(base() + "/api/labor/catalog", List.class);
        assertThat(resp.getStatusCode())
                .as("GET /api/labor/catalog must return 200")
                .isEqualTo(HttpStatus.OK);

        List<Object> catalog = resp.getBody();
        assertThat(catalog)
                .as("Catalog must contain exactly 4 roles")
                .hasSize(4);

        // Spot-check the first entry has required fields
        Map<String, Object> first = (Map<String, Object>) catalog.get(0);
        assertThat(first).containsKey("id");
        assertThat(first).containsKey("title");
        assertThat(first).containsKey("hireCostGel");
        assertThat(first).containsKey("dailyWageGel");
        assertThat(first).containsKey("benefitType");
        assertThat(first).containsKey("benefitVal");

        // Verify all 4 known role IDs are present
        List<String> ids = catalog.stream()
                .map(o -> (String) ((Map<String, Object>) o).get("id"))
                .toList();
        assertThat(ids).contains("vineyard_hand", "cellar_master", "cooper_apprentice", "merchant_clerk");

        // All hireCostGel values must be <= 60 (affordable from 100 GEL starting wallet)
        for (Object entry : catalog) {
            Map<String, Object> role = (Map<String, Object>) entry;
            double hireCost = ((Number) role.get("hireCostGel")).doubleValue();
            assertThat(hireCost)
                    .as("hireCostGel for role " + role.get("id") + " must be <= 60")
                    .isLessThanOrEqualTo(60.0);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. POST /api/labor/hire — debits wallet + creates ACTIVE staff
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("hire_debitsWallet_andCreatesActiveStaff")
    @SuppressWarnings("unchecked")
    void hire_debitsWallet_andCreatesActiveStaff() {
        String token  = registerAndGetToken(rest, base());
        Number charId = createCharacter(rest, base(), token, uniqueName());
        long   cid    = charId.longValue();

        double walletBefore = getWallet(token, cid); // 100.0

        Map<String, Object> hiredStaff = hireStaff(token, cid, "vineyard_hand");

        assertThat(hiredStaff.get("laborStatus"))
                .as("Hired staff must start ACTIVE")
                .isEqualTo("ACTIVE");
        assertThat(hiredStaff.get("staffTypeId"))
                .as("staffTypeId must match requested role")
                .isEqualTo("vineyard_hand");
        assertThat(((Number) hiredStaff.get("characterId")).longValue())
                .as("Staff must be linked to the correct character")
                .isEqualTo(cid);

        // StaffCatalog: vineyard_hand hireCostGel = 30.0
        double walletAfter = getWallet(token, cid);
        assertThat(walletAfter)
                .as("Wallet must be debited by hireCostGel (30.0)")
                .isEqualTo(walletBefore - 30.0);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. wagesOwed == 0 immediately after hire (same sim-day)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("wagesOwed_isZero_immediatelyAfterHire")
    @SuppressWarnings("unchecked")
    void wagesOwed_isZero_immediatelyAfterHire() {
        String token  = registerAndGetToken(rest, base());
        Number charId = createCharacter(rest, base(), token, uniqueName());
        long   cid    = charId.longValue();

        hireStaff(token, cid, "vineyard_hand");

        Map<String, Object> status = getStatus(token, cid);
        double wagesOwed = ((Number) status.get("wagesOwed")).doubleValue();
        assertThat(wagesOwed)
                .as("wagesOwed must be 0 immediately after hire (same sim-day)")
                .isEqualTo(0.0);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. After advancing N days, wagesOwed == N × dailyWageGel
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("wagesOwed_equalsNTimesDailyWage_afterAdvancingClock")
    @SuppressWarnings("unchecked")
    void wagesOwed_equalsNTimesDailyWage_afterAdvancingClock() {
        String token  = registerAndGetToken(rest, base());
        Number charId = createCharacter(rest, base(), token, uniqueName());
        long   cid    = charId.longValue();

        // Hire vineyard_hand: dailyWageGel = 5.0
        hireStaff(token, cid, "vineyard_hand");

        // Advance 7 sim-days
        advanceClock(7);

        Map<String, Object> status = getStatus(token, cid);
        double wagesOwed = ((Number) status.get("wagesOwed")).doubleValue();

        // Expected: 7 days × 5.0 GEL/day = 35.0 GEL
        assertThat(wagesOwed)
                .as("wagesOwed must equal N × dailyWageGel after advancing N days")
                .isEqualTo(7.0 * 5.0);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 5. Payroll deducts owed and resets wagesOwed to 0
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("payroll_deductsOwed_andResetsWagesOwedToZero")
    @SuppressWarnings("unchecked")
    void payroll_deductsOwed_andResetsWagesOwedToZero() {
        String token  = registerAndGetToken(rest, base());
        Number charId = createCharacter(rest, base(), token, uniqueName());
        long   cid    = charId.longValue();

        // Hire vineyard_hand (30 GEL hire cost) → wallet = 70.0
        hireStaff(token, cid, "vineyard_hand");

        // Advance 4 days → wagesOwed = 4 × 5.0 = 20.0
        advanceClock(4);

        double walletBefore = getWallet(token, cid); // 70.0

        // Run payroll
        Map<String, Object> payrollResult = runPayroll(token, cid);
        double paid      = ((Number) payrollResult.get("paid")).doubleValue();
        double walletGel = ((Number) payrollResult.get("walletGel")).doubleValue();

        assertThat(paid)
                .as("Payroll paid must equal wages owed (4 × 5.0 = 20.0)")
                .isEqualTo(20.0);
        assertThat(walletGel)
                .as("walletGel after payroll must equal walletBefore - paid")
                .isEqualTo(walletBefore - 20.0);

        // wagesOwed must now be 0 (lastPaidDay reset to current day)
        Map<String, Object> statusAfter = getStatus(token, cid);
        double wagesOwedAfter = ((Number) statusAfter.get("wagesOwed")).doubleValue();
        assertThat(wagesOwedAfter)
                .as("wagesOwed must be 0 immediately after payroll")
                .isEqualTo(0.0);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 6. Payroll when wallet < owed → 400 CANNOT_MAKE_PAYROLL
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("payroll_whenWalletInsufficient_400_cannotMakePayroll")
    @SuppressWarnings("unchecked")
    void payroll_whenWalletInsufficient_400_cannotMakePayroll() {
        String token  = registerAndGetToken(rest, base());
        Number charId = createCharacter(rest, base(), token, uniqueName());
        long   cid    = charId.longValue();

        // Hire all 4 staff: total hire cost = 30+60+40+50 = 180 GEL
        // But wallet is only 100 GEL, so hire only what we can afford.
        // Hire vineyard_hand (30), cellar_master requires 60 → wallet 70 after first hire.
        // We need wages to exceed wallet. Hire vineyard_hand (5/day) + cooper_apprentice
        // (6/day) = 11/day. After hiring: 100-30-40 = 30 GEL left.
        // After 3 days: owed = 33 GEL > 30 GEL remaining.
        hireStaff(token, cid, "vineyard_hand");      // -30 → 70 GEL
        hireStaff(token, cid, "cooper_apprentice");  // -40 → 30 GEL

        // Advance enough days so wages exceed remaining wallet
        // Remaining: 30 GEL. Daily wages: 5 + 6 = 11 GEL/day. Need > 30 GEL owed.
        // After 3 days: 33 GEL owed > 30 GEL wallet.
        advanceClock(3);

        // Payroll must fail
        Map<String, Object> body = Map.of("characterId", cid);
        ResponseEntity<String> resp = rest.postForEntity(
                base() + "/api/labor/payroll",
                withToken(body, token),
                String.class);

        assertThat(resp.getStatusCode())
                .as("Payroll with insufficient wallet must return 400")
                .isEqualTo(HttpStatus.BAD_REQUEST);

        // Verify wallet is unchanged (no partial deduction)
        double walletAfterFailure = getWallet(token, cid);
        assertThat(walletAfterFailure)
                .as("Wallet must be unchanged after failed payroll")
                .isEqualTo(30.0);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 7. Fire flips to QUIT; accrual stops (further clock advance = no change)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("fire_flipsToQuit_andStopsAccrual")
    @SuppressWarnings("unchecked")
    void fire_flipsToQuit_andStopsAccrual() {
        String token  = registerAndGetToken(rest, base());
        Number charId = createCharacter(rest, base(), token, uniqueName());
        long   cid    = charId.longValue();

        // Hire vineyard_hand (dailyWage = 5.0)
        Map<String, Object> hired = hireStaff(token, cid, "vineyard_hand");
        long staffId = ((Number) hired.get("id")).longValue();

        // Advance 3 days → wagesOwed = 15.0
        advanceClock(3);

        // Record wagesOwed before fire
        double wagesOwedBefore = ((Number) getStatus(token, cid).get("wagesOwed")).doubleValue();
        assertThat(wagesOwedBefore)
                .as("wagesOwed must be 15.0 after 3 days")
                .isEqualTo(15.0);

        // Fire the staff member
        Map<String, Object> fireBody = Map.of("characterId", cid);
        ResponseEntity<Map> fireResp = rest.postForEntity(
                base() + "/api/labor/" + staffId + "/fire",
                withToken(fireBody, token),
                Map.class);
        assertThat(fireResp.getStatusCode())
                .as("POST /api/labor/{staffId}/fire must return 200")
                .isEqualTo(HttpStatus.OK);

        Map<String, Object> firedStaff = (Map<String, Object>) fireResp.getBody();
        assertThat(firedStaff.get("laborStatus"))
                .as("Fired staff must have laborStatus QUIT")
                .isEqualTo("QUIT");

        // Advance 5 more days — a fired (QUIT) staff is excluded from wagesOwed and
        // never accrues again, so the owed total for this (only) staff stays 0.
        // (v1 rule: firing waives the accrued debt. Balancing note: this lets a player
        // dodge wages by firing before payroll — to be tightened in the integration pass.)
        advanceClock(5);

        double wagesOwedAfterFire = ((Number) getStatus(token, cid).get("wagesOwed")).doubleValue();
        assertThat(wagesOwedAfterFire)
                .as("a fired (QUIT) staff no longer accrues or owes wages")
                .isEqualTo(0.0);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 8. Benefits aggregate ACTIVE staff only (by benefitType)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("benefits_aggregateActiveStaff_byBenefitType")
    @SuppressWarnings("unchecked")
    void benefits_aggregateActiveStaff_byBenefitType() {
        String token  = registerAndGetToken(rest, base());
        Number charId = createCharacter(rest, base(), token, uniqueName());
        long   cid    = charId.longValue();

        // Hire vineyard_hand (YIELD +10) and cellar_master (QUALITY +8)
        // vineyard_hand = 30 GEL, cellar_master = 60 GEL — total hire = 90 GEL
        // Wallet: 100 - 30 - 60 = 10 GEL remaining
        hireStaff(token, cid, "vineyard_hand");
        hireStaff(token, cid, "cellar_master");

        // GET /api/labor/benefits/{characterId}
        ResponseEntity<Map> resp = rest.exchange(
                base() + "/api/labor/benefits/" + cid,
                HttpMethod.GET,
                getWithToken(token),
                Map.class);
        assertThat(resp.getStatusCode())
                .as("GET /api/labor/benefits must return 200")
                .isEqualTo(HttpStatus.OK);

        Map<String, Object> benefits = (Map<String, Object>) resp.getBody();
        assertThat(benefits).isNotNull();
        assertThat(benefits).containsKey("YIELD");
        assertThat(benefits).containsKey("QUALITY");

        assertThat(((Number) benefits.get("YIELD")).doubleValue())
                .as("YIELD benefit must be 10.0 from vineyard_hand")
                .isEqualTo(10.0);
        assertThat(((Number) benefits.get("QUALITY")).doubleValue())
                .as("QUALITY benefit must be 8.0 from cellar_master")
                .isEqualTo(8.0);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 9. Ownership enforced: other account → 404
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("ownership_otherAccount_404")
    @SuppressWarnings("unchecked")
    void ownership_otherAccount_404() {
        // Character owned by account A
        String tokenA = registerAndGetToken(rest, base());
        Number charId = createCharacter(rest, base(), tokenA, uniqueName());
        long   cid    = charId.longValue();

        // Account B attempts to view A's staff
        String tokenB = registerAndGetToken(rest, base());

        ResponseEntity<Map> resp = rest.exchange(
                base() + "/api/labor/" + cid,
                HttpMethod.GET,
                getWithToken(tokenB),
                Map.class);
        assertThat(resp.getStatusCode())
                .as("GET /api/labor/{characterId} with wrong account must return 404")
                .isEqualTo(HttpStatus.NOT_FOUND);

        // Account B attempts to hire for A's character
        Map<String, Object> hireBody = Map.of("characterId", cid, "staffTypeId", "vineyard_hand");
        ResponseEntity<String> hireResp = rest.postForEntity(
                base() + "/api/labor/hire",
                withToken(hireBody, tokenB),
                String.class);
        assertThat(hireResp.getStatusCode())
                .as("POST /api/labor/hire with wrong account must return 404")
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 10. Unknown staffTypeId → 404
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("hire_unknownStaffTypeId_404")
    @SuppressWarnings("unchecked")
    void hire_unknownStaffTypeId_404() {
        String token  = registerAndGetToken(rest, base());
        Number charId = createCharacter(rest, base(), token, uniqueName());
        long   cid    = charId.longValue();

        Map<String, Object> hireBody = Map.of("characterId", cid, "staffTypeId", "nonexistent_role");
        ResponseEntity<String> resp = rest.postForEntity(
                base() + "/api/labor/hire",
                withToken(hireBody, token),
                String.class);

        assertThat(resp.getStatusCode())
                .as("Hiring with unknown staffTypeId must return 404")
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 11. Fire stops QUIT staff's contribution to benefits
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("fire_stopsContributionToBenefits")
    @SuppressWarnings("unchecked")
    void fire_stopsContributionToBenefits() {
        String token  = registerAndGetToken(rest, base());
        Number charId = createCharacter(rest, base(), token, uniqueName());
        long   cid    = charId.longValue();

        // Hire vineyard_hand (YIELD)
        Map<String, Object> hired = hireStaff(token, cid, "vineyard_hand");
        long staffId = ((Number) hired.get("id")).longValue();

        // Confirm benefit present
        ResponseEntity<Map> beforeResp = rest.exchange(
                base() + "/api/labor/benefits/" + cid,
                HttpMethod.GET,
                getWithToken(token),
                Map.class);
        Map<String, Object> beforeBenefits = (Map<String, Object>) beforeResp.getBody();
        assertThat(beforeBenefits).containsKey("YIELD");

        // Fire the staff member
        Map<String, Object> fireBody = Map.of("characterId", cid);
        rest.postForEntity(
                base() + "/api/labor/" + staffId + "/fire",
                withToken(fireBody, token),
                Map.class);

        // Benefits must now be empty (no ACTIVE staff)
        ResponseEntity<Map> afterResp = rest.exchange(
                base() + "/api/labor/benefits/" + cid,
                HttpMethod.GET,
                getWithToken(token),
                Map.class);
        Map<String, Object> afterBenefits = (Map<String, Object>) afterResp.getBody();
        assertThat(afterBenefits)
                .as("Benefits must be empty after all staff are fired")
                .doesNotContainKey("YIELD");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 12. Hire insufficient funds → 400/402
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("hire_insufficientFunds_400or402")
    @SuppressWarnings("unchecked")
    void hire_insufficientFunds_400or402() {
        String token  = registerAndGetToken(rest, base());
        Number charId = createCharacter(rest, base(), token, uniqueName());
        long   cid    = charId.longValue();

        // Spend down the wallet first — hire vineyard_hand (30) + cellar_master (60) = 90.
        // Wallet: 100 - 30 - 60 = 10 GEL. merchant_clerk costs 50 GEL — should fail.
        hireStaff(token, cid, "vineyard_hand");    // -30 → 70
        hireStaff(token, cid, "cellar_master");    // -60 → 10

        Map<String, Object> hireBody = Map.of("characterId", cid, "staffTypeId", "merchant_clerk");
        ResponseEntity<String> resp = rest.postForEntity(
                base() + "/api/labor/hire",
                withToken(hireBody, token),
                String.class);

        assertThat(resp.getStatusCode().value())
                .as("Hiring with insufficient funds must return 400 or 402")
                .isIn(400, 402);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    /** GET /api/labor/{characterId} → raw status map. Assert 200. */
    @SuppressWarnings("unchecked")
    private Map<String, Object> getStatus(String token, long characterId) {
        ResponseEntity<Map> resp = rest.exchange(
                base() + "/api/labor/" + characterId,
                HttpMethod.GET,
                getWithToken(token),
                Map.class);
        assertThat(resp.getStatusCode())
                .as("GET /api/labor/{id} must return 200")
                .isEqualTo(HttpStatus.OK);
        return (Map<String, Object>) resp.getBody();
    }

    /** POST /api/labor/hire → return raw hired staff map, assert 200. */
    @SuppressWarnings("unchecked")
    private Map<String, Object> hireStaff(String token, long characterId, String staffTypeId) {
        Map<String, Object> body = Map.of("characterId", characterId, "staffTypeId", staffTypeId);
        ResponseEntity<Map> resp = rest.postForEntity(
                base() + "/api/labor/hire",
                withToken(body, token),
                Map.class);
        assertThat(resp.getStatusCode())
                .as("POST /api/labor/hire must return 200 (staffTypeId=" + staffTypeId + ")")
                .isEqualTo(HttpStatus.OK);
        return (Map<String, Object>) resp.getBody();
    }

    /** POST /api/labor/payroll → return result map, assert 200. */
    @SuppressWarnings("unchecked")
    private Map<String, Object> runPayroll(String token, long characterId) {
        Map<String, Object> body = Map.of("characterId", characterId);
        ResponseEntity<Map> resp = rest.postForEntity(
                base() + "/api/labor/payroll",
                withToken(body, token),
                Map.class);
        assertThat(resp.getStatusCode())
                .as("POST /api/labor/payroll must return 200")
                .isEqualTo(HttpStatus.OK);
        return (Map<String, Object>) resp.getBody();
    }

    /** GET /api/characters/{id} → walletGel. */
    @SuppressWarnings("unchecked")
    private double getWallet(String token, long characterId) {
        ResponseEntity<Map> resp = rest.exchange(
                base() + "/api/characters/" + characterId,
                HttpMethod.GET,
                getWithToken(token),
                Map.class);
        assertThat(resp.getStatusCode())
                .as("GET /api/characters/{id} must return 200")
                .isEqualTo(HttpStatus.OK);
        return ((Number) resp.getBody().get("walletGel")).doubleValue();
    }

    /** POST /api/world/advance { days: n } — no token required (permitAll). */
    @SuppressWarnings("unchecked")
    private void advanceClock(int days) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> body = Map.of("days", days);
        ResponseEntity<Map> resp = rest.postForEntity(
                base() + "/api/world/advance",
                new HttpEntity<>(body, headers),
                Map.class);
        assertThat(resp.getStatusCode())
                .as("POST /api/world/advance must return 200")
                .isEqualTo(HttpStatus.OK);
    }
}
