package com.game.travel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Persistent location record for a character — which region they are in and
 * whether they are currently travelling.
 *
 * <p>Lazy-created on first access (GET /api/travel/{characterId}) at the
 * character's {@code homeRegion} with {@code travelStatus = "SETTLED"}.
 *
 * <p>Column names deliberately avoid H2/SQL reserved words:
 * <ul>
 *   <li>{@code travel_status}  (not {@code status}, {@code state})</li>
 *   <li>{@code current_region} (not {@code region})</li>
 *   <li>{@code dest_region}    (nullable; null when SETTLED)</li>
 *   <li>{@code depart_day}     (absolute sim-day of departure)</li>
 *   <li>{@code arrive_day}     (absolute sim-day of arrival)</li>
 * </ul>
 */
@Entity
@Table(name = "character_locations")
public class CharacterLocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** FK to mmo_character.id — unique per character. */
    @Column(name = "character_id", nullable = false, unique = true)
    private Long characterId;

    /** Name of the Region enum value the character is currently in (or departing from). */
    @Column(name = "current_region", nullable = false)
    private String currentRegion;

    /**
     * "SETTLED" — character is in currentRegion.
     * "TRAVELLING" — character is en route to destRegion.
     */
    @Column(name = "travel_status", nullable = false)
    private String travelStatus;

    /** Destination region name; null when SETTLED. */
    @Column(name = "dest_region")
    private String destRegion;

    /** Absolute sim-day the character departed (0 when SETTLED and never travelled). */
    @Column(name = "depart_day", nullable = false)
    private long departDay;

    /** Absolute sim-day the character arrives; >= departDay + 1 when TRAVELLING. */
    @Column(name = "arrive_day", nullable = false)
    private long arriveDay;

    /** Wall-clock epoch-ms when the row was first created. */
    @Column(name = "created_at", nullable = false)
    private long createdAt;

    /** Required by JPA. */
    protected CharacterLocation() {}

    public CharacterLocation(Long characterId, String currentRegion, long createdAt) {
        this.characterId  = characterId;
        this.currentRegion = currentRegion;
        this.travelStatus  = "SETTLED";
        this.destRegion    = null;
        this.departDay     = 0L;
        this.arriveDay     = 0L;
        this.createdAt     = createdAt;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public Long getId()             { return id; }
    public Long getCharacterId()    { return characterId; }
    public String getCurrentRegion(){ return currentRegion; }
    public String getTravelStatus() { return travelStatus; }
    public String getDestRegion()   { return destRegion; }
    public long getDepartDay()      { return departDay; }
    public long getArriveDay()      { return arriveDay; }
    public long getCreatedAt()      { return createdAt; }

    // ── Setters used by TravelService ─────────────────────────────────────────

    public void setCurrentRegion(String currentRegion) { this.currentRegion = currentRegion; }
    public void setTravelStatus(String travelStatus)   { this.travelStatus  = travelStatus; }
    public void setDestRegion(String destRegion)       { this.destRegion    = destRegion; }
    public void setDepartDay(long departDay)           { this.departDay     = departDay; }
    public void setArriveDay(long arriveDay)           { this.arriveDay     = arriveDay; }
}
