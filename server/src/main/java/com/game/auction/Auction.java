package com.game.auction;

import jakarta.persistence.*;

/**
 * Persistent record of a timed, competitive auction.
 *
 * <p>Auction lifecycle:
 * <ul>
 *   <li>OPEN     — accepting bids; seller's item is held in escrow.</li>
 *   <li>SETTLED  — end day has passed; winner charged, item transferred (or
 *                  returned to seller if no bids). Idempotent.</li>
 *   <li>CANCELLED — reserved for future use (not yet exposed via API).</li>
 * </ul>
 *
 * <p>Table: {@code auctions}. Migration: V15__auctions.sql.
 *
 * <p>Column names are chosen to avoid H2 / SQL reserved words:
 * {@code auction_status} (not status), {@code end_day} (not day),
 * {@code start_bid_gel} / {@code current_bid_gel} (not value/balance),
 * {@code high_bidder_character_id} (not highBidder).
 */
@Entity
@Table(name = "auctions",
       indexes = {
           @Index(name = "idx_auctions_auction_status",        columnList = "auction_status"),
           @Index(name = "idx_auctions_seller_character_id",   columnList = "seller_character_id")
       })
public class Auction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** FK → mmo_character.id — the character that created this auction. */
    @Column(name = "seller_character_id", nullable = false)
    private Long sellerCharacterId;

    /**
     * Discriminator for the asset being auctioned.
     * <ul>
     *   <li>"GOODS"       — a stack of {@link com.game.goods.OwnedGood}.</li>
     *   <li>"CELLAR_ITEM" — a {@link com.game.market.CellarItem}.</li>
     * </ul>
     */
    @Column(nullable = false)
    private String kind;

    /**
     * Stable reference to the asset:
     * <ul>
     *   <li>GOODS: the {@code goodTypeId} catalog string (e.g. "pruning_shears").</li>
     *   <li>CELLAR_ITEM: the {@code cellarItemId} as a decimal string.</li>
     * </ul>
     */
    @Column(name = "ref_id", nullable = false)
    private String refId;

    /** Units on auction (1.0 for CELLAR_ITEM). */
    @Column(nullable = false)
    private double quantity;

    /** Opening bid floor in GEL. */
    @Column(name = "start_bid_gel", nullable = false)
    private double startBidGel;

    /**
     * Current highest bid in GEL; {@code null} until the first bid is placed.
     * Stored as nullable DOUBLE PRECISION.
     */
    @Column(name = "current_bid_gel")
    private Double currentBidGel;

    /**
     * Character id of the current highest bidder; {@code null} until the first bid.
     * Not a FK constraint — avoids cascade issues in test H2.
     */
    @Column(name = "high_bidder_character_id")
    private Long highBidderCharacterId;

    /**
     * Absolute sim-day on which the auction closes.
     * Settlement is triggered lazily when {@code currentDay >= endDay}.
     */
    @Column(name = "end_day", nullable = false)
    private long endDay;

    /**
     * Lifecycle state.
     * <ul>
     *   <li>OPEN      — accepting bids.</li>
     *   <li>SETTLED   — resolved (winner charged / item returned to seller).</li>
     *   <li>CANCELLED — reserved for future use.</li>
     * </ul>
     */
    @Column(name = "auction_status", nullable = false)
    private String auctionStatus;

    /** Epoch-ms creation timestamp. */
    @Column(name = "created_at", nullable = false)
    private long createdAt;

    // ── Constructors ──────────────────────────────────────────────────────────

    protected Auction() {}

    public Auction(Long sellerCharacterId, String kind, String refId,
                   double quantity, double startBidGel, long endDay) {
        this.sellerCharacterId = sellerCharacterId;
        this.kind              = kind;
        this.refId             = refId;
        this.quantity          = quantity;
        this.startBidGel       = startBidGel;
        this.endDay            = endDay;
        this.auctionStatus     = "OPEN";
        this.createdAt         = System.currentTimeMillis();
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public Long getId()                        { return id; }
    public Long getSellerCharacterId()         { return sellerCharacterId; }
    public String getKind()                    { return kind; }
    public String getRefId()                   { return refId; }
    public double getQuantity()                { return quantity; }
    public double getStartBidGel()             { return startBidGel; }
    public Double getCurrentBidGel()           { return currentBidGel; }
    public Long getHighBidderCharacterId()     { return highBidderCharacterId; }
    public long getEndDay()                    { return endDay; }
    public String getAuctionStatus()           { return auctionStatus; }
    public long getCreatedAt()                 { return createdAt; }

    // ── Setters ───────────────────────────────────────────────────────────────

    public void setCurrentBidGel(Double currentBidGel)               { this.currentBidGel = currentBidGel; }
    public void setHighBidderCharacterId(Long highBidderCharacterId)  { this.highBidderCharacterId = highBidderCharacterId; }
    public void setAuctionStatus(String auctionStatus)                { this.auctionStatus = auctionStatus; }
}
