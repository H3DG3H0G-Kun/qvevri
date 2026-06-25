-- V8__trade_offers.sql
-- Flyway migration for LANE TRADE — player-to-player economy.
-- Creates the trade_offers table.
-- Postgres production; H2 dev/test uses ddl-auto=update (Flyway disabled there).

-- ─────────────────────────────────────────────────────────────────────────────
-- trade_offers  (com.game.trade.TradeOffer)
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE trade_offers (
    id                    BIGSERIAL        PRIMARY KEY,
    seller_character_id   BIGINT           NOT NULL REFERENCES mmo_character(id),
    buyer_character_id    BIGINT           REFERENCES mmo_character(id),   -- NULL until accepted
    kind                  VARCHAR(32)      NOT NULL,   -- 'GOODS' | 'CELLAR_ITEM'
    reference             VARCHAR(256)     NOT NULL,   -- goodTypeId or cellarItemId as string
    quantity              DOUBLE PRECISION NOT NULL,
    price_gel             DOUBLE PRECISION NOT NULL,
    status                VARCHAR(32)      NOT NULL DEFAULT 'OPEN',  -- OPEN | ACCEPTED | CANCELLED
    created_at            BIGINT           NOT NULL
);

-- Marketplace scan: most reads filter on status = OPEN
CREATE INDEX idx_trade_offers_status           ON trade_offers (status);

-- Seller look-up: GET /api/trade/offers/mine
CREATE INDEX idx_trade_offers_seller_character ON trade_offers (seller_character_id);
