package com.game.estate;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request body for {@code POST /api/vineyards/{id}/action}.
 *
 * <p>All fields are required. The controller validates ownership via the
 * bearer token + {@code characterId} pair (same pattern as /manage).
 */
public class VineyardActionRequest {

    @JsonProperty("characterId")
    private Long characterId;

    /** Day within the current world-clock year on which the action takes effect (0..364). */
    @JsonProperty("dayOfYear")
    private Integer dayOfYear;

    /**
     * Action type string. Supported values:
     * {@code EMERGENCY_COPPER_SPRAY}, {@code EMERGENCY_SULFUR_SPRAY},
     * {@code EMERGENCY_NETTING}.
     */
    @JsonProperty("actionType")
    private String actionType;

    /** Numeric value for the action (spray intensity 0..1; netting 1.0=enable). */
    @JsonProperty("value")
    private Double value;

    // ── Getters ───────────────────────────────────────────────────────────────

    public Long    getCharacterId() { return characterId; }
    public Integer getDayOfYear()   { return dayOfYear; }
    public String  getActionType()  { return actionType; }
    public Double  getValue()       { return value; }
}
