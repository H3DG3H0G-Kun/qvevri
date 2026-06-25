package com.game.estate;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request body for POST /api/vineyards.
 *
 * <p>{@code seed} and {@code budLoad} are optional; defaults are applied by the controller.
 */
public class CreateVineyardRequest {

    @JsonProperty("characterId")
    private Long characterId;

    @JsonProperty("region")
    private String region;

    @JsonProperty("variety")
    private String variety;

    /** Optional — if absent the controller derives a seed from vineyard id + creation time. */
    @JsonProperty("seed")
    private Long seed;

    /** Optional — defaults to 12 if absent or <= 0. */
    @JsonProperty("budLoad")
    private Integer budLoad;

    // ── Getters ───────────────────────────────────────────────────────────────

    public Long getCharacterId() { return characterId; }
    public String getRegion()    { return region; }
    public String getVariety()   { return variety; }
    public Long getSeed()        { return seed; }
    public Integer getBudLoad()  { return budLoad; }
}
