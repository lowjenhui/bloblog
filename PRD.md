# Blob-log — PRD

## Why

The owner wants their weigh-ins in Google's health ecosystem without installing another third-party app, on a setup they control. It's the first of a series of small apps for getting fitter, and its database is the base those later apps build on.

## Goals (milestone 1)

- Stepping on the scale is the only thing the owner does. The weight ends up in Health Connect.
- Both household members' readings are kept, correctly attributed, in a database the owner controls.
- Cost: free. No cloud services, no Play Store.

## Non-goals (milestone 1)

Fitbit or Google Health cloud sync, an iPhone app, delivering the partner's data, body fat (unless Known gap 1 resolves), a web app.

## User stories

| ID | Story | Acceptance criteria |
|---|---|---|
| US-1 | As the owner, I set up both people once. | Setup saves two people with name and starting weight. Exactly one is "This is me". Weights outside 20–250 kg are rejected with the spec message. |
| US-2 | As the owner, I pair my scale. | Stepping on the scale during "Find your scale" shows `Found it — <weight> kg` within 15 s. The scale ID is saved. Other `AA BB` scales are ignored afterwards. |
| US-3 | As the owner, my weigh-in is saved without opening the app. | With the app closed and the screen off, a weigh-in appears in Readings and Health Connect within 2 minutes. The parser test decodes every final packet in `docs/scale-capture-2026-11-22.txt` as 67.65 kg. |
| US-4 | One weigh-in makes one reading. | Locked packets repeated for 30 s produce exactly one reading (unit test on the dedupe rule). |
| US-5 | Readings are attributed to the right person. | `AssignTest` and `DbTest` (`refsUseSevenDayAverage`, `luggageHeldBack`) cover SPEC rule 3: closest within 2 kg of the 7-day reference with a 2 kg margin assigns; ties, guests, bags and far-off readings stay unassigned. |
| US-6 | I'm asked only when it's unclear. | An unassigned reading posts the spec notification. Assigned readings post nothing. |
| US-7 | I can fix a wrong assignment. | Reassigning to or from the owner adds or removes the Health Connect record. "Delete weigh-in" removes it everywhere (see US-12, US-13). |
| US-8 | The partner's data isn't lost. | Partner readings are stored with raw hex and are never written to Health Connect. |
| US-9 | I open the app and see today's weight and my history. | `HomeTest`: `rangeDefaultsToMonth`, `headlineTextToday`, `headlineTextPastDay`, `emptyPersonShowsPlaceholder`, `historyNewestFirst`, `historyCountExcludesUnassigned`, `notSentMarkerOnlyOwner`, `whoseRowShownOnEveryTab`, `tapTargetsAtLeast48dp`. `NoticeTest` covers the notice strip priority. `TrendTest`: `axisCases`, `axisSingleValue`, `axisWideSpread`, `avg7BreaksAfterGap`, `monthLabelsAcrossYear`. `TrendChartViewTest`: `contentDescriptionPerson`, `emptyRangeDescription`, `scrubReportsNearestIndex`, `plotStartsAfterLabelGutter`. `DbTest`: `readingsForRange`, `historyIncludesUnassigned`, `monthsUseLocalZone`. |
| US-10 | I compare both people on one graph. | `HomeTest`: `compareHidesDay`, `compareSummaryLines`, `compareNoDataLine`. `TrendTest`: `compareBaselineUsesEarlierPoints`, `compareSeriesUsesLocalDays`, `summaryWording`. |
| US-11 | I manage people and appearance in Settings. | `SettingsTest`: `addPersonRunsBackfill`, `addHiddenAtTwo`. `TrendTest.initialsRule`. `DbTest`: `updatePersonPersists`, `personOrder`, `backfillOnlyNewPerson`. |
| US-12 | I can move a weigh-in to someone else from the row sheet. | `RowActionsTest`: `moveAwayFromOwnerRemovesFirst`, `moveToOwnerWrites`, `removeFailureKeepsRow`, `actingOnDeletedReadingIsNoOp`, `sheetAndDialogOpen`. |
| US-13 | I can delete junk safely, and it leaves Health Connect too. | `RowActionsTest`: `deleteUnwrittenSkipsHc`, `deleteWrittenRemovesFirst`, `removeFailureKeepsRow`, `sheetAndDialogOpen`. |
| US-14 | I get short messages about what just happened, and the screen updates live. | `LiveUpdateTest`: `savedBroadcastRefreshesAndShowsSnackbar`, `unassignedBroadcastNoSnackbar`. |
| US-15 | First launch asks for my name and weight. | Behaviour unchanged. Checked on the phone (design spec §10). |
| US-16 | I use the scale alone, or with a second person. | `ModeTest`: `newInstallIsSolo`, `upgradeWithTwoPeopleIsDuo`, `soloAssignIgnoresHiddenPerson`, `soloHidesChipsAndCompare`, `soloFallsBackFromHiddenTab`, `switchToDuoWithoutSecondOpensAdd`, `switchToDuoRestoresAndBackfills`, `someoneElseLinkAddsAndSwitches`. |
| US-17 | I choose Colourful, Calm or Dark. | `ThemeTest`: `defaultIsColourful`, `roundTrip`, `tokensPerTheme`. `SettingsTest`: `themeChoiceSaved`, `themeChangeKeepsTabAndRange`. |

## Decisions

| # | Decision | Reason |
|---|---|---|
| D-1 | Native Kotlin Android app, sideloaded | Health Connect can only be written from an on-device app. openScale's scale code is Kotlin. No Play review needed. |
| D-2 | Health Connect, not the Fitbit or Google Health API | The Fitbit Web API was shut down on 2 Nov 2026. In a personal setup the Google Health API needs re-login every 7 days. The owner only needs Health Connect. |
| D-3 | Own app instead of openScale or Feelfit | The owner wants no extra third-party app, and wants a configurable base for later apps. |
| D-4 | Background capture from the broadcast advertisement | The 2026-11-22 scan showed the scale broadcasts its weight, so no connection is needed. |
| D-5 | Auto-assign by nearest reference weight, ask when unclear | Hands-free in the common case, and never silently wrong when it's close. |
| D-6 | Own SQLite database plus Health Connect | Keeps the partner's data and the raw bytes. Health Connect only holds the owner's data. |
| D-7 | Notify only for unassigned readings | The owner's choice: quiet unless input is needed. |
| D-8 | ~~One screen (Readings list) and no charts~~ Reversed by D-12 | Health Connect and Fitbit already chart weight. |
| D-9 | Views, platform SQLite; Material Components and RecyclerView allowed; no Compose, Room or chart library | Smallest dependency set for a two-table app. Narrowed from "plain Views" in v0.3: RecyclerView (History, swipe-to-delete) and Material Components (row sheet) are View-based and save several hundred hand-written lines. |
| D-10 | Owner first; the second person is the next milestone, sent to Apple Health | The owner can use it now. The assignment code already handles two people, so adding the second person is a new person row plus an Apple Health export. Guests and far-off readings are held back from Health Connect. |
| D-11 | Second person → Apple Health milestone deferred (2026-11-25) | Owner: "It's not great." Every option was weak. A share button plus an iPhone Shortcut is manual on both phones. A shared Drive file read by a Shortcut is fragile. An own iPhone app needs a $99/yr account (or a reinstall every 7 days), and iOS can't scan for this scale in the background. Second-person readings are still captured and stored as unassigned. |
| D-12 | Charts and history in Blob-log (reverses D-8) | The owner wants to see raw weigh-ins and trends; Fitbit shows only the last few days. The partner's weigh-ins are viewable here while Health Connect keeps only the owner's. Blob-log's database stays the record for later tagging. |
| D-13 | Three themes: Colourful (default), Calm, Dark | Owner's decision, 2026-11-26. Chosen in Settings and applied by the app itself, ignoring the phone's dark mode. Reverses the earlier "light only". |
| D-14 | Modes: Just me (default) / Two people | Owner's decision, 2026-11-26. The app is public, so most people use it alone. Switching back to Just me hides the second person but keeps their data. Setup doesn't ask; the "Whose?" sheet offers "Someone else uses this scale? Add them". |

## Backlog

1. Deliver the partner's readings to their Apple Health. Deferred (D-11): revisit if a less manual route appears.
2. ~~Body fat~~: dropped. The scale broadcasts weight only (SPEC Known gap 1, 2026-11-25).
3. ~~Trend chart~~: done in v0.3 (D-12).
4. Later fitness apps reading the Blob-log database or Health Connect.
5. Tag weigh-ins with eating and behaviour notes and look for trends.
6. Goal weights.
