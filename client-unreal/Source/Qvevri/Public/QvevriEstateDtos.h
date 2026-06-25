// QvevriEstateDtos.h
// QVEVRI — Unreal Engine 5 client.
// DTOs for vineyard/estate and management-plan endpoints.
// All field names mirror the JSON wire contract in docs/API.md §10 + §6 EXACTLY.
//
// COVERAGE:
//   POST /api/vineyards                        -> FPlantVineyardRequest / FVineyardEntity
//   GET  /api/vineyards/{characterId}          -> TArray<FVineyardView>
//   POST /api/vineyards/{vineyardId}/harvest   -> FHarvestRequest / FHarvestResponse
//   GET  /api/vineyards/{vineyardId}/management -> FManagementPlan
//   POST /api/vineyards/{vineyardId}/manage    -> FManageRequest / FVineyardView
//   POST /api/vineyards/{vineyardId}/action    -> FVineyardActionRequest / FVineyardView
//
// Owned by: Unreal client lane.

#pragma once

#include "CoreMinimal.h"
#include "QvevriMarketDtos.h"   // FBottleSummary
#include "QvevriEstateDtos.generated.h"

// ---------------------------------------------------------------------------
// Vineyard entity  —  POST /api/vineyards (201)
// ---------------------------------------------------------------------------

/**
 * Request body for POST /api/vineyards.
 * Wire: { "characterId": 5, "region": "KAKHETI", "variety": "SAPERAVI",
 *         "seed": 42, "budLoad": 12 }
 * "seed" and "budLoad" are optional on the server (defaults applied when absent).
 * Set them to 0 to omit — the serializer will include them; the server uses
 * sensible defaults for seed=0 / budLoad=0.
 *
 * COMPILE NOTE: To truly omit optional fields you would need a custom JSON
 * serialisation step (e.g. manually build the FJsonObject). For simplicity
 * this DTO always sends all fields — the server tolerates seed=0.
 */
USTRUCT(BlueprintType)
struct QVEVRI_API FPlantVineyardRequest
{
    GENERATED_BODY()

    /** Wire key: "characterId" */
    UPROPERTY(BlueprintReadWrite, Category = "Qvevri|Estate")
    int64 characterId = 0;

    /** Wire key: "region" — enum name, e.g. "KAKHETI". */
    UPROPERTY(BlueprintReadWrite, Category = "Qvevri|Estate")
    FString region;

    /** Wire key: "variety" — enum name, e.g. "SAPERAVI". */
    UPROPERTY(BlueprintReadWrite, Category = "Qvevri|Estate")
    FString variety;

    /** Wire key: "seed" — optional; server derives default when 0. */
    UPROPERTY(BlueprintReadWrite, Category = "Qvevri|Estate")
    int64 seed = 0;

    /** Wire key: "budLoad" — optional; server defaults to 12 when 0. */
    UPROPERTY(BlueprintReadWrite, Category = "Qvevri|Estate")
    int32 budLoad = 0;
};

/**
 * 201 response from POST /api/vineyards — the bare Vineyard entity.
 * Wire: { "id": 1, "ownerCharacterId": 5, "region": "KAKHETI",
 *         "variety": "SAPERAVI", "seed": 42, "budLoad": 12,
 *         "status": "GROWING", "lastHarvestedYear": 0,
 *         "createdAt": 1718500000000 }
 */
USTRUCT(BlueprintType)
struct QVEVRI_API FVineyardEntity
{
    GENERATED_BODY()

    UPROPERTY(BlueprintReadOnly, Category = "Qvevri|Estate")
    int64 id = 0;

    UPROPERTY(BlueprintReadOnly, Category = "Qvevri|Estate")
    int64 ownerCharacterId = 0;

    UPROPERTY(BlueprintReadOnly, Category = "Qvevri|Estate")
    FString region;

    UPROPERTY(BlueprintReadOnly, Category = "Qvevri|Estate")
    FString variety;

    UPROPERTY(BlueprintReadOnly, Category = "Qvevri|Estate")
    int64 seed = 0;

    UPROPERTY(BlueprintReadOnly, Category = "Qvevri|Estate")
    int32 budLoad = 0;

    /** Wire key: "status" — e.g. "GROWING". */
    UPROPERTY(BlueprintReadOnly, Category = "Qvevri|Estate")
    FString status;

    UPROPERTY(BlueprintReadOnly, Category = "Qvevri|Estate")
    int32 lastHarvestedYear = 0;

    /** Wire key: "createdAt" — epoch milliseconds. */
    UPROPERTY(BlueprintReadOnly, Category = "Qvevri|Estate")
    int64 createdAt = 0;
};

// ---------------------------------------------------------------------------
// VineyardView  —  GET /api/vineyards/{characterId}
// ---------------------------------------------------------------------------

/**
 * Full live-state view returned by GET /api/vineyards/{characterId} (array)
 * and embedded in harvest responses.
 * Wire: { "vineyardId": 1, "ownerCharacterId": 5, "region": "KAKHETI",
 *         "variety": "SAPERAVI", "year": 1, "dayOfYear": 200,
 *         "stage": "VERAISON", "brix": 18.4, "taGL": 7.2, "pH": 3.35,
 *         "healthFraction": 0.92, "estimatedYieldKg": 8.1,
 *         "ripe": false, "alreadyHarvestedThisYear": false,
 *         "recentEvents": ["Bud break", "Flowering"],
 *         "plantedYear": 1, "vineAgeYears": 0 }
 *
 * "plantedYear" and "vineAgeYears" are additive (LANE M); absent from the
 * server response until that lane is deployed — defaults to 0.
 */
USTRUCT(BlueprintType)
struct QVEVRI_API FVineyardView
{
    GENERATED_BODY()

    UPROPERTY(BlueprintReadOnly, Category = "Qvevri|Estate")
    int64 vineyardId = 0;

    UPROPERTY(BlueprintReadOnly, Category = "Qvevri|Estate")
    int64 ownerCharacterId = 0;

    UPROPERTY(BlueprintReadOnly, Category = "Qvevri|Estate")
    FString region;

    UPROPERTY(BlueprintReadOnly, Category = "Qvevri|Estate")
    FString variety;

    UPROPERTY(BlueprintReadOnly, Category = "Qvevri|Estate")
    int32 year = 1;

    UPROPERTY(BlueprintReadOnly, Category = "Qvevri|Estate")
    int32 dayOfYear = 0;

    /** Wire key: "stage" — phenology stage enum name, e.g. "VERAISON". */
    UPROPERTY(BlueprintReadOnly, Category = "Qvevri|Estate")
    FString stage;

    UPROPERTY(BlueprintReadOnly, Category = "Qvevri|Estate")
    double brix = 0.0;

    /** Wire key: "taGL" — titratable acidity g/L. */
    UPROPERTY(BlueprintReadOnly, Category = "Qvevri|Estate")
    double taGL = 0.0;

    /** Wire key: "pH" */
    UPROPERTY(BlueprintReadOnly, Category = "Qvevri|Estate")
    double pH = 0.0;

    UPROPERTY(BlueprintReadOnly, Category = "Qvevri|Estate")
    double healthFraction = 1.0;

    UPROPERTY(BlueprintReadOnly, Category = "Qvevri|Estate")
    double estimatedYieldKg = 0.0;

    /** Wire key: "ripe" — true when stage==RIPENING and brix>=22. */
    UPROPERTY(BlueprintReadOnly, Category = "Qvevri|Estate")
    bool ripe = false;

    UPROPERTY(BlueprintReadOnly, Category = "Qvevri|Estate")
    bool alreadyHarvestedThisYear = false;

    UPROPERTY(BlueprintReadOnly, Category = "Qvevri|Estate")
    TArray<FString> recentEvents;

    /** Wire key: "plantedYear" — LANE M additive; 0 until delivered by server. */
    UPROPERTY(BlueprintReadOnly, Category = "Qvevri|Estate")
    int32 plantedYear = 0;

    /** Wire key: "vineAgeYears" — LANE M additive; 0 until delivered. */
    UPROPERTY(BlueprintReadOnly, Category = "Qvevri|Estate")
    int32 vineAgeYears = 0;
};

// ---------------------------------------------------------------------------
// Harvest  —  POST /api/vineyards/{vineyardId}/harvest
// ---------------------------------------------------------------------------

/**
 * Request body for POST /api/vineyards/{vineyardId}/harvest.
 * Wire: { "characterId": 5 }
 */
USTRUCT(BlueprintType)
struct QVEVRI_API FHarvestRequest
{
    GENERATED_BODY()

    /** Wire key: "characterId" */
    UPROPERTY(BlueprintReadWrite, Category = "Qvevri|Estate")
    int64 characterId = 0;
};

/**
 * Response from POST /api/vineyards/{vineyardId}/harvest.
 * Wire: { "cellarItem": { ... }, "bottle": { ... }, "vineyardView": { ... } }
 */
USTRUCT(BlueprintType)
struct QVEVRI_API FHarvestResponse
{
    GENERATED_BODY()

    /** Wire key: "cellarItem" — the deposited cellar item (defined in QvevriMarketDtos.h). */
    UPROPERTY(BlueprintReadOnly, Category = "Qvevri|Estate")
    FCellarItem cellarItem;

    /** Wire key: "bottle" — summary of the harvested wine. */
    UPROPERTY(BlueprintReadOnly, Category = "Qvevri|Estate")
    FBottleSummary bottle;

    /** Wire key: "vineyardView" — updated vineyard state with alreadyHarvestedThisYear=true. */
    UPROPERTY(BlueprintReadOnly, Category = "Qvevri|Estate")
    FVineyardView vineyardView;
};

// ---------------------------------------------------------------------------
// Management Plan  —  GET /api/vineyards/{vineyardId}/management
// ---------------------------------------------------------------------------

/**
 * Response from GET /api/vineyards/{vineyardId}/management.
 * Contains all 11 management levers exactly as persisted on the Vineyard entity.
 * Wire names defined in API.md §10 (MANAGE-SPEC §2).
 * Also used as the request body for POST .../manage (all fields optional there).
 */
USTRUCT(BlueprintType)
struct QVEVRI_API FManagementPlan
{
    GENERATED_BODY()

    /** Wire key: "budLoad" — range 1..40; default 12. */
    UPROPERTY(BlueprintReadWrite, Category = "Qvevri|Estate")
    int32 budLoad = 12;

    /** Wire key: "ownRoots" — default true. */
    UPROPERTY(BlueprintReadWrite, Category = "Qvevri|Estate")
    bool ownRoots = true;

    /** Wire key: "canopyOpenness01" — range 0..1; default 0.4. */
    UPROPERTY(BlueprintReadWrite, Category = "Qvevri|Estate")
    double canopyOpenness01 = 0.4;

    /** Wire key: "leafPulled" — default false. */
    UPROPERTY(BlueprintReadWrite, Category = "Qvevri|Estate")
    bool leafPulled = false;

    /** Wire key: "copperSpray01" — range 0..1; default 0.0. */
    UPROPERTY(BlueprintReadWrite, Category = "Qvevri|Estate")
    double copperSpray01 = 0.0;

    /** Wire key: "sulfurSpray01" — range 0..1; default 0.0. */
    UPROPERTY(BlueprintReadWrite, Category = "Qvevri|Estate")
    double sulfurSpray01 = 0.0;

    /** Wire key: "netting" — default false. */
    UPROPERTY(BlueprintReadWrite, Category = "Qvevri|Estate")
    bool netting = false;

    /** Wire key: "guardDog" — default false. */
    UPROPERTY(BlueprintReadWrite, Category = "Qvevri|Estate")
    bool guardDog = false;

    /** Wire key: "falcons" — default false. */
    UPROPERTY(BlueprintReadWrite, Category = "Qvevri|Estate")
    bool falcons = false;

    /** Wire key: "cats" — default false. */
    UPROPERTY(BlueprintReadWrite, Category = "Qvevri|Estate")
    bool cats = false;

    /** Wire key: "ducks" — default false. */
    UPROPERTY(BlueprintReadWrite, Category = "Qvevri|Estate")
    bool ducks = false;

    /** Wire key: "coverCrop01" — range 0..1; default 0.0. */
    UPROPERTY(BlueprintReadWrite, Category = "Qvevri|Estate")
    double coverCrop01 = 0.0;
};

/**
 * Request body for POST /api/vineyards/{vineyardId}/manage.
 * characterId is required; all lever fields are optional (server only updates
 * non-null fields). In UE5 we always send all fields — server ignores 0/false
 * as "unchanged" for booleans only when they equal the existing value.
 *
 * COMPILE NOTE: True partial updates (null = omit) require manual FJsonObject
 * construction per field. For the initial port, sending all fields is safe
 * because the server merges only the supplied values.
 */
USTRUCT(BlueprintType)
struct QVEVRI_API FManageRequest
{
    GENERATED_BODY()

    /** Wire key: "characterId" — required. */
    UPROPERTY(BlueprintReadWrite, Category = "Qvevri|Estate")
    int64 characterId = 0;

    /** Wire key: "budLoad" */
    UPROPERTY(BlueprintReadWrite, Category = "Qvevri|Estate")
    int32 budLoad = 12;

    UPROPERTY(BlueprintReadWrite, Category = "Qvevri|Estate")
    bool ownRoots = true;

    UPROPERTY(BlueprintReadWrite, Category = "Qvevri|Estate")
    double canopyOpenness01 = 0.4;

    UPROPERTY(BlueprintReadWrite, Category = "Qvevri|Estate")
    bool leafPulled = false;

    UPROPERTY(BlueprintReadWrite, Category = "Qvevri|Estate")
    double copperSpray01 = 0.0;

    UPROPERTY(BlueprintReadWrite, Category = "Qvevri|Estate")
    double sulfurSpray01 = 0.0;

    UPROPERTY(BlueprintReadWrite, Category = "Qvevri|Estate")
    bool netting = false;

    UPROPERTY(BlueprintReadWrite, Category = "Qvevri|Estate")
    bool guardDog = false;

    UPROPERTY(BlueprintReadWrite, Category = "Qvevri|Estate")
    bool falcons = false;

    UPROPERTY(BlueprintReadWrite, Category = "Qvevri|Estate")
    bool cats = false;

    UPROPERTY(BlueprintReadWrite, Category = "Qvevri|Estate")
    bool ducks = false;

    UPROPERTY(BlueprintReadWrite, Category = "Qvevri|Estate")
    double coverCrop01 = 0.0;
};

// ---------------------------------------------------------------------------
// Vineyard per-day action  —  POST /api/vineyards/{vineyardId}/action
// ---------------------------------------------------------------------------

/**
 * Request body for POST /api/vineyards/{vineyardId}/action.
 * Wire: { "characterId": 5, "dayOfYear": 180,
 *         "actionType": "EMERGENCY_COPPER_SPRAY", "value": 0.8 }
 *
 * Supported actionType values (API.md §10):
 *   EMERGENCY_COPPER_SPRAY  — copperSpray01 lever from dayOfYear; value 0..1.
 *   EMERGENCY_SULFUR_SPRAY  — sulfurSpray01 lever from dayOfYear; value 0..1.
 *   EMERGENCY_NETTING       — netting lever from dayOfYear; value>=0.5 = enable.
 */
USTRUCT(BlueprintType)
struct QVEVRI_API FVineyardActionRequest
{
    GENERATED_BODY()

    /** Wire key: "characterId" */
    UPROPERTY(BlueprintReadWrite, Category = "Qvevri|Estate")
    int64 characterId = 0;

    /** Wire key: "dayOfYear" — must be in 0..364. */
    UPROPERTY(BlueprintReadWrite, Category = "Qvevri|Estate")
    int32 dayOfYear = 0;

    /** Wire key: "actionType" — one of the EMERGENCY_* strings above. */
    UPROPERTY(BlueprintReadWrite, Category = "Qvevri|Estate")
    FString actionType;

    /** Wire key: "value" — intensity 0..1 (or >=0.5 for netting toggle). */
    UPROPERTY(BlueprintReadWrite, Category = "Qvevri|Estate")
    double value = 0.0;
};
