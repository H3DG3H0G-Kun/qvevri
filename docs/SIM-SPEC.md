# SIM-SPEC — Headless Simulation Core (Phase 0 + Part 12.4 target)

> **Source of truth** for the deterministic simulation core, analogous to
> `docs/API.md` for the netcode slice. Any change to shared types, module
> seams, or determinism rules MUST be made here first, in the same change.
> Lanes implement these signatures **exactly**; flag mismatches to the
> architect, never silently diverge.

## 0. Goal (the acceptance bar, from GDD Part 12.4)

A **deterministic, graphics-free** build that runs ONE Saperavi vine on ONE
Kakheti plot through ONE simulated year and prints the resulting bottle.
**Done when:** a single simulated year produces a believable `WineLot` whose
quality responds *correctly* to (a) the weather/vintage, (b) the pruning
bud-load decision, and (c) the harvest pick-timing — and the run is bit-for-bit
reproducible from a seed.

**In scope:** `core.time`, `core.weather`, `core.data`, `sim.soil` (light),
`sim.vine`, `sim.ops`, `sim.cellar`, `sim.resolve`, a text harness, tests.
**Explicitly OUT of scope for this phase:** threats/pests/disease/animals,
economy, professions, land/anti-monopoly, governance, multiple regions/grapes,
networking, persistence, UI. (Those are later phases — do not build them.)

## 1. Tech & determinism (non-negotiable)

- **Language/build:** Java 21, reuse the existing `server/` Maven project.
  Sim code lives under `server/src/main/java/com/game/sim/**` and
  `com/game/core/**`. **Do not touch** the Phase-3 netcode packages
  (`com.game.auth|session|ws|persistence|config|dto|exception`) or
  `server/src/test/**` for the existing slice.
- **Fixed timestep:** the agronomic tick is **one simulation day**. A year =
  day-of-year `0..364`. No wall-clock time anywhere in sim logic.
- **Seeded RNG:** all randomness derives from a single `long masterSeed` via a
  `SplittableRandom`-based stream factory (`RngStreams`). Each subsystem gets a
  named, independent stream so evaluation order can't change results. **Never**
  use `Math.random`, `System.currentTimeMillis`, `Instant.now`, `UUID`, or
  iteration over a `HashMap`/`HashSet` in logic. Use `LinkedHashMap`/sorted
  keys where order matters.
- **Reproducibility contract:** `run(masterSeed, inputs)` returns an equal
  `WineLot` across runs and machines. QA enforces this.
- **Units:** °C, grams/litre, litres, metres, degrees. Brix in °Bx. All
  floating point is `double`.

## 2. Shared data types (`com.game.core.data`) — FROZEN, implement verbatim

Use Java `record`s (immutable). These are the seams every lane codes against.

```java
package com.game.core.data;

public enum Region { KAKHETI } // only one this phase

public enum Variety { SAPERAVI } // red; only one this phase

public enum SoilType {            // stat block via SoilType.profile()
  HUMUS_CARBONATE, BLACK_EARTH, ALLUVIAL, CLAY_LIMESTONE, HEAVY_CLAY, SAND, VOLCANIC
}

public enum PhenoStage {
  DORMANCY, BUD_SWELL, BUDBREAK, SHOOT_GROWTH, FLOWERING, FRUIT_SET,
  BERRY_DEVELOPMENT, VERAISON, RIPENING, HARVESTED, LEAF_FALL
}

public enum FermentMethod { KAKHETIAN, IMERETIAN, RED, SPARKLING_BASE, SWEET }

public enum WineStyle { AMBER, WHITE, RED, SPARKLING_BASE, SWEET }

public enum Fault { NONE, REDUCTION_H2S, OXIDATION, VOLATILE_ACIDITY, STUCK_FERMENT }

public enum WinklerClass { I, II, III, IV, V }

/** A calendar position. dayOfYear 0..364. */
public record GameDate(int year, int dayOfYear) {}

/** Site geometry + soil for one plot. */
public record SiteProfile(
    SoilType soil,
    double slopeDeg,      // 0 flat .. 45 steep
    double aspectDeg,     // 0=N,90=E,180=S,270=W
    double altitudeM,
    double frostRisk,     // 0..1 (valley floor high, slope low)
    double waterProximity // 0..1
) {}

/** One day of weather (Tier-C field sampled to a plot). */
public record DailyWeather(
    int dayOfYear, double tMinC, double tMaxC, double rainMm, double humidity01
) {
  public double meanTempC() { return (tMinC + tMaxC) / 2.0; }
}

/** The whole-season weather + vintage roll. */
public record Vintage(
    int year, Region region, double gddSeason, WinklerClass winkler,
    String patternLabel // e.g. "warm-dry", "cool-wet"
) {}

/** Evolving state of the vine, updated each daily tick. */
public record VineState(
    PhenoStage stage,
    double gddAccum,         // since budbreak, base 10C
    double healthFraction,   // 0..1 (1 = perfectly healthy this phase)
    double potentialYieldKg, // locked progressively (bud load -> fruit set)
    // ripening clocks (meaningful from veraison):
    double brix,             // sugar, deg Bx
    double taGL,             // titratable acidity g/L
    double pH,
    double yanMgL,           // yeast-assimilable nitrogen mg/L
    double tanninRipeness01  // 0 green .. 1 ripe (reds)
) {}

/** Player's winter pruning choice. */
public record PruningDecision(int budLoad) {} // buds retained per vine

/** Player's pick. */
public record HarvestDecision(int dayOfYear) {}

/** The must captured at harvest (input to the cellar). */
public record MustProfile(
    double volumeL, double brix, double taGL, double pH, double yanMgL,
    double tanninRipeness01, double fruitHealth01, int vintageYear
) {}

/** Final product. quality 0..100. aroma keys sorted for determinism. */
public record WineLot(
    Variety variety, WineStyle style, int vintageYear,
    double volumeL, double abv, double quality, double ageabilityYears,
    Fault fault, java.util.SortedMap<String,Double> aroma,
    boolean appellationOk, String label
) {}
```

## 3. Module seams (interfaces / static APIs) — FROZEN signatures

### 3.1 `com.game.core.time`
```java
public final class RngStreams {           // deterministic stream factory
  public RngStreams(long masterSeed) {...}
  public java.util.random.RandomGenerator stream(String name) {...} // stable per name
}
public final class SimClock {             // fixed 1-day step
  public SimClock(int year) {...}
  public GameDate date() {...}
  public GameDate advanceDay() {...}      // returns new date, dayOfYear++ (wraps year)
}
```

### 3.2 `com.game.core.weather`
```java
public final class Gdd {
  public static double daily(DailyWeather w, double baseC) {  // max(0, mean-base)
    return Math.max(0, w.meanTempC() - baseC);
  }
}
public interface WeatherModel {
  /** Deterministic from (seed,region,year). 365 days, plausible Kakheti curve. */
  java.util.List<DailyWeather> generateYear(RngStreams rng, Region region, int year);
  Vintage rollVintage(RngStreams rng, Region region, int year,
                      java.util.List<DailyWeather> days); // GDD sum + Winkler
}
```
GDD base = **10°C** for the vine. Winkler from season GDD (°C, Apr1–Oct31):
I `<1390`, II `1390–1670`, III `1670–1940`, IV `1940–2220`, V `>2220`.
Kakheti should usually land in **III**.

### 3.3 `com.game.sim.soil`
```java
public final class SoilTypes { public static SoilStat profile(SoilType t){...} }
public record SoilStat(double vigor01, double waterHolding01, double frostBias01) {}
public final class SiteSuitability {
  /** 0..1: how well (variety) suits (site). Saperavi favours humus-carbonate
      slopes, dislikes frost-prone over-vigorous valley floors. */
  public static double score(Variety v, SiteProfile site) {...}
}
```

### 3.4 `com.game.sim.vine`
```java
public interface VineSimulator {
  /** Advance the vine one day. Pure: same inputs -> same output. */
  VineState tick(VineState prev, DailyWeather today, SiteProfile site,
                 double suitability, PruningDecision pruning);
}
```
Behaviour the impl must encode (kept simple but directionally real):
- Phenology gated by GDD/temperature: budbreak when sustained mean ≥10°C;
  flowering ~ when gddAccum crosses a threshold; véraison later; etc.
- **Bud load**: `potentialYieldKg` scales with budLoad, but an **overburdened**
  vine ripens slower (Brix rises more slowly per GDD) and loses quality; an
  **underburdened** vine gives little yield. There is a balanced sweet spot
  (document the Saperavi target, e.g. ~`budLoad≈12`).
- **Ripening clocks from véraison:** `brix` rises with accumulated post-véraison
  GDD; `taGL` falls; `pH` rises; `tanninRipeness01` climbs slowly; very late =
  raisining (volume/quality penalty). Hot vintage → faster Brix, lower final TA.

### 3.5 `com.game.sim.ops`
```java
public final class Harvest {
  /** Capture the must at the chosen pick day from the vine's state. */
  public static MustProfile pick(VineState atPick, double volumeFromYield);
}
```

### 3.6 `com.game.sim.cellar`
```java
public record CellarResult(double abv, double finalTaGL, double pH,
    Fault fault, double extraction01, double cleanliness01) {}
public interface Fermenter {
  /** Deterministic fermentation kinetics (not a timer). Models: temp band by
      style (whites 7-16C, reds 21-30C, >32 -> stuck), Brix-drop/day, low YAN
      -> H2S risk, cap management (tending01) affects extraction/cleanliness. */
  CellarResult ferment(MustProfile must, FermentMethod method,
                       double cellarTempC, double tending01, RngStreams rng);
}
```

### 3.7 `com.game.sim.resolve`
```java
public final class Resolver {
  /** Combine vine + cellar into one bottle (GDD Part 5.11). */
  public static WineLot resolve(Variety variety, FermentMethod method,
      MustProfile must, CellarResult cellar, Vintage vintage,
      double suitability, String label);
}
```
`quality = f(fruit health, ripeness balance at pick, extraction, fault penalty)`.
`style` from method. `appellationOk` = false this phase (no appellation rules
yet) **except** leave the hook. `aroma` = sorted map of descriptor→intensity
derived from variety + ripeness (keep small, e.g. "dark-fruit","spice","acid").

### 3.8 Harness — `com.game.sim.harness.YearRunner`
A `main(String[])` that: builds `RngStreams(seed)`, generates the Kakheti
weather year, runs the vine day-by-day applying a `PruningDecision` and a
`HarvestDecision`, ferments, resolves, and **prints** the `WineLot` plus a short
day-log (phenology transitions, Brix/TA at pick). Accept CLI args
`--seed --budLoad --pickDay` with sensible defaults. Runnable via
`mvn -q compile exec:java -Dexec.args="--seed 42 --budLoad 12 --pickDay 290"` or a documented `java` invocation.

## 4. Lane ownership (no two lanes write the same files)

| Lane | Owns (writes only here) |
|------|-------------------------|
| L1 core | `com/game/core/data/**`, `com/game/core/time/**`, `com/game/core/weather/**` |
| L2 vine | `com/game/sim/soil/**`, `com/game/sim/vine/**` |
| L3 cellar | `com/game/sim/ops/**`, `com/game/sim/cellar/**`, `com/game/sim/resolve/**`, `com/game/sim/harness/**` |
| L4 qa | `server/src/test/java/com/game/sim/**` (new sim tests only) + Maven `exec-maven-plugin` wiring if needed (coordinate, additive) |

`core.data` records + the §3 signatures are FROZEN: L2/L3/L4 code against them
while L1 implements them. Architect owns this spec + `docs/PROGRESS.md`.

## 5. Acceptance tests (L4 encodes these)

1. **Determinism:** `run(seed=42, budLoad=12, pick=290)` twice → equal `WineLot`.
2. **Pick timing:** same seed, sweep pickDay. Early pick → lower Brix, higher
   TA, lower still-wine quality; optimum window → max quality; very late →
   raisining penalty (quality and/or volume down).
3. **Bud load:** sweep budLoad at fixed pick. Overburden → lower Brix at harvest
   + quality penalty; underburden → low volume; balanced → best quality×volume.
4. **Vintage/weather:** a warm seed reaches target Brix earlier (fewer days)
   than a cool seed; cool year retains higher TA.
5. **Plausibility bounds:** Saperavi red in Kakheti, sensible inputs → ABV
   ~12–15%, quality in 0..100, style RED, no `NaN`/negative fields.

## 6. Versioning
`v1` of the sim contract. Additive within the phase; structural changes bump
here first.
