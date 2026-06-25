# QVEVRI — CONTENT BIBLE (v1)

*The narrative + systems design spine for QVEVRI, the Georgian wine-trade MMORPG.*

Owned by SCREENWRITER / narrative designer. This document is **design + content**, not
engineering. Where it reads like a spec ("CAREER IDENTITIES", "REGION CHARACTERS"), it is
a brief **for** engineering — the numbers and rules here are targets to implement in the
careers / regions / travel follow-up lanes, not things already built. The quest line in
`com.game.quest` is the one part of this bible that ships as code today.

Ground truth is the running backend: 7 regions, 10 calibrated varieties, 9 careers, 22
goods, the dynamic economy, and ~33 systems. Nothing here contradicts those values; it
gives them a soul.

---

## 1. World & tone premise

Georgia did not invent wine in a laboratory. It found it in a clay jar buried to the
shoulder in cool earth — the *qvevri* — eight thousand years ago, and never let go. In
QVEVRI you arrive as a *glekhi*, a peasant, with one hundred lari, a borrowed hoe, and
the wrong kind of pride. The land remembers older families than yours. The wine remembers
older hands. You begin at the bottom of a culture where a man's worth is measured not in
coin but in whether his *marani* — his cellar of buried jars — is one strangers walk a day
to drink from.

The tone is warm, earthy, and rooted in the real thing: the *tamada* who can make a table
weep and laugh in the same breath; the *supra* feast where the third toast is always to
those no longer at the table; the *Rtveli* harvest when whole valleys move at once and the
air goes sweet with crushed Saperavi; *churchkhela* strung up to dry like candles. We are
evocative but never precious. Wine here is labour before it is luxury — frost kills, mildew
rots, buyers haggle, loans come due. The fantasy is not escapism; it is the slow, satisfying
climb from a peasant's single vine to a *tavadi's* wine house whose amber Rkatsiteli is
poured at weddings three regions away.

---

## 2. THE PROGRESSION ARC — five phases, poor to rich

The spine of the game is one curve: **100 GEL → magnate**. Five named phases, each a
distinct fantasy with its own verbs, unlocks, and money shape. The bands below are *target*
net-worth ranges (wallet + assets), tuned so the climb is a grind with real plateaus, never
trivial and never a wall. Approximate real-time pacing assumes the live world clock
(~30 real-seconds per sim-day in prod), so a sim-year is a session-spanning arc, not a coffee break.

### Phase I — GLEKHI (the Peasant) · ~0–500 GEL
**Fantasy:** one vine, one hoe, one stubborn dream. You are nobody, and the valley knows it.
- **Does:** plant first cuttings on rented/cheap land; survive a first harvest; craft or buy
  one 300 L qvevri; sell your first three bottles at the Telavi bazaar; run the tutorial
  quest chain under the tamada's eye.
- **Unlocks:** the bazaar, the marani (basic cellar), first festival participation (Vine
  Blessing), the starter career kit.
- **Sources:** quest rewards (the dominant early income — see Economy table), first small
  harvest sale, festival stipends.
- **Sinks:** vine stock, basic tools (hoe, shears), the first vessel, sprays against mildew.
- **Exit when:** you own a vineyard that survived a vintage and a vessel to ferment in (~500 GEL net worth).

### Phase II — SMALLHOLDER · ~500–3,000 GEL
**Fantasy:** you make *your own* wine now, not just grapes. A real bottle with your name implied on it.
- **Does:** plant a proper block (10–40 vines); ferment a full qvevri; manage tending levers
  (canopy, sprays, guardians) across a season; sell finished wine on the player market; enter
  your first contest; take a small bank loan to buy a second vessel.
- **Unlocks:** the cellar building, research tier 1 (improved pruning, temp control), staff
  hiring (a vineyard hand), guild membership, the Rtveli festival as a producer.
- **Sources:** finished-wine sales (now the spine), contest prizes, tourism trickle, mid-tier quests.
- **Sinks:** vessels (a second qvevri / first steel tank), wages, loan interest, research fees, land expansion.
- **Exit when:** consistent multi-vessel production, a cellar, and ~3,000 GEL net worth.

### Phase III — ESTATE OWNER · ~3,000–15,000 GEL
**Fantasy:** an estate with a name. People ask where your wine is from before they ask your price.
- **Does:** own land in 2+ parcels; run the press-house; build a real staff (cellar master,
  clerk); ship wine to distant regions via logistics; win or place in regional contests;
  found or lead a guild; chase appellation fit by matching variety to its real home region.
- **Unlocks:** press-house building, research tier 2 (rootstock, cold soak, barrel program),
  auctions as a seller, the New Wine Fair, prestige market channels.
- **Sources:** premium appellation wine, auctions, tourism (now meaningful with buildings),
  guild dividends, export merchant orders.
- **Sinks:** the press-house and second buildings, full payroll, shipping costs, contest entry
  fees, larger loans, second-region land.
- **Exit when:** multi-region footprint, full building set, ~15,000 GEL net worth.

### Phase IV — WINE HOUSE · ~15,000–60,000 GEL
**Fantasy:** a *house*, not a man — a brand. Négociants buy your bulk; merchants fight for your label.
- **Does:** operate at volume across regions; blend appellation cuvées; sign recurring supply
  contracts; dominate a contest circuit; bankroll guild treasury and contests; employ the
  full staff roster; lend (via bank savings) rather than borrow.
- **Unlocks:** export licences, private-label négociant bonuses, top research nodes (barrel
  program), guild leadership perks, ranking-board prestige (Wealth / Vintner boards).
- **Sources:** volume contracts, export margin, high-tier auctions, contest circuits, tourism
  at scale, savings interest.
- **Sinks:** capital vessels (2000 L steel, oak programs), large payroll, guild patronage,
  reinvestment land, festival sponsorship.
- **Exit when:** you are a fixture on the Wealth board with ~60,000 GEL net worth.

### Phase V — TAVADI / MAGNATE (the Wine Lord) · 60,000+ GEL
**Fantasy:** the *tavadi*, the noble lord of the valley. Your marani is a pilgrimage. Your toast closes the supra.
- **Does:** set the market rather than follow it; sponsor festivals and contests as the NPC
  patrons once did; mentor newcomers (quest-giver role potential); collect rare-vintage
  auctions; rule a guild that shapes a region's economy.
- **Unlocks:** endgame prestige titles, festival-patron status, legacy achievements, the top
  of every ranking board.
- **Sources:** market-making spreads, prestige auctions, guild empire, tourism empire, legacy quests.
- **Sinks:** patronage (festivals, contests, guild treasury), prestige acquisitions, the deliberate
  *gifting* and feasting that is the whole point of being rich in this culture — wealth here is
  spent generously, or it is wasted.

---

## 3. ECONOMY BALANCE — sources vs sinks by phase

Goal: **100 GEL → 60,000+ GEL** is a long, satisfying grind with three real plateaus
(end of II, end of III, mid-IV). Numbers below are design targets for the careers/economy
follow-up lanes to hit, expressed against systems that already exist in the backend.

### Money SOURCES (income)

| Source | System (exists) | Phase it dominates | Target magnitude |
|---|---|---|---|
| Quest rewards | `com.game.quest` | I (carries early game) | 25–600 GEL/quest, ~3,800 GEL total across the arc |
| Grape sale (raw) | market / cellar | I–II | low; a fallback, never the path to wealth |
| Finished-wine sale | `com.game.market` + `WinePricer` | II–V (the spine) | 40–200 GEL/bottle by quality×appellation×scarcity |
| Contest prizes | `com.game.contest` | II–IV | 100–800 GEL/win |
| Festival stipends | `com.game.festival` | I–III | 30–75 GEL (Vine Blessing 30 → Rtveli 75) |
| Tourism | `com.game.tourism` | III–V | 2 GEL/day base + 1/building; scales with build-out |
| Auctions (as seller) | `com.game.auction` | III–V | uncapped; prestige vintages |
| Bank savings interest | `com.game.bank` | IV–V | compounding on parked capital |
| Guild dividends / treasury | `com.game.guild` | III–V | collective income |
| Loans (advance, not income) | `com.game.bank` | II–III | up to 1,000 GEL/loan, repaid with interest |

### Money SINKS (spend)

| Sink | System (exists) | Phase it bites | Target magnitude |
|---|---|---|---|
| Vine stock | `com.game.goods` | I–II | 8–20 GEL/cutting; buy 10–100 |
| Vessels | `com.game.goods` | I–IV | **300–2,400 GEL** — the dominant capital sink |
| Equipment (press/crusher) | `com.game.goods` | II–III | 320–1,200 GEL |
| Inputs (sprays, netting) | `com.game.goods` | every season | 8–55 GEL recurring |
| Land parcels | `com.game.land` | I–IV | 200 GEL/ha |
| Buildings | `com.game.build` | II–IV | cottage→marani→cellar→press-house, escalating |
| Wages (payroll) | `com.game.labor` | II–V | 5–8 GEL/day/staff, all-or-nothing |
| Loan interest | `com.game.bank` | II–III | compounding daily |
| Market / sale fees | `com.game.economy` | II–V | 5% of gross (deliberate sink) |
| Shipping | `com.game.logistics` | III–V | distance-based (40 km/day) |
| Contest / auction entry | contest / auction | II–V | entry fees, listing costs |
| Research | `com.game.research` | II–IV | 30–70 GEL/node |
| Patronage (endgame) | guild / festival | V | uncapped, voluntary, *the point* |

### The intended curve

- **Phase I** is quest-fuelled on purpose: a glekhi can't yet make money from wine, so the
  ~3,800 GEL of total quest reward (front-loaded) bootstraps the player past the first qvevri
  (300 L = 420 GEL) without a feel-bad wall. Target: reach 500 GEL net worth in the first
  arc of play.
- **Phase II plateau:** the second vessel + first loan + wages create the first real squeeze.
  A Smallholder should *feel* the 5% market fee and daily wage drip. Wine sales must clear
  these comfortably (a quality bottle at 60–120 GEL beats a 5/day hand) but not trivially.
- **Phase III plateau:** the press-house and second-region land are big lumps (multi-thousand
  GEL). Appellation fit (variety in its real home region) should be worth a **meaningful**
  WinePricer premium — this is the lever that makes regional play pay.
- **Phase IV–V:** income compounds (savings, guild, tourism empire) faster than sinks, by
  design — the late game is about *deploying* wealth (patronage) more than chasing it.

### ⚠ Currently mis-tuned / flagged for engineering

1. **Shop sell-back at 0.5× basePrice** (`ShopController`) lets a player buy and re-sell goods
   at a flat 50% loss with no friction beyond the haircut — fine as a sink, but verify it can't
   be gamed against a fluctuating market price. **Flag: confirm sell-back never exceeds buy
   price under any economy state.**
2. **Bank loan cap of 1,000 GEL** is well-tuned for Phase II but becomes irrelevant by Phase III.
   **Flag: consider scaling the cap by rank/progression level so loans stay a lever later.**
3. **Tourism base of 2 GEL/day + 1/building** is too flat to matter before Phase III and risks
   becoming a passive trickle that's ignored. **Flag: consider a region/festival multiplier so
   tourism rewards build-out and location, per the REGION CHARACTERS brief below.**
4. **Festival rewards (30–75 GEL) are flat regardless of phase** — generous in Phase I, trivial
   by Phase IV. Acceptable (festivals become social, not economic, at the top) but **flag: a
   patron/sponsor sink at festivals would close the loop for Phase V**.
5. **Quest rewards are one-time**; once the ~3,800 GEL arc is spent the player MUST be earning
   from wine. **Flag: ensure Phase II wine income is reliably positive after wages+fees, or the
   II plateau becomes a wall.** This is the single most important tuning check.
6. **Grape (raw) sale vs finished-wine sale spread** must always favour finishing the wine, or
   the Winemaker/Enologist careers lose their reason to exist. **Flag: verify raw-grape price <
   finished-wine price minus production cost, always.**

---

## 4. CAREER IDENTITIES — a spec for engineers

All 9 careers exist as `CareerType` with cosmetic descriptions today; only **Cooper** (crafts
vessels) and **Enologist** (grades wine) have real mechanics. This section briefs the economic
**PRO** and **CON** each career should implement so that *every* career has a distinct, viable
path to magnate — different verbs, same ceiling. PROs/CONs are deliberately balanced: each
strong edge is paid for by a real limitation. Numbers are starting targets to tune.

| Career | Fantasy | PRO (implement) | CON (implement) |
|---|---|---|---|
| **GROWER** | Hands in the soil; reads the vine like scripture. | **+15% yield** on owned vineyards; early-pick premium; cheaper vine stock. | Cannot ferment to top quality — must sell grapes or partner a Winemaker; weak sell margin. |
| **WINEMAKER** | Turns fruit into wine; master of the qvevri. | **+10% wine quality** from fermentation control; skin-contact & blend bonuses. | No yield bonus (buys grapes dearer or grows fewer); weak at retail margin. |
| **ENOLOGIST** | The analytical consultant; saves faulted vintages. | Earns **consulting fees** from other players + **+quality-grade premium** on own wine (already grades). | Tiny own-production scale; income depends on a client base / partners. |
| **NÉGOCIANT** | Buys bulk cheap, bottles under their own label. | **Buys finished wine ~20% below market**; private-label resale bonus; volume contracts. | **Cannot produce** (no vineyard/cellar quality of own) — pure middleman, exposed to supply. |
| **BROKER** | Never owns the wine; owns the deal. | Earns **commission on every deal** brokered + market-price intelligence (sees true prices). | Owns no inventory — zero income in a dead market; cannot capture asset appreciation. |
| **COOPER** | Shapes the clay and the oak; the vessel maker. | **Crafts vessels below bazaar price** + sells/maintains them (already crafts); partner fermentation bonus. | Not a wine producer — income capped by vessel demand; weak at grape/wine markets. |
| **NURSERYMAN** | Propagates the heritage cuttings every vineyard needs. | **Sells certified cuttings** at margin; gatekeeper for new vineyards; rare-cultivar premium. | No wine income at all — feast in a planting boom, famine in a mature market. |
| **HAULER** | Moves grape, must, and wine across the valleys. | **−30% shipping cost** on own goods + paid per-shipment for others; refrigerated quality-preservation. | Weak at sales/quality; holds inventory but cannot age wine; idle without freight. |
| **MERCHANT** | Relationships with restaurateurs, importers, collectors. | **+20% sell margin** + recurring buy-orders + export channel; **highest income ceiling**. | **Cannot improve wine quality** (no production edge) — buys at market, lives or dies on margin. |

**Balance principle:** producers (Grower/Winemaker/Cooper/Nurseryman) make *things*; traders
(Négociant/Broker/Merchant/Hauler) move *value*; the Enologist sells *expertise*. A solo
Winemaker can climb alone; a Broker needs a living market; the optimal late game is a guild
where careers specialise and trade — which is exactly the MMO fantasy.

---

## 5. REGION CHARACTERS — a spec for engineers

Today only **Kakheti / Saperavi** is fully simulated; other regions are selectable and default
to Kakheti sim behaviour. This section briefs the per-region simulation lane: each region's
signature varieties (from the real calibrated list), climate difficulty, wine styles, hazards,
and the "why settle here" hook. Climate deltas are real (per `WorldCatalog` + the weather model):
Kakheti is the warm/dry baseline; Imereti, Samegrelo, and coastal Guria-Adjara are wetter
(higher fungal pressure); Racha and Meskheti are colder and higher-altitude (lower yields).

### KAKHETI — Telavi · the baseline, the heartland
- **Signature varieties:** Saperavi (red), Rkatsiteli, Kisi, Mtsvane (whites).
- **Climate difficulty:** ⭐ EASY (warm continental, ~1,873 GDD, hot dry summers). The training-wheels region — fully simulated.
- **Styles:** classic qvevri amber Rkatsiteli (4–6 months skin contact); powerful, ageworthy Saperavi reds.
- **Hazards:** spring frost (minor), grapevine moth, summer heat spikes; low fungal pressure (the gift of dry air).
- **Why settle here:** reliable ripening, the deepest market, every tutorial quest. **The default start.** Forgiving margins; the place to learn the craft before risking a harder terroir.

### KARTLI — Gori · the sparkling frontier
- **Signature varieties:** Chinuri (the calibrated white), plus historic Goruli Mtsvane / Shavkapito.
- **Climate difficulty:** ⭐⭐ MODERATE (transitional continental, cooler by altitude, some spring-frost risk).
- **Styles:** fresh European-style whites; sparkling-wine heartland (Chinuri's high acid is the base).
- **Hazards:** spring frost (real risk at altitude), moderate rainfall.
- **Why settle here:** high-acid Chinuri rewards the sparkling/fresh niche; near Tbilisi's market. A specialist's region — the appellation premium for Chinuri-in-Kartli should be strong.

### IMERETI — Kutaisi · the green, wet west
- **Signature varieties:** Tsolikouri, Tsitska (both calibrated, late high-acid whites), plus Krakhuna.
- **Climate difficulty:** ⭐⭐⭐ HARD (temperate oceanic, cooler and wetter than Kakheti, high fungal pressure).
- **Styles:** Imeretian method — 10–30 days partial skin contact; gentler, fresher whites than Kakhetian amber.
- **Hazards:** **downy & powdery mildew (the defining threat)**, botrytis grey-rot in wet years — sprays are non-optional here.
- **Why settle here:** Tsolikouri and Tsitska are *home* here (top appellation fit); a skilled grower who masters mildew earns a premium the easy regions can't.

### RACHA-LECHKHUMI — Ambrolauri · the high sweet north
- **Signature varieties:** Aleksandrouli (the calibrated thin-skinned red), plus Mujuretuli.
- **Climate difficulty:** ⭐⭐⭐⭐ VERY HARD (mountain microclimate, cold winters, naturally low yields).
- **Styles:** naturally semi-sweet Khvanchkara-style reds (late harvest, arrested fermentation, residual sugar).
- **Hazards:** cold/short season, frost, **low yield by nature** — quantity is scarce.
- **Why settle here:** scarcity = price. Aleksandrouli semi-sweet from Racha is prestige wine; low volume, high margin per bottle. A patient, capital-heavy play.

### SAMEGRELO — Zugdidi · the humid subtropical
- **Signature varieties:** Ojaleshi (the calibrated aromatic red, high residual-sugar potential).
- **Climate difficulty:** ⭐⭐⭐⭐ VERY HARD (humid subtropical, high rainfall, significant disease pressure year-round).
- **Styles:** rich, full-bodied aromatic reds, some semi-sweet; qvevri-friendly.
- **Hazards:** **heaviest disease pressure of any region** (warm + wet = rot heaven); demanding canopy and spray management.
- **Why settle here:** Ojaleshi is found almost nowhere else — a near-monopoly variety with strong appellation fit. The reward for surviving the disease gauntlet is a rare, sought-after red.

### GURIA & ADJARA — Batumi · the Black Sea coast
- **Signature varieties:** Chkhaveri (the calibrated rosé/light-red), plus Jani.
- **Climate difficulty:** ⭐⭐⭐ HARD (mild maritime, wet, high humidity, frost-rare).
- **Styles:** light, low-alcohol reds and rosés from Chkhaveri (light skin contact / direct press).
- **Hazards:** humidity-driven fungal pressure; the maritime damp; less heat for full ripening.
- **Why settle here:** a distinctive light-and-fresh style nobody inland can copy + coastal tourism upside (lean into the tourism multiplier here). The lifestyle region.

### MESKHETI — Akhaltsikhe · the revived ancient south
- **Signature varieties:** the calibrated set defaults here (Mtsvane-family aromatics work); historic Meskhuri Mtsvane / Tavkveri.
- **Climate difficulty:** ⭐⭐⭐⭐ VERY HARD (high-altitude south, cold winters, short season).
- **Styles:** revived ancient qvevri traditions; elegant, aromatic, high-acid whites.
- **Hazards:** altitude cold, frost, short ripening window; thin existing infrastructure.
- **Why settle here:** the *frontier* — fewest competitors, a revival story, prestige for the pioneer who proves the terroir. High risk, high narrative reward; the late-game contrarian's estate.

---

## 6. NPC ROSTER

Twelve named NPCs anchor the world: quest-givers, merchants, the tamada, the enologist, the
cooper master, and regional figures. Names are consistent with the quest catalog in
`com.game.quest`. Voice samples are authored, in-character lines for the dialogue UI.

### Tamada Giorgi — KAKHETI (Telavi) · the Toastmaster, mentor & first quest-giver
The old toastmaster of Telavi, broad-handed and broad-hearted, who has buried more friends
than vines and toasted every one. He takes a liking to the newcomer for reasons he won't explain.
- *"Every great wine starts with a single vine, child. Plant it like you mean to be buried beside it."*
- *"A man's marani tells you more than his words. Mine has wept and rejoiced for forty harvests."*
- *"Drink — but listen first. The third toast is always for those who cannot raise their cup."*

### Nino the Viticulturist — KAKHETI (Telavi) · the vine-whisperer, harvest quests
Sharp-eyed and impatient with fools, Nino reads ripeness by the crunch of a seed between her
teeth. She trusts the vine over any instrument and you over neither, yet.
- *"The vines are heavy and the air smells of Saperavi. We pick before the rains, or we pick rot."*
- *"You measured the Brix? Good. Now spit out the seed and tell me what it told you."*
- *"Frost took my grandmother's whole block in one night. Respect the cold, glekhi."*

### Potter Davit — KAKHETI · the Cooper Master, vessel quests
The qvevri-maker of the valley, clay to the elbow, who speaks to his jars as he buries them.
He believes a vessel made in anger ferments anger. Gatekeeper of the Cooper craft.
- *"No Georgian wine is complete without a proper qvevri buried in the cool of the earth."*
- *"This clay came from the river my father fished. It will outlive us both, and our wine with it."*
- *"Bring me clay and patience. I do not sell haste; haste cracks in the kiln."*

### Merchant Tamar — KAKHETI (Telavi bazaar) · the buyer, selling quests
The shrewdest trader at the Telavi bazaar, all warmth at the front of the stall and steel
behind it. She'll buy your first bottles — and remember every one if you cross her.
- *"Three bottles of your Saperavi? Let me taste before we talk coin, friend."*
- *"The market wants what the market wants. Today it wants amber, and you, conveniently, have amber."*
- *"Sell me cheap once and you're a fool. Sell me cheap twice and I'm the fool — and I am no fool."*

### Elder Ketevan — KAKHETI (Tsinandali) · the keeper of the old marani, pilgrimage quests
The ancient keeper of the ancestral marani, who tends jars older than the church. She speaks in
half-riddles and means every word of them. The custodian of tradition.
- *"The spirits of the valley reveal their secrets only to those who walk to them. So walk."*
- *"This qvevri held wine when your great-grandfather's grandfather was a glekhi like you."*
- *"Pay your respects, and the old wine will teach you patience the young wine cannot."*

### Levan the Enologist — KARTLI (Gori) · the analyst, quality & sparkling quests
A university-trained enologist who came home to Kartli to prove science and tradition need not
quarrel. Precise, dry-humoured, allied to the high-acid sparkling style. The Enologist career mentor.
- *"Tradition is a hypothesis that survived a thousand vintages. I merely check its arithmetic."*
- *"Chinuri's acid is not a flaw to fix — it is a bubble waiting to be born. Trust the numbers."*
- *"A faulted must is not a tragedy. It is a problem, and problems, unlike spirits, can be solved."*

### Maro of the Wet Hills — IMERETI (Kutaisi) · the mildew-fighter, hard-region quests
A weathered Imeretian grower who has lost vintages to mildew and won them back, and will teach you
how at the price of your sweat. Blunt, generous, scarred by rot.
- *"In Kakheti the sun does your spraying for you. Here, the rot is patient. So are we."*
- *"Tsolikouri is born in this soil. Coddle it, spray it, and it will pay you back in gold."*
- *"You skipped a spray to save eight lari? Enjoy your eight lari. You'll lose eighty in grapes."*

### Davitashvili the Highlander — RACHA-LECHKHUMI (Ambrolauri) · the prestige grower
A proud, sparing Rachan who makes a little semi-sweet Aleksandrouli and sells it for the price of a
lot. Speaks slowly, charges fast. Master of the scarce-and-precious play.
- *"Up here the vine fights for every drop of sugar. That fight is what you taste. That fight is the price."*
- *"I make ten qvevri a year. Ten. The eleventh buyer can wait until next autumn, or beg."*
- *"Khvanchkara cannot be hurried and cannot be copied. The mountain sees to that."*

### Beso of the Swamp-Vines — SAMEGRELO (Zugdidi) · the disease-gauntlet survivor
A Megrelian grower with a gambler's nerve, who farms Ojaleshi in the worst disease pressure in the
country and laughs about it. Fast-talking, fatalistic, oddly cheerful.
- *"Rot, mildew, rain — Samegrelo throws it all. And Ojaleshi? Ojaleshi grows here and nowhere else."*
- *"Half my crop dies and the other half is worth double. The arithmetic loves me, my nerves less so."*
- *"You want a safe vineyard? Go to Kakheti. You want a wine no one else has? Sweat with me here."*

### Eka of the Coast — GURIA & ADJARA (Batumi) · the seaside vintner & tourism figure
A coastal Gurian who pairs light Chkhaveri rosé with the Black Sea tourist trade and never seems to
hurry. Sunny, shrewd about hospitality, queen of the tourism upside.
- *"Inland they make wine to age in the dark. We make wine to drink at the shore, today, laughing."*
- *"Chkhaveri is light as the sea breeze. The tourists drink it by the carafe — and pay by the carafe."*
- *"Bring the visitors and the wine sells itself. The coast is my cellar door, child."*

### Vakhtang the Pioneer — MESKHETI (Akhaltsikhe) · the frontier revivalist
A stubborn idealist reviving the ancient qvevri tradition in the cold high south, where the old wineries
are ruins and the competitors are ghosts. Visionary, weather-beaten, contrarian.
- *"They say Meskheti is too cold, too high, too forgotten. They said that of the qvevri once, too."*
- *"I dig my jars where monks dug theirs eight centuries ago. The terroir remembers. I only remind it."*
- *"Be first or be forgotten. Up here there is no crowded market — only the brave and the empty hills."*

### Sandro the Négociant — KAKHETI / travelling · the bulk buyer & broker NPC
A travelling négociant with a ledger for a heart and an eye for an underpriced lot, working every
region's bazaar in turn. Smooth, transactional, never sentimental about wine. The trading-career mentor.
- *"I don't make wine. I make markets. Show me your barrels and I'll show you a number."*
- *"Bottle it under my label and we both eat. Pride doesn't pay the hauler, friend."*
- *"Everyone in this valley sells. The trick — the only trick — is buying. Let me teach you."*
