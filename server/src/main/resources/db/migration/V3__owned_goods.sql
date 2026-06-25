-- V3__owned_goods.sql
-- Flyway migration for the goods/equipment/bazaar system (LANE G).
-- Creates the owned_goods table which tracks per-character good inventories.
-- H2 dev/test profiles DO NOT execute this file (spring.flyway.enabled=false there);
-- H2 schema is managed automatically via spring.jpa.hibernate.ddl-auto=update.

-- ─────────────────────────────────────────────────────────────────────────────
-- owned_goods  (com.game.goods.OwnedGood)
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE owned_goods (
    id             BIGSERIAL        PRIMARY KEY,
    character_id   BIGINT           NOT NULL REFERENCES mmo_character(id),
    good_type_id   VARCHAR(128)     NOT NULL,
    quantity       DOUBLE PRECISION NOT NULL,
    condition01    DOUBLE PRECISION NOT NULL DEFAULT 1.0,
    acquired_at    BIGINT           NOT NULL
);

-- Hot FK: inventory reads are always scoped to a character
CREATE INDEX idx_owned_goods_character_id ON owned_goods (character_id);

-- Stacking lookup: find existing row for (character, good) efficiently
CREATE INDEX idx_owned_goods_character_good ON owned_goods (character_id, good_type_id);
