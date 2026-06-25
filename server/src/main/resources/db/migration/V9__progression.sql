-- V9__progression.sql
-- Flyway migration for LANE PROGRESSION — per-character XP, level, and reputation.
-- Creates the progression_profiles table.
-- Postgres production; H2 dev/test uses ddl-auto=update (Flyway disabled there).

-- ─────────────────────────────────────────────────────────────────────────────
-- progression_profiles  (com.game.progression.ProgressionProfile)
-- ─────────────────────────────────────────────────────────────────────────────
-- Column naming avoids H2 reserved words:
--   - xp          (not 'value' or 'level')
--   - xp_level    (not 'level')
--   - reputation  (plain word, safe in both H2 and Postgres)
--   - updated_at  (not 'timestamp' or 'year')
CREATE TABLE progression_profiles (
    id              BIGSERIAL        PRIMARY KEY,
    character_id    BIGINT           NOT NULL REFERENCES mmo_character(id),
    xp              BIGINT           NOT NULL DEFAULT 0,
    xp_level        INT              NOT NULL DEFAULT 1,
    reputation      INT              NOT NULL DEFAULT 0,
    updated_at      BIGINT           NOT NULL
);

-- Ownership look-up: most reads filter on character_id; uniqueness enforced
-- here (one profile per character) instead of a separate table constraint.
CREATE UNIQUE INDEX idx_progression_profiles_character_id
    ON progression_profiles (character_id);
