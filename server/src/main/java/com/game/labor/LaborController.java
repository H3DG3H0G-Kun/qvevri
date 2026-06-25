package com.game.labor;

import com.game.account.AccountTokenService;
import com.game.character.CharacterService;
import com.game.exception.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.Map;

/**
 * REST controller for the LANE LABOR — {@code /api/labor/**}.
 *
 * <p>All endpoints use inline bearer-token authentication (extract from
 * {@code Authorization: Bearer <token>} header, resolve via
 * {@link AccountTokenService}, verify character ownership via
 * {@link CharacterService#getOwned}). {@code /api/labor/**} is already
 * {@code permitAll} in {@code SecurityConfig}.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>GET  /api/labor/catalog                  — all hireable staff roles</li>
 *   <li>GET  /api/labor/{characterId}             — character's staff + wagesOwed</li>
 *   <li>POST /api/labor/hire                      — hire staff (debit hireCost)</li>
 *   <li>POST /api/labor/payroll                   — pay all owed wages or 400</li>
 *   <li>POST /api/labor/{staffId}/fire            — mark staff QUIT</li>
 *   <li>GET  /api/labor/benefits/{characterId}    — aggregated benefitType → value</li>
 * </ul>
 *
 * <p>Error envelope: {@code {"error":{"code":"…","message":"…"}}} via the global
 * {@code GlobalExceptionHandler}.
 *
 * <p>Route ordering note: {@code /api/labor/catalog} and
 * {@code /api/labor/benefits/{characterId}} must be declared before
 * {@code /api/labor/{characterId}} in the class to ensure Spring MVC matches
 * the literal paths first. Spring MVC resolves the most specific path first by
 * default, so this ordering is safe; the annotations are explicit.
 */
@RestController
@RequestMapping("/api/labor")
public class LaborController {

    private final AccountTokenService accountTokenService;
    private final CharacterService    characterService;
    private final LaborService        laborService;

    public LaborController(AccountTokenService accountTokenService,
                           CharacterService characterService,
                           LaborService laborService) {
        this.accountTokenService = accountTokenService;
        this.characterService    = characterService;
        this.laborService        = laborService;
    }

    // ── GET /api/labor/catalog ────────────────────────────────────────────────

    /**
     * Returns all hireable staff roles from the static catalog.
     * No authentication required (catalog is public metadata).
     *
     * @return 200 with collection of {@link StaffRole}s
     */
    @GetMapping("/catalog")
    public ResponseEntity<Collection<StaffRole>> getCatalog() {
        return ResponseEntity.ok(laborService.getCatalog());
    }

    // ── GET /api/labor/benefits/{characterId} ─────────────────────────────────

    /**
     * Returns the aggregated benefits across all ACTIVE staff for the character.
     * Result is a map of {@code benefitType → summedBenefitVal}.
     *
     * @return 200 with map of benefitType to total value
     */
    @GetMapping("/benefits/{characterId}")
    public ResponseEntity<Map<String, Double>> getBenefits(
            @PathVariable Long characterId,
            HttpServletRequest request) {

        requireOwnedCharacter(request, characterId);
        return ResponseEntity.ok(laborService.getBenefits(characterId));
    }

    // ── GET /api/labor/{characterId} ──────────────────────────────────────────

    /**
     * Returns the character's staff list and total wages currently owed.
     *
     * <p>Response body: {@code { staff: HiredStaff[], wagesOwed: double }}.
     * {@code wagesOwed} is computed lazily from the world clock — zero on the
     * same sim-day as hire, growing each subsequent day.
     *
     * @return 200 with staff + wagesOwed
     */
    @GetMapping("/{characterId}")
    public ResponseEntity<Map<String, Object>> getStatus(
            @PathVariable Long characterId,
            HttpServletRequest request) {

        requireOwnedCharacter(request, characterId);
        return ResponseEntity.ok(laborService.getStatus(characterId));
    }

    // ── POST /api/labor/hire ──────────────────────────────────────────────────

    /**
     * Hires a new NPC staff member.
     *
     * <p>Request body: {@code { characterId, staffTypeId }}.
     * Debits {@code hireCostGel} from the wallet and creates an ACTIVE staff row
     * with {@code hiredDay = lastPaidDay = currentAbsoluteDay}.
     *
     * @return 200 with the newly created {@link HiredStaff}
     */
    @PostMapping("/hire")
    public ResponseEntity<HiredStaff> hire(
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {

        Long   characterId = extractCharacterId(body);
        String staffTypeId = extractString(body, "staffTypeId");

        requireOwnedCharacter(request, characterId);
        return ResponseEntity.ok(laborService.hire(characterId, staffTypeId));
    }

    // ── POST /api/labor/payroll ───────────────────────────────────────────────

    /**
     * Runs payroll — pays all wages owed to ACTIVE staff.
     *
     * <p>Request body: {@code { characterId }}.
     * Returns {@code { paid, walletGel }}.
     *
     * <p>If the wallet cannot cover the total wages owed, returns 400
     * {@code CANNOT_MAKE_PAYROLL}. v1 note: staff are NOT auto-fired.
     *
     * @return 200 with {@code { paid, walletGel }}
     */
    @PostMapping("/payroll")
    public ResponseEntity<Map<String, Object>> runPayroll(
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {

        Long characterId = extractCharacterId(body);

        requireOwnedCharacter(request, characterId);
        return ResponseEntity.ok(laborService.runPayroll(characterId));
    }

    // ── POST /api/labor/{staffId}/fire ────────────────────────────────────────

    /**
     * Fires a staff member (sets laborStatus to QUIT, stops accrual).
     *
     * <p>Request body: {@code { characterId }}.
     * No hire cost refund is issued (v1).
     *
     * @return 200 with the updated {@link HiredStaff}
     */
    @PostMapping("/{staffId}/fire")
    public ResponseEntity<HiredStaff> fire(
            @PathVariable Long staffId,
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {

        Long characterId = extractCharacterId(body);

        requireOwnedCharacter(request, characterId);
        return ResponseEntity.ok(laborService.fire(staffId, characterId));
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

    /** Extracts a named String field from a raw JSON body map. */
    private String extractString(Map<String, Object> body, String fieldName) {
        Object raw = body.get(fieldName);
        if (raw == null) {
            throw ApiException.badRequest("Missing required field: '" + fieldName + "'");
        }
        return raw.toString();
    }
}
