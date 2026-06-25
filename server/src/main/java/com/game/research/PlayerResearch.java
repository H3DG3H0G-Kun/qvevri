package com.game.research;

import jakarta.persistence.*;

/**
 * Persistent record of a character's progress on a single research node.
 *
 * <p>Table: {@code player_research}. Migration: V23__player_research.sql.
 *
 * <p>Lifecycle:
 * <ul>
 *   <li>RESEARCHING — research is in progress; {@code readyDay} is in the future.</li>
 *   <li>COMPLETE    — research finished (lazily flipped on read when currentDay &ge; readyDay).</li>
 * </ul>
 *
 * <p>Column-name notes (H2 reserved-word avoidance):
 * <ul>
 *   <li>{@code research_status} — avoids the reserved word {@code status}.</li>
 *   <li>{@code node_id}         — plain snake_case; fine in H2.</li>
 *   <li>{@code start_day}       — fine in H2.</li>
 *   <li>{@code ready_day}       — fine in H2.</li>
 *   <li>{@code created_at}      — epoch-ms timestamp.</li>
 * </ul>
 */
@Entity
@Table(name = "player_research",
       uniqueConstraints = {
           @UniqueConstraint(name = "uq_player_research_char_node",
                             columnNames = {"character_id", "node_id"})
       },
       indexes = {
           @Index(name = "idx_player_research_character_id", columnList = "character_id")
       })
public class PlayerResearch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** FK → mmo_character.id — the owning character. */
    @Column(name = "character_id", nullable = false)
    private Long characterId;

    /** Stable reference to a {@link ResearchNode} in {@link ResearchCatalog}. */
    @Column(name = "node_id", nullable = false)
    private String nodeId;

    /**
     * Lifecycle state.
     * Values: "RESEARCHING" | "COMPLETE".
     * Column {@code research_status} avoids H2 reserved word {@code status}.
     */
    @Column(name = "research_status", nullable = false)
    private String researchStatus;

    /** Absolute sim-day when research was started. */
    @Column(name = "start_day", nullable = false)
    private long startDay;

    /** Absolute sim-day when research completes (startDay + durationDays). */
    @Column(name = "ready_day", nullable = false)
    private long readyDay;

    /** Epoch-ms wall-clock timestamp of row creation. */
    @Column(name = "created_at", nullable = false)
    private long createdAt;

    // ── Constructors ──────────────────────────────────────────────────────────

    /** Required by JPA. */
    protected PlayerResearch() {}

    /**
     * Creates a new RESEARCHING row.
     *
     * @param characterId owning character
     * @param nodeId      stable catalog node id
     * @param startDay    current absolute sim-day
     * @param readyDay    sim-day when research will be complete (startDay + durationDays)
     */
    public PlayerResearch(Long characterId, String nodeId, long startDay, long readyDay) {
        this.characterId    = characterId;
        this.nodeId         = nodeId;
        this.researchStatus = ResearchStatus.RESEARCHING;
        this.startDay       = startDay;
        this.readyDay       = readyDay;
        this.createdAt      = System.currentTimeMillis();
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public Long   getId()             { return id; }
    public Long   getCharacterId()    { return characterId; }
    public String getNodeId()         { return nodeId; }
    public String getResearchStatus() { return researchStatus; }
    public long   getStartDay()       { return startDay; }
    public long   getReadyDay()       { return readyDay; }
    public long   getCreatedAt()      { return createdAt; }

    // ── Setters ───────────────────────────────────────────────────────────────

    public void setResearchStatus(String researchStatus) {
        this.researchStatus = researchStatus;
    }
}
