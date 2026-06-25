-- V19__festival_participations.sql
-- Flyway migration for LANE FESTIVAL — world seasonal events with participation rewards.
-- Creates the festival_participations table.
-- Postgres production; H2 dev/test uses ddl-auto (Flyway disabled there).
--
-- Column names avoid H2/SQL reserved words:
--   season_year  (not year)
--   festival_id  (not status/state/level)
--   claimed      (boolean; safe in both H2 and Postgres)
--   created_at   (epoch ms; BIGINT)

CREATE TABLE festival_participations (
    id             BIGSERIAL        PRIMARY KEY,
    character_id   BIGINT           NOT NULL REFERENCES mmo_character(id),
    festival_id    VARCHAR(64)      NOT NULL,
    season_year    INT              NOT NULL,
    claimed        BOOLEAN          NOT NULL DEFAULT TRUE,
    created_at     BIGINT           NOT NULL
);

-- Composite index for the primary access pattern:
-- look up participation by (character, festival, year) for the idempotency guard.
CREATE INDEX idx_festival_participations_lookup
    ON festival_participations (character_id, festival_id, season_year);
