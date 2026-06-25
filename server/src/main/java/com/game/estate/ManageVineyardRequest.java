package com.game.estate;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request body for POST /api/vineyards/{vineyardId}/manage.
 *
 * <p>All lever fields are optional (nullable wrappers). Only non-null fields
 * are applied to the Vineyard; null fields leave the current value unchanged.
 * {@code characterId} is required (ownership verification).
 *
 * <p>Range constraints (validated by the controller, not Bean Validation,
 * so we can return the same error envelope as the rest of the estate API):
 * <ul>
 *   <li>budLoad: 1..40</li>
 *   <li>canopyOpenness01, copperSpray01, sulfurSpray01, coverCrop01: 0.0..1.0</li>
 * </ul>
 */
public class ManageVineyardRequest {

    @JsonProperty("characterId")
    private Long characterId;

    // ── Lever fields (all optional) ────────────────────────────────────────────

    @JsonProperty("budLoad")
    private Integer budLoad;

    @JsonProperty("ownRoots")
    private Boolean ownRoots;

    @JsonProperty("canopyOpenness01")
    private Double canopyOpenness01;

    @JsonProperty("leafPulled")
    private Boolean leafPulled;

    @JsonProperty("copperSpray01")
    private Double copperSpray01;

    @JsonProperty("sulfurSpray01")
    private Double sulfurSpray01;

    @JsonProperty("netting")
    private Boolean netting;

    @JsonProperty("guardDog")
    private Boolean guardDog;

    @JsonProperty("falcons")
    private Boolean falcons;

    @JsonProperty("cats")
    private Boolean cats;

    @JsonProperty("ducks")
    private Boolean ducks;

    @JsonProperty("coverCrop01")
    private Double coverCrop01;

    // ── Getters ───────────────────────────────────────────────────────────────

    public Long    getCharacterId()       { return characterId; }
    public Integer getBudLoad()           { return budLoad; }
    public Boolean getOwnRoots()          { return ownRoots; }
    public Double  getCanopyOpenness01()  { return canopyOpenness01; }
    public Boolean getLeafPulled()        { return leafPulled; }
    public Double  getCopperSpray01()     { return copperSpray01; }
    public Double  getSulfurSpray01()     { return sulfurSpray01; }
    public Boolean getNetting()           { return netting; }
    public Boolean getGuardDog()          { return guardDog; }
    public Boolean getFalcons()           { return falcons; }
    public Boolean getCats()              { return cats; }
    public Boolean getDucks()             { return ducks; }
    public Double  getCoverCrop01()       { return coverCrop01; }
}
