package com.game.market;

import jakarta.persistence.*;

/**
 * Persistent inventory item stored in a character's cellar.
 *
 * <p>One row per bottle (or batch) produced by VineyardService and claimed
 * via POST /api/cellar/{characterId}/grow.  When a listing is created for
 * this item the {@code escrowed} flag is set to {@code true} so the item
 * cannot be listed a second time while the first listing is ACTIVE.
 *
 * <p>Fields mirror MMO-CORE-SPEC §2 exactly.
 */
@Entity
@Table(name = "cellar_items")
public class CellarItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** FK → Character.id (owner). Reassigned to buyer on a successful trade. */
    @Column(nullable = false)
    private Long characterId;

    /**
     * Item category, stored as the {@link com.game.econ.ItemType} name string
     * (e.g. "AGED_WINE").  Kept as a String so the econ enum can evolve without
     * a schema migration.
     */
    @Column(nullable = false)
    private String itemType;

    /** Volume in litres (for wine) or kg (for grapes). */
    @Column(nullable = false)
    private double quantity;

    /** Hedonic quality score 0..100. */
    @Column(nullable = false)
    private double quality;

    /** Harvest year from the sim. */
    @Column(nullable = false)
    private int vintageYear;

    /** Wine style name (e.g. "AMBER", "RED"). Null for non-wine goods. */
    private String style;

    /** True if appellation rules are satisfied. */
    @Column(nullable = false)
    private boolean appellationOk;

    /** Generated label string from the sim resolver. */
    private String label;

    /**
     * When {@code true} this item is reserved under an ACTIVE MarketListing.
     * Defaults to {@code false}.  Set to {@code true} on list; cleared when
     * the listing is SOLD or CANCELLED.
     */
    @Column(nullable = false)
    private boolean escrowed = false;

    /** Epoch-ms creation timestamp. */
    @Column(nullable = false)
    private long createdAt;

    // ── Winemaking depth v1 fields (BACKEND-DEPTH-SPEC §6, WINE lane) ─────────
    // All nullable — existing rows default to NULL, which means "no fermentation
    // in progress / instant-harvest path". Tests that check the old fields are
    // completely unaffected.

    /**
     * Fermentation lifecycle state. {@code null} = never started (instant-harvest
     * or not yet opted in). Values: "FERMENTING", "READY", "BOTTLED".
     */
    @Column(nullable = true)
    private String fermentationState;

    /**
     * FK → owned_goods.id of the vessel chosen for this batch.
     * {@code null} = no vessel selected (default behaviour unchanged).
     */
    @Column(nullable = true)
    private Long vesselGoodId;

    /**
     * Absolute world-clock day on which fermentation was started.
     * {@code null} when fermentation has not been started.
     */
    @Column(nullable = true)
    private Long fermentStartedDay;

    /**
     * Absolute world-clock day on which fermentation completes.
     * {@code fermentStartedDay + N_days}; {@code null} until started.
     */
    @Column(nullable = true)
    private Long fermentReadyDay;

    /**
     * Absolute world-clock day from which cellar aging begins (= fermentReadyDay
     * after the batch is ready). {@code null} until fermentation completes.
     */
    @Column(nullable = true)
    private Long agingFromDay;

    /**
     * Snapshot of the quality score at the time the CellarItem was created
     * (before aging improvements). Used to compute the current aged quality.
     * {@code null} for items created before winemaking depth was introduced.
     */
    @Column(nullable = true)
    private Double baseQuality;

    // ── Constructors ──────────────────────────────────────────────────────────

    protected CellarItem() {}

    public CellarItem(Long characterId, String itemType, double quantity,
                      double quality, int vintageYear, String style,
                      boolean appellationOk, String label) {
        this.characterId   = characterId;
        this.itemType      = itemType;
        this.quantity      = quantity;
        this.quality       = quality;
        this.vintageYear   = vintageYear;
        this.style         = style;
        this.appellationOk = appellationOk;
        this.label         = label;
        this.escrowed      = false;
        this.createdAt     = System.currentTimeMillis();
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public Long getId()             { return id; }
    public Long getCharacterId()    { return characterId; }
    public String getItemType()     { return itemType; }
    public double getQuantity()     { return quantity; }
    public double getQuality()      { return quality; }
    public int getVintageYear()     { return vintageYear; }
    public String getStyle()        { return style; }
    public boolean isAppellationOk(){ return appellationOk; }
    public String getLabel()        { return label; }
    public boolean isEscrowed()     { return escrowed; }
    public long getCreatedAt()      { return createdAt; }

    // ── Winemaking getters ────────────────────────────────────────────────────

    public String getFermentationState()  { return fermentationState; }
    public Long getVesselGoodId()         { return vesselGoodId; }
    public Long getFermentStartedDay()    { return fermentStartedDay; }
    public Long getFermentReadyDay()      { return fermentReadyDay; }
    public Long getAgingFromDay()         { return agingFromDay; }
    public Double getBaseQuality()        { return baseQuality; }

    // ── Setters ───────────────────────────────────────────────────────────────

    public void setId(Long id)                     { this.id = id; }
    public void setCharacterId(Long characterId)   { this.characterId = characterId; }
    public void setItemType(String itemType)       { this.itemType = itemType; }
    public void setQuantity(double quantity)       { this.quantity = quantity; }
    public void setQuality(double quality)         { this.quality = quality; }
    public void setVintageYear(int vintageYear)    { this.vintageYear = vintageYear; }
    public void setStyle(String style)             { this.style = style; }
    public void setAppellationOk(boolean ok)       { this.appellationOk = ok; }
    public void setLabel(String label)             { this.label = label; }
    public void setEscrowed(boolean escrowed)      { this.escrowed = escrowed; }
    public void setCreatedAt(long createdAt)       { this.createdAt = createdAt; }

    // ── Winemaking setters ────────────────────────────────────────────────────

    public void setFermentationState(String s)    { this.fermentationState = s; }
    public void setVesselGoodId(Long id)          { this.vesselGoodId = id; }
    public void setFermentStartedDay(Long d)      { this.fermentStartedDay = d; }
    public void setFermentReadyDay(Long d)        { this.fermentReadyDay = d; }
    public void setAgingFromDay(Long d)           { this.agingFromDay = d; }
    public void setBaseQuality(Double q)          { this.baseQuality = q; }
}
