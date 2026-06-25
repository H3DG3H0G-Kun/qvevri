-- V28__prestige_profiles.sql
-- Flyway migration for LANE PRESTIGE — per-character prestige points and title rank.
-- Postgres production; H2 dev/test uses ddl-auto=update (Flyway disabled there).
--
-- Column naming avoids H2/SQL reserved words:
--   title_rank  (not `rank`, `title`, or `level`)
--   prestige    (safe in H2/Postgres)
--   updated_at  (epoch-ms wall clock; safe)
--
-- Title ladder (static; not stored here — computed by TitleLadder.java):
--   GLEKHI    →     0 prestige
--   MEVENAKHE →    50 prestige
--   MEURNE    →   200 prestige
--   AZNAURI   →   600 prestige
--   TAVADI    → 1,500 prestige
--
-- Lazy semantics: rows are created on first GET /api/prestige/{characterId}.
-- Prestige never decreases; titleRank is kept in sync by PrestigeService.
--
-- No FK constraint on character_id (avoids cascade issues with mmo_character).

CREATE TABLE prestige_profiles (
    id           BIGSERIAL    PRIMARY KEY,
    character_id BIGINT       NOT NULL,                    -- FK → mmo_character.id (no cascade)
    prestige     BIGINT       NOT NULL DEFAULT 0,          -- total prestige accumulated
    title_rank   VARCHAR(32)  NOT NULL DEFAULT 'GLEKHI',  -- current title name from ladder
    updated_at   BIGINT       NOT NULL                     -- wall-clock epoch-ms of last change
);

-- Primary access: look up a character's prestige row (unique — one row per character)
CREATE UNIQUE INDEX uq_prestige_profiles_character_id
    ON prestige_profiles (character_id);
