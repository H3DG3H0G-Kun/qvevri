# CONTEST, ACHIEVEMENTS & CHAT SPEC (v1)

Three additive, new-package lanes. Pre-assigned migrations: **CHAT=V20, ACHIEVEMENT=V21, CONTEST=V22.**

## Hard rules (all lanes)
- Additive only; all existing tests stay green. NEW packages only — do NOT edit
  `Character`, `CellarItem`, market, goods, guild, or any existing package or config.
  READ their repos/services (call, never edit): `CharacterService.adjustWallet/getOwned`,
  `CellarItemRepository`, `GoodsService`, `GuildMemberRepository`,
  `WorldClockService.currentAbsoluteDay`.
- Inline bearer auth via `AccountTokenService` + ownership via `CharacterService.getOwned`.
  Matchers for `/api/chat|achievement|contest/**` already added.
- Time-based effects resolve LAZILY off the world clock (no scheduler). Deterministic
  judging — no randomness.
- H2 dev/test via ddl-auto — avoid reserved-word columns (no `value`, `year`, `state`,
  `level`, `status`, `rank`, `read`, `to`, `from`, `message` may be reserved → use
  `chat_status`, `body_text`, `season_year`, etc.; verify against H2). Flyway for prod.
- **Compile-safety (tests):** declare every response-body map used with
  `containsKey`/`containsKeys` as `Map<String,Object>` (NOT `Map<?,?>`). For endpoints
  that can return 4xx, read the body with `String.class` (an error envelope is a JSON
  object, not a List). For list endpoints, deserialize with `List.class`.

## LANE CHAT — `com.game.chat`. Migration V20.
Text channels + direct messages (REST poll; realtime over WS is a later pass).
- `ChatMessage` @Entity (`chat_messages`): id, channel (String — e.g. "GLOBAL",
  "REGION:KAKHETI", "GUILD:{guildId}", or "DM:{a}:{b}" with sorted ids),
  senderCharacterId, senderName, bodyText, createdAt. Repo:
  findByChannelOrderByCreatedAtDesc (paged/limited), and a since-id variant for polling.
- `ChatService` + `ChatController` `/api/chat/**` (auth + ownership of the sender):
  - `POST /api/chat/send` `{characterId, channel, body}` → validate the character may post
    to the channel (GLOBAL always; REGION = the character's home region; GUILD = must be a
    member via GuildMemberRepository; DM = must be one of the two ids); persist; return it.
    Reject empty/oversized body (e.g. >500 chars) with 400.
  - `GET /api/chat/{channel}?sinceId=` → recent messages in the channel (newest N, or those
    with id > sinceId for polling). For DM channels, the caller must be a participant
    (pass characterId as a query param + ownership) → else 403.
  - Helper to build a canonical DM channel id from two character ids (sorted).
- Flyway V20. Tests: send to GLOBAL + read back; region post requires home region (wrong
  region → 400/403); guild channel requires membership (non-member → 403); DM between two
  chars visible to both, not to a third (403); empty body → 400; sinceId polling returns
  only newer messages. Owns `com.game.chat` only + V20 + tests.

## LANE ACHIEVEMENTS — `com.game.achievement`. Migration V21.
A milestone catalog with per-character unlocks and one-time rewards.
- A static `AchievementCatalog` of ~6 achievements (Georgian wine flavour), each: id, title,
  description, rewardGel (double), rewardGoodTypeId (nullable, from GoodsCatalog),
  rewardGoodQty (double). Examples: "first_estate", "first_qvevri", "master_vintner"
  (a high-quality bottle), "wealthy_merchant", "guild_founder", "globetrotter". Provide
  all() + find(id).
- `PlayerAchievement` @Entity (`player_achievements`): id, characterId, achievementId,
  unlockedDay (long), createdAt — unique on (characterId, achievementId). Repo:
  findByCharacterId, findByCharacterIdAndAchievementId.
- `AchievementService` + `AchievementController` `/api/achievement/**` (auth + ownership):
  - `GET /api/achievement/catalog` → all definitions.
  - `GET /api/achievement/{characterId}` → that character's unlocked achievements.
  - `POST /api/achievement/{achievementId}/unlock` `{characterId}` → idempotent: first unlock
    records it + grants reward (adjustWallet + optional GoodsService.grant); already unlocked
    → 400 ALREADY_UNLOCKED (no double reward). v1 grants on demand (criteria auto-satisfied);
    real criteria-checking against other systems is a later integration pass.
- Flyway V21. Tests: catalog returned; unlock records + grants GEL (verify wallet); double
  unlock → 400 + wallet unchanged; list reflects unlocks; ownership enforced.
  Owns `com.game.achievement` only + V21 + tests.

## LANE CONTEST — `com.game.contest`. Migration V22.
Timed wine competitions judged deterministically on bottle quality.
- `Contest` @Entity (`contests`): id, name, description, endDay (long sim-day), prizeGel
  (double), contestStatus ("OPEN"|"JUDGED"), createdAt. `ContestEntry` @Entity
  (`contest_entries`): id, contestId, characterId, cellarItemId, qualityScore (double,
  snapshotted at entry time from the CellarItem), placement (Integer nullable — set at judging),
  createdAt. Repos as needed.
- `ContestService` + `ContestController` `/api/contest/**` (auth + ownership):
  - `POST /api/contest/create` `{name, description, durationDays, prizeGel}` → creates an OPEN
    contest (endDay = currentDay + durationDays). (v1: any authenticated character may open
    one; treat the prizeGel as NPC-funded for now — do not debit the creator, to keep v1 simple;
    note this.)
  - `GET /api/contest/open` → OPEN contests (lazily auto-judge any past endDay first).
  - `POST /api/contest/{id}/enter` `{characterId, cellarItemId}` → contest must be OPEN and not
    past endDay; character must own the cellar item; snapshot its quality; one entry per
    character per contest (400 if already entered). Return the entry.
  - `POST /api/contest/{id}/judge` `{}` + lazy auto-judge on reads: when currentDay >= endDay
    and OPEN → sort entries by qualityScore desc (deterministic, tie-break by entry id asc),
    assign placements 1..n, award the prize to placement 1 via adjustWallet(+prizeGel) (and
    optionally split: v1 = winner takes all), mark JUDGED. Idempotent.
  - `GET /api/contest/{id}/results` → entries with placements (after judged).
- Flyway V22. Tests: create OPEN; enter snapshots quality + one-entry-per-character (2nd → 400);
  enter after endDay → 400; enter an unowned item → 404/403; judge after advancing the clock
  ranks by quality and pays the winner (grow two bottles of different quality for two chars via
  /api/cellar/{id}/grow with different seeds/pick days, enter both, advance clock, judge, assert
  the higher-quality bottle's owner is placement 1 and got prizeGel); double-judge no-op.
  Owns `com.game.contest` only + V22 + tests.

## Deferred (connective pass, sequential from green)
- Achievement criteria auto-checked from real actions; contest wins feed RANKING + reputation;
  chat realtime over WebSocket; plus all earlier deferred wiring. Cross-cutting — sequential.
