package com.game.auction;

import jakarta.validation.constraints.NotNull;

/**
 * Request body for POST /api/auction/{id}/settle.
 */
public class SettleRequest {

    @NotNull
    private Long characterId;

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public Long getCharacterId()                   { return characterId; }
    public void setCharacterId(Long characterId)   { this.characterId = characterId; }
}
