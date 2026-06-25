package com.game.market;

import jakarta.persistence.*;

/**
 * A seller's offer to sell a {@link CellarItem} at a fixed ask price.
 *
 * <p>While a listing is ACTIVE the backing CellarItem has {@code escrowed=true}.
 * On SOLD or CANCELLED the escrow is lifted (or the item is transferred to the buyer).
 *
 * <p>Fields mirror MMO-CORE-SPEC §2 exactly.
 */
@Entity
@Table(name = "market_listings")
public class MarketListing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long sellerCharacterId;

    /** FK → CellarItem.id */
    @Column(nullable = false)
    private Long cellarItemId;

    /** Price in GEL at which the seller offers the item. */
    @Column(nullable = false)
    private double askPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ListingStatus status;

    @Column(nullable = false)
    private long createdAt;

    // ── Constructors ──────────────────────────────────────────────────────────

    protected MarketListing() {}

    public MarketListing(Long sellerCharacterId, Long cellarItemId, double askPrice) {
        this.sellerCharacterId = sellerCharacterId;
        this.cellarItemId      = cellarItemId;
        this.askPrice          = askPrice;
        this.status            = ListingStatus.ACTIVE;
        this.createdAt         = System.currentTimeMillis();
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public Long getId()                   { return id; }
    public Long getSellerCharacterId()    { return sellerCharacterId; }
    public Long getCellarItemId()         { return cellarItemId; }
    public double getAskPrice()           { return askPrice; }
    public ListingStatus getStatus()      { return status; }
    public long getCreatedAt()            { return createdAt; }

    // ── Setters ───────────────────────────────────────────────────────────────

    public void setId(Long id)                       { this.id = id; }
    public void setSellerCharacterId(Long id)        { this.sellerCharacterId = id; }
    public void setCellarItemId(Long id)             { this.cellarItemId = id; }
    public void setAskPrice(double price)            { this.askPrice = price; }
    public void setStatus(ListingStatus status)      { this.status = status; }
    public void setCreatedAt(long createdAt)         { this.createdAt = createdAt; }
}
