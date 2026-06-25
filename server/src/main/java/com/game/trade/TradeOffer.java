package com.game.trade;

import jakarta.persistence.*;

/**
 * Persistent record of a player-to-player trade offer.
 *
 * <p>A seller lists either a {@link com.game.market.CellarItem} (kind=CELLAR_ITEM)
 * or a goods stack (kind=GOODS) at a fixed price in GEL. Any other player can
 * accept the offer in a single atomic operation that transfers money and the item.
 *
 * <p>Table: {@code trade_offers}. Migration: V8__trade_offers.sql.
 */
@Entity
@Table(name = "trade_offers",
       indexes = {
           @Index(name = "idx_trade_offers_status",            columnList = "status"),
           @Index(name = "idx_trade_offers_seller_character",  columnList = "seller_character_id")
       })
public class TradeOffer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** FK → mmo_character.id — the character that created this offer. */
    @Column(name = "seller_character_id", nullable = false)
    private Long sellerCharacterId;

    /**
     * FK → mmo_character.id — populated when the offer is ACCEPTED.
     * {@code null} while OPEN or CANCELLED.
     */
    @Column(name = "buyer_character_id")
    private Long buyerCharacterId;

    /**
     * Discriminator for the asset being sold.
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
     *   <li>For GOODS: the {@code goodTypeId} catalog string (e.g. "pruning_shears").</li>
     *   <li>For CELLAR_ITEM: the {@code cellarItemId} serialised as a decimal string.</li>
     * </ul>
     */
    @Column(nullable = false)
    private String reference;

    /**
     * How many units are on offer.
     * <ul>
     *   <li>For GOODS: number of units (e.g. 2.0 pruning shears).</li>
     *   <li>For CELLAR_ITEM: must be 1.0 (a cellar item is an indivisible unit).</li>
     * </ul>
     */
    @Column(nullable = false)
    private double quantity;

    /** Total price in GEL for the entire lot (not per-unit). */
    @Column(name = "price_gel", nullable = false)
    private double priceGel;

    /**
     * Lifecycle state.
     * <ul>
     *   <li>OPEN      — visible in the marketplace; can be accepted or cancelled.</li>
     *   <li>ACCEPTED  — a buyer accepted; money+item have been transferred.</li>
     *   <li>CANCELLED — the seller cancelled; any reservation released.</li>
     * </ul>
     */
    @Column(nullable = false)
    private String status;

    /** Epoch-ms creation timestamp. */
    @Column(name = "created_at", nullable = false)
    private long createdAt;

    // ── Constructors ──────────────────────────────────────────────────────────

    protected TradeOffer() {}

    public TradeOffer(Long sellerCharacterId, String kind, String reference,
                      double quantity, double priceGel) {
        this.sellerCharacterId = sellerCharacterId;
        this.kind              = kind;
        this.reference         = reference;
        this.quantity          = quantity;
        this.priceGel          = priceGel;
        this.status            = TradeOfferStatus.OPEN;
        this.createdAt         = System.currentTimeMillis();
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public Long getId()                   { return id; }
    public Long getSellerCharacterId()    { return sellerCharacterId; }
    public Long getBuyerCharacterId()     { return buyerCharacterId; }
    public String getKind()               { return kind; }
    public String getReference()          { return reference; }
    public double getQuantity()           { return quantity; }
    public double getPriceGel()           { return priceGel; }
    public String getStatus()             { return status; }
    public long getCreatedAt()            { return createdAt; }

    // ── Setters ───────────────────────────────────────────────────────────────

    public void setBuyerCharacterId(Long buyerCharacterId) { this.buyerCharacterId = buyerCharacterId; }
    public void setStatus(String status)                   { this.status = status; }
}
