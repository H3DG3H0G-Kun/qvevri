#include "Qvevri.h"
#include "Modules/ModuleManager.h"

// Primary game module for the QVEVRI Unreal client.
// The module name string here ("Qvevri") MUST match the module name in
// Qvevri.Build.cs and the QVEVRI_API export macro used in the headers.
IMPLEMENT_PRIMARY_GAME_MODULE(FDefaultGameModuleImpl, Qvevri, "Qvevri");
