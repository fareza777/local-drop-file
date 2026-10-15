# Pushvia verification report (v1.1.0)

Date: 15 October 2026. Device: Xiaomi 2201117TY, Android 13 (real phone). Desktop: Windows 11 with the installed NSIS build.

## What changed in 1.1.0

- Renamed to **Pushvia: File Transfer & Sync** (launcher label "Pushvia"; the Android application ID stays `com.localdrop.app` so updates keep working). English is the default language; Indonesian is optional.
- New desktop UI (light/dark, queue with pause/resume/cancel/retry, speed and time left, search, drag and drop, tray, automatic phone discovery) and a compact Android UI that fits one screen on a 1080x2400 phone without scrolling on Home.
- Desktop received files and history: incoming files appear live in **Receive** and **Transfers** and are kept across restarts. This needed a Tauri capability (`desktop/capabilities/default.json`); without it the packaged app could not subscribe to progress events at all.
- Engine: resumable uploads (`/transfer/status`), `.part` downloads with SHA-256, shared HTTP client with retries, 8 GiB limit, empty files, PIN lockout, validated transfer ids (path traversal fix), WorkManager camera backup with a checkpoint that only advances for files that reached the PC, UDP discovery.

## Results

| Check | Result |
|---|---|
| Desktop frontend tests (`npm test`) | 17/17 pass |
| Desktop Rust tests (`cargo test`) | 8/8 pass (upload, resume, retry, cancel, download resume + hash) |
| Android unit tests | 15/15 pass (9 new server-engine tests) |
| Android debug build | pass |
| Real phone, packaged desktop: pair, list, upload, download | pass (`verification/native/native-smoke.json`) |
| Simulated phone push into the packaged receiver | file appears in Receive and Transfers, persists after restart |
| Layout at 1180x780, 900x620, light and dark | no overlap (`verification/ui`) |

## Not verified / known limits

- No physical PC-to-phone transfer over Wi-Fi: the phone was on mobile data plus Tailscale, so the run used `adb forward`.
- Phone discovery by UDP broadcast was not exercised on a real Wi-Fi network; manual address works.
- Camera backup under WorkManager: scheduling compiles and the logic is unit-level reasoned, but Android may refuse to start the transfer service from the background. Not tested across reboot or Doze.
- Interstitial ads, UMP consent flows, Android 8-12 and 14-16 behavior, large files over 100 MiB, and the ZIP save dialog were not tested.
- HTTP transfers are still unencrypted (no HTTPS yet). Installers are unsigned; SmartScreen may warn.
- Android lint was not re-run for 1.1.0. Play requirements (All files access declaration, signed AAB, privacy policy) are unchanged; see `RELEASE_CHECKLIST.md`.
