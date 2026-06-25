# PRODUCTION & SPATIAL-ECONOMY SPEC (v1)

Three additive, new-package lanes that make the economy dynamic and use the geo'd
regions. Pre-assigned migrations: **ECONOMY=V11, LOGISTICS=V12, BUILDINGS=V13.**

## Hard rules (all lanes)
- Additive only; all existing tests stay green. NEW packages only — do NOT edit
  `Character`, `CellarItem`, `WinePricer`, market, goods, wine, land, trade,
  progression, quest, sim, or config. Call existing services/repositories
  (`CharacterService.adjustWallet/getOwned`, `GoodsService.grant/decrement`,
  `CellarItemRepository`, `WorldClockService`, `WorldCatalog`/`RegionInfo` for region
  coordinates) — never modify them.
- Inline bearer auth via `AccountTokenService` + ownership via
  `CharacterService.getOwned`. Matchers for `/api/economy|logistics|build/**` already added.
- H2 dev/test via ddl-auto — avoid reserved-word column names (no `value`, `year`,
  `state`, `level`, `status` → prefer `xp_level`, `ship_status`, `building_level`, etc.).
- Determinism: time/travel use the world clock's sim-day + fixed geo math, never wall-clock.

## LANE ECONOMY — `com.game.economy`. Migration V11.
Dynamic prices that respond to supply and region, plus a sale fee money-sink.
- A pricing engine: for an item type + region, compute price = base (read from the
  existing `WinePricer`/catalog, read-only) × a supply factor (more active listings of
  that type → lower price; use `CellarItemRepository.countByItemTypeAndEscrowedFalse`)
  × a regional demand factor (a small static per-region multiplier table). Plus a sale
  fee (e.g. 5%) deducted as a sink, surfaced in quotes (do NOT hook market buy yet).
- `PriceSnapshot` @Entity (`price_snapshots`): id, itemType, region, price, supplyCount,
  simDay, createdAt — record on demand for history.
- Endpoints `/api/economy/**` (auth):
  - `GET /api/economy/price?itemType=&region=` → `{ basePrice, supplyFactor,
    regionalFactor, grossPrice, fee, netPrice, supplyCount }`.
  - `GET /api/economy/index` → per-region price index for the main wine item type.
  - `POST /api/economy/snapshot` `{itemType, region}` → persists + returns a PriceSnapshot.
- Tests: more supply → lower grossPrice; regional factor changes price by region; fee
  computed correctly; snapshot persists. New package only + V11 + tests.

## LANE LOGISTICS — `com.game.logistics`. Migration V12.
Ship goods/wine between regions; travel time from real geo distance (the hauler career).
- `Shipment` @Entity (`shipments`): id, ownerCharacterId, recipientCharacterId
  (nullable = deliver back to self), kind ("GOODS"|"CELLAR_ITEM"), refId
  (goodTypeId or cellarItemId), quantity, fromRegion, toRegion, departDay, arriveDay,
  shipStatus ("IN_TRANSIT"|"COLLECTED"|"CANCELLED"), createdAt. Repo: findByOwnerCharacterId.
- Travel: `arriveDay = departDay + travelDays`, where `travelDays = max(1,
  ceil(haversineKm(fromRegion, toRegion) / KM_PER_DAY))` using the region lat/long from
  `RegionInfo`/`WorldCatalog`. Distance math is pure + deterministic.
- Endpoints `/api/logistics/**` (auth + ownership):
  - `POST /api/logistics/ship` `{characterId, kind, refId, quantity, toRegion,
    recipientCharacterId?}` → remove the goods/escrow the cellar item from the sender,
    compute arriveDay, persist IN_TRANSIT, return it. (Seller must own the goods/item.)
  - `GET /api/logistics/{characterId}` → that character's shipments.
  - `POST /api/logistics/collect` `{characterId, shipmentId}` → only if
    `currentDay >= arriveDay` (else 400 NOT_ARRIVED); deliver: GOODS →
    `GoodsService.grant(recipient, …)`; CELLAR_ITEM → reassign `characterId` + clear
    escrow; mark COLLECTED.
- Tests: ship debits inventory + sets a future arriveDay; collect before arrival → 400;
  after advancing the clock past arriveDay → transfers ownership; farther region →
  strictly more travelDays (e.g. Kakheti→Adjara > Kakheti→Kartli). New package + V12 + tests.

## LANE BUILDINGS — `com.game.build`. Migration V13.
Construct estate buildings that grant production bonuses.
- `BuildingType` static catalog: MARANI (winery — quality/ferment bonus), CELLAR (raises
  aging cap), PRESS_HOUSE (extraction/quality), COTTAGE (storage). Each: id, displayName,
  costGel, input goods (list of goodTypeId+qty), bonusType (String), bonusValue (double).
- `Building` @Entity (`buildings`): id, ownerCharacterId, parcelId (nullable link to a
  land parcel), buildingTypeId, buildingLevel (int, default 1), builtDay, createdAt. Repo:
  findByOwnerCharacterId.
- Endpoints `/api/build/**` (auth + ownership):
  - `GET /api/build/catalog` → all BuildingTypes.
  - `POST /api/build/construct` `{characterId, parcelId?, buildingTypeId}` → pay costGel
    via `CharacterService.adjustWallet` (400 insufficient funds) + consume input goods via
    `GoodsService` (400 if missing), create the Building, return it.
  - `GET /api/build/{characterId}` → that character's buildings.
  - `GET /api/build/bonuses/{characterId}` → aggregated bonuses by type (sum across owned
    buildings) — a read other systems can later consult.
- May READ the land `ParcelRepository` to validate parcel ownership if a parcelId is given
  (read-only; do not edit land). Tests: construct debits wallet + consumes goods (seed via
  `/api/shop/buy` first); insufficient funds → 400; missing input goods → 400; list;
  bonuses aggregate correctly. New package only + V13 + tests.

## Deferred (next integration pass, after green)
- Wire bonuses into the WINE pipeline (marani/cellar actually change fermentation/aging),
  ECONOMY pricing into market buy/sell, and LOGISTICS into trade fulfilment. Plus the
  earlier deferred XP/quest auto-wiring. All cross-cutting — do AFTER a green build.
