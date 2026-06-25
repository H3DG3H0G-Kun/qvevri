package com.game.goods;

import java.util.Map;

/**
 * Immutable catalog entry for a purchasable / tradeable good.
 *
 * <p>Instances are created only by {@link GoodsCatalog}; the constructor is
 * package-private to enforce that.
 *
 * <p>The {@code attributes} map carries type-specific metadata for later
 * pipeline hooks (§6 integration pass):
 * <ul>
 *   <li>VESSEL  – "capacityL" (Double), "material" (String: qvevri/oak/steel)</li>
 *   <li>VINE_STOCK – "certified" (Boolean), "ownRoots" (Boolean), "variety" (String)</li>
 *   <li>EQUIPMENT  – "qualityTier" (Integer 1-3)</li>
 *   <li>INPUT      – "potency" (Double 0.0-1.0)</li>
 * </ul>
 */
public final class GoodType {

    private final String                id;
    private final GoodCategory          category;
    private final String                displayName;
    private final double                basePrice;
    private final boolean               consumable;
    private final Map<String, Object>   attributes;

    GoodType(String id, GoodCategory category, String displayName,
             double basePrice, boolean consumable, Map<String, Object> attributes) {
        this.id          = id;
        this.category    = category;
        this.displayName = displayName;
        this.basePrice   = basePrice;
        this.consumable  = consumable;
        this.attributes  = Map.copyOf(attributes);
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public String getId()                      { return id; }
    public GoodCategory getCategory()          { return category; }
    public String getDisplayName()             { return displayName; }
    public double getBasePrice()               { return basePrice; }
    public boolean isConsumable()              { return consumable; }
    public Map<String, Object> getAttributes() { return attributes; }
}
