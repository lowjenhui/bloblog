# Blob-log

Sideloaded Android app (Kotlin) that catches weigh-ins from a Yolanda broadcast-only BLE scale, assigns each to one of two people, stores them in its own database and writes the owner's weight to Health Connect.

- **Source of truth:** `SPEC.md` (behaviour, protocol, exact screen text, known gaps). `PRD.md` holds stories (US-n) and decisions (D-n).
- **Scale protocol fixture:** `docs/scale-capture-2026-11-22.txt`. Parser tests decode it.
- **Re-capture scale packets on the Mac:** run `tools/scan_scale.py` from macOS Terminal.app (not Claude's shell, where macOS kills Bluetooth access with exit 134):
  `uv run --with bleak python tools/scan_scale.py 120`
- **Build and test:** `export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home` (no system Java), then `./gradlew :app:testDebugUnitTest :app:assembleDebug`. The SDK is at `/opt/homebrew/share/android-commandlinetools` (in the gitignored `local.properties`).
- **Install on the phone:** `adb install -r app/build/outputs/apk/debug/app-debug.apk`. Logs: `adb logcat -s Bloblog`.
- **Model split (owner's rule):** Opus 5.5 (medium effort) plans and reviews. Sonnet 5.5 (medium effort) subagents execute, working from a written plan in `docs/plans/`. Opus reviews each diff and runs the tests before anything is committed. Tell the owner when the split causes a problem.
- Platform SQLite and plain Views only (PRD D-9). Code is in `app/src/main/java/app/bloblog/`.
