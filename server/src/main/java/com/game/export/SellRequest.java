package com.game.export;

/**
 * Request body for {@code POST /api/export/sell}.
 *
 * <p>All fields are required:
 * <ul>
 *   <li>{@code characterId}    — the selling character (ownership is verified against the bearer token)</li>
 *   <li>{@code foreignMarketId} — target market id from {@link ForeignMarketCatalog} (e.g. "byzantium")</li>
 *   <li>{@code cellarItemId}   — the {@code CellarItem} to export (must be owned by {@code characterId})</li>
 *   <li>{@code quantity}       — volume to export in litres; must be &gt; 0 and ≤ item.getQuantity()</li>
 * </ul>
 */
public class SellRequest {

    private Long characterId;
    private String foreignMarketId;
    private Long cellarItemId;
    private double quantity;

    public SellRequest() {}

    public SellRequest(Long characterId, String foreignMarketId, Long cellarItemId, double quantity) {
        this.characterId     = characterId;
        this.foreignMarketId = foreignMarketId;
        this.cellarItemId    = cellarItemId;
        this.quantity        = quantity;
    }

    public Long getCharacterId()      { return characterId; }
    public String getForeignMarketId() { return foreignMarketId; }
    public Long getCellarItemId()     { return cellarItemId; }
    public double getQuantity()       { return quantity; }

    public void setCharacterId(Long characterId)           { this.characterId = characterId; }
    public void setForeignMarketId(String foreignMarketId) { this.foreignMarketId = foreignMarketId; }
    public void setCellarItemId(Long cellarItemId)         { this.cellarItemId = cellarItemId; }
    public void setQuantity(double quantity)               { this.quantity = quantity; }
}
