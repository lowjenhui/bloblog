# Readings Screen Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Project rule (CLAUDE.md): Sonnet 5.5 (medium) implements each task; Opus reviews each diff and runs the tests, then commits. Implementers do **not** commit.

**Goal:** Replace the single readings list with person tabs, a pinned headline, a trend graph (Day/Week/Month/3M/Year), a month-filtered History, a Compare view, a Settings screen (People + Appearance), three themes, junk protection and live updates.

**Architecture:** Plain Android Views plus Material Components and RecyclerView. Pure-Kotlin logic (`Trend.kt`, `Notice.kt`, `Assign.kt`) holds every rule the spec pins and is unit-tested. One custom `TrendChartView` draws both graph modes on a `Canvas`. Home is a `CoordinatorLayout`: an `AppBarLayout` with the strip and chips (they scroll away) and the headline (pinned, lifting shadow), over a `RecyclerView` holding the graph header, the History header, the month chips and the rows. Themes are three XML styles resolved through custom attributes.

**Tech Stack:** Kotlin 2.2.0, AGP 8.11.1, minSdk 31, compileSdk 36, platform SQLite, `com.google.android.material:material:1.13.0`, `androidx.recyclerview:recyclerview:1.4.0`, JUnit 4, Robolectric 4.18.1 (`@Config(sdk = [35])`).

**Spec:** [docs/superpowers/specs/2026-11-26-readings-screen-design.md](../superpowers/specs/2026-11-26-readings-screen-design.md). Visual tokens: [DESIGN.md](../../DESIGN.md). Read both before every task. **All on-screen text comes verbatim from spec §5.**

## Global Constraints

- Work on branch `feat/readings-screen`. Every shell: `export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home`. Full check: `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug` with 0 lint errors.
- No Compose, Room or chart library. The only new dependencies are Material 1.13.0 and RecyclerView 1.4.0 (spec §3).
- Colours only through the theme attributes defined in Task 1, named after the DESIGN.md tokens. No hex literal in Kotlin or layouts outside `res/values/themes.xml`.
- Type 12/14/16/28 (18 for icon glyphs). Spacing from DESIGN.md "Spacing". Touch targets ≥ 48dp.
- Weights are stored as centi-kg `Int` (existing). Shown with 2 decimals and ` kg`.
- Only the `is_me` person is ever written to Health Connect. Backfill never writes to Health Connect (spec §7.3).
- No schema change. The DB stays at version 2.
- The 2 people maximum and "(Me) can't be changed" from spec §9 are unchanged.

## Review Focus

1. **Local month and day boundaries:** a weigh-in at 23:59 on 31 Oct must land in the Oct chip and in the Oct 31 Compare day, using the phone's time zone, not UTC. Tested in Task 2 (`compareSeriesUsesLocalDays`) and Task 4 (`monthsUseLocalZone`).
2. **People with 0 or 1 weigh-ins:** headline `–` / `No weigh-ins yet`, the graph's `No weigh-ins in this range`, and the axis for a single value. Tested in Task 2 (`axisSingleValue`) and Task 6 (`emptyPersonShowsPlaceholder`).
3. **A wide spread from a luggage weigh-in** (65 → 95 kg) must still give 3 round gridlines. Tested in Task 2 (`axisWideSpread`).
4. **Switching theme recreates the activity:** the selected tab and range must survive. Tested in Task 9 (`themeChangeKeepsTabAndRange`).
5. **The row sheet acting on a reading deleted meanwhile** (by live update or another sheet) must dismiss without crashing or writing to Health Connect. Tested in Task 7 (`actingOnDeletedReadingIsNoOp`).

---

### Task 1: Themes, tokens and preferences

**Files:**
- Modify: `app/build.gradle.kts` (add Material 1.13.0, RecyclerView 1.4.0)
- Create: `app/src/main/res/values/attrs.xml`, `app/src/main/res/values/themes.xml`, `app/src/main/res/drawable/bg_person_selected_colourful.xml` (the 120° gradient `#fff0d9 → #fbd6f7 → #cdf9fb`)
- Create: `app/src/main/java/app/bloblog/Theme.kt`
- Modify: `app/src/main/AndroidManifest.xml` (application theme `@style/Theme.Bloblog.Colourful`)
- Test: `app/src/test/java/app/bloblog/ThemeTest.kt`

**Interfaces:**
- Produces: the attributes `bl_background, bl_text, bl_textMuted, bl_divider, bl_gridline, bl_surfaceGrey, bl_onGrey, bl_select, bl_onSelect, bl_personSelectedBg` (reference: a colour or drawable), `bl_onPersonSelected, bl_ownerLine, bl_ownerDot, bl_ownerText, bl_ownerTint, bl_secondLine, bl_secondDot, bl_secondText, bl_secondTint, bl_notice, bl_warnText, bl_danger, bl_onDanger, bl_toast, bl_onToast, bl_toastStroke, bl_scrim`. Their values come from the DESIGN.md "Colour tokens" table, one style per theme: `Theme.Bloblog.Colourful` and `Theme.Bloblog.Calm` (parent `Theme.Material3.Light.NoActionBar`), `Theme.Bloblog.Dark` (parent `Theme.Material3.Dark.NoActionBar`). Colourful and Calm leave `bl_toastStroke` transparent.
- `enum class AppTheme(val style: Int) { COLOURFUL, CALM, DARK }`
- `object ThemePrefs { fun get(context: Context): AppTheme; fun set(context: Context, theme: AppTheme) }`, using SharedPreferences `"settings"`, key `"theme"`, default `COLOURFUL`
- `fun Activity.applyAppTheme()` calls `setTheme(ThemePrefs.get(this).style)`, and must be called before `super.onCreate`
- `fun Context.token(attr: Int): Int` resolves a colour attribute

- [ ] **Step 1: Write the failing tests** (Robolectric)
  - `defaultIsColourful`: a fresh context gives `ThemePrefs.get == COLOURFUL`
  - `roundTrip`: `set(DARK)`, then `get == DARK`
  - `tokensPerTheme`: for each theme, `ContextThemeWrapper(ctx, theme.style).token(R.attr.bl_text)` equals `0xFF3d2f55`, `0xFF1f2430` and `0xFFf1eef7`; and `bl_select` equals `0xFF3fe3f2`, `0xFFc4ecf0` and `0xFF5fd8e8`
- [ ] **Step 2:** Run `./gradlew :app:testDebugUnitTest --tests app.bloblog.ThemeTest`. Expected: FAIL (unresolved `ThemePrefs`).
- [ ] **Step 3:** Add the dependencies, `attrs.xml`, `themes.xml` (every token in all three styles) and `Theme.kt`. Set the manifest application theme. Remove `Theme.DeviceDefault.DayNight`.
- [ ] **Step 4:** Run the tests. Expected: PASS. Then run the full check: 0 lint errors.
- [ ] **Step 5:** Report for review. The reviewer commits `feat: three themes as tokens`.

### Task 2: Trend maths (pure Kotlin)

**Files:**
- Create: `app/src/main/java/app/bloblog/Trend.kt`
- Test: `app/src/test/java/app/bloblog/TrendTest.kt`

**Interfaces:**
- `data class Pt(val at: Long, val centi: Int)`. `at` is epoch ms.
- `enum class Range { DAY, WEEK, MONTH, M3, YEAR }`, plus `fun rangeStart(r: Range, nowMs: Long, zone: ZoneId): Long`: DAY = local midnight today; the others are now minus 7, 30, 90 or 365 days (spec §6).
- `fun avg7Segments(pts: List<Pt>): List<List<Pair<Long, Double>>>`: points sorted by time; the value at `t` is the mean kg of points in `(t−7d, t]`; a new segment starts when consecutive points are more than 7 days apart.
- `data class Axis(val lo: Double, val mid: Double, val hi: Double)` and `fun axisFor(values: List<Double>, pad: Double = 0.3): Axis`. The rule is in spec §6: `lo = floor((min − pad)·2)/2`, `step = max(0.5, ceil(((max + pad) − lo)/2·2)/2)`.
- `fun compareSeries(pts: List<Pt>, fromMs: Long, toMs: Long, zone: ZoneId): List<Pair<LocalDate, Double?>>`: one entry per local day in `[from, to]`. The value is the 7-day average at the end of that day minus the baseline, or null if there were no points in those 7 days. The baseline is the 7-day average at `fromMs` (using earlier points), else the first non-null day (spec §5.2).
- `fun summaryLine(name: String, kg: Double?, changeKg: Double?, past: Boolean): String`. Exact copy from spec §5.2: `"Julia is at 65.58 kg, down 0.45 kg."` / `"was at"`; `"no change"` when |change| < 0.05; `"Sam Lee has no weigh-ins in this range."` when `kg == null`.
- `fun initials(name: String): String` (spec §5.3)
- `fun monthChipLabels(months: List<YearMonth>): List<String>` (oldest → newest; the year on the first and on every January, e.g. `"Aug 2026"`, `"Sep"`, `"Jan 2027"`)

- [ ] **Step 1: Write the failing tests**
  - `axisCases`: `[65.0, 66.8] → (64.5, 66.0, 67.5)`, `[65.4, 65.9] → (65.0, 66.0, 67.0)`, `[77.6, 79.4] → (77.0, 78.5, 80.0)`, `[60.0, 70.0] → (59.5, 65.0, 70.5)`
  - `axisSingleValue`: `[66.0] → (65.5, 66.0, 66.5)`
  - `axisWideSpread`: `[65.0, 95.0]` → `lo == 64.5`, `hi ≥ 95.3`, and `mid` is a multiple of 0.5
  - `avg7BreaksAfterGap`: points on days 0, 1 and 10 give 2 segments; the day-1 value is the mean of day 0 and day 1
  - `compareBaselineUsesEarlierPoints`: a point 3 days before `from` sets the baseline, so the first day's value is not 0
  - `compareSeriesUsesLocalDays`: in `Asia/Singapore`, a point at 23:59 local on 31 Oct falls on 2026-10-31
  - `summaryWording`: down, up, no change (0.04), past tense, and no data, each with exact strings
  - `initialsRule`: `"Sam Lee" → "SL"`, `"Julia" → "JU"`, `"julia stone" → "JS"`
  - `monthLabelsAcrossYear`: `[2026-11, 2026-12, 2027-01] → ["Nov 2026", "Dec", "Jan 2027"]`
- [ ] **Step 2:** Run `--tests app.bloblog.TrendTest`. Expected: FAIL.
- [ ] **Step 3:** Implement `Trend.kt` with the signatures above.
- [ ] **Step 4:** Run. Expected: PASS.
- [ ] **Step 5:** Report for review (`feat: trend maths`).

### Task 3: Who's who v2 and backfill

**Files:**
- Modify: `app/src/main/java/app/bloblog/Assign.kt`, `app/src/main/java/app/bloblog/Db.kt` (`refs`), `app/src/main/java/app/bloblog/Scanner.kt` (pass the reading time)
- Test: modify `AssignTest.kt`; add cases to `DbTest.kt`

**Interfaces:**
- `assign(weightCentiKg: Int, refs: Map<Long, Int>): Long?` keeps its signature. The threshold changes to `d1 ≤ 200 && (no second person || d2 − d1 ≥ 200)` (spec §7.1).
- `Db.refs(atMs: Long): Map<Long, Int>`: for each person, the 7-day average (rounded centi) of their readings in `(atMs − 7d, atMs]`, else their latest assigned reading, else their start weight. `ScanReceiver` passes the capture time. Replace every call to `refs()` (`ScanReceiver` in Scanner.kt:62, and `DbTest.referenceWeights` lines 35 and 38).
- `Db.backfill(newPersonId: Long): Int`: goes through unassigned readings oldest first, calls `assign` with `refs(reading.takenAt)`, keeps the result **only if it equals `newPersonId`**, and returns the count. No Health Connect calls.

- [ ] **Step 1: Update and add the failing tests**
  - `AssignTest`: `assign(6330, owner 6630)` is now **null** (3.00 kg away); `assign(6430, …) == 1` (2.00 kg); the two-person cases still hold
  - `DbTest.refsUseSevenDayAverage`: readings of 6600 and 6700 in the last 7 days, plus 6000 from 10 days ago, give a ref of 6650
  - `DbTest.luggageHeldBack`: owner average 6600 and second person start 7840; a reading of 8160 gives null
  - `DbTest.backfillOnlyNewPerson`: unassigned readings of 7800 and 6610; after adding the second person (start 7840), `backfill` returns 1, the 6610 reading stays unassigned, and `pendingForMe()` is empty
- [ ] **Step 2:** Run. Expected: FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run the whole test suite. Expected: PASS.
- [ ] **Step 5:** Report for review (`feat: 7-day reference, 2 kg rule, backfill`).

### Task 4: Database queries for the new screens

**Files:**
- Modify: `app/src/main/java/app/bloblog/Db.kt`
- Test: `DbTest.kt`

**Interfaces:**
- `fun readingsFor(personId: Long, fromMs: Long, toMs: Long): List<Reading>` (oldest first)
- `fun historyMonth(personId: Long, month: YearMonth, zone: ZoneId): List<Reading>`: that person's readings plus unassigned ones in that local month, newest first
- `fun months(personId: Long, zone: ZoneId): List<YearMonth>`: local months that have readings for the person or unassigned ones, oldest first
- `fun unassigned(): List<Reading>` (oldest first)
- `fun updatePerson(id: Long, name: String, startWeightCenti: Int)`
- `fun startWeight(personId: Long): Int`
- `Person` gains `val order: Int` (0 for the first person added, 1 for the second), which selects the owner or second colour tokens
- `Reading` gains `val written: Boolean` (from `hc_written`), which drives the "· not sent yet" marker. Update every `Reading(...)` construction, including in the tests.

- [ ] **Step 1: Write the failing tests:** `readingsForRange`, `historyIncludesUnassigned`, `readingCarriesWritten`, `monthsUseLocalZone` (23:59 on 31 Oct in Asia/Singapore → Oct), `updatePersonPersists`, `personOrder`
- [ ] **Step 2:** Run. Expected: FAIL.
- [ ] **Step 3:** Implement with bound arguments, as the existing code does.
- [ ] **Step 4:** Run. Expected: PASS.
- [ ] **Step 5:** Report for review (`feat: history and range queries`).

### Task 5: Stickers and TrendChartView

**Files:**
- Copy `docs/stickers/blob-*.png` → `app/src/main/res/drawable-nodpi/blob_*.png` (12 files)
- Create: `app/src/main/java/app/bloblog/TrendChartView.kt`
- Test: `app/src/test/java/app/bloblog/TrendChartViewTest.kt`

**Interfaces:**
- `class TrendChartView(context, attrs) : View`
- `fun showPerson(pts: List<Pt>, range: Range, lineAttr: Int, dotAttr: Int, nowMs: Long)`: dots (radius 3, or 2 on YEAR) plus `avg7Segments` (no line on DAY), the axis from `axisFor`, and x labels as in spec §6 (Year: 5 month labels)
- `fun showCompare(series: List<Pair<String, List<Pair<LocalDate, Double?>>>>, lineAttrs: List<Int>)`: lines of change, a dashed zero line, and name labels at the line ends (hidden while scrubbing)
- `var onScrub: ((index: Int?) -> Unit)?`: the index of the nearest point (person mode) or day (Compare mode) while pressing; null on release. Calls `parent.requestDisallowInterceptTouchEvent(true)` while pressing.
- Empty data: draw the axis and the centred text `No weigh-ins in this range`
- `contentDescription` set on each `show…`, e.g. `Weight trend, month. 19 weigh-ins from 65.0 to 66.8 kg. 7-day average now 65.58 kg.`

- [ ] **Step 1: Write the failing tests** (Robolectric): `contentDescriptionPerson` (exact string for a fixed data set), `scrubReportsNearestIndex` (send DOWN/MOVE at the x of point 3 → callback gets 3; UP → null), `emptyRangeDescription`
- [ ] **Step 2:** Run. Expected: FAIL.
- [ ] **Step 3:** Implement. All colours come from `context.token(...)`.
- [ ] **Step 4:** Run. Expected: PASS.
- [ ] **Step 5:** Report for review (`feat: trend chart view and stickers`).

### Task 6: Home person view

**Files:**
- Create: `app/src/main/res/layout/activity_main.xml` (`CoordinatorLayout` → `AppBarLayout` with `liftOnScroll=true` → the strip and chip row with `scrollFlags="scroll"`, and the headline with no flags → `RecyclerView` with `appbar_scrolling_view_behavior`)
- Create: `app/src/main/java/app/bloblog/Notice.kt`, `app/src/main/java/app/bloblog/HomeAdapter.kt`
- Modify: `MainActivity.kt` (rebuilt; keep the permission and Health Connect flows, `Scanner.start` only in `onResume` and on permission grant)
- Test: `NoticeTest.kt`, `HomeTest.kt`

**Interfaces:**
- `enum class Notice { NEED_BT_PERMISSION, BT_OFF, LISTEN_FAILED, WHOSE, HC_NOT_CONNECTED }`, plus `fun noticeFor(scan: Scanner.State, unassigned: Int, hcAvailable: Boolean, hcConnected: Boolean, ownerTab: Boolean): Notice?` in the priority order of spec §5.1 (HC only on the owner tab, and never when HC isn't available)
- `HomeAdapter` item types: `GRAPH` (range pills + `TrendChartView` + legend), `HISTORY_HEADER` (title + count), `MONTHS` (horizontal chips), `ROW`, `WHOSE_ROW` (full-bleed `bl_notice`, covering the divider above), `EMPTY` (peek sticker + `No weigh-ins yet. Step on the scale.`)
- State kept in `savedInstanceState`: `tab` (person id, or `-1` for Compare), `range: Range`, `month: YearMonth`
- Headline: text exactly as spec §5.1 item 3. While `onScrub` is active it shows the scrubbed weigh-in in `bl_ownerText`.

- [ ] **Step 1: Write the failing tests**
  - `NoticeTest`: one case per priority line, plus `whoseBeatsHc`, `hcOnlyOnOwnerTab` and `hcUnavailableNeverShows`
  - `HomeTest` (Robolectric, seeded `Db`): `historyNewestFirst`, `whoseRowShownOnEveryTab`, `rangeDefaultsToMonth`, `emptyPersonShowsPlaceholder`, `headlineTextToday` (`"today · 7-day avg 65.58"`), `headlineTextPastDay` (latest weigh-in yesterday → `"Wed 25 Nov · 7-day avg …"`), `notSentMarkerOnlyOwner` (an owner row with `written == false` shows `· not sent yet` in `bl_warnText`; the second person's rows never do)
- [ ] **Step 2:** Run. Expected: FAIL.
- [ ] **Step 3:** Implement. Dividers are inset 16 on both sides, with none under the last row. Spacing follows DESIGN.md. The strip's `›` opens the row sheet for the newest unassigned reading (Task 7 hook: `openRowSheet(readingId)`).
- [ ] **Step 4:** Run, then the full check.
- [ ] **Step 5:** Report for review (`feat: home person view`).

### Task 7: Row sheet, delete and messages

**Files:**
- Create: `app/src/main/java/app/bloblog/RowActions.kt` (sheet, dialog, swipe, snackbars)
- Modify: `MainActivity.kt`, `HomeAdapter.kt` (`ItemTouchHelper` swipe-left on `ROW` and `WHOSE_ROW`)
- Test: `RowActionsTest.kt`

**Interfaces:**
- `fun openRowSheet(readingId: Long)`: a `BottomSheetDialog` laid out per spec §5.4. "Move to" lists the other people; `🗑 Delete weigh-in` is in `bl_text`.
- `fun confirmDelete(readingId: Long)`: a `MaterialAlertDialogBuilder` with the exact copy from spec §5.5, and the Delete button in `bl_danger` / `bl_onDanger`
- `suspend fun moveReading(readingId: Long, toPersonId: Long): Boolean` and `suspend fun deleteReading(readingId: Long): Boolean` reuse the existing Health Connect rules (remove before moving away from the owner; write when moving to the owner). They return false and show `Couldn't update Health Connect. Try again.` on failure.
- `fun showSaved(name: String, centi: Int)` and `fun showMatched(count: Int, name: String)`: a `Snackbar` with the sticker, background `bl_toast` and `bl_toastStroke`. Copy from spec §5.6.

- [ ] **Step 1: Write the failing tests** (Robolectric, with Health Connect replaced by a fake `HcPort` interface, `fun remove(id): Boolean` and `fun write(r)`): `moveAwayFromOwnerRemovesFirst`, `removeFailureKeepsRow`, `deleteUnwrittenSkipsHc`, `actingOnDeletedReadingIsNoOp`
- [ ] **Step 2:** Run. Expected: FAIL.
- [ ] **Step 3:** Implement. Introduce `interface HcPort` with `HealthConnect` as the default implementation, so the logic can be tested.
- [ ] **Step 4:** Run, then the full check.
- [ ] **Step 5:** Report for review (`feat: row sheet, delete, snackbars`).

### Task 8: Compare view

**Files:**
- Modify: `MainActivity.kt`, `HomeAdapter.kt` (Compare: graph item + summary; no headline, no History)
- Test: `HomeTest.kt` (add)

**Interfaces:** consumes `compareSeries`, `summaryLine` and `TrendChartView.showCompare`. The range pills are `Week Month 3M Year`; DAY maps to WEEK. The caption is `Change since 27 Oct · 7-day averages` (the date formatted `d MMM` from `rangeStart`). While scrubbing, the summary shows the day (`Thu 19 Nov`) and uses `past = true`.

- [ ] **Step 1: Write the failing tests:** `compareHidesDay`, `compareSummaryLines` (seeded data → the exact two lines), `compareNoDataLine`
- [ ] **Step 2:** Run. Expected: FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run, then the full check.
- [ ] **Step 5:** Report for review (`feat: compare view`).

### Task 9: Settings (People and Appearance)

**Files:**
- Create: `app/src/main/java/app/bloblog/SettingsActivity.kt` and its layout. Register it in the manifest.
- Modify: `MainActivity.kt` (⚙ opens Settings; the old setup dialog stays for first launch, with the wave sticker)
- Test: `SettingsTest.kt`

**Interfaces:** People section as in spec §5.3: avatars via `initials` and the `order` colour, `(Me)`, three lines per person, `+ Add person` hidden at 2, and the edit/add dialog with the existing validation copy. After adding, call `Db.backfill` and `showMatched` if the count > 0. Appearance: three radio rows with the copy from spec §5.3; choosing one calls `ThemePrefs.set` and then `recreate()`. The Health Connect-unavailable text goes on the owner row (spec §5.1 note).

- [ ] **Step 1: Write the failing tests:** `addPersonRunsBackfill`, `addHiddenAtTwo`, `themeChoiceSaved`, `themeChangeKeepsTabAndRange` (recreate MainActivity with a saved state → the same tab and range)
- [ ] **Step 2:** Run. Expected: FAIL.
- [ ] **Step 3:** Implement. Every activity calls `applyAppTheme()` first.
- [ ] **Step 4:** Run, then the full check.
- [ ] **Step 5:** Report for review (`feat: settings with people and appearance`).

### Task 10: Live updates

**Files:**
- Modify: `Scanner.kt` (`ScanReceiver`), `MainActivity.kt`
- Test: `LiveUpdateTest.kt`

**Interfaces:**
- After a reading is saved and assigned, `ScanReceiver` sends `Intent("app.bloblog.READING_SAVED").setPackage(context.packageName).putExtra("reading_id", id)`.
- `MainActivity` registers a receiver in `onStart` with `RECEIVER_NOT_EXPORTED`, and unregisters it in `onStop`. On receipt it refreshes the data and calls `showSaved` only if the reading has a person (spec §5.6).

- [ ] **Step 1: Write the failing test** (Robolectric): `savedBroadcastRefreshesAndShowsSnackbar` (send the broadcast for a seeded assigned reading → the new row appears and the snackbar text is `Saved 65.70 kg for Julia`), and `unassignedBroadcastNoSnackbar`
- [ ] **Step 2:** Run. Expected: FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run, then the full check.
- [ ] **Step 5:** Report for review (`feat: live updates`).

### Task 11: Docs travel with the code

**Files:** `SPEC.md` (rule 3 → spec §7.1; Screens → spec §5; Build status v0.3), `PRD.md` (D-8 reversed as **D-12**; D-9 narrowed per spec decision 7; stories **US-9…US-16**, one per spec §5.1–§5.7 plus themes, each with testable criteria naming the tests above; backlog: tagging, goals), `CLAUDE.md` (themes and the new files in one line each), `app/build.gradle.kts` (`versionName = "0.3"`, `versionCode = 3`)

- [ ] **Step 1:** Update the docs. Every behaviour sentence must match the spec, with no new behaviour.
- [ ] **Step 2:** Run the full check. Expected: PASS, 0 lint errors.
- [ ] **Step 3:** Report for review (`docs: v0.3 spec, PRD and build status`). The owner then installs via Google Drive and runs the on-phone checks in spec §10.
