package com.game.tourism;

import com.game.build.BuildingRepository;
import com.game.character.Character;
import com.game.character.CharacterRepository;
import com.game.character.CharacterService;
import com.game.exception.ApiException;
import com.game.world.clock.WorldClockService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Business logic for the Tourism lane — passive income that scales with
 * the number of estate buildings a character owns.
 *
 * <h2>Income formula (deterministic, lazy)</h2>
 * <pre>
 *   buildingsCount  = BuildingRepository.findByOwnerCharacterId(characterId).size()
 *   ratePerDay      = BASE_PER_DAY + PER_BUILDING × buildingsCount
 *   accruedSoFar    = (currentAbsoluteDay − lastClaimDay) × ratePerDay
 * </pre>
 *
 * <h2>Example</h2>
 * <ul>
 *   <li>Character owns 3 buildings, last claimed 10 days ago.</li>
 *   <li>ratePerDay = 2.0 + 1.0 × 3 = 5.0 GEL/day</li>
 *   <li>accruedSoFar = 10 × 5.0 = 50.0 GEL</li>
 * </ul>
 *
 * <h2>Lazy ledger creation</h2>
 * The ledger is created on the first read/claim call with
 * {@code lastClaimDay = currentAbsoluteDay}, ensuring a brand-new ledger
 * starts with 0 accrued income until sim-days pass.
 */
@Service
public class TourismService {

    /** Base passive income per sim-day regardless of buildings. */
    static final double BASE_PER_DAY = 2.0;

    /** Additional income per sim-day per owned building. */
    static final double PER_BUILDING = 1.0;

    private final TourismLedgerRepository ledgerRepository;
    private final BuildingRepository      buildingRepository;
    private final CharacterService        characterService;
    private final CharacterRepository     characterRepository;
    private final WorldClockService       worldClockService;

    public TourismService(TourismLedgerRepository ledgerRepository,
                          BuildingRepository buildingRepository,
                          CharacterService characterService,
                          CharacterRepository characterRepository,
                          WorldClockService worldClockService) {
        this.ledgerRepository    = ledgerRepository;
        this.buildingRepository  = buildingRepository;
        this.characterService    = characterService;
        this.characterRepository = characterRepository;
        this.worldClockService   = worldClockService;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Returns a snapshot of the tourism income state for the given character.
     * Lazy-creates the ledger if it does not yet exist (with lastClaimDay =
     * currentAbsoluteDay, so accruedSoFar = 0 on first call).
     *
     * @param characterId the character to query (ownership already verified by controller)
     * @return the income snapshot
     */
    @Transactional
    public TourismSnapshot getSnapshot(Long characterId) {
        TourismLedger ledger = getOrCreateLedger(characterId);
        long   currentDay    = worldClockService.currentAbsoluteDay();
        int    buildingsCount = buildingRepository.findByOwnerCharacterId(characterId).size();
        double ratePerDay    = BASE_PER_DAY + PER_BUILDING * buildingsCount;
        long   daysSince     = Math.max(0L, currentDay - ledger.getLastClaimDay());
        double accruedSoFar  = daysSince * ratePerDay;

        return new TourismSnapshot(
                ledger.getLastClaimDay(),
                buildingsCount,
                ratePerDay,
                accruedSoFar);
    }

    /**
     * Claims all accrued tourism income for the given character: credits the
     * wallet by the accrued amount and resets {@code lastClaimDay} to
     * {@code currentAbsoluteDay}. Returns the amount paid, the updated wallet
     * balance, and the new lastClaimDay.
     *
     * @param characterId the character claiming income (ownership already verified)
     * @return the claim result
     */
    @Transactional
    public TourismClaimResult claim(Long characterId) {
        TourismLedger ledger = getOrCreateLedger(characterId);
        long   currentDay    = worldClockService.currentAbsoluteDay();
        int    buildingsCount = buildingRepository.findByOwnerCharacterId(characterId).size();
        double ratePerDay    = BASE_PER_DAY + PER_BUILDING * buildingsCount;
        long   daysSince     = Math.max(0L, currentDay - ledger.getLastClaimDay());
        double accrued       = daysSince * ratePerDay;

        // Credit wallet (skip adjustWallet when accrued == 0.0 to avoid needless write)
        if (accrued > 0.0) {
            characterService.adjustWallet(characterId, accrued);
        }

        // Reset the claim watermark
        ledger.setLastClaimDay(currentDay);
        ledgerRepository.save(ledger);

        // Read the updated wallet balance to return to the caller
        double walletGel = characterRepository.findById(characterId)
                .map(Character::getWalletGel)
                .orElseThrow(() -> ApiException.badRequest(
                        "Character not found after claim: " + characterId));

        return new TourismClaimResult(accrued, walletGel, currentDay);
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    /**
     * Loads or lazy-creates the ledger. Creates with
     * {@code lastClaimDay = currentAbsoluteDay} so income starts accruing from now.
     */
    @Transactional
    TourismLedger getOrCreateLedger(Long characterId) {
        return ledgerRepository.findByCharacterId(characterId).orElseGet(() -> {
            long currentDay = worldClockService.currentAbsoluteDay();
            TourismLedger newLedger = new TourismLedger(
                    characterId,
                    currentDay,
                    System.currentTimeMillis());
            return ledgerRepository.save(newLedger);
        });
    }
}
