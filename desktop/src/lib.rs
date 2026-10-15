mod discovery;
mod pairing;
mod receiver;
mod service;
mod transfer;

use serde_json::Value;
use std::{collections::HashMap, path::Path, sync::{Arc, Mutex, atomic::{AtomicBool, Ordering}}};
use tauri::State;

#[derive(Default)] struct RuntimeState(Mutex<HashMap<String, Arc<AtomicBool>>>);

// ---- Push to PC: this computer becomes the receiver the phone pushes into. ----
#[tauri::command]
fn receiver_info(state: State<'_, receiver::Receiver>) -> receiver::ReceiverInfo { state.info() }
#[tauri::command]
fn receiver_start(app: tauri::AppHandle, state: State<'_, receiver::Receiver>, dest: Option<String>, port: Option<u16>) -> Result<receiver::ReceiverInfo, String> {
    let dest = dest.map(std::path::PathBuf::from)
        .unwrap_or_else(|| std::env::var("USERPROFILE").map(|h| std::path::PathBuf::from(h).join("Downloads").join("LocalDrop")).unwrap_or_else(|_| std::path::PathBuf::from("LocalDrop")));
    state.start(app, dest, port.unwrap_or(42198))
}
#[tauri::command]
fn receiver_stop(state: State<'_, receiver::Receiver>) -> receiver::ReceiverInfo { state.stop(); state.info() }

// ---- "Siap otomatis": start LocalDrop with Windows so the phone can push without touching the PC. ----
const RUN_KEY: &str = r"HKCU\Software\Microsoft\Windows\CurrentVersion\Run";
const RUN_VAL: &str = "LocalDrop Desktop";

#[tauri::command]
fn autostart_get() -> bool {
    std::process::Command::new("reg")
        .args(["query", RUN_KEY, "/v", RUN_VAL])
        .output()
        .map(|o| o.status.success())
        .unwrap_or(false)
}

#[tauri::command]
fn autostart_set(enable: bool) -> Result<bool, String> {
    if enable {
        let exe = std::env::current_exe().map_err(|e| e.to_string())?.to_string_lossy().into_owned();
        let out = std::process::Command::new("reg")
            .args(["add", RUN_KEY, "/v", RUN_VAL, "/d", &format!("\"{}\"", exe), "/f"])
            .output()
            .map_err(|e| e.to_string())?;
        if !out.status.success() {
            return Err(String::from_utf8_lossy(&out.stderr).trim().to_string());
        }
    } else {
        // Ignore failure when the value is already gone.
        let _ = std::process::Command::new("reg")
            .args(["delete", RUN_KEY, "/v", RUN_VAL, "/f"])
            .output();
    }
    Ok(autostart_get())
}

#[tauri::command]
fn list_local(path: Option<String>) -> Result<Value, String> { service::list_local(path.as_deref().unwrap_or("")) }
#[tauri::command]
fn pick_folder(app: tauri::AppHandle) -> Result<Value, String> {
    use tauri_plugin_dialog::DialogExt;
    // Ask once for a destination folder; every selected file is saved into it.
    match app.dialog().file().blocking_pick_folder() {
        Some(path) => Ok(serde_json::json!(path.to_string())),
        None => Ok(Value::Null),
    }
}
#[tauri::command]
fn discover(address: Option<String>) -> Result<Vec<service::DeviceInfo>, String> { service::discover(address) }
#[tauri::command]
fn pair_request(base: String, fingerprint: String, device_id: String) -> Result<Value, String> { service::pair_request(&base, &fingerprint, &device_id) }
#[tauri::command]
fn verify_pair(base: String, request_id: String, pin: String) -> Result<Value, String> { service::verify_pair(&base, &request_id, &pin) }
#[tauri::command]
fn list_files(base: String, token: String, path: Option<String>) -> Result<Value, String> { service::list_files(&base, &token, path.as_deref().unwrap_or("")) }
#[tauri::command]
fn create_folder(base: String, token: String, path: String, name: String) -> Result<Value, String> { service::create_folder(&base, &token, &path, &name) }
#[tauri::command]
fn delete_path(base: String, token: String, path: String) -> Result<Value, String> { service::delete_path(&base, &token, &path) }
#[tauri::command]
async fn send_file(base: String, token: String, path: String, transfer_id: Option<String>, remote_path: Option<String>, state: State<'_, RuntimeState>) -> Result<Value, String> {
    let id = transfer_id.clone().unwrap_or_else(|| path.clone());
    let cancelled = Arc::new(AtomicBool::new(false));
    state.0.lock().map_err(|e| e.to_string())?.insert(id.clone(), cancelled.clone());
    let result = tauri::async_runtime::spawn_blocking(move || service::send_file(&base, &token, &path, transfer_id.as_deref(), remote_path.as_deref(), &cancelled)).await.map_err(|e| e.to_string());
    state.0.lock().map_err(|e| e.to_string())?.remove(&id);
    result?
}
#[tauri::command]
async fn receive_file(app: tauri::AppHandle, base: String, token: String, file_id: String, destination: String, transfer_id: Option<String>) -> Result<Value, String> {
    use tauri::Emitter;
    let id = transfer_id.unwrap_or_default();
    // Report progress after each chunk so large files show a live percentage.
    let on_progress = move |done: u64, total: u64| {
        let _ = app.emit("transfer-progress", serde_json::json!({"id": id, "done": done, "total": total}));
    };
    tauri::async_runtime::spawn_blocking(move || service::receive_file(&base, &token, &file_id, &destination, on_progress)).await.map_err(|e| e.to_string())?
}
#[tauri::command]
fn download_zip(app: tauri::AppHandle, base: String, token: String, paths: Vec<String>, estimated_total: Option<u64>, transfer_id: Option<String>) -> Result<Value, String> {
    use tauri::Emitter;
    use tauri_plugin_dialog::DialogExt;
    if paths.is_empty() { return Err("Select at least one file.".into()); }
    // The Android zip endpoint keeps the first 500 paths; refuse beyond that so nothing is silently dropped.
    if paths.len() > 500 { return Err(format!("{} files selected. One ZIP holds up to 500 files.", paths.len())); }
    // One save dialog, then the ZIP streams straight to the chosen file.
    let downloads = std::env::var("USERPROFILE").ok().map(|h| format!("{}\\Downloads", h)).filter(|d| Path::new(d).is_dir());
    let mut dialog = app.dialog().file().set_file_name("localdrop-files.zip");
    if let Some(dir) = downloads { dialog = dialog.set_directory(dir); }
    let destination = match dialog.blocking_save_file() {
        Some(path) => path.to_string(),
        None => return Ok(Value::Null), // user cancelled
    };
    let id = transfer_id.unwrap_or_default();
    let total = estimated_total.unwrap_or(0);
    let on_progress = move |done: u64| {
        let _ = app.emit("transfer-progress", serde_json::json!({"id": id, "done": done, "total": total}));
    };
    service::download_zip(&base, &token, &paths, &destination, on_progress)
}
#[tauri::command]
fn set_transfer_paused(transfer_id: String, paused: bool, state: State<'_, RuntimeState>) -> Result<(), String> {
    let transfers = state.0.lock().map_err(|e| e.to_string())?;
    let flag = transfers.get(&transfer_id).ok_or("transfer not found")?;
    flag.store(paused, Ordering::Relaxed);
    Ok(())
}
#[tauri::command]
fn transfer_paused(transfer_id: String, state: State<'_, RuntimeState>) -> bool {
    state.0.lock().ok().and_then(|s| s.get(&transfer_id).map(|p| p.load(Ordering::Relaxed))).unwrap_or(false)
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default().plugin(tauri_plugin_dialog::init()).manage(RuntimeState::default()).manage(receiver::Receiver::new()).invoke_handler(tauri::generate_handler![discover, list_local, pick_folder, pair_request, verify_pair, list_files, create_folder, delete_path, send_file, receive_file, download_zip, set_transfer_paused, transfer_paused, receiver_info, receiver_start, receiver_stop, autostart_get, autostart_set]).run(tauri::generate_context!()).expect("failed to run LocalDrop Desktop");
}
