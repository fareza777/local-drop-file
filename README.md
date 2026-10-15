# LocalDrop

A native Android app (Kotlin / Jetpack Compose) and Windows companion (Rust / Tauri 2 / React) for file transfer on your own network.

## Start here

### Android → browser (no desktop installation)
1. Put both devices on the same trusted Wi-Fi.
2. In Android, open **Home → Browser → Start sharing**. Grant All files access if prompted, return to LocalDrop and start again.
3. On the other device, scan the QR or open the **full copied link**. The link contains an access token; keep it private.
4. Browse, upload or download in the browser. Stop sharing when finished.

Browser Share exposes shared phone storage, not the app's private folder. Android may restrict some folders even with All files access.

### Windows ↔ Android
1. On Android, start **Browser Share**.
2. In Desktop **Files**, enter the phone address and port, then choose **Connect → Pair device**.
3. Approve the request on Android. Enter the six-digit phone PIN in Desktop.
4. Open folders with a click. Select files in either pane, then choose **Send to phone** or **Save to PC**. Multiple downloads use ZIP (up to 500 files).

Desktop pairing tokens expire when phone sharing restarts. Forgetting a desktop session clears that computer's saved token; removing the computer in Android **Devices** revokes its current paired access.

### Android → PC (Push to PC)
1. In Desktop **Receive**, choose a folder and **Start receiving**.
2. Copy the full private receive link, including `?t=` and its token.
3. On Android, choose **Home → Send to PC**, paste the link, pick files and send.
4. Keep Desktop open. Windows startup is an optional preference, not a background service.

### Different networks
Choose one VPN, install on both devices, then join the same private network:
- Tailscale: https://tailscale.com/download — sign in to the same account.
- ZeroTier: https://www.zerotier.com/download/ — join the same network and authorize both devices.

Use the device's reachable VPN address and LocalDrop port. LocalDrop does not install/configure the VPN. Same-Wi-Fi transfers do not require one. VPN providers may relay encrypted traffic.

## Appearance and help

- Mobile: **Settings → General → System / Light / Dark**.
- Desktop: **Settings → Appearance** for theme and compact file rows.
- Mobile settings use General / Transfer / Privacy / Help categories.
- Desktop settings use Appearance / Receiving / Privacy / Help & downloads.
- Four-page onboarding has persistent Back / Continue / Skip actions. Scrolling remains available on small screens or at large accessibility font sizes, rather than clipping content.
- Desktop uses viewport-bounded panes. At 1180×780, the initial workspace and theme settings fit without page scrolling; file lists scroll independently.

## Build

### Android
JDK 17, Android SDK 36, AGP 8.13.0, Gradle 8.14.3. Install SDK platform/build tools using Android Studio. On Windows:

```bash
./gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
./gradlew.bat :app:connectedDebugAndroidTest
```

The connected tests require a running Android device/emulator. If the locally ignored Gradle wrapper JAR is unavailable after cloning, use a separately installed Gradle 8.14.3 or regenerate the wrapper; no wrapper binary is added to version control here.

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`. It is for testing, not a signed Play release.

### Desktop
Node 24, npm, Rust stable, Windows C++ build tools and WebView2:

```bash
cd desktop/frontend
npm ci
npm test
npm run build
cd ..
cargo test --locked
node frontend/node_modules/@tauri-apps/cli/tauri.js build --ci
cd frontend
npm run release:prepare
```

The Tauri build hook rebuilds frontend assets automatically. Prepared artifacts in `desktop/release/`:
- `LocalDrop-Windows-x64-setup.exe` — recommended per-user NSIS installer.
- `LocalDrop-Windows-x64.msi` — alternative Windows Installer package.
- `SHA256SUMS.txt` and `INSTALL.txt`.

The installer downloads WebView2 when missing, so that case requires internet. Packages are unsigned unless publisher signing is separately configured. Installation on a clean PC and SmartScreen reputation are not established by a successful build.

### Packaged-app smoke test
`npm run test:native` uses Playwright to attach to a real isolated WebView2 instance. It does not mock the transfer bridge. Prerequisites: emulator `emulator-5580`, phone Browser Share active and forwarded to host port 55252, test-only shared-storage permission, and a freshly built Desktop launched with an isolated `WEBVIEW2_USER_DATA_FOLDER` and `WEBVIEW2_ADDITIONAL_BROWSER_ARGUMENTS=--remote-debugging-port=9224`. The fixture uses `C:/Android/Sdk/platform-tools/adb.exe` and this checkout's own test folder; adapt the environment for another machine. Only run it on test devices.

## Ads

Debug builds use official Google sample IDs:
- App: `ca-app-pub-3940256099942544~3347511713`
- Adaptive banner: `ca-app-pub-3940256099942544/9214589741`
- Interstitial: `ca-app-pub-3940256099942544/1033173712`

UMP consent is checked before ads initialization/requests. Privacy options appear when UMP requires them. Banners appear only in idle History, away from transfer controls. Interstitial eligibility requires three successful Push batches, a five-minute minimum cooldown and a user-initiated return home, with no active server, push or pairing. No ad is shown at launch, exit, during onboarding or active transfers. Release ads are intentionally disabled until real app/unit IDs, privacy policy and consent messages are configured. No production credentials were added.

## Security and publishing

**Local HTTP transfer is not encrypted by LocalDrop.** Pairing/PIN/token checks authorize access; they do not add encryption. Use trusted Wi-Fi or an encrypted VPN. Never expose the receiver to the public internet or share private token links publicly.

The app retains `MANAGE_EXTERNAL_STORAGE` by explicit product choice. This permission requires Google Play declaration/review and may be rejected for a general file-transfer app. Target API 36 does not guarantee Play acceptance. Automatic media backup, actual VPN routing, Android 8–14/16 behavior, foreground-service longevity and full-screen interstitial rendering still need broader device testing. There are existing lint/compiler warnings; see the verification report.

Nothing is published automatically. See [release checklist](RELEASE_CHECKLIST.md) and [verification report](POLISH_VERIFICATION.md) before distributing or submitting to Play.
