package com.game.mail;

/**
 * Generic request body containing a single {@code characterId} field.
 * Used by read, claim, and delete endpoints.
 */
public class MailCharacterRequest {

    private Long characterId;

    public Long getCharacterId()          { return characterId; }
    public void setCharacterId(Long v)    { this.characterId = v; }
}
