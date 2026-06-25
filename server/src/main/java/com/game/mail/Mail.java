package com.game.mail;

import jakarta.persistence.*;

/**
 * Persistent mailbox message.
 *
 * <p>Table name: {@code mailbox} — avoids any reserved-word clash.
 * Column names are H2-safe: no {@code to}, {@code from}, {@code read}, {@code value},
 * {@code year}, {@code status}.
 *
 * <p>Attachments:
 * <ul>
 *   <li>GEL — {@code attach_kind="GEL"}, {@code attach_amount > 0}, {@code attach_ref_id} null.</li>
 *   <li>GOODS — {@code attach_kind="GOODS"}, {@code attach_ref_id=goodTypeId},
 *       {@code attach_amount=qty}.</li>
 *   <li>CELLAR_ITEM — {@code attach_kind="CELLAR_ITEM"}, {@code attach_ref_id=cellarItemId},
 *       {@code attach_amount=0}.</li>
 * </ul>
 * When there is no attachment all three nullable columns are null and
 * {@code attach_amount=0}.
 */
@Entity
@Table(name = "mailbox", indexes = {
        @Index(name = "idx_mailbox_recipient_character_id",
               columnList = "recipient_character_id")
})
public class Mail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** FK → mmo_character.id — who receives this message. */
    @Column(name = "recipient_character_id", nullable = false)
    private Long recipientCharacterId;

    /** FK → mmo_character.id — null for system-generated messages. */
    @Column(name = "sender_character_id")
    private Long senderCharacterId;

    @Column(nullable = false)
    private String subject;

    @Column(nullable = false, length = 4096)
    private String body;

    /** "GEL", "GOODS", "CELLAR_ITEM", or null (no attachment). */
    @Column(name = "attach_kind")
    private String attachKind;

    /**
     * goodTypeId (GOODS) or cellarItemId as String (CELLAR_ITEM).
     * Null for GEL or no attachment.
     */
    @Column(name = "attach_ref_id")
    private String attachRefId;

    /** Quantity of GEL or GOODS; 0 for CELLAR_ITEM or no attachment. */
    @Column(name = "attach_amount", nullable = false)
    private double attachAmount = 0.0;

    /** Whether the recipient has opened/read this mail. */
    @Column(name = "is_read", nullable = false)
    private boolean isRead = false;

    /** Whether the recipient has claimed the attachment. */
    @Column(name = "is_claimed", nullable = false)
    private boolean isClaimed = false;

    /** Epoch-ms creation timestamp. */
    @Column(name = "created_at", nullable = false)
    private long createdAt;

    // ── Constructors ──────────────────────────────────────────────────────────

    protected Mail() {}

    public Mail(Long recipientCharacterId,
                Long senderCharacterId,
                String subject,
                String body,
                String attachKind,
                String attachRefId,
                double attachAmount) {
        this.recipientCharacterId = recipientCharacterId;
        this.senderCharacterId    = senderCharacterId;
        this.subject              = subject;
        this.body                 = body;
        this.attachKind           = attachKind;
        this.attachRefId          = attachRefId;
        this.attachAmount         = attachAmount;
        this.isRead               = false;
        this.isClaimed            = false;
        this.createdAt            = System.currentTimeMillis();
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public Long getId()                    { return id; }
    public Long getRecipientCharacterId()  { return recipientCharacterId; }
    public Long getSenderCharacterId()     { return senderCharacterId; }
    public String getSubject()             { return subject; }
    public String getBody()               { return body; }
    public String getAttachKind()         { return attachKind; }
    public String getAttachRefId()        { return attachRefId; }
    public double getAttachAmount()       { return attachAmount; }
    public boolean isRead()               { return isRead; }
    public boolean isClaimed()            { return isClaimed; }
    public long getCreatedAt()            { return createdAt; }

    // ── Setters ───────────────────────────────────────────────────────────────

    public void setRead(boolean read)      { this.isRead = read; }
    public void setClaimed(boolean claimed){ this.isClaimed = claimed; }
    public void setCreatedAt(long t)       { this.createdAt = t; }
}
