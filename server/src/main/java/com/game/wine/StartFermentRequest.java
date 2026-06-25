package com.game.wine;

/**
 * Request body for {@code POST /api/wine/ferment/start}.
 *
 * <p>Either supply a {@code cellarItemId} (to start fermentation on an existing
 * CellarItem already in the cellar) or a {@code harvestVineyardId} (to trigger
 * harvest + immediate fermentation start in one step).
 *
 * <p>Only one of the two item-selection fields should be set; {@code cellarItemId}
 * takes precedence if both are supplied.
 *
 * <p>{@code vesselGoodId} is the database PK of the OwnedGood representing the
 * vessel to use.  May be {@code null} — in that case fermentation proceeds with
 * no vessel effect (default duration 14 days, no style/quality shift).
 */
public class StartFermentRequest {

    /** Id of an existing CellarItem to begin fermentation on. */
    private Long cellarItemId;

    /**
     * PK of the OwnedGood (VESSEL category) to ferment in.
     * Optional — {@code null} = no vessel selected.
     */
    private Long vesselGoodId;

    /** The character performing the action (ownership check). */
    private Long characterId;

    public Long getCellarItemId()   { return cellarItemId; }
    public Long getVesselGoodId()   { return vesselGoodId; }
    public Long getCharacterId()    { return characterId; }

    public void setCellarItemId(Long id)   { this.cellarItemId = id; }
    public void setVesselGoodId(Long id)   { this.vesselGoodId = id; }
    public void setCharacterId(Long id)    { this.characterId = id; }
}
