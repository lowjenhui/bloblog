<p align="center"><img src="docs/icon-source.webp" width="160" alt="Blob-log icon: a smiling blob on a bathroom scale holding a notepad"></p>

# Blob-log

A small Android app that catches weigh-ins from a Yolanda / "feelfit" Bluetooth bathroom scale in the background and saves them to **Health Connect**. You don't need the vendor app, and nothing goes to the cloud.

Step on the scale and the weight lands in Health Connect. You don't even have to open the app.

## Status

**v0.2. Works on the owner's phone.**

- Background capture with the app closed and the screen locked
- One reading per weigh-in, stored in the app's own database with the raw scale bytes
- Readings are assigned to the phone's owner by closest weight. Anything that isn't close, such as a guest, is held back and the app asks "Whose weigh-in?"
- The owner's readings are written to Health Connect, with a retry when the app opens if a write fails
- Next milestone: a second person, whose readings go to Apple Health

Body fat isn't supported: this scale doesn't broadcast impedance (see `SPEC.md`, known gap 1).

## How it works

The scale only broadcasts and is never connected to. Every reading is in its Bluetooth advertisement: manufacturer ID `0xFFFF` and header `AA BB`, with weight as a little-endian uint16 in 0.01 kg at bytes 17–18 and a "locked" flag (`0x20`) at byte 15. The app registers a system-held BLE scan filtered on that header, so Android wakes it only for scale packets, even after a reboot.

```
scale ──BLE broadcast──▶ ScanReceiver ─▶ SQLite ─▶ assign() ─▶ Health Connect (owner)
                                                          └──▶ "Whose weigh-in?" notification
```

The byte layout, the rules and the exact screen text are in [SPEC.md](SPEC.md). A real capture lives in [docs/scale-capture-2026-11-22.txt](docs/scale-capture-2026-11-22.txt), and the parser tests decode it.

## Build

Requires JDK 17 and the Android SDK (platform 36). Without Android Studio, on macOS:

```bash
brew install openjdk@17 && brew install --cask android-commandlinetools android-platform-tools
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
sdkmanager "platforms;android-36" "build-tools;36.0.0"
echo "sdk.dir=/opt/homebrew/share/android-commandlinetools" > local.properties
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Install (sideload, Android 12+)

- **USB or wireless debugging:** `adb install -r app/build/outputs/apk/debug/app-debug.apk`
- **No cable:** upload the APK to Google Drive, open it on the phone and allow "install unknown apps".

On first launch the app asks for your name and current weight, then Bluetooth, notification and Health Connect permissions.

## Is my scale supported?

Run the scanner on a Mac, from Terminal.app, and step on the scale:

```bash
uv run --with bleak python tools/scan_scale.py 120
```

If it prints `mfr 0xffff: aa bb …` lines with a sensible weight, the app should work with it.

## Docs

- [SPEC.md](SPEC.md): behaviour, protocol, screen text and known gaps (the source of truth)
- [PRD.md](PRD.md): goals, user stories and decisions
- [docs/plans/](docs/plans/): build plans

## Privacy

Readings stay on the phone: in the app's database, and in Health Connect for the owner. The app reads nothing from Health Connect and makes no network calls. It doesn't request internet permission.

## Credits

- The scale's broadcast format follows the QN/Yolanda scale support in [openScale](https://github.com/oliexdev/openScale), an open-source scale app that reverse-engineered these scales. Blob-log's parser was written independently from a capture of a real scale and shares no code with openScale.
- Built with [Claude Code](https://claude.com/claude-code). The plans the code was built from are in [docs/plans/](docs/plans/).

## Disclaimer

Blob-log is a personal hobby project. It isn't affiliated with or endorsed by Yolanda, feelfit, Google, Fitbit or Apple. It isn't a medical device: don't use it for medical decisions. Only tested with one Yolanda scale and one phone.

## License

[MIT](LICENSE)
