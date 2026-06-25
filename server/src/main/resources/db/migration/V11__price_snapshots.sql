-- V11__price_snapshots.sql
-- Flyway migration for LANE ECONOMY — dynamic pricing snapshots.
-- Creates the price_snapshots table.
-- Postgres production; H2 dev/test uses ddl-auto=update (Flyway disabled there).
--
-- Column names avoid H2/SQL reserved words:
--   item_type    (not type)
--   region       (safe — not reserved in Postgres or H2)
--   price        (safe)
--   supply_count (not count)
--   sim_day      (not day)
--   created_at   (epoch ms; BIGINT)

CREATE TABLE price_snapshots (
    id            BIGSERIAL    PRIMARY KEY,
    item_type     VARCHAR(32)  NOT NULL,
    region        VARCHAR(32)  NOT NULL,
    price         DOUBLE PRECISION NOT NULL,
    supply_count  BIGINT       NOT NULL,
    sim_day       BIGINT       NOT NULL,
    created_at    BIGINT       NOT NULL
);

-- Primary query pattern: fetch history for a given item type + region
CREATE INDEX idx_price_snapshots_item_region ON price_snapshots (item_type, region);
