package com.game.logistics;

import jakarta.persistence.*;

/**
 * Persistent record of a goods/wine shipment in transit between regions.
 *
 * <p>Created by POST /api/logistics/ship; collected via POST /api/logistics/collect
 * once {@code currentDay >= arriveDay}.
 *
 * <p>Column naming avoids H2/Postgres reserved words:
 * <ul>
 *   <li>{@code ship_status}  — not "status"</li>
 *   <li>{@code from_region} / {@code to_region} — not "from"</li>
 *   <li>{@code depart_day} / {@code arrive_day} — safe in both dialects</li>
 * </ul>
 */
@Entity
@Table(name = "shipments",
       indexes = @Index(name = "idx_shipments_owner_character_id",
                        columnList = "owner_character_id"))
public class Shipment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** FK → mmo_character.id of the character who created this shipment. */
    @Column(name = "owner_character_id", nullable = false)
    private Long ownerCharacterId;

    /**
     * FK → mmo_character.id of the intended recipient.
     * {@code null} means "deliver back to the owner" (self-shipment).
     */
    @Column(name = "recipient_character_id")
    private Long recipientCharacterId;

    /**
     * Type of the shipped item: {@code "GOODS"} or {@code "CELLAR_ITEM"}.
     */
    @Column(name = "kind", nullable = false)
    private String kind;

    /**
     * For GOODS: the {@code goodTypeId} string (e.g. "pruning_shears").
     * For CELLAR_ITEM: the {@link com.game.market.CellarItem} id as a string.
     */
    @Column(name = "ref_id", nullable = false)
    private String refId;

    /** Quantity shipped (unit count for goods; always 1.0 for CELLAR_ITEM). */
    @Column(name = "quantity", nullable = false)
    private double quantity;

    /** Origin region name (Region enum value as string). */
    @Column(name = "from_region", nullable = false)
    private String fromRegion;

    /** Destination region name (Region enum value as string). */
    @Column(name = "to_region", nullable = false)
    private String toRegion;

    /** World-clock absolute day on which the shipment departed. */
    @Column(name = "depart_day", nullable = false)
    private long departDay;

    /**
     * World-clock absolute day on which the shipment arrives.
     * {@code arriveDay = departDay + travelDays(fromRegion, toRegion)}.
     */
    @Column(name = "arrive_day", nullable = false)
    private long arriveDay;

    /**
     * Lifecycle status of this shipment.
     * Values: {@code "IN_TRANSIT"}, {@code "COLLECTED"}, {@code "CANCELLED"}.
     * Column named {@code ship_status} to avoid SQL reserved word {@code status}.
     */
    @Column(name = "ship_status", nullable = false)
    private String shipStatus;

    /** Epoch-ms creation timestamp. */
    @Column(name = "created_at", nullable = false)
    private long createdAt;

    // ── Constructors ──────────────────────────────────────────────────────────

    /** Required by JPA. */
    protected Shipment() {}

    public Shipment(Long ownerCharacterId,
                    Long recipientCharacterId,
                    String kind,
                    String refId,
                    double quantity,
                    String fromRegion,
                    String toRegion,
                    long departDay,
                    long arriveDay) {
        this.ownerCharacterId     = ownerCharacterId;
        this.recipientCharacterId = recipientCharacterId;
        this.kind                 = kind;
        this.refId                = refId;
        this.quantity             = quantity;
        this.fromRegion           = fromRegion;
        this.toRegion             = toRegion;
        this.departDay            = departDay;
        this.arriveDay            = arriveDay;
        this.shipStatus           = "IN_TRANSIT";
        this.createdAt            = System.currentTimeMillis();
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public Long   getId()                     { return id; }
    public Long   getOwnerCharacterId()       { return ownerCharacterId; }
    public Long   getRecipientCharacterId()   { return recipientCharacterId; }
    public String getKind()                   { return kind; }
    public String getRefId()                  { return refId; }
    public double getQuantity()               { return quantity; }
    public String getFromRegion()             { return fromRegion; }
    public String getToRegion()               { return toRegion; }
    public long   getDepartDay()              { return departDay; }
    public long   getArriveDay()              { return arriveDay; }
    public String getShipStatus()             { return shipStatus; }
    public long   getCreatedAt()              { return createdAt; }

    // ── Setters ───────────────────────────────────────────────────────────────

    public void setShipStatus(String shipStatus) { this.shipStatus = shipStatus; }
}
