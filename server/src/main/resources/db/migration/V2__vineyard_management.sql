-- V2__vineyard_management.sql
-- Adds per-vineyard management lever columns to mmo_vineyard (MANAGE-SPEC §2 / §5).
-- Defaults match the previously hardcoded constants in VineyardReplayService,
-- so all existing rows get the same behaviour as before (no replay output changes).
-- H2 dev/test profiles DO NOT execute this file (spring.flyway.enabled=false there);
-- H2 adds the columns automatically via ddl-auto=update.

ALTER TABLE mmo_vineyard
    ADD COLUMN IF NOT EXISTS own_roots          BOOLEAN          NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS canopy_openness01  DOUBLE PRECISION NOT NULL DEFAULT 0.40,
    ADD COLUMN IF NOT EXISTS leaf_pulled        BOOLEAN          NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS copper_spray01     DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    ADD COLUMN IF NOT EXISTS sulfur_spray01     DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    ADD COLUMN IF NOT EXISTS netting            BOOLEAN          NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS guard_dog          BOOLEAN          NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS falcons            BOOLEAN          NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS cats               BOOLEAN          NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS ducks              BOOLEAN          NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS cover_crop01       DOUBLE PRECISION NOT NULL DEFAULT 0.0;
