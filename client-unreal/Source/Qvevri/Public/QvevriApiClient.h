// QvevriApiClient.h
// QVEVRI — Unreal Engine 5 client.
// UGameInstanceSubsystem that owns all HTTP communication with the Spring Boot
// backend.  It is globally accessible via:
//
//   UQvevriApiClient* Api = GameInstance->GetSubsystem<UQvevriApiClient>();
//
// or from Blueprint via "Get Qvevri Api Client" (auto-exposed by subsystem).
//
// Design mirrors WebMmoApi.cs:
//   - Default base URL: http://localhost:8080  (override via SetBaseUrl).
//   - Bearer token stored internally; set via SetToken / cleared via ClearToken.
//   - All HTTP completion runs on the game thread (FHttpModule guarantees this).
//   - Callbacks: TFunction<void(const FResult&)> OnSuccess +
//                TFunction<void(const FQvevriApiError&)> OnError.
//
// Endpoints mirrored from IMmoApi.cs (the full surface):
//   Account   : Register, Login
//   World     : GetRegions, GetCareers, GetWorldClock, AdvanceClock
//   Characters: CreateCharacter, GetCharacters
//   Cellar    : GetCellar, Grow
//   Market    : GetMarket, ListOnMarket, Buy
//   Vineyards : PlantVineyard, GetVineyards, HarvestVineyard
//   Management: GetManagementPlan, ManageVineyard, PostVineyardAction
//
// Owned by: Unreal client lane.

#pragma once

#include "CoreMinimal.h"
#include "Subsystems/GameInstanceSubsystem.h"
#include "Interfaces/IHttpRequest.h"
#include "Interfaces/IHttpResponse.h"
#include "QvevriDtos.h"
#include "QvevriMarketDtos.h"
#include "QvevriEstateDtos.h"
#include "QvevriSession.h"
#include "QvevriApiClient.generated.h"

/**
 * Central HTTP client subsystem.
 *
 * USAGE FROM C++:
 * @code
 *   UQvevriApiClient* Api = GetGameInstance()->GetSubsystem<UQvevriApiClient>();
 *
 *   FLoginRequest Req;
 *   Req.username = TEXT("lasha");
 *   Req.password = TEXT("secret");
 *
 *   Api->Login(Req,
 *       [this](const FAuthResponse& Auth)
 *       {
 *           Api->SetToken(Auth.token);
 *           Api->Session.AccountId = Auth.accountId;
 *           Api->Session.Token     = Auth.token;
 *       },
 *       [this](const FQvevriApiError& Err)
 *       {
 *           UE_LOG(LogQvevri, Warning, TEXT("Login failed: %s"), *Err.Message);
 *       });
 * @endcode
 *
 * USAGE FROM BLUEPRINT:
 * Blueprint-callable wrappers are marked UFUNCTION(BlueprintCallable).
 * They use latent / async patterns; see K2_ variants (Blueprint-only) below.
 * The TFunction-based C++ overloads are the primary API for game code.
 */
UCLASS()
class QVEVRI_API UQvevriApiClient : public UGameInstanceSubsystem
{
    GENERATED_BODY()

public:
    // -----------------------------------------------------------------------
    // UGameInstanceSubsystem lifecycle
    // -----------------------------------------------------------------------

    virtual void Initialize(FSubsystemCollectionBase& Collection) override;
    virtual void Deinitialize() override;

    // -----------------------------------------------------------------------
    // Configuration
    // -----------------------------------------------------------------------

    /**
     * Override the backend base URL (default: http://localhost:8080).
     * Call before making any requests — e.g. from your GameInstance::Init().
     * Do NOT include a trailing slash.
     */
    UFUNCTION(BlueprintCallable, Category = "Qvevri|Config")
    void SetBaseUrl(const FString& Url);

    /**
     * Store the bearer token returned by Login / Register.
     * All subsequent protected requests will include
     * "Authorization: Bearer <Token>" automatically.
     */
    UFUNCTION(BlueprintCallable, Category = "Qvevri|Config")
    void SetToken(const FString& Token);

    /** Remove the stored bearer token (effectively logs out at the HTTP layer). */
    UFUNCTION(BlueprintCallable, Category = "Qvevri|Config")
    void ClearToken();

    /**
     * Returns true when a bearer token is stored.
     * Does NOT validate the token with the server.
     */
    UFUNCTION(BlueprintCallable, Category = "Qvevri|Config")
    bool HasToken() const;

    // -----------------------------------------------------------------------
    // Session state  (read/write from game code and UI)
    // -----------------------------------------------------------------------

    /**
     * Mutable session state — populated by game code after successful auth.
     * Exposed as UPROPERTY so Blueprint can read AccountId etc.
     */
    UPROPERTY(BlueprintReadWrite, Category = "Qvevri|Session")
    FQvevriSession Session;

    // -----------------------------------------------------------------------
    // Account endpoints  (no bearer token required)
    // -----------------------------------------------------------------------

    /**
     * POST /api/account/register
     * Creates a new account. On success, call SetToken(Auth.token) and store
     * Auth.accountId in Session.
     */
    void Register(
        const FRegisterRequest& Request,
        TFunction<void(const FAuthResponse&)> OnSuccess,
        TFunction<void(const FQvevriApiError&)> OnError);

    /**
     * POST /api/account/login
     * Authenticates an existing account.
     */
    void Login(
        const FLoginRequest& Request,
        TFunction<void(const FAuthResponse&)> OnSuccess,
        TFunction<void(const FQvevriApiError&)> OnError);

    // -----------------------------------------------------------------------
    // World endpoints  (no bearer token required)
    // -----------------------------------------------------------------------

    /**
     * GET /api/world/regions
     * Returns all regions. No auth required.
     * NOTE: FRegionInfo now includes latitude/longitude fields that will be
     * 0.0 until the backend lane delivers them.
     */
    void GetRegions(
        TFunction<void(const TArray<FRegionInfo>&)> OnSuccess,
        TFunction<void(const FQvevriApiError&)> OnError);

    /**
     * GET /api/world/careers
     * Returns all career types. No auth required.
     */
    void GetCareers(
        TFunction<void(const TArray<FCareerInfo>&)> OnSuccess,
        TFunction<void(const FQvevriApiError&)> OnError);

    /**
     * GET /api/world/clock
     * Returns the current shared world time. No auth required.
     */
    void GetWorldClock(
        TFunction<void(const FWorldClock&)> OnSuccess,
        TFunction<void(const FQvevriApiError&)> OnError);

    /**
     * POST /api/world/advance   (dev / test only)
     * Advances the world clock by N days. No auth required.
     */
    void AdvanceClock(
        const FAdvanceClockRequest& Request,
        TFunction<void(const FWorldClock&)> OnSuccess,
        TFunction<void(const FQvevriApiError&)> OnError);

    // -----------------------------------------------------------------------
    // Character endpoints  (bearer token required)
    // -----------------------------------------------------------------------

    /**
     * GET /api/characters
     * Returns all characters owned by the authenticated account.
     */
    void GetCharacters(
        TFunction<void(const TArray<FQvevriCharacter>&)> OnSuccess,
        TFunction<void(const FQvevriApiError&)> OnError);

    /**
     * POST /api/characters
     * Creates a new character for the authenticated account.
     */
    void CreateCharacter(
        const FCreateCharacterRequest& Request,
        TFunction<void(const FQvevriCharacter&)> OnSuccess,
        TFunction<void(const FQvevriApiError&)> OnError);

    // -----------------------------------------------------------------------
    // Cellar endpoints  (bearer token required)
    // -----------------------------------------------------------------------

    /**
     * GET /api/cellar/{characterId}
     * Returns non-escrowed cellar items for the given character.
     */
    void GetCellar(
        int64 CharacterId,
        TFunction<void(const TArray<FCellarItem>&)> OnSuccess,
        TFunction<void(const FQvevriApiError&)> OnError);

    /**
     * POST /api/cellar/{characterId}/grow
     * Runs a quick one-season vineyard simulation and deposits the result.
     */
    void Grow(
        int64 CharacterId,
        const FGrowRequest& Request,
        TFunction<void(const FGrowResponse&)> OnSuccess,
        TFunction<void(const FQvevriApiError&)> OnError);

    // -----------------------------------------------------------------------
    // Market endpoints  (bearer token required)
    // -----------------------------------------------------------------------

    /**
     * GET /api/market
     * Returns all ACTIVE listings with suggestedPrice from WinePricer.
     */
    void GetMarket(
        TFunction<void(const TArray<FMarketListingView>&)> OnSuccess,
        TFunction<void(const FQvevriApiError&)> OnError);

    /**
     * POST /api/market/list
     * Creates an ACTIVE listing; escrowed=true on the CellarItem.
     */
    void ListOnMarket(
        const FListOnMarketRequest& Request,
        TFunction<void(const FMarketListing&)> OnSuccess,
        TFunction<void(const FQvevriApiError&)> OnError);

    /**
     * POST /api/market/buy
     * Purchases a listing atomically.
     */
    void Buy(
        const FBuyRequest& Request,
        TFunction<void(const FTradeRecord&)> OnSuccess,
        TFunction<void(const FQvevriApiError&)> OnError);

    // -----------------------------------------------------------------------
    // Vineyard endpoints  (bearer token required)
    // -----------------------------------------------------------------------

    /**
     * POST /api/vineyards
     * Plants a new persistent vineyard. Returns 201 with FVineyardEntity.
     */
    void PlantVineyard(
        const FPlantVineyardRequest& Request,
        TFunction<void(const FVineyardEntity&)> OnSuccess,
        TFunction<void(const FQvevriApiError&)> OnError);

    /**
     * GET /api/vineyards/{characterId}
     * Returns live state of all vineyards, replayed to the current world day.
     */
    void GetVineyards(
        int64 CharacterId,
        TFunction<void(const TArray<FVineyardView>&)> OnSuccess,
        TFunction<void(const FQvevriApiError&)> OnError);

    /**
     * POST /api/vineyards/{vineyardId}/harvest
     * Harvests a ripe vineyard and deposits a CellarItem.
     */
    void HarvestVineyard(
        int64 VineyardId,
        const FHarvestRequest& Request,
        TFunction<void(const FHarvestResponse&)> OnSuccess,
        TFunction<void(const FQvevriApiError&)> OnError);

    // -----------------------------------------------------------------------
    // Management Plan endpoints  (bearer token required)
    // -----------------------------------------------------------------------

    /**
     * GET /api/vineyards/{vineyardId}/management
     * Fetches the current management lever snapshot.
     */
    void GetManagementPlan(
        int64 VineyardId,
        TFunction<void(const FManagementPlan&)> OnSuccess,
        TFunction<void(const FQvevriApiError&)> OnError);

    /**
     * POST /api/vineyards/{vineyardId}/manage
     * Applies a (partial) management plan update; returns updated VineyardView.
     */
    void ManageVineyard(
        int64 VineyardId,
        const FManageRequest& Request,
        TFunction<void(const FVineyardView&)> OnSuccess,
        TFunction<void(const FQvevriApiError&)> OnError);

    /**
     * POST /api/vineyards/{vineyardId}/action
     * Records a dated per-day tending action; returns updated VineyardView.
     */
    void PostVineyardAction(
        int64 VineyardId,
        const FVineyardActionRequest& Request,
        TFunction<void(const FVineyardView&)> OnSuccess,
        TFunction<void(const FQvevriApiError&)> OnError);

private:
    // -----------------------------------------------------------------------
    // Internal state
    // -----------------------------------------------------------------------

    /** Base URL without trailing slash. Default: http://localhost:8080 */
    FString BaseUrl = TEXT("http://localhost:8080");

    /** Bearer token.  Empty = unauthenticated. */
    FString BearerToken;

    // -----------------------------------------------------------------------
    // HTTP helper methods
    // -----------------------------------------------------------------------

    /**
     * Issue a GET request.
     *
     * @param Path       URL path, e.g. "/api/world/regions"
     * @param bRequireAuth  If true, adds the Authorization header; aborts with
     *                      FQvevriApiError{0, "UNAUTHORIZED", ...} if no token.
     * @param OnSuccess  Callback with the raw response body string (game thread).
     * @param OnError    Callback with structured error (game thread).
     */
    void DoGet(
        const FString& Path,
        bool bRequireAuth,
        TFunction<void(const FString& /*ResponseBody*/)> OnSuccess,
        TFunction<void(const FQvevriApiError&)> OnError);

    /**
     * Issue a POST request with a JSON body.
     *
     * @param Path       URL path, e.g. "/api/account/login"
     * @param Body       JSON string (produced by FJsonObjectConverter or manually).
     * @param bRequireAuth  As above.
     * @param OnSuccess  Callback with raw response body.
     * @param OnError    Callback with structured error.
     */
    void DoPost(
        const FString& Path,
        const FString& Body,
        bool bRequireAuth,
        TFunction<void(const FString&)> OnSuccess,
        TFunction<void(const FQvevriApiError&)> OnError);

    /**
     * Shared response handler.  Checks HTTP status; on error parses the
     * { "error": { "code", "message" } } envelope into FQvevriApiError.
     * Calls OnSuccess(ResponseBody) on 2xx, OnError(...) otherwise.
     */
    void HandleResponse(
        FHttpResponsePtr Response,
        bool bConnectedSuccessfully,
        TFunction<void(const FString&)> OnSuccess,
        TFunction<void(const FQvevriApiError&)> OnError);

    /**
     * Deserialise a USTRUCT from a JSON string using FJsonObjectConverter.
     * Returns false and populates OutError if parsing fails.
     *
     * COMPILE NOTE: FJsonObjectConverter::JsonObjectStringToUStruct is
     * available in the "JsonUtilities" module.  Ensure "JsonUtilities" is in
     * PublicDependencyModuleNames in Qvevri.Build.cs.
     */
    template <typename TStruct>
    bool ParseJsonResponse(
        const FString& JsonString,
        TStruct& OutResult,
        FQvevriApiError& OutError);

    /**
     * Deserialise a TArray<TStruct> from a top-level JSON array string.
     * FJsonObjectConverter does not handle root arrays directly; this helper
     * wraps the JSON in {"items":[...]} and deserialises the items field.
     *
     * COMPILE NOTE: Verify this approach compiles correctly — an alternative
     * is to manually iterate the TJsonReader array and call
     * FJsonObjectConverter::JsonObjectToUStruct per element.
     */
    template <typename TStruct>
    bool ParseJsonArrayResponse(
        const FString& JsonString,
        TArray<TStruct>& OutResult,
        FQvevriApiError& OutError);

    /**
     * Serialise a USTRUCT to a compact JSON string.
     * Uses FJsonObjectConverter::UStructToJsonObjectString.
     */
    template <typename TStruct>
    FString SerializeRequest(const TStruct& InStruct);

    // -----------------------------------------------------------------------
    // Static parse helpers (called from lambdas — must be accessible without
    // a this-capture of the subsystem pointer lifetime)
    // -----------------------------------------------------------------------

    /**
     * Deserialise a single JSON object string into a USTRUCT.
     * Static so it can be called from TFunction lambdas without capturing
     * the UObject pointer, which can become stale.
     */
    template <typename TStruct>
    static bool StaticParseJson(
        const FString& JsonString,
        TStruct& OutResult,
        FQvevriApiError& OutError);

    /**
     * Deserialise a JSON array string into a TArray<TStruct>.
     * Iterates the root array and calls FJsonObjectConverter per element.
     */
    template <typename TStruct>
    static bool StaticParseJsonArray(
        const FString& JsonString,
        TArray<TStruct>& OutResult,
        FQvevriApiError& OutError);
};
