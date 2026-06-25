-- V25__hired_staff.sql
-- Flyway migration for LANE LABOR — hired NPC staff with wage accrual.
-- Postgres production; H2 dev/test uses ddl-auto=update (Flyway disabled there).
--
-- Column names avoid H2/SQL reserved words:
--   labor_status   (not status, state, rank, level)
--   staff_type_id  (stable FK string into StaffCatalog; not 'type' alone)
--   hired_day      (absolute sim-day of hire)
--   last_paid_day  (watermark for wage accrual; updated on each payroll run)
--   created_at     (wall-clock epoch-ms of row creation)
--
-- Wage accrual model (computed lazily, no column needed):
--   wagesOwed = (currentAbsoluteDay - last_paid_day) * dailyWageGel
--   where dailyWageGel is looked up in StaffCatalog by staff_type_id.
--
-- v1 note: auto-fire on missed payroll is deferred; the player is responsible
-- for keeping the wallet funded. No FK constraint on staff_type_id (static catalog
-- is in Java, not a DB table).

CREATE TABLE hired_staff (
    id             BIGSERIAL        PRIMARY KEY,
    character_id   BIGINT           NOT NULL,               -- FK → mmo_character.id (no cascade)
    staff_type_id  VARCHAR(64)      NOT NULL,               -- stable id from StaffCatalog
    hired_day      BIGINT           NOT NULL,               -- absolute sim-day when hired
    last_paid_day  BIGINT           NOT NULL,               -- last payroll watermark (= hired_day initially)
    labor_status   VARCHAR(16)      NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE | QUIT
    created_at     BIGINT           NOT NULL                -- wall-clock epoch-ms
);

-- Primary access pattern: look up all staff for a character (status endpoint + payroll)
CREATE INDEX idx_hired_staff_character_id ON hired_staff (character_id);
