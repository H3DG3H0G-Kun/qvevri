-- V7__land_parcels.sql
-- Flyway migration for LANE LAND — QVEVRI MMO.
-- Creates:
--   land_parcels  — player-owned estate parcels anchored to Georgian coordinates
--
-- H2 dev/test profiles DO NOT execute this file (spring.flyway.enabled=false there);
-- H2 schema is managed by ddl-auto=update/create-drop from the @Entity annotations.

-- ─────────────────────────────────────────────────────────────────────────────
-- land_parcels  (land.Parcel entity)
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE land_parcels (
    id                    BIGSERIAL        PRIMARY KEY,
    owner_character_id    BIGINT           NOT NULL REFERENCES mmo_character(id),
    name                  VARCHAR(256)     NOT NULL,
    region                VARCHAR(64)      NOT NULL,
    latitude              DOUBLE PRECISION NOT NULL,
    longitude             DOUBLE PRECISION NOT NULL,
    size_hectares         DOUBLE PRECISION NOT NULL,
    vineyard_id           BIGINT,          -- soft link; nullable; no FK to avoid cross-lane DDL coupling
    created_at            BIGINT           NOT NULL
);

-- Hot FK: parcel list is always scoped to a character
CREATE INDEX idx_land_parcel_owner_character_id ON land_parcels (owner_character_id);

-- Optional: scan by region for world-map queries
CREATE INDEX idx_land_parcel_region ON land_parcels (region);
