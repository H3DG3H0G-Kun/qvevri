package com.game.bank;

import com.game.account.AccountTokenService;
import com.game.character.CharacterService;
import com.game.exception.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller for the LANE BANK — {@code /api/bank/**}.
 *
 * <p>All endpoints use inline bearer-token authentication (extract from
 * {@code Authorization: Bearer <token>} header, resolve via
 * {@link AccountTokenService}, verify character ownership via
 * {@link CharacterService#getOwned}). {@code /api/bank/**} is already
 * {@code permitAll} in {@code SecurityConfig}.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>GET  /api/bank/{characterId}       — savings account + loans (interest accrued)</li>
 *   <li>POST /api/bank/deposit             — wallet → savings</li>
 *   <li>POST /api/bank/withdraw            — savings → wallet</li>
 *   <li>POST /api/bank/loan                — issue a new OPEN loan</li>
 *   <li>POST /api/bank/repay               — repay an OPEN loan</li>
 * </ul>
 *
 * <p>Error envelope: {@code {"error":{"code":"…","message":"…"}}} via the global
 * {@code GlobalExceptionHandler}.
 */
@RestController
@RequestMapping("/api/bank")
public class BankController {

    private final AccountTokenService accountTokenService;
    private final CharacterService    characterService;
    private final BankService         bankService;

    public BankController(AccountTokenService accountTokenService,
                          CharacterService characterService,
                          BankService bankService) {
        this.accountTokenService = accountTokenService;
        this.characterService    = characterService;
        this.bankService         = bankService;
    }

    // ── GET /api/bank/{characterId} ───────────────────────────────────────────

    /**
     * Returns the character's savings account and all loans, with interest on
     * OPEN loans accrued to the current sim-day first. Lazy-creates a zero-balance
     * account if none exists.
     *
     * @return 200 {@link BankStatusResponse}
     */
    @GetMapping("/{characterId}")
    public ResponseEntity<BankStatusResponse> getStatus(
            @PathVariable Long characterId,
            HttpServletRequest request) {

        requireOwnedCharacter(request, characterId);
        return ResponseEntity.ok(bankService.getStatus(characterId));
    }

    // ── POST /api/bank/deposit ────────────────────────────────────────────────

    /**
     * Moves GEL from the character's wallet into savings.
     *
     * <p>Request body: {@code {characterId, amountGel}}.
     *
     * @return 200 with the updated {@link BankAccount}
     */
    @PostMapping("/deposit")
    public ResponseEntity<BankAccount> deposit(
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {

        Long   characterId = extractCharacterId(body);
        double amountGel   = extractAmount(body, "amountGel");

        requireOwnedCharacter(request, characterId);
        return ResponseEntity.ok(bankService.deposit(characterId, amountGel));
    }

    // ── POST /api/bank/withdraw ───────────────────────────────────────────────

    /**
     * Moves GEL from savings into the character's wallet.
     *
     * <p>Request body: {@code {characterId, amountGel}}.
     *
     * @return 200 with the updated {@link BankAccount}
     */
    @PostMapping("/withdraw")
    public ResponseEntity<BankAccount> withdraw(
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {

        Long   characterId = extractCharacterId(body);
        double amountGel   = extractAmount(body, "amountGel");

        requireOwnedCharacter(request, characterId);
        return ResponseEntity.ok(bankService.withdraw(characterId, amountGel));
    }

    // ── POST /api/bank/loan ───────────────────────────────────────────────────

    /**
     * Issues a new OPEN loan and credits the character's wallet with the principal.
     *
     * <p>Request body: {@code {characterId, amountGel}}.
     * Cap: {@code amountGel <= 1000} GEL; only one OPEN loan at a time.
     *
     * @return 200 with the newly created {@link Loan}
     */
    @PostMapping("/loan")
    public ResponseEntity<Loan> takeLoan(
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {

        Long   characterId = extractCharacterId(body);
        double amountGel   = extractAmount(body, "amountGel");

        requireOwnedCharacter(request, characterId);
        return ResponseEntity.ok(bankService.takeLoan(characterId, amountGel));
    }

    // ── POST /api/bank/repay ──────────────────────────────────────────────────

    /**
     * Accrues interest, then pays up to {@code amountGel} from the wallet
     * toward the outstanding loan balance.
     *
     * <p>Request body: {@code {characterId, amountGel}}.
     *
     * @return 200 with the updated {@link Loan}
     */
    @PostMapping("/repay")
    public ResponseEntity<Loan> repay(
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {

        Long   characterId = extractCharacterId(body);
        double amountGel   = extractAmount(body, "amountGel");

        requireOwnedCharacter(request, characterId);
        return ResponseEntity.ok(bankService.repay(characterId, amountGel));
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Extracts and validates the bearer token, returning the accountId.
     *
     * @throws ApiException 401 if the Authorization header is missing or invalid
     */
    private Long requireAccountId(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            throw ApiException.unauthorized("Missing or malformed Authorization header");
        }
        String token = header.substring(7).strip();
        return accountTokenService.accountIdFor(token)
                .orElseThrow(() -> ApiException.unauthorized("Invalid or expired token"));
    }

    /**
     * Verifies that {@code characterId} belongs to the authenticated account.
     *
     * @throws ApiException 401 if the token is invalid, 404 if the character is not
     *                      found or not owned by this account
     */
    private void requireOwnedCharacter(HttpServletRequest request, Long characterId) {
        Long accountId = requireAccountId(request);
        characterService.getOwned(characterId, accountId)
                .orElseThrow(() -> ApiException.notFound(
                        "Character " + characterId
                        + " not found or not owned by this account"));
    }

    /** Extracts {@code characterId} from a raw JSON body map. */
    private Long extractCharacterId(Map<String, Object> body) {
        Object raw = body.get("characterId");
        if (raw == null) {
            throw ApiException.badRequest("Missing required field: 'characterId'");
        }
        return ((Number) raw).longValue();
    }

    /** Extracts a named double field from a raw JSON body map. */
    private double extractAmount(Map<String, Object> body, String fieldName) {
        Object raw = body.get(fieldName);
        if (raw == null) {
            throw ApiException.badRequest("Missing required field: '" + fieldName + "'");
        }
        return ((Number) raw).doubleValue();
    }
}
