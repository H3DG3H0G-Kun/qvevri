package com.game.bank;

import com.game.character.CharacterService;
import com.game.exception.ApiException;
import com.game.world.clock.WorldClockService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Business logic for the LANE BANK feature.
 *
 * <h2>Interest model</h2>
 * Interest accrues LAZILY and deterministically. On any access (status or repay),
 * for each OPEN loan we apply:
 * <pre>
 *   outstandingGel *= Math.pow(1 + dailyRate, currentDay − lastAccruedDay)
 *   lastAccruedDay  = currentDay
 * </pre>
 * If {@code currentDay == lastAccruedDay} the exponent is 0 and
 * {@code Math.pow(..., 0) == 1.0}, so the outstanding is unchanged — no floating
 * point drift on same-day accesses.
 *
 * <h2>Loan cap</h2>
 * Maximum principal per loan: {@link #MAX_PRINCIPAL} GEL. A character may hold
 * at most one OPEN loan at a time.
 *
 * <h2>Repayment epsilon</h2>
 * When {@code outstandingGel <= REPAID_EPSILON} (1e-7) after a payment, the loan
 * is considered fully repaid to avoid spurious sub-cent floating-point remainders.
 */
@Service
@Transactional
public class BankService {

    /** Maximum GEL amount that may be borrowed in a single loan. */
    static final double MAX_PRINCIPAL = 1_000.0;

    /** Per-sim-day compound interest rate (1% per day). */
    static final double DEFAULT_DAILY_RATE = 0.01;

    /** Outstanding balance at or below this threshold is treated as fully repaid. */
    static final double REPAID_EPSILON = 1e-7;

    private final BankAccountRepository bankAccountRepository;
    private final LoanRepository        loanRepository;
    private final CharacterService      characterService;
    private final WorldClockService     worldClockService;

    public BankService(BankAccountRepository bankAccountRepository,
                       LoanRepository loanRepository,
                       CharacterService characterService,
                       WorldClockService worldClockService) {
        this.bankAccountRepository = bankAccountRepository;
        this.loanRepository        = loanRepository;
        this.characterService      = characterService;
        this.worldClockService     = worldClockService;
    }

    // ── GET /api/bank/{characterId} ───────────────────────────────────────────

    /**
     * Returns the character's savings account and all loans, with interest on
     * all OPEN loans accrued to the current world-clock day first.
     *
     * <p>Lazy-creates a zero-balance {@link BankAccount} if none exists yet.
     *
     * @param characterId the character to query
     * @return account + loans (interest already applied)
     */
    public BankStatusResponse getStatus(Long characterId) {
        BankAccount account = getOrCreateAccount(characterId);
        List<Loan>  loans   = loanRepository.findByCharacterId(characterId);
        accrueAll(loans);
        return new BankStatusResponse(account, loans);
    }

    // ── POST /api/bank/deposit ────────────────────────────────────────────────

    /**
     * Moves {@code amountGel} from the character's wallet into savings.
     *
     * @param characterId the character
     * @param amountGel   amount to deposit (must be &gt; 0)
     * @return the updated {@link BankAccount}
     * @throws ApiException BAD_REQUEST if amount &le; 0 or wallet insufficient
     */
    public BankAccount deposit(Long characterId, double amountGel) {
        if (amountGel <= 0.0) {
            throw ApiException.badRequest("Deposit amount must be greater than 0");
        }
        // adjustWallet throws INSUFFICIENT_FUNDS / BAD_REQUEST automatically
        characterService.adjustWallet(characterId, -amountGel);

        BankAccount account = getOrCreateAccount(characterId);
        account.setSavingsGel(account.getSavingsGel() + amountGel);
        return bankAccountRepository.save(account);
    }

    // ── POST /api/bank/withdraw ───────────────────────────────────────────────

    /**
     * Moves {@code amountGel} from savings into the character's wallet.
     *
     * @param characterId the character
     * @param amountGel   amount to withdraw (must be &gt; 0)
     * @return the updated {@link BankAccount}
     * @throws ApiException BAD_REQUEST if amount &le; 0 or savings insufficient
     */
    public BankAccount withdraw(Long characterId, double amountGel) {
        if (amountGel <= 0.0) {
            throw ApiException.badRequest("Withdrawal amount must be greater than 0");
        }
        BankAccount account = getOrCreateAccount(characterId);
        if (account.getSavingsGel() < amountGel) {
            throw ApiException.badRequest(
                    "Insufficient savings: have " + account.getSavingsGel()
                    + " GEL, need " + amountGel + " GEL");
        }
        account.setSavingsGel(account.getSavingsGel() - amountGel);
        bankAccountRepository.save(account);

        characterService.adjustWallet(characterId, +amountGel);
        return account;
    }

    // ── POST /api/bank/loan ───────────────────────────────────────────────────

    /**
     * Issues a new OPEN loan of {@code amountGel} and credits the character's wallet.
     *
     * <p>Rules:
     * <ul>
     *   <li>amount must be &gt; 0</li>
     *   <li>amount must not exceed {@link #MAX_PRINCIPAL}</li>
     *   <li>the character must not already hold an OPEN loan</li>
     * </ul>
     *
     * @param characterId the character
     * @param amountGel   loan principal (must be &gt; 0 and &le; {@link #MAX_PRINCIPAL})
     * @return the newly created {@link Loan}
     * @throws ApiException BAD_REQUEST if amount is out of range or a loan already exists
     */
    public Loan takeLoan(Long characterId, double amountGel) {
        if (amountGel <= 0.0) {
            throw ApiException.badRequest("Loan amount must be greater than 0");
        }
        if (amountGel > MAX_PRINCIPAL) {
            throw ApiException.badRequest(
                    "Loan amount " + amountGel + " GEL exceeds maximum of "
                    + MAX_PRINCIPAL + " GEL");
        }

        // Guard: exactly one OPEN loan allowed at a time
        Optional<Loan> existing =
                loanRepository.findByCharacterIdAndLoanStatus(characterId, "OPEN");
        if (existing.isPresent()) {
            throw ApiException.badRequest(
                    "Character already has an OPEN loan; repay it before taking a new one");
        }

        long currentDay = worldClockService.currentAbsoluteDay();
        Loan loan = new Loan(characterId, amountGel, DEFAULT_DAILY_RATE, currentDay);
        loanRepository.save(loan);

        // Credit the character's wallet with the loan principal
        characterService.adjustWallet(characterId, +amountGel);

        return loan;
    }

    // ── POST /api/bank/repay ──────────────────────────────────────────────────

    /**
     * Accrues interest on the OPEN loan, then pays up to {@code amountGel} from
     * the character's wallet toward the outstanding balance.
     *
     * <p>The actual payment is {@code min(amountGel, outstandingGel)} — you cannot
     * overpay; any surplus stays in the wallet. The wallet must be able to cover the
     * intended payment amount (if {@code amountGel > outstanding}, the wallet only
     * needs to cover {@code outstanding}).
     *
     * <p>When outstanding reaches &le; {@link #REPAID_EPSILON}, the loan is marked
     * {@code "REPAID"}.
     *
     * @param characterId the character
     * @param amountGel   amount the character wishes to repay (must be &gt; 0)
     * @return the updated {@link Loan}
     * @throws ApiException BAD_REQUEST if amount &le; 0, no OPEN loan exists, or wallet insufficient
     */
    public Loan repay(Long characterId, double amountGel) {
        if (amountGel <= 0.0) {
            throw ApiException.badRequest("Repayment amount must be greater than 0");
        }

        Loan loan = loanRepository
                .findByCharacterIdAndLoanStatus(characterId, "OPEN")
                .orElseThrow(() -> ApiException.badRequest(
                        "No OPEN loan found for character " + characterId));

        // Accrue interest before computing how much is owed
        accrueInterest(loan);

        double toPay = Math.min(amountGel, loan.getOutstandingGel());

        // Wallet must cover the actual payment (not necessarily the full requested amount)
        characterService.adjustWallet(characterId, -toPay);

        double newOutstanding = loan.getOutstandingGel() - toPay;
        loan.setOutstandingGel(newOutstanding);

        if (newOutstanding <= REPAID_EPSILON) {
            loan.setOutstandingGel(0.0);
            loan.setLoanStatus("REPAID");
        }

        return loanRepository.save(loan);
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    /**
     * Lazy-creates a zero-balance {@link BankAccount} for the character if one
     * does not already exist.
     */
    private BankAccount getOrCreateAccount(Long characterId) {
        return bankAccountRepository.findByCharacterId(characterId)
                .orElseGet(() -> {
                    BankAccount account = new BankAccount(characterId);
                    return bankAccountRepository.save(account);
                });
    }

    /**
     * Applies compound interest to all OPEN loans in the list and persists them.
     * Idempotent when called multiple times on the same world-clock day.
     */
    private void accrueAll(List<Loan> loans) {
        for (Loan loan : loans) {
            if ("OPEN".equals(loan.getLoanStatus())) {
                accrueInterest(loan);
                loanRepository.save(loan);
            }
        }
    }

    /**
     * Applies compound interest to a single OPEN loan in-place.
     *
     * <p>Formula: {@code outstanding *= Math.pow(1 + dailyRate, currentDay − lastAccruedDay)}
     * then {@code lastAccruedDay = currentDay}.
     *
     * <p>When {@code currentDay == lastAccruedDay} the exponent is 0, so
     * {@code Math.pow(x, 0) == 1.0} — no change.
     */
    private void accrueInterest(Loan loan) {
        long currentDay = worldClockService.currentAbsoluteDay();
        long delta = currentDay - loan.getLastAccruedDay();
        if (delta > 0) {
            double factor = Math.pow(1.0 + loan.getDailyRate(), delta);
            loan.setOutstandingGel(loan.getOutstandingGel() * factor);
            loan.setLastAccruedDay(currentDay);
        }
    }
}
