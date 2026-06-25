-- V15__auctions.sql
-- Flyway migration for LANE AUCTION — timed competitive bidding with lazy settlement.
-- Creates the auctions table.
-- Postgres production; H2 dev/test uses ddl-auto=update (Flyway disabled there).
--
-- Column names avoid H2/SQL reserved words:
--   auction_status         (not status, state)
--   start_bid_gel          (not value, balance)
--   current_bid_gel        (not value, balance)
--   end_day                (not day, date)
--   high_bidder_character_id (not bidder)
--   ref_id                 (not reference — reserved in some dialects)

CREATE TABLE auctions (
    id                          BIGSERIAL        PRIMARY KEY,
    seller_character_id         BIGINT           NOT NULL REFERENCES mmo_character(id),
    kind                        VARCHAR(32)      NOT NULL,            -- 'GOODS' | 'CELLAR_ITEM'
    ref_id                      VARCHAR(256)     NOT NULL,            -- goodTypeId or cellarItemId string
    quantity                    DOUBLE PRECISION NOT NULL,
    start_bid_gel               DOUBLE PRECISION NOT NULL,
    current_bid_gel             DOUBLE PRECISION,                     -- NULL until first bid
    high_bidder_character_id    BIGINT,                              -- NULL until first bid
    end_day                     BIGINT           NOT NULL,            -- absolute sim-day of expiry
    auction_status              VARCHAR(32)      NOT NULL DEFAULT 'OPEN', -- OPEN | SETTLED | CANCELLED
    created_at                  BIGINT           NOT NULL
);

-- Most reads filter on auction_status = 'OPEN'
CREATE INDEX idx_auctions_auction_status      ON auctions (auction_status);

-- Seller look-up (future mine endpoint)
CREATE INDEX idx_auctions_seller_character_id ON auctions (seller_character_id);
