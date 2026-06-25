package com.game.bank;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Persistent loan record for a character.
 *
 * <p>Table: {@code loans}. A character may hold at most one OPEN loan at a time.
 *
 * <p>Interest accrues LAZILY and deterministically: on any access, the service
 * applies {@code outstandingGel *= Math.pow(1 + dailyRate, currentDay − lastAccruedDay)}
 * and sets {@code lastAccruedDay = currentDay}. When {@code currentDay == lastAccruedDay}
 * the exponent is 0, so the multiplication is a no-op.
 *
 * <p>Column names avoid H2/SQL reserved words:
 * {@code loan_status} (not {@code status}), {@code daily_rate}, {@code opened_day},
 * {@code last_accrued_day}, {@code outstanding_gel}, {@code principal_gel}.
 */
@Entity
@Table(
    name = "loans",
    indexes = @Index(name = "idx_loans_character_id", columnList = "character_id")
)
public class Loan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** FK → mmo_character.id. */
    @Column(name = "character_id", nullable = false)
    private Long characterId;

    /** Original loan principal in GEL. */
    @Column(name = "principal_gel", nullable = false)
    private double principalGel;

    /**
     * Current outstanding balance in GEL (grows with compound interest).
     * Named {@code outstanding_gel} to avoid reserved words.
     */
    @Column(name = "outstanding_gel", nullable = false)
    private double outstandingGel;

    /**
     * Per-sim-day interest rate, e.g. {@code 0.01} = 1%/day.
     * Named {@code daily_rate} to avoid reserved words.
     */
    @Column(name = "daily_rate", nullable = false)
    private double dailyRate;

    /**
     * Absolute sim-day the loan was opened.
     * Named {@code opened_day} to avoid the reserved word {@code year} and
     * SQL keyword {@code day}.
     */
    @Column(name = "opened_day", nullable = false)
    private long openedDay;

    /**
     * Absolute sim-day interest was last accrued.
     * Named {@code last_accrued_day} to avoid reserved words.
     */
    @Column(name = "last_accrued_day", nullable = false)
    private long lastAccruedDay;

    /**
     * Loan lifecycle state: {@code "OPEN"} or {@code "REPAID"}.
     * Named {@code loan_status} to avoid the H2 reserved word {@code status}.
     */
    @Column(name = "loan_status", nullable = false)
    private String loanStatus;

    /** Wall-clock epoch-ms timestamp of row creation. */
    @Column(name = "created_at", nullable = false)
    private long createdAt;

    /** Required by JPA. */
    protected Loan() {}

    public Loan(Long characterId,
                double principalGel,
                double dailyRate,
                long openedDay) {
        this.characterId    = characterId;
        this.principalGel   = principalGel;
        this.outstandingGel = principalGel;   // outstanding starts equal to principal
        this.dailyRate      = dailyRate;
        this.openedDay      = openedDay;
        this.lastAccruedDay = openedDay;
        this.loanStatus     = "OPEN";
        this.createdAt      = Instant.now().toEpochMilli();
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public Long   getId()             { return id; }
    public Long   getCharacterId()    { return characterId; }
    public double getPrincipalGel()   { return principalGel; }
    public double getOutstandingGel() { return outstandingGel; }
    public double getDailyRate()      { return dailyRate; }
    public long   getOpenedDay()      { return openedDay; }
    public long   getLastAccruedDay() { return lastAccruedDay; }
    public String getLoanStatus()     { return loanStatus; }
    public long   getCreatedAt()      { return createdAt; }

    // ── Setters ───────────────────────────────────────────────────────────────

    public void setOutstandingGel(double outstandingGel) { this.outstandingGel = outstandingGel; }
    public void setLastAccruedDay(long lastAccruedDay)   { this.lastAccruedDay = lastAccruedDay; }
    public void setLoanStatus(String loanStatus)         { this.loanStatus = loanStatus; }
}
