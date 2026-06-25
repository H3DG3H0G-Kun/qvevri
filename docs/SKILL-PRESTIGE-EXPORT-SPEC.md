# SKILLS, PRESTIGE & EXPORT SPEC (v1)

Three additive, new-package lanes. Pre-assigned migrations: **SKILL=V27, PRESTIGE=V28, EXPORT=V29.**

## Hard rules (all lanes)
- Additive only; all existing tests stay green. NEW packages only — do NOT edit `Character`,
  `CellarItem`, market, goods, economy, or any existing package or config. READ their
  repos/services (call, never edit): `CharacterService.adjustWallet/getOwned`,
  `CellarItemRepository`, `WinePricer` (read-only base price), `WorldClockService`.
- Inline bearer auth (`AccountTokenService`) + ownership (`CharacterService.getOwned`).
  Matchers for `/api/skill|prestige|export/**` already added.
- H2 dev/test via ddl-auto — reserved-word-safe columns (no `value`,`year`,`state`,`level`,
  `status`,`rank`,`points` may be risky → use `skill_points`,`title_rank`,`export_status`,etc.;
  verify against H2). Flyway for prod. Time effects lazy off the world clock.
- **Compile-safety (tests):** response maps used with `containsKey` declared `Map<String,Object>`
  (NOT `Map<?,?>`); 4xx reads `String.class`; list reads `List.class`.

## LANE SKILL — `com.game.skill`. Migration V27.
A per-character talent tree: spend skill points on passive talents.
- Static `SkillCatalog` of ~8 talents (record/class): id, title, description, cost (int points),
  prereqId (nullable), bonusType (String), bonusValue (double). Examples: "green_thumb" (yield),
  "master_palate" (quality), "shrewd_bargainer" (sell margin), "frugal_logistics" (shipping),
  "deep_cellar" (aging cap), "vine_whisperer" (threat resistance, prereq green_thumb), "haggler"
  (buy discount), "vintner_eye" (grade accuracy). Provide all() + find(id).
- `SkillProfile` @Entity (`skill_profiles`): id, characterId (unique), totalPoints (int),
  spentPoints (int), createdAt. `LearnedSkill` @Entity (`learned_skills`): id, characterId,
  skillId (String), learnedAt (long), unique (characterId, skillId). Repos for both.
- v1 point source: lazy-create a SkillProfile at totalPoints = STARTING_POINTS (e.g. 5) on first
  access. (Earning more points by leveling ties to progression in the integration pass — note it.)
- `SkillService` + `SkillController` `/api/skill/**` (auth + ownership):
  - `GET /api/skill/catalog` → talents.
  - `GET /api/skill/{characterId}` → { totalPoints, spentPoints, availablePoints, learned[] }
    (lazy-create profile).
  - `POST /api/skill/{skillId}/learn` `{characterId}` → 404 unknown talent; 400 already learned;
    400 PREREQ_NOT_MET if prereq not learned; 400 INSUFFICIENT_POINTS if availablePoints < cost;
    else record LearnedSkill + spentPoints += cost. Return updated profile.
  - `POST /api/skill/respec` `{characterId}` → clears all LearnedSkills + resets spentPoints=0
    (optional small GEL cost via adjustWallet; v1 free, note it). Return profile.
  - `GET /api/skill/bonuses/{characterId}` → aggregated bonusType → summed bonusValue across learned.
- Flyway V27. Tests: catalog; profile lazy-creates with 5 points; learn spends points + records;
  prereq enforced; insufficient points → 400; double-learn → 400; respec clears + frees points;
  bonuses aggregate. Owns `com.game.skill` only + V27 + tests.

## LANE PRESTIGE — `com.game.prestige`. Migration V28.
Prestige points and the noble-title ladder (the social arc spine from the CONTENT-BIBLE).
- Static `TitleLadder`: ordered titles with a prestige threshold each, e.g.
  GLEKHI(0) → MEVENAKHE(50) → MEURNE(200) → AZNAURI(600) → TAVADI(1500) (use bible-consistent
  Georgian-flavoured names + ascending thresholds). Provide all() + titleFor(prestige).
- `PrestigeProfile` @Entity (`prestige_profiles`): id, characterId (unique), prestige (long),
  titleRank (String — current title), updatedAt. Repo: findByCharacterId.
- `PrestigeService` + `PrestigeController` `/api/prestige/**` (auth + ownership):
  - `GET /api/prestige/ladder` → the title ladder (titles + thresholds).
  - `GET /api/prestige/{characterId}` → { prestige, title, nextTitle, prestigeToNext } (lazy-create at 0).
  - `POST /api/prestige/{characterId}/award` `{amount, reason}` → add prestige (reject amount<=0),
    recompute titleRank from the ladder, return updated profile. (v1 exposes award for client/dev;
    later, contests/festivals/wealth feed prestige automatically — note it.)
- Flyway V28. Tests: ladder returned ordered; profile auto-creates GLEKHI at 0; award raises
  prestige + promotes title at thresholds (50 → MEVENAKHE, etc.); nextTitle/prestigeToNext correct;
  award<=0 → 400; ownership. Owns `com.game.prestige` only + V28 + tests.

## LANE EXPORT — `com.game.export`. Migration V29.
Foreign markets: sell wine abroad at different prices, with tariffs and demand.
- Static `ForeignMarketCatalog` of ~4 markets (record/class): id, name, priceMultiplier (double,
  e.g. Russia 1.3, Byzantium 1.5, Persia 1.2, Poland_Lithuania 1.15), tariffRate (double, e.g.
  0.10), demandNote (String). Provide all() + find(id).
- `ExportRecord` @Entity (`export_records`): id, sellerCharacterId, foreignMarketId, cellarItemId,
  grossGel, tariffGel, netGel, soldDay (long), createdAt. Repo: findBySellerCharacterId.
- Pricing: base = a per-bottle base value derived from the CellarItem (read its quality; use a
  simple `base = quality × K` with a documented K, or read `WinePricer` base if easily callable —
  read-only); gross = base × quantity × market.priceMultiplier; tariff = gross × market.tariffRate;
  net = gross − tariff.
- `ExportService` + `ExportController` `/api/export/**` (auth + ownership):
  - `GET /api/export/markets` → the foreign markets.
  - `POST /api/export/sell` `{characterId, foreignMarketId, cellarItemId, quantity}` → character must
    own the cellar item (404 otherwise); compute gross/tariff/net; credit net via adjustWallet(+net);
    consume/decrement the sold quantity from the cellar item (or mark sold — reassign to a sink /
    set quantity; if simpler, require selling the whole item and delete/escrow it — pick one and
    document); persist an ExportRecord; return { record, walletGel }.
  - `GET /api/export/{characterId}` → that character's ExportRecords.
- Flyway V29. Tests: markets returned; sell credits wallet by net (gross − tariff) and records it;
  higher-priceMultiplier market pays more for the same bottle; selling an unowned item → 404;
  ownership enforced. Use AccountTestHelper; grow a bottle via /api/cellar/{id}/grow to have something
  to export. Owns `com.game.export` only + V29 + tests.

## Deferred (integration pass, sequential from green)
- Skill/prestige bonuses applied to live actions; prestige auto-fed by contests/wealth/festivals;
  export demand shifts over time + Merchant career export bonus; skill points from leveling.
