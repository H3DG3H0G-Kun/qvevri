-- V20__chat_messages.sql
-- Flyway migration for LANE CHAT — text channels and direct messages.
-- Creates the chat_messages table.
-- Postgres production; H2 dev/test uses ddl-auto (Flyway disabled there).
--
-- Column names avoid H2/SQL reserved words:
--   body_text          (not `body` or `message` — potentially reserved in some H2 builds)
--   channel            (safe in both H2 and Postgres)
--   sender_character_id (not `from` — SQL reserved word)
--   sender_name        (safe)
--   created_at         (epoch ms; BIGINT — not `timestamp` which is a type keyword)

CREATE TABLE chat_messages (
    id                   BIGSERIAL        PRIMARY KEY,
    channel              VARCHAR(128)     NOT NULL,
    sender_character_id  BIGINT           NOT NULL REFERENCES mmo_character(id),
    sender_name          VARCHAR(120)     NOT NULL,
    body_text            VARCHAR(500)     NOT NULL,
    created_at           BIGINT           NOT NULL
);

-- Composite index for the primary access patterns:
--   1. fetch newest N messages in a channel (ORDER BY created_at DESC)
--   2. polling: fetch messages with id > sinceId in a channel
CREATE INDEX idx_chat_messages_channel_id
    ON chat_messages (channel, id);

CREATE INDEX idx_chat_messages_channel_created_at
    ON chat_messages (channel, created_at DESC);
