# PROGRESSION & QUEST SPEC (v1)

Two additive, new-package lanes. Pre-assigned migrations: **PROGRESSION=V9, QUEST=V10.**

## Hard rules (both lanes)
- Additive only; all existing tests stay green. New packages only — do NOT edit
  `Character.java`, `CellarItem.java`, goods, wine, land, trade, sim, or config.
- Inline bearer auth via `AccountTokenService` + ownership via
  `CharacterService.getOwned`, exactly like the other controllers. Security matchers
  for `/api/progression/**` and `/api/quests/**` are already added.
- H2 dev/test via ddl-auto — avoid reserved-word column names (no `value`, `year`,
  `state`, `level` may be reserved — prefer `xp_level`, `quest_status`, etc.; verify
  against H2). Flyway for prod.
- Money/goods rewards move via `CharacterService.adjustWallet` / `GoodsService.grant`
  (call them; never edit them).

## LANE PROGRESSION — `com.game.progression`. Migration V9.
Per-character experience, level, and reputation, tracked separately from the existing
`Character.rank` (do not touch Character).
- `ProgressionProfile` @Entity (`progression_profiles`): id, characterId (unique),
  xp (long), xpLevel (int), reputation (int), updatedAt. Repo: findByCharacterId.
- `ProgressionService`: lazy-create a profile at xp=0/level=1; `awardXp(characterId,
  amount, reason)` adds xp and recomputes level from a deterministic curve
  (e.g. level = floor(sqrt(xp / 100)) + 1, or a documented table); `adjustReputation`.
- Endpoints `/api/progression/**` (auth + ownership):
  - `GET /api/progression/{characterId}` → the profile (auto-created if absent).
  - `POST /api/progression/{characterId}/award` `{amount, reason}` → awards xp,
    returns updated profile. (v1 exposes this for the client/dev; later, other systems
    call `ProgressionService.awardXp` directly.)
- Tests: profile auto-creates; award increases xp and bumps level at thresholds;
  reputation adjust; ownership enforced (other account → 404/403); negative/zero award
  rejected (400).
- Owns: `com.game.progression` only + V9 + tests.

## LANE QUEST — `com.game.quest`. Migration V10.
NPC-given quests with rewards — gives the existing NPC dialog UI a real backend.
- A static `QuestCatalog` of ~5 starter quests with Georgian wine flavour, each:
  id, title, description, giverNpc (name), objective (a simple typed goal, e.g.
  PLANT_VINE / SELL_BOTTLES count / CRAFT_VESSEL), rewardGel, optional rewardGoodTypeId+qty.
- `PlayerQuest` @Entity (`player_quests`): id, characterId, questId, questStatus
  (OFFERED/ACTIVE/COMPLETED), progress (int), startedAt, completedAt. Repo:
  findByCharacterId, findByCharacterIdAndQuestId.
- `QuestService` + `QuestController` `/api/quests/**` (auth + ownership):
  - `GET /api/quests/catalog` → all quest definitions.
  - `GET /api/quests/{characterId}` → that character's PlayerQuest[].
  - `POST /api/quests/{characterId}/accept` `{questId}` → creates ACTIVE PlayerQuest
    (400 if already accepted, 400 unknown quest).
  - `POST /api/quests/{characterId}/complete` `{questId}` → marks COMPLETED and grants
    the reward via `CharacterService.adjustWallet` (+ `GoodsService.grant` if a good
    reward). v1 may complete on demand (objective auto-satisfied); deeper objective
    tracking is a later pass. Idempotent: re-completing an already-COMPLETED quest does
    not double-reward (400 or no-op).
- Tests: catalog returned; accept creates ACTIVE; double-accept 400; complete grants
  GEL (wallet increases) and flips to COMPLETED; re-complete does not double-pay;
  ownership enforced.
- Owns: `com.game.quest` only + V10 + tests.

## Deferred (next)
- Auto-wiring progression XP + quest objective tracking into the real action endpoints
  (harvest/sell/craft/trade) — a cross-cutting integration pass after these land green.
- Realtime presence (waits for Unreal); auth hardening / token scoping; anti-cheat.
