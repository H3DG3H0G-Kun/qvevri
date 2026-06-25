# QVEVRI — Unreal Engine 5 Client

This folder contains the Unreal Engine 5.7 client for QVEVRI.  
The Spring Boot backend (`server/`) and the shared contract (`docs/API.md`) are **unchanged**.

This document covers:
1. [Project setup](#1-project-setup)
2. [Adding the source files](#2-adding-the-source-files)
3. [Module dependencies (Build.cs)](#3-module-dependencies-buildcs)
4. [Configuring the API base URL](#4-configuring-the-api-base-url)
5. [Calling the API client from C++](#5-calling-the-api-client-from-c)
6. [Calling the API client from Blueprint](#6-calling-the-api-client-from-blueprint)
7. [What is NOT here yet](#7-what-is-not-here-yet)

---

## 1. Project setup

### Create a UE 5.7 C++ project

1. Launch the Unreal Engine 5.7 editor from the Epic Games Launcher.
2. Choose **Games > Blank** template.
3. Select **C++** (not Blueprint-only — the networking layer is C++).
4. Name the project exactly **`Qvevri`** (this controls the module name and export macro).
5. Set the location to `<repo>/client-unreal/` or a sibling directory.
6. Click **Create Project**.

The editor generates:
```
client-unreal/
  Qvevri.uproject
  Source/
    Qvevri/
      Qvevri.Build.cs   <- replace with ours (see §3)
      QvevriGameMode.h / .cpp   <- keep or delete; not used by the net layer
```

### Enable the Cesium for Unreal plugin

> Cesium is needed for the globe / spatial rendering phase. The networking layer
> compiled here does not depend on it yet, but add it now while the project is
> fresh.

1. In the editor: **Edit > Plugins** — search for **Cesium for Unreal**.
2. If not listed, download it from the **Epic Games Marketplace** (free) or from
   [cesium.com/platform/cesium-for-unreal/](https://cesium.com/platform/cesium-for-unreal/).
3. Enable the plugin and restart the editor.
4. Sign in to [Cesium ion](https://cesium.ion/) (free account) and generate a
   **Project Access Token** for your project.
5. In Unreal: **Edit > Project Settings > Cesium** — paste the token.
6. After UMG/world work begins, uncomment `CesiumRuntime` in `Qvevri.Build.cs`.

---

## 2. Adding the source files

Copy the files from this repo into the generated project:

```
# From: client-unreal/Source/Qvevri/Public/
#   QvevriDtos.h          — auth + world + character DTOs
#   QvevriMarketDtos.h    — cellar + market DTOs
#   QvevriEstateDtos.h    — vineyard + management DTOs
#   QvevriSession.h       — session holder (FQvevriSession)
#   QvevriApiClient.h     — HTTP subsystem declaration

# From: client-unreal/Source/Qvevri/Private/
#   QvevriApiClient.cpp   — HTTP subsystem implementation

# From: client-unreal/Source/Qvevri/
#   Qvevri.Build.cs       — module build rules (replace the generated one)
```

After copying, right-click `Qvevri.uproject` in Windows Explorer and choose
**Generate Visual Studio project files** (or run `UnrealBuildTool` manually).
Open the `.sln` in Visual Studio / Rider and build.

---

## 3. Module dependencies (Build.cs)

The supplied `Qvevri.Build.cs` already declares the required modules:

```csharp
PublicDependencyModuleNames.AddRange(new string[]
{
    "Core", "CoreUObject", "Engine", "InputCore",
    "HTTP",           // FHttpModule
    "Json",           // TJsonReader / FJsonObject / FJsonSerializer
    "JsonUtilities",  // FJsonObjectConverter
});
```

**Do not remove** `Json` or `JsonUtilities` — `QvevriApiClient.cpp` uses both.

When you start building UI:
- Uncomment `UMG`, `Slate`, `SlateCore` in Build.cs.

When you add Cesium world rendering:
- Uncomment `CesiumRuntime` in Build.cs.

---

## 4. Configuring the API base URL

The default base URL is `http://localhost:8080` (matches the Spring Boot dev
server). Override it once, early in your game startup:

```cpp
// In your GameInstance::Init() or a BeginPlay somewhere before the first request:
UQvevriApiClient* Api = GetSubsystem<UQvevriApiClient>();
Api->SetBaseUrl(TEXT("http://localhost:8080"));
// For a remote dev server: Api->SetBaseUrl(TEXT("http://192.168.1.42:8080"));
```

You can also set it from the Project Settings by exposing a config variable or
reading from a `.ini` file — recommended for shipping builds.

---

## 5. Calling the API client from C++

`UQvevriApiClient` is a `UGameInstanceSubsystem`.  Retrieve it from any
`UObject` that has access to the `UGameInstance`:

```cpp
#include "QvevriApiClient.h"

// Retrieve the subsystem (GameInstance lifetime — always valid while the game runs)
UQvevriApiClient* Api = GetGameInstance()->GetSubsystem<UQvevriApiClient>();
```

### Login + store session

```cpp
FLoginRequest Req;
Req.username = TEXT("lasha");
Req.password = TEXT("secret");

Api->Login(
    Req,
    [Api](const FAuthResponse& Auth)
    {
        // Store token so all subsequent protected calls work.
        Api->SetToken(Auth.token);
        Api->Session.AccountId = Auth.accountId;
        Api->Session.Token     = Auth.token;
        UE_LOG(LogTemp, Log, TEXT("Logged in — accountId=%lld"), Auth.accountId);
    },
    [](const FQvevriApiError& Err)
    {
        UE_LOG(LogTemp, Warning, TEXT("Login failed [%s]: %s"), *Err.Code, *Err.Message);
    });
```

### Fetch characters after login

```cpp
Api->GetCharacters(
    [Api](const TArray<FCharacter>& Characters)
    {
        for (const FCharacter& C : Characters)
        {
            UE_LOG(LogTemp, Log, TEXT("Character: %s (%s / %s)"),
                *C.name, *C.careerType, *C.homeRegion);
        }
        if (Characters.Num() > 0)
        {
            Api->Session.ActiveCharacter = Characters[0];
        }
    },
    [](const FQvevriApiError& Err)
    {
        UE_LOG(LogTemp, Warning, TEXT("GetCharacters failed: %s"), *Err.Message);
    });
```

### Plant a vineyard

```cpp
FPlantVineyardRequest Req;
Req.characterId = Api->Session.ActiveCharacter.id;
Req.region      = TEXT("KAKHETI");
Req.variety     = TEXT("SAPERAVI");

Api->PlantVineyard(
    Req,
    [](const FVineyardEntity& V)
    {
        UE_LOG(LogTemp, Log, TEXT("Planted vineyard id=%lld"), V.id);
    },
    [](const FQvevriApiError& Err)
    {
        UE_LOG(LogTemp, Warning, TEXT("PlantVineyard failed: %s"), *Err.Message);
    });
```

All callbacks fire **on the game thread** — it is safe to update UObjects,
widgets, and UProperties directly inside them.

---

## 6. Calling the API client from Blueprint

`UQvevriApiClient` is a `UGameInstanceSubsystem` and is therefore accessible
from Blueprint via the **Get Game Instance Subsystem** node:

1. Drag off the **Game Instance** node.
2. Call **Get Qvevri Api Client** (the subsystem shows up automatically).
3. `UFUNCTION(BlueprintCallable)` methods like `SetBaseUrl`, `SetToken`,
   `ClearToken`, `HasToken`, and session access are directly callable.

For async calls (Login, GetCharacters, etc.) the current C++ API uses
`TFunction` callbacks, which are **not Blueprint-native**.  Two options:

- **Option A (recommended for now):** Create a thin Blueprint Function Library
  that wraps each `TFunction`-based call and dispatches success/failure via
  Blueprint-exposed events or `UK2Node_AsyncTask` (latent nodes).
- **Option B:** Convert each method to a `UFUNCTION(BlueprintCallable, meta=(Latent, ...))` using `FLatentActionInfo`.

This is listed in the "what comes next" section below.

---

## 7. What is NOT here yet

This commit delivers the **networking + data layer only** — the engine-agnostic
HTTP + JSON + DTO + session plumbing.

Next phases (in order):

1. **Blueprint async wrappers** — expose all API calls as latent Blueprint nodes
   so UMG widgets can drive them without C++.

2. **UMG design-system kit** — port the `docs/UI-DESIGN-SYSTEM.md` tokens
   (colors, fonts, spacing) and core widgets (`QvButton`, `QvPanel`, `QvField`,
   `QvTabs`, `QvBadge`) as a UMG widget library.

3. **Auth + character screens (UMG)** — Login, Register, CharacterSelect,
   CharacterCreate.  Maps directly to the C# UI Toolkit screens in
   `client/Assets/Scripts/UI/`.

4. **Estate / market / goods DTOs** — `QvevriGoodsDtos.h` covering
   `GET /api/goods/catalog`, `GET /api/goods/{characterId}`, shop buy/sell,
   profession capabilities/claim-kit/cooper/lab, and vineyard-action.  Also add
   `UQvevriApiClient` methods: `GetGoodsCatalog`, `GetOwnedGoods`, `ShopBuy`,
   `ShopSell`, `GetProfessionCapabilities`, `ClaimKit`, `CooperCraft`,
   `LabGrade`.

5. **Cesium world integration** — `ACesiumGeoreference` setup, estate anchoring
   at real lat/long from `FRegionInfo.latitude / .longitude`, player presence
   via Unreal replication.

6. **World session (WebSocket)** — port `WebSocketNetClient.cs`:
   `hello / move / ping -> welcome / state / join / leave` over
   `IWebSocket` (the `WebSockets` UE module).

---

## Source file index

| File | Description |
|---|---|
| `Source/Qvevri/Qvevri.Build.cs` | Module dependencies |
| `Source/Qvevri/Public/QvevriDtos.h` | Auth + world + character USTRUCTs |
| `Source/Qvevri/Public/QvevriMarketDtos.h` | Cellar + market USTRUCTs |
| `Source/Qvevri/Public/QvevriEstateDtos.h` | Vineyard + management USTRUCTs |
| `Source/Qvevri/Public/QvevriSession.h` | Session holder (FQvevriSession) |
| `Source/Qvevri/Public/QvevriApiClient.h` | HTTP subsystem declaration |
| `Source/Qvevri/Private/QvevriApiClient.cpp` | HTTP subsystem implementation |
