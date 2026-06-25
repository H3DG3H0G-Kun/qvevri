-- V24__tourism_ledgers.sql
-- Flyway migration for LANE TOURISM — passive income that scales with owned buildings.
-- Postgres production; H2 dev/test uses ddl-auto=create-drop (Flyway disabled there).
--
-- Column names avoid H2/SQL reserved words:
--   character_id   (FK → mmo_character.id; not a reserved word but being explicit)
--   last_claim_day (not last_day, day, or date — those are reserved in H2/Postgres)
--   created_at     (standard epoch-ms timestamp; not reserved)
--
-- No `value`, `year`, `level`, `status`, `rank` columns used.

CREATE TABLE tourism_ledgers (
    id             BIGSERIAL        PRIMARY KEY,
    character_id   BIGINT           NOT NULL,   -- FK → mmo_character.id (no cascade needed)
    last_claim_day BIGINT           NOT NULL,   -- absolute sim-day of last income claim
    created_at     BIGINT           NOT NULL    -- wall-clock epoch-ms of row creation
);

-- Unique: exactly one ledger per character (lazy-created on first access)
CREATE UNIQUE INDEX uq_tourism_ledgers_character_id ON tourism_ledgers (character_id);
