mod receiver;
mod service;

use serde_json::{json, Value};
use service::Ctl;
use std::{collections::HashMap, path::Path, sync::{Arc, Mutex, atomic::Ordering}};
use tauri::{Emitter, Manager, State};

#[derive(Default)] struct RuntimeState(Mutex<HashMap<String, Arc<Ctl>>>);

impl RuntimeState {
    fn register(&self, id: &str) -> Result<Arc<Ctl>, String> {
        let ctl = Arc::new(Ctl::default());
        self.0.lock().map_err(|e| e.to_string())?.insert(id.to_string(), ctl.clone());
        Ok(ctl)
    }
    fn release(&self, id: &str) { if let Ok(mut m) = self.0.lock() { m.remove(id); } }
}

// ---- Push to PC: this computer becomes the receiver the phone pushes into. ----
#[tauri::command]
fn receiver_info(state: State<'_, receiver::Receiver>) -> receiver::ReceiverInfo { state.info() }
#[tauri::command]
fn receiver_start(app: tauri::AppHandle, state: State<'_, receiver::Receiver>, dest: Option<String>, port: Option<u16>) -> Result<receiver::ReceiverInfo, String> {
    let dest = dest.map(std::path::PathBuf::from)
        .unwrap_or_else(|| std::env::var("USERPROFILE").map(|h| std::path::PathBuf::from(h).join("Downloads").join("Pushvia")).unwrap_or_else(|_| std::path::PathBuf::from("Pushvia")));
    state.start(app, dest, port.unwrap_or(42198))
}
#[tauri::command]
fn receiver_stop(state: State<'_, receiver::Receiver>) -> receiver::ReceiverInfo { state.stop(); state.info() }

// ---- "Siap otomatis": start Pushvia with Windows so the phone can push without touching the PC. ----
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
fn pick_files(app: tauri::AppHandle) -> Result<Vec<String>, String> {
    use tauri_plugin_dialog::DialogExt;
    Ok(app.dialog().file().blocking_pick_files().unwrap_or_default().into_iter().map(|p| p.to_string()).collect())
}
#[tauri::command]
fn open_path(path: String) -> Result<(), String> {
    let p = Path::new(&path);
    let target = if p.is_dir() { p.to_path_buf() } else { p.parent().map(|x| x.to_path_buf()).ok_or("invalid path")? };
    if !target.is_dir() { return Err("folder not found".into()); }
    if p.is_file() {
        std::process::Command::new("explorer").arg(format!("/select,{}", p.display())).spawn().map_err(|e| e.to_string())?;
    } else {
        std::process::Command::new("explorer").arg(&target).spawn().map_err(|e| e.to_string())?;
    }
    Ok(())
}
#[tauri::command]
fn discover(address: Option<String>) -> Result<Vec<service::DeviceInfo>, String> { service::discover(address) }
#[tauri::command]
async fn discover_lan() -> Result<Vec<service::DeviceInfo>, String> { tauri::async_runtime::spawn_blocking(|| service::discover_lan(1600)).await.map_err(|e| e.to_string())? }
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

fn emit_progress(app: &tauri::AppHandle, id: &str, done: u64, total: u64) {
    let _ = app.emit("transfer-progress", json!({"id": id, "done": done, "total": total}));
}

#[tauri::command]
async fn send_file(app: tauri::AppHandle, base: String, token: String, path: String, transfer_id: Option<String>, remote_path: Option<String>, state: State<'_, RuntimeState>) -> Result<Value, String> {
    let id = transfer_id.unwrap_or_else(|| path.clone());
    let ctl = state.register(&id)?;
    let progress_id = id.clone();
    let result = tauri::async_runtime::spawn_blocking(move || service::send_file(&base, &token, &path, remote_path.as_deref(), &ctl, |d, t| emit_progress(&app, &progress_id, d, t))).await.map_err(|e| e.to_string());
    state.release(&id);
    result?
}
#[tauri::command]
async fn receive_file(app: tauri::AppHandle, base: String, token: String, file_id: String, destination: String, transfer_id: Option<String>, state: State<'_, RuntimeState>) -> Result<Value, String> {
    let id = transfer_id.unwrap_or_default();
    let ctl = state.register(&id)?;
    let progress_id = id.clone();
    let result = tauri::async_runtime::spawn_blocking(move || service::receive_file(&base, &token, &file_id, &destination, &ctl, |d, t| emit_progress(&app, &progress_id, d, t))).await.map_err(|e| e.to_string());
    state.release(&id);
    result?
}
#[tauri::command]
async fn download_zip(app: tauri::AppHandle, base: String, token: String, paths: Vec<String>, estimated_total: Option<u64>, transfer_id: Option<String>, state: State<'_, RuntimeState>) -> Result<Value, String> {
    use tauri_plugin_dialog::DialogExt;
    if paths.is_empty() { return Err("Select at least one file.".into()); }
    // The Android zip endpoint keeps the first 500 paths; refuse beyond that so nothing is silently dropped.
    if paths.len() > 500 { return Err(format!("{} files selected. One ZIP holds up to 500 files.", paths.len())); }
    // One save dialog, then the ZIP streams straight to the chosen file.
    let downloads = std::env::var("USERPROFILE").ok().map(|h| format!("{}\\Downloads", h)).filter(|d| Path::new(d).is_dir());
    let mut dialog = app.dialog().file().set_file_name("localdrop-files.zip");
    if let Some(dir) = downloads { dialog = dialog.set_directory(dir); }
    let picked = tauri::async_runtime::spawn_blocking(move || dialog.blocking_save_file()).await.map_err(|e| e.to_string())?;
    let Some(destination) = picked.map(|p| p.to_string()) else { return Ok(Value::Null) };
    let id = transfer_id.unwrap_or_default();
    let total = estimated_total.unwrap_or(0);
    let ctl = state.register(&id)?;
    let progress_id = id.clone();
    let result = tauri::async_runtime::spawn_blocking(move || service::download_zip(&base, &token, &paths, &destination, &ctl, |done| emit_progress(&app, &progress_id, done, total.max(done)))).await.map_err(|e| e.to_string());
    state.release(&id);
    result?
}
#[tauri::command]
fn set_transfer_paused(transfer_id: String, paused: bool, state: State<'_, RuntimeState>) -> Result<(), String> {
    let transfers = state.0.lock().map_err(|e| e.to_string())?;
    transfers.get(&transfer_id).ok_or("transfer not found")?.paused.store(paused, Ordering::Relaxed);
    Ok(())
}
#[tauri::command]
fn cancel_transfer(transfer_id: String, state: State<'_, RuntimeState>) -> Result<(), String> {
    let transfers = state.0.lock().map_err(|e| e.to_string())?;
    transfers.get(&transfer_id).ok_or("transfer not found")?.cancelled.store(true, Ordering::Relaxed);
    Ok(())
}
#[tauri::command]
fn transfer_paused(transfer_id: String, state: State<'_, RuntimeState>) -> bool {
    state.0.lock().ok().and_then(|s| s.get(&transfer_id).map(|c| c.paused.load(Ordering::Relaxed))).unwrap_or(false)
}

fn show_main_window(app: &tauri::AppHandle) {
    if let Some(window) = app.get_webview_window("main") { let _ = window.show(); let _ = window.unminimize(); let _ = window.set_focus(); }
}

fn build_tray(app: &tauri::App) -> tauri::Result<()> {
    use tauri::{menu::{Menu, MenuItem}, tray::{MouseButton, MouseButtonState, TrayIconBuilder, TrayIconEvent}};
    let open = MenuItem::with_id(app, "open", "Open Pushvia", true, None::<&str>)?;
    let quit = MenuItem::with_id(app, "quit", "Quit (stops receiving)", true, None::<&str>)?;
    let menu = Menu::with_items(app, &[&open, &quit])?;
    let mut tray = TrayIconBuilder::new().tooltip("Pushvia Desktop").menu(&menu).show_menu_on_left_click(false)
        .on_menu_event(|app, event| match event.id.as_ref() {
            "open" => show_main_window(app),
            "quit" => { app.state::<receiver::Receiver>().stop(); app.exit(0); }
            _ => {}
        })
        .on_tray_icon_event(|tray, event| {
            if let TrayIconEvent::Click { button: MouseButton::Left, button_state: MouseButtonState::Up, .. } = event { show_main_window(tray.app_handle()); }
        });
    if let Some(icon) = app.default_window_icon() { tray = tray.icon(icon.clone()); }
    tray.build(app)?;
    Ok(())
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_dialog::init())
        .manage(RuntimeState::default())
        .manage(receiver::Receiver::new())
        .setup(|app| { build_tray(app)?; Ok(()) })
        .on_window_event(|window, event| {
            // While receiving, closing the window keeps Pushvia running in the tray instead of silently dropping the phone's upload.
            if let tauri::WindowEvent::CloseRequested { api, .. } = event {
                if window.app_handle().state::<receiver::Receiver>().info().running { api.prevent_close(); let _ = window.hide(); }
            }
        })
        .invoke_handler(tauri::generate_handler![discover, discover_lan, list_local, pick_folder, pick_files, open_path, pair_request, verify_pair, list_files, create_folder, delete_path, send_file, receive_file, download_zip, set_transfer_paused, cancel_transfer, transfer_paused, receiver_info, receiver_start, receiver_stop, autostart_get, autostart_set])
        .run(tauri::generate_context!())
        .expect("failed to run Pushvia Desktop");
}
