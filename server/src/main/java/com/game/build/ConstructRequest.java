package com.game.build;

/**
 * Request body for POST /api/build/construct.
 *
 * <p>{@code parcelId} is optional: when null the building is not attached to
 * any land parcel. When provided the controller validates that the parcel belongs
 * to the character (read-only check via {@link com.game.land.ParcelRepository}).
 */
public class ConstructRequest {

    private Long   characterId;
    private Long   parcelId;      // nullable
    private String buildingTypeId;

    public ConstructRequest() {}

    public Long   getCharacterId()    { return characterId; }
    public Long   getParcelId()       { return parcelId; }
    public String getBuildingTypeId() { return buildingTypeId; }

    public void setCharacterId(Long characterId)       { this.characterId = characterId; }
    public void setParcelId(Long parcelId)             { this.parcelId = parcelId; }
    public void setBuildingTypeId(String buildingTypeId) { this.buildingTypeId = buildingTypeId; }
}
