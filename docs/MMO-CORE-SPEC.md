# MMO-CORE-SPEC — Persistent Identity, Characters, Regions & Market (v1)

The MMORPG backbone: real accounts, characters (career type + home region),
a persistent cellar, and a working player market. Source of truth for the
entities, endpoints, and client flow. Lanes implement these EXACTLY; flag
mismatches to the architect.

> **Non-breaking rule:** the existing movement slice (`/api/auth/login`,
> `/api/sessions/**`, `/ws/game`, `UserStore`, `TokenService`) and all 89 green
> tests MUST keep working. This is an ADDITIVE system in new packages. Do not
> modify the existing auth/session/ws code. Verify with `mvn test`.

## 0. Persistence
- Reuse the existing H2 setup (dev: `jdbc:h2:file:./data/game`, `ddl-auto=update`;
  test profile: H2 mem). New `@Entity` classes auto-create tables — no config
  change needed. (Postgres + Flyway is a later hardening; note it, don't do it.)
- JPA entities + Spring Data repositories. Constructor injection. No secrets.

## 1. Auth for the new system (self-contained, non-breaking)
- New `AccountTokenService`: in-memory `ConcurrentHashMap<token, accountId>`
  (mirrors the existing TokenService pattern; independent of it).
- New endpoints are `permitAll` at the Spring Security level (add the path
  matchers to SecurityConfig — additive lines only). Each protected endpoint
  validates `Authorization: Bearer <token>` itself via `AccountTokenService`,
  returning 401 `{ "error": { "code": "UNAUTHORIZED", ... } }` if missing/invalid.
- Passwords hashed with BCrypt (`spring-boot-starter-security` already present).

## 2. Entities (fields are the contract)

```
Account            (com.game.account)
  id Long PK auto · email (unique) · username (unique) · passwordHash · createdAt(long)

Character          (com.game.character)
  id Long PK auto · accountId Long (FK) · name · careerType (enum CareerType)
  · homeRegion (enum Region) · rank (enum Rank, default GLEKHI)
  · walletGel double (start 100.0) · createdAt(long)

CellarItem         (com.game.market)   // persisted inventory item
  id Long PK auto · characterId Long (FK) · itemType (enum, mirror econ.ItemType)
  · quantity double · quality double · vintageYear int · style String
  · appellationOk boolean · label String · escrowed boolean(default false) · createdAt

MarketListing      (com.game.market)
  id Long PK auto · sellerCharacterId Long · cellarItemId Long · askPrice double
  · status (enum: ACTIVE, SOLD, CANCELLED) · createdAt

TradeRecord        (com.game.market)
  id Long PK auto · buyerCharacterId Long · sellerCharacterId Long · cellarItemId Long
  · price double · createdAt
```

## 3. World data (`com.game.world`)
```
enum Region   { KAKHETI, KARTLI, IMERETI, RACHA_LECHKHUMI, SAMEGRELO, GURIA_ADJARA, MESKHETI }
enum CareerType { GROWER, WINEMAKER, ENOLOGIST, NEGOCIANT, BROKER, COOPER, NURSERYMAN, HAULER, MERCHANT }
enum Rank     { GLEKHI, MEVENAKHE, WINEMAKER, HOUSE }   // progression; new chars start GLEKHI
record RegionInfo(Region region, String displayName, String climate, String signatureGrapes, String methodNote)
record CareerInfo(CareerType type, String displayName, String description)
```
Populate `RegionInfo` for all 7 from GDD Part 3 (Kakheti warm/Rkatsiteli+Saperavi/
Kakhetian method; Imereti cooler/Tsolikouri+Tsitska/Imeretian no-stems; etc.).
Populate `CareerInfo` for all 9 from GDD Part 8. NOTE: full per-region sim is
future — only Kakheti/Saperavi is fully simulated today; other regions are
selectable and stored, and the vineyard sim defaults to Kakheti behaviour for now.

## 4. REST endpoints

**Account (permitAll):**
- `POST /api/account/register` `{email,username,password}` → 201 `{accountId, token}`
- `POST /api/account/login` `{username,password}` → 200 `{accountId, token}` · 401 INVALID_CREDENTIALS

**World (permitAll, no auth):**
- `GET /api/world/regions` → `RegionInfo[]`
- `GET /api/world/careers` → `CareerInfo[]`

**Characters (bearer token → account):**
- `POST /api/characters` `{name, careerType, homeRegion}` → 201 Character (must belong to token's account)
- `GET /api/characters` → Character[] for the authenticated account
- `GET /api/characters/{id}` → Character (404 if not owned)

**Cellar (bearer token; verify character belongs to account):**
- `GET /api/cellar/{characterId}` → CellarItem[] (non-escrowed)
- `POST /api/cellar/{characterId}/grow` `{seed,budLoad,pickDay,threats}` → grows a vintage
  via the existing `VineyardService`, stores the resulting bottle as a `CellarItem`
  (via `econ.Item.fromBottle` mapping), returns the new CellarItem + the VineyardYearResult

**Market (bearer token):**
- `GET /api/market` → active listings, each with `askPrice` and a `suggestedPrice`
  computed by `econ.WinePricer` (use a simple `MarketContext` from current supply)
- `POST /api/market/list` `{characterId, cellarItemId, askPrice}` → Listing
  (marks the CellarItem escrowed; verify ownership)
- `POST /api/market/buy` `{characterId, listingId}` → TradeRecord
  (transfer item to buyer's cellar, credit seller wallet, debit buyer wallet,
  set listing SOLD; reject if insufficient funds or self-buy)

All bodies JSON; errors use the standard envelope `{ "error": { "code", "message" } }`.

## 5. Lane ownership
| Lane | Owns (writes only here) |
|------|--------------------------|
| MA identity | `server/.../com/game/account/**`, `com/game/character/**`, `com/game/world/**` + additive SecurityConfig permitAll lines |
| MB market | `server/.../com/game/market/**` (entities, repos, MarketController, CellarController, claim/grow flow) |
| MC client | `client/Assets/Scripts/Net/Account/**`, `Net/Market/**`, `Gameplay/UI/**` (new panels) |
| MQ qa | `server/src/test/java/com/game/{account,character,world,market}/**` |

MB reuses `econ.*` (WinePricer, Item, ItemType) and `vineyard.VineyardService`, and
references `Character` by id (MA owns the entity). MA owns the auth/account/character
layer MB and MC depend on. Architect owns docs + PROGRESS.

## 6. Client flow (MC) — functional UI (IMGUI ok), robust offline
1. **Account screen**: register / login → store token.
2. **Character select**: `GET /api/characters`; "Create new".
3. **Character create**: name + pick CareerType + pick Region (from `/api/world/*`).
4. **Main HUD**: Vineyard (grow → claim to cellar), Cellar (your bottles), Market
   (list a bottle, browse, buy). All via the new endpoints, bearer token attached.
Reuse the existing `WebVineyardApi` pattern (UnityWebRequest→Task). Degrade
gracefully if the server is down (show error, allow retry).

## 7. Determinism / tests (MQ)
- Account: register→login round-trip, duplicate username rejected, bad password 401.
- Character: create with career+region, list returns only own characters, ownership 404.
- World: regions(7) and careers(9) returned.
- Cellar: grow adds a bottle deterministically (same seed → same quality).
- Market: list→buy transfers item + moves money; insufficient funds rejected; self-buy rejected.
- Existing 89 tests still green.

## 8. Versioning
`v1` of the MMO core. Additive within the phase.
