# Wiring the UI Toolkit login screen (one-time, in the Unity editor)

Everything is code/text except the `PanelSettings` asset, which is a binary
ScriptableObject Unity must create. Five clicks:

1. **Create PanelSettings.** Project window → right-click `Assets/UI` →
   Create ▸ UI Toolkit ▸ Panel Settings Asset. Name it `QvevriPanelSettings`.
   (Its default Theme Style Sheet is fine — our USS overrides the visuals.)
   Optional but recommended: set Scale Mode = "Scale with Screen Size",
   reference resolution 1920×1080, so the screen scales cleanly.

2. **Create a screen GameObject.** In the GameScene (or a new bootstrap scene):
   GameObject ▸ UI Toolkit ▸ UI Document. Select it.

3. **Point the UIDocument** (Inspector): Panel Settings = `QvevriPanelSettings`,
   Source Asset = `Assets/UI/Screens/Login/Login.uxml`.

4. **Add the controller.** On the same GameObject, Add Component ▸
   `LoginScreenController`. (It auto-creates a `WebMmoApi` + `MmoSession` if none
   are injected, so it runs standalone for visual testing.)

5. **Press Play.** Make sure the server is running (`mvn spring-boot:run` in
   `server/`, or your run config) so register/login actually hit the API.

## Coexistence with the current IMGUI login
The existing `MmoHubBootstrap` still auto-spawns the old IMGUI hub. While testing
the new screen they'll overlap. To see the UI Toolkit screen alone, temporarily
disable the IMGUI auto-start (comment out the body of `MmoHubBootstrap.Init()` in
`Assets/Scripts/Gameplay/UI/Mmo/MmoHub.cs`). Once we've ported the core screens to
UI Toolkit we remove the IMGUI hub entirely.

## Fonts (optional, makes it sing)
Drop a serif + a Georgian-capable sans into `Assets/UI/Fonts/`, then set
`--qv-font-serif` / `--qv-font-sans` in `theme.uss` to
`url("project://database/Assets/UI/Fonts/<File>.ttf")`. Until then it uses the
engine default font — readable, just not yet characterful.

## Full screen set + the ScreenRouter (after login is verified)
The port covers 11 screens, all in the same theme: Login, CharacterSelect,
CharacterCreate, Vineyards, VineyardManage, VineyardAction, Bazaar, Inventory,
Profession, Market, NpcDialog, plus a MainShell top-bar (wallet + Day/Year clock
+ nav tabs). `ScreenRouter.cs` ties them together and injects one shared
`WebMmoApi` + `GoodsApiClient` + `MmoSession` into every controller.

One-time scene wiring:
1. Create one shared `QvevriPanelSettings` (step 1 above) used by ALL screens.
2. For each screen, make a GameObject with a UIDocument (that PanelSettings +
   the screen's UXML) and its controller component. Give the gameplay screens a
   higher `UIDocument.sortingOrder` than the Shell so they draw above the bar.
3. Add one empty GameObject with `ScreenRouter` and drag each screen's controller
   into its inspector slot. The router shows Login first, then
   CharacterSelect → (CharacterCreate) → Shell + the tabbed gameplay screens,
   switching screens on the Shell's tab clicks.

Note: the router toggles each screen GameObject active/inactive (each is its own
UIDocument sharing the panel). The exact bar-vs-content layering is the kind of
thing we tune on the screenshot loop — get one screen rendering, send a shot,
iterate.

## What renders
A centred clay panel on a dark cellar backdrop: QVEVRI serif wordmark, a
Sign in / Register segmented toggle, gold-ringed input wells (email shown only in
register mode), a wine-red primary button, inline status line, and a footer that
swaps modes. All styling comes from `theme.uss` + `components.uss`, so the next
screens reuse the exact same look.
