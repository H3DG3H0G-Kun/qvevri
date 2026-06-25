using UnrealBuildTool;
using System.Collections.Generic;

public class QvevriTarget : TargetRules
{
	public QvevriTarget(TargetInfo Target) : base(Target)
	{
		Type = TargetType.Game;
		DefaultBuildSettings = BuildSettingsVersion.Latest;
		IncludeOrderVersion = EngineIncludeOrderVersion.Latest;
		ExtraModuleNames.Add("Qvevri");
	}
}
