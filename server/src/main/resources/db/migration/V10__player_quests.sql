-- V10__player_quests.sql
-- Flyway migration for LANE QUEST — NPC-given quests with rewards.
-- Creates the player_quests table.
-- Postgres production; H2 dev/test uses ddl-auto=update (Flyway disabled there).
--
-- Column names avoid H2/SQL reserved words:
--   quest_status  (not status, state, or level)
--   started_at    (epoch ms; BIGINT)
--   completed_at  (nullable epoch ms; BIGINT)

CREATE TABLE player_quests (
    id             BIGSERIAL        PRIMARY KEY,
    character_id   BIGINT           NOT NULL REFERENCES mmo_character(id),
    quest_id       VARCHAR(64)      NOT NULL,
    quest_status   VARCHAR(32)      NOT NULL DEFAULT 'ACTIVE',
    progress       INT              NOT NULL DEFAULT 0,
    started_at     BIGINT           NOT NULL,
    completed_at   BIGINT
);

-- Primary access pattern: load all quests for a character
CREATE INDEX idx_player_quests_character_id ON player_quests (character_id);

-- Uniqueness: one row per (character, quest) combination enforced at the service
-- layer. An explicit unique index can be added in a later migration once the
-- OFFERED pre-accept flow is finalised; for now the service guard is sufficient.
