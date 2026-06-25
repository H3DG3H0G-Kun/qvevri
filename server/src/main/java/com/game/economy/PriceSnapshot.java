package com.game.economy;

import jakarta.persistence.*;

/**
 * Persistent price-snapshot record, captured on demand via
 * POST /api/economy/snapshot.
 *
 * <p>Table: {@code price_snapshots}. Migration: V11__price_snapshots.sql.
 *
 * <p>Column names avoid H2/SQL reserved words:
 * <ul>
 *   <li>{@code item_type}    — the ItemType name string (e.g. "AGED_WINE")</li>
 *   <li>{@code region}       — the Region enum name string (e.g. "KAKHETI")</li>
 *   <li>{@code price}        — computed grossPrice at snapshot time</li>
 *   <li>{@code supply_count} — active (non-escrowed) listings count</li>
 *   <li>{@code sim_day}      — world-clock sim day (epoch ms; wall-clock used when no WorldClock present)</li>
 *   <li>{@code created_at}   — epoch ms wall-clock timestamp</li>
 * </ul>
 */
@Entity
@Table(name = "price_snapshots",
       indexes = {
           @Index(name = "idx_price_snapshots_item_region",
                  columnList = "item_type, region")
       })
public class PriceSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** ItemType enum name (e.g. "AGED_WINE"). Not a foreign key — intentionally denormalized. */
    @Column(name = "item_type", nullable = false)
    private String itemType;

    /** Region enum name (e.g. "KAKHETI"). */
    @Column(name = "region", nullable = false)
    private String region;

    /** Computed grossPrice = basePrice × supplyFactor × regionalFactor, in GEL. */
    @Column(name = "price", nullable = false)
    private double price;

    /** Active non-escrowed listing count at snapshot time. */
    @Column(name = "supply_count", nullable = false)
    private long supplyCount;

    /**
     * World-clock sim day at snapshot time.
     * Uses wall-clock millis when no dedicated WorldClockService is wired.
     */
    @Column(name = "sim_day", nullable = false)
    private long simDay;

    /** Epoch-ms wall-clock creation timestamp. */
    @Column(name = "created_at", nullable = false)
    private long createdAt;

    // ── Constructors ──────────────────────────────────────────────────────────

    /** Required by JPA. */
    protected PriceSnapshot() {}

    public PriceSnapshot(String itemType, String region,
                         double price, long supplyCount, long simDay) {
        this.itemType    = itemType;
        this.region      = region;
        this.price       = price;
        this.supplyCount = supplyCount;
        this.simDay      = simDay;
        this.createdAt   = System.currentTimeMillis();
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public Long   getId()          { return id; }
    public String getItemType()    { return itemType; }
    public String getRegion()      { return region; }
    public double getPrice()       { return price; }
    public long   getSupplyCount() { return supplyCount; }
    public long   getSimDay()      { return simDay; }
    public long   getCreatedAt()   { return createdAt; }
}
