-- V1__baseline.sql
-- Flyway baseline migration for QVEVRI (Postgres).
-- DDL derived from the @Entity classes as of 2026-06-16.
-- H2 dev/test profiles DO NOT execute this file (spring.flyway.enabled=false there).

-- ─────────────────────────────────────────────────────────────────────────────
-- 1. player_state  (persistence.PlayerStateEntity — composite PK player_id+session_id)
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE player_state (
    player_id   VARCHAR(64)  NOT NULL,
    session_id  VARCHAR(64)  NOT NULL,
    display_name VARCHAR(128),
    x           DOUBLE PRECISION NOT NULL DEFAULT 0,
    y           DOUBLE PRECISION NOT NULL DEFAULT 0,
    z           DOUBLE PRECISION NOT NULL DEFAULT 0,
    rotation_y  DOUBLE PRECISION NOT NULL DEFAULT 0,
    updated_at  BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (player_id, session_id)
);

-- ─────────────────────────────────────────────────────────────────────────────
-- 2. mmo_account  (account.Account)
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE mmo_account (
    id            BIGSERIAL    PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,
    username      VARCHAR(128) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    created_at    BIGINT       NOT NULL
);

-- ─────────────────────────────────────────────────────────────────────────────
-- 3. mmo_character  (character.Character)
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE mmo_character (
    id           BIGSERIAL    PRIMARY KEY,
    account_id   BIGINT       NOT NULL REFERENCES mmo_account(id),
    name         VARCHAR(128) NOT NULL,
    career_type  VARCHAR(64)  NOT NULL,
    home_region  VARCHAR(64)  NOT NULL,
    rank         VARCHAR(64)  NOT NULL DEFAULT 'GLEKHI',
    wallet_gel   DOUBLE PRECISION NOT NULL DEFAULT 100.0,
    created_at   BIGINT       NOT NULL
);

-- Hot FK: most character lookups filter by account
CREATE INDEX idx_mmo_character_account_id ON mmo_character (account_id);

-- ─────────────────────────────────────────────────────────────────────────────
-- 4. cellar_items  (market.CellarItem)
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE cellar_items (
    id              BIGSERIAL    PRIMARY KEY,
    character_id    BIGINT       NOT NULL REFERENCES mmo_character(id),
    item_type       VARCHAR(64)  NOT NULL,
    quantity        DOUBLE PRECISION NOT NULL,
    quality         DOUBLE PRECISION NOT NULL,
    vintage_year    INTEGER      NOT NULL,
    style           VARCHAR(32),
    appellation_ok  BOOLEAN      NOT NULL DEFAULT FALSE,
    label           VARCHAR(512),
    escrowed        BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      BIGINT       NOT NULL
);

-- Hot FK: cellar reads are always scoped to a character
CREATE INDEX idx_cellar_item_character_id ON cellar_items (character_id);

-- ─────────────────────────────────────────────────────────────────────────────
-- 5. market_listings  (market.MarketListing)
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE market_listings (
    id                    BIGSERIAL    PRIMARY KEY,
    seller_character_id   BIGINT       NOT NULL REFERENCES mmo_character(id),
    cellar_item_id        BIGINT       NOT NULL REFERENCES cellar_items(id),
    ask_price             DOUBLE PRECISION NOT NULL,
    status                VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    created_at            BIGINT       NOT NULL
);

-- GET /api/market filters on status = ACTIVE
CREATE INDEX idx_market_listing_status ON market_listings (status);

-- Seller look-ups (e.g. "my listings")
CREATE INDEX idx_market_listing_seller ON market_listings (seller_character_id);

-- ─────────────────────────────────────────────────────────────────────────────
-- 6. trade_records  (market.TradeRecord)
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE trade_records (
    id                   BIGSERIAL    PRIMARY KEY,
    buyer_character_id   BIGINT       NOT NULL REFERENCES mmo_character(id),
    seller_character_id  BIGINT       NOT NULL REFERENCES mmo_character(id),
    cellar_item_id       BIGINT       NOT NULL REFERENCES cellar_items(id),
    price                DOUBLE PRECISION NOT NULL,
    created_at           BIGINT       NOT NULL
);

-- ─────────────────────────────────────────────────────────────────────────────
-- 7. mmo_vineyard  (estate.Vineyard)
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE mmo_vineyard (
    id                    BIGSERIAL    PRIMARY KEY,
    owner_character_id    BIGINT       NOT NULL REFERENCES mmo_character(id),
    region                VARCHAR(64)  NOT NULL,
    variety               VARCHAR(64)  NOT NULL,
    seed                  BIGINT       NOT NULL,
    bud_load              INTEGER      NOT NULL DEFAULT 12,
    status                VARCHAR(32)  NOT NULL DEFAULT 'GROWING',
    last_harvested_year   INTEGER      NOT NULL DEFAULT 0,
    created_at            BIGINT       NOT NULL
);

-- Hot FK: vineyard list is always scoped to a character
CREATE INDEX idx_mmo_vineyard_owner_character_id ON mmo_vineyard (owner_character_id);

-- ─────────────────────────────────────────────────────────────────────────────
-- 8. mmo_world_clock  (world.clock.WorldClock — singleton row, id=1)
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE mmo_world_clock (
    id                    BIGINT       PRIMARY KEY,
    current_absolute_day  BIGINT       NOT NULL DEFAULT 0,
    last_advance_epoch_ms BIGINT       NOT NULL DEFAULT 0
);

-- Seed the singleton clock row (year 1, day 0)
INSERT INTO mmo_world_clock (id, current_absolute_day, last_advance_epoch_ms)
VALUES (1, 0, 0);
