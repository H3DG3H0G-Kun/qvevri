-- V5__wine_grades.sql
-- Flyway migration for LANE P (Professions) — QVEVRI MMO.
-- Creates:
--   profession_kit_claims  — tracks which characters have received their starter kit
--   wine_grades            — enologist lab-grade records
--
-- H2 dev/test profiles DO NOT execute this file (spring.flyway.enabled=false there);
-- H2 schema is managed by ddl-auto=update/create-drop from the @Entity annotations.

-- ─────────────────────────────────────────────────────────────────────────────
-- 1. profession_kit_claims  (ProfessionKitClaim entity)
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE profession_kit_claims (
    id            BIGSERIAL    PRIMARY KEY,
    character_id  BIGINT       NOT NULL UNIQUE REFERENCES mmo_character(id),
    career_type   VARCHAR(64)  NOT NULL,
    granted_at    BIGINT       NOT NULL
);

CREATE INDEX idx_prof_kit_character_id ON profession_kit_claims (character_id);

-- ─────────────────────────────────────────────────────────────────────────────
-- 2. wine_grades  (WineGrade entity)
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE wine_grades (
    id                    BIGSERIAL        PRIMARY KEY,
    cellar_item_id        BIGINT           NOT NULL REFERENCES cellar_items(id),
    grader_character_id   BIGINT           NOT NULL REFERENCES mmo_character(id),
    score                 DOUBLE PRECISION NOT NULL,
    certified             BOOLEAN          NOT NULL DEFAULT FALSE,
    created_at            BIGINT           NOT NULL
);

-- Lookup: all grades for a given cellar item
CREATE INDEX idx_wine_grade_cellar_item_id ON wine_grades (cellar_item_id);

-- Lookup: all grades issued by a specific enologist
CREATE INDEX idx_wine_grade_grader_character_id ON wine_grades (grader_character_id);
