-- V29__export_records.sql — foreign-market export transactions (EXPORT lane).
-- H2 dev/test use ddl-auto; this Flyway script is the Postgres prod schema.

CREATE TABLE export_records (
    id                   BIGSERIAL        PRIMARY KEY,
    seller_character_id  BIGINT           NOT NULL REFERENCES mmo_character(id),
    foreign_market_id    VARCHAR(64)      NOT NULL,
    cellar_item_id       BIGINT           NOT NULL,
    quantity             DOUBLE PRECISION NOT NULL,
    gross_gel            DOUBLE PRECISION NOT NULL,
    tariff_gel           DOUBLE PRECISION NOT NULL,
    net_gel              DOUBLE PRECISION NOT NULL,
    sold_day             BIGINT           NOT NULL,
    created_at           BIGINT           NOT NULL
);
CREATE INDEX idx_export_records_seller_character_id ON export_records (seller_character_id);
