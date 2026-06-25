# QVEVRI — Full MMORPG Backlog

Everything left to turn the current build (a tested simulation + a single-player
"grow a vintage" demo) into a real, persistent, multiplayer Georgian wine MMORPG.

Legend: ✅ done · 🟡 foundation exists · ⬜ not started

> **Reality check.** This is a multi-year effort for a team (engineering + 3D art
> + audio + design + live-ops). The hardest *systemic* part — the deterministic
> wine simulation — is already built. Most of what follows is breadth, content,
> persistence, and scale. Epics are ordered roughly by dependency.

---

## EPIC 1 — Accounts & Identity 🟡
- 🟡 Login/token exists but is **dev-grade** (in-memory users, auto-register, tokens lost on restart) — must be hardened
- ⬜ Persistent account store in a real DB (email, hashed password, status)
- ⬜ Register / verify / password-reset flows
- ⬜ Proper session tokens (expiry, refresh, revoke), rate limiting, lockout
- ⬜ Multiple **characters per account**
- ⬜ Account security & privacy (GDPR-style data handling)

## EPIC 2 — Character Creation & Character Types ⬜
- ⬜ `Character` entity (name, account link, appearance, created-at, rank)
- ⬜ **Character types / careers** (GDD Part 8 unbundled value chain), each a "class":
  grower (*glekhi → mevenakhe*), winemaker, enologist/lab owner, négociant,
  broker, cooper, nurseryman, hauler, merchant/*duqani* keeper, sommelier/critic,
  consultant, council member
- ⬜ Starting kit & starting rank per career (a glekhi starts with one vine + a strip of land)
- ⬜ Character-creation screen (name, career, appearance, **starting region/spawn**)
- ⬜ Appearance/customization (model variants, regional dress, gender, palette)
- ⬜ Per-career **skill trees / progression** (the 4 currencies: skill, capital, reputation, relationships)
- ⬜ Character persistence + character-select screen

## EPIC 3 — World, Regions & Spawn Selection ⬜
- ⬜ Region data model for all 7 zones (GDD Part 3): Kakheti, Kartli, Imereti,
  Racha-Lechkhumi, Samegrelo, Guria/Adjara, Meskheti — each with climate, grapes, method, risks
- ⬜ **Spawn/region selection** at character creation (+ starting village per region)
- ⬜ Extend the sim beyond Saperavi/Kakheti: more grapes & regional methods
  (Imeretian no-stems, Kakhetian 100% chacha, maglari, etc.)
- ⬜ World map + zones + **micro-plots** with hidden terroir stats (soil/slope/aspect/altitude/frost)
- ⬜ 3D world per region: terrain, villages, *marani*, *bazari*, *koshki* towers, landmarks
- ⬜ Travel between regions (roads/passes → hauler role); fast-travel vs overland

## EPIC 4 — Persistence Layer (the backbone) 🟡
- 🟡 H2 + JPA wired for the movement slice only; needs to become the system of record
- ⬜ Production DB (Postgres) + schema migrations (Flyway/Liquibase)
- ⬜ Persist **everything**: accounts, characters, vineyards/plots, vines, inventories,
  wallets, market listings, contracts, buildings, reputation, region/world state, the clock
- ⬜ Fidelity tiers for scale (GDD Part 11): Tier A per-vine (active), B per-block (idle), C regional field
- ⬜ Caching layer (Redis) for hot state; DB connection pooling

## EPIC 5 — Persistent World Clock & Server-Side Simulation ⬜
- ⬜ Continuous **server-authoritative world clock** + seasonal calendar (not one-shot per request)
- ⬜ Daily tick job advancing all vineyards/vines/threats/weather across the world
- ⬜ Decide & implement **time compression** (open design decision — gates everything)
- ⬜ Player actions applied against persistent state (prune, tend, *pijaji*, harvest, …) with server validation
- ⬜ Job scheduler + idempotent, restart-safe ticking; catch-up for offline plots

## EPIC 6 — Economy at MMO Scale (finish Phase 2) 🟡
- 🟡 econ core built (items, `WinePricer`, `Bazari`, contracts skeleton) — not yet wired to play
- ⬜ Market REST endpoints + Unity market UI (list / browse / buy) — **immediate next step**
- ⬜ Wallet/currency + economic sinks (holding tax, upkeep, spoilage, transfer tax)
- ⬜ Full contract types (spot, seasonal supply, profit-share, wages, sharecropping, consulting)
- ⬜ Player-run **service businesses** (lab, custom-crush, nursery, hauling, cold storage)
- ⬜ Grading + **reputation engine** + trusted "graded by" stamps
- ⬜ **Land & anti-monopoly** system (use-it-or-lose-it, super-linear holding tax, council release, ceilings)

## EPIC 7 — In-World Gameplay (3D play loop) 🟡
- 🟡 character can move on a plane (movement slice) — needs to become real play
- ⬜ Own & manage a vineyard in 3D (walk the plot; the ~15 vineyard operations as in-world actions)
- ⬜ Cellar/*marani* management (fill qvevri, *pijaji*, seal, open, distill chacha, bottle, label)
- ⬜ Visual "tells" for sim state (oily leaf = downy mildew, boar tracks, etc. — GDD Part 11)
- ⬜ Inventory UI, character sheet, skills UI (replace the IMGUI debug panels)
- ⬜ NPC labor & hiring; apprenticeship

## EPIC 8 — Networking & Multiplayer at Scale 🟡
- 🟡 single shared lobby with movement broadcast exists (a slice, not MMO-grade)
- ⬜ Many players in shared regions: zoning, **area-of-interest / interest management**
- ⬜ Server sharding / instancing per region; horizontal scale + load balancing
- ⬜ Real-time presence (see others in village/market), entity replication
- ⬜ Chat (local / region / guild / whisper), friends, social graph
- ⬜ Anti-cheat: full server authority, action validation, rate limits

## EPIC 9 — Governance & Society (GDD Part 9–10) ⬜
- ⬜ Households (*komli*), villages (*temi*), cooperatives, guilds, houses/châteaux
- ⬜ Consent-based governance: elders/*khevisberi*, assemblies (*darbazi*), elections, depose/secede, taxes
- ⬜ Settlements & **building/construction** (communal marani, bazari, supra hall, koshki, roads)
- ⬜ Optional, opt-in conflict layer (feuds, mediated peace) — off by default

## EPIC 10 — Seasonal & Social Events (GDD Part 10) ⬜
- ⬜ **rtveli** (harvest) server-wide seasonal event (communal harvest, hiring frenzy)
- ⬜ **supra** (feast) social hub + *tamada* (toastmaster) minigame
- ⬜ Vintage-of-the-year, collectibles, leaderboards

## EPIC 11 — Content & Art (large, ongoing) ⬜
- ⬜ 3D models: characters (regional dress, ranks), vines (growth stages), qvevri, marani, buildings, koshki, terrain ×7 regions
- ⬜ Animations (tending, foot-treading crush, toasting), VFX
- ⬜ Audio: UNESCO-listed polyphonic soundtrack, ambient, SFX
- ⬜ UI/UX art incl. real Mkhedruli (ქართული) script on signage & labels
- ⬜ Authenticity pass with a Georgian winemaker/cultural consultant

## EPIC 12 — Client Architecture (Unity, MMO-grade) 🟡
- 🟡 Unity project opens/plays with URP; debug panels only
- ⬜ Login → character-select → character-create → in-world flow & screens
- ⬜ Real UI framework (replace IMGUI), HUD, menus
- ⬜ World/scene streaming, addressables, third-person rig & camera
- ⬜ Client state sync against the persistent world; reconnection/resilience

## EPIC 13 — Live-Ops, Infra, Polish ⬜
- ⬜ Deployment, environments, CI/CD to servers; observability (logs/metrics/traces)
- ⬜ Balancing tools & telemetry; economy dashboards (vs hoarding bots)
- ⬜ Load/scale testing; performance budgets
- ⬜ Onboarding/tutorial ("a glekhi is never stuck"), localization (KA/EN)
- ⬜ Playtesting program, community, support

---

## Design decisions to lock before scaling (GDD Part 13)
- ⬜ Time compression (qvevri maturation pace) — **gates persistence & economy math**
- ⬜ Threat realism / catastrophe harshness
- ⬜ Launch professions; conflict layer on/off
- ⬜ The minute-to-minute fun loop (what a 20-min session feels like)

## What's reusable today (the head start)
The deterministic simulation (Phases 0–1, 23 threats), the econ core, the wire
contracts, the Maven/test/CI scaffolding, and the working sim-over-HTTP +
Unity demo. The simulation is the moat; the rest is breadth, persistence, and scale.

## Suggested near-term order (next few increments)
1. Market loop (endpoint + UI) — finish making wine tradeable.
2. Real persistence (Postgres) for accounts + characters + vineyards.
3. Character creation + character types + region/spawn selection (Epics 2–3, minimal).
4. Persistent world clock so vineyards live between sessions (Epic 5).
5. Then breadth: regions, professions, society.
