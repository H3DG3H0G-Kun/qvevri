# QVEVRI — Unreal + Cesium migration plan (v1)

Decision: move the **client** from Unity to **Unreal Engine 5 + Cesium for Unreal**,
to render a real-world, photoreal-leaning map of Georgia, get Nanite/Lumen fidelity,
and use Unreal's networking for realtime player presence. The **backend does not
change.** This doc is the steering plan; nothing here touches the Java server's
behaviour.

## What carries over unchanged (the majority of the work)
- **The entire Spring Boot backend** — sim, economy, professions, world clock,
  persistence, 187 tests. It speaks REST/JSON and is engine-agnostic.
- **`docs/API.md`** — the client/server contract. Unreal consumes the same endpoints.
- **The UI design system** (`docs/UI-DESIGN-SYSTEM.md`) — palette, type, spacing,
  component specs survive as *design*. Reimplemented in UMG (see below).
- **All game-design / domain work** — regions, varieties, appellations, threats.

## What gets rebuilt (bounded — the client shell only)
- Unity C# networking/DTO layer → Unreal HTTP + JSON (C++ and/or Blueprints).
- UI Toolkit screens (UXML/USS) → **UMG** widget library (the look transfers, the
  implementation is redone).
- Unity 3D scene / bootstrap / vineyard primitives → Unreal level + Cesium world.
The Unity client is *shelved, not deleted* — backend, contract, and design survive,
so this is the client surface, not the engine of the game.

## Target architecture
Two authorities, cleanly split:

1. **Java backend = world & economy authority.** Owns the deterministic vineyard
   sim, market, professions, persistence, world clock. Stateless REST today; add a
   lightweight realtime channel later only if needed. Knows nothing about tiles or
   rendering. This is why Cesium's dedicated-server limitations don't bite us — the
   server that matters never streams tiles.
2. **Unreal + Cesium = client + spatial presence.** Streams the Georgia world
   (Cesium ion: Google Photorealistic 3D Tiles where available + Cesium World
   Terrain + aerial imagery elsewhere), places vineyards/estates at real lat/long,
   and handles player movement + seeing other players in a zone via Unreal
   replication. Pulls all game state from the Java API.

Presence layer (the "5000 players see each other" problem): Unreal replication for
movement within a zone, area-of-interest culling, client-side interpolation — the
Java backend stays the source of truth for everything persistent. Realtime presence
is deliberately separate from the authoritative sim (the Tick A vs Tick B split from
SCALE-DESIGN.md still holds).

## Cesium specifics & honest caveats
- **Coverage:** Photorealistic 3D Tiles are city-focused (2,500+ cities). Rural
  Kakheti vineyard areas will blend Cesium World Terrain + high-res imagery + our
  own placed vineyard/estate assets rather than pure photogrammetry. Tbilisi and
  towns can be photoreal; the vines themselves are our art on real terrain.
- **Georeference:** one `CesiumGeoreference` origin; estates anchored by real
  lat/long. Our 7 backend regions map to real Georgian wine appellations — we add
  real coordinates to the Region data (see "first steps", a backend task I can do now).
- **Multiplayer + Cesium gotchas (real):** dedicated servers don't render, so
  Cesium's frustum-based tile selection doesn't run there; there are reported
  collision-replication and multiplayer world-origin-rebasing issues. Mitigation:
  keep authority in Java (no tiles on the authoritative server); if/when an Unreal
  dedicated server is used purely for presence, enable multiplayer origin rebasing
  and use simplified server-side collision (or Cesium physics-mesh streaming) rather
  than relying on rendered tiles. Prototype this seam early before committing to a
  presence topology.
- **Access:** Cesium ion account + per-project access token; Google 3D Tiles billed
  via Google Maps Platform — worth confirming cost at expected usage.

## UI: porting the design system to UMG
The tokens and components in `docs/UI-DESIGN-SYSTEM.md` become a UMG widget library:
a shared style data-asset (colors/fonts/metrics) + reusable user widgets
(QvButton wine/gold/ghost, QvPanel, QvField, QvTabs, QvBadge…). The wine-cellar
aesthetic, the login mockup, every screen layout we designed — all reusable as
specs. Unreal also has Common UI for input-routed, multi-platform menus; worth using
for the navigation shell.

## Networking from Unreal
- Unreal **HTTP module** (`FHttpModule`) + a JSON lib for the REST calls; mirror the
  DTOs from `docs/API.md`. Consider generating C++ structs from the contract.
- Auth: same bearer-token flow (`/api/account/login` → token → `Authorization`).
- Keep `docs/API.md` as the single source of truth; both ends conform to it.

## Phased plan
1. **Backend bridge (engine-agnostic, doable now):** add real lat/long to the 7
   regions + expose via the world API; confirm `API.md` is complete for an Unreal
   client. (I can do this immediately — see below.)
2. **Unreal project + Cesium spike:** UE5 project, Cesium ion token, stream Georgia,
   stand at a real Kakheti coordinate. Pure visual proof.
3. **Auth + character vertical slice:** HTTP login/register → fetch characters →
   spawn at an estate's real coordinates. Proves the contract end-to-end.
4. **UMG design-system kit:** port tokens + core widgets; rebuild the login + one
   gameplay screen to lock the look in-engine.
5. **Estates on the map:** place vineyards at coordinates, open the manage/bazaar/
   profession screens from the world.
6. **Presence prototype:** two clients seeing each other move in one zone; validate
   the Cesium-multiplayer seam before scaling.

## Honest constraints (mine)
I can't run or see Unreal/Cesium here, same as Unity — I write C++/Blueprints/UMG and
backend code, you compile and screenshot. Visual + Cesium work is *even more*
screenshot-loop dependent. Where I add the most value immediately is the
engine-agnostic backend bridge (phase 1) and the architecture/contract — so I suggest
we start there while you stand up the UE5 + Cesium project.
