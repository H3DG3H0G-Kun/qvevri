# VINEYARD-API — Sim-over-HTTP contract (v1.2)

> Bridges the headless simulation (SIM-SPEC / SIM-THREATS-SPEC) to the Unity
> client so the wine game is visible/playable. Source of truth for the
> `/api/vineyard/*` wire formats and the client `IVineyardApi` seam. Server and
> client implement these EXACTLY; flag mismatches to the architect.

## 0. Goal

A player presses "Grow a Vintage" in the client, the server runs one simulated
year of one Saperavi vine (optionally with threats), and the client displays the
resulting bottle + the year's notable events. No auth required for this compute
endpoint (it owns no player data yet).

## 1. REST endpoint

### POST `/api/vineyard/simulate`  (no auth)
Runs one deterministic simulated year and resolves a bottle.

**Request** (all fields optional; defaults shown):
```json
{
  "seed": 42,
  "variety": "SAPERAVI",
  "soil": "HUMUS_CARBONATE",
  "budLoad": 12,
  "pickDay": 270,
  "threats": true
}
```
Validation: `budLoad` 1..40, `pickDay` 1..364, `variety`/`soil` must be valid
enum names (else `400 BAD_REQUEST` with the standard error envelope from
API.md §1).

**200 OK** — `VineyardYearResult`:
```jsonc
{
  "seed": 42,
  "vintage": { "year": 1, "gddSeason": 1873.4, "winkler": "III", "pattern": "warm-dry" },
  "pickDay": 270,
  "suitability": 0.84,
  "must": {
    "volumeL": 6.3, "brix": 23.7, "taGL": 6.6, "pH": 3.52,
    "yanMgL": 180.0, "tanninRipeness01": 0.71, "fruitHealth01": 0.88
  },
  "bottle": {
    "variety": "SAPERAVI", "style": "RED", "vintageYear": 1,
    "volumeL": 6.3, "abv": 13.9, "quality": 71.2, "ageabilityYears": 7.5,
    "fault": "NONE", "appellationOk": false, "label": "...",
    "aroma": { "acid": 0.45, "dark-fruit": 0.78, "spice": 0.5 }
  },
  "events": [
    "day 95: budbreak",
    "day 210: véraison",
    "day 232: starling flock on the block — fruit stripped",
    "day 270: harvested at 23.7 Bx"
  ]
}
```
`events` = a compact, ordered, human-readable log of phenology transitions and
notable threat days (the threat `tell` strings), for display. Keep it under ~40
lines. `aroma` keys sorted (deterministic).

## 2. Client seam — `Game.Net.Vineyard.IVineyardApi`

The UI depends only on this interface. netcode provides a real
`WebVineyardApi` (calls the endpoint) and a `FakeVineyardApi` (offline; returns a
plausible canned result so the UI works with no server).

```csharp
namespace Game.Net.Vineyard {
  [System.Serializable] public class VineyardRequest {
    public long seed = 42; public string variety = "SAPERAVI";
    public string soil = "HUMUS_CARBONATE"; public int budLoad = 12;
    public int pickDay = 270; public bool threats = true;
  }
  // DTOs mirror §1 response exactly (field names = wire names).
  public interface IVineyardApi {
    // Resolves with the parsed result, or throws/!= success on error.
    System.Threading.Tasks.Task<VineyardYearResult> SimulateAsync(VineyardRequest req);
  }
}
```
Base URL (dev): `http://localhost:8080`. Use `UnityWebRequest` (UnityWebRequest
runs on the player loop) or `HttpClient`; marshal completion to the main thread
for the UI. JSON via Newtonsoft (already a dependency), field names matching §1.

## 3. Ownership lanes

| Lane | Owns (writes only here) |
|------|-------------------------|
| VA server-vineyard | `server/src/main/java/com/game/vineyard/**` (+ its tests) |
| VB server-econ | `server/src/main/java/com/game/econ/**` (+ its tests) |
| VC client-vineyard | `client/Assets/Scripts/Net/Vineyard/**` and `client/Assets/Scripts/Gameplay/UI/**` |
| VD qa | `server/src/test/java/com/game/vineyard/**`, `server/src/test/java/com/game/econ/**` |

The server `vineyard` package REUSES the existing sim (`com.game.core.*`,
`com.game.sim.*`) — it must NOT duplicate or modify it; it wraps the same
pipeline as `YearRunner`/`ThreatYearRunner` and returns the DTO instead of
printing. No changes to the frozen sim packages or the Phase-3 netcode packages.

## 4. Determinism

Same request → identical `VineyardYearResult` (the sim is deterministic from
`seed`). The endpoint must not introduce wall-clock or RNG outside the seeded
streams.

## 5. Versioning
`v1.2`. Additive within the phase.
