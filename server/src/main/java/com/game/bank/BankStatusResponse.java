package com.game.bank;

import java.util.List;

/**
 * Response DTO for {@code GET /api/bank/{characterId}}.
 *
 * <p>Returns the character's savings account together with all their loans
 * (interest already accrued to the current world-clock day before this
 * object is constructed).
 */
public class BankStatusResponse {

    private final BankAccount account;
    private final List<Loan>  loans;

    public BankStatusResponse(BankAccount account, List<Loan> loans) {
        this.account = account;
        this.loans   = loans;
    }

    public BankAccount getAccount() { return account; }
    public List<Loan>  getLoans()   { return loans; }
}
