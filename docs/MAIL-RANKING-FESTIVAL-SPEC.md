# MAIL, RANKINGS & FESTIVALS SPEC (v1)

Three additive, new-package lanes. Pre-assigned migrations: **MAIL=V17, RANKING=V18, FESTIVAL=V19.**

## Hard rules (all lanes)
- Additive only; all existing tests stay green. NEW packages only — do NOT edit
  `Character`, `CellarItem`, `WinePricer`, market, goods, guild, bank, auction, or any
  other existing package or config. READ their repositories/services (call, never edit):
  `CharacterService.adjustWallet/getOwned`, `CharacterRepository`, `GoodsService`,
  `CellarItemRepository`, `GuildRepository`/`GuildMemberRepository`,
  `WorldClockService.currentAbsoluteDay/currentDayOfYear`.
- Inline bearer auth via `AccountTokenService` + ownership via `CharacterService.getOwned`.
  Matchers for `/api/mail|ranking|festival/**` already added.
- Time-based effects resolve LAZILY off the world clock (no scheduler). Deterministic.
- H2 dev/test via ddl-auto — avoid reserved-word columns (no `value`, `year`, `state`,
  `level`, `status`, `read`, `to`, `from` → use `mail_status`, `is_read`, `sender_*`,
  `season_year`, etc.; verify against H2). Flyway for prod.
- **CRITICAL (compile-safety):** in tests, declare every response-body map you call
  `containsKey`/`containsKeys` on as `Map<String,Object>` (NOT `Map<?,?>`) — the wildcard
  capture is a compile error. For endpoints that can return a 4xx, read the body with
  `String.class` (an error envelope is a JSON object, not a List) and assert on status.

## LANE MAIL — `com.game.mail`. Migration V17.
In-game mailbox: system + player letters, with optional attachments that deliver on claim.
- `Mail` @Entity (`mailbox`): id, recipientCharacterId, senderCharacterId (nullable = system),
  subject, body, attachKind (nullable: "GEL"|"GOODS"|"CELLAR_ITEM"), attachRefId (nullable
  String: goodTypeId or cellarItemId; null for GEL), attachAmount (double, 0 if none),
  isRead (boolean default false), isClaimed (boolean default false), createdAt. Repo:
  findByRecipientCharacterId, findByRecipientCharacterIdAndIsClaimedFalse.
- `MailService` + `MailController` `/api/mail/**` (auth + ownership of the acting char):
  - `POST /api/mail/send` `{senderCharacterId, recipientCharacterId, subject, body,
    attachKind?, attachRefId?, attachAmount?}` → if an attachment is included, ESCROW it
    from the sender now (GEL → adjustWallet(sender,−amount); GOODS → GoodsService.decrement;
    CELLAR_ITEM → set escrowed=true) so it can't be double-spent; persist the mail. System
    mail (no sender) may be created without escrow by a helper (not exposed publicly), but
    the public send requires a sender who owns the attachment.
  - `GET /api/mail/{characterId}` → that character's mail (recipient).
  - `POST /api/mail/{mailId}/read` `{characterId}` → mark isRead (recipient only).
  - `POST /api/mail/{mailId}/claim` `{characterId}` → recipient claims the attachment:
    GEL → adjustWallet(recipient,+amount); GOODS → grant; CELLAR_ITEM → setCharacterId(recipient)
    + clear escrow. Idempotent: already-claimed → 400. Mail with no attachment → claim is a no-op/400.
  - `POST /api/mail/{mailId}/delete` `{characterId}` → recipient deletes (only if no
    unclaimed attachment, else 400).
- Flyway V17. Tests: send with GEL attachment escrows sender wallet; recipient sees it;
  read marks read; claim credits recipient + can't double-claim; send GOODS/CELLAR_ITEM
  transfers on claim; ownership enforced. Owns `com.game.mail` only + V17 + tests.

## LANE RANKING — `com.game.ranking`. Migration V18.
Leaderboards across the world — computed live, with optional persisted snapshots.
- A `RankingService` that builds leaderboards by reading existing repositories (READ-ONLY):
  - WEALTH — top characters by `Character.walletGel` (use `CharacterRepository`).
  - VINTNER — top characters by best `CellarItem.quality` they own (use `CellarItemRepository`).
  - GUILD — top guilds by member count and/or treasury (use guild repos).
  Each entry: rank, subjectId, subjectName, score. Return top N (e.g. 20).
- `RankingSnapshot` @Entity (`ranking_snapshots`): id, board (String), subjectId, subjectName,
  score (double), rankPos (int), simDay (long), createdAt — persisted by a snapshot endpoint
  for history/trends. Repo: findByBoardOrderByRankPosAsc (most recent snapshot set).
- `RankingController` `/api/ranking/**` (auth):
  - `GET /api/ranking/wealth` / `GET /api/ranking/vintner` / `GET /api/ranking/guild` →
    live top-N entries.
  - `GET /api/ranking/me?board=&characterId=` → the caller's own rank + score on a board
    (auth + ownership).
  - `POST /api/ranking/snapshot` `{board}` → compute + persist the current board, return it.
- Flyway V18. Tests: seed a couple of characters with different wallets → wealth board
  orders them descending with correct ranks; vintner board reflects cellar quality; me-rank
  returns the caller's position; snapshot persists. (Reads are cross-package but read-only.)
  Owns `com.game.ranking` only + V18 + tests.

## LANE FESTIVAL — `com.game.festival`. Migration V19.
A world calendar of seasonal events with active windows and participation rewards.
- A static `FestivalCalendar` of ~4 events, each: id, name, description, startDayOfYear,
  endDayOfYear (inclusive window within the 0–364 year), bonusType (String), bonusValue
  (double), rewardGel (double). E.g. "Rtveli" (harvest festival, autumn window),
  "New Wine Fair", "Vine Blessing" (spring), "Qvevri Opening". Provide all() + active(day).
- `FestivalParticipation` @Entity (`festival_participations`): id, characterId, festivalId,
  seasonYear (int), claimed (boolean), createdAt — one row per (character, festival, year).
  Repo: findByCharacterIdAndFestivalIdAndSeasonYear.
- `FestivalService` + `FestivalController` `/api/festival/**` (auth + ownership):
  - `GET /api/festival/calendar` → all festival definitions.
  - `GET /api/festival/active` → festivals whose [start,end] day-of-year window contains the
    current world day-of-year (lazy from `WorldClockService.currentDayOfYear`).
  - `POST /api/festival/{festivalId}/participate` `{characterId}` → only if the festival is
    currently active (else 400 NOT_ACTIVE); idempotent per (character, festival, current
    world year) — first time grants `rewardGel` via adjustWallet and records participation;
    repeat in same year → 400 ALREADY_PARTICIPATED.
- Flyway V19. Tests: calendar returns events; active reflects the clock (advance the clock
  into a festival window via /api/world/advance, then assert it's active); participate during
  an active festival grants GEL and records; participating twice same year → 400; participating
  when not active → 400; ownership enforced. Owns `com.game.festival` only + V19 + tests.

## Deferred (connective pass, sequential from green)
- Route auction-outbid refunds and trade receipts through MAIL; feed festival bonuses into the
  WINE pipeline and ECONOMY; surface RANKINGS to guild/festival rewards. Plus all earlier
  deferred wiring. Cross-cutting — sequential, not parallel.
