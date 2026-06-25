package com.game.quest;

import jakarta.persistence.*;

/**
 * Persistent record of a single character's progress on a quest.
 *
 * <p>Table: {@code player_quests}. Migration: V10__player_quests.sql.
 *
 * <p>Lifecycle:
 * <ul>
 *   <li>ACTIVE     — accepted by the player; in progress.</li>
 *   <li>COMPLETED  — reward already granted; idempotent re-complete is blocked.</li>
 *   <li>OFFERED    — reserved for future NPC-offer pre-accept state; not yet used.</li>
 * </ul>
 *
 * <p>Column names avoid H2 reserved words: {@code quest_status} (not {@code status}),
 * {@code started_at} / {@code completed_at} epoch-ms longs.
 */
@Entity
@Table(name = "player_quests",
       indexes = {
           @Index(name = "idx_player_quests_character_id", columnList = "character_id")
       })
public class PlayerQuest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** FK → mmo_character.id — the owning character. */
    @Column(name = "character_id", nullable = false)
    private Long characterId;

    /** Stable reference to a {@link QuestDefinition} in {@link QuestCatalog}. */
    @Column(name = "quest_id", nullable = false)
    private String questId;

    /**
     * Lifecycle state string.
     * Values: OFFERED / ACTIVE / COMPLETED.
     * Column name {@code quest_status} avoids H2 reserved word {@code status}.
     */
    @Column(name = "quest_status", nullable = false)
    private String questStatus;

    /**
     * How many objective units have been completed so far.
     * In v1 this starts at 0 and is set to the quest's objectiveCount on complete.
     */
    @Column(nullable = false)
    private int progress;

    /** Epoch-ms timestamp when the quest was accepted (transitioned to ACTIVE). */
    @Column(name = "started_at", nullable = false)
    private long startedAt;

    /**
     * Epoch-ms timestamp when the quest was completed.
     * Null until the quest reaches COMPLETED state.
     */
    @Column(name = "completed_at")
    private Long completedAt;

    // ── Constructors ──────────────────────────────────────────────────────────

    /** Required by JPA. */
    protected PlayerQuest() {}

    /**
     * Creates a new ACTIVE PlayerQuest for the given character and quest.
     *
     * @param characterId the owning character
     * @param questId     stable catalog quest id
     */
    public PlayerQuest(Long characterId, String questId) {
        this.characterId = characterId;
        this.questId     = questId;
        this.questStatus = QuestStatus.ACTIVE;
        this.progress    = 0;
        this.startedAt   = System.currentTimeMillis();
        this.completedAt = null;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public Long   getId()          { return id; }
    public Long   getCharacterId() { return characterId; }
    public String getQuestId()     { return questId; }
    public String getQuestStatus() { return questStatus; }
    public int    getProgress()    { return progress; }
    public long   getStartedAt()   { return startedAt; }
    public Long   getCompletedAt() { return completedAt; }

    // ── Setters ───────────────────────────────────────────────────────────────

    public void setQuestStatus(String questStatus) { this.questStatus = questStatus; }
    public void setProgress(int progress)           { this.progress = progress; }
    public void setCompletedAt(Long completedAt)    { this.completedAt = completedAt; }
}
