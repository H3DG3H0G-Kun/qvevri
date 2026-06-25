# QVEVRI — Build Roadmap & Status

Status of the build against the GDD's phased plan (Part 12.3).
Legend: ✅ done · 🟡 partial · ⬜ not started

---

## Phase 0 — Foundation (`core.*`) ✅
- ✅ `core.time` — fixed-step tick, seeded deterministic RNG, calendar
- ✅ `core.data` — entity records (weather, site, vine, must, wine lot, …)
- ✅ `core.weather` — GDD clock, Kakheti weather year, per-seed vintage variation

## Phase 1 — Vine-to-bottle simulation (`sim.*`) ✅
- ✅ `sim.soil` — soil stat blocks + site-suitability score
- ✅ `sim.vine` — phenology state machine, 4 ripening clocks, bud-load effects
- ✅ `sim.ops` — harvest pick
- ✅ `sim.cellar` — fermentation kinetics + wine faults
- ✅ `sim.resolve` — final WineLot (quality, style, ABV, aroma, ageability)
- ✅ headless `YearRunner` harness + acceptance tests
- ✅ `sim.threats` — 23 named threats (weather/fungal/virus/pest/animal) + engine + harness + tests
- 🟡 calibration: ripening caps, vintage spread, yield-under-stress all fixed; quality curve still shallow & peaks early, threat pressure a bit harsh (flagged for tuning)

## Phase 2 — Economy (`econ.*`) 🟡
- ✅ items + inventory (grapes/must/wine/brandy; `Item.fromBottle`)
- ✅ `WinePricer` (quality × vintage × appellation × scarcity)
- ✅ `Bazari` market core (list / buy / escrow, `Trade`) + `Contract` skeleton
- ⬜ market REST endpoint (list a bottle, browse, buy)
- ⬜ market UI in Unity
- ⬜ persisted player inventories / wallets
- ⬜ professions (lab, négociant, broker, cooper, …) — the unbundled value chain
- ⬜ reputation engine
- ⬜ land & anti-monopoly system (plots, holding tax, lapse-reclaim, council)

## Phase 3 — Playable client + persistence 🟡
- ✅ auth + session + 3D character movement slice (login / join / move)
- ✅ position persistence (H2) for the movement slice
- ✅ Unity client opens & plays (URP, capsule, follow camera)
- ✅ vineyard "sim-over-HTTP" endpoint + Unity "Grow a Vintage" panel ← the visible game
- 🟡 movement feel (works but rough — deferred)
- ⬜ persisted vineyards/plots per player (sim is stateless per request today)
- ⬜ multi-year season loop (one year per request today)
- ⬜ real visual vineyard/cellar (currently a text panel + a capsule)

## Phase 4 — Content & breadth ⬜
- ⬜ more grapes / regions (only Saperavi / Kakheti now)
- ⬜ Imereti method (amber/white), other regional methods
- ⬜ appellation certification rules
- ⬜ rtveli (harvest) seasonal event

## Phase 5 — Society & governance ⬜
- ⬜ houses / cooperatives / guilds
- ⬜ temi / council governance, consent-based rule, taxes
- ⬜ settlements & building

## Open design decisions (not locked yet) ⬜
- ⬜ time compression (how long a qvevri takes to mature)
- ⬜ threat realism level / catastrophe harshness
- ⬜ launch professions, optional conflict layer

## Cross-cutting / infrastructure ✅
- ✅ Maven build, Java 21, **89 tests green**, CI workflow, `.properties` config
- ✅ architect contracts (API.md, SIM-SPEC, SIM-THREATS-SPEC, VINEYARD-API, SLICE-TASKS) + PROGRESS log

---

### One-line summary
The **simulation is done and proven** (Phases 0–1), a **visible vineyard demo** runs end-to-end (slice of Phase 3), and the **economy core exists but isn't wired into play yet** (Phase 2 half-done). The large MMO breadth — professions, land, society, multiple regions, multiplayer at scale — is still ahead.

### Immediate next step
Close the economy loop: turn a grown bottle into a sellable `Item`, add a market endpoint + small Unity market panel so wine you **grow** becomes wine you **trade**.
