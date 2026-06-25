-- V21__player_achievements.sql
-- Flyway migration for LANE ACHIEVEMENT — milestone catalog with per-character unlocks.
-- Creates the player_achievements table.
-- Postgres production; H2 dev/test uses ddl-auto (Flyway disabled there).
--
-- Column names avoid H2/SQL reserved words:
--   achievement_id  (not status/state/level/rank/value)
--   unlocked_day    (sim-day long; not year/day which could be reserved in some dialects)
--   created_at      (epoch ms; BIGINT)
--
-- The UNIQUE constraint on (character_id, achievement_id) enforces the
-- idempotent unlock guard at the database level (service also checks first).

CREATE TABLE player_achievements (
    id             BIGSERIAL        PRIMARY KEY,
    character_id   BIGINT           NOT NULL REFERENCES mmo_character(id),
    achievement_id VARCHAR(64)      NOT NULL,
    unlocked_day   BIGINT           NOT NULL,
    created_at     BIGINT           NOT NULL
);

-- Primary access pattern: load all achievements for a character
CREATE INDEX idx_player_achievements_character_id
    ON player_achievements (character_id);

-- Uniqueness: one row per (character, achievement) — enforces idempotent unlock
CREATE UNIQUE INDEX uq_player_achievements_char_ach
    ON player_achievements (character_id, achievement_id);
