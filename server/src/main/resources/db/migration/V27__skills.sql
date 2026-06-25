-- V27__skills.sql — per-character talent tree (SKILL lane).
-- H2 dev/test use ddl-auto; this Flyway script is the Postgres prod schema.

CREATE TABLE skill_profiles (
    id            BIGSERIAL    PRIMARY KEY,
    character_id  BIGINT       NOT NULL REFERENCES mmo_character(id),
    total_points  INT          NOT NULL DEFAULT 5,
    spent_points  INT          NOT NULL DEFAULT 0,
    created_at    BIGINT       NOT NULL
);
CREATE UNIQUE INDEX uq_skill_profiles_character_id ON skill_profiles (character_id);

CREATE TABLE learned_skills (
    id            BIGSERIAL    PRIMARY KEY,
    character_id  BIGINT       NOT NULL REFERENCES mmo_character(id),
    skill_id      VARCHAR(64)  NOT NULL,
    learned_at    BIGINT       NOT NULL
);
CREATE UNIQUE INDEX uq_learned_skills_char_skill ON learned_skills (character_id, skill_id);
CREATE INDEX idx_learned_skills_character_id ON learned_skills (character_id);
