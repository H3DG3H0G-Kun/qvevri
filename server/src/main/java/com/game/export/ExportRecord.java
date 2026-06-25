package com.game.export;

import jakarta.persistence.*;

/**
 * Persistent record of a successful foreign-market export transaction.
 *
 * <h2>Pricing formula (documented here and in ExportService)</h2>
 * <pre>
 *   K               = 0.5  (GEL per quality point — documented constant)
 *   perBottleBase   = item.getQuality() × K
 *   gross           = perBottleBase × quantity × market.priceMultiplier()
 *   tariff          = gross × market.tariffRate()
 *   net             = gross − tariff
 * </pre>
 *
 * <p>All monetary amounts are stored in GEL.
 *
 * <h2>Column-name safety</h2>
 * H2 and PostgreSQL reserved words avoided: {@code foreign_market_id}, {@code gross_gel},
 * {@code tariff_gel}, {@code net_gel}, {@code sold_day} are all safe. The column name
 * {@code quantity} is safe; {@code value} / {@code year} / {@code level} / {@code status}
 * are NOT used here.
 */
@Entity
@Table(name = "export_records",
        indexes = @Index(name = "idx_export_records_seller_character_id",
                         columnList = "seller_character_id"))
public class ExportRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** FK → mmo_character.id of the selling character. */
    @Column(name = "seller_character_id", nullable = false)
    private Long sellerCharacterId;

    /** Id of the {@link ForeignMarket} (e.g. "russia", "byzantium"). */
    @Column(name = "foreign_market_id", nullable = false)
    private String foreignMarketId;

    /** FK → cellar_items.id of the exported item. */
    @Column(name = "cellar_item_id", nullable = false)
    private Long cellarItemId;

    /** Volume (litres) exported in this transaction. */
    @Column(nullable = false)
    private double quantity;

    /** Gross revenue before tariff (GEL). */
    @Column(name = "gross_gel", nullable = false)
    private double grossGel;

    /** Tariff deducted from gross (GEL). */
    @Column(name = "tariff_gel", nullable = false)
    private double tariffGel;

    /** Net revenue credited to the seller's wallet (grossGel − tariffGel, GEL). */
    @Column(name = "net_gel", nullable = false)
    private double netGel;

    /** Absolute world-clock day on which the export was executed. */
    @Column(name = "sold_day", nullable = false)
    private long soldDay;

    /** Wall-clock epoch-ms of row creation. */
    @Column(name = "created_at", nullable = false)
    private long createdAt;

    /** Required by JPA. */
    protected ExportRecord() {}

    public ExportRecord(Long sellerCharacterId, String foreignMarketId, Long cellarItemId,
                        double quantity, double grossGel, double tariffGel, double netGel,
                        long soldDay) {
        this.sellerCharacterId = sellerCharacterId;
        this.foreignMarketId   = foreignMarketId;
        this.cellarItemId      = cellarItemId;
        this.quantity          = quantity;
        this.grossGel          = grossGel;
        this.tariffGel         = tariffGel;
        this.netGel            = netGel;
        this.soldDay           = soldDay;
        this.createdAt         = System.currentTimeMillis();
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public Long getId()                  { return id; }
    public Long getSellerCharacterId()   { return sellerCharacterId; }
    public String getForeignMarketId()   { return foreignMarketId; }
    public Long getCellarItemId()        { return cellarItemId; }
    public double getQuantity()          { return quantity; }
    public double getGrossGel()          { return grossGel; }
    public double getTariffGel()         { return tariffGel; }
    public double getNetGel()            { return netGel; }
    public long getSoldDay()             { return soldDay; }
    public long getCreatedAt()           { return createdAt; }
}
