-- V26__character_locations.sql
-- Flyway migration for LANE TRAVEL — character location tracking between regions.
-- Postgres production; H2 dev/test uses ddl-auto=update (Flyway disabled there).
--
-- Column names avoid H2/SQL reserved words:
--   travel_status   (not status, state)
--   current_region  (not region)
--   dest_region     (nullable; null when SETTLED)
--   depart_day      (absolute sim-day of departure; 0 when never travelled)
--   arrive_day      (absolute sim-day of arrival; >= depart_day + 1 when TRAVELLING)
--   created_at      (wall-clock epoch-ms of row creation)
--
-- Travel model:
--   travelDays = max(1, ceil(haversineKm(from, to) / 40))
--   arriveDay  = departDay + travelDays
--   Travel cost: flat 5 GEL per departure (deducted from character wallet).
--
-- Lazy semantics: rows are created on first GET /api/travel/{characterId}.
-- Arrival is also lazy: on GET the service checks currentAbsoluteDay >= arrive_day
-- and flips travel_status → SETTLED without a scheduler.
--
-- No FK constraint on character_id (avoids cascade issues with mmo_character).

CREATE TABLE character_locations (
    id             BIGSERIAL        PRIMARY KEY,
    character_id   BIGINT           NOT NULL,                    -- FK → mmo_character.id (no cascade)
    current_region VARCHAR(32)      NOT NULL,                    -- Region enum name (SETTLED location)
    travel_status  VARCHAR(16)      NOT NULL DEFAULT 'SETTLED',  -- SETTLED | TRAVELLING
    dest_region    VARCHAR(32),                                  -- null when SETTLED
    depart_day     BIGINT           NOT NULL DEFAULT 0,          -- absolute sim-day of departure
    arrive_day     BIGINT           NOT NULL DEFAULT 0,          -- absolute sim-day of arrival
    created_at     BIGINT           NOT NULL                     -- wall-clock epoch-ms
);

-- Primary access: look up a character's location row (unique — one row per character)
CREATE UNIQUE INDEX uq_character_locations_character_id
    ON character_locations (character_id);
