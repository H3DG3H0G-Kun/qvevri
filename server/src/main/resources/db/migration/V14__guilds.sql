-- V14__guilds.sql
-- Flyway migration for LANE GUILDS — player wine-house cooperatives.
-- Creates the guilds and guild_members tables.
-- Postgres production; H2 dev/test uses ddl-auto=update (Flyway disabled there).
--
-- Column names avoid H2/SQL reserved words:
--   guild_name           (not name — some H2 versions treat 'name' oddly in indexes)
--   guild_role           (not role — reserved in SQL:2003 and some H2 builds)
--   treasury_gel         (not balance — reserved in some H2 versions)
--   joined_at            (not date/timestamp — descriptive, safe)
--   founder_character_id (not character — reserved keyword in SQL)

CREATE TABLE guilds (
    id                    BIGSERIAL     PRIMARY KEY,
    guild_name            VARCHAR(120)  NOT NULL,
    founder_character_id  BIGINT        NOT NULL REFERENCES mmo_character(id),
    treasury_gel          DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    created_at            BIGINT        NOT NULL
);

-- Enforce unique guild names
CREATE UNIQUE INDEX idx_guilds_name ON guilds (guild_name);

CREATE TABLE guild_members (
    id            BIGSERIAL   PRIMARY KEY,
    guild_id      BIGINT      NOT NULL REFERENCES guilds(id),
    character_id  BIGINT      NOT NULL REFERENCES mmo_character(id),
    guild_role    VARCHAR(16) NOT NULL,
    joined_at     BIGINT      NOT NULL
);

-- A character may belong to at most one guild
CREATE UNIQUE INDEX idx_guild_members_character_id ON guild_members (character_id);

-- Primary access pattern: load all members of a guild
CREATE INDEX idx_guild_members_guild_id ON guild_members (guild_id);
