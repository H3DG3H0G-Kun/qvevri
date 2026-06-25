-- V4__vineyard_multiseason.sql
-- Multi-season vine establishment + per-day tending actions (GOODS-ECON-SPEC LANE-M).
-- H2 dev/test profiles DO NOT execute this file (spring.flyway.enabled=false there);
-- H2 schema is managed automatically via spring.jpa.hibernate.ddl-auto=update.
--
-- Design notes:
--   planted_year is NULLABLE: existing rows get NULL, which VineyardReplayService
--   treats as "fully mature" (multiplier = 1.0 exactly) — no output change for old rows.
--
--   vineyard_actions is a new table; an empty table produces zero effect during
--   replay (the action loop never fires) so baseline behaviour is preserved.

-- ─────────────────────────────────────────────────────────────────────────────
-- mmo_vineyard: add planted_year column
-- ─────────────────────────────────────────────────────────────────────────────
ALTER TABLE mmo_vineyard
    ADD COLUMN IF NOT EXISTS planted_year INTEGER DEFAULT NULL;

-- ─────────────────────────────────────────────────────────────────────────────
-- vineyard_actions  (com.game.estate.VineyardAction)
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS vineyard_actions (
    id           BIGSERIAL        PRIMARY KEY,
    vineyard_id  BIGINT           NOT NULL REFERENCES mmo_vineyard(id),
    season_year  INTEGER          NOT NULL,
    day_of_year  INTEGER          NOT NULL,
    action_type  VARCHAR(64)      NOT NULL,
    action_value DOUBLE PRECISION NOT NULL
);

-- Hot index: replay loads all actions for (vineyard, year) in day order
CREATE INDEX IF NOT EXISTS idx_vineyard_actions_vineyard_year
    ON vineyard_actions (vineyard_id, season_year);
