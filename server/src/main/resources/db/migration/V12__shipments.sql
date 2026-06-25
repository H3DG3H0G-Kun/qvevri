-- V12__shipments.sql
-- Flyway migration for LANE LOGISTICS — region-to-region shipment tracking.
-- Creates the shipments table.
-- Postgres production; H2 dev/test uses ddl-auto=update (Flyway disabled there).
--
-- Column names avoid H2/SQL reserved words:
--   ship_status   (not status, state)
--   from_region   (not from — reserved in SQL)
--   to_region     (not to)
--   depart_day    (safe in both dialects)
--   arrive_day    (safe in both dialects)
--   ref_id        (not reference — avoid reserved ambiguity)

CREATE TABLE shipments (
    id                      BIGSERIAL    PRIMARY KEY,
    owner_character_id      BIGINT       NOT NULL REFERENCES mmo_character(id),
    recipient_character_id  BIGINT                REFERENCES mmo_character(id),
    kind                    VARCHAR(32)  NOT NULL,
    ref_id                  VARCHAR(255) NOT NULL,
    quantity                DOUBLE PRECISION NOT NULL,
    from_region             VARCHAR(64)  NOT NULL,
    to_region               VARCHAR(64)  NOT NULL,
    depart_day              BIGINT       NOT NULL,
    arrive_day              BIGINT       NOT NULL,
    ship_status             VARCHAR(32)  NOT NULL DEFAULT 'IN_TRANSIT',
    created_at              BIGINT       NOT NULL
);

-- Primary access pattern: load all shipments for a character
CREATE INDEX idx_shipments_owner_character_id ON shipments (owner_character_id);
