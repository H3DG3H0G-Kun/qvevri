package com.game.tourism;

import jakarta.persistence.*;

/**
 * Persistent ledger tracking tourism income accrual per character.
 *
 * <p>Table: {@code tourism_ledgers}. Column names avoid H2/SQL reserved words
 * (no {@code value}, {@code year}, {@code level}, {@code status}, {@code rank}).
 *
 * <p>Income accrues lazily off the world clock:
 * <ul>
 *   <li>On first access, the ledger is created with {@code lastClaimDay = currentAbsoluteDay},
 *       so no income has accrued yet.</li>
 *   <li>On each subsequent access, accrued income =
 *       {@code (currentAbsoluteDay - lastClaimDay) × ratePerDay}.</li>
 *   <li>On claim, the accrued amount is credited to the wallet and
 *       {@code lastClaimDay} is reset to {@code currentAbsoluteDay}.</li>
 * </ul>
 */
@Entity
@Table(name = "tourism_ledgers",
       indexes = @Index(name = "uq_tourism_ledgers_character_id",
                        columnList = "character_id",
                        unique = true))
public class TourismLedger {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** FK → mmo_character.id. Unique: one ledger per character. */
    @Column(name = "character_id", nullable = false, unique = true)
    private Long characterId;

    /**
     * The absolute world-clock day when income was last claimed (or the ledger
     * was created). Named {@code last_claim_day} (not {@code last_day} etc.) to
     * avoid any reserved-word conflict.
     */
    @Column(name = "last_claim_day", nullable = false)
    private long lastClaimDay;

    /** Wall-clock epoch-ms timestamp of row creation. */
    @Column(name = "created_at", nullable = false)
    private long createdAt;

    /** Required by JPA. */
    protected TourismLedger() {}

    public TourismLedger(Long characterId, long lastClaimDay, long createdAt) {
        this.characterId  = characterId;
        this.lastClaimDay = lastClaimDay;
        this.createdAt    = createdAt;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public Long getId()          { return id; }
    public Long getCharacterId() { return characterId; }
    public long getLastClaimDay() { return lastClaimDay; }
    public long getCreatedAt()   { return createdAt; }

    // ── Setter ────────────────────────────────────────────────────────────────

    public void setLastClaimDay(long lastClaimDay) {
        this.lastClaimDay = lastClaimDay;
    }
}
