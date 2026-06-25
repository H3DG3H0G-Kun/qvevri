# QVEVRI — UI Design System (v1)

The single source of truth for the game's look. Every screen is assembled from
the tokens and components defined here, so the style holds across all of them by
construction. Tech: **Unity UI Toolkit** (UXML for structure, USS for style).
Tokens live in `client/Assets/UI/Styles/theme.uss` as USS custom properties
(`--qv-*`); components in `components.uss`; each screen has its own small `.uss`
that only does layout.

## Aesthetic — "Georgian wine cellar"
Deep Saperavi wine-red for primary actions, antique gold for accents and trim,
aged-clay/parchment panels on a dark cellar backdrop. Serif display type for
titles (heritage), clean sans for everything functional (legibility). Warm,
old-world, but not cluttered — generous spacing, thin gold hairlines, restrained
ornament. No flat material look; surfaces feel like clay, wood, and candlelight.

## Color tokens
Base / surfaces:
- `--qv-bg-deep`     #140D10  (screen backdrop, darkest)
- `--qv-bg`          #1C1316  (cellar base)
- `--qv-surface`     #241817  (panels / cards)
- `--qv-surface-2`   #2B1F1C  (raised panel top, inputs hover)
- `--qv-field`       #17100F  (input wells)

Wine (primary action):
- `--qv-wine`        #6D1D27
- `--qv-wine-strong` #8A2633  (gradient top / hover)
- `--qv-wine-border` #A8404B
- `--qv-wine-glow`   rgba(120,25,40,0.50)

Gold (accent / focus / highlights):
- `--qv-gold`        #C39A45
- `--qv-gold-bright` #E6C878
- `--qv-gold-soft`   #D8B25A
- `--qv-gold-dim`    #8A7252  (muted icons)

Clay / borders:
- `--qv-line`        #4A3526  (default hairline border)
- `--qv-line-strong` #5A4230  (panel border)
- `--qv-line-gold`   #6B4F37  (active/focus border)

Text:
- `--qv-text`        #E9DCC3  (primary, warm parchment-white)
- `--qv-text-dim`    #B08D6A  (secondary)
- `--qv-text-mute`   #7D6850  (hints / footnotes)
- `--qv-text-label`  #A98E6B  (field labels, uppercase)
- `--qv-on-gold`     #2A1B18  (text on gold fills)
- `--qv-on-wine`     #F3E6CF  (text on wine fills)

Semantic:
- `--qv-danger`      #E2655A
- `--qv-success`     #7FB069
- `--qv-warning`     #E0A33A

## Typography
Two families. Ship a serif and a sans as font assets under
`client/Assets/UI/Fonts/` and point the tokens at them (until then they fall back
to engine defaults — clearly a TODO, not a blocker).
- Display / serif: titles, the wordmark. Token `--qv-font-serif`.
  Suggested: a warm transitional serif (e.g. "Gelica", "Playfair", or a Georgian
  Mtavruli-friendly face). Used at 27/22/18 px, letter-spacing 2–4 px.
- UI / sans: everything else. Token `--qv-font-sans`.
  Suggested: a humanist sans with good Georgian (mkhedruli) coverage
  (e.g. "BPG Nino", "Noto Sans Georgian"). 14 px body, 11–13 px labels.

Type scale (px): display 27, h1 22, h2 18, h3 16, body 14, label 11–12, caption 11.
Weights: 400 regular, 500 medium (titles + buttons). Labels use uppercase +
1 px tracking. Never below 11 px.

## Spacing & shape
Spacing scale (px): 2, 4, 6, 8, 12, 16, 20, 24, 32, 40. Tokens `--qv-s-1..-10`.
Radius: inputs/buttons 8 (`--qv-r`), cards 12 (`--qv-r-lg`), pills 999.
Borders: 1 px hairlines. Focus = 2 px gold inner glow (box-shadow-like via border
+ a translucent gold outline). Panel elevation via a single soft dark shadow.

## Components (see components.uss)
- `.qv-screen`      full-bleed backdrop + top gold filament bar.
- `.qv-panel`       raised clay card (border, radius-lg, shadow, padding 24).
- `.qv-title` / `.qv-subtitle` / `.qv-eyebrow`  display + secondary + uppercase kicker.
- `.qv-field`       labelled input well (icon + TextField), with `:focus` gold ring.
- `.qv-label`       uppercase field label.
- `.qv-btn`         base button. Variants: `.qv-btn--wine` (primary),
  `.qv-btn--gold` (secondary/confirm), `.qv-btn--ghost` (tertiary/cancel).
- `.qv-tabs` / `.qv-tab` (+ `.is-active`)  segmented toggle (e.g. Sign in / Register).
- `.qv-link`        gold inline text action.
- `.qv-error` / `.qv-success`  inline status line under forms.
- `.qv-divider`     thin clay rule.
- `.qv-badge`       small pill (rank, status, GEL).

States everywhere: `:hover` (lift toward `-strong`/`-bright`), `:active`
(scale 0.98), `:focus` (gold ring), `:disabled` (50% opacity, no pointer).

## Screen inventory (rolled out from these components)
Auth: login, register (done first as the reference). Then: character select,
character create, NPC dialog, bazaar/shop, inventory/equipment, vineyard manage,
profession panel, world/map, market listings. Each is layout-only on top of the
shared theme + components.

## Workflow note
UI renders in Unity, which the assistant can't see — visual polish runs on a
screenshot loop (build → screenshot → iterate), same as the test loop. Font and
any illustrated art are assets the user supplies; the system is designed so they
drop into clearly-marked token/slots without touching screen code.
