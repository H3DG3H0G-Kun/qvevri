package com.game.build;

import jakarta.persistence.*;

/**
 * Persistent record of an estate building owned by a character.
 *
 * <p>Table: {@code buildings}. Column names avoid H2/SQL reserved words
 * (no {@code level}, {@code value}, {@code status}, {@code year}).
 *
 * <p>Each row links a character to a {@link BuildingType} (via the stable
 * {@code building_type_id} string) and optionally to a land parcel.
 */
@Entity
@Table(name = "buildings",
       indexes = @Index(name = "idx_buildings_owner_character_id", columnList = "owner_character_id"))
public class Building {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** FK → mmo_character.id. */
    @Column(name = "owner_character_id", nullable = false)
    private Long ownerCharacterId;

    /** Nullable FK → land_parcels.id (optional parcel attachment). */
    @Column(name = "parcel_id")
    private Long parcelId;

    /** Stable id from {@link BuildingCatalog}. */
    @Column(name = "building_type_id", nullable = false)
    private String buildingTypeId;

    /**
     * Upgrade tier. Default 1 at construction.
     * Named {@code building_level} to avoid the SQL reserved word {@code level}.
     */
    @Column(name = "building_level", nullable = false)
    private int buildingLevel = 1;

    /** World absolute-day when the building was constructed. */
    @Column(name = "built_day", nullable = false)
    private long builtDay;

    /** Wall-clock epoch-ms timestamp of row creation. */
    @Column(name = "created_at", nullable = false)
    private long createdAt;

    /** Required by JPA. */
    protected Building() {}

    public Building(Long ownerCharacterId,
                    Long parcelId,
                    String buildingTypeId,
                    long builtDay,
                    long createdAt) {
        this.ownerCharacterId = ownerCharacterId;
        this.parcelId         = parcelId;
        this.buildingTypeId   = buildingTypeId;
        this.buildingLevel    = 1;
        this.builtDay         = builtDay;
        this.createdAt        = createdAt;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public Long   getId()                { return id; }
    public Long   getOwnerCharacterId()  { return ownerCharacterId; }
    public Long   getParcelId()          { return parcelId; }
    public String getBuildingTypeId()    { return buildingTypeId; }
    public int    getBuildingLevel()     { return buildingLevel; }
    public long   getBuiltDay()          { return builtDay; }
    public long   getCreatedAt()         { return createdAt; }
}
