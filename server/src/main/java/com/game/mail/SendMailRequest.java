package com.game.mail;

/**
 * Request body for {@code POST /api/mail/send}.
 */
public class SendMailRequest {

    private Long   senderCharacterId;
    private Long   recipientCharacterId;
    private String subject;
    private String body;

    /** Optional: "GEL", "GOODS", or "CELLAR_ITEM". */
    private String attachKind;

    /** Optional: goodTypeId or cellarItemId (as String). Null for GEL. */
    private String attachRefId;

    /** Optional: quantity of GEL or GOODS; ignored for CELLAR_ITEM. */
    private Double attachAmount;

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public Long getSenderCharacterId()      { return senderCharacterId; }
    public void setSenderCharacterId(Long v){ this.senderCharacterId = v; }

    public Long getRecipientCharacterId()       { return recipientCharacterId; }
    public void setRecipientCharacterId(Long v) { this.recipientCharacterId = v; }

    public String getSubject()          { return subject; }
    public void setSubject(String v)    { this.subject = v; }

    public String getBody()             { return body; }
    public void setBody(String v)       { this.body = v; }

    public String getAttachKind()          { return attachKind; }
    public void setAttachKind(String v)    { this.attachKind = v; }

    public String getAttachRefId()         { return attachRefId; }
    public void setAttachRefId(String v)   { this.attachRefId = v; }

    public Double getAttachAmount()        { return attachAmount; }
    public void setAttachAmount(Double v)  { this.attachAmount = v; }
}
