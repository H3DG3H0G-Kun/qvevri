// QvevriRegionPins.h
// QVEVRI — Unreal Engine 5 client. Phase 4: pin the wine regions on the Cesium globe.
//
// Drop ONE of these actors into your level. On BeginPlay it asks the live backend
// (GET /api/world/regions) for all regions and, for each one that carries a real
// latitude/longitude, spawns a labelled marker anchored to that exact spot on the
// Cesium globe via a UCesiumGlobeAnchorComponent.
//
// Prerequisites:
//   - The Spring Boot backend is running (default http://localhost:8080).
//   - A Cesium World Terrain tileset + a CesiumGeoreference exist in the level.
//
// Owned by: Unreal client lane.

#pragma once

#include "CoreMinimal.h"
#include "GameFramework/Actor.h"
#include "QvevriDtos.h"
#include "QvevriRegionPins.generated.h"

/**
 * Spawns one pin per backend region at its real-world coordinates on the globe.
 */
UCLASS()
class QVEVRI_API AQvevriRegionPins : public AActor
{
    GENERATED_BODY()

public:
    AQvevriRegionPins();

    /**
     * Override the backend base URL. Leave blank to use the subsystem default
     * (http://localhost:8080). No trailing slash.
     */
    UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "Qvevri")
    FString BaseUrlOverride;

    /** Height in metres above the WGS-84 ellipsoid to float each pin. */
    UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "Qvevri")
    double PinHeightMeters = 2000.0;

    /** Diameter of each pin sphere, in metres (so it reads from flight altitude). */
    UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "Qvevri")
    double PinDiameterMeters = 4000.0;

    /** Pin colour (defaults to Saperavi wine-red). */
    UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "Qvevri")
    FLinearColor PinColor = FLinearColor(0.55f, 0.02f, 0.08f);

protected:
    virtual void BeginPlay() override;

private:
    /** Fan out one pin per region that has coordinates. */
    void SpawnPinsForRegions(const TArray<FRegionInfo>& Regions);

    /** Build a single labelled, globe-anchored marker for one region. */
    void SpawnOnePin(const FRegionInfo& Region);
};
