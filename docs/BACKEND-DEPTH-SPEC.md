# BACKEND-DEPTH-SPEC — wiring the loop, winemaking, land, player economy (v1)

Four player-requested threads, built as three disjoint parallel lanes. Pre-assigned
Flyway migrations to avoid collisions: **WINE=V6, LAND=V7, TRADE=V8**.

## Hard rules (all lanes)
- **Additive + gated.** All existing 187 tests must stay green. New behaviour only
  triggers when the player opts in (selects a vessel, uses certified stock, accepts
  a contract…). The default / no-equipment path must reproduce today's output
  byte-for-byte. The Kakheti+Saperavi default vineyard as current tests construct it
  must be unchanged.
- **Determinism preserved.** No wall-clock or randomness in the sim path; vineyard
  state stays a pure replay. Time-based winemaking uses the world clock's sim-day,
  not real time.
- **No secrets; constructor injection; standard error envelope; inline bearer auth**
  like the other controllers. Add `permitAll` matchers for any new `/api/**` prefix
  in SecurityConfig (WINE may add `/api/wine/**`, LAND `/api/land/**`, TRADE
  `/api/trade/**`).
- **Disjoint ownership (critical):** only the WINE lane may edit `CellarItem.java`,
  `WinePricer`, and the estate harvest flow. LAND and TRADE are NEW packages and must
  NOT edit `Vineyard.java`, `CellarItem.java`, `WinePricer`, goods, or profession —
  they call existing repositories/services (`CharacterService.adjustWallet`,
  `CellarItemRepository`, `GoodsService`) instead.

---

## LANE WINE — `com.game.wine` (+ gated edits to estate/cellar/econ). Migration V6.
Covers "wire the loop (§6)" + "winemaking depth". This is the biggest lane; deliver
a solid, gated v1, not an exhaustive sim.

§6 integration — each effect OFF by default, ON only when the player supplies the good:
- **Vessel** — when fermenting, the player may select a VESSEL `OwnedGood`
  (qvevri 300/500/1000 L, oak barrel, steel tank). Material/size shifts style/quality.
  No vessel selected → current behaviour.
- **Certified vines** — if certified VINE_STOCK was used at planting, set phylloxera
  resistance / ownRoots accordingly; otherwise the existing default flag stands.
- **Press tier** — a press `OwnedGood` nudges extraction/quality at harvest; none → today.
- **Sprays consumed** — when a copper/sulfur lever > 0 is active for a season, draw the
  matching INPUT good down from inventory via `GoodsService` (a money/goods sink). v1:
  consume if present; do NOT alter sim output (keeps determinism + tests green).
- **WinePricer reads WineGrade** — if a certified `WineGrade` exists for a cellar item,
  apply a quality premium; absent → current price.

Winemaking depth v1 (additive — do NOT replace the existing instant harvest path):
- A **fermentation step**: harvesting may start a fermentation in a chosen vessel that
  resolves over N sim-days into wine; **cellar aging** then improves quality over sim-time
  up to a cap (vintage gets better with age, with diminishing returns). Add
  `CellarItem` fields for this (e.g. fermentation state, started/ready sim-day,
  aging-from day) — nullable, defaulting so existing rows/tests are unaffected.
- Endpoints under `/api/wine/**`: start fermentation `{cellarItemId|harvest, vesselGoodId}`,
  status, bottle. The existing `harvest` endpoint keeps working exactly as today.
- Owns: NEW `com.game.wine` package; gated edits to estate harvest integration,
  `CellarItem.java` (additive nullable fields), `WinePricer`. Migration **V6**.
- Tests: vessel changes style only when selected; grade premium applied only when graded;
  fermentation resolves deterministically over sim-days; aging improves up to cap;
  DEFAULT harvest path byte-identical. Keep all current tests green.

## LANE LAND — `com.game.land` (NEW package only). Migration V7.
Owned estates/parcels anchored to real coordinates (builds on the geo'd regions).
- `Parcel` @Entity (`land_parcels`): id, ownerCharacterId, name, region (enum name),
  latitude, longitude (within the region; jitter around the region centre from
  `RegionInfo` so estates cluster near the real town), sizeHectares, optional
  vineyardId link, createdAt. Coordinates must stay inside Georgia's box
  (lat 41.0–43.6, lon 40.0–46.8).
- Endpoints `/api/land/**`: claim/buy a parcel (wallet sink via
  `CharacterService.adjustWallet`), list my parcels, detail, (optional) attach an
  existing vineyard to a parcel by storing its id on the parcel.
- Does NOT edit `Vineyard.java` — the link lives on `Parcel`. Migration **V7**.
- Tests: buy debits wallet + creates parcel at in-region coords; insufficient funds 400;
  list/detail ownership enforced; coordinates inside the bounding box.
- Owns: `com.game.land` only + V7 + tests.

## LANE TRADE — `com.game.trade` (NEW package only). Migration V8.
Player-to-player economy: the grower→winemaker→merchant value chain.
- `TradeOffer` @Entity (`trade_offers`): id, sellerCharacterId, buyerCharacterId
  (nullable until accepted), kind (e.g. GOODS or CELLAR_ITEM), reference
  (goodTypeId or cellarItemId), quantity, priceGel, status
  (OPEN/ACCEPTED/CANCELLED), createdAt.
- Endpoints `/api/trade/**`: create offer, list open offers, accept (atomic:
  `CharacterService.adjustWallet` buyer−/seller+, then transfer the item — reassign
  `CellarItem.characterId` via `CellarItemRepository`, or move a `GoodsService` stack —
  guarding insufficient funds/ownership), cancel.
- (Optional) a simple **hauling** delay before delivery completes, using the world clock.
- Does NOT edit `CellarItem.java`, goods, or character files — uses their repos/services.
  Migration **V8**.
- Tests: create + list; accept moves money and item ownership atomically; can't accept
  your own / already-accepted offer; insufficient funds 400; ownership enforced.
- Owns: `com.game.trade` only + V8 + tests.

---

## Deferred (next round, after these are green)
- Realtime multiplayer presence (waits for Unreal).
- Progression/XP/reputation, NPC/quest backend, regional weather events on whole
  vintages, auth hardening / token scoping, anti-cheat / server-authority validation.
