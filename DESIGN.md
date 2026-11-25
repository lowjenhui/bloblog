# Blob-log: visual design

The source of truth for colours, type, spacing and components. Screen text and behaviour live in [SPEC.md](SPEC.md). Mockups are in [docs/mockups/](docs/mockups/).

The tone is **fun, not loud**: white or dark surfaces, colours taken from the blob and the icon, the icon's soft gradient used once (Colourful only), and blob stickers only in moments that are empty or need attention. Values are in dp on Android (px in the mockups).

## Themes

The user picks one theme in **Settings → Appearance**. The choice is saved on the phone. It doesn't follow the phone's dark mode.

| Theme | Mood | Default |
|---|---|---|
| **Colourful** | Blob aqua, heart pink and the icon's soft gradient. Bright fills carry dark text. | ✓ |
| **Calm** | White, modern cool-neutral greys, and the blob's aqua and pink at their softest. No gradient. | |
| **Dark** | Plum-black background, soft aqua and pink, light text. Floating things (toast, sheet, dialog) sit on a raised dark surface. | |

Stickers, type, spacing and components are the same in every theme. Only the colour tokens change.

## Colour tokens

Every theme defines every token. No colour outside this table: add a token here first.

| Token | Colourful | Calm | Dark | Use |
|---|---|---|---|---|
| `background` | `#ffffff` | `#ffffff` | `#16131d` | Page |
| `text` | `#3d2f55` | `#1f2430` | `#f1eef7` | Body text |
| `text-muted` | `#6b6680` | `#5f6573` | `#a9a3b8` | Captions, times, axis labels |
| `divider` | `#eceaf2` | `#eceef1` | `#2f2a3a` | Row dividers |
| `gridline` | `#e3e7f5` | `#eceef2` | `#2f2a3a` | Graph gridlines |
| `surface-grey` | `#f1f0f4` | `#f2f3f5` | `#221e2b` | Unselected chips, range track |
| `on-grey` | `#4b465c` | `#3f4452` | `#d9d4e3` | Text on `surface-grey` |
| `select` / `on-select` | `#3fe3f2` / `#3d2f55` | `#c4ecf0` / `#1f2430` | `#5fd8e8` / `#16131d` | Selected range pill, month chip, `+ Add person` |
| `person-selected` / text | soft `gradient` / `#3d2f55` | `#e3f5f7` / `#1f2430` | `#25414a` / `#f1eef7` | Selected person chip |
| `owner-line` | `#0091c2` | `#3b9aa8` | `#5fd8e8` | The owner's graph line |
| `owner-dot` | `#7fdcef` | `#b5dde3` | `#2f8f9c` | The owner's weigh-in dots |
| `owner-text` / `owner-tint` | `#006f94` / `#dcfaff` | `#2f7a86` / `#e3f5f7` | `#7fe3f0` / `#1d3a40` | The owner's name, and their avatar letters on its tint |
| `second-line` | `#fc568c` | `#d4789a` | `#ff8fb3` | The second person's graph line |
| `second-dot` | `#ffb3cd` | `#ecc0d0` | `#a3576f` | The second person's dots |
| `second-text` / `second-tint` | `#c8185c` / `#ffeef4` | `#a84d6e` / `#f9eaf0` | `#ffa3c1` / `#3a2230` | The second person's name, and their avatar letters on its tint |
| `notice` | `#fff3dc` | `#f7f1e6` | `#3a2f1f` | Top notice strip, and the background of a "Whose?" row |
| `warn-text` | `#7a4b00` | `#7a5420` | `#ffd58a` | "Whose?", "not sent yet", the strip's `›` |
| `danger` / text | `#c42a62` / white | `#c42a62` / white | `#ff8fb3` / `#16131d` | Delete button in the confirmation dialog only |
| `toast` / text | `#3d2f55` / white | `#1f2430` / white | `#2c2738` + 1 px `#3a3448` border / `#f1eef7` | The "Saved" toast (Dark: a raised surface, not inverted) |
| `scrim` | `#3d2f5566` | `#1f243066` | `#00000099` | Behind sheets and dialogs |
| `gradient` | `#fff0d9 → #fbd6f7 → #cdf9fb`, 120° | n/a | n/a | Colourful's selected person chip only |

**Contrast** (WCAG; text ≥ 4.5:1, graph lines ≥ 3:1). Every text pair passes in all three themes, checked by measuring the rendered mockups. The weakest text is the muted time on a `notice` row: 4.98 (Colourful), 4.94 (Calm), 5.37 (Dark). The weakest lines are Calm `second-line` at 3.0:1 and `owner-line` at 3.3:1, and Colourful `second-line` at 3.1:1.

Rules:
- Bright fills carry dark text (`on-select`). White text appears only on `danger` (light themes) and the light themes' toast.
- Each person keeps one colour everywhere: avatar, their own graph, the Compare line and the Compare summary.
- Dark mode floats things on raised surfaces (`toast`, sheets and dialogs on `surface-grey` or lighter); it never flashes a light surface.

## Type

One family, the system font (Roboto on Android).

| Size | Weight | Use |
|---|---|---|
| 28 | Bold | Headline weight |
| 16 | Bold | Section and screen titles ("History", "People", dialog titles) |
| 14 | Regular | Body text, rows, banner, person chips, buttons, the Compare summary |
| 14 | Bold | The weight value in a row |
| 12 | Regular | Captions, range pills, month chips, axis labels, legend |
| 12 | Bold | Avatar initials, Compare line labels |
| 18 | n/a | Icon glyphs only (⚙, ←) |

Selected chips and pills are **not bold**: the fill alone marks the selection.

## Spacing

4-dp grid. Allowed values: 4, 8, 12, 16, 24, 32.

| Where | Space |
|---|---|
| Screen edge (left, right, top) | 16 |
| Between stacked blocks (top strip → person chips; title → intro → list → button → note) | 12–16 (strip → chips is 16) |
| Around the headline | 24 above (from the chips), 16 below (to the range pills) |
| Between a label and its control (pills → graph, History → month chips → list) | 8 |
| Between sections (graph legend → History) | 32 |
| Graph and legend inset from the screen edge, inside the 16 edge | 12 left and right (the pills stay full width) |
| Row padding, one-line rows (History) | 12 top and bottom |
| Row padding, multi-line rows (People) | 16 top and bottom |
| Inside dialogs | 24 |
| Bottom sheet | 16 sides, 24 bottom |
| Gap between icon or avatar and its text | 8 (small), 12 (avatars, stickers ≥ 40) |

Intentional exceptions: the "Whose?" row sits **−1** above its slot so it covers the divider above it, and the top strip and highlighted rows ignore the 16 screen edge (full bleed).

## Components

- **Solo mode (Just me):** no person chips and no Compare. ⚙ (40 × 40, `surface-grey`) sits at the right end of the pinned headline.
- **Segmented control** ("Who weighs in?"): a full-width `surface-grey` track with no inner padding, two 40-tall segments with 14 text; the selected one is `select` / `on-select`, like the range pills.
- **Settings** (from ⚙): a `Settings` title, then a **People** section (as before) and an **Appearance** section: three radio rows, `Colourful` / `Calm` / `Dark`, each with a one-line description and three 16 dp colour dots ringed with a 1 dp `#8a8599` outline, so they show on any background.

- **Person chips:** 40 tall, fully rounded and equal width, followed by a 40 × 40 round ⚙. Selected uses the gradient with `text`; unselected uses `surface-grey` / `on-grey`. The third chip is **Compare**.
- **Range pills:** a full-width `surface-grey` track with no inner padding. Each segment is 40 tall, and the selected one is `select` with `text`. Compare has no Day.
- **Month chips:** 40 tall with 16 side padding. Only months with weigh-ins are shown, newest on the right. The first chip and every January chip show the year ("Aug 2026", "Jan 2027").
- **Headline:** the 28 bold weight, then a two-line 12 label (person's name, then "today · 7-day avg …"). It pins to the top while scrolling and **only gets a shadow while pinned**. While you press and slide the graph, it shows the point under your finger.
- **Graph:** dots are `owner-dot` (r 3), the 7-day average is `owner-line` (2.5 stroke). On Compare, the second person's line is `second-line`. The axis fits the data: 3 gridlines on half-kilo values, with "kg" on the top label. Year view labels months. Pressing and sliding shows a dashed guide and a ring. There's no floating bubble.
- **Top notice strip:** above the person chips, flush with the top and side edges of the screen, square corners, `notice` background, 8 top/bottom and 16 side padding, a sticker at 28, one line of 14 text, and `›` in `warn-text`. One notice at a time (priority in the design doc). It's used for "Whose?" and for Bluetooth and Health Connect problems, each with its own sticker. No border, no rounding: it sits outside the content, so it never reads as another pill.
- **Lists:** row text is inset 16 from the screen edge, and so are the dividers, on both sides. A `divider` goes under each row **except the last** (History, People, the bottom sheet). A highlighted row (a "Whose?" weigh-in) has a full-bleed `notice` background, edge to edge, with square corners. It sits 1 higher so it covers the divider above it, and its text stays at 16.
- **Toast:** a `text`-coloured background with white text, 16 radius, and the scale-blob sticker at 36.
- **Dialogs:** 24 radius and 24 padding. The confirm button is a filled pill. The Delete button uses `danger`. "Delete weigh-in" in the row sheet is plain `text` with the bin icon, so it isn't mistaken for the second person's pink.

## Stickers

Cut from the blob sticker sheet. They only appear when something is empty or needs attention, never on every row:

| Sticker | Where |
|---|---|
| question | the "Whose?" banner and row |
| peek | an empty month |
| scale | first run, and the "Saved" toast |
| wave | setup, and the People intro |
| bluetooth | Bluetooth off |
| heart | Health Connect not connected |
| sleep | listening failed |
| search | spare |

## Decided layout options kept for reference

- Chart → History break: **A, 32 of space** (chosen). B (hairline) and C (grey band) are in [docs/mockups/2026-11-25-chart-history-break-options.html](docs/mockups/2026-11-25-chart-history-break-options.html).
