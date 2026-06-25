# WORLD-CLOCK-SPEC — Persistent World Clock & Living Vineyards (v1)

Turns "request a vintage" into "own a vineyard that grows over server time, that
you return to, tend, and harvest." Source of truth for the world clock, the
persistent `Vineyard`, and the replay model. Lanes implement EXACTLY; non-breaking
to the 111 green tests.

## 0. Key idea — replay, don't persist evolving state
The sim is deterministic from (seed, region, year). So a `Vineyard` persists only
its **config**; its state on any day = **replay** the per-day sim from the season
start to the target day. Benefits: no VineState/threat-memory serialization,
bit-exact reproducibility, trivial offline catch-up (just replay to "now").

## 1. World time
- **Absolute day**: `absoluteDay = (year-1)*365 + dayOfYear`, `year` starts at 1,
  `dayOfYear` in 0..364.
- **WorldClock** (single persisted row, `com.game.world.clock`): `currentAbsoluteDay`,
  `lastAdvanceEpochMs`. Advances via `@Scheduled` every
  `world.real-seconds-per-sim-day` (config property, default **30** — 1 sim-day per
  30 s; a ~300-day season ≈ 2.5 h). On each scheduled fire, advance by the whole
  number of sim-days of real time elapsed (so it self-corrects after downtime).
- `WorldClockService`: `int currentAbsoluteDay()`, `int currentYear()`,
  `int currentDayOfYear()`, `void advanceDays(int n)` (used by the scheduler + dev endpoint).
- **Config**: `world.real-seconds-per-sim-day` in application.properties (provisional
  answer to GDD Open Decision #1 — time compression; tune later). Test profile sets
  it very high (clock effectively frozen) so tests control time via the dev endpoint.
- Endpoints (`com.game.world.clock`, permitAll):
  - `GET /api/world/clock` → `{ year, dayOfYear, absoluteDay, realSecondsPerSimDay }`
  - `POST /api/world/advance` `{ days }` → advance the clock by N sim-days (dev/test;
    returns the new clock). Keep enabled for now; gate behind a flag later.

## 2. Persistent Vineyard (`com.game.estate`)
```
Vineyard @Entity (table mmo_vineyard)
  id Long PK · ownerCharacterId Long · region (enum Region) · variety (enum Variety)
  · seed long · budLoad int (default 12) · status (enum: GROWING, FALLOW)
  · lastHarvestedYear int (default 0; 0 = never) · createdAt long
```
v1 scope: an established vineyard yields **one vintage per world-year**. The 3-season
vine-establishment and multi-year vine aging are deferred (note, don't build).
Only Saperavi/Kakheti is fully simulated; other region/variety choices are stored
and currently simulated with Kakheti behaviour (same note as MMO-CORE §3).

## 3. Replay model (`com.game.estate.VineyardReplayService`)
- `VineyardView viewAt(Vineyard v, int year, int dayOfYear)`:
  build `RngStreams(seed)`, `weather.generateYear(rng, region, year)`, initial
  VineState, loop day `0..dayOfYear` applying `vineSimulator.tick(...)` and (always-on
  for owned vineyards) the threat engine per day (mirror `VineyardService`/
  `ThreatYearRunner` composition); return the current state. Pure & deterministic.
- `VineyardView` (DTO): `vineyardId, ownerCharacterId, region, variety, year,
  dayOfYear, stage, brix, taGL, pH, healthFraction, estimatedYieldKg, ripe (bool),
  alreadyHarvestedThisYear (bool), recentEvents[] (last ~8 phenology/threat tells)`.
  `ripe` = stage is RIPENING and brix ≥ 22.
- Harvest: replay to the current day, then `Harvest.pick` → `KineticFermenter.ferment`
  → `Resolver.resolve` (reuse VineyardService's cellar constants), producing a `WineLot`.

## 4. Endpoints (`com.game.estate`, bearer token → account; verify character ownership)
- `POST /api/vineyards` `{characterId, region, variety, seed?, budLoad?}` → 201 Vineyard
  (seed defaults to a value derived from id/time; budLoad default 12).
- `GET /api/vineyards/{characterId}` → `VineyardView[]` — each vineyard's state computed
  at the **current world day** via replay.
- `GET /api/vineyards/detail/{vineyardId}` → single `VineyardView` (must own).
- `POST /api/vineyards/{vineyardId}/harvest` `{characterId}` → replay to current day,
  resolve the bottle, deposit a `CellarItem` for the owner, set
  `lastHarvestedYear = currentYear`; **reject** (400) if `alreadyHarvestedThisYear`
  or not yet `ripe`. Returns `{ cellarItem, bottle, vineyardView }`.

## 5. Dependencies / seams
- WA1 (clock) provides `WorldClockService` (signatures in §1).
- WA2 (estate) reuses: `WorldClockService`; the sim (`KakhetiWeatherModel`,
  `KakhetiVineSimulator`, `ThreatEngine`+`ThreatRegistry`, `Harvest`,
  `KineticFermenter`, `Resolver`); `com.game.character.CharacterService` (ownership);
  and `com.game.market.CellarItem` + `CellarItemRepository` (deposit harvested bottle —
  mirror MB's BottleDto→CellarItem mapping).
- Additive `permitAll` for `/api/vineyards/**` in SecurityConfig (inline token check),
  and `/api/world/advance` is under the already-permitted `/api/world/**`.

## 6. Lane ownership
| Lane | Owns |
|------|------|
| WA1 clock | `com/game/world/clock/**` + `world.real-seconds-per-sim-day` in application(.properties + test) |
| WA2 estate | `com/game/estate/**` + additive SecurityConfig permitAll for `/api/vineyards/**` |
| WC client | `client/Assets/Scripts/Net/Account/**` (estate API additions), `Gameplay/UI/**` (clock + vineyard panels) |
| WQ qa | `server/src/test/java/com/game/world/**` (clock), `com/game/estate/**` |

## 7. Tests (WQ)
- clock: GET returns a date; POST advance increments year/dayOfYear correctly across the 365 rollover; determinism of date math.
- vineyard: plant→GET shows GROWING with a plausible stage for the current day; advancing the clock changes the view (later stage/higher brix); harvest when ripe deposits a bottle in the cellar; harvest-twice-same-year rejected; harvest-when-unripe rejected; ownership enforced.
- replay determinism: same vineyard + same world day → identical VineyardView.
- existing 111 tests stay green.

## 8. Versioning
`v1`. Additive. (Future: 3-season vine establishment, per-region grapes/methods,
player tending actions over time, Tier-B idle aggregation for scale.)
