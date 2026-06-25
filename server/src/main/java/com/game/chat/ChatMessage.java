package com.game.chat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Persistent chat message entity.
 *
 * <p>Table: {@code chat_messages}. Channels are plain strings:
 * <ul>
 *   <li>{@code "GLOBAL"} — server-wide public channel</li>
 *   <li>{@code "REGION:{REGION}"} — e.g. {@code "REGION:KAKHETI"}</li>
 *   <li>{@code "GUILD:{guildId}"} — e.g. {@code "GUILD:7"}</li>
 *   <li>{@code "DM:{minId}:{maxId}"} — sorted ascending, e.g. {@code "DM:3:9"}</li>
 * </ul>
 *
 * <p>Column names avoid H2/SQL reserved words:
 * <ul>
 *   <li>{@code body_text} — not {@code body} or {@code message} (potentially reserved)</li>
 *   <li>{@code channel}   — safe in both H2 and Postgres</li>
 *   <li>{@code sender_character_id}, {@code sender_name}, {@code created_at} — safe</li>
 * </ul>
 */
@Entity
@Table(name = "chat_messages")
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Channel key — see class-level Javadoc for format. */
    @Column(name = "channel", nullable = false)
    private String channel;

    /** FK → mmo_character.id (not enforced as a JPA relation to avoid cross-package deps). */
    @Column(name = "sender_character_id", nullable = false)
    private Long senderCharacterId;

    /** Display name of the sender, snapshotted at send time. */
    @Column(name = "sender_name", nullable = false)
    private String senderName;

    /**
     * Message body text. Maximum 500 characters — enforced at the service layer,
     * not at the DB column level, so tests can inspect the exact error code.
     */
    @Column(name = "body_text", nullable = false, length = 500)
    private String bodyText;

    /** Wall-clock epoch-ms timestamp when the message was persisted. */
    @Column(name = "created_at", nullable = false)
    private long createdAt;

    /** Required by JPA. */
    protected ChatMessage() {}

    public ChatMessage(String channel, Long senderCharacterId, String senderName,
                       String bodyText, long createdAt) {
        this.channel           = channel;
        this.senderCharacterId = senderCharacterId;
        this.senderName        = senderName;
        this.bodyText          = bodyText;
        this.createdAt         = createdAt;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public Long   getId()                { return id; }
    public String getChannel()           { return channel; }
    public Long   getSenderCharacterId() { return senderCharacterId; }
    public String getSenderName()        { return senderName; }
    public String getBodyText()          { return bodyText; }
    public long   getCreatedAt()         { return createdAt; }
}
