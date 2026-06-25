-- V22__contests.sql
-- Flyway migration for LANE CONTEST — timed wine competitions judged on quality.
-- Postgres production; H2 dev/test uses ddl-auto=update (Flyway disabled there).
--
-- Column names avoid H2/SQL reserved words:
--   contest_status  (not status, state)
--   end_day         (not day, date)
--   prize_gel       (not value, prize — prize is not reserved but being explicit)
--   quality_score   (not quality — quality is not reserved but snake_case parity)
--   created_at
--
-- v1 note: prize_gel is NPC-funded; contests.creator_character_id omitted
-- since v1 does not debit the creator and no per-creator query is needed yet.

CREATE TABLE contests (
    id               BIGSERIAL        PRIMARY KEY,
    name             VARCHAR(255)     NOT NULL,
    description      VARCHAR(1000)    NOT NULL,
    end_day          BIGINT           NOT NULL,            -- absolute sim-day of expiry
    prize_gel        DOUBLE PRECISION NOT NULL,            -- GEL to award placement-1 winner
    contest_status   VARCHAR(32)      NOT NULL DEFAULT 'OPEN',  -- OPEN | JUDGED
    created_at       BIGINT           NOT NULL
);

-- Most reads filter on contest_status = 'OPEN'
CREATE INDEX idx_contests_contest_status ON contests (contest_status);

CREATE TABLE contest_entries (
    id               BIGSERIAL        PRIMARY KEY,
    contest_id       BIGINT           NOT NULL REFERENCES contests(id),
    character_id     BIGINT           NOT NULL,            -- FK → mmo_character.id (no cascade)
    cellar_item_id   BIGINT           NOT NULL,            -- FK → cellar_items.id (snapshot only)
    quality_score    DOUBLE PRECISION NOT NULL,            -- snapshot of CellarItem.quality at entry time
    placement        INTEGER,                              -- NULL until judged; 1 = best
    created_at       BIGINT           NOT NULL
);

-- Fast lookup of all entries for a contest (judging + results)
CREATE INDEX idx_contest_entries_contest_id   ON contest_entries (contest_id);

-- Fast lookup of a character's entries across contests
CREATE INDEX idx_contest_entries_character_id ON contest_entries (character_id);
