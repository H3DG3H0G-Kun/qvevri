package com.game.bank;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Persistent savings account for a character.
 *
 * <p>Table: {@code bank_accounts}. One row per character (unique constraint on
 * {@code character_id}). Lazy-created on first access — if no row exists for a
 * character, {@link BankService} inserts one with {@code savings_gel = 0}.
 *
 * <p>Column names avoid H2/SQL reserved words: {@code savings_gel} (not
 * {@code balance} or {@code value}), {@code character_id} (not {@code character}).
 */
@Entity
@Table(
    name = "bank_accounts",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_bank_accounts_character_id",
        columnNames = "character_id"
    )
)
public class BankAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** FK → mmo_character.id. Unique: one savings account per character. */
    @Column(name = "character_id", nullable = false, unique = true)
    private Long characterId;

    /**
     * Current savings balance in GEL. Named {@code savings_gel} to avoid the
     * H2 reserved word {@code balance}.
     */
    @Column(name = "savings_gel", nullable = false)
    private double savingsGel = 0.0;

    /** Wall-clock epoch-ms timestamp of row creation. */
    @Column(name = "created_at", nullable = false)
    private long createdAt;

    /** Required by JPA. */
    protected BankAccount() {}

    public BankAccount(Long characterId) {
        this.characterId = characterId;
        this.savingsGel  = 0.0;
        this.createdAt   = Instant.now().toEpochMilli();
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public Long   getId()          { return id; }
    public Long   getCharacterId() { return characterId; }
    public double getSavingsGel()  { return savingsGel; }
    public long   getCreatedAt()   { return createdAt; }

    // ── Setters ───────────────────────────────────────────────────────────────

    public void setSavingsGel(double savingsGel) { this.savingsGel = savingsGel; }
}
