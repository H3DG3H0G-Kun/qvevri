# Vertical Slice 1 — Task Breakdown & Ownership

Architect-owned coordination doc. Each task is independently buildable once
`docs/API.md` (the contract) is fixed. **No two agents write the same files.**

## Ownership lanes (hard boundaries)

| Agent           | Owns (writes only here)                                             |
|-----------------|--------------------------------------------------------------------|
| java-backend    | `server/**`                                                        |
| netcode         | `client/Assets/Scripts/Net/**`                                     |
| unity-gameplay  | `client/Assets/Scripts/Gameplay/**`                                |
| unity-graphics  | `client/Assets/Art/**`, `client/Assets/Settings/**` (render/URP), scene file |
| qa-test         | `server/src/test/**`, `client/Assets/Tests/**`, `.github/workflows/**` |
| game-architect  | `docs/**` (this file, API.md, PROGRESS.md)                         |

If you need something outside your lane, request it from the architect — do not
reach across.

---

## Internal client interface (the gameplay ⇆ netcode seam)

So gameplay and netcode build in parallel, they meet at **one C# interface**.
netcode implements it; gameplay consumes it. Defined here, owned by netcode in
`client/Assets/Scripts/Net/INetClient.cs`.

```csharp
namespace Game.Net {
  public struct Vec3 { public float x, y, z; }

  public struct RemotePlayer {
    public string playerId;
    public string displayName;
    public Vec3 position;
    public float rotationY;
  }

  public interface INetClient {
    // Auth + join. Resolves with the local playerId when "welcome" is received.
    System.Threading.Tasks.Task<string> LoginAndJoinAsync(
        string username, string password, string sessionId);

    // Spawn position from the join response. Valid after LoginAndJoinAsync
    // resolves / after OnSelfAssigned fires. (Architect amendment v1.1 —
    // resolves netcode-7.2 + gameplay-R1: spawn must reach gameplay.)
    Vec3 SpawnPosition { get; }

    // Fire-and-forget local movement -> server. The ≤20/s send throttle lives
    // inside the implementation, not in gameplay.
    void SendMove(Vec3 position, float rotationY);

    // Raised on the main thread (marshalled) when a "state"/"join"/"leave"
    // snapshot arrives. Gameplay subscribes to render remote players.
    event System.Action<RemotePlayer[]> OnPlayersUpdated;
    event System.Action<string> OnSelfAssigned;   // playerId (SpawnPosition set first)
    event System.Action<string> OnError;
  }
}

> **Architect amendments (v1.1), binding for implementation:**
> - `INetClient.SpawnPosition` added (above) so gameplay can place the local
>   player. netcode sets it from `join.spawn` before `OnSelfAssigned` fires.
> - **Scene ownership stays with unity-graphics.** To avoid cross-lane scene
>   edits, unity-gameplay bootstraps via `[RuntimeInitializeOnLoadMethod]`
>   (no scene wiring needed) and owns a tiny `CameraFollow` under Gameplay that
>   drives the graphics-provided Camera. unity-graphics provides ground, light,
>   camera, and the `CharacterPrefab`; it does not touch Gameplay scripts.
> - **unity-graphics lane extended** to `client/ProjectSettings/` (Graphics/
>   Quality settings) so the URP pipeline can be made active. No
>   `Packages/manifest.json` changes this slice (no Cinemachine — camera follow
>   is a simple offset in gameplay's CameraFollow).
> - **unity-gameplay** extracts pure movement/yaw math into a static
>   `MovementMath` helper (no MonoBehaviour) so qa-test can unit-test it.
> - netcode owns `FakeNetClient`; gameplay codes against `INetClient` only.
```

Gameplay codes against `INetClient`; netcode provides the concrete
`WebSocketNetClient`. A `FakeNetClient` (netcode-owned) lets gameplay run with
no server.

---

## Tasks

### T1 — java-backend (`server/**`)
Spring Boot (Java 21, Maven). Deliver:
- `POST /api/auth/login`, `POST /api/sessions/join`,
  `GET /api/sessions/{id}/players` per API.md §3.
- WebSocket `/ws/game` handler per API.md §4 (hello/move → welcome/state/
  join/leave broadcast, ~10/s tick).
- JPA `PlayerState` entity + repository, H2 file DB, throttled persistence
  (≤1 write / 500 ms / player) per API.md §5.
- Constructor injection, no secrets in code, `application.properties`.
- Provide a runnable `mvn spring-boot:run`.

### T2 — netcode (`client/Assets/Scripts/Net/**`)
- `INetClient.cs` (the interface above), DTOs mirroring API.md §2–4 with JSON
  (de)serialization that matches the wire format exactly.
- `WebSocketNetClient.cs`: REST login + join, WS connect with `?token=`,
  send `hello`/`move`, parse `welcome`/`state`/`join`/`leave`/`error`, marshal
  events to the Unity main thread.
- `FakeNetClient.cs` for offline gameplay dev.
- Document any field you cannot satisfy back to the architect (do not change
  API.md yourself).

### T3 — unity-gameplay (`client/Assets/Scripts/Gameplay/**`)
- `PlayerController.cs`: WASD/stick input → CharacterController movement on a 3D
  character, writes yaw; calls `INetClient.SendMove` at ≤20/s.
- `RemotePlayerSpawner.cs`: subscribes to `OnPlayersUpdated`, instantiates/moves
  remote avatars by playerId, smoothing toward latest state.
- `GameBootstrap.cs`: wires login flow → join → hands control to controller.
  Depend only on `Game.Net.INetClient` (inject `FakeNetClient` until netcode
  lands).

### T4 — unity-graphics (`client/Assets/Art/**`, `client/Assets/Settings/**`)
- A simple rigged-or-capsule 3D character prefab + material, a ground plane,
  directional light, and a follow camera setup.
- URP render pipeline asset + quality settings; target 60 fps on mid hardware.
- Expose a `CharacterPrefab` the gameplay spawner can reference (document the
  prefab path/GUID for gameplay). No gameplay logic in art scripts.

### T5 — qa-test (`server/src/test/**`, `client/Assets/Tests/**`, `.github/**`)
- JUnit tests: login (valid/invalid), join, persistence round-trip, WS
  move→state echo (use Spring's WS test support or a thin integration test).
- Unity EditMode tests: DTO (de)serialization matches API.md fixtures; gameplay
  movement math; `FakeNetClient` event contract.
- `.github/workflows/ci.yml`: build+test server (Maven); compile/test client
  (Unity test-runner or at minimum C# compile via dotnet for the scripts).
- Report failures to the architect; do not edit non-test source to make tests
  pass — file a bug instead.

---

## Dependency order (for sequencing, not blocking)

```
API.md (done) ─┬─> T1 java-backend ───┐
               ├─> T2 netcode ────────┼─> T5 qa-test (tests integration)
               ├─> T3 gameplay (Fake) ┘
               └─> T4 graphics (prefab) ──> T3 references prefab
```

All five can start immediately against API.md + the `INetClient` seam. QA
writes tests against the contract in parallel and runs them once code lands.

## Definition of done (slice)
- `mvn spring-boot:run` serves auth/session/WS; H2 persists positions.
- Unity scene boots, logs in, joins, moves a character, sees remote players.
- QA suite green in CI. Each agent appends a line to `docs/PROGRESS.md`.
