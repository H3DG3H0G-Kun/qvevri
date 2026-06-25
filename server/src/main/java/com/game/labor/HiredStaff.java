package com.game.labor;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Persistent record of an NPC staff member hired by a character.
 *
 * <p>Table: {@code hired_staff}. One row per hire event; a character may hire
 * multiple staff of different (or the same) type.
 *
 * <p>Column names avoid H2/SQL reserved words:
 * <ul>
 *   <li>{@code labor_status} — not {@code status}, {@code state}</li>
 *   <li>{@code staff_type_id} — FK to a stable id in {@link StaffCatalog}</li>
 *   <li>{@code hired_day}     — absolute sim-day the staff was hired</li>
 *   <li>{@code last_paid_day} — absolute sim-day wages were last settled via payroll</li>
 *   <li>{@code created_at}   — wall-clock epoch-ms of row creation</li>
 * </ul>
 *
 * <h2>Wage accrual model</h2>
 * Wages accrue LAZILY and deterministically. At any read/payroll call, for each
 * ACTIVE staff member:
 * <pre>
 *   wagesOwed += (currentAbsoluteDay - lastPaidDay) * dailyWageGel
 * </pre>
 * After payroll, {@code lastPaidDay} is set to {@code currentAbsoluteDay}.
 * When {@code currentAbsoluteDay == lastPaidDay} the delta is 0 — no drift.
 *
 * <h2>Lifecycle</h2>
 * {@code ACTIVE} → wage accrues; contributes to {@code /benefits}.
 * {@code QUIT}   → no further accrual; benefit stops.
 * (Auto-fire on missed payroll is a v2 feature; v1 leaves the decision to the player.)
 */
@Entity
@Table(
    name = "hired_staff",
    indexes = @Index(name = "idx_hired_staff_character_id", columnList = "character_id")
)
public class HiredStaff {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** FK → mmo_character.id. */
    @Column(name = "character_id", nullable = false)
    private Long characterId;

    /** Stable staff role id from {@link StaffCatalog}. */
    @Column(name = "staff_type_id", nullable = false)
    private String staffTypeId;

    /** Absolute sim-day this staff member was hired. */
    @Column(name = "hired_day", nullable = false)
    private long hiredDay;

    /**
     * Absolute sim-day wages were last paid out (via payroll).
     * Initialized to {@code hiredDay} so wages start accruing from the day after hire,
     * meaning wagesOwed == 0 on the same day as hire.
     */
    @Column(name = "last_paid_day", nullable = false)
    private long lastPaidDay;

    /**
     * Staff lifecycle state: {@code "ACTIVE"} or {@code "QUIT"}.
     * Named {@code labor_status} to avoid the H2 reserved word {@code status}.
     */
    @Column(name = "labor_status", nullable = false)
    private String laborStatus;

    /** Wall-clock epoch-ms timestamp of row creation. */
    @Column(name = "created_at", nullable = false)
    private long createdAt;

    /** Required by JPA. */
    protected HiredStaff() {}

    /**
     * Creates a new ACTIVE hired staff record.
     *
     * @param characterId the hiring character
     * @param staffTypeId stable role id from {@link StaffCatalog}
     * @param hiredDay    current absolute sim-day (also used as lastPaidDay)
     */
    public HiredStaff(Long characterId, String staffTypeId, long hiredDay) {
        this.characterId  = characterId;
        this.staffTypeId  = staffTypeId;
        this.hiredDay     = hiredDay;
        this.lastPaidDay  = hiredDay;   // wages start accruing from the next day
        this.laborStatus  = "ACTIVE";
        this.createdAt    = Instant.now().toEpochMilli();
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public Long   getId()           { return id; }
    public Long   getCharacterId()  { return characterId; }
    public String getStaffTypeId()  { return staffTypeId; }
    public long   getHiredDay()     { return hiredDay; }
    public long   getLastPaidDay()  { return lastPaidDay; }
    public String getLaborStatus()  { return laborStatus; }
    public long   getCreatedAt()    { return createdAt; }

    // ── Setters ───────────────────────────────────────────────────────────────

    public void setLastPaidDay(long lastPaidDay) { this.lastPaidDay = lastPaidDay; }
    public void setLaborStatus(String laborStatus) { this.laborStatus = laborStatus; }
}
