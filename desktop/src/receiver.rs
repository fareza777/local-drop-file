use serde_json::{json, Value};
use sha2::{Digest, Sha256};
use std::{
    fs::{self, File, OpenOptions},
    io::{Read, Write},
    net::TcpListener,
    path::{Path, PathBuf},
    sync::{Arc, Mutex},
};
use tauri::{AppHandle, Emitter};
use tiny_http::{Header, Request, Response, Server};

pub const CHUNK_SIZE: usize = 1024 * 1024;

/// Live snapshot of the receiver shown in the desktop UI.
#[derive(Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ReceiverInfo {
    pub running: bool,
    pub address: String,
    pub port: u16,
    pub token: String,
    pub dest: String,
    pub files_done: usize,
    pub files_total: usize,
    pub bytes_done: u64,
    pub bytes_total: u64,
}

#[derive(Default)]
struct Session {
    files_done: usize,
    files_total: usize,
    bytes_done: u64,
    bytes_total: u64,
}

/// Everything the HTTP thread needs, shared with the UI.
pub struct Receiver {
    inner: Mutex<Inner>,
    /// Shared with the HTTP thread so live counters reach the UI.
    session: Arc<Mutex<Session>>,
    /// Kept so stop() can call the official graceful-shutdown unblock().
    server: Mutex<Option<Arc<Server>>>,
    worker: Mutex<Option<std::thread::JoinHandle<()>>>,
}

struct Inner {
    running: bool,
    address: String,
    port: u16,
    token: String,
    dest: PathBuf,
}

use serde::Serialize;

impl Receiver {
    pub fn new() -> Self {
        Self {
            inner: Mutex::new(Inner {
                running: false,
                address: String::new(),
                port: 0,
                token: String::new(),
                dest: PathBuf::new(),
            }),
            session: Arc::new(Mutex::new(Session::default())),
            server: Mutex::new(None),
            worker: Mutex::new(None),
        }
    }

    pub fn info(&self) -> ReceiverInfo {
        let i = self.inner.lock().unwrap();
        let s = self.session.lock().unwrap();
        ReceiverInfo {
            running: i.running,
            address: i.address.clone(),
            port: i.port,
            token: i.token.clone(),
            dest: i.dest.to_string_lossy().into_owned(),
            files_done: s.files_done,
            files_total: s.files_total,
            bytes_done: s.bytes_done,
            bytes_total: s.bytes_total,
        }
    }

    /// Start listening. `port` 0 picks an ephemeral port.
    pub fn start(&self, app: AppHandle, dest: PathBuf, port: u16) -> Result<ReceiverInfo, String> {
        fs::create_dir_all(&dest).map_err(|e| e.to_string())?;
        // Refuse a second start while one is live.
        if self.inner.lock().unwrap().running {
            return Err("receiver already running".into());
        }
        let listener = TcpListener::bind(("0.0.0.0", port)).map_err(|e| e.to_string())?;
        let actual_port = listener.local_addr().map_err(|e| e.to_string())?.port();
        let server = Arc::new(Server::from_listener(listener, None).map_err(|e| e.to_string())?);

        let token = persistent_token();
        let address = local_address(actual_port);

        {
            let mut i = self.inner.lock().unwrap();
            i.running = true;
            i.address = address.clone();
            i.port = actual_port;
            i.token = token.clone();
            i.dest = dest.clone();
        }
        *self.session.lock().unwrap() = Session::default();
        *self.server.lock().unwrap() = Some(server.clone());

        let serve_server = server.clone();
        let dest_for_serve = dest.clone();
        let token_for_serve = token.clone();
        let session_for_serve = self.session.clone();
        *self.worker.lock().unwrap() = Some(std::thread::spawn(move || serve(app, serve_server, dest_for_serve, token_for_serve, session_for_serve)));

        Ok(self.info())
    }

    pub fn stop(&self) {
        let port = self.inner.lock().unwrap().port;
        if let Some(s) = self.server.lock().unwrap().take() { s.unblock(); }
        if let Some(worker) = self.worker.lock().unwrap().take() { let _ = worker.join(); }
        // tiny_http drops a listener bound to 0.0.0.0 by connecting to that address.
        // Windows may reject that wake-up; loopback explicitly releases the accept thread.
        if port != 0 {
            let _ = std::net::TcpStream::connect_timeout(&([127, 0, 0, 1], port).into(), std::time::Duration::from_millis(100));
        }
        let mut i = self.inner.lock().unwrap();
        i.running = false;
        i.address.clear();
    }
}

/// Stable pseudo-uuid without pulling in a uuid crate.
fn uuid_v4_like() -> String {
    use std::sync::atomic::{AtomicU32, Ordering as AtomicOrdering};
    static COUNTER: AtomicU32 = AtomicU32::new(0);
    use std::time::{SystemTime, UNIX_EPOCH};
    let t = SystemTime::now().duration_since(UNIX_EPOCH).unwrap_or_default().as_nanos();
    let p = std::process::id();
    let c = COUNTER.fetch_add(1, AtomicOrdering::Relaxed);
    format!("{:x}-{:x}-{:x}", t, p, c)
}

/// The receiver token is saved to disk so the address already pasted on the
/// phone keeps working after a PC restart (a fresh token every start would
/// force the user to re-copy the address from the PC each time).
fn persistent_token() -> String {
    let file = std::env::var("APPDATA")
        .map(|r| PathBuf::from(r).join("LocalDrop Desktop").join("receiver.token"))
        .ok();
    if let Some(f) = &file {
        if let Ok(s) = fs::read_to_string(f) {
            let t = s.trim().to_string();
            if t.len() >= 16 {
                return t;
            }
        }
    }
    let t = format!("{:x}", Sha256::digest(uuid_v4_like().as_bytes()))[..24].to_string();
    if let Some(f) = file {
        if let Some(p) = f.parent() {
            let _ = fs::create_dir_all(p);
        }
        let _ = fs::write(&f, &t);
    }
    t
}

/// Best-effort address so the phone knows exactly where to push.
/// Preference: Tailscale (100.x) > private LAN (192.168/10/172.16-31) > anything
/// routable. Loopback and link-local (169.254) are never handed out — a phone
/// cannot reach those, and picking one makes the pasted address useless.
fn local_address(port: u16) -> String {
    let mut candidates: Vec<String> = Vec::new();
    // UDP "connect" only asks the routing table which source IP would be used,
    // so no packet is actually needed: 100.x probes the Tailscale/ZeroTier path,
    // 8.8.8.8 probes the default LAN/Internet route.
    for dest in ["100.100.100.100:80", "8.8.8.8:53"] {
        if let Ok(ip) = udp_source_ip(dest) {
            candidates.push(ip);
        }
    }
    if let Ok(list) = local_ips() {
        candidates.extend(list);
    }
    match candidates.into_iter().filter(|ip| reachable(ip)).max_by_key(|ip| rank(ip)) {
        Some(ip) => format!("http://{}:{}", ip, port),
        None => format!("http://127.0.0.1:{}", port),
    }
}

fn udp_source_ip(dest: &str) -> Result<String, ()> {
    let s = std::net::UdpSocket::bind("0.0.0.0:0").map_err(|_| ())?;
    s.connect(dest).map_err(|_| ())?;
    Ok(s.local_addr().map_err(|_| ())?.ip().to_string())
}

/// Usable from another device on the network (or the mesh).
fn reachable(ip: &str) -> bool {
    !ip.starts_with("127.") && !ip.starts_with("169.254.") && !ip.contains(':')
}

/// Higher is better: stable mesh address, then private LAN, then the rest.
fn rank(ip: &str) -> i32 {
    if ip.starts_with("100.") {
        3 // Tailscale/ZeroTier-style mesh: stable and works across networks
    } else if ip.starts_with("192.168.") || ip.starts_with("10.") || is_172_private(ip) {
        2 // private LAN
    } else {
        1
    }
}

fn is_172_private(ip: &str) -> bool {
    ip.strip_prefix("172.").map_or(false, |rest| {
        rest.split('.')
            .next()
            .and_then(|o| o.parse::<u8>().ok())
            .map_or(false, |o| (16..=31).contains(&o))
    })
}

fn local_ips() -> Result<Vec<String>, ()> {
    // Enumerate addresses by resolving the machine's own hostname.
    let hostname = std::env::var("COMPUTERNAME")
        .or_else(|_| std::env::var("HOSTNAME"))
        .unwrap_or_else(|_| "localhost".into());
    let mut out = Vec::new();
    if let Ok(addrs) = (hostname.as_str(), 0u16).to_socket_addrs() {
        for a in addrs {
            let ip = a.ip().to_string();
            if !ip.starts_with("127.") && !ip.contains(':') {
                out.push(ip);
            }
        }
    }
    if out.is_empty() {
        Err(())
    } else {
        Ok(out)
    }
}

use std::net::ToSocketAddrs;

/// The receiver loop: auth every data call, support resume + skip.
fn serve(app: AppHandle, server: Arc<Server>, dest: PathBuf, token: String, session: Arc<Mutex<Session>>) {
    for request in server.incoming_requests() {
        let _ = handle(&app, request, &dest, &token, &session);
    }
    // Loop ended via unblock() (graceful stop): reflect state back to UI.
    let _ = app.emit("receiver-state", json!({"running": false}));
}

fn handle(app: &AppHandle, mut request: Request, dest: &Path, token: &str, session: &Arc<Mutex<Session>>) -> Option<()> {
    let method = request.method().to_string();
    let url = request.url().to_string();
    let path = url.split('?').next().unwrap_or("").to_string();
    let query = parse_query(&url);

    // Health is open so the phone can confirm it reached a LocalDrop receiver.
    if method == "GET" && path == "/health" {
        respond(request, 200, json!({"name":"LocalDrop Desktop","protocol":"v1","push":true}));
        return Some(());
    }

    // Every data endpoint requires the session token (header, or ?t= in the pasted URL).
    if !authorized(&request, token, &query) {
        respond(request, 401, json!({"error":"invalid token"}));
        return Some(());
    }

    match (method.as_str(), path.as_str()) {
        // Phone declares a file; we reply with how many bytes we already hold.
        ("POST", "/upload/init") => {
            let body = read_body(&mut request);
            let v: Value = serde_json::from_slice(&body).unwrap_or(Value::Null);
            let file_id = v["file_id"].as_str().unwrap_or("");
            let size = v["size"].as_u64().unwrap_or(0);
            let sha = v["sha256"].as_str().unwrap_or("");
            if file_id.is_empty() || size == 0 {
                respond(request, 400, json!({"error":"bad file_id or size"}));
                return Some(());
            }
            let part = part_path(dest, file_id);
            // Already fully received and verified? Tell the phone to skip it.
            if let Some(final_path) = final_path(dest, file_id) {
                if final_path.is_file() {
                    if let Ok(m) = fs::metadata(&final_path) {
                        if m.len() == size && sha_ok(&final_path, sha) {
                            // Count it so the UI totals stay correct when resuming a batch.
                            {
                                let mut s = session.lock().unwrap();
                                s.files_done += 1;
                                s.bytes_done += size;
                            }
                            respond(request, 200, json!({"received": size, "size": size, "complete": true, "skip": true}));
                            return Some(());
                        }
                    }
                }
            }
            let received = fs::metadata(&part).map(|m| m.len()).unwrap_or(0).min(size);
            if received >= size && received > 0 {
                // A full .part that never got finalized (crash between write and rename):
                // verify it now; promote on success, or clear it so the phone resends.
                if !sha.is_empty() && sha_ok(&part, sha) {
                    if let Some(dest_final) = final_path(dest, file_id) {
                        if let Some(parent) = dest_final.parent() {
                            let _ = fs::create_dir_all(parent);
                        }
                        let dest_final = dedup_name(dest_final);
                        if fs::rename(&part, &dest_final).is_ok() {
                            let mut s = session.lock().unwrap();
                            s.files_done += 1;
                            s.bytes_done += size;
                            respond(request, 200, json!({"received": size, "size": size, "complete": true, "skip": true}));
                            return Some(());
                        }
                    }
                }
                let _ = fs::remove_file(&part);
                respond(request, 200, json!({"received": 0, "size": size, "complete": false}));
                return Some(());
            }
            respond(request, 200, json!({"received": received, "size": size, "complete": false}));
        }
        // Phone streams one chunk; we append and report the new offset.
        ("POST", "/upload/chunk") => {
            let file_id = query.get("file_id").cloned().unwrap_or_default();
            let offset: u64 = query.get("offset").and_then(|o| o.parse().ok()).unwrap_or(0);
            let total: u64 = header_u64(&request, "x-file-size").unwrap_or(0);
            let expect_sha = header_str(&request, "x-sha256").unwrap_or_default();
            if file_id.is_empty() || total == 0 {
                respond(request, 400, json!({"error":"bad chunk"}));
                return Some(());
            }
            let mut body = Vec::new();
            if request.as_reader().read_to_end(&mut body).is_err() || body.is_empty() {
                respond(request, 400, json!({"error":"empty chunk"}));
                return Some(());
            }
            if body.len() > CHUNK_SIZE * 2 {
                respond(request, 413, json!({"error":"chunk too large"}));
                return Some(());
            }
            let part = part_path(dest, &file_id);
            if let Some(parent) = part.parent() {
                let _ = fs::create_dir_all(parent);
            }
            let current = fs::metadata(&part).map(|m| m.len()).unwrap_or(0);
            // Out-of-order or stale offset: tell the phone where to resume.
            if offset != current {
                respond(request, 200, json!({"received": current, "size": total, "complete": false, "resync": true}));
                return Some(());
            }
            let write_ok = (|| -> std::io::Result<()> {
                let mut f = OpenOptions::new().create(true).append(true).open(&part)?;
                f.write_all(&body)?;
                f.flush()
            })();
            if write_ok.is_err() {
                respond(request, 500, json!({"error":"disk write failed"}));
                return Some(());
            }
            let received = current + body.len() as u64;
            if received >= total {
                // Finished: verify the whole file, then move it into place.
                if !expect_sha.is_empty() && !sha_ok(&part, &expect_sha) {
                    let _ = fs::remove_file(&part);
                    respond(request, 422, json!({"error":"sha256 mismatch","restart": true}));
                    return Some(());
                }
                let dest_final = match final_path(dest, &file_id) {
                    Some(p) => p,
                    None => {
                        respond(request, 400, json!({"error":"bad path"}));
                        return Some(());
                    }
                };
                if let Some(parent) = dest_final.parent() {
                    let _ = fs::create_dir_all(parent);
                }
                let dest_final = dedup_name(dest_final);
                if fs::rename(&part, &dest_final).is_err() {
                    respond(request, 500, json!({"error":"finalize failed"}));
                    return Some(());
                }
                {
                    let mut s = session.lock().unwrap();
                    s.files_done += 1;
                    s.bytes_done = (s.bytes_done + body.len() as u64).min(s.bytes_total.max(received));
                }
                let _ = app.emit("receiver-progress", json!({
                    "fileId": file_id,
                    "done": received,
                    "size": total,
                    "complete": true,
                    "counts": counts(session),
                }));
                respond(request, 200, json!({"received": received, "size": total, "complete": true}));
            } else {
                {
                    let mut s = session.lock().unwrap();
                    // bytes_done tracks the whole batch; fall back to this file's bytes if no batch was declared.
                    s.bytes_done = (s.bytes_done + body.len() as u64).min(s.bytes_total.max(received));
                }
                let _ = app.emit("receiver-progress", json!({
                    "fileId": file_id,
                    "done": received,
                    "size": total,
                    "complete": false,
                    "counts": counts(session),
                }));
                respond(request, 200, json!({"received": received, "size": total, "complete": false}));
            }
        }
        // Phone reports the batch size up front so the UI shows a real total.
        ("POST", "/upload/batch") => {
            let body = read_body(&mut request);
            let v: Value = serde_json::from_slice(&body).unwrap_or(Value::Null);
            let total = v["files"].as_u64().unwrap_or(0);
            let bytes = v["bytes"].as_u64().unwrap_or(0);
            {
                let mut s = session.lock().unwrap();
                s.files_total = total as usize;
                s.bytes_total = bytes;
            }
            let _ = app.emit("receiver-batch", json!({"files": total, "bytes": bytes}));
            respond(request, 200, json!({"ok": true}));
        }
        _ => respond(request, 404, json!({"error":"not found"})),
    }
    Some(())
}

/// Compact session snapshot attached to progress events for the UI.
fn counts(session: &Arc<Mutex<Session>>) -> Value {
    let s = session.lock().unwrap();
    json!({"filesDone": s.files_done, "filesTotal": s.files_total, "bytesDone": s.bytes_done, "bytesTotal": s.bytes_total})
}

fn authorized(request: &Request, token: &str, query: &std::collections::HashMap<String, String>) -> bool {
    if token.is_empty() {
        return true;
    }
    header_str(request, "x-token").map(|t| t == token).unwrap_or(false)
        || query.get("t").map(|t| t == token).unwrap_or(false)
}

fn header_str(request: &Request, name: &'static str) -> Option<String> {
    request
        .headers()
        .iter()
        .find(|h| h.field.equiv(name))
        .map(|h| h.value.as_str().to_string())
}

fn header_u64(request: &Request, name: &'static str) -> Option<u64> {
    header_str(request, name)?.parse().ok()
}

fn read_body(request: &mut Request) -> Vec<u8> {
    let mut buf = Vec::new();
    let _ = request.as_reader().read_to_end(&mut buf);
    buf
}

fn respond(request: Request, status: u16, value: Value) {
    let header = Header::from_bytes(&b"Content-Type"[..], &b"application/json"[..]).unwrap();
    let resp = Response::from_string(value.to_string()).with_status_code(status).with_header(header);
    let _ = request.respond(resp);
}

fn parse_query(url: &str) -> std::collections::HashMap<String, String> {
    let mut out = std::collections::HashMap::new();
    if let Some(q) = url.split('?').nth(1) {
        for pair in q.split('&') {
            let mut it = pair.splitn(2, '=');
            if let (Some(k), Some(v)) = (it.next(), it.next()) {
                out.insert(
                    k.to_string(),
                    v.replace("%2F", "/").replace("%3A", ":").replace('+', " "),
                );
            }
        }
    }
    out
}

/// Map a phone-relative id like `DCIM/Camera/a.jpg` to a safe path under dest.
fn final_path(dest: &Path, file_id: &str) -> Option<PathBuf> {
    let clean = file_id.replace('\\', "/");
    if clean.is_empty() || clean.starts_with('/') || clean.split('/').any(|s| s.is_empty() || s == ".." || s == ".") {
        return None;
    }
    let mut p = dest.to_path_buf();
    for seg in clean.split('/') {
        if seg.contains(':') || seg.chars().any(|c| c.is_control()) {
            return None;
        }
        p.push(seg);
    }
    Some(p)
}

/// Hidden partial file keyed by a hash of the id, so resume survives restarts.
fn part_path(dest: &Path, file_id: &str) -> PathBuf {
    let mut hasher = Sha256::new();
    hasher.update(file_id.as_bytes());
    let key = format!("{:x}", hasher.finalize());
    dest.join(".ldpart").join(format!("{}.part", &key[..key.len().min(40)]))
}

/// If the target name already exists, suffix it instead of overwriting.
fn dedup_name(path: PathBuf) -> PathBuf {
    if !path.exists() {
        return path;
    }
    let stem = path.file_stem().map(|s| s.to_string_lossy().into_owned()).unwrap_or_default();
    let ext = path.extension().map(|s| format!(".{}", s.to_string_lossy())).unwrap_or_default();
    let parent = path.parent().map(|p| p.to_path_buf()).unwrap_or_default();
    let mut n = 1;
    loop {
        let cand = parent.join(format!("{} ({}){}", stem, n, ext));
        if !cand.exists() {
            return cand;
        }
        n += 1;
    }
}

fn sha_ok(path: &Path, expected: &str) -> bool {
    if expected.is_empty() {
        return true;
    }
    let mut f = match File::open(path) {
        Ok(f) => f,
        Err(_) => return false,
    };
    let mut hasher = Sha256::new();
    let mut buf = [0u8; 64 * 1024];
    loop {
        match f.read(&mut buf) {
            Ok(0) => break,
            Ok(n) => hasher.update(&buf[..n]),
            Err(_) => return false,
        }
    }
    format!("{:x}", hasher.finalize()).eq_ignore_ascii_case(expected)
}

#[cfg(test)]
mod tests {
    #[test]
    fn never_advertises_unreachable_addresses() {
        let a = super::local_address(42198);
        assert!(!a.contains("169.254"), "link-local leaked: {}", a);
        assert!(!a.contains("127."), "loopback leaked: {}", a);
        assert!(a.ends_with(":42198"), "port missing: {}", a);
    }
}
