# QVEVRI — session handoff

Read this first when resuming work in a new Claude/Cowork session. It captures
where the project stands so you don't have to reconstruct context.

## Backend (`server/`, Spring Boot, Java 21)
- ~42 systems across Flyway migrations V1–V29. **~473 tests green** — verify with
  `mvn test` (the assistant cannot run Maven in its sandbox; run it on Windows
  with JDK 21 and paste the output).
- **Integration pass COMPLETE.** A central `BonusService` aggregates bonuses from
  career + skills + buildings + completed research + active staff (each normalized
  to its canonical unit) and is wired into 5 live actions:
  - SELL_MARGIN → export sell
  - BUY_DISCOUNT → bazaar buy
  - QUALITY → winemaking
  - SHIPPING_DISCOUNT → travel
  - YIELD → harvest (cellar grow)
- Dev DB: H2 file at `./data/game`; server binds 0.0.0.0:8080.

## Unreal client (`client-unreal/`, UE 5.7 + Cesium for Unreal)
- C++ networking layer (`UQvevriApiClient`) + DTOs ported and compiling.
- **Phase 4 in progress:** `AQvevriRegionPins` fetches `/api/world/regions` and
  pins the 7 regions on the Cesium globe via `CesiumGlobeAnchorComponent`.
- **PENDING FIX — region pins not appearing.** `KakhetiWorld` is a World Partition
  level. The pins actor's `BeginPlay` didn't run because (a) it was dropped ~5700 km
  from origin into a far WP cell, and (b) "Enable World Bounds Checks" is on (culls
  far actors). To fix:
  1. World Settings → uncheck **Enable World Bounds Checks**.
  2. Select **QvevriRegionPins** → set Location to 0,0,0 → uncheck **Is Spatially
     Loaded** (World Partition section).
  3. Ctrl+S, Play, wait ~5s, watch Output Log for `[Qvevri] RegionPins: pinned ...`.
  (Backend must be running; pins actor has a `Base Url Override` field.)

## Migration / repo
- Pushed to **https://github.com/H3DG3H0G-Kun/qvevri** (git + LFS for `Content/`).
- Fixed bug: an un-anchored `data/` gitignore rule had excluded the
  `com.game.core.data` source package. Force-added via `git add -f server/src`.
  If source ever goes missing again, suspect an un-anchored ignore pattern.

## Immediate next steps
1. New PC toolchain: install **VS 2022** (`https://aka.ms/vs/17/release/vs_community.exe`
   — NOT VS 2026; 2026's compiler isn't supported by UE 5.7). Then right-click the
   `.uproject` → Generate VS project files → build the `Qvevri` module.
2. Apply the World Partition fix above to see the region pins.
3. Backend next-batch options: auto-fire XP/quests/achievements on live actions;
   feed prestige from contests/wealth/festivals.

## Conventions (see CLAUDE.md)
- Backend lanes are additive: new package, pre-assigned Flyway version, keep all
  tests green, "default/no-bonus character unchanged."
- Multiplayer: run ONE backend; both clients point at it (shared H2 world). For a
  friend on the same LAN, they set `Base Url Override` to `http://<host-ip>:8080`.
