# Pushvia: File Transfer & Sync

Move files between your Android phone and Windows PC over your own network: no cloud, no account.
Native Android app (Kotlin / Jetpack Compose) and Windows companion (Rust / Tauri 2 / React).

The interface is in English by default. Indonesian is available in **Settings → General → Language** on both apps.

## Download

- **Windows:** https://github.com/fareza777/local-drop-file/releases/latest (`Pushvia-Windows-x64-setup.exe`, or the `.msi`)
- **Android:** install `app-debug.apk` from a build, or the `Pushvia-Android-debug.apk` attached to a release. It is a test build, not a signed Play release.

## Start here

### Phone ↔ PC (pairing)
1. On Android open **Home → Browser → Start sharing**. Grant All files access if asked, return, and start again.
2. On Windows open **Files**. Your phone appears automatically on the same Wi-Fi; press **Pair**. Over a VPN, type `address:port` instead.
3. Approve on the phone, then type the six-digit PIN on the PC.
4. Click files in either pane, then **Send to phone** or **Save to PC**. Transfers run in a queue: pause, resume, cancel or retry them in **Transfers**. Interrupted uploads and downloads continue where they stopped.

### Phone → PC (Send to PC)
1. On Windows open **Receive → Start receiving** and **Copy link** (the link contains a private token).
2. On Android choose **Send to PC**, paste the link, pick files and send. Closing the window keeps receiving in the system tray.

### Browser only (no desktop app)
Start sharing on the phone and scan the QR code from any browser on the same network. Keep the link private.

### Different networks
Install one VPN on both devices (Tailscale or ZeroTier), join the same network, then use the VPN address and port. Pushvia does not install or configure a VPN.

## Features

- Side-by-side phone and PC file browser with search, drag and drop, and per-file progress, speed and time left.
- Resumable chunked transfers with SHA-256 verification; empty files and large files (up to 8 GiB) are supported.
- Automatic phone discovery on the local network (UDP broadcast); manual address for VPNs.
- PIN protection with lockout after repeated wrong PINs; pairing requires approval on the phone.
- Camera photo backup to the PC (WorkManager): only new photos and videos, retried until they reach the PC.
- Light, dark and system themes; compact rows; English and Indonesian.
- System tray receiver, optional start with Windows.

## Build

### Android
JDK 17, Android SDK 36, AGP 8.13, Gradle 8.14.3:

```bash
./gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
./gradlew.bat :app:connectedDebugAndroidTest   # needs a device or emulator
```

If the ignored Gradle wrapper JAR is missing after cloning, use an installed Gradle 8.14.3 or regenerate the wrapper.

### Desktop
Node 24, Rust stable, Windows C++ build tools and WebView2:

```bash
cd desktop/frontend && npm ci && npm test && npm run build
cd .. && cargo test --locked
node frontend/node_modules/@tauri-apps/cli/tauri.js build --ci
cd frontend && npm run release:prepare
```

`desktop/release/` then holds the NSIS installer, MSI, `SHA256SUMS.txt` and `INSTALL.txt`. Installers are unsigned, so Windows SmartScreen may warn.
`npm run shots` renders every screen with a demo bridge (`?demo=1`) for design review. `npm run test:native` drives the packaged app against a real phone; see the header of `scripts/native-smoke.mjs`.

### CI and releases
`.github/workflows/ci.yml` runs the frontend, Rust and Android tests on every push. Pushing a tag such as `v1.1.0` runs `release.yml`, which builds the Windows installers and a debug APK and attaches them to the GitHub release.

## Ads

Debug builds use Google's sample AdMob IDs. UMP consent is checked before any ad request. Banners appear only on an idle History screen; an interstitial needs three successful pushes, a five-minute cooldown and a user-initiated return home. Release ads are disabled until real IDs, a privacy policy and consent messages are configured.

## Security and publishing

**Local HTTP transfer is not encrypted by Pushvia.** Pairing, PIN and token checks authorize access; they do not encrypt traffic. Use trusted Wi-Fi or an encrypted VPN, and never expose the receiver to the public internet.

The app keeps `MANAGE_EXTERNAL_STORAGE` by product choice; Google Play requires a declaration and may reject it. See [RELEASE_CHECKLIST.md](RELEASE_CHECKLIST.md) and [POLISH_VERIFICATION.md](POLISH_VERIFICATION.md).
