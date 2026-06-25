using UnrealBuildTool;
using System.Collections.Generic;

public class QvevriEditorTarget : TargetRules
{
	public QvevriEditorTarget(TargetInfo Target) : base(Target)
	{
		Type = TargetType.Editor;
		DefaultBuildSettings = BuildSettingsVersion.Latest;
		IncludeOrderVersion = EngineIncludeOrderVersion.Latest;
		ExtraModuleNames.Add("Qvevri");
	}
}
