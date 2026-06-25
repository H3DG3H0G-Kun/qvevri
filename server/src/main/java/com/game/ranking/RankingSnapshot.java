package com.game.ranking;

import jakarta.persistence.*;

/**
 * Persisted leaderboard snapshot row.
 *
 * <p>Table: {@code ranking_snapshots}. Each POST /api/ranking/snapshot call
 * writes one row per entry on the requested board.
 *
 * <p>Column names chosen to avoid H2 reserved words:
 * {@code rank_pos} (not {@code rank}), {@code sim_day} (not {@code day}),
 * {@code board_name} mapped to column {@code board} (safe in H2),
 * {@code subject_id}, {@code subject_name}, {@code score}, {@code created_at}.
 * Specifically NOT using: {@code value}, {@code year}, {@code level},
 * {@code status}, {@code rank} (reserved in SQL:2003).
 */
@Entity
@Table(name = "ranking_snapshots",
       indexes = @Index(name = "idx_ranking_snapshots_board_rank_pos",
                        columnList = "board, rank_pos"))
public class RankingSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Board identifier: "WEALTH", "VINTNER", or "GUILD". */
    @Column(name = "board", nullable = false, length = 16)
    private String board;

    /** Character id (WEALTH/VINTNER) or guild id (GUILD). */
    @Column(name = "subject_id", nullable = false)
    private long subjectId;

    /** Character name or guild name at the time of the snapshot. */
    @Column(name = "subject_name", nullable = false)
    private String subjectName;

    /** Score value used for ordering (wallet GEL, max quality, or member count). */
    @Column(name = "score", nullable = false)
    private double score;

    /** 1-based rank position within this board snapshot. */
    @Column(name = "rank_pos", nullable = false)
    private int rankPos;

    /** World-clock absolute day at the time of the snapshot. */
    @Column(name = "sim_day", nullable = false)
    private long simDay;

    /** Wall-clock epoch-ms timestamp of when this snapshot was persisted. */
    @Column(name = "created_at", nullable = false)
    private long createdAt;

    /** Required by JPA. */
    protected RankingSnapshot() {}

    public RankingSnapshot(String board, long subjectId, String subjectName,
                           double score, int rankPos, long simDay, long createdAt) {
        this.board       = board;
        this.subjectId   = subjectId;
        this.subjectName = subjectName;
        this.score       = score;
        this.rankPos     = rankPos;
        this.simDay      = simDay;
        this.createdAt   = createdAt;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public Long   getId()          { return id; }
    public String getBoard()       { return board; }
    public long   getSubjectId()   { return subjectId; }
    public String getSubjectName() { return subjectName; }
    public double getScore()       { return score; }
    public int    getRankPos()     { return rankPos; }
    public long   getSimDay()      { return simDay; }
    public long   getCreatedAt()   { return createdAt; }
}
