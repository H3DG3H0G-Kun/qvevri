-- V6__winemaking.sql
-- Flyway migration for LANE WINE — winemaking depth v1 (BACKEND-DEPTH-SPEC §6).
-- Pre-assigned migration version: WINE = V6.
--
-- All columns are NULLABLE so:
--   (a) existing cellar_items rows are unaffected (all columns read as NULL).
--   (b) H2 dev/test profile never runs this (ddl-auto=create-drop handles it
--       via the @Entity annotations).  Postgres prod applies this migration.
--
-- fermentation states: PENDING, FERMENTING, READY, BOTTLED
-- ─────────────────────────────────────────────────────────────────────────────

-- ── Fermentation / aging fields on cellar_items ───────────────────────────────
--
-- fermentation_state  : NULL (no fermentation started) | 'FERMENTING' | 'READY' | 'BOTTLED'
-- vessel_good_id      : FK → owned_goods.id (the vessel OwnedGood chosen for this batch)
-- ferment_started_day : absolute world-clock day on which fermentation was started
-- ferment_ready_day   : absolute day when fermentation completes (= started + N_days)
-- aging_from_day      : absolute day from which cellar aging begins (= ferment_ready_day)
-- base_quality        : snapshot of quality at bottle creation (before aging improves it)

ALTER TABLE cellar_items
    ADD COLUMN fermentation_state VARCHAR(32)      DEFAULT NULL,
    ADD COLUMN vessel_good_id     BIGINT           DEFAULT NULL
        REFERENCES owned_goods(id) ON DELETE SET NULL,
    ADD COLUMN ferment_started_day BIGINT          DEFAULT NULL,
    ADD COLUMN ferment_ready_day   BIGINT          DEFAULT NULL,
    ADD COLUMN aging_from_day      BIGINT          DEFAULT NULL,
    ADD COLUMN base_quality        DOUBLE PRECISION DEFAULT NULL;

-- Index for "find all fermenting items for a character" (status page)
CREATE INDEX idx_cellar_item_fermentation_state ON cellar_items (fermentation_state)
    WHERE fermentation_state IS NOT NULL;
