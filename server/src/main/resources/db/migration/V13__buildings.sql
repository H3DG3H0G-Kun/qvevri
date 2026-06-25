-- V13__buildings.sql
-- Flyway migration for LANE BUILDINGS — estate buildings that grant production bonuses.
-- Creates the buildings table.
-- Postgres production; H2 dev/test uses ddl-auto=update (Flyway disabled there).
--
-- Column names avoid H2/SQL reserved words:
--   building_type_id  (not type)
--   building_level    (not level)
--   built_day         (not day or year)
--   owner_character_id (not character or owner)

CREATE TABLE buildings (
    id                    BIGSERIAL     PRIMARY KEY,
    owner_character_id    BIGINT        NOT NULL REFERENCES mmo_character(id),
    parcel_id             BIGINT        REFERENCES land_parcels(id),
    building_type_id      VARCHAR(64)   NOT NULL,
    building_level        INT           NOT NULL DEFAULT 1,
    built_day             BIGINT        NOT NULL,
    created_at            BIGINT        NOT NULL
);

-- Primary access pattern: load all buildings for a character
CREATE INDEX idx_buildings_owner_character_id ON buildings (owner_character_id);
