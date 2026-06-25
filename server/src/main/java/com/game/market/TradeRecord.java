package com.game.market;

import jakarta.persistence.*;

/**
 * Immutable ledger entry created when a listing is purchased.
 *
 * <p>One TradeRecord per completed buy transaction.
 * Fields mirror MMO-CORE-SPEC §2 exactly.
 */
@Entity
@Table(name = "trade_records")
public class TradeRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long buyerCharacterId;

    @Column(nullable = false)
    private Long sellerCharacterId;

    /** FK → CellarItem.id (the transferred item). */
    @Column(nullable = false)
    private Long cellarItemId;

    /** The price paid in GEL (equals MarketListing.askPrice at time of purchase). */
    @Column(nullable = false)
    private double price;

    @Column(nullable = false)
    private long createdAt;

    // ── Constructors ──────────────────────────────────────────────────────────

    protected TradeRecord() {}

    public TradeRecord(Long buyerCharacterId, Long sellerCharacterId,
                       Long cellarItemId, double price) {
        this.buyerCharacterId  = buyerCharacterId;
        this.sellerCharacterId = sellerCharacterId;
        this.cellarItemId      = cellarItemId;
        this.price             = price;
        this.createdAt         = System.currentTimeMillis();
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public Long getId()                  { return id; }
    public Long getBuyerCharacterId()    { return buyerCharacterId; }
    public Long getSellerCharacterId()   { return sellerCharacterId; }
    public Long getCellarItemId()        { return cellarItemId; }
    public double getPrice()             { return price; }
    public long getCreatedAt()           { return createdAt; }

    // ── Setters ───────────────────────────────────────────────────────────────

    public void setId(Long id)                        { this.id = id; }
    public void setBuyerCharacterId(Long id)          { this.buyerCharacterId = id; }
    public void setSellerCharacterId(Long id)         { this.sellerCharacterId = id; }
    public void setCellarItemId(Long id)              { this.cellarItemId = id; }
    public void setPrice(double price)                { this.price = price; }
    public void setCreatedAt(long createdAt)          { this.createdAt = createdAt; }
}
