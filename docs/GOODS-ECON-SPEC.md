# GOODS-ECON-SPEC — Goods, equipment, professions & multi-season (v1)

A big expansion across three themes the player asked for, built as parallel
foundations now. The deep cross-wiring (equipment GATING the grow/make pipeline)
is an explicit **follow-up integration pass** after these land green — see §6.

Shared rules: bearer token → account via `AccountTokenService` (endpoints are
permitAll at security level; validate inline like estate/market). Wallet moves via
`com.game.character.CharacterService.adjustWallet`. Deterministic where simulated.
H2 dev/test via ddl-auto; prod via Flyway — **migration versions are pre-assigned
per lane to avoid collisions: G=V3, M=V4, P=V5.** Keep all current tests green.

---

## LANE G — Goods, equipment & the bazaar (`com.game.goods`)
The economy beyond wine: buy the vines, vessels, machines, tools and inputs needed
to grow/tend/make/sell.

- `GoodCategory` enum: `VINE_STOCK, VESSEL, EQUIPMENT, INPUT, TOOL`.
- `GoodType` (static catalog entry): id (stable string, e.g. `qvevri_500l`,
  `saperavi_cuttings_certified`, `basket_press`, `copper_sulfate`, `pruning_shears`),
  category, displayName, basePrice (GEL), consumable (bool), and free-form
  `attributes` (Map<String,Double/String>) for later pipeline hooks (e.g. vessel
  capacityL, press qualityTier, vine certified/ownRoots, input potency).
- `GoodsCatalog`: static table of ~15–25 goods spanning all categories, with
  realistic Georgian flavor (qvevri sizes 300/500/1000 L; oak barrel; steel tank;
  certified vs. own-root cuttings for several varieties; basket press / mechanical
  crusher-destemmer; copper sulfate, sulfur, bird netting, cover-crop seed; pruning
  shears, hoe). Prices spread so they're meaningful sinks.
- `OwnedGood` @Entity (`owned_goods`): id, characterId, goodTypeId, quantity (double
  — supports consumable stacks), condition01 (1.0 new), acquiredAt. Repo with
  findByCharacterId, findByCharacterIdAndGoodTypeId.
- Endpoints:
  - `GET /api/goods/catalog` → all GoodTypes (permitAll, no auth needed).
  - `GET /api/goods/{characterId}` → that character's OwnedGood[] (auth + ownership).
  - `POST /api/shop/buy` `{characterId, goodTypeId, quantity}` → NPC bazaar sells at
    `basePrice × quantity`; debit wallet (400 if insufficient), add/stack OwnedGood;
    return the OwnedGood + new wallet. (NPC vendor = money sink + enables play.)
  - `POST /api/shop/sell` `{characterId, ownedGoodId, quantity}` → sell back at a
    fraction (e.g. 0.5×basePrice) to the NPC; remove/decrement; credit wallet.
- Flyway **V3__owned_goods.sql**. Tests: catalog returned; buy debits + grants;
  insufficient funds 400; sell credits + decrements; ownership enforced.
- Ownership: ONLY `com/game/goods/**` + `db/migration/V3__owned_goods.sql` + tests.

## LANE P — Professions & crafting (`com.game.profession`)
Careers that DO something (the unbundled value chain). v1 = capability framework +
two concrete actions. Depends on Lane G goods (code against this spec's GoodType /
OwnedGood / a `GoodsService.grant(characterId, goodTypeId, qty)` G exposes) and on
`com.game.market.CellarItem` + `CharacterService`.
- `CareerCapability`: map each `world.CareerType` → what it may do + a one-time
  **starter kit** (goods + maybe GEL). Grant the kit **lazily/idempotently** in a
  profession service (first call, keyed by characterId) — do NOT edit the character
  package.
- Two concrete profession actions (endpoints under `/api/profession/**`, auth+ownership):
  1. **Cooper craft** — `POST /api/profession/cooper/craft` `{characterId, recipeId}`:
     consume INPUT goods (e.g. clay/staves from the catalog) → produce a VESSEL
     OwnedGood (a qvevri/barrel) the cooper can then sell on the bazaar. Career-gated
     to COOPER. Static recipe table.
  2. **Lab grade** — `POST /api/profession/lab/grade` `{characterId, cellarItemId}`:
     ENOLOGIST inspects a bottle and issues a grade/certification. Store a
     `WineGrade` @Entity (`wine_grades`: id, cellarItemId, graderCharacterId, score,
     certified, createdAt) in this package. (WinePricer reading the grade for a
     premium is a §6 follow-up — just persist + return it now.)
- `GET /api/profession/capabilities` → career→capabilities catalog.
- Flyway **V5__wine_grades.sql**. Tests: starter kit idempotent; cooper craft
  consumes+produces and is career-gated (non-cooper → 403); lab grade persists;
  insufficient inputs → 400.
- Ownership: ONLY `com/game/profession/**` + `db/migration/V5__wine_grades.sql` +
  tests. Reference G's goods + market CellarItem + CharacterService; do not edit them.

## LANE M — Multi-season vines & per-day tending (`com.game.estate` only)
Vines live across years and respond to dated actions.
- **Vine age / establishment**: add `plantedYear` (or derive age from a planting
  absolute-day) to `Vineyard`. In `VineyardReplayService`, apply an **establishment/
  age yield curve** to the computed yield: year-1 ~30%, year-2 ~65%, years 3–25 full,
  then gentle decline; very old vines slightly higher quality. Keep this an estate-side
  multiplier on yield — **do NOT modify `com/game/sim/**`** (the calibrated vine sim
  stays untouched, so all sim tests stay green). Mature default vines (as today's
  tests construct them, age≥3) must reproduce current output.
- **Per-day action timeline**: add an optional `VineyardAction` list (entity
  `vineyard_actions`: id, vineyardId, dayOfYear, actionType, value, year) applied
  during replay on the matching day — so an emergency spray on day 180 only affects
  days ≥180 (causal, deterministic). The season-plan levers from MANAGE-SPEC remain
  the baseline; actions override from their day forward. Endpoint
  `POST /api/vineyards/{id}/action` `{characterId, dayOfYear, actionType, value}`.
- Flyway **V4__vineyard_multiseason.sql** (planting year column + vineyard_actions).
- Tests: age-1 vine yields < mature; same plan, action applied mid-season changes only
  later days; determinism (same inputs → same view). Default mature path unchanged.
- Ownership: ONLY `com/game/estate/**` + `db/migration/V4__vineyard_multiseason.sql`
  + tests. Do NOT touch sim/**, goods/**, profession/**, auth, client.

## LANE C — Client UI for all of the above (`client/Assets/**` only)
- **Bazaar/Shop panel**: browse `GET /api/goods/catalog` by category; buy (POST
  /api/shop/buy) with wallet feedback; sell back.
- **Equipment/Inventory panel**: list `GET /api/goods/{characterId}` (vessels, tools,
  inputs, vines) with quantities/condition.
- **Profession panel**: show capabilities; cooper craft button; lab grade a chosen
  cellar bottle; claim starter kit.
- **Vineyard depth**: surface vine age/establishment and a per-day action button
  (e.g. "spray now") on the plot/Vineyards tab.
- Reuse WebMmoApi/session/CoroutineRunner; IMGUI fine; robust offline. Match wire
  names from this spec exactly.

---

## §6 — Deferred integration pass (NEXT, after these are green)
Wire goods into the sim/pipeline, once both sides exist:
- Owning/selecting a **qvevri/vessel** gates or improves fermentation at harvest;
  vessel material/size shifts style/quality.
- **Certified vine stock** purchased & used at planting sets phylloxera resistance
  (own-roots vs grafted) instead of the current flag default.
- **Press/crusher tier** nudges extraction/quality; **sprays/nets** consumed from
  inventory when the corresponding lever is used (tending draws down INPUT goods).
- **WinePricer** reads `WineGrade` for a certified-quality premium.
This pass is intentionally separate to avoid coupling four concurrent lanes.
