# REGIONS-SPEC — Multi-region & multi-variety simulation (v1)

Make **where you spawn** and **what you plant** matter in the glass. Today the sim
hardcodes Kakheti/Saperavi; this wires real Georgian regions and grapes into the
deterministic pipeline.

> **HARD CONSTRAINT — do not break the 122 green tests.** KAKHETI + SAPERAVI must
> reproduce today's numbers EXACTLY. Achieve this by extracting the current
> hardcoded constants verbatim into the KAKHETI `RegionClimate` and SAPERAVI
> `VarietyProfile` entries, then having the weather/vine code read those profiles.
> Every existing sim/threats/vineyard/estate test must still pass unchanged.

## 1. Enums (extend)
- `com.game.core.data.Region`: extend from `{KAKHETI}` to the 7 GDD regions, names
  matching `com.game.world.Region`: KAKHETI, KARTLI, IMERETI, RACHA_LECHKHUMI,
  SAMEGRELO, GURIA_ADJARA, MESKHETI. (This also fixes the latent bug where planting
  a non-Kakheti vineyard 400s, because estate parses `core.data.Region`.)
- `com.game.core.data.Variety`: extend from `{SAPERAVI}` to a real starter set, e.g.
  SAPERAVI (red), RKATSITELI (white), MTSVANE (white), KISI (white), TSOLIKOURI
  (white), TSITSKA (white), ALEKSANDROULI (red), OJALESHI (red), CHKHAVERI (rosé),
  CHINURI (white). Add a `boolean isWhite()`/color accessor or carry color in the profile.

## 2. New data tables (code, not config)
- `RegionClimate` (per Region): seasonal warmth/GDD offset, rainfall, humidity, frost
  risk — the knobs the weather model already uses internally. KAKHETI = current
  calibrated values verbatim. Differentiate the others **relative to Kakheti**:
  - KARTLI: slightly cooler, drier, continental.
  - IMERETI: cooler, wetter, more humid (higher fungal pressure).
  - RACHA_LECHKHUMI: cool, high-altitude, shorter season.
  - SAMEGRELO: warm, very humid, high rainfall.
  - GURIA_ADJARA: warm, wettest (subtropical), highest humidity.
  - MESKHETI: cool, high-altitude, terraced, short season.
- `VarietyProfile` (per Variety): the phenology/ripening knobs `KakhetiVineSimulator`
  currently hardcodes — GDD-to-stage thresholds, KG_PER_BUD, BRIX target/cap, TA floor,
  tannin behaviour, color (red/white), signature aromas. SAPERAVI = current values
  verbatim. Whites: lower tannin, higher acid retention, white style; aromatic whites
  (Mtsvane/Kisi) carry floral aroma; Tsolikouri/Tsitska late + high acid; Racha reds
  lean semi-sweet-friendly (high sugar).
- Provide a sensible **fallback** profile for any Region/Variety not explicitly tuned,
  so nothing 400s or NPEs.

## 3. Wire it through
- `WeatherModel`/`KakhetiWeatherModel`: it already takes `Region`; make it read the
  `RegionClimate` for that region instead of using Kakheti constants unconditionally.
  (Rename to a general `GeorgianWeatherModel` only if you keep a `KakhetiWeatherModel`
  alias/bean so nothing else breaks — otherwise leave the class name and just
  generalize the body.)
- `KakhetiVineSimulator` (the `VineSimulator` impl): read a `VarietyProfile` for the
  planted variety instead of hardcoded Saperavi constants. Keep the class/bean name
  to avoid breaking wiring; just parameterize internals.
- `com.game.estate.VineyardReplayService`: it currently hardcodes `Region.KAKHETI`,
  a Kakheti `SiteProfile`, and Saperavi behaviour. Use `v.getRegion()` and
  `v.getVariety()`; pick a region-appropriate `SiteProfile` (per-region soil/altitude/
  slope/frost defaults; KAKHETI keeps today's exact `SiteProfile(HUMUS_CARBONATE,
  12,180,450,0.15,0.25)`).
- `com.game.vineyard.VineyardService` (on-demand sim): same — honor the request's
  region/variety (today it fixes Kakheti/HUMUS_CARBONATE). Keep the default request
  (SAPERAVI/KAKHETI) producing identical output.
- Appellation (`Resolver`): keep current logic working; optionally make appellation
  eligibility region+variety aware (e.g., Saperavi→Kakheti OK). Don't break tests.

## 4. Determinism
All randomness stays seeded via `RngStreams` (seed, region, year). Same
(seed, region, variety, year, day) ⇒ identical result. No wall-clock, no new RNG.

## 5. Tests (add; keep existing green)
- A `RegionVarietyTest`: same seed/year, KAKHETI/SAPERAVI vs IMERETI/TSOLIKOURI ⇒
  measurably different must (e.g., cooler+wetter Imereti gives lower brix / higher TA;
  white variety lower tannin). Assert *direction*, not exact magic numbers.
- A determinism test: same inputs ⇒ identical VineyardView/bottle.
- Confirm all 122 prior tests unchanged.

## 6. Lane ownership
Backend only: `com/game/core/data/{Region,Variety,...}.java`, new profile classes
(`com/game/core/weather/**` or a new `com/game/sim/region/**`), `com/game/core/weather/**`,
`com/game/sim/vine/**`, optional `com/game/sim/resolve/**`, and the wiring edits in
`com/game/estate/VineyardReplayService.java` + `com/game/vineyard/VineyardService.java`,
plus tests under `com/game/sim/**`. **Do NOT touch** `com/game/world/clock/**`,
`application.properties`, or any client code (other lanes own those).
