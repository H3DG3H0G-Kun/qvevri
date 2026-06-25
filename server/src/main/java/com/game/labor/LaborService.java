package com.game.labor;

import com.game.character.Character;
import com.game.character.CharacterRepository;
import com.game.character.CharacterService;
import com.game.exception.ApiException;
import com.game.world.clock.WorldClockService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Business logic for the LANE LABOR feature.
 *
 * <h2>Wage accrual model</h2>
 * Wages accrue LAZILY and deterministically. On any read or payroll call,
 * for each ACTIVE staff member:
 * <pre>
 *   wagesOwed += (currentAbsoluteDay - lastPaidDay) * dailyWageGel
 * </pre>
 * If {@code currentAbsoluteDay == lastPaidDay} the delta is 0 — no drift on
 * same-day accesses.
 *
 * <h2>Payroll semantics (v1)</h2>
 * Payroll is all-or-nothing: if the wallet can cover the full total wages owed
 * across all ACTIVE staff, it deducts the amount and resets every ACTIVE
 * staff member's {@code lastPaidDay} to the current day. If the wallet cannot
 * cover the full amount, the request is rejected with 400 CANNOT_MAKE_PAYROLL
 * and no state is mutated. Players must manage their balance; v1 does NOT
 * auto-fire staff on a missed payroll (deferred to a future sequential pass).
 *
 * <h2>Fire semantics</h2>
 * Firing sets {@code laborStatus = "QUIT"}, stops further wage accrual, and
 * stops benefit contribution. No refund of hire cost is issued.
 */
@Service
@Transactional
public class LaborService {

    private final HiredStaffRepository hiredStaffRepository;
    private final CharacterService     characterService;
    private final CharacterRepository  characterRepository;
    private final WorldClockService    worldClockService;

    public LaborService(HiredStaffRepository hiredStaffRepository,
                        CharacterService characterService,
                        CharacterRepository characterRepository,
                        WorldClockService worldClockService) {
        this.hiredStaffRepository = hiredStaffRepository;
        this.characterService     = characterService;
        this.characterRepository  = characterRepository;
        this.worldClockService    = worldClockService;
    }

    // ── GET /api/labor/catalog ────────────────────────────────────────────────

    /**
     * Returns all available staff roles from the static catalog.
     *
     * @return unmodifiable collection of {@link StaffRole}s
     */
    @Transactional(readOnly = true)
    public Collection<StaffRole> getCatalog() {
        return StaffCatalog.all();
    }

    // ── GET /api/labor/{characterId} ──────────────────────────────────────────

    /**
     * Returns all staff for the character plus the total wages currently owed.
     *
     * <p>{@code wagesOwed} is computed lazily: for each ACTIVE staff member,
     * {@code (currentDay - lastPaidDay) * dailyWageGel}.
     *
     * @param characterId the character to query
     * @return map with keys {@code "staff"} (list of HiredStaff) and
     *         {@code "wagesOwed"} (double, total GEL accrued but not yet paid)
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getStatus(Long characterId) {
        List<HiredStaff> allStaff = hiredStaffRepository.findByCharacterId(characterId);
        long currentDay = worldClockService.currentAbsoluteDay();
        double wagesOwed = computeWagesOwed(allStaff, currentDay);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("staff", allStaff);
        result.put("wagesOwed", wagesOwed);
        return result;
    }

    // ── POST /api/labor/hire ──────────────────────────────────────────────────

    /**
     * Hires a new NPC staff member.
     *
     * <p>Rules:
     * <ul>
     *   <li>staffTypeId must exist in {@link StaffCatalog}</li>
     *   <li>wallet must cover {@code hireCostGel}; delegates to
     *       {@link CharacterService#adjustWallet} which throws INSUFFICIENT_FUNDS
     *       (HTTP 402) automatically on shortfall</li>
     *   <li>staff is created ACTIVE with {@code hiredDay = lastPaidDay = currentDay}</li>
     * </ul>
     *
     * @param characterId the hiring character
     * @param staffTypeId stable id from {@link StaffCatalog}
     * @return the newly created {@link HiredStaff}
     * @throws ApiException 404 if staffTypeId is unknown
     * @throws ApiException INSUFFICIENT_FUNDS if wallet cannot cover hireCostGel
     */
    public HiredStaff hire(Long characterId, String staffTypeId) {
        StaffRole role = StaffCatalog.find(staffTypeId);
        if (role == null) {
            throw ApiException.notFound("Unknown staffTypeId: " + staffTypeId);
        }

        // Debit hire cost — adjustWallet throws INSUFFICIENT_FUNDS automatically
        characterService.adjustWallet(characterId, -role.hireCostGel());

        long currentDay = worldClockService.currentAbsoluteDay();
        HiredStaff staff = new HiredStaff(characterId, staffTypeId, currentDay);
        return hiredStaffRepository.save(staff);
    }

    // ── POST /api/labor/payroll ───────────────────────────────────────────────

    /**
     * Pays all accrued wages for ACTIVE staff.
     *
     * <p>Computes total wages owed. If the character's wallet can cover the full
     * amount, deducts it and resets every ACTIVE staff's {@code lastPaidDay} to
     * the current day. Returns the amount paid and the resulting wallet balance.
     *
     * <p>If the wallet is insufficient, throws 400 CANNOT_MAKE_PAYROLL with no
     * state mutation. v1 note: does NOT auto-fire staff; leaves it to the player.
     *
     * @param characterId the character running payroll
     * @return map with keys {@code "paid"} (double) and {@code "walletGel"} (double)
     * @throws ApiException 400 CANNOT_MAKE_PAYROLL if wallet &lt; total wages owed
     */
    public Map<String, Object> runPayroll(Long characterId) {
        long currentDay = worldClockService.currentAbsoluteDay();
        List<HiredStaff> activeStaff =
                hiredStaffRepository.findByCharacterIdAndLaborStatus(characterId, "ACTIVE");

        double totalOwed = computeWagesOwed(activeStaff, currentDay);

        if (totalOwed > 0.0) {
            // Attempt to debit — adjustWallet throws ApiException("INSUFFICIENT_FUNDS", ...)
            // with HTTP 402 when wallet < owed. Catch that specific code and re-throw as
            // CANNOT_MAKE_PAYROLL per spec. Let other ApiExceptions (e.g. character not found)
            // propagate unchanged.
            try {
                characterService.adjustWallet(characterId, -totalOwed);
            } catch (ApiException e) {
                if ("INSUFFICIENT_FUNDS".equals(e.getCode())) {
                    throw new ApiException(
                            "CANNOT_MAKE_PAYROLL",
                            "Insufficient funds to pay wages of " + totalOwed + " GEL. "
                            + "Earn more GEL or fire some staff before running payroll. "
                            + "(v1: staff are not auto-fired on a missed payroll.)",
                            HttpStatus.BAD_REQUEST);
                }
                throw e;   // unexpected exception — propagate as-is
            }

            // Deduction succeeded — reset lastPaidDay for all ACTIVE staff
            for (HiredStaff staff : activeStaff) {
                staff.setLastPaidDay(currentDay);
                hiredStaffRepository.save(staff);
            }
        }

        // Read updated wallet balance directly from the character entity
        double walletGel = characterRepository.findById(characterId)
                .map(Character::getWalletGel)
                .orElseThrow(() -> ApiException.badRequest(
                        "Character not found after payroll: " + characterId));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("paid", totalOwed);
        result.put("walletGel", walletGel);
        return result;
    }

    // ── POST /api/labor/{staffId}/fire ────────────────────────────────────────

    /**
     * Fires a staff member, setting their status to {@code "QUIT"}.
     *
     * <p>No refund of the hire cost is issued (v1). Further wage accrual and
     * benefit contribution stop immediately.
     *
     * @param staffId     primary key of the {@link HiredStaff} row
     * @param characterId the character performing the fire (ownership check)
     * @return the updated {@link HiredStaff} (with laborStatus = "QUIT")
     * @throws ApiException 404 if staffId not found or not owned by characterId
     */
    public HiredStaff fire(Long staffId, Long characterId) {
        HiredStaff staff = hiredStaffRepository.findById(staffId)
                .orElseThrow(() -> ApiException.notFound("Staff member not found: " + staffId));

        if (!staff.getCharacterId().equals(characterId)) {
            throw ApiException.notFound(
                    "Staff member " + staffId + " is not owned by character " + characterId);
        }

        staff.setLaborStatus("QUIT");
        return hiredStaffRepository.save(staff);
    }

    // ── GET /api/labor/benefits/{characterId} ─────────────────────────────────

    /**
     * Aggregates benefit values across all ACTIVE staff for the character.
     *
     * <p>Result is a map of {@code benefitType → summedBenefitVal}.
     * Only ACTIVE staff contribute; QUIT staff are excluded.
     *
     * @param characterId the character whose benefits to aggregate
     * @return map of benefitType (e.g. "YIELD") → total benefitVal (double)
     */
    @Transactional(readOnly = true)
    public Map<String, Double> getBenefits(Long characterId) {
        List<HiredStaff> activeStaff =
                hiredStaffRepository.findByCharacterIdAndLaborStatus(characterId, "ACTIVE");

        Map<String, Double> benefits = new LinkedHashMap<>();
        for (HiredStaff staff : activeStaff) {
            StaffRole role = StaffCatalog.find(staff.getStaffTypeId());
            if (role != null) {
                benefits.merge(role.benefitType(), role.benefitVal(), Double::sum);
            }
        }
        return benefits;
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    /**
     * Computes total wages owed across the given staff list for the given day.
     * Only ACTIVE staff contribute.
     */
    private double computeWagesOwed(List<HiredStaff> staffList, long currentDay) {
        double total = 0.0;
        for (HiredStaff staff : staffList) {
            if (!"ACTIVE".equals(staff.getLaborStatus())) {
                continue;
            }
            StaffRole role = StaffCatalog.find(staff.getStaffTypeId());
            if (role == null) {
                continue;
            }
            long delta = currentDay - staff.getLastPaidDay();
            if (delta > 0) {
                total += delta * role.dailyWageGel();
            }
        }
        return total;
    }
}
