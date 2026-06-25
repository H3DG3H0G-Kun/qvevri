// QvevriDtos.h
// QVEVRI — Unreal Engine 5 client.
// All USTRUCTs mirror the JSON wire contract in docs/API.md EXACTLY.
// Field names are camelCase to match JSON keys:
//   FJsonObjectConverter converts UPROPERTY names case-insensitively
//   to JSON keys, so "accountId" property <-> "accountId" JSON key.
//
// COVERAGE (this file — auth + world + character set):
//   POST /api/account/register      -> FRegisterRequest  / FAuthResponse
//   POST /api/account/login         -> FLoginRequest     / FAuthResponse
//   GET  /api/world/regions         -> TArray<FRegionInfo>
//   GET  /api/world/careers         -> TArray<FCareerInfo>
//   GET  /api/characters            -> TArray<FQvevriCharacter>
//   POST /api/characters            -> FCreateCharacterRequest / FQvevriCharacter
//   GET  /api/world/clock           -> FWorldClock
//   POST /api/world/advance         -> FAdvanceClockRequest / FWorldClock
//
// See QvevriEstateDtos.h for vineyard/harvest/management DTOs.
// See QvevriMarketDtos.h for cellar/market DTOs.
//
// Owned by: Unreal client lane.
// Do NOT modify the Java server/* or the Unity client/* trees.

#pragma once

#include "CoreMinimal.h"
#include "QvevriDtos.generated.h"

// ---------------------------------------------------------------------------
// Error envelope  { "error": { "code": "...", "message": "..." } }
// Parsed by UQvevriApiClient when the HTTP status is non-2xx.
// ---------------------------------------------------------------------------

/**
 * Inner object of the standard server error envelope.
 * Wire shape: { "code": "INVALID_CREDENTIALS", "message": "human readable" }
 */
USTRUCT(BlueprintType)
struct QVEVRI_API FQvevriErrorBody
{
    GENERATED_BODY()

    /** Server error code, e.g. INVALID_CREDENTIALS, UNAUTHORIZED, BAD_REQUEST. */
    UPROPERTY(BlueprintReadOnly, Category = "Qvevri|Error")
    FString code;

    /** Human-readable description. */
    UPROPERTY(BlueprintReadOnly, Category = "Qvevri|Error")
    FString message;
};

/**
 * Outer wrapper of the standard server error envelope.
 * Wire shape: { "error": { "code": "...", "message": "..." } }
 */
USTRUCT(BlueprintType)
struct QVEVRI_API FQvevriErrorEnvelope
{
    GENERATED_BODY()

    UPROPERTY(BlueprintReadOnly, Category = "Qvevri|Error")
    FQvevriErrorBody error;
};

/**
 * Structured error surfaced to callers via the OnError callback.
 * HttpStatus == 0 means a network-level failure (no response at all).
 */
USTRUCT(BlueprintType)
struct QVEVRI_API FQvevriApiError
{
    GENERATED_BODY()

    /** HTTP status code, or 0 for network / connection failures. */
    UPROPERTY(BlueprintReadOnly, Category = "Qvevri|Error")
    int32 HttpStatus = 0;

    /** Server error code from the envelope, e.g. "UNAUTHORIZED". Empty on network error. */
    UPROPERTY(BlueprintReadOnly, Category = "Qvevri|Error")
    FString Code;

    /** Human-readable message. May be raw response body when envelope parsing fails. */
    UPROPERTY(BlueprintReadOnly, Category = "Qvevri|Error")
    FString Message;
};

// ---------------------------------------------------------------------------
// Account  —  POST /api/account/register  +  POST /api/account/login
// ---------------------------------------------------------------------------

/**
 * Request body for POST /api/account/register.
 * Wire: { "email": "...", "username": "...", "password": "..." }
 */
USTRUCT(BlueprintType)
struct QVEVRI_API FRegisterRequest
{
    GENERATED_BODY()

    /** Wire key: "email" */
    UPROPERTY(BlueprintReadWrite, Category = "Qvevri|Auth")
    FString email;

    /** Wire key: "username" */
    UPROPERTY(BlueprintReadWrite, Category = "Qvevri|Auth")
    FString username;

    /** Wire key: "password" */
    UPROPERTY(BlueprintReadWrite, Category = "Qvevri|Auth")
    FString password;
};

/**
 * Request body for POST /api/account/login.
 * Wire: { "username": "...", "password": "..." }
 */
USTRUCT(BlueprintType)
struct QVEVRI_API FLoginRequest
{
    GENERATED_BODY()

    /** Wire key: "username" */
    UPROPERTY(BlueprintReadWrite, Category = "Qvevri|Auth")
    FString username;

    /** Wire key: "password" */
    UPROPERTY(BlueprintReadWrite, Category = "Qvevri|Auth")
    FString password;
};

/**
 * 201 response from POST /api/account/register
 * 200 response from POST /api/account/login
 * Wire: { "accountId": 1, "token": "eyJ..." }
 */
USTRUCT(BlueprintType)
struct QVEVRI_API FAuthResponse
{
    GENERATED_BODY()

    /** Wire key: "accountId" — the server-assigned account identifier. */
    UPROPERTY(BlueprintReadOnly, Category = "Qvevri|Auth")
    int64 accountId = 0;

    /** Wire key: "token" — opaque bearer token; store in FQvevriSession. */
    UPROPERTY(BlueprintReadOnly, Category = "Qvevri|Auth")
    FString token;
};

// ---------------------------------------------------------------------------
// World  —  GET /api/world/regions  +  GET /api/world/careers
// ---------------------------------------------------------------------------

/**
 * One entry from GET /api/world/regions.
 * Wire: { "region": "KAKHETI", "displayName": "...", "climate": "...",
 *         "signatureGrapes": "...", "methodNote": "...",
 *         "latitude": 41.6, "longitude": 45.7 }
 *
 * NOTE: "latitude" and "longitude" are NEW fields being added to the backend
 * in parallel (see UNREAL-MIGRATION.md §backend-bridge).  They will be absent
 * from the response until the server lane delivers them; FJsonObjectConverter
 * leaves unmatched UPROPERTY fields at their default (0.0) when the key is
 * missing, so existing clients remain safe.
 */
USTRUCT(BlueprintType)
struct QVEVRI_API FRegionInfo
{
    GENERATED_BODY()

    /** Wire key: "region" — enum name, e.g. "KAKHETI". */
    UPROPERTY(BlueprintReadOnly, Category = "Qvevri|World")
    FString region;

    /** Wire key: "displayName" */
    UPROPERTY(BlueprintReadOnly, Category = "Qvevri|World")
    FString displayName;

    /** Wire key: "climate" */
    UPROPERTY(BlueprintReadOnly, Category = "Qvevri|World")
    FString climate;

    /** Wire key: "signatureGrapes" */
    UPROPERTY(BlueprintReadOnly, Category = "Qvevri|World")
    FString signatureGrapes;

    /** Wire key: "methodNote" */
    UPROPERTY(BlueprintReadOnly, Category = "Qvevri|World")
    FString methodNote;

    /**
     * Wire key: "latitude"
     * Real-world WGS-84 latitude of the region centre.
     * Used by Cesium to anchor the region on the globe.
     * Defaults to 0.0 until the server lane delivers this field.
     */
    UPROPERTY(BlueprintReadOnly, Category = "Qvevri|World")
    double latitude = 0.0;

    /**
     * Wire key: "longitude"
     * Real-world WGS-84 longitude of the region centre.
     * Defaults to 0.0 until the server lane delivers this field.
     */
    UPROPERTY(BlueprintReadOnly, Category = "Qvevri|World")
    double longitude = 0.0;
};

/**
 * One entry from GET /api/world/careers.
 * Wire: { "type": "WINEMAKER", "displayName": "...", "description": "..." }
 */
USTRUCT(BlueprintType)
struct QVEVRI_API FCareerInfo
{
    GENERATED_BODY()

    /** Wire key: "type" — career enum name, e.g. "WINEMAKER". */
    UPROPERTY(BlueprintReadOnly, Category = "Qvevri|World")
    FString type;

    /** Wire key: "displayName" */
    UPROPERTY(BlueprintReadOnly, Category = "Qvevri|World")
    FString displayName;

    /** Wire key: "description" */
    UPROPERTY(BlueprintReadOnly, Category = "Qvevri|World")
    FString description;
};

// ---------------------------------------------------------------------------
// Characters  —  GET /api/characters  +  POST /api/characters
// ---------------------------------------------------------------------------

/**
 * Request body for POST /api/characters.
 * Wire: { "name": "...", "careerType": "WINEMAKER", "homeRegion": "KAKHETI" }
 */
USTRUCT(BlueprintType)
struct QVEVRI_API FCreateCharacterRequest
{
    GENERATED_BODY()

    /** Wire key: "name" */
    UPROPERTY(BlueprintReadWrite, Category = "Qvevri|Character")
    FString name;

    /** Wire key: "careerType" — must match a value from GET /api/world/careers. */
    UPROPERTY(BlueprintReadWrite, Category = "Qvevri|Character")
    FString careerType;

    /** Wire key: "homeRegion" — must match a value from GET /api/world/regions. */
    UPROPERTY(BlueprintReadWrite, Category = "Qvevri|Character")
    FString homeRegion;
};

/**
 * Character entity returned by GET /api/characters, GET /api/characters/{id},
 * and POST /api/characters (201).
 * Wire: { "id": 1, "accountId": 2, "name": "...", "careerType": "...",
 *         "homeRegion": "...", "rank": "...", "walletGel": 500.0,
 *         "createdAt": 1718500000000 }
 */
USTRUCT(BlueprintType)
struct QVEVRI_API FQvevriCharacter
{
    GENERATED_BODY()

    /** Wire key: "id" */
    UPROPERTY(BlueprintReadOnly, Category = "Qvevri|Character")
    int64 id = 0;

    /** Wire key: "accountId" */
    UPROPERTY(BlueprintReadOnly, Category = "Qvevri|Character")
    int64 accountId = 0;

    /** Wire key: "name" */
    UPROPERTY(BlueprintReadOnly, Category = "Qvevri|Character")
    FString name;

    /** Wire key: "careerType" */
    UPROPERTY(BlueprintReadOnly, Category = "Qvevri|Character")
    FString careerType;

    /** Wire key: "homeRegion" */
    UPROPERTY(BlueprintReadOnly, Category = "Qvevri|Character")
    FString homeRegion;

    /** Wire key: "rank" */
    UPROPERTY(BlueprintReadOnly, Category = "Qvevri|Character")
    FString rank;

    /** Wire key: "walletGel" — current GEL balance. */
    UPROPERTY(BlueprintReadOnly, Category = "Qvevri|Character")
    double walletGel = 0.0;

    /** Wire key: "createdAt" — epoch milliseconds. */
    UPROPERTY(BlueprintReadOnly, Category = "Qvevri|Character")
    int64 createdAt = 0;
};

// ---------------------------------------------------------------------------
// World Clock  —  GET /api/world/clock  +  POST /api/world/advance
// ---------------------------------------------------------------------------

/**
 * Response from GET /api/world/clock and POST /api/world/advance.
 * Wire: { "year": 1, "dayOfYear": 0, "absoluteDay": 0,
 *         "realSecondsPerSimDay": 30 }
 */
USTRUCT(BlueprintType)
struct QVEVRI_API FWorldClock
{
    GENERATED_BODY()

    /** Wire key: "year" — in-game year, >= 1. */
    UPROPERTY(BlueprintReadOnly, Category = "Qvevri|WorldClock")
    int32 year = 1;

    /** Wire key: "dayOfYear" — 0..364. */
    UPROPERTY(BlueprintReadOnly, Category = "Qvevri|WorldClock")
    int32 dayOfYear = 0;

    /** Wire key: "absoluteDay" — (year-1)*365 + dayOfYear. */
    UPROPERTY(BlueprintReadOnly, Category = "Qvevri|WorldClock")
    int32 absoluteDay = 0;

    /** Wire key: "realSecondsPerSimDay" — wall-clock seconds per one game day. */
    UPROPERTY(BlueprintReadOnly, Category = "Qvevri|WorldClock")
    double realSecondsPerSimDay = 30.0;
};

/**
 * Request body for POST /api/world/advance (dev/test only).
 * Wire: { "days": 10 }
 */
USTRUCT(BlueprintType)
struct QVEVRI_API FAdvanceClockRequest
{
    GENERATED_BODY()

    /** Wire key: "days" — must be >= 0. */
    UPROPERTY(BlueprintReadWrite, Category = "Qvevri|WorldClock")
    int32 days = 0;
};
