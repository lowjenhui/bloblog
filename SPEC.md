# Blob-log — Spec (milestone 1)

Source of truth for behaviour. PRD.md holds goals, stories and decisions.

## What it is

A sideloaded Android app (Kotlin) on the owner's phone. It catches weigh-ins from the Yolanda "feelfit" bathroom scale in the background, decides whether each one is the owner's or their partner's, stores every reading in its own database, and writes the owner's weight to Health Connect. Nothing goes to the cloud. Fitbit is out of scope.

## Build status (v0.2)

People: the phone owner (`isMe`) is set up first. The second person comes in the next milestone, and their readings go to Apple Health, not Health Connect.

Done in v0.2: rules 2–7 for one person, setup for the owner only (screen 1, interim form), permissions (screen 3), readings with names and reassignment (screen 4), and the notification (screen 5). Not done yet: scale pairing (screen 2). Second person to Apple Health is deferred (PRD D-11). Until pairing is built, the scan filter accepts any `AA BB` scale, and each reading stores its scale ID. Readings captured by v0.1 start unassigned after the upgrade. Checked on the owner's phone on 2026-11-24: setup, permissions, reassigning a v0.1 reading, and a background weigh-in written to Health Connect with the app closed.

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
3. **Who's who.** There are two people. Each has a *reference weight*: their latest assigned reading, or the starting weight entered at setup. For reading `r`, let `d1` be its distance to the nearest person's reference weight and `d2` its distance to the other person's.
   - Auto-assign to the nearest person when `d1 ≤ 3 kg` **and** `d2 − d1 ≥ 2 kg`.
   - Otherwise the reading is **unassigned**, and the app posts the notification below.
   - With one person set up, `d2` is infinite, so only "within 3 kg of the owner's reference weight" applies. Guests are held back.
4. **Health Connect.** Only readings assigned to the person marked "This is me" are written, as a `WeightRecord` (time = capture time, `clientRecordId` = reading id). Reassigning a reading away from the owner deletes its record. Reassigning one to the owner writes it. Unassigned and partner readings are never written.
5. **Storage.** Every reading is kept in the app database, including the raw advertisement hex, so it can be re-decoded later.
6. **Deleting.** "Not us" removes the reading from the database, and from Health Connect if it was written there.
7. **Units.** Everything is stored and shown in kg, with 2 decimal places.

## Data

- `person(id, name, startWeightKg, isMe)`: exactly two rows. Exactly one has `isMe = true`.
- `reading(id, takenAt, weightKg, rawHex, personId nullable, writtenToHealthConnect)`
- `settings(scaleId)`

## Screens and messages (exact text)

**1. Setup** (first launch, and Settings → People)
- Title: `Who uses the scale?`
- Two cards, each with fields `Name` and `Current weight (kg)`. One card has the toggle `This is me`, on by default for the first card.
- Button: `Next`
- Validation: `Enter a weight between 20 and 250 kg.`
- **v0.2 interim:** a single non-cancelable dialog for the phone owner only, with the title, fields, button and validation above and no toggle.

**2. Find your scale**
- Title: `Find your scale`
- Body: `Step on your scale now. Keep your phone nearby.`
- While scanning: `Looking for your scale…`
- When found: `Found it — 67.65 kg`, with the button `Use this scale`
- After 60 s with nothing found: `No scale found. Make sure Bluetooth is on and step on the scale again.`, with the button `Try again`

**3. Permissions**, asked in this order, each with a one-line reason shown before the system prompt:
- Bluetooth: `Blob-log listens for your scale in the background.`
- Notifications: `So you can say whose weigh-in it was when it's unclear.`
- Health Connect (weight, write): `Your weight is saved to Health Connect.`
- v0.2 shows no reason text before the Notifications and Health Connect prompts. The Health Connect banner on screen 4 serves as its reason.
- If Health Connect isn't available: `Health Connect isn't available on this phone.`, with no button.
- Privacy screen (opened from Health Connect's permission page): `Blob-log saves your weight from your scale to Health Connect. It doesn't read any Health Connect data, and nothing leaves your phone.`
- If a write fails, or the permission arrives later, unsent readings of the owner are retried each time the app opens.

**4. Readings** (home screen)
- Title: `Readings`
- Rows, newest first: `Sat 22 Nov · 07:42` / `67.65 kg` / person's name, or `Who's this?` in accent colour if unassigned.
- Empty state: `No weigh-ins yet. Step on the scale.`
- Status line at the top:
  - `Listening for your scale, even when Blob-log is closed.`
  - `Blob-log listens for your scale in the background.`, with the button `Allow Bluetooth`
  - `Bluetooth is off. Turn it on, then reopen Blob-log.`
  - `Couldn't start listening. Reopen Blob-log to try again.`
- Banner when the Health Connect permission is missing: `Health Connect isn't connected. Readings are saved here but not sent.`, with the button `Connect`
- Tapping a row opens a sheet titled `Whose weigh-in?` with the buttons `<Name 1>`, `<Name 2>` and `Not us — delete`.
- If Health Connect can't remove a record when reassigning or deleting, the reading is left unchanged and a toast shows `Couldn't update Health Connect. Try again.`

**5. Notification** (unassigned reading only)
- Title: `Whose weigh-in?`
- Body: `67.65 kg at 07:42 — tap to choose.`
- Tapping it opens the sheet from screen 4.

## Known gaps

1. **Body fat:** resolved (2026-11-25): not available. In a barefoot weigh-in, held for about 9 s after locking, no byte except the clock changed, and bytes 19–21 are the same with nobody on the scale. This is a weight-only scale.
2. **Background scanning reliability:** worked on the owner's phone on 2026-11-24 (app closed, screen locked, one weigh-in captured). Not yet tested after days of idle Doze, a reboot, or Bluetooth being toggled.
3. **Unknown bytes** 9–14, 16 and 19–23. Byte 16 might be the unit (`0x10` = kg?). It hasn't been tested with the scale set to lb.
4. **Close weights:** resolved for this household. The two people are more than 10 kg apart, so the 3 kg / 2 kg rule separates them easily. Until the second person is set up, each of their weigh-ins shows up as unassigned with a "Whose weigh-in?" notification.
5. **Partner's data** stays on the owner's phone. Getting it to the partner's Apple Health is deferred (PRD D-11). Their weigh-ins stay stored as unassigned.
6. **Two scales or a neighbour's scale:** the filter on the scale ID ignores other scales. Re-pairing is only possible from Settings.
7. **Bluetooth toggled off and on:** the background scan may stop until Blob-log is reopened or the phone restarts. This is untested.
8. **Android 12+ only** (minSdk 31). Is the owner's phone on Android 12 or later?
