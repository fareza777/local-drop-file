# Desktop frontend bridge (Tauri v2)

Import `invoke` from `@tauri-apps/api/core` in the UI. Command argument names are camelCase at the JS boundary (Tauri maps Rust snake_case names).

```ts
invoke('discover', { address: string | null })
invoke('pair_request', { base, fingerprint, deviceId }) // returns request_id and PIN for explicit peer confirmation
invoke('verify_pair', { base, requestId, pin }) // returns access_token after peer approval
invoke('list_files', { base, token })
invoke('send_file', { base, token, path, transferId })
invoke('receive_file', { base, token, fileId, destination })
invoke('set_transfer_paused', { transferId, paused })
invoke('transfer_paused', { transferId: string })
```

## Build prerequisites

Rust stable MSVC is required. On Windows, install the Visual Studio C++ workload and Windows 10/11 SDK so `kernel32.lib` and the MSVC linker are available. The current machine has `rustc` and `cargo` plus `link.exe`, but no Windows SDK library path, so `cargo check` cannot link build scripts yet. The Android APK and frontend build are independently verified.

`discover` currently probes a manually entered Android origin. UDP broadcast is sent for future responders; Android health probing is the reliable path today.
