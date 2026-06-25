-- V16__bank.sql
-- Flyway migration for LANE BANK — savings accounts + loans with per-sim-day compound interest.
-- Postgres production; H2 dev/test uses ddl-auto=update (Flyway disabled there).
--
-- Column names avoid H2/SQL reserved words:
--   savings_gel       (not balance, value)
--   loan_status       (not status, state)
--   daily_rate        (not rate)
--   opened_day        (not day, year)
--   last_accrued_day  (not day, year)
--   outstanding_gel   (not balance, value)
--   principal_gel     (not balance, value)

-- ── bank_accounts ─────────────────────────────────────────────────────────────
-- One savings account per character (unique on character_id).
-- Lazy-created on first bank access with savings_gel = 0.

CREATE TABLE bank_accounts (
    id             BIGSERIAL   PRIMARY KEY,
    character_id   BIGINT      NOT NULL REFERENCES mmo_character(id),
    savings_gel    DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    created_at     BIGINT      NOT NULL
);

-- Enforce one account per character; also the primary lookup path.
CREATE UNIQUE INDEX uq_bank_accounts_character_id ON bank_accounts (character_id);

-- ── loans ─────────────────────────────────────────────────────────────────────
-- Each row is a single loan episode. A character may hold at most one OPEN loan
-- at a time (enforced in BankService, not by a DB constraint, so history is kept).

CREATE TABLE loans (
    id               BIGSERIAL        PRIMARY KEY,
    character_id     BIGINT           NOT NULL REFERENCES mmo_character(id),
    principal_gel    DOUBLE PRECISION NOT NULL,
    outstanding_gel  DOUBLE PRECISION NOT NULL,
    daily_rate       DOUBLE PRECISION NOT NULL,
    opened_day       BIGINT           NOT NULL,
    last_accrued_day BIGINT           NOT NULL,
    loan_status      VARCHAR(16)      NOT NULL,   -- 'OPEN' | 'REPAID'
    created_at       BIGINT           NOT NULL
);

-- Primary access pattern: load all loans for a character
CREATE INDEX idx_loans_character_id ON loans (character_id);
