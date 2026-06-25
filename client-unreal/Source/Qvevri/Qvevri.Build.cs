// Qvevri.Build.cs
// QVEVRI — Unreal Engine 5 module build rules.
//
// This file is read by UnrealBuildTool (UBT).  It must live at:
//   <ProjectRoot>/Source/Qvevri/Qvevri.Build.cs
//
// Required modules for the networking + JSON layer:
//   HTTP           — FHttpModule, IHttpRequest / IHttpResponse
//   Json           — TJsonReader, FJsonObject, FJsonSerializer
//   JsonUtilities  — FJsonObjectConverter (UStruct <-> JSON)
//
// Add the Cesium for Unreal module here once the plugin is installed:
//   PublicDependencyModuleNames.Add("CesiumRuntime");

using UnrealBuildTool;

public class Qvevri : ModuleRules
{
    public Qvevri(ReadOnlyTargetRules Target) : base(Target)
    {
        // Use Unity build for faster compilation.
        PCHUsage = PCHUsageMode.UseExplicitOrSharedPCHs;

        // ---------------------------------------------------------------------------
        // Core engine modules — always required
        // ---------------------------------------------------------------------------
        PublicDependencyModuleNames.AddRange(new string[]
        {
            "Core",
            "CoreUObject",
            "Engine",
            "InputCore",
        });

        // ---------------------------------------------------------------------------
        // Networking + JSON — required for QvevriApiClient
        // ---------------------------------------------------------------------------
        PublicDependencyModuleNames.AddRange(new string[]
        {
            "HTTP",             // FHttpModule, IHttpRequest
            "Json",             // TJsonReader, FJsonObject, FJsonSerializer, FJsonValue
            "JsonUtilities",    // FJsonObjectConverter (UStruct <-> JSON)
        });

        // ---------------------------------------------------------------------------
        // UMG — add when you start building UI widgets (UMG screens come next)
        // ---------------------------------------------------------------------------
        // PublicDependencyModuleNames.Add("UMG");
        // PrivateDependencyModuleNames.Add("Slate");
        // PrivateDependencyModuleNames.Add("SlateCore");

        // ---------------------------------------------------------------------------
        // Cesium for Unreal — globe anchoring for region pins (Phase 4)
        // ---------------------------------------------------------------------------
        PublicDependencyModuleNames.Add("CesiumRuntime");

        // ---------------------------------------------------------------------------
        // Private includes — only needed inside this module
        // ---------------------------------------------------------------------------
        PrivateDependencyModuleNames.AddRange(new string[]
        {
            "Slate",
            "SlateCore",
        });
    }
}
