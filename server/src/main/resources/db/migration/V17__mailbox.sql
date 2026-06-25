-- V17__mailbox.sql
-- Flyway migration for LANE MAIL — in-game mailbox with optional attachments.
-- Postgres production DDL; H2 dev/test uses ddl-auto=update (Flyway disabled).
--
-- Reserved-word avoidance (H2 + SQL-92):
--   recipient_character_id  (not "to")
--   sender_character_id     (not "from")
--   is_read                 (not "read")
--   is_claimed              (not "status")
--   attach_kind             (not "kind" alone, safe but explicit)
--   attach_ref_id           (not "ref" or "value")
--   attach_amount           (not "value" or "amount" alone — amount is safe but qualified)
--   created_at              (not "date" or "timestamp")

CREATE TABLE mailbox (
    id                      BIGSERIAL           PRIMARY KEY,
    recipient_character_id  BIGINT              NOT NULL REFERENCES mmo_character(id),
    sender_character_id     BIGINT              REFERENCES mmo_character(id),
    subject                 VARCHAR(255)        NOT NULL,
    body                    VARCHAR(4096)       NOT NULL,
    attach_kind             VARCHAR(16),                    -- 'GEL' | 'GOODS' | 'CELLAR_ITEM' | NULL
    attach_ref_id           VARCHAR(255),                   -- goodTypeId or cellarItemId (string); NULL for GEL/none
    attach_amount           DOUBLE PRECISION    NOT NULL DEFAULT 0.0,
    is_read                 BOOLEAN             NOT NULL DEFAULT FALSE,
    is_claimed              BOOLEAN             NOT NULL DEFAULT FALSE,
    created_at              BIGINT              NOT NULL
);

-- Primary access pattern: load all mail for a recipient character
CREATE INDEX idx_mailbox_recipient_character_id ON mailbox (recipient_character_id);
