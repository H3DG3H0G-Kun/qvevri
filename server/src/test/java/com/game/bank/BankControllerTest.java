package com.game.bank;

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
 * Integration tests for {@code /api/bank/**} (LANE BANK).
 *
 * <p>The test profile freezes the world clock ({@code world.real-seconds-per-sim-day=86400000});
 * tests advance it explicitly via {@code POST /api/world/advance} to drive
 * deterministic compound interest.
 *
 * <p>Characters start with 100 GEL (seeded by CharacterService.create).
 *
 * <p>Scenarios covered:
 * <ol>
 *   <li>GET lazy-creates an account at savings 0.</li>
 *   <li>deposit moves wallet → savings (guarded against over-deposit).</li>
 *   <li>withdraw moves savings → wallet (guarded against insufficient savings).</li>
 *   <li>loan credits wallet and creates an OPEN loan.</li>
 *   <li>second loan while one is OPEN → 400.</li>
 *   <li>interest grows outstanding after advancing several sim-days.</li>
 *   <li>repay reduces outstanding; full repay flips loan to REPAID.</li>
 *   <li>over-cap loan amount (> 1000 GEL) → 400.</li>
 *   <li>non-positive deposit/withdraw/loan/repay amount → 400.</li>
 *   <li>ownership enforced: other account → 404.</li>
 * </ol>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class BankControllerTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    private String base() {
        return "http://localhost:" + port;
    }

    private static String uniqueName() {
        return "bnk_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 1. GET lazy-creates account at savings = 0
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getStatus_lazyCreatesAccount_atSavingsZero")
    @SuppressWarnings("unchecked")
    void getStatus_lazyCreatesAccount_atSavingsZero() {
        String token  = registerAndGetToken(rest, base());
        Number charId = createCharacter(rest, base(), token, uniqueName());
        long   cid    = charId.longValue();

        Map<String, Object> status = getStatus(token, cid);

        assertThat(status).containsKey("account");
        assertThat(status).containsKey("loans");

        Map<String, Object> account = (Map<String, Object>) status.get("account");
        assertThat(account).isNotNull();
        assertThat(((Number) account.get("savingsGel")).doubleValue())
                .as("Savings must be 0 on first access (lazy creation)")
                .isEqualTo(0.0);
        assertThat(((Number) account.get("characterId")).longValue())
                .as("Account must be linked to the correct character")
                .isEqualTo(cid);

        List<?> loans = (List<?>) status.get("loans");
        assertThat(loans)
                .as("No loans yet for a fresh character")
                .isEmpty();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. deposit moves wallet → savings
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("deposit_movesWalletToSavings")
    @SuppressWarnings("unchecked")
    void deposit_movesWalletToSavings() {
        String token  = registerAndGetToken(rest, base());
        Number charId = createCharacter(rest, base(), token, uniqueName());
        long   cid    = charId.longValue();

        double walletBefore = getWallet(token, cid); // 100.0

        Map<String, Object> depositBody = Map.of("characterId", cid, "amountGel", 40.0);
        ResponseEntity<Map> resp = rest.postForEntity(
                base() + "/api/bank/deposit",
                withToken(depositBody, token),
                Map.class);
        assertThat(resp.getStatusCode())
                .as("POST /api/bank/deposit must return 200")
                .isEqualTo(HttpStatus.OK);

        Map<String, Object> account = (Map<String, Object>) resp.getBody();
        assertThat(account).isNotNull();
        assertThat(((Number) account.get("savingsGel")).doubleValue())
                .as("Savings must increase by deposited amount")
                .isEqualTo(40.0);

        double walletAfter = getWallet(token, cid);
        assertThat(walletAfter)
                .as("Wallet must decrease by the deposited amount")
                .isEqualTo(walletBefore - 40.0);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2b. deposit with insufficient wallet → 400
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("deposit_insufficientWallet_400")
    @SuppressWarnings("unchecked")
    void deposit_insufficientWallet_400() {
        String token  = registerAndGetToken(rest, base());
        Number charId = createCharacter(rest, base(), token, uniqueName());
        long   cid    = charId.longValue();

        // Character starts with 100 GEL; try to deposit 9999
        Map<String, Object> depositBody = Map.of("characterId", cid, "amountGel", 9999.0);
        ResponseEntity<Map> resp = rest.postForEntity(
                base() + "/api/bank/deposit",
                withToken(depositBody, token),
                Map.class);
        assertThat(resp.getStatusCode().value())
                .as("Deposit exceeding wallet must be rejected")
                .isIn(400, 402);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. withdraw moves savings → wallet (and insufficient savings → 400)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("withdraw_movesSavingsToWallet")
    @SuppressWarnings("unchecked")
    void withdraw_movesSavingsToWallet() {
        String token  = registerAndGetToken(rest, base());
        Number charId = createCharacter(rest, base(), token, uniqueName());
        long   cid    = charId.longValue();

        // Deposit 50 GEL first
        deposit(token, cid, 50.0);

        double walletBefore = getWallet(token, cid); // 50.0

        Map<String, Object> withdrawBody = Map.of("characterId", cid, "amountGel", 30.0);
        ResponseEntity<Map> withdrawResp = rest.postForEntity(
                base() + "/api/bank/withdraw",
                withToken(withdrawBody, token),
                Map.class);
        assertThat(withdrawResp.getStatusCode())
                .as("POST /api/bank/withdraw must return 200")
                .isEqualTo(HttpStatus.OK);

        Map<String, Object> account = (Map<String, Object>) withdrawResp.getBody();
        assertThat(account).isNotNull();
        assertThat(((Number) account.get("savingsGel")).doubleValue())
                .as("Savings must decrease by the withdrawn amount")
                .isEqualTo(20.0);

        double walletAfter = getWallet(token, cid);
        assertThat(walletAfter)
                .as("Wallet must increase by the withdrawn amount")
                .isEqualTo(walletBefore + 30.0);

        // Insufficient savings → 400
        Map<String, Object> overBody = Map.of("characterId", cid, "amountGel", 9999.0);
        ResponseEntity<Map> overResp = rest.postForEntity(
                base() + "/api/bank/withdraw",
                withToken(overBody, token),
                Map.class);
        assertThat(overResp.getStatusCode())
                .as("Withdrawing more than savings must return 400")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. loan credits wallet and creates an OPEN loan
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("loan_creditsWallet_andCreatesOpenLoan")
    @SuppressWarnings("unchecked")
    void loan_creditsWallet_andCreatesOpenLoan() {
        String token  = registerAndGetToken(rest, base());
        Number charId = createCharacter(rest, base(), token, uniqueName());
        long   cid    = charId.longValue();

        double walletBefore = getWallet(token, cid); // 100.0

        Map<String, Object> loanBody = Map.of("characterId", cid, "amountGel", 200.0);
        ResponseEntity<Map> loanResp = rest.postForEntity(
                base() + "/api/bank/loan",
                withToken(loanBody, token),
                Map.class);
        assertThat(loanResp.getStatusCode())
                .as("POST /api/bank/loan must return 200")
                .isEqualTo(HttpStatus.OK);

        Map<String, Object> loan = (Map<String, Object>) loanResp.getBody();
        assertThat(loan).isNotNull();
        assertThat(loan.get("loanStatus"))
                .as("Newly issued loan must have status OPEN")
                .isEqualTo("OPEN");
        assertThat(((Number) loan.get("principalGel")).doubleValue())
                .as("Principal must equal requested amount")
                .isEqualTo(200.0);
        assertThat(((Number) loan.get("outstandingGel")).doubleValue())
                .as("Outstanding must equal principal on creation")
                .isEqualTo(200.0);
        assertThat(((Number) loan.get("characterId")).longValue())
                .as("Loan must be linked to the correct character")
                .isEqualTo(cid);

        double walletAfter = getWallet(token, cid);
        assertThat(walletAfter)
                .as("Wallet must be credited by the loan principal")
                .isEqualTo(walletBefore + 200.0);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 5. second OPEN loan → 400
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("secondLoan_whileOpenExists_400")
    @SuppressWarnings("unchecked")
    void secondLoan_whileOpenExists_400() {
        String token  = registerAndGetToken(rest, base());
        Number charId = createCharacter(rest, base(), token, uniqueName());
        long   cid    = charId.longValue();

        // First loan succeeds
        takeLoan(token, cid, 100.0);

        // Second loan must be rejected
        Map<String, Object> loanBody = Map.of("characterId", cid, "amountGel", 50.0);
        ResponseEntity<Map> resp = rest.postForEntity(
                base() + "/api/bank/loan",
                withToken(loanBody, token),
                Map.class);
        assertThat(resp.getStatusCode())
                .as("Taking a second loan while one is OPEN must return 400")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 6. interest grows outstanding after advancing the world clock
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("interest_growsOutstanding_afterAdvancingClock")
    @SuppressWarnings("unchecked")
    void interest_growsOutstanding_afterAdvancingClock() {
        String token  = registerAndGetToken(rest, base());
        Number charId = createCharacter(rest, base(), token, uniqueName());
        long   cid    = charId.longValue();

        // Take a 500 GEL loan (daily rate = 0.01)
        Map<String, Object> loanResp = takeLoan(token, cid, 500.0);
        double outstandingAtOrigin = ((Number) loanResp.get("outstandingGel")).doubleValue();

        // Advance the world clock by 10 sim-days
        advanceClock(10);

        // GET status — accrues interest lazily on read
        Map<String, Object> status = getStatus(token, cid);
        List<?> loans = (List<?>) status.get("loans");
        assertThat(loans)
                .as("Loan list must not be empty after taking a loan")
                .isNotEmpty();

        Map<String, Object> loan = (Map<String, Object>) loans.get(0);
        double outstandingAfter = ((Number) loan.get("outstandingGel")).doubleValue();

        // After 10 days at 1%/day: outstanding = 500 * (1.01)^10 ≈ 552.31
        double expected = outstandingAtOrigin * Math.pow(1.01, 10);
        assertThat(outstandingAfter)
                .as("Outstanding must have grown by compound interest (10 days at 1%%/day)")
                .isGreaterThan(outstandingAtOrigin);
        assertThat(outstandingAfter)
                .as("Outstanding must match compound interest formula: P*(1+r)^n")
                .isCloseTo(expected, org.assertj.core.data.Offset.offset(0.01));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 7. repay reduces outstanding; full repay flips REPAID
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("repay_reducesOutstanding_andFlipsRepaidAtZero")
    @SuppressWarnings("unchecked")
    void repay_reducesOutstanding_andFlipsRepaidAtZero() {
        String token  = registerAndGetToken(rest, base());
        Number charId = createCharacter(rest, base(), token, uniqueName());
        long   cid    = charId.longValue();

        // Take a 100 GEL loan; wallet is now 200 GEL
        takeLoan(token, cid, 100.0);

        // Partial repay: 50 GEL
        Map<String, Object> repayBody = Map.of("characterId", cid, "amountGel", 50.0);
        ResponseEntity<Map> repayResp = rest.postForEntity(
                base() + "/api/bank/repay",
                withToken(repayBody, token),
                Map.class);
        assertThat(repayResp.getStatusCode())
                .as("POST /api/bank/repay must return 200")
                .isEqualTo(HttpStatus.OK);

        Map<String, Object> loan = (Map<String, Object>) repayResp.getBody();
        assertThat(loan).isNotNull();
        double outstandingAfterPartial = ((Number) loan.get("outstandingGel")).doubleValue();
        assertThat(outstandingAfterPartial)
                .as("Outstanding must decrease after partial repayment")
                .isLessThan(100.0);
        assertThat(loan.get("loanStatus"))
                .as("Loan must still be OPEN after partial repayment")
                .isEqualTo("OPEN");

        // Repay the remaining balance in full (use a large amount; service pays min(amount, outstanding))
        Map<String, Object> fullRepayBody = Map.of("characterId", cid, "amountGel", 9999.0);
        ResponseEntity<Map> fullRepayResp = rest.postForEntity(
                base() + "/api/bank/repay",
                withToken(fullRepayBody, token),
                Map.class);
        assertThat(fullRepayResp.getStatusCode())
                .as("Full repayment must return 200")
                .isEqualTo(HttpStatus.OK);

        Map<String, Object> repaidLoan = (Map<String, Object>) fullRepayResp.getBody();
        assertThat(repaidLoan).isNotNull();
        assertThat(repaidLoan.get("loanStatus"))
                .as("Loan must be REPAID after paying off the full outstanding balance")
                .isEqualTo("REPAID");
        assertThat(((Number) repaidLoan.get("outstandingGel")).doubleValue())
                .as("Outstanding must be 0 on a REPAID loan")
                .isEqualTo(0.0);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 8. over-cap loan (> MAX_PRINCIPAL) → 400
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("loan_overCap_400")
    @SuppressWarnings("unchecked")
    void loan_overCap_400() {
        String token  = registerAndGetToken(rest, base());
        Number charId = createCharacter(rest, base(), token, uniqueName());
        long   cid    = charId.longValue();

        Map<String, Object> loanBody = Map.of("characterId", cid, "amountGel", 1001.0);
        ResponseEntity<Map> resp = rest.postForEntity(
                base() + "/api/bank/loan",
                withToken(loanBody, token),
                Map.class);
        assertThat(resp.getStatusCode())
                .as("Loan exceeding MAX_PRINCIPAL must return 400")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 9. non-positive amounts → 400
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("nonPositiveAmounts_400")
    @SuppressWarnings("unchecked")
    void nonPositiveAmounts_400() {
        String token  = registerAndGetToken(rest, base());
        Number charId = createCharacter(rest, base(), token, uniqueName());
        long   cid    = charId.longValue();

        // Deposit 0
        Map<String, Object> depositZero = Map.of("characterId", cid, "amountGel", 0.0);
        assertThat(rest.postForEntity(base() + "/api/bank/deposit",
                withToken(depositZero, token), Map.class).getStatusCode())
                .as("deposit(0) must return 400")
                .isEqualTo(HttpStatus.BAD_REQUEST);

        // Withdraw 0
        Map<String, Object> withdrawZero = Map.of("characterId", cid, "amountGel", 0.0);
        assertThat(rest.postForEntity(base() + "/api/bank/withdraw",
                withToken(withdrawZero, token), Map.class).getStatusCode())
                .as("withdraw(0) must return 400")
                .isEqualTo(HttpStatus.BAD_REQUEST);

        // Loan 0
        Map<String, Object> loanZero = Map.of("characterId", cid, "amountGel", 0.0);
        assertThat(rest.postForEntity(base() + "/api/bank/loan",
                withToken(loanZero, token), Map.class).getStatusCode())
                .as("loan(0) must return 400")
                .isEqualTo(HttpStatus.BAD_REQUEST);

        // Repay 0 (need an OPEN loan to hit repay logic, but 0 check fires first)
        takeLoan(token, cid, 50.0);
        Map<String, Object> repayZero = Map.of("characterId", cid, "amountGel", 0.0);
        assertThat(rest.postForEntity(base() + "/api/bank/repay",
                withToken(repayZero, token), Map.class).getStatusCode())
                .as("repay(0) must return 400")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 10. ownership enforced: other account → 404
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("ownership_otherAccount_404")
    @SuppressWarnings("unchecked")
    void ownership_otherAccount_404() {
        // Character owned by account A
        String tokenA = registerAndGetToken(rest, base());
        Number charId = createCharacter(rest, base(), tokenA, uniqueName());
        long   cid    = charId.longValue();

        // Account B tries to access A's character
        String tokenB = registerAndGetToken(rest, base());

        ResponseEntity<Map> resp = rest.exchange(
                base() + "/api/bank/" + cid,
                HttpMethod.GET,
                getWithToken(tokenB),
                Map.class);
        assertThat(resp.getStatusCode())
                .as("GET /api/bank/{characterId} with wrong account must return 404")
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    /** GET /api/bank/{characterId} → raw status map. */
    @SuppressWarnings("unchecked")
    private Map<String, Object> getStatus(String token, long characterId) {
        ResponseEntity<Map> resp = rest.exchange(
                base() + "/api/bank/" + characterId,
                HttpMethod.GET,
                getWithToken(token),
                Map.class);
        assertThat(resp.getStatusCode())
                .as("GET /api/bank/{id} must return 200")
                .isEqualTo(HttpStatus.OK);
        return (Map<String, Object>) resp.getBody();
    }

    /** POST /api/bank/deposit → assert 200. */
    @SuppressWarnings("unchecked")
    private void deposit(String token, long characterId, double amount) {
        Map<String, Object> body = Map.of("characterId", characterId, "amountGel", amount);
        ResponseEntity<Map> resp = rest.postForEntity(
                base() + "/api/bank/deposit",
                withToken(body, token),
                Map.class);
        assertThat(resp.getStatusCode())
                .as("Deposit helper must succeed")
                .isEqualTo(HttpStatus.OK);
    }

    /** POST /api/bank/loan → return raw loan map, assert 200. */
    @SuppressWarnings("unchecked")
    private Map<String, Object> takeLoan(String token, long characterId, double amount) {
        Map<String, Object> body = Map.of("characterId", characterId, "amountGel", amount);
        ResponseEntity<Map> resp = rest.postForEntity(
                base() + "/api/bank/loan",
                withToken(body, token),
                Map.class);
        assertThat(resp.getStatusCode())
                .as("takeLoan helper must succeed (200)")
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
