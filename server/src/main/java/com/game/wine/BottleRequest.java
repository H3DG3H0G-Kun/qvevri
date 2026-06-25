package com.game.wine;

/**
 * Request body for {@code POST /api/wine/ferment/bottle}.
 * Locks a READY CellarItem's quality and transitions it to BOTTLED state.
 */
public class BottleRequest {

    /** The CellarItem to bottle. Must be in READY state. */
    private Long cellarItemId;

    /** The character performing the action (ownership check). */
    private Long characterId;

    public Long getCellarItemId() { return cellarItemId; }
    public Long getCharacterId()  { return characterId; }

    public void setCellarItemId(Long id) { this.cellarItemId = id; }
    public void setCharacterId(Long id)  { this.characterId = id; }
}
