# More blob, CSV download, year dividers: Implementation Plan

> Project rule (CLAUDE.md): Sonnet implements, Opus reviews each diff and runs the checks, then commits. Implementers don't commit.

**Goal:** add four blob moments, a CSV download of all weigh-ins in Settings, and year dividers in the History month chips.

**Spec:** this plan pins the owner's decisions of 2026-11-26 (Q46–Q49). Base spec: [docs/superpowers/specs/2026-11-26-readings-screen-design.md](../superpowers/specs/2026-11-26-readings-screen-design.md). Tokens: [DESIGN.md](../../DESIGN.md). **Blob never reacts to weight going up or down.** Stickers only mark empty states, actions and milestones.

## Global Constraints
- Every shell: `export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home`. Full check: `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:lintDebug`, all green, with 0 lint errors.
- Colours via theme attributes only. Sticker sizes: 24 in rows, 28 in the strip, 32 beside headings, 36 in messages, 48 in empty states, 56 in dialogs.
- No new permissions and no new dependencies.

### Task A: Blob moments
**Files:** `TrendChartView.kt`, `HomeAdapter.kt`, `RowActions.kt`, `SettingsActivity.kt`, `MainActivity.kt` (live update), tests in `HomeTest`, `RowActionsTest`, `LiveUpdateTest`
1. **Empty range** (not Day): a `search` sticker (48) centred above the existing text `No weigh-ins in this range`. Draw it in the graph's empty area, or as a view over the chart, whichever is smaller.
2. **Day with no weigh-in today:** a `run` sticker (48) with the text `No weigh-in yet today` (12 sp, `bl_textMuted`), replacing the empty-range text on Day.
3. **Delete confirmation:** a `peek` sticker (56) above the dialog title. The copy is unchanged.
4. **First weigh-in:** when a live update brings a person's **first ever** assigned weigh-in, show the message with the `cheer` sticker: `Your first weigh-in!` for the `(Me)` person, `Sam Lee's first weigh-in!` for others (`<Name>'s first weigh-in!`), instead of `Saved …`.
- **Not** a `wave` sticker beside `Appearance` (owner, 2026-11-26).
- Tests: `emptyRangeShowsSearchSticker`, `dayWithoutTodayShowsRun` (text present), `deleteDialogHasPeek`, `firstWeighInMessage` (Me and other), `laterWeighInSavedMessage` (the second weigh-in still says `Saved 65.70 kg for Alex`).

### Task B: CSV download
**Files:** create `CsvExport.kt`; modify `SettingsActivity.kt`; test `CsvExportTest.kt`
- `fun weighInsCsv(db: Db, zone: ZoneId): String`. Header: `date,time,weight_kg,person,in_health_connect`. One row per reading, **oldest first**, for everyone, including hidden and unassigned readings. `date` is `yyyy-MM-dd` and `time` is `HH:mm`, both local. `weight_kg` has 2 decimals with a `.` decimal point. `person` is the name (empty for unassigned). `in_health_connect` is `yes` or `no`. Names containing `,` `"` or a newline are quoted per RFC 4180. Lines end with `\n`.
- Settings: a new last section **Data** (title 16 bold, with 32 above it). One 56-tall row: a `notebook` sticker (40), `Download weigh-ins (CSV)` (14), and the caption `Everyone's weigh-ins, one row each.` (12, muted). Tapping it launches `ActivityResultContracts.CreateDocument("text/csv")` with the name `blob-log-<yyyy-MM-dd>.csv`, then writes the string via `contentResolver.openOutputStream`. On success, show a message with the `notebook` sticker: `Saved blob-log-2026-11-26.csv`. On failure: `Couldn't save the file. Try again.`
- Tests: `csvHeaderAndOrder`, `csvUnassignedAndHidden`, `csvQuotesNames` (`Stone, Alex` → `"Stone, Alex"`), `csvLocalTime` (Asia/Singapore).

### Task C: Year dividers in the month chips
**Files:** `HomeAdapter.kt`, `Trend.kt` (replace `monthChipLabels`), `TrendTest`, `HomeTest`
- Chips show the month only (`Aug`). Before the first chip of each year, a year label (`2026`, 12 sp, `bl_textMuted`, 8 dp side margins). Between years, a 1 dp × 24 dp `bl_divider` hairline before the year label.
- Replace `monthChipLabels(months)` with `fun monthChipGroups(months: List<YearMonth>): List<Pair<Int, List<YearMonth>>>` (year → its months, oldest first).
- Tests: `monthGroupsAcrossYear` (`[2026-11, 2026-12, 2027-01]` → `[(2026, [Nov, Dec]), (2027, [Jan])]`), `chipsShowMonthOnly`.

### Task D: Docs
Update SPEC.md (screens: graph empty states, delete dialog sticker, first weigh-in message, Settings → Data, month chips) and DESIGN.md (the sticker table and sizes, the year divider). Bump `versionName` to `0.3.1` and `versionCode` to `4`.
