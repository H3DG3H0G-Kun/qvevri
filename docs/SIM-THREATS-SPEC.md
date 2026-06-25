# SIM-THREATS-SPEC — The Threat Engine (Phase 1 breadth)

> **Source of truth** for `sim.threats` (GDD Part 5.6 / 5.7). Extends, and is
> subordinate to, `docs/SIM-SPEC.md`. The API types in §3 are FROZEN and already
> authored under `com.game.sim.threats.api` — lanes implement against them
> exactly. Flag mismatches to the architect; never silently diverge.

## 0. Goal

Add a deterministic threat layer over the existing Saperavi/Kakheti vine year so
the resulting bottle visibly reflects the season's hazards. Each threat follows
the GDD pattern: **trigger -> spread -> damage -> warning sign (tell) -> counter
-> curable or terminal.** The engine runs every simulated day alongside the vine
and aggregates effects onto the vine's health, yield, quality, and faults.

**Done when:** running the year with threats produces, deterministically:
- A clean low-pressure year -> a healthy bottle close to the no-threat baseline.
- A wet year -> rising mildew pressure that measurably cuts quality/yield, with
  the right tells in the day-log.
- Own-roots on non-sand soil -> phylloxera establishes and, across seasons, is
  terminal (year 1 survives with declining health).
- A starling flock at veraison or a frost/hail event -> a sharp yield hit.

## 1. Design decisions (architect, binding)

1. **Realism:** specific named threats, each a `ThreatSource` with its own
   numeric pressure model and a human-readable `tell` string. No single blanket
   "blight" meter.
2. **Harshness:** harsh but counterable. Threats reduce health/yield/quality and
   are mostly preventable/curable via canopy, sprays, netting, ecosystem allies.
   **Terminal only:** phylloxera (own-roots, non-sand), esca/trunk disease, and
   chronic viruses (fanleaf, leafroll — no cure, slow sap, fixed only by
   replant). Weather year-killers (frost/hail) are catastrophic but not vine-
   terminal.
3. **Determinism:** every source draws from a named RNG sub-stream
   (`rng.stream("threat." + id)`); regional/weather events are seeded from
   `(masterSeed, region, year, day)`. Same inputs -> same outcome. No wall-clock,
   no `Math.random`, no `HashMap` iteration in logic (use `LinkedHashMap`/lists).
4. **Scope this phase:** the single Saperavi/Kakheti plot from Phase 0, one year.
   Multi-plot regional spread, multi-season virus/phylloxera death, and player-
   issued counter *operations* are modeled as inputs/memory but not yet driven by
   a UI. Keep regional spread as a per-day pressure sample (no neighbour graph).

## 2. How it composes with the existing vine (no frozen-contract changes)

`VineSimulator.tick(...)` (SIM-SPEC §3.4) is unchanged. The threat layer runs
**after** the daily vine tick, as a separate system:

```
for each day d:
    vine = vineSimulator.tick(vine, weather[d], site, suitability, pruning)
    DayResult r = threatEngine.step(vine, dayInputs[d])   // applies threats
    vine = r.vine()                                        // health/yield adjusted
```

The engine clamps `healthFraction` to [0,1], multiplies `potentialYieldKg`,
accumulates a `qualityPenalty01`, may set an induced `Fault`, and may flag the
vine dead. The accumulated quality penalty and any fault are handed to
`Resolver.resolve` via the must/cellar path (see §5).

## 3. Frozen API (already authored under `com.game.sim.threats.api`)

```java
enum ThreatCategory { FUNGAL, VIRUS, PEST, ANIMAL, WEATHER }

record ThreatMemory(double level, double aux, int ticksActive,
                    int yearsActive, boolean established) {
    static ThreatMemory none();   // all zero/false
}

record ThreatEffect(double healthDelta,      // added to healthFraction (<=0 damages)
                    double yieldMultiplier,  // multiplies potentialYieldKg (<=1)
                    double qualityPenalty01, // added to a running 0..1 penalty
                    Fault inducedFault,      // NONE, or a fault/taint to force
                    boolean killVine,
                    String tell,             // symptom text, "" if none
                    ThreatMemory nextMemory) {
    static ThreatEffect none(ThreatMemory mem); // neutral: 0, x1, 0, NONE, false, ""
}

record ThreatContext(int dayOfYear, DailyWeather today, double gddSeason,
                     VineState vine, SiteProfile site,
                     // management + ecosystem levers (defaulted by the harness):
                     boolean ownRoots, double canopyOpenness01, boolean leafPulled,
                     double copperSpray01, double sulfurSpray01, boolean netting,
                     boolean guardDog, boolean falcons, boolean cats, boolean ducks,
                     double coverCrop01,
                     RandomGenerator rng, ThreatMemory memory) {}

interface ThreatSource {
    String id();                 // stable, unique, lowercase-dotted (e.g. "fungal.downy")
    ThreatCategory category();
    ThreatEffect evaluate(ThreatContext ctx);   // pure given ctx
}
```

A source is **pure** w.r.t. its `ThreatContext` (which carries its own RNG stream
and prior `ThreatMemory`). Cross-day state lives only in `ThreatMemory`.

## 4. Lane ownership + the FROZEN class list

No two lanes write the same files. Each lane creates exactly the classes named
below (Lane D's registry instantiates them by these names — do not rename).

### Lane A — weather catastrophes + pressure (`com.game.sim.threats.weather.**`)
`SpringFrost`, `Hail`, `DroughtHeatwave`, `Flood`, `Fire` (each `implements
ThreatSource`), plus a `RegionalPressure` helper (static) computing a daily
fungal-pressure index from temperature + humidity + rain that the fungal lane may
reuse. Frost counters: late pruning / slope siting (low `site.frostRisk`) / a
proxy for smudge. Hail counter: `netting`. Drought counter: canopy/`coverCrop01`.

### Lane B — fungal + viruses (`com.game.sim.threats.fungal.**`, `...virus.**`)
`DownyMildew`, `PowderyMildew`, `Botrytis` (models grey rot vs the noble-rot
jackpot on healthy ripe fruit in the right humid-then-dry pattern), `BlackRot`,
`EscaTrunkDisease` (terminal, enters via pruning wounds). Viruses: `FanleafVirus`,
`LeafrollVirus` (chronic, no cure, slow yield/ripeness sap). Counters: canopy
openness, leaf-pulling, `copperSpray01` (downy), `sulfurSpray01` (powdery).

### Lane C — pests + animals + ecosystem (`com.game.sim.threats.pest.**`, `...animal.**`)
Pests: `Phylloxera` (terminal when `ownRoots` && soil != SAND; escape by grafting;
SAND immune), `Nematode` (clay soils), `GrapevineMoth` (GDD-scheduled broods at
~120/520/1047, base 12C), `Leafhopper`, `SpiderMite`, `Mealybug`. Animals (mostly
arrive at veraison/ripening): `Starlings`, `WildBoar`, `RoeDeer`, `Rodents`,
`WaspsDrosophila`. Ecosystem allies SUPPRESS via context flags: `falcons`->birds,
`guardDog`->boar/deer, `cats`->rodents, `ducks`->insects, `coverCrop01`->mites/
leafhoppers. Accuracy hook (GDD 5.7): bees are NOT pollinators; do not model them.

### Lane D — engine + integration + QA (`com.game.sim.threats.engine.**`, `...harness.**`, tests)
- `engine.ThreatEngine`: holds an ordered `List<ThreatSource>` + a
  `Map<String,ThreatMemory>` (per source id). `DayResult step(VineState vine,
  DayInputs env)` builds a per-source `ThreatContext` (inject
  `rng.stream("threat."+id)` and that source's memory), evaluates each in list
  order, aggregates effects onto the vine (clamp health 0..1, multiply yield,
  sum `qualityPenalty01` capped at 1, most-severe induced fault wins, OR of
  `killVine`), persists `nextMemory`, and returns the updated vine + a
  `ThreatReport` (active tells + aggregate deltas + cumulative quality penalty).
- `engine.ThreatRegistry`: a static factory returning the full `List<ThreatSource>`
  by instantiating every class named in Lanes A/B/C (deterministic order).
- `engine.DayResult`, `engine.ThreatReport`, `engine.DayInputs` records.
- `harness.ThreatYearRunner`: like `YearRunner` but composes the ThreatEngine
  each day; prints the year's threat events + final bottle. Run with
  `mvn -q compile exec:java -Dexec.mainClass=com.game.sim.threats.harness.ThreatYearRunner -Dexec.args="--seed 42 --pickDay 290"`.
  Default levers: ownRoots=true, no sprays/netting, no allies (so threats show).
  Do NOT edit the existing `YearRunner`.
- Tests under `server/src/test/java/com/game/sim/threats/**` (see §6).

Architect owns this spec, the `com.game.sim.threats.api.**` types (already
written), and `docs/PROGRESS.md`.

## 5. Quality/fault integration

The engine returns a cumulative `qualityPenalty01`. `ThreatYearRunner` passes it
through to the bottle by reducing `fruitHealth01` in the captured `MustProfile`
(health already feeds `Resolver` quality) and, if a threat set an induced fault
(e.g. rot -> VOLATILE_ACIDITY, fire -> a smoke-taint proxy via OXIDATION), by
passing a pre-faulted `CellarResult` is NOT allowed — instead reduce must health
and let the cellar/resolve compute naturally; induced faults are surfaced in the
ThreatReport and applied by overriding the resolved `WineLot.fault()` only when
the threat fault is more severe than the cellar's. Keep this logic in
`ThreatYearRunner`/engine, not in the frozen `Resolver`.

## 6. Acceptance tests (Lane D)

1. **Determinism:** same seed/levers -> identical end-of-year `VineState` and
   `ThreatReport`.
2. **Mildew responds to weather:** a wet/humid seed yields higher downy/powdery
   pressure and lower final quality than a dry seed; `copperSpray01`/canopy
   openness measurably reduce it.
3. **Phylloxera gating:** own-roots + non-sand -> establishes (memory.established
   true) and health declines; SAND soil OR grafted (`ownRoots=false`) -> no
   establishment, no damage.
4. **Veraison animals:** `Starlings` only bite from veraison onward; `falcons`
   true suppresses the loss; netting reduces hail/bird damage.
5. **Frost:** an early-spring frost event on a high-`frostRisk` site cuts yield
   hard; low frost-risk (slope) mitigates.
6. **Plausibility/no-regression:** with all threat pressures near zero, the
   bottle stays within ~5 quality points of the Phase-0 no-threat baseline; no
   NaN/negative fields; health stays in [0,1].

## 7. Versioning
`v1` of the threats contract. Additive within the phase; structural changes bump
here first.
