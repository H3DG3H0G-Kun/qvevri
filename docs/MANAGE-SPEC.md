# MANAGE-SPEC — Vineyard management that drives the sim (v1)

Turn a vineyard from "plant → wait → harvest" into something you **tend**. The
threat/vine engine already responds to management levers; today they're hardcoded
defaults in the replay. This exposes them as a per-vineyard **management plan**
that the deterministic replay reads, and lets players set it (eventually from the
3D plot).

## 1. Determinism model (important)
State stays a pure function of inputs:
`state = f(seed, region, variety, year, managementPlan, day)`.
The management plan is part of the input. It is **mutable**: changing it recomputes
the season's projection (intuitive — you revised your plan, the forecast updates).
A harvested vintage is still locked via `lastHarvestedYear`, so you can't re-roll a
finished wine. No dated-action timeline in v1 (the plan applies for the whole
replayed season); per-day action history is a future extension.

## 2. Management levers (persist on the Vineyard; defaults = today's hardcoded values)
These are exactly the `ThreatEngine` `DayInputs` levers currently hardcoded in
`VineyardReplayService`. Move them onto the persistent vineyard with these defaults
so existing behaviour + all tests are unchanged:

| lever | type | default | effect (already in the engine) |
|-------|------|---------|--------------------------------|
| budLoad | int (1..40) | 12 | yield vs. overburden quality loss (vine sim) |
| ownRoots | bool | true | phylloxera risk (own-roots = vulnerable) |
| canopyOpenness01 | double 0..1 | 0.40 | fungal microclimate / sun |
| leafPulled | bool | false | botrytis/mildew airflow |
| copperSpray01 | double 0..1 | 0.0 | suppresses downy mildew / black rot |
| sulfurSpray01 | double 0..1 | 0.0 | suppresses powdery mildew |
| netting | bool | false | reduces bird damage at véraison |
| guardDog | bool | false | reduces boar/deer |
| falcons | bool | false | reduces starlings |
| cats | bool | false | reduces rodents |
| ducks | bool | false | reduces slugs/insects |
| coverCrop01 | double 0..1 | 0.0 | soil/vigor & some pests |

## 3. Endpoints (estate; bearer token → account; verify character owns the vineyard)
- `GET /api/vineyards/{vineyardId}/management` → current plan (the levers above).
  (Or fold a `management` block into the existing detail view — implementer's call,
  but keep `GET /api/vineyards/{characterId}` list shape backward-compatible.)
- `POST /api/vineyards/{vineyardId}/manage` `{characterId, <any subset of levers>}`
  → validate ranges (0..1 doubles; budLoad 1..40), persist, return the updated
  `VineyardView` recomputed at the current world day so the client sees the effect.

## 4. Wire into replay
`VineyardReplayService` stops using hardcoded levers and reads them from the
`Vineyard`. The threat-engine `DayInputs` and the vine `PruningDecision(budLoad)`
get the vineyard's plan. Determinism unchanged (still seeded via `RngStreams`).

## 5. Persistence / migration
Add the lever columns to the `mmo_vineyard` entity (estate). Dev H2 (`ddl-auto=update`)
adds them automatically with defaults; add a Flyway **`V2__vineyard_management.sql`**
(Postgres) `ALTER TABLE mmo_vineyard ADD COLUMN ...` with the same defaults so prod
matches. Existing rows get defaults = today's behaviour.

## 6. Tests (add; keep all current green)
- Default plan reproduces today's output exactly (Kakheti/Saperavi unchanged).
- `copperSpray01=0.8` in a wet region (e.g. IMERETI) ⇒ higher fruit health / quality
  than spray=0 there (downy mildew suppressed). Assert direction.
- `netting=true` ⇒ better outcome than netting=false in a bird-pressured véraison.
- Over-cropping (budLoad high) ⇒ lower quality than balanced. (May already be covered
  by BudLoadTest — don't duplicate; just don't break it.)
- Range validation: out-of-range lever ⇒ 400; manage on unowned vineyard ⇒ 403/404.

## 7. Lanes
| Lane | Owns |
|------|------|
| TA backend | `com/game/estate/**` (Vineyard entity + levers, VineyardReplayService wiring, manage/management endpoints + DTOs), `server/src/main/resources/db/migration/V2__vineyard_management.sql`, tests under `com/game/estate/**`. Do NOT touch sim/**, core/data/**, auth/**, client. |
| TC client | `client/Assets/**` only — a Manage panel (set levers) reachable from the 3D plot interaction + Vineyards tab; reflect a few levers visually (nets, cover crop, guard-dog marker); persist via `/manage`; refresh plot/view after. Reuse WebMmoApi + session. |

Determinism, named values, no secrets. Keep the Saperavi/Kakheti default-lever path
byte-identical so the suite stays green.
