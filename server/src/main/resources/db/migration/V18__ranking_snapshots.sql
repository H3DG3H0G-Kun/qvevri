-- V18__ranking_snapshots.sql
-- Flyway migration for LANE RANKING — persisted leaderboard snapshot rows.
-- Postgres production; H2 dev/test uses ddl-auto=create-drop (Flyway disabled).
--
-- Column names chosen to avoid H2/SQL reserved words:
--   rank_pos    (not `rank`  — reserved in SQL:2003 window functions)
--   sim_day     (not `day`, `year`, `level`)
--   board       (safe — not a reserved word in H2 or Postgres)
--   subject_id  (safe)
--   subject_name (safe)
--   score       (safe)
--   created_at  (safe)
-- Specifically NOT using: `rank`, `value`, `year`, `level`, `status`.

CREATE TABLE ranking_snapshots (
    id           BIGSERIAL        PRIMARY KEY,
    board        VARCHAR(16)      NOT NULL,          -- 'WEALTH' | 'VINTNER' | 'GUILD'
    subject_id   BIGINT           NOT NULL,          -- character id or guild id
    subject_name VARCHAR(255)     NOT NULL,
    score        DOUBLE PRECISION NOT NULL,
    rank_pos     INT              NOT NULL,           -- 1-based position on this board
    sim_day      BIGINT           NOT NULL,           -- world-clock absolute day at snapshot time
    created_at   BIGINT           NOT NULL            -- wall-clock epoch-ms
);

-- Primary access pattern: GET /api/ranking/snapshot by board, ordered by rank_pos
CREATE INDEX idx_ranking_snapshots_board_rank_pos
    ON ranking_snapshots (board, rank_pos);
