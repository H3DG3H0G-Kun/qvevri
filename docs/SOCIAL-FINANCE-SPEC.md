# SOCIAL & FINANCE SPEC (v1)

Three additive, new-package lanes. Pre-assigned migrations: **GUILDS=V14, AUCTION=V15, BANK=V16.**

## Hard rules (all lanes)
- Additive only; all existing tests stay green. NEW packages only — do NOT edit
  `Character`, `CellarItem`, `WinePricer`, market, goods, wine, land, trade,
  progression, quest, economy, logistics, build, sim, or config. Call existing
  services/repos (`CharacterService.adjustWallet/getOwned`, `GoodsService`,
  `CellarItemRepository`, `WorldClockService.currentAbsoluteDay`) — never modify them.
- Inline bearer auth via `AccountTokenService` + ownership via
  `CharacterService.getOwned`. Matchers for `/api/guild|auction|bank/**` already added.
- Time-based effects (auction expiry, loan interest) use the world clock's sim-day and
  resolve LAZILY on access (no scheduler hook needed) — deterministic, no wall-clock.
- H2 dev/test via ddl-auto — avoid reserved-word column names (no `value`, `year`,
  `state`, `level`, `status`, `balance` may be reserved → prefer `guild_status`,
  `auction_status`, `gel_balance`, etc.; verify against H2). Flyway for prod.
- Money math in GEL doubles; never let a wallet go negative except where a loan
  explicitly allows it (bank lane decides its own rule).

## LANE GUILDS — `com.game.guild`. Migration V14.
Player "wine houses" (cooperatives) with members, roles, and a shared treasury.
- `Guild` @Entity (`guilds`): id, name (unique), founderCharacterId, treasuryGel
  (double, default 0), createdAt. `GuildMember` @Entity (`guild_members`): id, guildId,
  characterId (unique across guilds — a character is in at most one guild), guildRole
  ("FOUNDER"|"MEMBER"), joinedAt. Repos as needed.
- `GuildService` + `GuildController` `/api/guild/**` (auth + ownership of the acting char):
  - `POST /api/guild/create` `{characterId, name}` → creates guild + FOUNDER membership
    (400 if name taken or character already in a guild).
  - `POST /api/guild/{guildId}/join` `{characterId}` → adds MEMBER (400 if already in any guild).
  - `POST /api/guild/{guildId}/leave` `{characterId}` → removes membership (founder can
    leave only if last member, or transfer — v1: 400 if founder with other members).
  - `GET /api/guild/{guildId}` → guild + member list.
  - `POST /api/guild/{guildId}/deposit` `{characterId, amountGel}` → moves GEL from the
    member's wallet to treasury (adjustWallet −, treasury +; 400 insufficient).
  - `POST /api/guild/{guildId}/withdraw` `{characterId, amountGel}` → FOUNDER only;
    treasury → wallet (400 if treasury short or not founder).
- Flyway V14. Tests: create + membership; duplicate name 400; join/second-guild 400;
  deposit moves wallet→treasury; withdraw founder-only + funds guarded; leave rules.
- Owns: `com.game.guild` only + V14 + tests.

## LANE AUCTION — `com.game.auction`. Migration V15.
Timed auctions with competitive bidding; lazy settlement on expiry.
- `Auction` @Entity (`auctions`): id, sellerCharacterId, kind ("GOODS"|"CELLAR_ITEM"),
  refId, quantity, startBidGel, currentBidGel (nullable), highBidderCharacterId
  (nullable), endDay (long, sim-day), auctionStatus ("OPEN"|"SETTLED"|"CANCELLED"),
  createdAt. Repo: findByAuctionStatus, findBySellerCharacterId.
- Bids: hold the current high bid by reserving the bidder's GEL is OPTIONAL; v1 simpler —
  just record currentBid/highBidder and charge the winner at settlement (validate the
  bidder *can* afford the bid at bid time). New bid must exceed currentBid (or startBid).
- `AuctionService` + `AuctionController` `/api/auction/**` (auth + ownership):
  - `POST /api/auction/create` `{characterId, kind, refId, quantity, startBidGel,
    durationDays}` → seller must own the goods/item; escrow the cellar item or hold the
    goods (decrement to reserve); endDay = currentDay + durationDays; OPEN.
  - `GET /api/auction/open` → OPEN auctions (auto-settle any past endDay first — see below).
  - `POST /api/auction/{id}/bid` `{characterId, amountGel}` → must be OPEN and not past
    endDay, amount > current/start, bidder != seller, bidder can afford (400 otherwise).
  - `POST /api/auction/{id}/settle` `{characterId}` and lazy auto-settle on reads: when
    `currentDay >= endDay`, if there is a high bidder → charge winner (adjustWallet −),
    pay seller (+), transfer item to winner; else return the escrowed item to the seller;
    mark SETTLED. Idempotent.
- Flyway V15. Tests: create escrows item; bid raises currentBid; too-low bid 400; can't
  bid own auction; settle after endDay (advance clock) pays seller + transfers to winner;
  no-bid settle returns item to seller; double-settle no-op.
- Owns: `com.game.auction` only + V15 + tests.

## LANE BANK — `com.game.bank`. Migration V16.
Savings + loans with per-sim-day interest (money sink).
- `BankAccount` @Entity (`bank_accounts`): id, characterId (unique), savingsGel (double,
  default 0), createdAt. `Loan` @Entity (`loans`): id, characterId, principalGel,
  outstandingGel, dailyRate (double, e.g. 0.01), openedDay (long), lastAccruedDay (long),
  loanStatus ("OPEN"|"REPAID"), createdAt.
- Interest accrues LAZILY: on any access (status/repay), apply
  `outstanding *= (1 + dailyRate)^(currentDay − lastAccruedDay)` then set
  lastAccruedDay = currentDay. Deterministic.
- `BankService` + `BankController` `/api/bank/**` (auth + ownership):
  - `GET /api/bank/{characterId}` → account + open loans (interest accrued first).
  - `POST /api/bank/deposit` `{characterId, amountGel}` → wallet → savings (400 insufficient).
  - `POST /api/bank/withdraw` `{characterId, amountGel}` → savings → wallet (400 short).
  - `POST /api/bank/loan` `{characterId, amountGel}` → create OPEN loan, credit wallet +amount
    (cap max principal by a rule, e.g. ≤ 1000; 400 if an OPEN loan already exists).
  - `POST /api/bank/repay` `{characterId, amountGel}` → accrue, then wallet → loan
    outstanding (400 insufficient wallet); when outstanding hits 0 → REPAID.
- Flyway V16. Tests: deposit/withdraw move money + guard; loan credits wallet + creates
  OPEN loan; second loan 400; interest grows outstanding after advancing the clock; repay
  reduces outstanding and flips REPAID at 0.
- Owns: `com.game.bank` only + V16 + tests.

## Deferred (the connective pass, do sequentially from green)
- Guild treasury funding guild-built estates; auction price feeding the ECONOMY index;
  bank interest as a macro sink; plus the earlier deferred building→pipeline,
  economy→market, and XP/quest→actions wiring. All cross-cutting — sequential, not parallel.
