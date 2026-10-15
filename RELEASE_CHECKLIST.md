# LocalDrop release checklist

## Recommended desktop distribution

Use one official public download hub: this repository's [GitHub Releases](https://github.com/fareza777/local-drop-file/releases). Nothing was uploaded or published by this task.

1. Build and verify the current version locally; run `npm run release:prepare` from `desktop/frontend`.
2. Test installation/uninstallation on a clean Windows x64 PC, including missing WebView2 and a standard non-administrator account. Confirm receiving stops on close and Windows startup remains opt-in.
3. Sign the executable and installers with the publisher's code-signing identity. Store signing credentials outside source control. Unsigned downloads may trigger SmartScreen; signing alone does not guarantee immediate reputation.
4. Prepare a draft GitHub Release and attach `LocalDrop-Windows-x64-setup.exe`, `LocalDrop-Windows-x64.msi`, `SHA256SUMS.txt`, release notes and installation guidance. Do not publish until explicitly authorized.
5. After publishing a stable release, keep asset names consistent. Share this durable page in Play listing, Android Settings and support material:
   `https://github.com/fareza777/local-drop-file/releases/latest`
6. A one-click setup link is usable only after that exact asset exists in a stable release:
   `https://github.com/fareza777/local-drop-file/releases/latest/download/LocalDrop-Windows-x64-setup.exe`
7. Consider immutable releases and build attestations. Recalculate checksums after signing. Checksums detect corruption; they are not a replacement for publisher signing.

Do not advertise the bare EXE as a fully portable app yet. Its clean-machine WebView2/data-storage behavior has not been verified. MSI and setup artifacts are available locally; they are not yet a live download service.

## Play submission gates

- [ ] Keep target API at least 36 and verify current Play Console requirements before submission.
- [ ] Complete the All files access declaration and establish an eligible core use case. Current product intentionally retains shared-storage browsing. Approval is not guaranteed; if rejected, switch to SAF-selected files/folders rather than bypassing policy.
- [ ] Test storage and media permissions on API 26–36+, including denied/revoked and selected-photo access.
- [ ] Verify Android foreground-service limits, timeout handling and background-start restrictions. Do not promise unlimited screen-locked receiving/backup without these tests.
- [ ] Test automatic media backup success/failure/cancellation, timestamp advancement, reboots and PC-offline retry on actual devices. Current alarm-based backup is not certified reliable.
- [ ] Verify non-empty and empty files, over-100-MiB files, interrupted/resumed transfers, duplicate filenames, multi-file ZIP and large selections. The Android upload protocol currently caps files at 100 MiB and rejects empty desktop uploads.
- [ ] Test Tailscale and ZeroTier on real separate networks; confirm reachability, approved membership and firewall rules.
- [ ] Use Android App Bundle signed with the Play upload key; configure Play App Signing. The debug APK is not a Play-ready deliverable.
- [ ] Provide public privacy policy, Data safety disclosures, support details and accurate screenshots. File traffic and ad SDK traffic have different privacy characteristics.
- [ ] Replace sample AdMob IDs, configure UMP consent messages and complete ad disclosures before enabling release ads. Keep test IDs for debug/testing.
- [ ] Verify EEA/UK/Switzerland consent and privacy-options flows, offline consent errors, under-age policy decisions and two full-screen interstitial cycles. A rendered test banner does not validate these flows.
- [ ] Review remaining lint/compiler warnings and system-launch splash duplication.

## Official references

- [Google Play target API requirements](https://support.google.com/googleplay/android-developer/answer/11926878)
- [Google Play All files access policy](https://support.google.com/googleplay/android-developer/answer/10467955)
- [GMA Next-Gen setup](https://developers.google.com/admob/android/next-gen/quick-start)
- [UMP consent guidance](https://developers.google.com/admob/android/next-gen/privacy)
- [Interstitial placement guidance](https://support.google.com/admob/answer/6066980)
- [Tauri Windows installers](https://v2.tauri.app/distribute/windows-installer/)
- [Tauri Windows signing](https://v2.tauri.app/distribute/sign/windows/)
- [Microsoft SmartScreen reputation](https://learn.microsoft.com/en-us/windows/apps/package-and-deploy/smartscreen-reputation)
- [GitHub release links](https://docs.github.com/en/repositories/releasing-projects-on-github/linking-to-releases)
