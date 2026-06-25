// QvevriMarketDtos.h
// QVEVRI — Unreal Engine 5 client.
// DTOs for cellar, market, and shared bottle/vintage types.
// All field names mirror the JSON wire contract in docs/API.md §8 EXACTLY.
//
// COVERAGE:
//   GET  /api/cellar/{characterId}          -> TArray<FCellarItem>
//   POST /api/cellar/{characterId}/grow     -> FGrowRequest / FGrowResponse
//   GET  /api/market                        -> TArray<FMarketListingView>
//   POST /api/market/list                   -> FListOnMarketRequest / FMarketListing
//   POST /api/market/buy                    -> FBuyRequest / FTradeRecord
//
// Also defines FBottleSummary and FVintageSummary used by estate responses.
//
// Owned by: Unreal client lane.

#pragma once

#include "CoreMinimal.h"
#include "QvevriMarketDtos.generated.h"

// ---------------------------------------------------------------------------
// Shared wine summary types (embedded in cellar + harvest responses)
// ---------------------------------------------------------------------------

/**
 * Bottle summary embedded in grow and harvest responses.
 * Wire keys all lowercase to match the C# JsonProperty annotations exactly.
 */
USTRUCT(BlueprintType)
struct QVEVRI_API FBottleSummary
{
    GENERATED_BODY()

    UPROPERTY(BlueprintReadOnly, Category = "Qvevri|Market")
    FString variety;

    UPROPERTY(BlueprintReadOnly, Category = "Qvevri|Market")
    FString style;

    UPROPERTY(BlueprintReadOnly, Category = "Qvevri|Market")
    int32 vintageYear = 0;

    UPROPERTY(BlueprintReadOnly, Category = "Qvevri|Market")
    double quality = 0.0;

    UPROPERTY(BlueprintReadOnly, Category = "Qvevri|Market")
    double abv = 0.0;

    /** Wire key: "fault" — null / empty string when no fault. */
    UPROPERTY(BlueprintReadOnly, Category = "Qvevri|Market")
    FString fault;

    UPROPERTY(BlueprintReadOnly, Category = "Qvevri|Market")
    FString label;
};

/**
 * Vintage summary embedded in GrowResponse.
 * Wire keys match C# annotations (lowercase).
 */
USTRUCT(BlueprintType)
struct QVEVRI_API FVintageSummary
{
    GENERATED_BODY()

    UPROPERTY(BlueprintReadOnly, Category = "Qvevri|Market")
    int32 year = 0;

    UPROPERTY(BlueprintReadOnly, Category = "Qvevri|Market")
    double gddSeason = 0.0;

    UPROPERTY(BlueprintReadOnly, Category = "Qvevri|Market")
    FString winkler;

    UPROPERTY(BlueprintReadOnly, Category = "Qvevri|Market")
    FString pattern;
};

// ---------------------------------------------------------------------------
// Cellar  —  GET /api/cellar/{characterId}
// ---------------------------------------------------------------------------

/**
 * A bottle / inventory item.
 * Wire: { "id": 1, "characterId": 5, "itemType": "AGED_WINE",
 *         "quantity": 12.4, "quality": 78.3, "vintageYear": 1,
 *         "style": "RED", "appellationOk": true,
 *         "label": "...", "escrowed": false, "createdAt": 1718500000000 }
 */
USTRUCT(BlueprintType)
struct QVEVRI_API FCellarItem
{
    GENERATED_BODY()

    UPROPERTY(BlueprintReadOnly, Category = "Qvevri|Market")
    int64 id = 0;

    UPROPERTY(BlueprintReadOnly, Category = "Qvevri|Market")
    int64 characterId = 0;

    /** Wire key: "itemType" — e.g. "AGED_WINE". */
    UPROPERTY(BlueprintReadOnly, Category = "Qvevri|Market")
    FString itemType;

    UPROPERTY(BlueprintReadOnly, Category = "Qvevri|Market")
    double quantity = 0.0;

    UPROPERTY(BlueprintReadOnly, Category = "Qvevri|Market")
    double quality = 0.0;

    UPROPERTY(BlueprintReadOnly, Category = "Qvevri|Market")
    int32 vintageYear = 0;

    UPROPERTY(BlueprintReadOnly, Category = "Qvevri|Market")
    FString style;

    UPROPERTY(BlueprintReadOnly, Category = "Qvevri|Market")
    bool appellationOk = false;

    UPROPERTY(BlueprintReadOnly, Category = "Qvevri|Market")
    FString label;

    /** Wire key: "escrowed" — true when the item is locked for a market listing. */
    UPROPERTY(BlueprintReadOnly, Category = "Qvevri|Market")
    bool escrowed = false;

    /** Wire key: "createdAt" — epoch milliseconds. */
    UPROPERTY(BlueprintReadOnly, Category = "Qvevri|Market")
    int64 createdAt = 0;
};

/**
 * Request body for POST /api/cellar/{characterId}/grow.
 * Wire: { "seed": 42, "budLoad": 12, "pickDay": 270, "threats": true }
 * All fields optional on the server (defaults: seed=random, budLoad=12,
 * pickDay=270, threats=false).
 */
USTRUCT(BlueprintType)
struct QVEVRI_API FGrowRequest
{
    GENERATED_BODY()

    UPROPERTY(BlueprintReadWrite, Category = "Qvevri|Market")
    int64 seed = 42;

    UPROPERTY(BlueprintReadWrite, Category = "Qvevri|Market")
    int32 budLoad = 12;

    UPROPERTY(BlueprintReadWrite, Category = "Qvevri|Market")
    int32 pickDay = 270;

    UPROPERTY(BlueprintReadWrite, Category = "Qvevri|Market")
    bool threats = false;
};

/**
 * VineyardYearResult embedded in grow response.
 * Wire key: "result" (GrowResponse.result in API.md §8).
 * Note: the C# code also has a "vineyardYearResult" wrapper — the server
 * key is "result" per API.md §8 ("result": { VineyardYearResult }).
 *
 * COMPILE NOTE: verify the exact server key by inspecting the actual JSON.
 * The C# DTO uses [JsonProperty("vineyardYearResult")] which suggests the
 * server may nest under that key; fallback — rename this field accordingly.
 */
USTRUCT(BlueprintType)
struct QVEVRI_API FVineyardYearResult
{
    GENERATED_BODY()

    UPROPERTY(BlueprintReadOnly, Category = "Qvevri|Market")
    int64 seed = 0;

    UPROPERTY(BlueprintReadOnly, Category = "Qvevri|Market")
    int32 pickDay = 0;

    UPROPERTY(BlueprintReadOnly, Category = "Qvevri|Market")
    double suitability = 0.0;

    UPROPERTY(BlueprintReadOnly, Category = "Qvevri|Market")
    FVintageSummary vintage;

    UPROPERTY(BlueprintReadOnly, Category = "Qvevri|Market")
    FBottleSummary bottle;
};

/**
 * Response from POST /api/cellar/{characterId}/grow.
 * Wire: { "cellarItem": { ... }, "result": { ... } }
 *
 * COMPILE NOTE: API.md §8 names the nested key "result" but the C# DTO
 * maps it as [JsonProperty("vineyardYearResult")].  The field below is
 * named "vineyardYearResult" to match the C# wire observation; verify
 * against a live server response and rename if needed.
 */
USTRUCT(BlueprintType)
struct QVEVRI_API FGrowResponse
{
    GENERATED_BODY()

    /** Wire key: "cellarItem" */
    UPROPERTY(BlueprintReadOnly, Category = "Qvevri|Market")
    FCellarItem cellarItem;

    /** Wire key: "vineyardYearResult" (per C# DTO) — see COMPILE NOTE above. */
    UPROPERTY(BlueprintReadOnly, Category = "Qvevri|Market")
    FVineyardYearResult vineyardYearResult;
};

// ---------------------------------------------------------------------------
// Market  —  GET /api/market  +  POST /api/market/list  +  POST /api/market/buy
// ---------------------------------------------------------------------------

/**
 * Raw listing entity.
 * Returned by POST /api/market/list, and nested inside FMarketListingView.
 * Wire: { "id": 1, "sellerCharacterId": 5, "cellarItemId": 1,
 *         "askPrice": 50.0, "status": "ACTIVE", "createdAt": ... }
 */
USTRUCT(BlueprintType)
struct QVEVRI_API FMarketListing
{
    GENERATED_BODY()

    UPROPERTY(BlueprintReadOnly, Category = "Qvevri|Market")
    int64 id = 0;

    UPROPERTY(BlueprintReadOnly, Category = "Qvevri|Market")
    int64 sellerCharacterId = 0;

    UPROPERTY(BlueprintReadOnly, Category = "Qvevri|Market")
    int64 cellarItemId = 0;

    UPROPERTY(BlueprintReadOnly, Category = "Qvevri|Market")
    double askPrice = 0.0;

    /** Wire key: "status" — e.g. "ACTIVE". */
    UPROPERTY(BlueprintReadOnly, Category = "Qvevri|Market")
    FString status;

    /** Wire key: "createdAt" — epoch milliseconds. */
    UPROPERTY(BlueprintReadOnly, Category = "Qvevri|Market")
    int64 createdAt = 0;
};

/**
 * One row from GET /api/market.
 * Wire: { "listing": { ... }, "cellarItem": { ... }, "suggestedPrice": 47.23 }
 */
USTRUCT(BlueprintType)
struct QVEVRI_API FMarketListingView
{
    GENERATED_BODY()

    UPROPERTY(BlueprintReadOnly, Category = "Qvevri|Market")
    FMarketListing listing;

    UPROPERTY(BlueprintReadOnly, Category = "Qvevri|Market")
    FCellarItem cellarItem;

    UPROPERTY(BlueprintReadOnly, Category = "Qvevri|Market")
    double suggestedPrice = 0.0;
};

/**
 * Request body for POST /api/market/list.
 * Wire: { "characterId": 5, "cellarItemId": 1, "askPrice": 50.0 }
 */
USTRUCT(BlueprintType)
struct QVEVRI_API FListOnMarketRequest
{
    GENERATED_BODY()

    UPROPERTY(BlueprintReadWrite, Category = "Qvevri|Market")
    int64 characterId = 0;

    UPROPERTY(BlueprintReadWrite, Category = "Qvevri|Market")
    int64 cellarItemId = 0;

    UPROPERTY(BlueprintReadWrite, Category = "Qvevri|Market")
    double askPrice = 0.0;
};

/**
 * Request body for POST /api/market/buy.
 * Wire: { "characterId": 7, "listingId": 1 }
 */
USTRUCT(BlueprintType)
struct QVEVRI_API FBuyRequest
{
    GENERATED_BODY()

    UPROPERTY(BlueprintReadWrite, Category = "Qvevri|Market")
    int64 characterId = 0;

    UPROPERTY(BlueprintReadWrite, Category = "Qvevri|Market")
    int64 listingId = 0;
};

/**
 * Response from POST /api/market/buy — a TradeRecord.
 * Wire: { "id": 1, "buyerCharacterId": 7, "sellerCharacterId": 5,
 *         "cellarItemId": 1, "price": 50.0, "createdAt": ... }
 */
USTRUCT(BlueprintType)
struct QVEVRI_API FTradeRecord
{
    GENERATED_BODY()

    UPROPERTY(BlueprintReadOnly, Category = "Qvevri|Market")
    int64 id = 0;

    UPROPERTY(BlueprintReadOnly, Category = "Qvevri|Market")
    int64 buyerCharacterId = 0;

    UPROPERTY(BlueprintReadOnly, Category = "Qvevri|Market")
    int64 sellerCharacterId = 0;

    UPROPERTY(BlueprintReadOnly, Category = "Qvevri|Market")
    int64 cellarItemId = 0;

    UPROPERTY(BlueprintReadOnly, Category = "Qvevri|Market")
    double price = 0.0;

    /** Wire key: "createdAt" — epoch milliseconds. */
    UPROPERTY(BlueprintReadOnly, Category = "Qvevri|Market")
    int64 createdAt = 0;
};
