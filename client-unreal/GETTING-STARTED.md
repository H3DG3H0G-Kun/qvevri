# QVEVRI — Unreal client: getting started

You have Unreal Engine 5.7 installed. This is the full path from zero to "flying over a
real, photoreal-leaning Kakheti, talking to the live backend." Do the phases in order —
each one ends with a clear "success looks like" so you know it worked before moving on.

The project already exists at `client-unreal/` (a complete UE C++ project named **Qvevri**:
`.uproject`, `Source/Qvevri/` networking code, target files). You are not creating it from
scratch — you are opening, compiling, then adding Cesium.

---

## Phase 0 — Prerequisite: Visual Studio 2022 (C++)

Unreal compiles the C++ module with MSVC. Without it, the project will not build.

1. Install **Visual Studio 2022 Community** (free).
2. In the installer, check the **"Game development with C++"** workload. (Also fine to add
   ".NET desktop development".)
3. Finish install.

If you already have it with that workload, skip to Phase 1.

> Success: `Visual Studio 2022` is installed with the C++ game-dev workload.

---

## Phase 1 — Open and compile the project (networking layer, no Cesium yet)

1. Open `C:\Users\H3DG3H0G\Desktop\repos\unity-game\client-unreal\`.
2. **Right-click `Qvevri.uproject` → "Generate Visual Studio project files."** (Wait for it.)
3. **Double-click `Qvevri.uproject`.** Unreal will say the `Qvevri` module is missing / out of
   date and ask to rebuild — click **Yes**. First compile takes a few minutes.
   - If asked which engine version, pick **5.7**.
4. The editor opens to an empty default level.

> Success: the Unreal Editor opens with no errors. That means our DTOs + `UQvevriApiClient`
> compiled against your real engine.

**If the rebuild fails:** open `Qvevri.sln` in Visual Studio 2022, set configuration to
**Development Editor / Win64**, Build → Build Solution, and read the first error. Paste it to
me — first-compile errors are usually a missing workload or a one-line header tweak.

---

## Phase 2 — Run the backend (so the client has something to talk to)

The Unreal client calls `http://localhost:8080`. Start the server:

1. Open a terminal in `C:\Users\H3DG3H0G\Desktop\repos\unity-game\server`.
2. Run: `mvn spring-boot:run`  (uses your JDK 21).
3. Wait for "Started GameServerApplication".
4. Verify in a browser: open `http://localhost:8080/api/world/regions` — you should see JSON
   listing the 7 regions **with latitude/longitude**. That lat/long is what Cesium will use.

> Success: the regions endpoint returns JSON with coordinates. Leave the server running.

---

## Phase 3 — Cesium for Unreal + the Kakheti world

### 3a. Install the plugin
1. Open the **Epic Games Launcher → Fab** (or fab.com in a browser, signed in).
2. Search **"Cesium for Unreal"**, add it (free), and install it to your **5.7** engine.

### 3b. Enable it in the project
1. Back in the Qvevri editor: **Edit → Plugins**.
2. Search **"Cesium for Unreal"**, tick **Enabled**, and **restart the editor** when prompted.

### 3c. Get a Cesium ion token (free)
1. Sign up at **cesium.com** (free Community tier).
2. Go to **Access Tokens → Create token** (or copy the default token).
3. In the editor: **Window → Cesium** to open the Cesium panel → **Connect to Cesium ion**
   (sign in), or paste the token when prompted.

### 3d. Stream the terrain (no buildings)
1. In the **Cesium** panel, click the quick-add for **"Cesium World Terrain + Bing Maps Aerial
   imagery"** (or "+ Blank 3D Tiles" then add World Terrain). This gives real terrain + satellite
   imagery — and NO buildings, which is exactly the look you want for rural Kakheti.
   - Do NOT add "Google Photorealistic 3D Tiles" — that's the buildings/photogrammetry layer and
     it bills through Google. Skip it for the vineyard world.

### 3e. Point the world at Kakheti
1. Select the **CesiumGeoreference** actor in the World Outliner (the Cesium quick-add created it).
2. In Details, set the **Origin**:
   - **Origin Latitude:** `41.92`
   - **Origin Longitude:** `45.47`
   - **Origin Height:** `0` (metres)
   (These are Telavi, Kakheti — the same coordinates the backend returns for KAKHETI.)
3. In the Cesium panel, add a **Cesium DynamicPawn** (a fly-camera). Select it.

### 3f. Fly there
1. Press **Play**.
2. Use the DynamicPawn controls (W/A/S/D + mouse, scroll to change speed) to fly around.

> Success: you are flying over the real terrain of Kakheti, streamed live. That's the spike.

---

## Phase 4 — Connect the C++ client to the world (next coding step — I help here)

Once Phases 1–3 are green, the next milestone is wiring our `UQvevriApiClient` to Cesium so the
game actually uses the backend:

1. Uncomment `PublicDependencyModuleNames.Add("CesiumRuntime");` in `Source/Qvevri/Qvevri.Build.cs`
   (it's already there as a comment) so C++ can talk to Cesium.
2. On Play, create the `UQvevriApiClient` subsystem, call `GetRegions()`, and for each region drop
   a marker (a `CesiumGlobeAnchor`-ed actor) at its returned lat/long — so the 7 wine regions
   appear pinned on the real map.
3. Then: login/register against the backend, fetch the player's character, and spawn them at their
   home region's coordinates.

That's the first real end-to-end: real Kakheti + live backend data on the map. Tell me when
Phases 1–3 are done and I'll give you the exact C++ for Phase 4.

---

## Notes / gotchas
- The module is named **Qvevri** — our headers use the `QVEVRI_API` export macro generated from
  that name. Don't rename the project.
- Keep the **backend running** whenever you Play and want live data.
- I can't see your screen — at any phase, if something errors, paste the message and I'll fix it.
  Same loop as the Java side.
