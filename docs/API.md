# API Contract — Vertical Slice 1

> **Source of truth** for the client ⇆ server contract. Any change to wire
> formats, routes, or DTO shapes MUST be made here first, in the same change.

## Slice scope

A player can: **log in → join a session → move a 3D character → have that
position persisted server-side** and visible to other players in the same
session.

Out of scope for this slice: matchmaking, combat, inventory, chat, anti-cheat,
horizontal scaling, password reset.

---

## 1. Conventions

- Base URL (dev): `http://localhost:8080`
- WebSocket URL (dev): `ws://localhost:8080/ws/game`
- All REST request/response bodies are JSON, `Content-Type: application/json`.
- Coordinates use Unity's **left-handed Y-up** convention. Units = meters.
- Timestamps (`t`) are server epoch milliseconds (`long`).
- Auth: `Authorization: Bearer <token>` header on REST; token passed as a
  query param `?token=<token>` on the WebSocket upgrade request.
- Errors use this envelope:

```json
{ "error": { "code": "INVALID_CREDENTIALS", "message": "human readable" } }
```

Error codes used in this slice: `INVALID_CREDENTIALS`, `UNAUTHORIZED`,
`SESSION_NOT_FOUND`, `SESSION_FULL`, `BAD_REQUEST`.

---

## 2. Shared value types

```jsonc
// Vec3 — world position, meters
{ "x": 0.0, "y": 0.0, "z": 0.0 }

// PlayerState — one player's transform in a session
{
  "playerId": "p_8f3a",
  "displayName": "Lasha",
  "position": { "x": 0.0, "y": 0.0, "z": 0.0 },
  "rotationY": 0.0,        // yaw in degrees, 0..360
  "t": 1718500000000       // server time of last update
}
```

---

## 3. REST endpoints

### POST `/api/auth/login`
Authenticates and returns a session token. For the slice, credentials are
validated against an in-memory user store (seeded users); any unknown username
auto-registers with the supplied password (dev convenience, gated by a flag).

**Request**
```json
{ "username": "lasha", "password": "secret" }
```

**200 OK**
```json
{
  "token": "eyJ...",          // opaque bearer token, treat as secret
  "playerId": "p_8f3a",
  "displayName": "lasha",
  "expiresInSec": 3600
}
```

**401** `INVALID_CREDENTIALS`

---

### POST `/api/sessions/join`
Joins the single shared dev session (or creates it if absent). Requires auth.

**Request**
```json
{ "sessionId": "lobby" }      // optional; defaults to "lobby"
```

**200 OK**
```json
{
  "sessionId": "lobby",
  "spawn": { "x": 0.0, "y": 0.0, "z": 0.0 },
  "self": "p_8f3a",
  "players": [ /* PlayerState[] currently in the session, excluding self */ ]
}
```

**401** `UNAUTHORIZED` · **404** `SESSION_NOT_FOUND` · **409** `SESSION_FULL`
(cap = 16 for the slice)

---

### GET `/api/sessions/{sessionId}/players`
Debug/QA read-only snapshot of persisted state. Requires auth.

**200 OK** → `{ "players": [ /* PlayerState[] */ ] }`

---

## 4. WebSocket protocol — `/ws/game?token=<token>`

Text frames, each a single JSON object with a `type` discriminator. The server
closes the socket with code `4401` if the token is missing/invalid.

### Client → Server

```jsonc
// Sent after socket open to bind the connection to a joined session.
{ "type": "hello", "sessionId": "lobby" }

// Movement update. Client sends at most 20/s (throttle); server may persist
// a throttled subset. seq is a monotonically increasing client counter.
{ "type": "move", "seq": 42, "position": {"x":1,"y":0,"z":3}, "rotationY": 90.0 }

// Keep-alive (optional; socket also kept alive by WS ping/pong).
{ "type": "ping", "t": 1718500000000 }
```

### Server → Client

```jsonc
// Sent once right after a valid "hello".
{ "type": "welcome", "self": "p_8f3a", "players": [ /* PlayerState[] */ ] }

// Broadcast snapshot of all players in the session, ~10/s tick.
{ "type": "state", "t": 1718500000000, "players": [ /* PlayerState[] */ ] }

// A player joined or left (delta, complements the periodic snapshot).
{ "type": "join", "player": { /* PlayerState */ } }
{ "type": "leave", "playerId": "p_77c2" }

{ "type": "pong", "t": 1718500000000 }
{ "type": "error", "error": { "code": "...", "message": "..." } }
```

### Authority & reconciliation (slice rules)
- Movement is **client-authoritative** for the slice (no server reconciliation).
  The server trusts and rebroadcasts client positions. (A later slice replaces
  this with server validation.)
- The server persists each player's latest position at most once per 500 ms.
- On disconnect, the player's last position is retained in the DB; a `leave`
  is broadcast. Re-join restores the persisted position as the spawn point.

---

## 5. Persistence model (server-owned, documented for QA)

`player_state` table (one row per player per session):

| column       | type     | notes                          |
|--------------|----------|--------------------------------|
| player_id    | varchar  | PK part                        |
| session_id   | varchar  | PK part                        |
| display_name | varchar  |                                |
| x, y, z      | double   | last known position            |
| rotation_y   | double   | last known yaw                 |
| updated_at   | bigint   | epoch ms                       |

Dev DB: H2 file-backed (`./data/game`), JPA/Hibernate `ddl-auto=update`.

---

## 6. Architect clarifications (v1.1 — resolves plan-phase questions)

These are binding decisions made during plan review. They tighten, but do not
break, v1. All teammates implement to these.

1. **Bad/missing WS token.** The server rejects the upgrade in
   `beforeHandshake` with **HTTP 401** (no socket is opened). Where a socket is
   already open, it is closed with code **4401**. The client treats *either* an
   upgrade 401 *or* a 4401 close as `UNAUTHORIZED`. (Resolves backend-A,
   netcode-7.5.)
2. **Spawn point.** `join.spawn` = the player's **persisted** position if a row
   exists for `(playerId, sessionId)`, else `{0,0,0}`. (Resolves backend-B.)
3. **Session creation.** For this slice any `sessionId` (default `"lobby"`)
   **auto-creates** on join. `SESSION_NOT_FOUND` is reserved for future named
   sessions and is effectively unreachable now. `SESSION_FULL` (cap 16) still
   applies. (Resolves backend-C.)
4. **Initial player list authority.** The WS `welcome.players` list is
   **authoritative**. The REST `join.players` list is a convenience snapshot;
   on divergence, `welcome` wins. (Resolves netcode-7.1.)
5. **`move.seq`.** Advisory only — used for server-side ordering/debug. The
   server does **not** require gap-free sequences and does not echo `seq`.
   (Resolves netcode-7.6.)
6. **Transport.** Raw WebSocket text frames (Spring `TextWebSocketHandler`).
   **No STOMP/SockJS.** QA integration tests use a raw WS client. (Resolves
   qa-3.)
7. **Test DB profile.** The server ships `application-test.properties` with
   `spring.datasource.url=jdbc:h2:mem:testdb` for an isolated in-memory test DB.
   (Resolves qa-5.)
8. **Client runtime profile.** Unity client uses **.NET Standard 2.1** API
   compatibility (`System.Net.WebSockets.ClientWebSocket` available) and the
   **legacy Input Manager** for the slice. (Resolves netcode-7.3, gameplay-R3.)

## 7. Versioning

This document is `v1.1` of the slice contract. Breaking changes bump a
`X-Api-Version` header and a note here. Within the slice, additive changes only.

---

## 8. MMO-CORE Cellar & Market endpoints (MB lane, additive)

All endpoints require `Authorization: Bearer <token>` (validated inline via
`AccountTokenService`). Errors use the standard envelope `{"error":{"code","message"}}`.

### GET `/api/cellar/{characterId}`
Returns non-escrowed `CellarItem[]` for the character.
- **401** if token missing/invalid
- **404** if character not found or not owned by the token's account

### POST `/api/cellar/{characterId}/grow`
Grows a vintage via the vineyard simulation pipeline.

**Request**
```json
{ "seed": 42, "budLoad": 12, "pickDay": 270, "threats": true }
```
All fields optional (defaults shown). Variety and soil are server-fixed at Saperavi / HUMUS_CARBONATE.

**200 OK**
```json
{
  "cellarItem": { "id": 1, "characterId": 5, "itemType": "AGED_WINE",
                  "quantity": 12.4, "quality": 78.3, "vintageYear": 1,
                  "style": "RED", "appellationOk": true,
                  "label": "Glekhi's First Saperavi", "escrowed": false,
                  "createdAt": 1718500000000 },
  "result": { /* VineyardYearResult */ }
}
```

### GET `/api/market`
Returns all ACTIVE listings, each with `suggestedPrice` from `econ.WinePricer`.

**200 OK** → `MarketListingView[]`
```json
[{ "listing": { "id":1, "sellerCharacterId":5, "cellarItemId":1,
                "askPrice":50.0, "status":"ACTIVE", "createdAt":... },
   "cellarItem": { /* CellarItem */ },
   "suggestedPrice": 47.23 }]
```

### POST `/api/market/list`
Creates an ACTIVE listing; escrowed=true on the CellarItem.

**Request** `{ "characterId": 5, "cellarItemId": 1, "askPrice": 50.0 }`

**200 OK** → `MarketListing`
- **400** if item already escrowed or not owned by the character

### POST `/api/market/buy`
Purchases a listing atomically.

**Request** `{ "characterId": 7, "listingId": 1 }`

**200 OK** → `TradeRecord`
- **400** `"Cannot buy your own listing"` if buyer == seller character
- **400** `"Insufficient funds"` if buyer wallet < askPrice

---

## 9. World catalog & clock endpoints (WA1/MA lane, additive)

All endpoints are `permitAll` — `/api/world/**` is already in SecurityConfig.

### GET `/api/world/regions`
Returns the static catalog of all seven Georgian wine regions.

**200 OK** → `RegionInfo[]`
```json
[
  {
    "region":         "KAKHETI",
    "displayName":    "Kakheti",
    "climate":        "Warm continental; hot dry summers, cold winters; ~1 873 GDD (Winkler III)",
    "signatureGrapes":"Rkatsiteli, Saperavi",
    "methodNote":     "Traditional Kakhetian method: 4–6 months skin contact in qvevri; ...",
    "latitude":       41.92,
    "longitude":      45.47
  }
]
```

| field            | type     | notes                                                                     |
|------------------|----------|---------------------------------------------------------------------------|
| `region`         | string   | Enum name — `KAKHETI`, `KARTLI`, `IMERETI`, `RACHA_LECHKHUMI`, `SAMEGRELO`, `GURIA_ADJARA`, `MESKHETI` |
| `displayName`    | string   | Human-readable name                                                       |
| `climate`        | string   | Short climate description                                                 |
| `signatureGrapes`| string   | Comma-separated signature grape varieties                                 |
| `methodNote`     | string   | Winemaking method note                                                    |
| `latitude`       | double   | WGS84 decimal degrees; center of the region (representative town). Range: 41.0–43.6 |
| `longitude`      | double   | WGS84 decimal degrees; center of the region (representative town). Range: 40.0–46.8 |

Region → representative town mapping (WGS84):

| `region`          | town          | latitude | longitude |
|-------------------|---------------|----------|-----------|
| `KAKHETI`         | Telavi        | 41.92    | 45.47     |
| `KARTLI`          | Gori          | 41.98    | 44.11     |
| `IMERETI`         | Kutaisi       | 42.27    | 42.70     |
| `RACHA_LECHKHUMI` | Ambrolauri    | 42.52    | 43.15     |
| `SAMEGRELO`       | Zugdidi       | 42.51    | 41.87     |
| `GURIA_ADJARA`    | Batumi        | 41.64    | 41.64     |
| `MESKHETI`        | Akhaltsikhe   | 41.64    | 42.98     |

### GET `/api/world/careers`
Returns the static catalog of all nine career types. Response is `CareerInfo[]`; each entry has `type` (enum name) and `displayName`.

---

### GET `/api/world/clock`
Returns the current shared world time.

**200 OK**
```json
{
  "year":                 1,
  "dayOfYear":            0,
  "absoluteDay":          0,
  "realSecondsPerSimDay": 30
}
```

Date math: `absoluteDay = (year - 1) * 365 + dayOfYear`, `year >= 1`, `dayOfYear` in `0..364`.
Config key `world.real-seconds-per-sim-day` (default 30 s; test profile 86400000 s = frozen).

### POST `/api/world/advance`
Dev/test endpoint — advances the clock by N sim-days and returns the new state.

**Request**
```json
{ "days": 10 }
```

**200 OK** → same shape as `GET /api/world/clock`
- **400** if `days < 0`

---

## 10. Vineyard (estate) endpoints (WA2 lane, additive — WORLD-CLOCK-SPEC §4)

All require `Authorization: Bearer <token>` (inline token check via
`AccountTokenService`). Errors use the standard envelope `{"error":{"code","message"}}`.

### POST `/api/vineyards`
Plants a new persistent vineyard.

**Request**
```json
{ "characterId": 5, "region": "KAKHETI", "variety": "SAPERAVI", "seed": 42, "budLoad": 12 }
```
`seed` and `budLoad` are optional; server derives defaults.

**201 Created** → `Vineyard` entity
```json
{ "id": 1, "ownerCharacterId": 5, "region": "KAKHETI", "variety": "SAPERAVI",
  "seed": 42, "budLoad": 12, "status": "GROWING",
  "lastHarvestedYear": 0, "createdAt": 1718500000000 }
```
- **401** if token missing/invalid
- **403** if `characterId` not owned by the token's account

### GET `/api/vineyards/{characterId}`
Returns live state of all vineyards for the character, replayed to the current world day.

**200 OK** → `VineyardView[]`
```json
[{
  "vineyardId": 1, "ownerCharacterId": 5, "region": "KAKHETI", "variety": "SAPERAVI",
  "year": 1, "dayOfYear": 200, "stage": "VERAISON",
  "brix": 18.4, "taGL": 7.2, "pH": 3.35, "healthFraction": 0.92,
  "estimatedYieldKg": 8.1, "ripe": false, "alreadyHarvestedThisYear": false,
  "recentEvents": ["Bud break", "Flowering"]
}]
```
`ripe` = stage is RIPENING and brix ≥ 22.

### POST `/api/vineyards/{vineyardId}/harvest`
Harvests a ripe vineyard; deposits a `CellarItem` for the owner and sets
`lastHarvestedYear = currentYear`.

**Request** `{ "characterId": 5 }`

**200 OK**
```json
{
  "cellarItem": { /* CellarItem — same shape as §8 */ },
  "bottle": { "variety": "SAPERAVI", "style": "RED", "vintageYear": 1,
              "quality": 77.4, "abv": 13.8, "fault": null, "label": "..." },
  "vineyardView": { /* VineyardView with alreadyHarvestedThisYear=true */ }
}
```
- **400** if `alreadyHarvestedThisYear` is true
- **400** if not yet `ripe`
- **403** if character does not own the vineyard

### GET `/api/vineyards/{vineyardId}/management`
Returns the current management plan (lever snapshot) for the vineyard.
Bearer token → account; vineyard must be owned by a character of that account.
Client: `IMmoApi.GetManagementPlanAsync(vineyardId)` → `ManagementPlanDto`. (TC lane)

**200 OK** → `ManagementPlanDto`
```json
{
  "vineyardId": 1,
  "budLoad": 12,
  "ownRoots": true,
  "canopyOpenness01": 0.4,
  "leafPulled": false,
  "copperSpray01": 0.0,
  "sulfurSpray01": 0.0,
  "netting": false,
  "guardDog": false,
  "falcons": false,
  "cats": false,
  "ducks": false,
  "coverCrop01": 0.0
}
```
- **401** if token missing/invalid
- **404** if vineyard not found or not owned by the token's account

### POST `/api/vineyards/{vineyardId}/manage`
Applies a partial management plan update. Only non-null lever fields are changed.
Persists the changes and returns the updated `VineyardView` recomputed at the current world day.
Client: `IMmoApi.ManageVineyardAsync(vineyardId, ManageRequestDto)` → `VineyardViewDto`. (TC lane)

**Request** `{ "characterId": 5, <any subset of levers> }`
```json
{
  "characterId": 5,
  "netting": true,
  "copperSpray01": 0.5,
  "budLoad": 10
}
```
All lever fields are optional; `characterId` is required.
Lever ranges: doubles `0.0..1.0`; `budLoad` `1..40`.

**200 OK** → `VineyardView` (same shape as `GET /api/vineyards/{characterId}` list items)

- **400** if any lever is out of range
- **401** if token missing/invalid
- **404** if vineyard not found or character does not own it

### POST `/api/vineyards/{vineyardId}/action`
Records a dated per-day tending action for the current world-clock year and
returns the recomputed `VineyardView` with all actions for that year applied.
Actions are causal: an action on `dayOfYear` D overrides the relevant lever for
all simulated days >= D; days < D are unaffected. Multiple actions in the same
year are applied in `dayOfYear` order during replay (deterministic).

**Request**
```json
{
  "characterId": 5,
  "dayOfYear": 180,
  "actionType": "EMERGENCY_COPPER_SPRAY",
  "value": 0.8
}
```
Supported `actionType` values:
- `EMERGENCY_COPPER_SPRAY` — override `copperSpray01` lever from `dayOfYear`; `value` is 0..1 intensity.
- `EMERGENCY_SULFUR_SPRAY` — override `sulfurSpray01` lever from `dayOfYear`; `value` is 0..1 intensity.
- `EMERGENCY_NETTING`      — override `netting` lever from `dayOfYear`; `value >= 0.5` = enable.

**200 OK** → `VineyardView` (same shape as `GET /api/vineyards/{characterId}` list items)

- **400** if `dayOfYear` is not in 0..364, or `actionType`/`value` missing
- **401** if token missing/invalid
- **404** if vineyard not found or character does not own it

---

## 11. Land (estate parcel) endpoints (LAND lane, BACKEND-DEPTH-SPEC V7)

All require `Authorization: Bearer <token>` (inline bearer check). Errors use the standard envelope `{"error":{"code","message"}}`.

Parcels are anchored to real Georgian coordinates: each parcel's latitude/longitude is derived from the region's representative-town centre (see §9) with a small deterministic jitter (±up to ~0.08°) so estates cluster near the real town but don't all overlap. Coordinates are clamped inside Georgia's bounding box (lat 41.0–43.6, lon 40.0–46.8).

Price: **200 GEL/ha**.

### POST `/api/land/parcels`
Claim/buy a parcel of land.

**Request**
```json
{ "characterId": 5, "region": "KAKHETI", "name": "Iveria Estate", "sizeHectares": 2.5 }
```

**201 Created**
```json
{
  "parcel": {
    "id": 1, "ownerCharacterId": 5, "name": "Iveria Estate",
    "region": "KAKHETI", "latitude": 41.934, "longitude": 45.461,
    "sizeHectares": 2.5, "vineyardId": null, "createdAt": 1750000000000
  },
  "newWalletGel": 400.0
}
```
- **400/402** `INSUFFICIENT_FUNDS` if wallet < price
- **401** if token missing/invalid
- **404** if `characterId` not owned by the token's account

### GET `/api/land/{characterId}`
Returns all parcels owned by the character.

**200 OK** → `Parcel[]`

- **401** if token missing/invalid
- **404** if `characterId` not owned by the token's account

### GET `/api/land/parcel/{parcelId}`
Returns a single parcel. Only the owner's account may access it.

**200 OK** → `Parcel`

- **401** if token missing/invalid
- **404** if parcel not found or not owned by the token's account

### POST `/api/land/parcels/{parcelId}/attach-vineyard`
Attaches an existing vineyard to a parcel (stores `vineyardId` on the parcel).
The vineyard must belong to the same character as the parcel.

**Request** `{ "characterId": 5, "vineyardId": 3 }`

**200 OK** → updated `Parcel`

- **401** if token missing/invalid
- **404** if parcel or vineyard not found or not owned by the character

---

## 12. Progression endpoints (PROGRESSION lane, V9)

All require `Authorization: Bearer <token>` (inline bearer check via `AccountTokenService`).
Errors use the standard envelope `{"error":{"code","message"}}`.

Level curve (deterministic): `xpLevel = floor(sqrt(xp / 100.0)) + 1`.
Examples: xp=0→level 1, xp=100→level 2, xp=400→level 3, xp=900→level 4, xp=2500→level 6.

### GET `/api/progression/{characterId}`
Returns the progression profile for the character. Auto-creates at xp=0 / level=1 / reputation=0 on first access.

**200 OK**
```json
{
  "id": 1,
  "characterId": 5,
  "xp": 400,
  "xpLevel": 3,
  "reputation": 0,
  "updatedAt": 1750000000000
}
```
- **401** if token missing/invalid
- **404** if character not found or not owned by the token's account

### POST `/api/progression/{characterId}/award`
Awards XP to the character and returns the updated profile.

**Request**
```json
{ "amount": 100, "reason": "First vineyard harvest" }
```
`amount` must be > 0 (400 otherwise). `reason` is a human-readable string (informational only).

**200 OK** → same shape as `GET /api/progression/{characterId}`

- **400** if `amount` is missing, zero, or negative
- **401** if token missing/invalid
- **404** if character not found or not owned by the token's account

---

## 13. Quest endpoints (QUEST lane, V10)

All require `Authorization: Bearer <token>` (inline bearer check via `AccountTokenService`).
Errors use the standard envelope `{"error":{"code","message"}}`.

Quest status values: `OFFERED` / `ACTIVE` / `COMPLETED`.

Objective types (client enum-like strings): `PLANT_VINE` / `SELL_BOTTLES` / `CRAFT_VESSEL` / `HARVEST` / `VISIT`.

### GET `/api/quests/catalog`
Returns all static quest definitions. No per-character data; auth is validated but no ownership check.

**200 OK**
```json
[
  {
    "id": "first_vine",
    "title": "The First Vine",
    "description": "...",
    "giverNpc": "Tamada Giorgi",
    "objectiveType": "PLANT_VINE",
    "objectiveCount": 1,
    "rewardGel": 25.0,
    "rewardGoodTypeId": "saperavi_cuttings_certified",
    "rewardGoodQty": 3.0
  }
]
```
- **401** if token missing/invalid

### GET `/api/quests/{characterId}`
Returns all PlayerQuest rows for the given character.

**200 OK** → `PlayerQuest[]`
```json
[
  {
    "id": 1,
    "characterId": 5,
    "questId": "first_vine",
    "questStatus": "ACTIVE",
    "progress": 0,
    "startedAt": 1750000000000,
    "completedAt": null
  }
]
```
- **401** if token missing/invalid
- **404** if character not found or not owned by the token's account

### POST `/api/quests/{characterId}/accept`
Accepts a quest for the character. Creates a new `PlayerQuest` in ACTIVE state.

**Request**
```json
{ "questId": "first_vine" }
```

**200 OK** → `PlayerQuest` (same shape as above, `questStatus = "ACTIVE"`)

- **400** if `questId` is missing or unknown
- **400** if the character already has this quest in any state
- **401** if token missing/invalid
- **404** if character not found or not owned by the token's account

### POST `/api/quests/{characterId}/complete`
Marks the quest COMPLETED and grants the reward: `CharacterService.adjustWallet(+rewardGel)` and, if `rewardGoodTypeId != null`, `GoodsService.grant(rewardGoodTypeId, rewardGoodQty)`.

**Idempotent guard:** if the quest is already COMPLETED the call returns 400 without re-granting any reward.

**Request**
```json
{ "questId": "first_vine" }
```

**200 OK** → `PlayerQuest` (same shape as above, `questStatus = "COMPLETED"`, `completedAt` populated)

- **400** if `questId` is missing or unknown
- **400** if quest was not accepted (no ACTIVE row) or is already COMPLETED
- **401** if token missing/invalid
- **404** if character not found or not owned by the token's account

---

## 14. Building (estate) endpoints (BUILD lane, V13)

All require `Authorization: Bearer <token>` (inline bearer check via `AccountTokenService`).
Errors use the standard envelope `{"error":{"code","message"}}`.

### Building types

| id            | displayName    | costGel | inputs                         | bonusType    | bonusValue |
|---------------|----------------|---------|--------------------------------|--------------|------------|
| `COTTAGE`     | Estate Cottage | 30      | 1× `cover_crop_seed`           | `STORAGE`    | 50.0       |
| `MARANI`      | Marani (Winery)| 80      | 2× `clay_lining_compound`      | `WINE_QUALITY`| 0.05      |
| `CELLAR`      | Stone Cellar   | 70      | 1× `oak_barrel_225l`           | `AGING_CAP`  | 5.0        |
| `PRESS_HOUSE` | Press House    | 60      | 1× `basket_press`              | `EXTRACTION` | 0.04       |

**Affordability note**: COTTAGE is the starter building. costGel=30 + 1× `cover_crop_seed` (14 GEL shop price) = 44 GEL total, within the 100 GEL starting wallet.

### GET `/api/build/catalog`
Returns all four static building type definitions.

**200 OK** → `BuildingType[]`
```json
[{ "id": "COTTAGE", "displayName": "Estate Cottage", "costGel": 30.0,
   "inputs": [{"goodTypeId":"cover_crop_seed","qty":1.0}],
   "bonusType": "STORAGE", "bonusValue": 50.0 }]
```
- **401** if token missing/invalid

### POST `/api/build/construct`
Constructs a new estate building.

**Request**
```json
{ "characterId": 5, "parcelId": 1, "buildingTypeId": "COTTAGE" }
```
`parcelId` is optional. When provided, the parcel must be owned by the character.

**Transaction order** (funds/goods safety):
1. Pre-check wallet (read-only; 400 INSUFFICIENT_FUNDS if insufficient).
2. Pre-check all input goods (read-only; 400 MISSING_GOODS if any absent/insufficient).
3. Debit wallet (`adjustWallet(-costGel)`) — first mutation.
4. Consume each input good (`GoodsService.decrement`).
5. Persist and return the `Building`.

**200 OK** → `Building`
```json
{ "id": 1, "ownerCharacterId": 5, "parcelId": null,
  "buildingTypeId": "COTTAGE", "buildingLevel": 1,
  "builtDay": 0, "createdAt": 1750000000000 }
```
- **400** if `buildingTypeId` is unknown
- **400** `INSUFFICIENT_FUNDS` if wallet < costGel (wallet not debited)
- **400** `MISSING_GOODS` if any input good is absent or insufficient (wallet not debited)
- **404** if `parcelId` given but not owned by the character
- **401** if token missing/invalid

### GET `/api/build/{characterId}`
Returns all buildings owned by the character.

**200 OK** → `Building[]`

- **401** if token missing/invalid
- **404** if character not found or not owned by the token's account

### GET `/api/build/bonuses/{characterId}`
Returns aggregated production bonuses across all the character's buildings.
`bonusValue` per building = `BuildingType.bonusValue × buildingLevel`.

**200 OK**
```json
{ "STORAGE": 100.0, "WINE_QUALITY": 0.05 }
```
- **401** if token missing/invalid
- **404** if character not found or not owned by the token's account

---

## 14. Logistics endpoints (LOGISTICS lane, V12)

All require `Authorization: Bearer <token>` (inline bearer check via `TokenHelper`).
Errors use the standard envelope `{"error":{"code","message"}}`.

Travel-time model: `arriveDay = departDay + max(1, ceil(haversineKm(from, to) / 40))`.
`40 km/day` = laden cart speed across Georgian mountain roads.
Region coordinates are WGS84 from `WorldCatalog` (see §9).

Shipment `shipStatus` values: `IN_TRANSIT` / `COLLECTED` / `CANCELLED`.
Shipment `kind` values: `GOODS` / `CELLAR_ITEM`.

### POST `/api/logistics/ship`
Creates a shipment. Debits GOODS inventory or escrows a CellarItem.
If `fromRegion` is omitted, defaults to the sender character's `homeRegion`.

**Request**
```json
{
  "characterId":          5,
  "kind":                 "GOODS",
  "refId":                "pruning_shears",
  "quantity":             1.0,
  "fromRegion":           "KAKHETI",
  "toRegion":             "KARTLI",
  "recipientCharacterId": 7
}
```
`recipientCharacterId` is optional; if omitted the shipment is delivered back to the owner.
`fromRegion` is optional; if omitted uses the sender's `homeRegion`.

**200 OK** → `Shipment`
```json
{
  "id": 1,
  "ownerCharacterId": 5,
  "recipientCharacterId": 7,
  "kind": "GOODS",
  "refId": "pruning_shears",
  "quantity": 1.0,
  "fromRegion": "KAKHETI",
  "toRegion": "KARTLI",
  "departDay": 42,
  "arriveDay": 45,
  "shipStatus": "IN_TRANSIT",
  "createdAt": 1750000000000
}
```
- **400** if `toRegion` is unknown, `kind` is invalid, goods not owned, or insufficient quantity
- **400** if CellarItem is already escrowed
- **401** if token missing/invalid
- **404** if character not found or not owned by the token's account

### GET `/api/logistics/{characterId}`
Returns all shipments (any status) for the given character.

**200 OK** → `Shipment[]` (same shape as above)

- **401** if token missing/invalid
- **404** if character not found or not owned by the token's account

### POST `/api/logistics/collect`
Collects an arrived shipment. Delivers goods/wine to the recipient.

**Request**
```json
{ "characterId": 5, "shipmentId": 1 }
```

**200 OK** → `Shipment` with `shipStatus = "COLLECTED"`

- **400 NOT_ARRIVED** if `currentDay < arriveDay`
- **400** if shipment is already COLLECTED or CANCELLED
- **401** if token missing/invalid
- **404** if shipment not found or not owned by the character

---

## 15. Bank endpoints (BANK lane, V16)

All require `Authorization: Bearer <token>` (inline bearer check via `AccountTokenService`).
Errors use the standard envelope `{"error":{"code","message"}}`.

Savings accounts are lazy-created at `savingsGel = 0` on first access.
Interest accrues **lazily** on any GET or repay call: `outstandingGel *= (1 + dailyRate)^(currentDay − lastAccruedDay)`, then `lastAccruedDay = currentDay`. Daily rate is `0.01` (1 %/sim-day). Maximum principal per loan: `1000 GEL`. One OPEN loan per character at a time.

### GET `/api/bank/{characterId}`
Returns the character's savings account and all loans, with interest accrued first.

**200 OK**
```json
{
  "account": {
    "id": 1, "characterId": 5, "savingsGel": 40.0, "createdAt": 1750000000000
  },
  "loans": [
    {
      "id": 1, "characterId": 5, "principalGel": 200.0, "outstandingGel": 221.35,
      "dailyRate": 0.01, "openedDay": 0, "lastAccruedDay": 10,
      "loanStatus": "OPEN", "createdAt": 1750000000000
    }
  ]
}
```
- **401** if token missing/invalid
- **404** if character not found or not owned by the token's account

### POST `/api/bank/deposit`
Moves GEL from the character's wallet into savings.

**Request** `{ "characterId": 5, "amountGel": 40.0 }`

**200 OK** → updated `BankAccount`

- **400** if `amountGel <= 0` or wallet insufficient

### POST `/api/bank/withdraw`
Moves GEL from savings into the character's wallet.

**Request** `{ "characterId": 5, "amountGel": 20.0 }`

**200 OK** → updated `BankAccount`

- **400** if `amountGel <= 0` or savings insufficient

### POST `/api/bank/loan`
Issues a new OPEN loan; credits the character's wallet with the principal.

**Request** `{ "characterId": 5, "amountGel": 200.0 }`

**200 OK** → `Loan` (`loanStatus = "OPEN"`, `outstandingGel = principalGel`, `dailyRate = 0.01`)

- **400** if `amountGel <= 0`, `amountGel > 1000`, or an OPEN loan already exists

### POST `/api/bank/repay`
Accrues interest, then pays `min(amountGel, outstandingGel)` from the wallet. Flips to `REPAID` when `outstandingGel ≤ 1e-7`.

**Request** `{ "characterId": 5, "amountGel": 50.0 }`

**200 OK** → updated `Loan`

- **400** if `amountGel <= 0`, wallet insufficient, or no OPEN loan exists

---

## 15. Guild (wine-house) endpoints (GUILD lane, V14)

All require `Authorization: Bearer <token>` (inline bearer check via `TokenHelper`).
Errors use the standard envelope `{"error":{"code","message"}}`.

Guild role values: `FOUNDER` / `MEMBER`.

### POST `/api/guild/create`
Creates a new guild and records the creating character as FOUNDER.

**Request**
```json
{ "characterId": 5, "name": "House Giorgi" }
```

**200 OK** → `Guild`
```json
{ "id": 1, "name": "House Giorgi", "founderCharacterId": 5,
  "treasuryGel": 0.0, "createdAt": 1750000000000 }
```
- **400** if `name` is already taken
- **400** if character is already a member of any guild
- **401** if token missing/invalid
- **404** if `characterId` not owned by the token's account

### POST `/api/guild/{guildId}/join`
Adds the character as a MEMBER of the guild.

**Request** `{ "characterId": 7 }`

**200 OK** → `GuildMember`
```json
{ "id": 2, "guildId": 1, "characterId": 7, "guildRole": "MEMBER", "joinedAt": 1750000000000 }
```
- **400** if character is already a member of any guild
- **401** if token missing/invalid
- **404** if guild not found, or `characterId` not owned by the token's account

### POST `/api/guild/{guildId}/leave`
Removes the character's membership from the guild.
A FOUNDER may only leave if they are the last member (400 otherwise — must transfer or disband first).

**Request** `{ "characterId": 7 }`

**200 OK** (empty body)

- **400** if character is not a member of this guild
- **400** if character is FOUNDER and other members remain
- **401** if token missing/invalid
- **404** if `characterId` not owned by the token's account

### GET `/api/guild/{guildId}`
Returns the guild plus its full member list. Any authenticated user may call this.

**200 OK**
```json
{
  "guild":   { "id": 1, "name": "House Giorgi", "founderCharacterId": 5, "treasuryGel": 25.0, "createdAt": 1750000000000 },
  "members": [
    { "id": 1, "guildId": 1, "characterId": 5, "guildRole": "FOUNDER", "joinedAt": 1750000000000 },
    { "id": 2, "guildId": 1, "characterId": 7, "guildRole": "MEMBER",  "joinedAt": 1750000001000 }
  ]
}
```
- **401** if token missing/invalid
- **404** if guild not found

### POST `/api/guild/{guildId}/deposit`
Moves GEL from the member's wallet into the guild treasury.

**Request** `{ "characterId": 7, "amountGel": 50.0 }`

**200 OK** → updated `Guild` (with new `treasuryGel`)

- **400** if `amountGel` ≤ 0
- **400/402** if character's wallet is insufficient
- **400/403** if character is not a member of this guild
- **401** if token missing/invalid
- **404** if guild not found, or `characterId` not owned by the token's account

### POST `/api/guild/{guildId}/withdraw`
Moves GEL from the guild treasury into the FOUNDER's wallet. FOUNDER only.

**Request** `{ "characterId": 5, "amountGel": 20.0 }`

**200 OK** → updated `Guild` (with new `treasuryGel`)

- **400** if `amountGel` ≤ 0
- **400** if character is not the FOUNDER
- **400** if treasury has insufficient funds
- **401** if token missing/invalid
- **404** if guild not found, or `characterId` not owned by the token's account

---

## 16. Mail endpoints (MAIL lane, V17)

All require `Authorization: Bearer <token>` (inline bearer check via `TokenHelper`).
Errors use the standard envelope `{"error":{"code","message"}}`.

Attachment kinds: `"GEL"` | `"GOODS"` | `"CELLAR_ITEM"`. Attachments are escrowed from the sender at send-time and delivered to the recipient at claim-time.

### POST `/api/mail/send`
Sends a mail. Attachment (if any) is escrowed atomically from the sender.

**Request**
```json
{
  "senderCharacterId":    5,
  "recipientCharacterId": 7,
  "subject":              "A gift",
  "body":                 "Enjoy the wine",
  "attachKind":           "GEL",
  "attachRefId":          null,
  "attachAmount":         20.0
}
```
`attachKind`, `attachRefId`, `attachAmount` are optional (omit for no attachment).
For `GOODS`: `attachRefId` = goodTypeId, `attachAmount` = quantity.
For `CELLAR_ITEM`: `attachRefId` = cellarItemId (as String), `attachAmount` ignored.
For `GEL`: `attachRefId` null, `attachAmount` = GEL to transfer.

**200 OK** → `Mail` entity (see fields below)
- **400** if sender wallet insufficient (GEL), goods not owned/insufficient (GOODS), or CellarItem not owned/already escrowed (CELLAR_ITEM)
- **401** if token missing/invalid
- **404** if `senderCharacterId` not owned by the token's account

### GET `/api/mail/{characterId}`
Returns all mail in the character's inbox.

**200 OK** → `Mail[]`
```json
[{
  "id": 1,
  "recipientCharacterId": 7,
  "senderCharacterId": 5,
  "subject": "A gift",
  "body": "Enjoy the wine",
  "attachKind": "GEL",
  "attachRefId": null,
  "attachAmount": 20.0,
  "read": false,
  "claimed": false,
  "createdAt": 1750000000000
}]
```
- **401** if token missing/invalid
- **404** if `characterId` not owned by the token's account

### POST `/api/mail/{mailId}/read`
Marks a mail as read. Only the recipient may call this.

**Request** `{ "characterId": 7 }`

**200 OK** → updated `Mail` (with `read: true`)

- **401** if token missing/invalid
- **404** if mail not found or `characterId` is not the recipient

### POST `/api/mail/{mailId}/claim`
Claims the attachment. Delivers escrowed asset to the recipient.
Idempotent guard: already-claimed → 400. No-attachment mail → 400.

**Request** `{ "characterId": 7 }`

**200 OK** → updated `Mail` (with `claimed: true`)

- **400** if mail already claimed or has no attachment
- **401** if token missing/invalid
- **404** if mail not found or `characterId` is not the recipient

### POST `/api/mail/{mailId}/delete`
Deletes a mail. Only the recipient may delete.
Blocked (400) if mail has an unclaimed attachment.

**Request** `{ "characterId": 7 }`

**200 OK** (empty body)

- **400** if mail has an unclaimed attachment
- **401** if token missing/invalid
- **404** if mail not found or `characterId` is not the recipient

---

## 17. Ranking endpoints (RANKING lane, V18)

All require `Authorization: Bearer <token>` (inline bearer check via `TokenHelper`).
Errors use the standard envelope `{"error":{"code","message"}}`.

Boards: `wealth` | `vintner` | `guild`. Each board returns up to 20 entries.

`RankEntry` shape:
```json
{ "rankPos": 1, "subjectId": 5, "subjectName": "Lasha", "score": 100.0 }
```
`rankPos` = 1-based position (1 = best). `subjectId` = character id (wealth/vintner) or guild id (guild).

### GET `/api/ranking/wealth`
Returns the live top-20 characters sorted by `walletGel` descending.

**200 OK** → `RankEntry[]`
- **401** if token missing/invalid

### GET `/api/ranking/vintner`
Returns the live top-20 characters sorted by their best `CellarItem.quality` descending.

**200 OK** → `RankEntry[]`
- **401** if token missing/invalid

### GET `/api/ranking/guild`
Returns the live top-20 guilds sorted by member count descending, tie-broken by `treasuryGel` descending. Returns an empty list if no guilds exist.

**200 OK** → `RankEntry[]`
- **401** if token missing/invalid

### GET `/api/ranking/me?board=&characterId=`
Returns the caller's rank + score on the given board (auth + ownership: `characterId` must be owned by the token's account).

Valid `board` values: `wealth`, `vintner`. Returns `rankPos=0` if the character is not on the board (e.g. no cellar items for vintner).

**200 OK** → `RankEntry` (with `rankPos=0` if not on board)
- **400** if `board` is not `wealth` or `vintner`
- **401** if token missing/invalid
- **404** if `characterId` not owned by the token's account

### POST `/api/ranking/snapshot`
Computes and persists the current board as `RankingSnapshot` rows. Returns the persisted rows.

**Request**
```json
{ "board": "wealth" }
```
`board` must be one of: `wealth`, `vintner`, `guild`.

**200 OK** → `RankingSnapshot[]`
```json
[{
  "id": 1,
  "board": "WEALTH",
  "subjectId": 5,
  "subjectName": "Lasha",
  "score": 100.0,
  "rankPos": 1,
  "simDay": 0,
  "createdAt": 1750000000000
}]
```
- **400** if `board` is missing or unknown
- **401** if token missing/invalid

---

## 18. Achievement endpoints (ACHIEVEMENT lane, V21)

All require `Authorization: Bearer <token>` (inline bearer check via `AccountTokenService`).
Errors use the standard envelope `{"error":{"code","message"}}`.

### GET `/api/achievement/catalog`
Returns all static achievement definitions. Auth validated; no character ownership check.

**200 OK** → `AchievementDefinition[]`
```json
[
  {
    "id": "first_estate",
    "title": "Lord of the Soil",
    "description": "...",
    "rewardGel": 50.0,
    "rewardGoodTypeId": "saperavi_cuttings_certified",
    "rewardGoodQty": 5.0
  }
]
```
- **401** if token missing/invalid

Six achievements: `first_estate` (50 GEL + saperavi_cuttings_certified×5), `first_qvevri` (75 GEL + qvevri_300l×1), `master_vintner` (150 GEL + copper_sulfate×10), `wealthy_merchant` (200 GEL, no goods), `guild_founder` (100 GEL + saperavi_cuttings_certified×10), `globetrotter` (125 GEL, no goods).

### GET `/api/achievement/{characterId}`
Returns all unlocked `PlayerAchievement` rows for the character.

**200 OK** → `PlayerAchievement[]`
```json
[
  {
    "id": 1,
    "characterId": 5,
    "achievementId": "first_estate",
    "unlockedDay": 0,
    "createdAt": 1750000000000
  }
]
```
- **401** if token missing/invalid
- **404** if character not found or not owned by the token's account

### POST `/api/achievement/{achievementId}/unlock`
Unlocks an achievement for the character and grants the one-time reward.

**Idempotent guard:** if already unlocked → 400 `ALREADY_UNLOCKED` (no double reward). v1 grants on demand (no criteria check).

**Request**
```json
{ "characterId": 5 }
```

**200 OK** → `PlayerAchievement` (same shape as above)

- **400** `ALREADY_UNLOCKED` if the achievement was already unlocked for this character
- **401** if token missing/invalid
- **404** if `achievementId` is unknown, or `characterId` not owned by the token's account

---

## 19. Chat endpoints (CHAT lane, V20)

All require `Authorization: Bearer <token>` (inline bearer check via `TokenHelper`).
Errors use the standard envelope `{"error":{"code","message"}}`.

Channel formats:
- `"GLOBAL"` — server-wide public channel; any authenticated character may post or read.
- `"REGION:{REGION}"` — e.g. `"REGION:KAKHETI"`; character's `homeRegion` must match.
- `"GUILD:{guildId}"` — e.g. `"GUILD:7"`; character must be a guild member.
- `"DM:{a}:{b}"` — direct message; `a` and `b` are character ids sorted ascending (e.g. `"DM:3:9"`).

### POST `/api/chat/send`
Sends a message to a channel. Validates posting rights and persists the message.

**Request**
```json
{ "characterId": 5, "channel": "GLOBAL", "body": "Hello!" }
```

**200 OK** → `ChatMessage`
```json
{
  "id": 1,
  "channel": "GLOBAL",
  "senderCharacterId": 5,
  "senderName": "Lasha",
  "bodyText": "Hello!",
  "createdAt": 1750000000000
}
```
- **400** if `body` is blank or exceeds 500 characters, or `REGION` mismatch
- **401** if token missing/invalid
- **403** if character is not a member of a `GUILD` channel or not a participant in a `DM` channel
- **404** if `characterId` not owned by the token's account

### GET `/api/chat/{channel}?characterId=&sinceId=`
Returns messages for the channel.

`characterId` is required for `REGION`, `GUILD`, and `DM` channels; optional for `GLOBAL`.
If `sinceId` is provided, returns only messages with `id > sinceId` in ascending order (polling).
Otherwise returns the newest 50 messages.

**200 OK** → `ChatMessage[]`

- **401** if token missing/invalid
- **403** if character is not authorised to read the channel
- **404** if `characterId` not owned by the token's account

**DM channel helper:** to build the canonical DM channel key from two character ids `a` and `b`, sort ascending: `"DM:{min(a,b)}:{max(a,b)}"`.

---

## 20. Contest endpoints (CONTEST lane, V22)

All require `Authorization: Bearer <token>` (inline bearer check via `TokenHelper`).
Errors use the standard envelope `{"error":{"code","message"}}`.

Contest `contestStatus` values: `OPEN` | `JUDGED`.

Judging is **lazy** — triggered on `GET /api/contest/open` and `POST /api/contest/{id}/judge`
when `currentDay >= endDay`. No scheduler is used.

**v1 prize note:** `prizeGel` is NPC-funded — the contest creator is never debited at creation
time. Winner-takes-all: placement-1 character receives the full `prizeGel`.

### POST `/api/contest/create`
Creates an OPEN contest. Any authenticated account may create.

**Request**
```json
{ "name": "Grand Saperavi Cup", "description": "Best aged Saperavi wins",
  "durationDays": 5, "prizeGel": 100.0 }
```
`endDay = currentAbsoluteDay + durationDays`.

**200 OK** → `Contest`
```json
{
  "id": 1, "name": "Grand Saperavi Cup", "description": "Best aged Saperavi wins",
  "endDay": 5, "prizeGel": 100.0, "contestStatus": "OPEN", "createdAt": 1750000000000
}
```
- **400** if `durationDays < 1` or `prizeGel <= 0`
- **401** if token missing/invalid

### GET `/api/contest/open`
Returns all OPEN contests, lazily judging any past their `endDay` first.

**200 OK** → `Contest[]`

- **401** if token missing/invalid

### POST `/api/contest/{id}/enter`
Enters a character's cellar item into an OPEN contest. Snapshots `CellarItem.quality`
as `qualityScore` at entry time — post-entry changes to the original item do not affect results.

**Request**
```json
{ "characterId": 5, "cellarItemId": 1 }
```

**200 OK** → `ContestEntry`
```json
{
  "id": 1, "contestId": 1, "characterId": 5, "cellarItemId": 1,
  "qualityScore": 78.3, "placement": null, "createdAt": 1750000000000
}
```
- **400** if contest is not OPEN, `currentDay >= endDay`, or character has already entered
- **401** if token missing/invalid
- **404** if contest not found, or `cellarItemId` not owned by `characterId`

### POST `/api/contest/{id}/judge`
Judges the contest. Requires `currentDay >= endDay`.
Idempotent — already-JUDGED contests return their current state without error.

**Request** `{}` (empty body; any authenticated account may trigger judging)

Judging algorithm:
1. Load all entries.
2. Sort by `qualityScore` descending; tie-break by entry `id` ascending (deterministic).
3. Assign `placement` 1..n.
4. Award `prizeGel` to placement-1 character via `adjustWallet(+prizeGel)` (winner-takes-all).
5. Mark `contestStatus = JUDGED`.

**200 OK** → `Contest` (with `contestStatus = "JUDGED"`)

- **400** if contest has not yet expired (`currentDay < endDay`)
- **401** if token missing/invalid

### GET `/api/contest/{id}/results`
Returns all entries for the contest with placements (set after judging).

**200 OK** → `ContestEntry[]`

- **401** if token missing/invalid
- **404** if contest not found

---

## 21. Research endpoints (RESEARCH lane, V23)

All require `Authorization: Bearer <token>` (inline bearer check via `AccountTokenService`).
Errors use the standard envelope `{"error":{"code","message"}}`.

A per-character tech tree: pay GEL, wait sim-days, unlock a bonus.
Research status values: `RESEARCHING` | `COMPLETE`.
Lazy completion: on any GET, RESEARCHING rows with `currentDay >= readyDay` are flipped to COMPLETE.

### GET `/api/research/catalog`
Returns all 6 static research node definitions. Auth validated; no character ownership check.

**200 OK** → `ResearchNode[]`
```json
[
  {
    "id": "improved_pruning",
    "title": "Improved Pruning Techniques",
    "description": "...",
    "costGel": 30.0,
    "durationDays": 3,
    "prereqId": null,
    "bonusType": "YIELD_BONUS",
    "bonusValue": 0.08
  }
]
```

Six nodes — cost / duration / prereq / bonus:

| id                  | costGel | durationDays | prereqId       | bonusType             | bonusValue |
|---------------------|---------|-------------|----------------|-----------------------|------------|
| `improved_pruning`  | 30.0    | 3           | none           | `YIELD_BONUS`         | 0.08       |
| `temp_control`      | 45.0    | 4           | none           | `FERMENT_SPEED`       | 0.15       |
| `rootstock_program` | 60.0    | 6           | none           | `PHYLLOXERA_RESIST`   | 0.50       |
| `logistics_network` | 40.0    | 3           | none           | `SHIPPING_COST_REDUCTION` | 0.20   |
| `cold_soak`         | 55.0    | 5           | `temp_control` | `QUALITY_BONUS`       | 0.06       |
| `barrel_program`    | 70.0    | 7           | `temp_control` | `AGING_QUALITY`       | 0.10       |

- **401** if token missing/invalid

### GET `/api/research/{characterId}`
Returns all `PlayerResearch` rows for the character.
Lazily completes any RESEARCHING rows whose `readyDay <= currentAbsoluteDay` on read.

**200 OK** → `PlayerResearch[]`
```json
[
  {
    "id": 1,
    "characterId": 5,
    "nodeId": "improved_pruning",
    "researchStatus": "RESEARCHING",
    "startDay": 0,
    "readyDay": 3,
    "createdAt": 1750000000000
  }
]
```

- **401** if token missing/invalid
- **404** if character not found or not owned by the token's account

### POST `/api/research/{nodeId}/start`
Starts research on the given node for the character.

**Request**
```json
{ "characterId": 5 }
```

**200 OK** → `PlayerResearch` (with `researchStatus = "RESEARCHING"`, `readyDay = currentDay + durationDays`)

- **400** if the node is already started or complete for this character
- **400** `PREREQ_NOT_MET` if the node's `prereqId` is not COMPLETE for this character
- **400** `INSUFFICIENT_FUNDS` if wallet < `costGel`
- **401** if token missing/invalid
- **404** if `nodeId` is unknown, or `characterId` not owned by the token's account

---

## 22. Travel endpoints (TRAVEL lane, V26)

All require `Authorization: Bearer <token>` (inline bearer check via `TokenHelper`).
Errors use the standard envelope `{"error":{"code","message"}}`.

Travel-time model: `arriveDay = departDay + max(1, ceil(haversineKm(from, to) / 40))`.
`40 km/day` = laden cart speed across Georgian mountain roads.
Region coordinates are WGS84 from `WorldCatalog` (see §9).
GEL cost per trip: **5 GEL flat** (deducted at departure; `400 INSUFFICIENT_FUNDS` if low).

`travelStatus` values: `SETTLED` | `TRAVELLING`.

### GET `/api/travel/{characterId}`
Returns the character's current location. Lazy-creates at the character's `homeRegion`
(SETTLED) on first access. Also lazily resolves arrival: if TRAVELLING and
`currentAbsoluteDay >= arriveDay`, flips `currentRegion = destRegion`, `travelStatus = SETTLED`,
`destRegion = null`.

**200 OK**
```json
{
  "id": 1,
  "characterId": 5,
  "currentRegion": "KAKHETI",
  "travelStatus": "SETTLED",
  "destRegion": null,
  "departDay": 0,
  "arriveDay": 0,
  "createdAt": 1750000000000
}
```
- **401** if token missing/invalid
- **404** if character not found or not owned by the token's account

### POST `/api/travel/{characterId}/depart`
Initiates travel from the character's current region to the given destination.

**Request**
```json
{ "toRegion": "KARTLI" }
```

**200 OK** → `CharacterLocation` (same shape as above, `travelStatus = "TRAVELLING"`)

- **400** `ALREADY_TRAVELLING` if character is already TRAVELLING
- **400** if `toRegion` is unknown or equals the character's current region
- **400** `INSUFFICIENT_FUNDS` if wallet < 5 GEL
- **401** if token missing/invalid
- **404** if character not found or not owned by the token's account

## 23. Prestige endpoints (PRESTIGE lane, V28)

Prestige represents a character's social standing in the QVEVRI world.
The title ladder is a static ordered sequence of Georgian-flavoured noble titles.
In v1 prestige is awarded via the `/award` endpoint (for client/dev); automatic
feeds from contests/festivals/wealth milestones are deferred to the integration pass.

Title ladder (ascending thresholds):

| Title | Threshold |
|---|---|
| GLEKHI (peasant) | 0 |
| MEVENAKHE (vine-keeper) | 50 |
| MEURNE (steward) | 200 |
| AZNAURI (noble) | 600 |
| TAVADI (lord) | 1 500 |

`titleRank` is always the highest title whose threshold ≤ total prestige.
Prestige never decreases.

### GET `/api/prestige/ladder`
Returns the full title ladder. No auth required.

**200 OK**
```json
[
  { "title": "GLEKHI",    "threshold": 0    },
  { "title": "MEVENAKHE", "threshold": 50   },
  { "title": "MEURNE",    "threshold": 200  },
  { "title": "AZNAURI",   "threshold": 600  },
  { "title": "TAVADI",    "threshold": 1500 }
]
```

### GET `/api/prestige/{characterId}`
Returns the prestige profile. Lazy-creates at prestige=0 / title=GLEKHI on first access.

**200 OK**
```json
{
  "prestige":       60,
  "title":          "MEVENAKHE",
  "nextTitle":      "MEURNE",
  "prestigeToNext": 140
}
```
- `nextTitle` is `null` and `prestigeToNext` is `0` when the character has reached TAVADI.
- **401** if token missing/invalid
- **404** if character not found or not owned by the token's account

### POST `/api/prestige/{characterId}/award`
Awards prestige to the character and returns the updated profile view.

**Request**
```json
{ "amount": 60, "reason": "contest victory" }
```

**200 OK** → same shape as GET `/{characterId}` response above

- **400** `BAD_REQUEST` if `amount` is missing, not numeric, or ≤ 0
- **401** if token missing/invalid
- **404** if character not found or not owned by the token's account
