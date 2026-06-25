package com.game.land;

/**
 * Request body for POST /api/land/parcels/{parcelId}/attach-vineyard.
 */
public class AttachVineyardRequest {

    private Long characterId;
    private Long vineyardId;

    public AttachVineyardRequest() {}

    public Long getCharacterId() { return characterId; }
    public Long getVineyardId()  { return vineyardId; }

    public void setCharacterId(Long characterId) { this.characterId = characterId; }
    public void setVineyardId(Long vineyardId)   { this.vineyardId = vineyardId; }
}
