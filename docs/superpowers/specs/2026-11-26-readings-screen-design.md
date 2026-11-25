# Readings screen redesign: trend graph, history, two people

Date: 2026-11-26. Status: reviewed by a builder check. Waiting for the owner's approval.
Look and feel: [DESIGN.md](../../../DESIGN.md) (tokens, type, spacing, components). Agreed mockups: `.superpowers/brainstorm/…/ux-fixes-v15.html` (not committed). The saved option set is in [docs/mockups/](../../mockups/).

## 1. Intent

The owner wants to **see their raw weigh-ins** and how they trend. Fitbit only shows the last few days, not a list of weights. The owner also wants to see **their partner's weigh-ins and trend in Blob-log**, while their own Health Connect keeps only their own data. Later, the owner wants to tag weigh-ins with eating and behaviour patterns and look for trends, so Blob-log's own database stays the record.

Success: open the app and see today's weight at a glance, scroll a clean list of every weigh-in by month, read the trend for Day / Week / Month / 3 months / Year, compare both people on one graph, and fix or delete junk (luggage, guests) safely. It should feel **fun, not loud**.

## 2. Goals, non-goals, anti-goals

**Goals**
- A person view per person: a pinned headline weight, a graph with five ranges, and History filtered by month.
- A Compare view: both people's change on one graph, plus a two-line prose summary.
- A Settings screen: People ("Who weighs in?" Just me / Two people; add the second person, rename, edit starting weight) and Appearance (Colourful, Calm or Dark).
- A solo mode for people who use the scale alone: no person chips, no Compare.
- Junk protection: hold back suspicious jumps, confirm before deleting, and remove junk from Health Connect too.
- Live updates while the app is open.

**Non-goals (this round):** tagging and notes, goal weights, Apple Health or any export, removing a person, charts in notifications, a home-screen widget.

**Anti-goals:** a loud or cluttered screen; stickers on every row; any reading other than the owner's reaching Health Connect; data leaving the phone; a design that makes adding tags later hard (the database stays the source of truth).

## 3. Constraints

- Sideloaded Android 12+ app. Kotlin, Views (not Compose), platform SQLite, no chart library (PRD D-9). Free, offline, no new permissions.
- **Allowed new View libraries (planner's call):** `androidx.recyclerview` (History list and swipe-to-delete via `ItemTouchHelper`) and Material Components (`BottomSheetDialog` for the row sheet). Both are View-based and save several hundred hand-written lines. The app theme moves to a Material light theme (`Theme.Material3.Light.NoActionBar`), with colours from DESIGN.md. No other new dependencies.
- Only the owner (`is_me`) is ever written to Health Connect (SPEC rule 4, unchanged).
- **Three themes**, chosen in Settings: Colourful (default), Calm, Dark. All tokens are in DESIGN.md. The app applies the chosen theme itself and ignores the phone's dark mode. The choice is saved in `SharedPreferences` and applied before the first screen draws, so there's no flash of the wrong theme.
- Existing data must survive. No schema migration is needed (see §8).
- Visual values come from DESIGN.md only. Add any new value there first.

## 4. Approaches considered

| | Approach | Trade-off |
|---|---|---|
| **A (chosen, planner's call)** | Keep plain Views and draw the graph in one custom `View` on `Canvas` | Fits D-9 and the small app. Full control over press-and-slide, half-kilo axes and the 7-day line. About 200–300 lines for the chart. |
| B | Rewrite the UI in Jetpack Compose with a chart library (e.g. Vico) | A nicer UI toolkit, but it reverses D-9, rewrites working screens, and pulls in a large dependency for one chart. |
| C | Views plus MPAndroidChart | Less chart code, but the library is no longer actively maintained, and it would need fighting to get the agreed look (pinned headline readout, no bubble, round half-kilo axis). |

The owner described outcomes, not tooling, so A is the planner's call. It reverses PRD **D-8** ("one screen, no charts"), because the owner now wants raw data and trends in Blob-log itself.

## 5. Screens

All text below is exact. "Julia" and "Sam Lee" stand for the people's names, and the weights are examples.

### 5.1 Home: person view (first screen)

Top to bottom, scrolling as one page:

1. **Top notice strip** (only when needed), above everything, edge to edge (DESIGN.md "Top notice strip"). One at a time, in this priority:
   1. Bluetooth permission missing: `Blob-log listens for your scale in the background.` with the button `Allow Bluetooth` (bluetooth sticker)
   2. Bluetooth off: `Bluetooth is off. Turn it on, then reopen Blob-log.` (bluetooth sticker)
   3. Listening failed: `Couldn't start listening. Reopen Blob-log to try again.` (sleep sticker)
   4. Unassigned weigh-ins, on every tab (question sticker):
      - one: `Whose is 81.20 kg on Mon 23 Nov? ›`
      - several: `2 weigh-ins need a name ›`

      Tapping opens the row sheet (§5.4) for the newest unassigned weigh-in.
   5. Health Connect not connected, on the owner's tab only: `Health Connect isn't connected. Readings are saved here but not sent.` with the button `Connect` (heart sticker).

   If Health Connect isn't available on the phone at all, nothing shows here. Instead, the owner's row in Settings → People says `Health Connect isn't available on this phone` in place of `Sent to Health Connect`, so a permanent condition never blocks the "Whose?" notice.
2. **Person chips:** `Julia` · `Sam Lee` · `Compare`, then ⚙ (opens Settings). The selected chip uses the gradient. Chips follow the order people were added; the owner is first. 16 below the strip.
3. **Headline**, 24 below the chips and 16 above the range pills. It pins to the top while scrolling, with a shadow only while pinned:
   - Weight: the person's latest weigh-in, e.g. `65.70 kg`
   - Label line 1: the person's name
   - Label line 2: `today · 7-day avg 65.58` if the latest weigh-in is from today, otherwise `Wed 25 Nov · 7-day avg 65.58`
   - No weigh-ins yet: weight `–`, line 2 `No weigh-ins yet`
   - While pressing and sliding the graph, it shows the point under the finger: weight in `owner-text`, line 2 `Thu 19 Nov · 07:38`. On release it reverts.
4. **Range pills:** `Day` `Week` `Month` `3M` `Year`. Default `Month`. The choice is remembered per app session (not saved).
5. **Graph** (§6), then the legend: `● weigh-ins  ━ 7-day average · press and slide: the number above follows your finger`. On Day: `● weigh-ins · press and slide: the number above follows your finger`.
6. 32 of space.
7. **History:** the title `History`, with the count on the right, e.g. `19 weigh-ins in Nov` (`1 weigh-in in Nov` for one).
8. **Month chips:** months that have weigh-ins for this person, or unassigned ones. Newest on the right and scrolled into view on open. The first chip and every January chip show the year. Default: the newest month.
9. **Rows** for the selected month, newest first:
   - Assigned row: `Wed 25 Nov · 07:15`, with `65.70 kg` on the right. The owner's rows not yet in Health Connect add `· not sent yet` in `warn-text`.
   - Unassigned row (shown on every tab, in its month): a full-bleed `notice` background with square corners, the question sticker, `Mon 23 Nov · 09:12`, and `81.20 kg · Whose?`. It sits over the divider above it.
   - No line under the last row.
   - Tap a row → row sheet (§5.4). Swipe a row left → delete confirmation (§5.5).
   - Empty month: can't happen, since only months with data get chips. A person with no weigh-ins at all: peek sticker plus `No weigh-ins yet. Step on the scale.`

### 5.2 Compare view

- The top strip and chips as in 5.1, with `Compare` selected. The same notice rules apply, except "Health Connect not connected" doesn't show here.
- Caption: `Change since 27 Oct · 7-day averages` (the date is the start of the range).
- Range pills: `Week` `Month` `3M` `Year`. There's no Day. If Day was selected on a person tab, Compare opens on `Week`.
- **Graph:** one line per person, with one point per day. A day's value is the person's 7-day average at the end of that day (the mean of their weigh-ins in the 7 days ending then). If they have no weigh-ins in those 7 days, that day is a gap and the line breaks. The **baseline** is the same 7-day average at the start of the range, using weigh-ins from before the window. If there's none, it's the first day in the range that has a value. Each point is plotted as its value minus the baseline. A dashed zero line. Each line is labelled with the name only, at its right end, in the person's colour with a white halo. The labels hide while sliding.
- **Summary** under the graph, one line per person, 14 regular, name bold in their colour:
  - resting: `Today`, then `Julia is at 65.58 kg, down 0.45 kg.` (`up` / `down`; `no change` when |change| < 0.05)
  - sliding: the date (`Thu 19 Nov`), then `Julia was at 65.65 kg, down 0.38 kg.`
  - a person with no weigh-ins in the range: `Sam Lee has no weigh-ins in this range.` (no line drawn)
- No headline, no History.

### 5.3 Settings (from ⚙): People and Appearance

The title is `Settings`. Two sections, **People** then **Appearance**, with 32 between them.

**People**

- The wave sticker beside: `Who steps on the scale. Weigh-ins are matched to the closest person.`
- One row per person (16 top and bottom, no line under the last):
  - two-letter avatar in their colour (first letters of the first two words, or the first two letters of a one-word name, upper case)
  - the name, with ` (Me)` in muted text for the owner
  - `Sent to Health Connect` for the owner, `Saved in Blob-log only` for others
  - `Started at 66.30 kg`
  - `›`
- Button: `+ Add person`, hidden once there are two people (two is the limit for now).

**Appearance**
- Three radio rows, one selected:
  - `Colourful`: `Blob colours and a soft gradient`
  - `Calm`: `Neutral greys with soft blob colours`
  - `Dark`: `Easy on the eyes at night`
- Each row shows three colour dots for that theme.
- Choosing a row applies the theme straight away (the screen redraws in the new theme) and saves it.
- Note: `Each person's colour is also their line on the Compare tab. Only the person marked (Me) is sent to Health Connect.`
- **Tap a person** → an edit dialog with fields `Name` and `Starting weight (kg)`, and the buttons `Cancel` / `Save`. The same validation as setup: `Enter a name.` and `Enter a weight between 20 and 250 kg.` (Me) can't be changed (§9).
- **Add person** → the same dialog, titled `Add person`. After saving, Blob-log assigns any unassigned weigh-ins it can to the new person (§7.3), then shows a toast: `Matched 6 earlier weigh-ins to Sam Lee` (no toast if 0).

### 5.4 Row sheet (tap a row, or tap the banner)

- A drag handle at the top. Header: the sticker (question for unassigned), the weight (16 bold), and `Mon 23 Nov · 09:12 · not assigned`, or the person's name if assigned.
- `Move to`, then one row per person **other than the current owner of the reading** (avatar and name, 48 tall, no line under the last).
- 12 of space, then `🗑 Delete weigh-in` in plain `text` (not pink, so it isn't confused with the second person's colour).
- Moving: the same as SPEC rule 4. Moving to the owner writes to Health Connect; moving away from the owner deletes the Health Connect record first. If that delete fails, the toast `Couldn't update Health Connect. Try again.` shows and nothing changes.

### 5.5 Delete confirmation

A dialog titled `Delete this weigh-in?`, with the body `81.20 kg on Mon 23 Nov at 09:12. If it was sent to Health Connect, it's removed there too. This can't be undone.` and the buttons `Cancel` / `Delete` (`danger` fill, white text). Deleting removes the Health Connect record first if it was written; if that fails, the same toast shows and the row stays.

### 5.6 Toasts

- A new weigh-in saved while the app is open and assigned to a person: scale sticker plus `Saved 65.70 kg for Julia`, about 3 s. No toast for unassigned ones; the banner appears instead.
- `Matched N earlier weigh-ins to Sam Lee` (§5.3).
- `Couldn't update Health Connect. Try again.`

### 5.7 Setup (first launch, existing)

The existing dialog gets the wave sticker and follows DESIGN.md. Its behaviour is unchanged: it creates the owner.

### 5.8 Modes: Just me / Two people

The app is public, so most people use it alone. It has two modes:

- **Setting:** `Who weighs in?` with a segmented control `Just me` | `Two people` at the top of Settings → People. It's saved on the phone (`SharedPreferences` `"settings"`, key `"people_mode"`).
- **Default:** a new install is `Just me` (setup is unchanged: one dialog, name and weight). An existing install that already has two people starts as `Two people`.

**Just me**
- Home has no person chips and no Compare. ⚙ moves to the right end of the pinned headline (40 × 40, `surface-grey`), so it's reachable even after scrolling. Below the top notice strip, the headline is first, 16 below the strip (or 16 from the top of the screen when there's no strip).
- The headline label line 1 still shows the owner's name.
- Assignment (§7.1) considers **only the owner**. A hidden second person never claims weigh-ins.
- Row sheet for an unassigned weigh-in: `This is me` (owner avatar), `🗑 Delete weigh-in`, then a quiet link row `Someone else uses this scale? Add them` that opens the Add person dialog. Saving it switches to `Two people` and runs the backfill (§7.3).
- Settings note under the people list: `Weigh-ins far from your weight are held back and ask “Whose?”, so guests and bags never reach Health Connect.`

**Two people**
- Everything else in §5 applies as written.
- Settings note: `Weigh-ins are matched to the closest person. Only the person marked (Me) is sent to Health Connect.`

**Switching**
- `Just me` → `Two people` with no second person: the Add person dialog opens. `Cancel` leaves the mode on `Just me`.
- `Just me` → `Two people` with a hidden second person: they reappear with all their data. The backfill (§7.3) runs for them, so weigh-ins held as "Whose?" while they were hidden can be matched.
- `Two people` → `Just me`: the second person and their data are **kept but hidden**: no tab, no Compare, not considered by assignment. New weigh-ins near their weight are held as "Whose?". If Compare or their tab was open, Home switches to the owner's tab.

## 6. Graph rules

- **Ranges** are rolling windows ending now: Day = today since midnight; Week = 7 days; Month = 30 days; 3M = 90 days; Year = 365 days.
- **Dots:** every assigned weigh-in of the person in the range.
- **7-day average line** (all ranges except Day): at each weigh-in time `t`, the mean of that person's weigh-ins in `(t − 7 days, t]`. The line breaks where two consecutive weigh-ins are more than 7 days apart. On Year, dots are drawn smaller (r 2) so the line stays readable.
- **Y axis:** `lo` = the half-kilo at or below (min − 0.3); `step` = the smallest half-kilo ≥ ((max + 0.3) − lo) / 2, at least 0.5; gridlines at lo, lo + step and lo + 2·step, labelled with one decimal, with ` kg` on the top label only. Compare uses the same rule on the changes (all values and 0), with `+`/`−` signs.
- **X axis:** Day, Week, Month and 3M get three labels (start, middle and end). Day: `00:00`, `12:00`, `now`. Week, Month, 3M: dates like `27 Oct`. Year gets five month labels, one per quarter plus the end (e.g. `Dec`, `Mar`, `Jun`, `Sep`, `Nov`).
- **Press and slide:** the readout snaps to the nearest weigh-in by time (person view) or the nearest day (Compare), with a dashed vertical guide and a ring on the line. The page doesn't scroll while sliding.
- **Empty range:** draw no line and show the axis with the text `No weigh-ins in this range` centred.
- **Accessibility:** the graph has a content description, e.g. `Weight trend, month. 19 weigh-ins from 65.0 to 66.8 kg. 7-day average now 65.58 kg.` Every tappable element is at least 48 dp. All text colours meet the contrast figures in DESIGN.md.

## 7. Behaviour changes

### 7.1 Who's who (SPEC rule 3, changed)

- A person's **reference weight** is now their 7-day average at the moment of the reading. If they have no weigh-ins in the last 7 days, it's their latest assigned weigh-in; if they have none, their starting weight.
- Auto-assign to the nearest person when `d1 ≤ 2 kg` (was 3) **and** `d2 − d1 ≥ 2 kg`. Otherwise the reading is unassigned and notified (unchanged).
- Why: it catches junk such as luggage (5–20 kg bags) and guests. Known limit: someone holding a bag that brings them within 2 kg of the other person's average is still auto-assigned to that person. Fix it in the row sheet.

### 7.2 Live updates

When a reading is saved while Home is open, the screen updates without reopening: the headline, graph, History, banner and toast. The planner leaves the mechanism to the builder (for example a broadcast from `ScanReceiver` to the open activity, or the activity re-reading on a database change signal).

### 7.3 Adding the second person: backfill

After `Add person` is saved, go through unassigned readings from oldest to newest and apply §7.1 to each, with reference weights updated as readings get assigned. **Only assignments to the new person are kept**; a reading whose best match is someone else stays unassigned for the owner to tap (owner's decision, 2026-11-26: nothing reaches Health Connect without the owner choosing it). Matched readings drop out of the banner. No Health Connect writes happen, because the new person is never "me".

### 7.4 Unchanged

Capture, the duplicate check, notifications (unassigned only), the Health Connect rules, and storage are all unchanged.

## 8. Assets

The 12 sticker cut-outs (transparent PNGs) are saved in `docs/stickers/blob-<name>.png`, and the source sheet in `docs/stickers-source.webp`. The build copies them to `app/src/main/res/drawable-nodpi/blob_<name>.png` (Android resource names use underscores).

## 8b. Data

No schema change:
- **Person colour** comes from the order people were added. The first person (always the owner, from setup) gets the owner colours (`owner-*`, blob aqua and ocean); the second gets the `second-*` colours (heart pink). On each person's own tab, the graph uses that person's colours.
- **Month chips, counts, the 7-day average and Compare values** are all computed from `reading` and `person`.
- **New `Db` functions** the builder will need: readings for a person in a time range; distinct months with readings for a person, plus unassigned; unassigned readings oldest-first; update a person's name and starting weight.

## 9. Decisions made here (planner's calls, owner can override)

1. Chart drawn by hand on `Canvas` (approach A).
2. Rolling windows for the ranges, not calendar periods.
3. Two people maximum for now. `+ Add person` hides at two.
4. The owner ("Me") can't be changed in-app. Changing it would mean moving Health Connect ownership of past readings, which is out of scope.
5. Range choice isn't saved between app launches; the app always opens on Month.
6. No toast for unassigned readings; the banner and notification cover them.
7. RecyclerView and Material Components are allowed (§3). This narrows D-9 to "Views, platform SQLite, no Compose, no Room, no chart library".
8. The "Whose?" notice ranks above "Health Connect not connected". "Health Connect isn't available" is shown in Settings → People, not as a banner.
9. The Compare baseline is each person's 7-day average at the range start, with daily points and gaps (§5.2).
10. Year gets five month labels on the x axis.
12. Notices are an edge-to-edge cream strip above the person chips, and "Whose?" History rows are a full-bleed cream band (owner's decision, 2026-11-26).
14. Modes (owner's decision, 2026-11-26): `Just me` / `Two people` in Settings → People. Just me is the default for new installs. Switching back to Just me hides the second person but keeps their data. Setup doesn't ask; the "Whose?" sheet offers `Someone else uses this scale? Add them`.
13. Themes (owner's decision, 2026-11-26): one list, Colourful | Calm | Dark, with Colourful the default. It lives in Settings, which replaces the People screen. Calm is the softest neutral variant (white, cool greys, pale blob aqua and dusty pink). Dark floats the toast on a raised surface. This reverses the earlier "light only".
11. Colours (owner's decision, 2026-11-26): blob aqua selections with plum text, ocean line for the owner, the icon's heart pink for the second person, the soft gradient on the selected person chip. See DESIGN.md.

## 10. Testing (for the plan)

- **Unit tests:** the 7-day average (including gaps > 7 days); axis range (the cases 65.0–66.8, 65.4–65.9, 77.6–79.4, 60–70 → 64.5/66.0/67.5, 65.0/66.0/67.0, 77.0/78.5/80.0, 59.5/65.0/70.5); Compare change and its baseline; the new assignment rule (2 kg, 7-day reference, fallbacks, luggage example); month chip labels across a year end; the avatar initials rule; the summary wording (up/down/no change/no data).
- **Robolectric `Db` tests:** readings by person and range, distinct months, backfill.
- **On the owner's phone:** scrolling with the pinned headline; press-and-slide; swipe-to-delete; a live toast while the app is open; adding Sam Lee with backfill; that Health Connect never receives Sam Lee's data.

## 11. Docs to update with the build

- **SPEC.md:** rule 3 (§7.1), the screens (§5), and the new messages.
- **PRD.md:** D-8 reversed (new D-12 with this design's reason), new stories with ids for each §5 screen, and backlog items for tagging and goals.
- **DESIGN.md:** any new value.
