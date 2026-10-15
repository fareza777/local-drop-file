# LocalDrop polish & verification

Date: 13 October 2026. No commit, push, release upload or Play publication performed.

## Delivered

- Graphite/platinum interface with electric-blue accents, not a green theme. System / Light / Dark settings on both platforms.
- Larger readable typography: desktop primary UI copy at 14px; mobile body/caption styles enlarged. Accessible font scaling is retained.
- Compact categorized settings, actual trusted-device navigation, private receive-link validation, shareable official desktop release-page links, and optional VPN setup/download help.
- Four short onboarding pages and a brief branded splash replacing the emoji placeholder. Navigation remains visible; small screens / large text can scroll rather than losing controls.
- Side-by-side desktop file panes with independently scrolling lists, keyboard focus feedback, native drag/drop subscription and visible error/transfer states.
- Official AdMob debug test app/banner/interstitial IDs with UMP gating. Release ad requests remain disabled.
- Windows NSIS per-user installer and MSI generated; named release artifacts and SHA-256 manifest prepared locally.

## Bugs found and corrected

1. Desktop Android selection used display/path values instead of API `file_id`, breaking selected downloads.
2. Desktop pairing requested a 30-day token from a factory restricted to one hour; approved pairing therefore failed.
3. Removing an Android trusted device did not invalidate its already-issued paired token; authentication now checks device ownership/trust.
4. Desktop upload allocated a 1-MiB hashing buffer on the Windows UI stack and crashed the packaged app. Buffer moved to heap; send/receive work moved to blocking workers rather than the UI thread. A 512-KiB-stack regression test now covers upload.
5. Android chunk downloads rejected normal final short chunks and nested paths; response ranges are now bounded to remaining bytes, paths validated, malformed offsets rejected.
6. Desktop receiver stop left its listener occupied on Windows. Stop now joins the worker and explicitly wakes the loopback accept thread. Repeated start/stop on the same test port verified.
7. Receiver snapshot fields were snake_case while GUI consumed camelCase; serialization now matches the UI.
8. Settings had an inactive trusted-devices row, an incorrect storage description and misleading encryption/no-server/mandatory-VPN claims. These now reflect actual behavior.
9. Share-sheet setup deep link previously went to Settings instead of Push mode; now opens Send to PC.
10. Splash theme API-specific attributes caused lint errors on minSdk 26; resources now use API-qualified styles. AGP/Gradle upgraded to a combination supporting API 36.

## Verification results

| Check | Result |
|---|---|
| Desktop frontend build | PASS |
| Frontend helper tests | 7/7 PASS; helper-file line coverage 100%, branch coverage 86.49% (not whole-app coverage) |
| npm audit | 0 vulnerabilities reported |
| Rust tests after final receiver repair | 3/3 PASS |
| Tauri release build | PASS; Windows setup + MSI |
| Android Kotlin compilation / debug APK | PASS |
| Android unit/integration tests | 6/6 PASS |
| Android Compose emulator tests | 2/2 PASS on API 35 x86_64 |
| Android lint | PASS gate; 0 errors, 40 warnings remain |
| Packaged native GUI smoke test | PASS: 6 checked workflows, no recorded page errors |
| Installer SHA-256 manifest | Verified against prepared artifacts |
| Desktop browser console | No application error observed during inspected interactions |
| Desktop 1180×780 initial page + theme settings | No page overflow; internal file list scrolling remains |
| Android home & general settings on test emulator | Primary controls visible without scrolling |
| Actual debug adaptive banner | “Test Ad” rendered in idle History |
| Interstitial full-screen rendering | NOT exercised; eligibility/cooldown guard unit-tested only |

### Real interface testing

The native smoke test attached to the **actual packaged Windows WebView2 app**, not a mock React bridge. It discovered Android through an ADB-forwarded local connection, clicked Pair, approved on the emulator, entered the phone PIN, browsed local folders, selected a dedicated file and uploaded it. Phone bytes were compared exactly; the native download bridge then returned identical bytes. Transfer status, dark theme, compact rows, receiver link, stop and same-port restart were tested.

The standalone save dialog and a physical-PC-to-physical-phone LAN path were not automated. Native receiver used an isolated test port and folder; no production receiver data was modified by this workflow.

- [Native smoke result](verification/native-smoke.json)
- [Paired desktop workspace](verification/desktop-paired.png)
- [Desktop dark settings](verification/desktop-settings-dark.png)
- [Desktop receiver](verification/desktop-receive.png) — private receive token masked
- [Android home](verification/android-home.png)
- [Android settings](verification/android-settings.png)
- [Android unit test report](app/build/reports/tests/testDebugUnitTest/index.html)
- [Android UI test report](app/build/reports/androidTests/connected/debug/index.html)
- [Android lint report](app/build/reports/lint-results-debug.html)

## Artifacts

- [Android debug APK](app/build/outputs/apk/debug/app-debug.apk)
- [Windows setup installer](desktop/release/LocalDrop-Windows-x64-setup.exe)
- [Windows MSI](desktop/release/LocalDrop-Windows-x64.msi)
- [SHA-256 checksums](desktop/release/SHA256SUMS.txt)
- [Installation note](desktop/release/INSTALL.txt)

## Remaining limitations / release blockers

- **Not certified Play-ready.** All files access remains by explicit user choice and needs policy approval. Target API 36 is set, but signed AAB, Play declaration, public privacy policy/Data safety and production consent configuration are still required.
- Local transfer uses HTTP, not LocalDrop end-to-end encryption. Use trusted Wi-Fi or an encrypted VPN. Tokens authorize access but do not encrypt files.
- Real VPN connections, slow/unstable networks, paused/resumed large batches, empty/over-100-MiB files, ZIP save dialog, permission revocation and API 26–34/36 device behavior require more testing.
- Automatic media backup is existing alarm-based code and is **not verified reliable**. Audit scheduling time bases, receiver declaration, Android background-service restrictions and failed-batch timestamp handling before marketing it as guaranteed camera backup.
- Foreground-service long-running behavior and API 35+ timeouts require a separate runtime hardening pass. No unlimited background-transfer guarantee is made.
- Android lint's 40 warnings include restricted storage access, old dependencies, splash duplication warning, legacy API/locale/icon issues and existing code advisories. Rust emits existing unused-code warnings.
- The custom short splash can follow the Android 12+ system splash; this was not fully replaced with a single system-managed animated splash.
- Banner rendered on the test emulator; consent-region/revoke flows and full-screen interstitial presentation still need device tests. Debug test ads are not proof of production monetization setup.
- A minimum five-minute interstitial cooldown is a product safety policy, not a claim that Google mandates that exact duration.
- Windows installers are unsigned. Clean-machine installation, publisher signing and SmartScreen reputation remain unverified.
- The GitHub release-page link is configured, but **no live download release has been published or verified**. Desktop distribution checklist: [RELEASE_CHECKLIST.md](RELEASE_CHECKLIST.md).

The dev-server Preview remains available for design review; native transfers require the packaged app.
