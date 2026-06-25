// QvevriSession.h
// QVEVRI — Unreal Engine 5 client.
// Session holder: stores auth state (accountId, bearer token, active character)
// and exposes helpers used by UQvevriApiClient and game code.
//
// FQvevriSession is a plain struct — it lives on UQvevriApiClient (the
// UGameInstanceSubsystem).  Game code retrieves the subsystem and reads /
// mutates session state through it.
//
// Owned by: Unreal client lane.

#pragma once

#include "CoreMinimal.h"
#include "QvevriDtos.h"
#include "QvevriSession.generated.h"

/**
 * Lightweight session holder.
 * Populated on successful Register / Login; cleared on logout.
 *
 * Blueprint-accessible so UI widgets can read AccountId, token expiry, etc.
 * without going through C++.
 */
USTRUCT(BlueprintType)
struct QVEVRI_API FQvevriSession
{
    GENERATED_BODY()

    // -----------------------------------------------------------------------
    // Auth state
    // -----------------------------------------------------------------------

    /**
     * Server-assigned account identifier.
     * 0 when not logged in.
     */
    UPROPERTY(BlueprintReadOnly, Category = "Qvevri|Session")
    int64 AccountId = 0;

    /**
     * Opaque bearer token returned by /api/account/login or /register.
     * Empty string when not logged in.
     * Sent as "Authorization: Bearer <Token>" on all protected endpoints.
     */
    UPROPERTY(BlueprintReadOnly, Category = "Qvevri|Session")
    FString Token;

    // -----------------------------------------------------------------------
    // Character state
    // -----------------------------------------------------------------------

    /**
     * The character the player is currently playing.
     * Id == 0 when no character is selected yet.
     */
    UPROPERTY(BlueprintReadOnly, Category = "Qvevri|Session")
    FQvevriCharacter ActiveCharacter;

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Returns true when the player is authenticated
     * (Token is non-empty and AccountId is non-zero).
     */
    bool IsLoggedIn() const
    {
        return AccountId != 0 && !Token.IsEmpty();
    }

    /**
     * Returns true when a character has been selected (ActiveCharacter.id != 0).
     */
    bool HasActiveCharacter() const
    {
        return ActiveCharacter.id != 0;
    }

    /** Resets all session state to the logged-out defaults. */
    void Clear()
    {
        AccountId = 0;
        Token.Empty();
        ActiveCharacter = FQvevriCharacter{};
    }
};
