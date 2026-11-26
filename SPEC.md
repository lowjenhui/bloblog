# Blob-log — Spec (milestone 1)

Source of truth for behaviour. PRD.md holds goals, stories and decisions.

## What it is

A sideloaded Android app (Kotlin) on the owner's phone. It catches weigh-ins from the Yolanda "feelfit" bathroom scale in the background, decides whether each one is the owner's or their partner's, stores every reading in its own database, and writes the owner's weight to Health Connect. Nothing goes to the cloud. Fitbit is out of scope.

## Build status (v0.3.1)

Done in v0.3.1: blob moments (empty-range and Day stickers, a peek on the delete dialog, a cheer on a first weigh-in), a CSV download of all weigh-ins in Settings → Data, and year dividers in the History month chips. Done in v0.3: the redesigned Home (person view with trend graph and History, Compare, Settings with People and Appearance, row sheet, delete confirmation, snackbars, live updates), three themes, Just me / Two people modes, and rules 2–7 with the new rule 3. The owner is set up first; the second person is added in Settings, and their readings stay in Blob-log only. Not done: scale pairing, and delivering the second person's data anywhere else (PRD D-11). Still to do: the on-phone checks in the design spec §10 (scrolling with the pinned headline, press-and-slide, swipe-to-delete, a live snackbar, adding Sam Lee with backfill, that Health Connect never receives the second person's data).

## Scale protocol (observed 2026-11-22)

The scale is broadcast-only: it has no name, advertises no services, and is never connected to. Every reading comes from BLE advertisement manufacturer data with company ID `0xFFFF`. The raw capture is in `docs/scale-capture-2026-11-22.txt`.

| Bytes (0-based) | Meaning | Confidence |
|---|---|---|
| 0–1 | `AA BB` header | matches openScale |
| 2–7 | scale ID (`ed 67 39 86 b4 d7` for this scale) | constant across the capture |
| 8–10 | 24-bit little-endian counter, most likely a seconds clock | went from `68 42 e1` to `52 53 e5` between captures about 3 days apart (+265k) |
| 15 | status: `0x02`/`0x03` = measuring, `0x23` = final (bit `0x20` set = locked) | observed |
| 17–18 | weight, little-endian uint16, 0.01 kg (`6d 1a` = 67.65 kg) | matches the scale display |
| 11–14, 16 | constant (`00 ff ff ff`, `10`) | both captures |
| 19–21 | constant `52 59 04`, even with nobody on the scale | both captures, so not a body reading |
| 22–23 | counter that ticks until the weight locks, then freezes | both captures |

No impedance is broadcast, so this scale gives weight only (see Known gaps 1).

## Rules

1. **Capture.** A background BLE scan filtered on manufacturer ID `0xFFFF` and data prefix `AA BB` + the paired scale ID wakes the app. It keeps running after reboot and with the app closed.
2. **One weigh-in = one reading.** Only locked packets (status bit `0x20`) with weight > 0 count. A locked packet whose weight is within 0.1 kg of the last saved reading, and arrives within 120 s of it, is the same weigh-in and is ignored.
3. **Who's who.** A person's *reference weight* is their 7-day average at the moment of the reading. If they have no weigh-ins in the last 7 days, it's their latest assigned reading; if they have none, their starting weight. For reading `r`, let `d1` be its distance to the nearest active person's reference weight and `d2` its distance to the other's.
   - Auto-assign to the nearest person when `d1 ≤ 2 kg` **and** `d2 − d1 ≥ 2 kg`.
   - Otherwise the reading is **unassigned**, and the app posts the notification below.
   - Active people: in Just me only the owner (`d2` is infinite, so only "within 2 kg of the owner's reference weight" applies); in Two people both.
   - Known limit: someone holding a bag that brings them within 2 kg of the other person's average is still auto-assigned to that person. Fix it in the row sheet.
   - After a person is added, unassigned readings are gone through oldest to newest and rule 3 applied with updated reference weights. Only assignments to the new person are kept, nothing is written to Health Connect, and the rest stay unassigned.
4. **Health Connect.** Only readings assigned to the person marked "This is me" are written, as a `WeightRecord` (time = capture time, `clientRecordId` = reading id). Reassigning a reading away from the owner deletes its record. Reassigning one to the owner writes it. Unassigned and partner readings are never written.
5. **Storage.** Every reading is kept in the app database, including the raw advertisement hex, so it can be re-decoded later.
6. **Deleting.** "Delete weigh-in" (after confirmation) removes the reading from the database, and from Health Connect if it was written there.
7. **Units.** Everything is stored and shown in kg, with 2 decimal places.

## Data

No schema change in v0.3.
- `person(id, name, startWeightKg, isMe)`: one or two rows. Exactly one has `isMe = true`. A person's order (and so their colour) comes from the order they were added; the first is the owner.
- `reading(id, takenAt, weightKg, rawHex, personId nullable, writtenToHealthConnect)`. The app reads `writtenToHealthConnect` as `Reading.written`.
- `settings(scaleId)`
- `SharedPreferences` `"settings"`: `"theme"` (Colourful, Calm or Dark; default Colourful) and `"people_mode"` (Just me or Two people).
- Month chips, counts, 7-day averages and Compare values are computed from `reading` and `person`.

## Screens and messages (exact text)

Look and feel: DESIGN.md. Design spec: `docs/superpowers/specs/2026-11-26-readings-screen-design.md`. "Alex" and "Sam Lee" stand for the people's names, and the weights are examples.

### Home: person view (first screen)

Top to bottom, scrolling as one page:

1. **Top notice strip** (only when needed), edge to edge, above everything. One at a time, in this priority:
   1. Bluetooth permission missing: `Blob-log listens for your scale in the background.` with the button `Allow Bluetooth`
   2. Bluetooth off: `Bluetooth is off. Turn it on, then reopen Blob-log.`
   3. Listening failed: `Couldn't start listening. Reopen Blob-log to try again.`
   4. Unassigned weigh-ins, on every tab: one: `Whose is 81.20 kg on Mon 23 Nov? ›`; several: `2 weigh-ins need a name ›`. Tapping opens the row sheet for the newest unassigned weigh-in.
   5. Health Connect not connected, on the owner's tab only: `Health Connect isn't connected. Readings are saved here but not sent.` with the button `Connect`

   If Health Connect isn't available on the phone at all, nothing shows here. The owner's row in Settings → People says `Health Connect isn't available on this phone` in place of `Sent to Health Connect`.
2. **Person chips:** `Alex` · `Sam Lee` · `Compare`, then ⚙ (opens Settings). The selected chip uses the gradient. Chips follow the order people were added; the owner is first.
3. **Headline**, pinned to the top while scrolling, with a shadow only while pinned:
   - Weight: the person's latest weigh-in, e.g. `65.70 kg`
   - Label line 1: the person's name
   - Label line 2: `today · 7-day avg 65.58` if the latest weigh-in is from today, otherwise `Wed 25 Nov · 7-day avg 65.58`
   - No weigh-ins yet: weight `–`, line 2 `No weigh-ins yet`
   - While pressing and sliding the graph, it shows the point under the finger: weight in `owner-text`, line 2 `Thu 19 Nov · 07:38`. On release it reverts.
4. **Range pills:** `Day` `Week` `Month` `3M` `Year`. Default `Month`. The choice is remembered per app session (not saved).
5. **Graph** (Graph rules below), then the legend: `● weigh-ins  ━ 7-day average · press and slide: the number above follows your finger`. On Day: `● weigh-ins · press and slide: the number above follows your finger`.
6. **History:** the title `History`, with the count on the right, e.g. `19 weigh-ins in Nov` (`1 weigh-in in Nov` for one).
7. **Month chips:** months that have weigh-ins for this person, or unassigned ones. Newest on the right and scrolled into view on open. Chips show the month only (`Aug`). Before each year's group of chips is a year label (`2026`), and a hairline divider separates one year from the next. Default: the newest month.
8. **Rows** for the selected month, newest first:
   - Assigned row: `Wed 25 Nov · 07:15`, with `65.70 kg` on the right. The owner's rows not yet in Health Connect add `· not sent yet` in `warn-text`.
   - Unassigned row (shown on every tab, in its month): a full-bleed `notice` background with square corners, the question sticker, `Mon 23 Nov · 09:12`, and `81.20 kg · Whose?`.
   - No line under the last row.
   - Tap a row → row sheet. Swipe a row left → delete confirmation.
   - A person with no weigh-ins at all: peek sticker plus `No weigh-ins yet. Step on the scale.`

### Compare view

- The top strip and chips as on Home, with `Compare` selected. The same notice rules apply, except "Health Connect not connected" doesn't show here.
- Caption: `Change since 27 Oct · 7-day averages` (the date is the start of the range).
- Range pills: `Week` `Month` `3M` `Year`. There's no Day. If Day was selected on a person tab, Compare opens on `Week`.
- Graph: one line per person, with one point per day. A day's value is the person's 7-day average at the end of that day. If they have no weigh-ins in those 7 days, that day is a gap and the line breaks. The baseline is the same 7-day average at the start of the range, using weigh-ins from before the window; if there's none, the first day in the range that has a value. Each point is plotted as its value minus the baseline. A dashed zero line. Each line is labelled with the name only, at its right end, in the person's colour with a white halo. The labels hide while sliding.
- Summary under the graph, one line per person:
  - resting: `Today`, then `Alex is at 65.58 kg, down 0.45 kg.` (`up` / `down`; `no change` when |change| < 0.05)
  - sliding: the date (`Thu 19 Nov`), then `Alex was at 65.65 kg, down 0.38 kg.`
  - a person with no weigh-ins in the range: `Sam Lee has no weigh-ins in this range.` (no line drawn)
  - a person with weigh-ins in the range but none in the 7 days ending on that day: `Sam Lee has no weigh-ins that week.`
- No headline, no History.

### Settings (from ⚙)

The title is `Settings`. Three sections, **People**, **Appearance** then **Data**.

**People**
- `Who weighs in?` with a segmented control `Just me` | `Two people` at the top (see Modes).
- The wave sticker beside: `Who steps on the scale. Weigh-ins are matched to the closest person.`
- One row per person:
  - two-letter avatar in their colour (first letters of the first two words, or the first two letters of a one-word name, upper case)
  - the name, with ` (Me)` in muted text for the owner
  - `Sent to Health Connect` for the owner, `Saved in Blob-log only` for others
  - `Started at 66.30 kg`
  - `›`
- Button: `+ Add person`, hidden once there are two people.
- Note under the people list: in Just me, `Weigh-ins far from your weight are held back and ask “Whose?”, so guests and bags never reach Health Connect.`; in Two people, `Weigh-ins are matched to the closest person. Only the person marked (Me) is sent to Health Connect.`
- Tap a person → an edit dialog with fields `Name` and `Starting weight (kg)`, and the buttons `Cancel` / `Save`. Validation: `Enter a name.` and `Enter a weight between 20 and 250 kg.` The owner (Me) can't be changed.
- Add person → the same dialog, titled `Add person`. After saving, unassigned weigh-ins are matched to the new person (rule 3), then the message `Matched 6 earlier weigh-ins to Sam Lee` shows (none if 0).

**Appearance**
- Three radio rows, one selected, each with three colour dots for that theme:
  - `Colourful`: `Blob colours and a soft gradient`
  - `Calm`: `Neutral greys with soft blob colours`
  - `Dark`: `Easy on the eyes at night`
- Choosing a row applies the theme straight away and saves it.
- Note: `Each person's colour is also their line on the Compare tab. Only the person marked (Me) is sent to Health Connect.`

**Data**
- One row: the notebook sticker, `Download weigh-ins (CSV)`, and the caption `Everyone's weigh-ins, one row each.`
- Tapping it opens the system Save-to picker with the file name `blob-log-<yyyy-MM-dd>.csv` (today's date, e.g. `blob-log-2026-11-26.csv`).
- The file has the header `date,time,weight_kg,person,in_health_connect`, then one row per weigh-in, oldest first, for everyone including hidden and unassigned ones. `date` is `yyyy-MM-dd` and `time` is `HH:mm`, both local. `weight_kg` has 2 decimals. `person` is the name, empty if unassigned. `in_health_connect` is `yes` or `no`. Names with a comma, quote or newline are quoted.
- After saving: the notebook sticker plus `Saved blob-log-2026-11-26.csv`. If it fails: `Couldn't save the file. Try again.`

### Row sheet (tap a row, or tap the notice strip)

- A drag handle at the top. Header: the sticker (question for unassigned), the weight, and `Mon 23 Nov · 09:12 · not assigned`, or the person's name if assigned.
- `Move to`, then one row per person other than the current owner of the reading (avatar and name).
- `🗑 Delete weigh-in` in plain `text`.
- Moving follows rule 4. If Health Connect can't remove the record, the message `Couldn't update Health Connect. Try again.` shows and nothing changes.
- In Just me, an unassigned weigh-in's sheet shows `This is me` (owner avatar), `🗑 Delete weigh-in`, then a link row `Someone else uses this scale? Add them`, which opens the Add person dialog. Saving it switches to Two people and runs the backfill. If a hidden second person already exists, the link switches to Two people and runs the backfill without the dialog.

### Delete confirmation

A dialog with the peek sticker above the title `Delete this weigh-in?`, with the body `81.20 kg on Mon 23 Nov at 09:12. If it was sent to Health Connect, it's removed there too. This can't be undone.` and the buttons `Cancel` / `Delete`. Deleting removes the Health Connect record first if it was written; if that fails, the same message shows and the row stays.

### Messages (snackbars)

- A new weigh-in saved while the app is open and assigned to a person: scale sticker plus `Saved 65.70 kg for Alex`, about 3 s. None for unassigned ones; the notice strip appears instead.
- A person's first ever weigh-in, saved while the app is open: the cheer sticker plus `Your first weigh-in!` for the owner, `Sam Lee's first weigh-in!` for others, instead of `Saved …`.
- `Matched N earlier weigh-ins to Sam Lee`
- `Couldn't update Health Connect. Try again.`
- `Saved blob-log-2026-11-26.csv` (notebook sticker) and `Couldn't save the file. Try again.`, from the CSV download.

### Setup (first launch)

A single non-cancelable dialog for the phone owner, with the wave sticker, following DESIGN.md. Title `Who uses the scale?`, fields `Name` and `Current weight (kg)`, button `Next`, validation `Enter a weight between 20 and 250 kg.` It creates the owner. Setup doesn't ask about modes.

### Modes: Just me / Two people

- Setting: `Who weighs in?` at the top of Settings → People, saved on the phone (`SharedPreferences` `"settings"`, key `"people_mode"`).
- Default: a new install is Just me. An existing install that already has two people starts as Two people.
- **Just me:** Home has no person chips and no Compare. ⚙ sits at the right end of the pinned headline. Headline label line 1 still shows the owner's name. Assignment (rule 3) considers only the owner; a hidden second person never claims weigh-ins.
- **Two people:** everything above applies as written.
- **Switching:** Just me → Two people with no second person opens the Add person dialog; `Cancel` leaves the mode on Just me. With a hidden second person, they reappear with all their data and the backfill runs for them. Two people → Just me keeps the second person and their data but hides them: no tab, no Compare, not considered by assignment. New weigh-ins near their weight are held as "Whose?". If Compare or their tab was open, Home switches to the owner's tab.

### Graph rules

- **Ranges** are rolling windows ending now: Day = today since midnight; Week = 7 days; Month = 30 days; 3M = 90 days; Year = 365 days.
- **Dots:** every assigned weigh-in of the person in the range.
- **7-day average line** (all ranges except Day): at each weigh-in time `t`, the mean of that person's weigh-ins in `(t − 7 days, t]`. The averages use weigh-ins from the 7 days before the range start, but only points inside the range are drawn. The line breaks where two consecutive weigh-ins are more than 7 days apart. On Year, dots are drawn smaller (r 2).
- **Y axis:** `lo` = the half-kilo at or below (min − 0.3); `step` = the smallest half-kilo ≥ ((max + 0.3) − lo) / 2, at least 0.5; gridlines at lo, lo + step and lo + 2·step, labelled with one decimal, with ` kg` on the top label only. Compare uses the same rule on the changes (all values and 0), with `+`/`−` signs.
- **X axis:** Day, Week, Month and 3M get three labels (start, middle, end). Day: `00:00`, `12:00`, `now`. Week, Month, 3M: dates like `27 Oct`. Year gets five month labels, one per quarter plus the end.
- **Press and slide:** the readout snaps to the nearest weigh-in by time (person view) or the nearest day (Compare), with a dashed vertical guide and a ring on the line. The page doesn't scroll while sliding.
- **Empty range:** no line, the axis, and the search sticker above the text `No weigh-ins in this range`, centred. On Day, the run sticker above `No weigh-in yet today` instead.
- **Accessibility:** content description, e.g. `Weight trend, month. 19 weigh-ins from 65.0 to 66.8 kg. 7-day average now 65.58 kg.` Tappable elements are at least 48 dp.

### Other screens

**Find your scale** (not built; interim: the scan filter accepts any `AA BB` scale)
- Title: `Find your scale`
- Body: `Step on your scale now. Keep your phone nearby.`
- While scanning: `Looking for your scale…`
- When found: `Found it — 67.65 kg`, with the button `Use this scale`
- After 60 s with nothing found: `No scale found. Make sure Bluetooth is on and step on the scale again.`, with the button `Try again`

**Permissions**, asked in this order, each with a one-line reason shown before the system prompt:
- Bluetooth: `Blob-log listens for your scale in the background.`
- Notifications: `So you can say whose weigh-in it was when it's unclear.`
- Health Connect (weight, write): `Your weight is saved to Health Connect.`
- v0.3 shows no reason text before the Notifications and Health Connect prompts. The Health Connect notice on Home serves as its reason.
- If Health Connect isn't available: `Health Connect isn't available on this phone.`, with no button.
- Privacy screen (opened from Health Connect's permission page): `Blob-log saves your weight from your scale to Health Connect. It doesn't read any Health Connect data, and nothing leaves your phone.`
- If a write fails, or the permission arrives later, unsent readings of the owner are retried each time the app opens.

**Notification** (unassigned reading only)
- Title: `Whose weigh-in?`
- Body: `67.65 kg at 07:42 — tap to choose.`
- Tapping it opens the row sheet.

## Known gaps

1. **Body fat:** resolved (2026-11-25): not available. In a barefoot weigh-in, held for about 9 s after locking, no byte except the clock changed, and bytes 19–21 are the same with nobody on the scale. This is a weight-only scale.
2. **Background scanning reliability:** worked on the owner's phone on 2026-11-24 (app closed, screen locked, one weigh-in captured). Not yet tested after days of idle Doze, a reboot, or Bluetooth being toggled.
3. **Unknown bytes** 9–14, 16 and 19–23. Byte 16 might be the unit (`0x10` = kg?). It hasn't been tested with the scale set to lb.
4. **Close weights:** resolved for this household. The two people are more than 10 kg apart, so the 2 kg rule separates them easily. In Just me, weigh-ins far from the owner's weight are held as "Whose?".
5. **Partner's data** stays on the owner's phone, now viewable in Blob-log as a second person. Getting it to the partner's Apple Health is deferred (PRD D-11).
6. **Two scales or a neighbour's scale:** the filter on the scale ID ignores other scales. Re-pairing is only possible from Settings.
7. **Bluetooth toggled off and on:** the background scan may stop until Blob-log is reopened or the phone restarts. This is untested.
8. **Android 12+ only** (minSdk 31). Is the owner's phone on Android 12 or later?
9. **Cold-start flash (Dark):** on Android 13+ the system remembers the chosen theme for the starting window. On Android 12 (API 31–32) a Dark user sees a white starting window for a moment on a cold start.
