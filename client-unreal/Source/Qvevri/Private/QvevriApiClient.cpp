// QvevriApiClient.cpp
// QVEVRI — Unreal Engine 5 client.
// Implementation of UQvevriApiClient — the HTTP + JSON networking layer.
//
// All calls flow through DoGet / DoPost -> HandleResponse -> parse callback.
// FHttpModule dispatches completion on the game thread, so no explicit
// marshalling is needed.
//
// JSON serialisation/deserialisation:
//   Requests  : FJsonObjectConverter::UStructToJsonObjectString
//   Responses : FJsonObjectConverter::JsonObjectStringToUStruct
//              + manual array handling via TJsonReaderFactory
//
// Owned by: Unreal client lane.

#include "QvevriApiClient.h"

#include "HttpModule.h"
#include "Interfaces/IHttpRequest.h"
#include "Interfaces/IHttpResponse.h"
#include "JsonObjectConverter.h"       // FJsonObjectConverter (JsonUtilities)
#include "Serialization/JsonReader.h"
#include "Serialization/JsonSerializer.h"
#include "Dom/JsonObject.h"
#include "Dom/JsonValue.h"

DEFINE_LOG_CATEGORY_STATIC(LogQvevri, Log, All);

// ---------------------------------------------------------------------------
// UGameInstanceSubsystem lifecycle
// ---------------------------------------------------------------------------

void UQvevriApiClient::Initialize(FSubsystemCollectionBase& Collection)
{
    Super::Initialize(Collection);
    UE_LOG(LogQvevri, Log, TEXT("QvevriApiClient initialised. BaseUrl=%s"), *BaseUrl);
}

void UQvevriApiClient::Deinitialize()
{
    Super::Deinitialize();
}

// ---------------------------------------------------------------------------
// Configuration
// ---------------------------------------------------------------------------

void UQvevriApiClient::SetBaseUrl(const FString& Url)
{
    // Strip trailing slash to mirror WebMmoApi.cs: _baseUrl.TrimEnd('/')
    BaseUrl = Url;
    BaseUrl.RemoveFromEnd(TEXT("/"));
    UE_LOG(LogQvevri, Log, TEXT("QvevriApiClient BaseUrl set to: %s"), *BaseUrl);
}

void UQvevriApiClient::SetToken(const FString& Token)
{
    BearerToken = Token;
}

void UQvevriApiClient::ClearToken()
{
    BearerToken.Empty();
    Session.Clear();
}

bool UQvevriApiClient::HasToken() const
{
    return !BearerToken.IsEmpty();
}

// ---------------------------------------------------------------------------
// Account
// ---------------------------------------------------------------------------

void UQvevriApiClient::Register(
    const FRegisterRequest& Request,
    TFunction<void(const FAuthResponse&)> OnSuccess,
    TFunction<void(const FQvevriApiError&)> OnError)
{
    DoPost(
        TEXT("/api/account/register"),
        SerializeRequest(Request),
        /*bRequireAuth=*/false,
        [OnSuccess, OnError](const FString& Body)
        {
            FAuthResponse Result;
            FQvevriApiError ParseError;
            if (UQvevriApiClient::StaticParseJson(Body, Result, ParseError))
            {
                OnSuccess(Result);
            }
            else
            {
                OnError(ParseError);
            }
        },
        OnError);
}

void UQvevriApiClient::Login(
    const FLoginRequest& Request,
    TFunction<void(const FAuthResponse&)> OnSuccess,
    TFunction<void(const FQvevriApiError&)> OnError)
{
    DoPost(
        TEXT("/api/account/login"),
        SerializeRequest(Request),
        /*bRequireAuth=*/false,
        [OnSuccess, OnError](const FString& Body)
        {
            FAuthResponse Result;
            FQvevriApiError ParseError;
            if (UQvevriApiClient::StaticParseJson(Body, Result, ParseError))
            {
                OnSuccess(Result);
            }
            else
            {
                OnError(ParseError);
            }
        },
        OnError);
}

// ---------------------------------------------------------------------------
// World
// ---------------------------------------------------------------------------

void UQvevriApiClient::GetRegions(
    TFunction<void(const TArray<FRegionInfo>&)> OnSuccess,
    TFunction<void(const FQvevriApiError&)> OnError)
{
    DoGet(
        TEXT("/api/world/regions"),
        /*bRequireAuth=*/false,
        [OnSuccess, OnError](const FString& Body)
        {
            TArray<FRegionInfo> Results;
            FQvevriApiError ParseError;
            if (UQvevriApiClient::StaticParseJsonArray(Body, Results, ParseError))
            {
                OnSuccess(Results);
            }
            else
            {
                OnError(ParseError);
            }
        },
        OnError);
}

void UQvevriApiClient::GetCareers(
    TFunction<void(const TArray<FCareerInfo>&)> OnSuccess,
    TFunction<void(const FQvevriApiError&)> OnError)
{
    DoGet(
        TEXT("/api/world/careers"),
        /*bRequireAuth=*/false,
        [OnSuccess, OnError](const FString& Body)
        {
            TArray<FCareerInfo> Results;
            FQvevriApiError ParseError;
            if (UQvevriApiClient::StaticParseJsonArray(Body, Results, ParseError))
            {
                OnSuccess(Results);
            }
            else
            {
                OnError(ParseError);
            }
        },
        OnError);
}

void UQvevriApiClient::GetWorldClock(
    TFunction<void(const FWorldClock&)> OnSuccess,
    TFunction<void(const FQvevriApiError&)> OnError)
{
    DoGet(
        TEXT("/api/world/clock"),
        /*bRequireAuth=*/false,
        [OnSuccess, OnError](const FString& Body)
        {
            FWorldClock Result;
            FQvevriApiError ParseError;
            if (UQvevriApiClient::StaticParseJson(Body, Result, ParseError))
            {
                OnSuccess(Result);
            }
            else
            {
                OnError(ParseError);
            }
        },
        OnError);
}

void UQvevriApiClient::AdvanceClock(
    const FAdvanceClockRequest& Request,
    TFunction<void(const FWorldClock&)> OnSuccess,
    TFunction<void(const FQvevriApiError&)> OnError)
{
    DoPost(
        TEXT("/api/world/advance"),
        SerializeRequest(Request),
        /*bRequireAuth=*/false,
        [OnSuccess, OnError](const FString& Body)
        {
            FWorldClock Result;
            FQvevriApiError ParseError;
            if (UQvevriApiClient::StaticParseJson(Body, Result, ParseError))
            {
                OnSuccess(Result);
            }
            else
            {
                OnError(ParseError);
            }
        },
        OnError);
}

// ---------------------------------------------------------------------------
// Characters
// ---------------------------------------------------------------------------

void UQvevriApiClient::GetCharacters(
    TFunction<void(const TArray<FQvevriCharacter>&)> OnSuccess,
    TFunction<void(const FQvevriApiError&)> OnError)
{
    DoGet(
        TEXT("/api/characters"),
        /*bRequireAuth=*/true,
        [OnSuccess, OnError](const FString& Body)
        {
            TArray<FQvevriCharacter> Results;
            FQvevriApiError ParseError;
            if (UQvevriApiClient::StaticParseJsonArray(Body, Results, ParseError))
            {
                OnSuccess(Results);
            }
            else
            {
                OnError(ParseError);
            }
        },
        OnError);
}

void UQvevriApiClient::CreateCharacter(
    const FCreateCharacterRequest& Request,
    TFunction<void(const FQvevriCharacter&)> OnSuccess,
    TFunction<void(const FQvevriApiError&)> OnError)
{
    DoPost(
        TEXT("/api/characters"),
        SerializeRequest(Request),
        /*bRequireAuth=*/true,
        [OnSuccess, OnError](const FString& Body)
        {
            FQvevriCharacter Result;
            FQvevriApiError ParseError;
            if (UQvevriApiClient::StaticParseJson(Body, Result, ParseError))
            {
                OnSuccess(Result);
            }
            else
            {
                OnError(ParseError);
            }
        },
        OnError);
}

// ---------------------------------------------------------------------------
// Cellar
// ---------------------------------------------------------------------------

void UQvevriApiClient::GetCellar(
    int64 CharacterId,
    TFunction<void(const TArray<FCellarItem>&)> OnSuccess,
    TFunction<void(const FQvevriApiError&)> OnError)
{
    const FString Path = FString::Printf(TEXT("/api/cellar/%lld"), CharacterId);
    DoGet(
        Path,
        /*bRequireAuth=*/true,
        [OnSuccess, OnError](const FString& Body)
        {
            TArray<FCellarItem> Results;
            FQvevriApiError ParseError;
            if (UQvevriApiClient::StaticParseJsonArray(Body, Results, ParseError))
            {
                OnSuccess(Results);
            }
            else
            {
                OnError(ParseError);
            }
        },
        OnError);
}

void UQvevriApiClient::Grow(
    int64 CharacterId,
    const FGrowRequest& Request,
    TFunction<void(const FGrowResponse&)> OnSuccess,
    TFunction<void(const FQvevriApiError&)> OnError)
{
    const FString Path = FString::Printf(TEXT("/api/cellar/%lld/grow"), CharacterId);
    DoPost(
        Path,
        SerializeRequest(Request),
        /*bRequireAuth=*/true,
        [OnSuccess, OnError](const FString& Body)
        {
            FGrowResponse Result;
            FQvevriApiError ParseError;
            if (UQvevriApiClient::StaticParseJson(Body, Result, ParseError))
            {
                OnSuccess(Result);
            }
            else
            {
                OnError(ParseError);
            }
        },
        OnError);
}

// ---------------------------------------------------------------------------
// Market
// ---------------------------------------------------------------------------

void UQvevriApiClient::GetMarket(
    TFunction<void(const TArray<FMarketListingView>&)> OnSuccess,
    TFunction<void(const FQvevriApiError&)> OnError)
{
    DoGet(
        TEXT("/api/market"),
        /*bRequireAuth=*/true,
        [OnSuccess, OnError](const FString& Body)
        {
            TArray<FMarketListingView> Results;
            FQvevriApiError ParseError;
            if (UQvevriApiClient::StaticParseJsonArray(Body, Results, ParseError))
            {
                OnSuccess(Results);
            }
            else
            {
                OnError(ParseError);
            }
        },
        OnError);
}

void UQvevriApiClient::ListOnMarket(
    const FListOnMarketRequest& Request,
    TFunction<void(const FMarketListing&)> OnSuccess,
    TFunction<void(const FQvevriApiError&)> OnError)
{
    DoPost(
        TEXT("/api/market/list"),
        SerializeRequest(Request),
        /*bRequireAuth=*/true,
        [OnSuccess, OnError](const FString& Body)
        {
            FMarketListing Result;
            FQvevriApiError ParseError;
            if (UQvevriApiClient::StaticParseJson(Body, Result, ParseError))
            {
                OnSuccess(Result);
            }
            else
            {
                OnError(ParseError);
            }
        },
        OnError);
}

void UQvevriApiClient::Buy(
    const FBuyRequest& Request,
    TFunction<void(const FTradeRecord&)> OnSuccess,
    TFunction<void(const FQvevriApiError&)> OnError)
{
    DoPost(
        TEXT("/api/market/buy"),
        SerializeRequest(Request),
        /*bRequireAuth=*/true,
        [OnSuccess, OnError](const FString& Body)
        {
            FTradeRecord Result;
            FQvevriApiError ParseError;
            if (UQvevriApiClient::StaticParseJson(Body, Result, ParseError))
            {
                OnSuccess(Result);
            }
            else
            {
                OnError(ParseError);
            }
        },
        OnError);
}

// ---------------------------------------------------------------------------
// Vineyards
// ---------------------------------------------------------------------------

void UQvevriApiClient::PlantVineyard(
    const FPlantVineyardRequest& Request,
    TFunction<void(const FVineyardEntity&)> OnSuccess,
    TFunction<void(const FQvevriApiError&)> OnError)
{
    DoPost(
        TEXT("/api/vineyards"),
        SerializeRequest(Request),
        /*bRequireAuth=*/true,
        [OnSuccess, OnError](const FString& Body)
        {
            FVineyardEntity Result;
            FQvevriApiError ParseError;
            if (UQvevriApiClient::StaticParseJson(Body, Result, ParseError))
            {
                OnSuccess(Result);
            }
            else
            {
                OnError(ParseError);
            }
        },
        OnError);
}

void UQvevriApiClient::GetVineyards(
    int64 CharacterId,
    TFunction<void(const TArray<FVineyardView>&)> OnSuccess,
    TFunction<void(const FQvevriApiError&)> OnError)
{
    const FString Path = FString::Printf(TEXT("/api/vineyards/%lld"), CharacterId);
    DoGet(
        Path,
        /*bRequireAuth=*/true,
        [OnSuccess, OnError](const FString& Body)
        {
            TArray<FVineyardView> Results;
            FQvevriApiError ParseError;
            if (UQvevriApiClient::StaticParseJsonArray(Body, Results, ParseError))
            {
                OnSuccess(Results);
            }
            else
            {
                OnError(ParseError);
            }
        },
        OnError);
}

void UQvevriApiClient::HarvestVineyard(
    int64 VineyardId,
    const FHarvestRequest& Request,
    TFunction<void(const FHarvestResponse&)> OnSuccess,
    TFunction<void(const FQvevriApiError&)> OnError)
{
    const FString Path = FString::Printf(TEXT("/api/vineyards/%lld/harvest"), VineyardId);
    DoPost(
        Path,
        SerializeRequest(Request),
        /*bRequireAuth=*/true,
        [OnSuccess, OnError](const FString& Body)
        {
            FHarvestResponse Result;
            FQvevriApiError ParseError;
            if (UQvevriApiClient::StaticParseJson(Body, Result, ParseError))
            {
                OnSuccess(Result);
            }
            else
            {
                OnError(ParseError);
            }
        },
        OnError);
}

// ---------------------------------------------------------------------------
// Management Plan
// ---------------------------------------------------------------------------

void UQvevriApiClient::GetManagementPlan(
    int64 VineyardId,
    TFunction<void(const FManagementPlan&)> OnSuccess,
    TFunction<void(const FQvevriApiError&)> OnError)
{
    const FString Path = FString::Printf(TEXT("/api/vineyards/%lld/management"), VineyardId);
    DoGet(
        Path,
        /*bRequireAuth=*/true,
        [OnSuccess, OnError](const FString& Body)
        {
            FManagementPlan Result;
            FQvevriApiError ParseError;
            if (UQvevriApiClient::StaticParseJson(Body, Result, ParseError))
            {
                OnSuccess(Result);
            }
            else
            {
                OnError(ParseError);
            }
        },
        OnError);
}

void UQvevriApiClient::ManageVineyard(
    int64 VineyardId,
    const FManageRequest& Request,
    TFunction<void(const FVineyardView&)> OnSuccess,
    TFunction<void(const FQvevriApiError&)> OnError)
{
    const FString Path = FString::Printf(TEXT("/api/vineyards/%lld/manage"), VineyardId);
    DoPost(
        Path,
        SerializeRequest(Request),
        /*bRequireAuth=*/true,
        [OnSuccess, OnError](const FString& Body)
        {
            FVineyardView Result;
            FQvevriApiError ParseError;
            if (UQvevriApiClient::StaticParseJson(Body, Result, ParseError))
            {
                OnSuccess(Result);
            }
            else
            {
                OnError(ParseError);
            }
        },
        OnError);
}

void UQvevriApiClient::PostVineyardAction(
    int64 VineyardId,
    const FVineyardActionRequest& Request,
    TFunction<void(const FVineyardView&)> OnSuccess,
    TFunction<void(const FQvevriApiError&)> OnError)
{
    const FString Path = FString::Printf(TEXT("/api/vineyards/%lld/action"), VineyardId);
    DoPost(
        Path,
        SerializeRequest(Request),
        /*bRequireAuth=*/true,
        [OnSuccess, OnError](const FString& Body)
        {
            FVineyardView Result;
            FQvevriApiError ParseError;
            if (UQvevriApiClient::StaticParseJson(Body, Result, ParseError))
            {
                OnSuccess(Result);
            }
            else
            {
                OnError(ParseError);
            }
        },
        OnError);
}

// ---------------------------------------------------------------------------
// DoGet
// ---------------------------------------------------------------------------

void UQvevriApiClient::DoGet(
    const FString& Path,
    bool bRequireAuth,
    TFunction<void(const FString&)> OnSuccess,
    TFunction<void(const FQvevriApiError&)> OnError)
{
    if (bRequireAuth && BearerToken.IsEmpty())
    {
        FQvevriApiError Err;
        Err.HttpStatus = 0;
        Err.Code       = TEXT("UNAUTHORIZED");
        Err.Message    = TEXT("No bearer token — call Login or Register first.");
        OnError(Err);
        return;
    }

    const FString Url = BaseUrl + Path;

    TSharedRef<IHttpRequest, ESPMode::ThreadSafe> HttpRequest =
        FHttpModule::Get().CreateRequest();

    HttpRequest->SetURL(Url);
    HttpRequest->SetVerb(TEXT("GET"));
    HttpRequest->SetHeader(TEXT("Accept"), TEXT("application/json"));

    if (!BearerToken.IsEmpty())
    {
        HttpRequest->SetHeader(
            TEXT("Authorization"),
            FString::Printf(TEXT("Bearer %s"), *BearerToken));
    }

    // Capture callbacks by value so they outlive this stack frame.
    HttpRequest->OnProcessRequestComplete().BindLambda(
        [this, OnSuccess, OnError]
        (FHttpRequestPtr /*Req*/, FHttpResponsePtr Response, bool bSuccess)
        {
            HandleResponse(Response, bSuccess, OnSuccess, OnError);
        });

    HttpRequest->ProcessRequest();
}

// ---------------------------------------------------------------------------
// DoPost
// ---------------------------------------------------------------------------

void UQvevriApiClient::DoPost(
    const FString& Path,
    const FString& Body,
    bool bRequireAuth,
    TFunction<void(const FString&)> OnSuccess,
    TFunction<void(const FQvevriApiError&)> OnError)
{
    if (bRequireAuth && BearerToken.IsEmpty())
    {
        FQvevriApiError Err;
        Err.HttpStatus = 0;
        Err.Code       = TEXT("UNAUTHORIZED");
        Err.Message    = TEXT("No bearer token — call Login or Register first.");
        OnError(Err);
        return;
    }

    const FString Url = BaseUrl + Path;

    TSharedRef<IHttpRequest, ESPMode::ThreadSafe> HttpRequest =
        FHttpModule::Get().CreateRequest();

    HttpRequest->SetURL(Url);
    HttpRequest->SetVerb(TEXT("POST"));
    HttpRequest->SetHeader(TEXT("Content-Type"), TEXT("application/json"));
    HttpRequest->SetHeader(TEXT("Accept"),       TEXT("application/json"));
    HttpRequest->SetContentAsString(Body);

    if (!BearerToken.IsEmpty())
    {
        HttpRequest->SetHeader(
            TEXT("Authorization"),
            FString::Printf(TEXT("Bearer %s"), *BearerToken));
    }

    HttpRequest->OnProcessRequestComplete().BindLambda(
        [this, OnSuccess, OnError]
        (FHttpRequestPtr /*Req*/, FHttpResponsePtr Response, bool bSuccess)
        {
            HandleResponse(Response, bSuccess, OnSuccess, OnError);
        });

    HttpRequest->ProcessRequest();
}

// ---------------------------------------------------------------------------
// HandleResponse
// ---------------------------------------------------------------------------

void UQvevriApiClient::HandleResponse(
    FHttpResponsePtr Response,
    bool bConnectedSuccessfully,
    TFunction<void(const FString&)> OnSuccess,
    TFunction<void(const FQvevriApiError&)> OnError)
{
    // Network / connection failure (no HTTP response)
    if (!bConnectedSuccessfully || !Response.IsValid())
    {
        FQvevriApiError Err;
        Err.HttpStatus = 0;
        Err.Code       = TEXT("");
        Err.Message    = TEXT("Network error: no response received.");
        OnError(Err);
        return;
    }

    const int32 StatusCode  = Response->GetResponseCode();
    const FString RespBody  = Response->GetContentAsString();

    if (StatusCode >= 200 && StatusCode < 300)
    {
        OnSuccess(RespBody);
        return;
    }

    // Non-2xx: attempt to parse the standard error envelope
    // { "error": { "code": "...", "message": "..." } }
    FQvevriApiError Err;
    Err.HttpStatus = StatusCode;
    Err.Message    = RespBody; // fallback if envelope parse fails

    TSharedPtr<FJsonObject> JsonObject;
    TSharedRef<TJsonReader<>> Reader = TJsonReaderFactory<>::Create(RespBody);
    if (FJsonSerializer::Deserialize(Reader, JsonObject) && JsonObject.IsValid())
    {
        const TSharedPtr<FJsonObject>* ErrorObj = nullptr;
        if (JsonObject->TryGetObjectField(TEXT("error"), ErrorObj) &&
            ErrorObj && (*ErrorObj).IsValid())
        {
            FString Code, Message;
            (*ErrorObj)->TryGetStringField(TEXT("code"),    Code);
            (*ErrorObj)->TryGetStringField(TEXT("message"), Message);
            Err.Code    = Code;
            Err.Message = Message.IsEmpty() ? RespBody : Message;
        }
    }

    UE_LOG(LogQvevri, Warning,
        TEXT("HTTP %d [%s]: %s"), Err.HttpStatus, *Err.Code, *Err.Message);

    OnError(Err);
}

// ---------------------------------------------------------------------------
// StaticParseJson  (non-template static helpers — one per DTO type)
// ---------------------------------------------------------------------------
// FJsonObjectConverter::JsonObjectStringToUStruct is templated but we expose
// lightweight static wrappers to keep the lambda capture syntax clean.

template <typename TStruct>
bool UQvevriApiClient::StaticParseJson(
    const FString& JsonString,
    TStruct& OutResult,
    FQvevriApiError& OutError)
{
    if (!FJsonObjectConverter::JsonObjectStringToUStruct<TStruct>(
            JsonString,
            &OutResult,
            /*CheckFlags=*/0,
            /*SkipFlags=*/0))
    {
        OutError.HttpStatus = 0;
        OutError.Code       = TEXT("PARSE_ERROR");
        OutError.Message    = FString::Printf(
            TEXT("Failed to parse JSON response: %.200s"), *JsonString);
        UE_LOG(LogQvevri, Warning, TEXT("JSON parse error: %s"), *OutError.Message);
        return false;
    }
    return true;
}

template <typename TStruct>
bool UQvevriApiClient::StaticParseJsonArray(
    const FString& JsonString,
    TArray<TStruct>& OutResult,
    FQvevriApiError& OutError)
{
    // FJsonObjectConverter has no direct "root array to TArray" helper, so we
    // parse manually: iterate the root JSON array and deserialise each element.
    TArray<TSharedPtr<FJsonValue>> JsonArray;
    TSharedRef<TJsonReader<>> Reader = TJsonReaderFactory<>::Create(JsonString);

    if (!FJsonSerializer::Deserialize(Reader, JsonArray))
    {
        OutError.HttpStatus = 0;
        OutError.Code       = TEXT("PARSE_ERROR");
        OutError.Message    = FString::Printf(
            TEXT("Expected JSON array but got: %.200s"), *JsonString);
        UE_LOG(LogQvevri, Warning, TEXT("JSON array parse error: %s"), *OutError.Message);
        return false;
    }

    OutResult.Reset();
    OutResult.Reserve(JsonArray.Num());

    for (const TSharedPtr<FJsonValue>& Value : JsonArray)
    {
        const TSharedPtr<FJsonObject>* ObjPtr = nullptr;
        if (!Value->TryGetObject(ObjPtr) || !ObjPtr || !(*ObjPtr).IsValid())
        {
            OutError.HttpStatus = 0;
            OutError.Code       = TEXT("PARSE_ERROR");
            OutError.Message    = TEXT("JSON array element is not an object.");
            return false;
        }

        TStruct Item;
        if (!FJsonObjectConverter::JsonObjectToUStruct<TStruct>(
                (*ObjPtr).ToSharedRef(), &Item,
                /*CheckFlags=*/0, /*SkipFlags=*/0))
        {
            OutError.HttpStatus = 0;
            OutError.Code       = TEXT("PARSE_ERROR");
            OutError.Message    = TEXT("Failed to deserialise JSON array element.");
            UE_LOG(LogQvevri, Warning, TEXT("Array element parse error."));
            return false;
        }
        OutResult.Add(MoveTemp(Item));
    }
    return true;
}

template <typename TStruct>
FString UQvevriApiClient::SerializeRequest(const TStruct& InStruct)
{
    FString Output;
    // CheckFlags=0: include all UPROPERTY fields regardless of specifiers.
    FJsonObjectConverter::UStructToJsonObjectString<TStruct>(
        InStruct, Output, /*CheckFlags=*/0, /*SkipFlags=*/0);
    return Output;
}

// ---------------------------------------------------------------------------
// Explicit template instantiations
// ---------------------------------------------------------------------------
// Required because the template bodies are in the .cpp, not the header.
// Add one line per DTO type used with StaticParseJson / StaticParseJsonArray.

template bool UQvevriApiClient::StaticParseJson(const FString&, FAuthResponse&,       FQvevriApiError&);
template bool UQvevriApiClient::StaticParseJson(const FString&, FWorldClock&,          FQvevriApiError&);
template bool UQvevriApiClient::StaticParseJson(const FString&, FQvevriCharacter&,           FQvevriApiError&);
template bool UQvevriApiClient::StaticParseJson(const FString&, FGrowResponse&,        FQvevriApiError&);
template bool UQvevriApiClient::StaticParseJson(const FString&, FMarketListing&,       FQvevriApiError&);
template bool UQvevriApiClient::StaticParseJson(const FString&, FTradeRecord&,         FQvevriApiError&);
template bool UQvevriApiClient::StaticParseJson(const FString&, FVineyardEntity&,      FQvevriApiError&);
template bool UQvevriApiClient::StaticParseJson(const FString&, FHarvestResponse&,     FQvevriApiError&);
template bool UQvevriApiClient::StaticParseJson(const FString&, FManagementPlan&,      FQvevriApiError&);
template bool UQvevriApiClient::StaticParseJson(const FString&, FVineyardView&,        FQvevriApiError&);

template bool UQvevriApiClient::StaticParseJsonArray(const FString&, TArray<FRegionInfo>&,        FQvevriApiError&);
template bool UQvevriApiClient::StaticParseJsonArray(const FString&, TArray<FCareerInfo>&,         FQvevriApiError&);
template bool UQvevriApiClient::StaticParseJsonArray(const FString&, TArray<FQvevriCharacter>&,          FQvevriApiError&);
template bool UQvevriApiClient::StaticParseJsonArray(const FString&, TArray<FCellarItem>&,         FQvevriApiError&);
template bool UQvevriApiClient::StaticParseJsonArray(const FString&, TArray<FMarketListingView>&,  FQvevriApiError&);
template bool UQvevriApiClient::StaticParseJsonArray(const FString&, TArray<FVineyardView>&,       FQvevriApiError&);

template FString UQvevriApiClient::SerializeRequest(const FRegisterRequest&);
template FString UQvevriApiClient::SerializeRequest(const FLoginRequest&);
template FString UQvevriApiClient::SerializeRequest(const FAdvanceClockRequest&);
template FString UQvevriApiClient::SerializeRequest(const FCreateCharacterRequest&);
template FString UQvevriApiClient::SerializeRequest(const FGrowRequest&);
template FString UQvevriApiClient::SerializeRequest(const FListOnMarketRequest&);
template FString UQvevriApiClient::SerializeRequest(const FBuyRequest&);
template FString UQvevriApiClient::SerializeRequest(const FPlantVineyardRequest&);
template FString UQvevriApiClient::SerializeRequest(const FHarvestRequest&);
template FString UQvevriApiClient::SerializeRequest(const FManageRequest&);
template FString UQvevriApiClient::SerializeRequest(const FVineyardActionRequest&);
