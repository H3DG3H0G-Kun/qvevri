# REGIONS, TRAVEL & CAREERS SPEC (v1)

Three lanes that turn the world from "Kakheti-only, careers cosmetic, no travel" into a
living map — built to docs/CONTENT-BIBLE.md. TRAVEL = migration **V26**; CAREERS and
REGIONS need no migration (career = static catalog; regions = sim wiring).

## Hard rules (all lanes)
- All existing tests stay green. Inline bearer auth (`AccountTokenService`) + ownership
  (`CharacterService.getOwned`). Matchers for `/api/travel|career/**` already added.
- Time-based effects resolve LAZILY off the world clock. Deterministic; no wall-clock, no RNG in sim.
- H2 dev/test via ddl-auto — reserved-word-safe columns. Tests: response maps used with
  `containsKey` declared `Map<String,Object>` (NOT `Map<?,?>`); 4xx reads `String.class`.
- Disjoint ownership: TRAVEL = `com.game.travel` only; CAREERS = `com.game.career` only;
  REGIONS = sim region-wiring + estate replay region path only. No lane touches another's files.

## LANE TRAVEL — `com.game.travel`. Migration V26.
Players move between regions; travel takes sim-days over real geo distance.
- `CharacterLocation` @Entity (`character_locations`): id, characterId (unique), currentRegion
  (String), travelStatus ("SETTLED"|"TRAVELLING"), destRegion (nullable), departDay (long),
  arriveDay (long), createdAt. Repo: findByCharacterId.
- Travel time: reuse `com.game.logistics.GeoUtil` (read-only) — `travelDays(from,to)` over the
  region lat/long (or compute haversine the same way). Optional GEL cost per trip (e.g. small flat
  or distance-scaled; keep modest). Lazy-create the location at the character's `homeRegion`
  (SETTLED) on first access.
- `TravelService` + `TravelController` `/api/travel/**` (auth + ownership):
  - `GET /api/travel/{characterId}` → location; lazily arrive first: if TRAVELLING and
    currentDay >= arriveDay → currentRegion = destRegion, status SETTLED, clear dest.
  - `POST /api/travel/{characterId}/depart` `{toRegion}` → must be SETTLED (else 400 already
    travelling); toRegion valid + != current (400); compute arriveDay = currentDay + travelDays;
    optional cost via adjustWallet (400 insufficient); set TRAVELLING. Return location.
- Tests: GET lazy-creates at homeRegion SETTLED; depart sets TRAVELLING with arriveDay>departDay;
  depart while travelling → 400; unknown/again-same region → 400; after advancing the clock past
  arriveDay, GET shows SETTLED at destRegion; farther region → more days. Owns `com.game.travel` + V26 + tests.

## LANE CAREERS — `com.game.career` (NEW package, additive). No migration.
The pro/con economic identity of each of the 9 careers as DATA + lookup. (Applying these
multipliers to real prices/yield/shipping is the deferred integration pass — this lane only
defines and exposes them, so it stays additive and safe.)
- Static `CareerProfileCatalog`: for each `world.CareerType`, a `CareerProfile` (record) with the
  multipliers from the CONTENT-BIBLE career specs, e.g.: sellMarginMult (Merchant +0.20),
  yieldMult (Grower +0.15), qualityMult (Winemaker +0.10), shippingDiscount (Hauler 0.30),
  buyDiscount (Négociant 0.20), craftDiscount (Cooper), gradeFeeIncome (Enologist),
  brokerCommission (Broker), cuttingMargin (Nurseryman) — plus a short `summary` and the
  one-line `pro` / `con` strings. Use 0.0 / 1.0 neutral defaults for fields a career doesn't use.
  Provide all() + of(careerType).
- `CareerController` `/api/career/**` (auth):
  - `GET /api/career/catalog` → all 9 CareerProfiles.
  - `GET /api/career/{characterId}` → the profile for that character's career (ownership).
- Tests: catalog returns 9 with the expected headline multipliers (e.g. Merchant sellMargin,
  Grower yield); character endpoint returns the profile matching their careerType; ownership enforced.
  Owns `com.game.career` only + tests. (Reads CareerType + CharacterService; edits nothing else.)

## LANE REGIONS — per-region simulation (sim wiring + estate replay). No migration.
Make all 7 regions simulate distinctly using the real climate deltas; KAKHETI stays byte-identical.
- The vine sim currently hardcodes a Kakheti site and ignores region. Wire it so a vineyard's
  REGION drives: (a) its climate offset (the existing `RegionClimates` deltas), and (b) a per-region
  site profile (create `RegionSiteProfiles`: altitude/slope/aspect/soil per region — Kakheti = the
  current canonical site exactly). Apply these in the weather/sim generation used by
  `VineyardReplayService` / the estate harvest path.
- **CRITICAL byte-identical rule:** KAKHETI must use the baseline (zero) climate offset AND the
  exact current canonical site, so every existing sim, threats, appellation, and estate test
  (which use Kakheti/default) produces IDENTICAL output. Only non-Kakheti regions diverge.
- Add tests: two vineyards with the same seed+variety in DIFFERENT regions produce DIFFERENT
  harvest output (brix/health/volume); a KAKHETI vineyard reproduces today's output exactly
  (assert against a known prior value or a same-construction baseline). All existing sim/estate
  tests must stay green.
- Owns: the sim region-wiring (RegionClimates application + new RegionSiteProfiles) + the estate
  replay region path + new tests. Do NOT change variety calibration, the threat engine math, or
  appellation rules — only inject region climate+site. No migration (sim is computed).

## Deferred (integration pass, sequential from green)
- Apply CAREER multipliers to real market/sell/yield/shipping; charge travel for cross-region market
  access; region reputation. Plus all earlier deferred wiring. Cross-cutting — sequential, not parallel.
