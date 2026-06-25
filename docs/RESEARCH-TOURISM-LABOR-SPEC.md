# RESEARCH, TOURISM & LABOR SPEC (v1)

Three additive, new-package lanes. Pre-assigned migrations: **RESEARCH=V23, TOURISM=V24, LABOR=V25.**

## Hard rules (all lanes)
- Additive only; all existing tests stay green. NEW packages only — do NOT edit
  `Character`, `CellarItem`, market, goods, build, land, or any existing package or config.
  READ their repos/services (call, never edit): `CharacterService.adjustWallet/getOwned`,
  `GoodsService`, `BuildingRepository` (com.game.build, V13 — for tourism), `WorldClockService.currentAbsoluteDay`.
- Inline bearer auth via `AccountTokenService` + ownership via `CharacterService.getOwned`.
  Matchers for `/api/research|tourism|labor/**` already added.
- Time-based effects (research completion, tourism income, wage accrual) resolve LAZILY off
  the world clock (currentAbsoluteDay), no scheduler. Deterministic.
- H2 dev/test via ddl-auto — avoid reserved-word columns (no `value`, `year`, `state`,
  `level`, `status`, `rank` → use `research_status`, `last_paid_day`, etc.; verify against H2).
  Flyway for prod.
- **Compile-safety (tests):** every response-body map used with `containsKey`/`containsKeys`
  declared `Map<String,Object>` (NOT `Map<?,?>`); 4xx reads use `String.class`; list reads `List.class`.

## LANE RESEARCH — `com.game.research`. Migration V23.
A per-character tech tree: pay GEL, wait sim-days, unlock a bonus.
- Static `ResearchCatalog` of ~6 nodes (record/class): id, title, description, costGel (double),
  durationDays (int), prereqId (nullable String — must be COMPLETE first), bonusType (String),
  bonusValue (double). Examples: "improved_pruning" (yield), "temp_control" (faster ferment,
  prereq none), "cold_soak" (quality, prereq temp_control), "rootstock_program" (phylloxera),
  "logistics_network" (cheaper shipping), "barrel_program" (aging). Provide all() + find(id).
- `PlayerResearch` @Entity (`player_research`): id, characterId, nodeId (String), researchStatus
  ("RESEARCHING"|"COMPLETE"), startDay (long), readyDay (long), createdAt. Unique on
  (characterId, nodeId). Repo: findByCharacterId, findByCharacterIdAndNodeId.
- `ResearchService` + `ResearchController` `/api/research/**` (auth + ownership):
  - `GET /api/research/catalog` → all nodes.
  - `GET /api/research/{characterId}` → that character's PlayerResearch (auto-complete any whose
    readyDay has passed on read).
  - `POST /api/research/{nodeId}/start` `{characterId}` → 404 unknown node; 400 if already
    started/complete; 400 if prereq not COMPLETE; debit costGel via adjustWallet (400 insufficient);
    create RESEARCHING with readyDay = currentDay + durationDays.
  - Lazy completion: on any read, RESEARCHING rows with currentDay >= readyDay flip to COMPLETE.
- Flyway V23. Tests: catalog returned; start debits wallet + creates RESEARCHING; prereq enforced
  (start a node needing a prereq before it's done → 400); after advancing the clock past readyDay,
  the node reads COMPLETE; double-start → 400; insufficient funds → 400; ownership enforced.
  Owns `com.game.research` only + V23 + tests.

## LANE TOURISM — `com.game.tourism`. Migration V24.
Estates draw visitors that generate passive income, scaling with owned buildings.
- `TourismLedger` @Entity (`tourism_ledgers`): id, characterId (unique), lastClaimDay (long),
  createdAt. Lazy income: on claim, `days = currentDay − lastClaimDay`; `rate = BASE_PER_DAY +
  PER_BUILDING × (#buildings the character owns, read via BuildingRepository.findByOwnerCharacterId)`;
  `income = days × rate`; credit wallet via adjustWallet(+income); set lastClaimDay = currentDay.
  (Lazy-create the ledger at first access with lastClaimDay = currentDay so the first claim after
  some days yields income.)
- `TourismService` + `TourismController` `/api/tourism/**` (auth + ownership):
  - `GET /api/tourism/{characterId}` → { lastClaimDay, currentRatePerDay, buildingsCount,
    accruedSoFar } (accrued computed but not yet paid).
  - `POST /api/tourism/{characterId}/claim` `{}` → pays accrued income, returns { paid, walletGel,
    lastClaimDay }.
- Flyway V24. Tests: GET lazy-creates a ledger; building more (via /api/build/construct — buy a
  cheap COTTAGE first) raises currentRatePerDay; after advancing the clock N days, claim pays
  days×rate and wallet increases; claiming again immediately pays ~0; ownership enforced.
  (Reads BuildingRepository — cross-package read only.) Owns `com.game.tourism` only + V24 + tests.

## LANE LABOR — `com.game.labor`. Migration V25.
Hire NPC staff: upfront cost + ongoing wages (a sink), each granting a benefit.
- Static `StaffCatalog` of ~4 roles (record/class): id, title, hireCostGel (double), dailyWageGel
  (double), benefitType (String), benefitValue (double). Examples: "vineyard_hand" (yield),
  "cellar_master" (quality), "cooper_apprentice" (craft), "merchant_clerk" (sales). Provide all()+find.
- `HiredStaff` @Entity (`hired_staff`): id, characterId, staffTypeId (String), hiredDay (long),
  lastPaidDay (long), laborStatus ("ACTIVE"|"QUIT"), createdAt. Repo: findByCharacterId,
  findByCharacterIdAndLaborStatus.
- `LaborService` + `LaborController` `/api/labor/**` (auth + ownership):
  - `GET /api/labor/catalog` → roles.
  - `GET /api/labor/{characterId}` → that character's staff + the total wages currently owed
    (accrued: for each ACTIVE staff, `(currentDay − lastPaidDay) × dailyWage`).
  - `POST /api/labor/hire` `{characterId, staffTypeId}` → debit hireCostGel (400 insufficient),
    create ACTIVE staff with hiredDay=lastPaidDay=currentDay.
  - `POST /api/labor/payroll` `{characterId}` → compute total wages owed across ACTIVE staff; if
    wallet can cover it → adjustWallet(−owed), set each lastPaidDay=currentDay; if NOT → 400
    CANNOT_MAKE_PAYROLL (v1: do not auto-fire; leave it to the player; note this). Return amount paid.
  - `POST /api/labor/{staffId}/fire` `{characterId}` → mark QUIT (no refund).
  - `GET /api/labor/benefits/{characterId}` → aggregated benefitType → summed benefitValue across ACTIVE staff.
- Flyway V25. Tests: catalog returned; hire debits wallet + creates ACTIVE; wages accrue after
  advancing the clock (owed > 0); payroll deducts owed and resets (owed→0); payroll with insufficient
  wallet → 400; fire flips to QUIT and stops further accrual; benefits aggregate; ownership enforced.
  Owns `com.game.labor` only + V25 + tests.

## Deferred (connective pass, sequential from green)
- Research/tourism/labor bonuses feed the WINE pipeline + ECONOMY + LOGISTICS; tourism scales with
  building TYPES not just count; payroll auto-fires on miss. Cross-cutting — sequential, not parallel.
