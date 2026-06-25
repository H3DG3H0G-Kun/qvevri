package com.game.estate;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request body for POST /api/vineyards/{vineyardId}/harvest.
 */
public class HarvestRequest {

    @JsonProperty("characterId")
    private Long characterId;

    public Long getCharacterId() { return characterId; }
}
