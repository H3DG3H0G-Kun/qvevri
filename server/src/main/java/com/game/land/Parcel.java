package com.game.land;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A player-owned land parcel anchored to real Georgian coordinates.
 *
 * <p>Coordinates are derived from the region's representative-town centre
 * ({@link com.game.world.WorldCatalog#REGIONS}) with a deterministic jitter
 * so parcels cluster near the real town but don't all overlap.
 * Both latitude and longitude are clamped inside Georgia's bounding box
 * (lat 41.0–43.6, lon 40.0–46.8).
 *
 * <p>The optional {@code vineyardId} is a read-only soft link to an
 * {@code estate.Vineyard}; the LAND lane stores the id on this entity and
 * does NOT edit {@code Vineyard.java}.
 */
@Entity
@Table(name = "land_parcels")
public class Parcel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long ownerCharacterId;

    @Column(nullable = false)
    private String name;

    /** Region enum name (e.g. "KAKHETI"). Stored as a plain String — no FK to Region. */
    @Column(nullable = false)
    private String region;

    /** WGS84 decimal degrees; clamped inside Georgia (41.0–43.6). */
    @Column(nullable = false)
    private double latitude;

    /** WGS84 decimal degrees; clamped inside Georgia (40.0–46.8). */
    @Column(nullable = false)
    private double longitude;

    @Column(nullable = false)
    private double sizeHectares;

    /** Optional soft link to an existing Vineyard (null if not attached). */
    @Column
    private Long vineyardId;

    @Column(nullable = false)
    private long createdAt;

    /** Required by JPA. */
    protected Parcel() {}

    public Parcel(Long ownerCharacterId, String name, String region,
                  double latitude, double longitude,
                  double sizeHectares, long createdAt) {
        this.ownerCharacterId = ownerCharacterId;
        this.name             = name;
        this.region           = region;
        this.latitude         = latitude;
        this.longitude        = longitude;
        this.sizeHectares     = sizeHectares;
        this.vineyardId       = null;
        this.createdAt        = createdAt;
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public Long getId()                 { return id; }
    public Long getOwnerCharacterId()   { return ownerCharacterId; }
    public String getName()             { return name; }
    public String getRegion()           { return region; }
    public double getLatitude()         { return latitude; }
    public double getLongitude()        { return longitude; }
    public double getSizeHectares()     { return sizeHectares; }
    public Long getVineyardId()         { return vineyardId; }
    public long getCreatedAt()          { return createdAt; }

    // ── Setters ───────────────────────────────────────────────────────────────

    public void setVineyardId(Long vineyardId) { this.vineyardId = vineyardId; }
}
