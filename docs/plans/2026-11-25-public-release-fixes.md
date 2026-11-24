# Plan: code fixes before the repo goes public

**For the executor.** Read `CLAUDE.md`, `SPEC.md` and every file in `app/src/main/java/app/bloblog/` and `app/src/test/java/app/bloblog/` first. Keep to the house style: smallest change, platform SQLite and plain Views. Do **not** edit SPEC.md, PRD.md, README.md, CLAUDE.md, anything in `docs/`, or `AssignTest.kt`, because the planner is editing those in parallel. Do **not** commit.

Every shell needs `export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home`. Final check: `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug` passes with 0 lint errors.

Finding numbers come from the audit. Do them in this order. Run the unit tests after each one.

### 2. Launcher label
`AndroidManifest.xml`: remove `android:label="Readings"` from `MainActivity`, so the home-screen icon uses the app label `Blob-log`. In `MainActivity.onCreate`, set `title = "Readings"` so the screen title is unchanged.

### 3. BootReceiver accepts any sender
In `BootReceiver.onReceive`, return early unless `intent.action` is `Intent.ACTION_BOOT_COMPLETED` or `Intent.ACTION_MY_PACKAGE_REPLACED`. Comment the reason in one line: any app can send an explicit intent, and repeated scan starts get throttled.

### 4. Silent failure on delete or reassign
In `MainActivity.assignTo` and `MainActivity.delete`, when `HealthConnect.remove` returns false, show `Toast.makeText(this, "Couldn't update Health Connect. Try again.", Toast.LENGTH_LONG)` before returning.

### 9. SQL built from string templates (`Db.kt`)
Pass every value as a bind argument (`?` with `selectionArgs`) instead of interpolating `$id`, `${me.id}` and `$limit`. The `readings(...)` helper can take a where-clause with `?` placeholders plus an args array. `LIMIT ?` works as a bind argument in Android SQLite. Keep the behaviour identical.

### 10. Redundant manifest attribute
Remove `tools:targetApi="s"` from the `BLUETOOTH_SCAN` permission. Remove the `xmlns:tools` namespace if nothing else uses it.

### 12. Scan script header (`tools/scan_scale.py`)
Replace the "Throwaway" first line with a short docstring. It should say what the script does (scans BLE for Yolanda/QN broadcast scales and prints their manufacturer data, decoding weight), how to run it (`uv run --with bleak python tools/scan_scale.py [seconds]`, default 180), that on macOS it must run from Terminal.app so it gets Bluetooth permission, and what a supported scale looks like in the output (`mfr 0xffff: aa bb ...` lines). Don't change the logic.

### 5. Database tests (Robolectric)
- Add `testImplementation("org.robolectric:robolectric:4.18.1")` and `testImplementation("androidx.test:core:1.7.0")`. In `android { testOptions { unitTests.isIncludeAndroidResources = true } }`. If 4.18.1 can't run SDK 36, annotate the test class with `@Config(sdk = [35])` (or the highest SDK it supports) and report it.
- New `app/src/test/java/app/bloblog/DbTest.kt` using `@RunWith(RobolectricTestRunner::class)` and `ApplicationProvider.getApplicationContext()`. Write a fresh DB for each test (delete `bloblog.db` in `@Before`). Tests:
  1. **Dedupe:** a final packet saved at t=0, the same packet at t=30 s, and the same packet through a *second* `Db` instance at t=60 s. Expect exactly one row. Build packets with `ScalePacket(id, 0x23, 6765)`.
  2. **Reference weights:** add a person with start weight 6630. `refs()` returns 6630. Save a reading of 6700 and `setPerson` it to that person, then `refs()` returns 6700.
  3. **Pending for me:** a reading assigned to the `is_me` person shows in `pendingForMe()`. After `markWritten(id, true)` it doesn't.
  4. **Upgrade v1→v2:** use `SQLiteDatabase.openOrCreateDatabase` on `context.getDatabasePath("bloblog.db")`. Create the v1 table exactly as v1 did: `reading (id INTEGER PRIMARY KEY, taken_at INTEGER NOT NULL, weight_centi INTEGER NOT NULL, scale_id TEXT NOT NULL, raw_hex TEXT NOT NULL)`. Insert one row, set `version = 1` and close. Then `Db(context).latest()` returns that row with `personId == null`, and `pendingForMe()` is empty.

## Done when
The full check passes and the report lists:
- files changed
- test counts
- lint error and warning counts
- any deviation, and why
