// QvevriRegionPins.cpp
// QVEVRI — Unreal Engine 5 client. Phase 4 implementation.

#include "QvevriRegionPins.h"

#include "QvevriApiClient.h"

#include "Engine/StaticMesh.h"
#include "Engine/World.h"
#include "Components/StaticMeshComponent.h"
#include "Components/TextRenderComponent.h"
#include "Materials/MaterialInterface.h"
#include "Materials/MaterialInstanceDynamic.h"

// Cesium for Unreal — anchors an actor to a real lon/lat/height on the globe.
#include "CesiumGlobeAnchorComponent.h"

AQvevriRegionPins::AQvevriRegionPins()
{
    PrimaryActorTick.bCanEverTick = false;
    RootComponent = CreateDefaultSubobject<USceneComponent>(TEXT("Root"));
}

void AQvevriRegionPins::BeginPlay()
{
    Super::BeginPlay();

    UGameInstance* GameInstance = GetGameInstance();
    if (!GameInstance)
    {
        UE_LOG(LogTemp, Error, TEXT("[Qvevri] RegionPins: no GameInstance."));
        return;
    }

    UQvevriApiClient* Api = GameInstance->GetSubsystem<UQvevriApiClient>();
    if (!Api)
    {
        UE_LOG(LogTemp, Error, TEXT("[Qvevri] RegionPins: UQvevriApiClient subsystem not found."));
        return;
    }

    if (!BaseUrlOverride.IsEmpty())
    {
        Api->SetBaseUrl(BaseUrlOverride);
    }

    UE_LOG(LogTemp, Display, TEXT("[Qvevri] RegionPins: requesting regions from backend..."));

    // Capture weakly — the HTTP completion may fire after this actor is gone.
    TWeakObjectPtr<AQvevriRegionPins> WeakThis(this);

    Api->GetRegions(
        [WeakThis](const TArray<FRegionInfo>& Regions)
        {
            AQvevriRegionPins* Self = WeakThis.Get();
            if (!Self)
            {
                return;
            }
            UE_LOG(LogTemp, Display, TEXT("[Qvevri] RegionPins: received %d regions."), Regions.Num());
            Self->SpawnPinsForRegions(Regions);
        },
        [](const FQvevriApiError& Err)
        {
            UE_LOG(LogTemp, Error,
                TEXT("[Qvevri] RegionPins: GetRegions failed (status=%d, code=%s): %s"),
                Err.HttpStatus, *Err.Code, *Err.Message);
        });
}

void AQvevriRegionPins::SpawnPinsForRegions(const TArray<FRegionInfo>& Regions)
{
    int32 Pinned = 0;
    for (const FRegionInfo& Region : Regions)
    {
        const bool bHasCoords =
            !(FMath::IsNearlyZero(Region.latitude) && FMath::IsNearlyZero(Region.longitude));

        if (!bHasCoords)
        {
            UE_LOG(LogTemp, Warning,
                TEXT("[Qvevri] RegionPins: '%s' has no coordinates (0,0) — skipping."),
                *Region.region);
            continue;
        }

        SpawnOnePin(Region);
        ++Pinned;
    }
    UE_LOG(LogTemp, Display, TEXT("[Qvevri] RegionPins: placed %d pins on the globe."), Pinned);
}

void AQvevriRegionPins::SpawnOnePin(const FRegionInfo& Region)
{
    UWorld* World = GetWorld();
    if (!World)
    {
        return;
    }

    // 1 Unreal unit = 1 cm; the engine sphere mesh is 100 uu in diameter.
    const double DiameterUu  = PinDiameterMeters * 100.0;
    const double MeshScale   = DiameterUu / 100.0;
    const double LabelHeight = DiameterUu * 0.75;

    FActorSpawnParameters Params;
    Params.Owner = this;
    AActor* Pin = World->SpawnActor<AActor>(AActor::StaticClass(), FTransform::Identity, Params);
    if (!Pin)
    {
        return;
    }
#if WITH_EDITOR
    Pin->SetActorLabel(FString::Printf(TEXT("Pin_%s"), *Region.region));
#endif

    // Root.
    USceneComponent* Root = NewObject<USceneComponent>(Pin, TEXT("Root"));
    Pin->SetRootComponent(Root);
    Root->RegisterComponent();

    // Sphere mesh marker.
    UStaticMeshComponent* Mesh = NewObject<UStaticMeshComponent>(Pin, TEXT("PinMesh"));
    Mesh->SetupAttachment(Root);
    Mesh->RegisterComponent();
    Mesh->SetCollisionEnabled(ECollisionEnabled::NoCollision);

    if (UStaticMesh* Sphere = LoadObject<UStaticMesh>(nullptr, TEXT("/Engine/BasicShapes/Sphere.Sphere")))
    {
        Mesh->SetStaticMesh(Sphere);
        Mesh->SetWorldScale3D(FVector(MeshScale));
    }

    // Tint it with a dynamic instance of the engine's basic shape material.
    if (UMaterialInterface* BaseMat =
            LoadObject<UMaterialInterface>(nullptr, TEXT("/Engine/BasicShapes/BasicShapeMaterial.BasicShapeMaterial")))
    {
        if (UMaterialInstanceDynamic* Mid = UMaterialInstanceDynamic::Create(BaseMat, Pin))
        {
            Mid->SetVectorParameterValue(TEXT("Color"), PinColor);
            Mesh->SetMaterial(0, Mid);
        }
    }

    // Floating label with the region's display name.
    UTextRenderComponent* Label = NewObject<UTextRenderComponent>(Pin, TEXT("PinLabel"));
    Label->SetupAttachment(Root);
    Label->RegisterComponent();
    Label->SetText(FText::FromString(Region.displayName.IsEmpty() ? Region.region : Region.displayName));
    Label->SetHorizontalAlignment(EHTA_Center);
    Label->SetWorldSize(static_cast<float>(DiameterUu));
    Label->SetTextRenderColor(FColor::White);
    Label->SetRelativeLocation(FVector(0.0, 0.0, LabelHeight));

    // Anchor the whole actor to the region's real coordinates on the Cesium globe.
    // FVector order for Cesium is (Longitude, Latitude, Height-in-metres).
    UCesiumGlobeAnchorComponent* Anchor =
        NewObject<UCesiumGlobeAnchorComponent>(Pin, TEXT("GlobeAnchor"));
    Anchor->RegisterComponent();
    Pin->AddInstanceComponent(Anchor);
    Anchor->MoveToLongitudeLatitudeHeight(
        FVector(Region.longitude, Region.latitude, PinHeightMeters));

    UE_LOG(LogTemp, Display,
        TEXT("[Qvevri] RegionPins: pinned '%s' at lat=%.5f lon=%.5f."),
        *Region.region, Region.latitude, Region.longitude);
}
