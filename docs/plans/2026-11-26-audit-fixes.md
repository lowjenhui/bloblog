# Audit fixes 1–7 Implementation Plan

> Project rule (CLAUDE.md): Sonnet implements, Opus reviews the diff and runs the checks, then commits. Implementers don't commit.

**Goal:** fix the seven findings from the 2026-11-26 whole-repo audit without changing anything the user sees, except that items 1 and 2 behave correctly.

**Global constraints**
- Every shell: `export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home`. Full check: `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:lintDebug`. Everything green, and 0 lint errors (`grep -c 'severity="Error"' app/build/reports/lint-results-debug.xml`).
- TDD for 1, 2 and 3: the test first, seen failing, then the fix.
- No new dependencies. No changes to on-screen text.

### 1. Health Connect gone → weigh-ins stuck
`HealthConnect.remove(context, readingId)`: when `client(context) == null`, call `Db.markWritten(readingId, false)` and return `true`. There's no record to remove, so moving and deleting can go ahead. Permission errors (client present, call throws) still return `false`.
- Test (`RowActionsTest`): `deleteWhenHealthConnectGoneSucceeds`. Exercise the real `HealthConnect.remove` under Robolectric, where the client is unavailable, through `hcPort`. Delete a reading marked written; it's gone from the DB, and no "Couldn't update Health Connect" message is shown.

### 2. `syncPending` without permission
At the top of `HealthConnect.syncPending(context, hc)`, return early unless the caller is allowed to write. Add `suspend fun canWrite(): Boolean` to `HcPort` (default implementation: `HealthConnect.hasPermission(context)`; the fake returns a settable flag).
- Test: `syncPendingSkipsWithoutPermission`: with the fake's `canWrite = false`, `syncPending` calls `write` 0 times.

### 3. Testable capture path
Move the loop body of `ScanReceiver.onReceive` into a top-level function in `Scanner.kt`:
`suspend fun handleWeighIn(context: Context, db: Db, p: ScalePacket, raw: ByteArray, nowMs: Long, hc: HcPort = hcPort(context)): Long?`
It saves (`saveIfNew`), assigns with `refs(now, ModePrefs.get(...))`, then either posts the unassigned notification or calls `writeIfDue` under `HealthConnect.lock`, then sends `ACTION_READING_SAVED`. It returns the reading id, or null if not saved. Behaviour stays identical; the receiver keeps `goAsync`, the error handling and `finish()`.
- Tests (new `CaptureTest`, Robolectric): `ownerWeighInWritesHealthConnectAndBroadcasts` (DUO, owner at 6630, packet 6600 → the fake gets 1 write, and the broadcast is sent; check with `shadowOf(app).broadcastIntents`), and `soloPartnerWeightIsHeldBack` (SOLO, a hidden partner at 7840, packet 7820 → 0 writes, and the reading stays unassigned).

### 4. One person dialog
Extend `personDialog` so first-launch setup can use it: `firstPerson: Boolean = false` gives the title `Who uses the scale?`, the button `Next`, no Cancel, not cancellable, the wave sticker (64 dp) above the fields, the weight hint `Current weight (kg)`, and saving with `isMe = true` (no mode change, no backfill). Replace `MainActivity.showSetup`'s own form with `personDialog(null, firstPerson = true) { _, _ -> setupShowing = false; render() }`, keeping the `setupShowing` guard. The validation text stays exactly the same.
- Tests: the existing setup tests must still pass unchanged. If none exists, add `firstLaunchSetupCreatesOwner` (HomeTest): fill the name and `66.3`, tap Next → one person, `isMe`, start weight 6630.

### 5. Remove `Db.isWritten`
`RowActions.moveReading` and `deleteReading` use `db.reading(readingId)` (already loaded) and its `.written`. Delete `Db.isWritten`, and update any test that calls it.

### 6. One shared database helper
`Db` gets `companion object { fun get(context: Context): Db }`, returning a single instance created lazily on `applicationContext`, thread-safe. Replace every `Db(context)`, `Db(this)` and `Db(a)` in `app/src/main` with `Db.get(...)`. Tests may keep `Db(context)` where they reset the database, **but** `Db.get` must not keep handing out a stale instance after a test deletes the database file. Add `@VisibleForTesting fun resetForTests()`, and call it in each test class's `@Before` that calls `deleteDatabase`. Keep `@Synchronized` on `saveIfNew`; it now really serialises, because there's one instance.

### 7. One key for "reading_id"
Use `Notifications.EXTRA_READING_ID` in `Scanner.kt` (the broadcast) and `MainActivity.kt` (the receiver) instead of the literal `"reading_id"`. Update the tests that send that broadcast.

**Done when:** the full check passes, the report lists the tests per item with RED/GREEN for 1–3, and no on-screen text changed (grep the diff for changed string literals).
