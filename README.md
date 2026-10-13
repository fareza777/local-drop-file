# LocalDrop

Local-network file transfer with an Android Jetpack Compose app and Rust desktop companion scaffold.

## Structure
- `app/` — Android app, Material 3 UI, branded splash/icon, Browser Share HTTP server, SAF picker, transfer/device/settings models, token and auto-stop primitives.
- `desktop/` — Rust core modules and minimal Vite/React UI scaffold.
- `protocol/README.md` — shared HTTP and security contract.

## Android build
Requires JDK 17+, Android SDK 35, Android Gradle Plugin 8.7.3, and Gradle 8.9 or newer. This machine has SDK 35 and cached Gradle 8.2.1, but that Gradle version cannot run the configured Android plugin. Run `gradlew :app:assembleDebug` from Android Studio after syncing with Gradle 8.9+.

## Implemented baseline
- Compose onboarding, dashboard, share, history, and settings.
- Browser Share with QR payload, token-authenticated HTTP, bounded streaming upload/download, and file listing.
- SAF multi-file selection copied into private storage.
- Preferences-backed onboarding/settings/history/trusted-device data.
- Auto-stop when app goes to background or the share screen closes.
- Rust pairing, network-interface classification, resumable chunk I/O and SHA-256 helpers, and local store model.
- Desktop frontend for nearby peers, pairing UX, transfers/history, and trusted-device views.

## Build status
- Android `:app:assembleDebug` passes with Gradle 8.11.1 (APK at `app/build/outputs/apk/debug/app-debug.apk`).
- Desktop frontend `npm run build` passes.
- Desktop native builds pass: `cargo build --release` and `npx --prefix frontend tauri build` (run from `desktop/`) produce `target/release/localdrop-desktop.exe` plus MSI/NSIS installers in `target/release/bundle/`.

The native desktop bridge now has separate pairing request/verification commands, health discovery, chunked send/receive, SHA-256 verification, and a real GUI wiring through Tauri invoke. Cross-device validation still requires an Android device plus a PC on the same network.
