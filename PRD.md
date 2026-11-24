# Blob-log — PRD

## Why

The owner wants their weigh-ins in Google's health ecosystem without installing another third-party app, on a setup they control. It's the first of a series of small apps for getting fitter, and its database is the base those later apps build on.

## Goals (milestone 1)

- Stepping on the scale is the only thing the owner does. The weight ends up in Health Connect.
- Both household members' readings are kept, correctly attributed, in a database the owner controls.
- Cost: free. No cloud services, no Play Store.

## Non-goals (milestone 1)

Fitbit or Google Health cloud sync, charts, an iPhone app, delivering the partner's data, body fat (unless Known gap 1 resolves), a web app.

## User stories

| ID | Story | Acceptance criteria |
|---|---|---|
| US-1 | As the owner, I set up both people once. | Setup saves two people with name and starting weight. Exactly one is "This is me". Weights outside 20–250 kg are rejected with the spec message. |
| US-2 | As the owner, I pair my scale. | Stepping on the scale during "Find your scale" shows `Found it — <weight> kg` within 15 s. The scale ID is saved. Other `AA BB` scales are ignored afterwards. |
| US-3 | As the owner, my weigh-in is saved without opening the app. | With the app closed and the screen off, a weigh-in appears in Readings and Health Connect within 2 minutes. The parser test decodes every final packet in `docs/scale-capture-2026-11-22.txt` as 67.65 kg. |
| US-4 | One weigh-in makes one reading. | Locked packets repeated for 30 s produce exactly one reading (unit test on the dedupe rule). |
| US-5 | Readings are attributed to the right person. | Unit tests cover the spec rule: closest within 3 kg with a 2 kg margin assigns; ties, guests and far-off readings stay unassigned. |
| US-6 | I'm asked only when it's unclear. | An unassigned reading posts the spec notification. Assigned readings post nothing. |
| US-7 | I can fix a wrong assignment. | Reassigning to or from the owner adds or removes the Health Connect record. "Not us — delete" removes it everywhere. |
| US-8 | The partner's data isn't lost. | Partner readings are stored with raw hex and are never written to Health Connect. |

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
| D-8 | One screen (Readings list) and no charts | Health Connect and Fitbit already chart weight. |
| D-9 | Platform SQLite and plain Views, no Room or Compose | Smallest dependency set for a two-table app. Revisit if the UI grows. |
| D-10 | Owner first; the second person is the next milestone, sent to Apple Health | The owner can use it now. The assignment code already handles two people, so adding the second person is a new person row plus an Apple Health export. Guests and far-off readings are held back from Health Connect. |

## Backlog

1. Deliver the partner's readings to their Apple Health (likely an iPhone Shortcut reading an export).
2. Body fat, if impedance turns up (Known gap 1).
3. Trend chart.
4. Later fitness apps reading the Blob-log database or Health Connect.
