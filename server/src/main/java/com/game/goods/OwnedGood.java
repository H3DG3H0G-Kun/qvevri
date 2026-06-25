package com.game.goods;

import jakarta.persistence.*;

/**
 * Persistent record of a good owned by a character.
 *
 * <p>One row per (character, goodType) combination; consumable goods are
 * stacked via the {@code quantity} field (e.g. 12 cuttings, 5 kg of sulfur).
 * Durable goods also use quantity so a character can own multiple qvevri.
 *
 * <p>{@code condition01} starts at {@code 1.0} (new) and may degrade over
 * time in future passes. {@code acquiredAt} is epoch-ms.
 */
@Entity
@Table(name = "owned_goods",
       indexes = @Index(name = "idx_owned_goods_character_id", columnList = "character_id"))
public class OwnedGood {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** FK → mmo_character.id. */
    @Column(name = "character_id", nullable = false)
    private Long characterId;

    /** Stable string id from {@link GoodsCatalog}. */
    @Column(name = "good_type_id", nullable = false)
    private String goodTypeId;

    /**
     * Stack quantity. For consumables this decrements on use; for durables
     * it counts how many units the character owns.
     */
    @Column(nullable = false)
    private double quantity;

    /**
     * Condition 0.0 (broken) – 1.0 (new). Defaults to 1.0 on acquisition;
     * degradation hooks are a §6 concern.
     */
    @Column(name = "condition01", nullable = false)
    private double condition01 = 1.0;

    /** Epoch-ms timestamp when the good was first acquired. */
    @Column(name = "acquired_at", nullable = false)
    private long acquiredAt;

    /** Required by JPA. */
    protected OwnedGood() {}

    public OwnedGood(Long characterId, String goodTypeId, double quantity) {
        this.characterId = characterId;
        this.goodTypeId  = goodTypeId;
        this.quantity    = quantity;
        this.condition01 = 1.0;
        this.acquiredAt  = System.currentTimeMillis();
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public Long getId()           { return id; }
    public Long getCharacterId()  { return characterId; }
    public String getGoodTypeId() { return goodTypeId; }
    public double getQuantity()   { return quantity; }
    public double getCondition01(){ return condition01; }
    public long getAcquiredAt()   { return acquiredAt; }

    // ── Setters ───────────────────────────────────────────────────────────────

    public void setQuantity(double quantity)     { this.quantity = quantity; }
    public void setCondition01(double condition) { this.condition01 = condition; }
}
