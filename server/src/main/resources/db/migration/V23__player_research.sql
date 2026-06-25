-- V23__player_research.sql
-- Flyway migration for LANE RESEARCH — per-character tech-tree progress.
-- Postgres production; H2 dev/test uses ddl-auto=update (Flyway disabled there).
--
-- Column names avoid H2/SQL reserved words:
--   research_status  (not status, state, level, rank)
--   node_id          (not value — "value" is reserved in H2)
--   start_day        (not day, date — these are reserved in some dialects)
--   ready_day        (safe)
--   created_at       (epoch-ms wall-clock timestamp)
--
-- Unique constraint (character_id, node_id) enforces one row per character per node.

CREATE TABLE player_research (
    id               BIGSERIAL        PRIMARY KEY,
    character_id     BIGINT           NOT NULL,            -- FK → mmo_character.id (no cascade)
    node_id          VARCHAR(100)     NOT NULL,            -- stable catalog node id
    research_status  VARCHAR(32)      NOT NULL DEFAULT 'RESEARCHING', -- RESEARCHING | COMPLETE
    start_day        BIGINT           NOT NULL,            -- absolute sim-day research started
    ready_day        BIGINT           NOT NULL,            -- absolute sim-day research completes
    created_at       BIGINT           NOT NULL             -- epoch-ms wall-clock
);

-- Unique constraint: one row per (character, node) — cannot start the same node twice
CREATE UNIQUE INDEX uq_player_research_char_node
    ON player_research (character_id, node_id);

-- Fast lookup of all research rows for a character
CREATE INDEX idx_player_research_character_id
    ON player_research (character_id);
