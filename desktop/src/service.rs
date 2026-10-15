use reqwest::blocking::Client;
use serde::{Deserialize, Serialize};
use serde_json::{json, Value};
use sha2::{Digest, Sha256};
use std::{
    fs::{File, OpenOptions},
    io::{Read, Seek, SeekFrom, Write},
    net::UdpSocket,
    path::Path,
    sync::{atomic::{AtomicBool, Ordering}, OnceLock},
    time::{Duration, Instant},
};

pub const CHUNK_SIZE: usize = 2 * 1024 * 1024;
const MAX_ATTEMPTS: u32 = 4;
pub const DISCOVERY_PORT: u16 = 42199;

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct DeviceInfo { pub name: String, pub device_id: String, pub address: String, pub protocol: String }

/// Pause / cancel flags shared between a running transfer and the UI.
#[derive(Default)]
pub struct Ctl { pub paused: AtomicBool, pub cancelled: AtomicBool }
impl Ctl {
    /// Blocks while paused. Returns Err when the transfer was cancelled.
    fn checkpoint(&self) -> Result<(), String> {
        while self.paused.load(Ordering::Relaxed) {
            if self.cancelled.load(Ordering::Relaxed) { break; }
            std::thread::sleep(Duration::from_millis(100));
        }
        if self.cancelled.load(Ordering::Relaxed) { Err("cancelled".into()) } else { Ok(()) }
    }
}

fn url(base: &str, path: &str) -> String { format!("{}/api/v1/{}", base.trim_end_matches('/'), path.trim_start_matches('/')) }
/// One shared client keeps connections alive between chunks instead of reconnecting for every 2 MiB.
fn client() -> &'static Client {
    static HTTP: OnceLock<Client> = OnceLock::new();
    HTTP.get_or_init(|| Client::builder().connect_timeout(Duration::from_secs(10)).timeout(Duration::from_secs(120)).pool_idle_timeout(Duration::from_secs(30)).tcp_nodelay(true).build().expect("HTTP client"))
}
fn auth(req: reqwest::blocking::RequestBuilder, token: &str) -> reqwest::blocking::RequestBuilder { if token.is_empty() { req } else { req.bearer_auth(token) } }
fn response(r: reqwest::blocking::Response) -> Result<Value, String> { let status = r.status(); let text = r.text().map_err(|e| e.to_string())?; if !status.is_success() { return Err(format!("HTTP {}: {}", status, text)); } serde_json::from_str(&text).or_else(|_| Ok(json!({"body": text}))) }
fn retriable(error: &str) -> bool { !(error.contains("HTTP 401") || error.contains("HTTP 403") || error.contains("HTTP 404") || error.contains("cancelled")) }

// Windows drives (C:\, D:\, ...) that exist on this PC.
fn drives() -> Vec<Value> {
    ('A'..='Z').filter_map(|c| {
        let p = format!("{c}:\\");
        if Path::new(&p).is_dir() { Some(json!({"name": format!("{c}:"), "path": p, "type": "directory", "size": 0})) } else { None }
    }).collect()
}
pub fn list_local(path: &str) -> Result<Value, String> {
    // Empty path or the special "drives" value shows the drive list, so the user can reach every drive.
    if path == "drives" { return Ok(json!({"path": "", "items": drives()})); }
    let base = if path.is_empty() { std::env::var("USERPROFILE").map_err(|e| e.to_string())? } else { path.to_string() };
    let dir = Path::new(&base);
    if !dir.is_dir() { return Err("local folder not found".into()); }
    let mut items = Vec::new();
    for entry in std::fs::read_dir(dir).map_err(|e| e.to_string())? {
        let Ok(entry) = entry else { continue };
        let Ok(meta) = entry.metadata() else { continue };
        let name = entry.file_name().to_string_lossy().to_string();
        items.push(json!({"name":name,"path":entry.path().to_string_lossy(),"type":if meta.is_dir(){"directory"}else{"file"},"size":if meta.is_file(){meta.len()}else{0},"modified":meta.modified().ok().and_then(|m| m.duration_since(std::time::UNIX_EPOCH).ok()).map(|d| d.as_millis() as u64).unwrap_or(0)}));
    }
    items.sort_by(|a,b| (a["type"] != "directory").cmp(&(b["type"] != "directory")).then_with(|| a["name"].as_str().unwrap_or("").to_lowercase().cmp(&b["name"].as_str().unwrap_or("").to_lowercase())));
    Ok(json!({"path":base,"items":items}))
}

fn normalize(base: &str) -> String { if base.starts_with("http://") || base.starts_with("https://") { base.to_string() } else { format!("http://{base}") } }
pub fn discover(address: Option<String>) -> Result<Vec<DeviceInfo>, String> {
    let mut found = Vec::new();
    if let Some(base) = address {
        let normalized = normalize(&base);
        if let Ok(r) = client().get(url(&normalized, "health")).send() {
            if r.status().is_success() {
                if let Ok(v) = response(r) { found.push(DeviceInfo { name: v["name"].as_str().unwrap_or("Pushvia").into(), device_id: v["device_id"].as_str().unwrap_or_default().into(), address: normalized, protocol: "v1".into() }); }
            }
        }
    }
    Ok(found)
}

fn local_ipv4s() -> Vec<std::net::Ipv4Addr> {
    let mut ips = Vec::new();
    if let Ok(s) = UdpSocket::bind("0.0.0.0:0") { if s.connect("8.8.8.8:53").is_ok() { if let Ok(std::net::SocketAddr::V4(a)) = s.local_addr() { ips.push(*a.ip()); } } }
    let host = std::env::var("COMPUTERNAME").unwrap_or_else(|_| "localhost".into());
    if let Ok(addrs) = std::net::ToSocketAddrs::to_socket_addrs(&(host.as_str(), 0u16)) { for a in addrs { if let std::net::SocketAddr::V4(v4) = a { if !v4.ip().is_loopback() && !ips.contains(v4.ip()) { ips.push(*v4.ip()); } } } }
    ips
}

/// Broadcasts a discovery probe on the local network and collects phone replies.
/// Phones answer with {"name","device_id","port"} while Browser Share is running.
pub fn discover_lan(wait_ms: u64) -> Result<Vec<DeviceInfo>, String> {
    let socket = UdpSocket::bind("0.0.0.0:0").map_err(|e| e.to_string())?;
    socket.set_broadcast(true).map_err(|e| e.to_string())?;
    socket.set_read_timeout(Some(Duration::from_millis(250))).map_err(|e| e.to_string())?;
    let probe = b"LOCALDROP/1 DISCOVER\r\n";
    let mut targets = vec![format!("255.255.255.255:{DISCOVERY_PORT}")];
    for ip in local_ipv4s() { let o = ip.octets(); targets.push(format!("{}.{}.{}.255:{DISCOVERY_PORT}", o[0], o[1], o[2])); }
    targets.dedup();
    let started = Instant::now();
    let mut found: Vec<DeviceInfo> = Vec::new();
    let mut sent = 0;
    let mut buf = [0u8; 1024];
    while started.elapsed() < Duration::from_millis(wait_ms) {
        if sent < 2 && started.elapsed() > Duration::from_millis(sent * 600) { for t in &targets { let _ = socket.send_to(probe, t); } sent += 1; }
        let Ok((n, from)) = socket.recv_from(&mut buf) else { continue };
        let Ok(v) = serde_json::from_slice::<Value>(&buf[..n]) else { continue };
        let Some(port) = v["port"].as_u64().filter(|p| (1..=65535).contains(p)) else { continue };
        let address = format!("http://{}:{}", from.ip(), port);
        if found.iter().any(|d| d.address == address) { continue; }
        found.push(DeviceInfo { name: v["name"].as_str().unwrap_or("Pushvia Android").chars().take(60).collect(), device_id: v["device_id"].as_str().unwrap_or_default().chars().take(80).collect(), address, protocol: "v1".into() });
    }
    Ok(found)
}

pub fn pair_request(base: &str, fingerprint: &str, device_id: &str) -> Result<Value, String> {
    let name = std::env::var("COMPUTERNAME").unwrap_or_else(|_| "Pushvia Desktop".into());
    response(client().post(url(base, "pair/request")).json(&json!({"fingerprint": fingerprint, "device_id": device_id, "name": name})).send().map_err(|e| e.to_string())?)
}
pub fn verify_pair(base: &str, request_id: &str, pin: &str) -> Result<Value, String> {
    response(client().post(url(base, "pair/verify")).json(&json!({"request_id": request_id, "pin": pin})).send().map_err(|e| e.to_string())?)
}

pub fn list_files(base: &str, token: &str, path: &str) -> Result<Value, String> { response(auth(client().get(url(base, "transfer/files")).query(&[("path", path)]), token).send().map_err(|e| e.to_string())?) }
pub fn create_folder(base: &str, token: &str, path: &str, name: &str) -> Result<Value, String> { let clean=Path::new(name).file_name().and_then(|n|n.to_str()).ok_or("invalid folder")?; let target=if path.is_empty(){clean.to_string()}else{format!("{path}/{clean}")}; response(auth(client().post(url(base, "transfer/folder")).json(&json!({"path":target})),token).send().map_err(|e| e.to_string())?) }
pub fn delete_path(base: &str, token: &str, path: &str) -> Result<Value, String> { response(auth(client().delete(url(base, "transfer/path")).query(&[("path",path)]),token).send().map_err(|e| e.to_string())?) }

/// Streams a file through SHA-256 using a heap buffer (the Windows UI stack is small).
pub fn sha256_file(path: &Path, ctl: Option<&Ctl>) -> Result<String, String> {
    let mut f = File::open(path).map_err(|e| e.to_string())?;
    let mut hasher = Sha256::new();
    let mut buf = vec![0u8; 256 * 1024];
    loop {
        if let Some(c) = ctl { c.checkpoint()?; }
        let n = f.read(&mut buf).map_err(|e| e.to_string())?;
        if n == 0 { break; }
        hasher.update(&buf[..n]);
    }
    Ok(format!("{:x}", hasher.finalize()))
}

/// Deterministic id so an interrupted upload continues from the phone's partial file, even after a restart.
pub fn resume_id(digest: &str, remote: &str) -> String {
    let tail = format!("{:x}", Sha256::digest(remote.as_bytes()));
    format!("ld-{}-{}", &digest[..24], &tail[..12])
}

fn remote_offset(base: &str, token: &str, id: &str, remote: &str, size: u64) -> u64 {
    let req = client().get(url(base, "transfer/status")).query(&[("transfer_id", id), ("file_id", remote), ("size", &size.to_string())]);
    auth(req, token).send().ok().and_then(|r| response(r).ok()).and_then(|v| v["offset"].as_u64()).unwrap_or(0).min(size)
}

pub fn send_file(base: &str, token: &str, path: &str, remote_path: Option<&str>, ctl: &Ctl, on_progress: impl Fn(u64, u64)) -> Result<Value, String> {
    let p = Path::new(path);
    let mut f = File::open(p).map_err(|e| e.to_string())?;
    let size = f.metadata().map_err(|e| e.to_string())?.len();
    let name = p.file_name().and_then(|x| x.to_str()).ok_or("invalid filename")?;
    let remote = remote_path.unwrap_or(name);
    if size == 0 {
        return response(auth(client().post(url(base, "transfer/empty")).query(&[("file_id", remote)]), token).send().map_err(|e| e.to_string())?);
    }
    let digest = sha256_file(p, Some(ctl))?;
    let id = resume_id(&digest, remote);
    let mut offset = remote_offset(base, token, &id, remote, size);
    on_progress(offset, size);
    let mut last = json!({"transfer_id": id, "offset": offset});
    let mut chunk = vec![0u8; CHUNK_SIZE];
    while offset < size {
        ctl.checkpoint()?;
        f.seek(SeekFrom::Start(offset)).map_err(|e| e.to_string())?;
        let want = CHUNK_SIZE.min((size - offset) as usize);
        f.read_exact(&mut chunk[..want]).map_err(|e| e.to_string())?;
        let mut attempt = 0;
        let sent = loop {
            attempt += 1;
            let req = client().post(url(base, "transfer/chunk"))
                .query(&[("transfer_id", id.as_str()), ("file_id", remote), ("offset", &offset.to_string())])
                .header("x-sha256", &digest).header("x-file-size", size.to_string())
                .body(chunk[..want].to_vec());
            match auth(req, token).send().map_err(|e| e.to_string()).and_then(response) {
                Ok(v) => break Ok(v),
                Err(e) if attempt < MAX_ATTEMPTS && retriable(&e) => {
                    std::thread::sleep(Duration::from_millis(400 * attempt as u64));
                    ctl.checkpoint()?;
                    let resumed = remote_offset(base, token, &id, remote, size);
                    if resumed != offset { offset = resumed; f.seek(SeekFrom::Start(offset)).map_err(|e| e.to_string())?; break Err(String::new()); }
                }
                Err(e) => return Err(e),
            }
        };
        match sent {
            Ok(v) => { offset += want as u64; last = v; on_progress(offset, size); }
            Err(_) => { on_progress(offset, size); }
        }
    }
    Ok(last)
}

/// Downloads one file in chunks into `<destination>.part`, continues a previous partial file,
/// hashes while writing, and only renames to the final name when the size matches.
pub fn receive_file(base: &str, token: &str, file_id: &str, destination: &str, ctl: &Ctl, on_progress: impl Fn(u64, u64)) -> Result<Value, String> {
    let part = format!("{destination}.part");
    let mut offset = std::fs::metadata(&part).map(|m| m.len()).unwrap_or(0);
    let mut hasher = Sha256::new();
    if offset > 0 {
        let mut existing = File::open(&part).map_err(|e| e.to_string())?;
        let mut buf = vec![0u8; 256 * 1024];
        loop { let n = existing.read(&mut buf).map_err(|e| e.to_string())?; if n == 0 { break; } hasher.update(&buf[..n]); }
    }
    let mut out = OpenOptions::new().create(true).append(true).open(&part).map_err(|e| e.to_string())?;
    let mut total = 0u64;
    let mut restarted = false;
    loop {
        ctl.checkpoint()?;
        let mut attempt = 0;
        let (status, content_range, bytes) = loop {
            attempt += 1;
            let req = client().get(url(base, "transfer/chunk")).query(&[("file_id", file_id), ("offset", &offset.to_string()), ("length", &CHUNK_SIZE.to_string())]);
            match auth(req, token).send().map_err(|e| e.to_string()) {
                Ok(r) => {
                    let status = r.status();
                    let cr = r.headers().get("content-range").and_then(|v| v.to_str().ok()).map(str::to_string);
                    match r.bytes() { Ok(b) => break (status, cr, b), Err(e) if attempt < MAX_ATTEMPTS => { let _ = e; std::thread::sleep(Duration::from_millis(400 * attempt as u64)); } Err(e) => return Err(e.to_string()) }
                }
                Err(e) if attempt < MAX_ATTEMPTS && retriable(&e) => std::thread::sleep(Duration::from_millis(400 * attempt as u64)),
                Err(e) => return Err(e),
            }
        };
        if status.as_u16() == 416 && offset > 0 && !restarted {
            drop(out); let _ = std::fs::remove_file(&part); offset = 0; hasher = Sha256::new(); restarted = true;
            out = OpenOptions::new().create(true).append(true).open(&part).map_err(|e| e.to_string())?;
            continue;
        }
        if !status.is_success() { return Err(format!("HTTP {status}")); }
        if let Some(t) = content_range.as_deref().and_then(|cr| cr.rsplit('/').next()).and_then(|t| t.parse::<u64>().ok()) { total = t; }
        if bytes.is_empty() { break; }
        out.write_all(&bytes).map_err(|e| e.to_string())?;
        hasher.update(&bytes);
        offset += bytes.len() as u64;
        on_progress(offset, total.max(offset));
        if total > 0 && offset >= total { break; }
    }
    out.flush().map_err(|e| e.to_string())?;
    drop(out);
    if total > 0 && offset != total { return Err(format!("incomplete download: {offset} of {total} bytes")); }
    let _ = std::fs::remove_file(destination);
    std::fs::rename(&part, destination).map_err(|e| e.to_string())?;
    Ok(json!({"file_id": file_id, "path": destination, "size": offset, "sha256": format!("{:x}", hasher.finalize())}))
}

/// Streams one ZIP of many remote files (POST /transfer/zip) straight to disk.
/// Written to a .part file first so an interrupted transfer never leaves a half ZIP
/// with the final name. No total request timeout: 300 files can take minutes.
pub fn download_zip(base: &str, token: &str, paths: &[String], destination: &str, ctl: &Ctl, on_progress: impl Fn(u64)) -> Result<Value, String> {
    let http = Client::builder().connect_timeout(Duration::from_secs(15)).build().map_err(|e| e.to_string())?;
    let r = auth(http.post(url(base, "transfer/zip")).json(&json!({"paths": paths})), token).send().map_err(|e| e.to_string())?;
    let status = r.status();
    if !status.is_success() { let text = r.text().unwrap_or_default(); return Err(format!("HTTP {}: {}", status, text)); }
    let part = format!("{}.part", destination);
    let copied = (|| -> Result<u64, String> {
        let mut out = File::create(&part).map_err(|e| e.to_string())?;
        let mut reader = r;
        let mut buf = vec![0u8; 64 * 1024];
        let mut done = 0u64;
        loop {
            ctl.checkpoint()?;
            let n = reader.read(&mut buf).map_err(|e| e.to_string())?;
            if n == 0 { break; }
            out.write_all(&buf[..n]).map_err(|e| e.to_string())?;
            done += n as u64;
            on_progress(done);
        }
        out.flush().map_err(|e| e.to_string())?;
        Ok(done)
    })();
    match copied {
        Ok(done) => {
            let _ = std::fs::remove_file(destination);
            std::fs::rename(&part, destination).map_err(|e| e.to_string())?;
            Ok(json!({"path": destination, "size": done}))
        }
        Err(e) => { let _ = std::fs::remove_file(&part); Err(e) }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::{collections::HashMap, net::{TcpListener, TcpStream}, sync::{Arc, Mutex}};

    type Log = Arc<Mutex<Vec<(String, Vec<u8>)>>>;

    fn read_request(stream: &mut TcpStream) -> Option<(String, String, Vec<u8>)> {
        stream.set_read_timeout(Some(Duration::from_secs(5))).ok()?;
        let mut headers = Vec::new();
        while !headers.ends_with(b"\r\n\r\n") { let mut byte = [0u8; 1]; if stream.read(&mut byte).ok()? == 0 { return None; } headers.push(byte[0]); }
        let text = String::from_utf8(headers).ok()?;
        let first = text.lines().next()?.to_string();
        let length: usize = text.lines().find_map(|l| l.to_ascii_lowercase().strip_prefix("content-length:").map(|n| n.trim().parse().unwrap_or(0))).unwrap_or(0);
        let mut body = vec![0; length]; stream.read_exact(&mut body).ok()?;
        let mut parts = first.split(' ');
        Some((parts.next()?.to_string(), parts.next()?.to_string(), body))
    }
    fn reply(stream: &mut TcpStream, status: &str, body: &str) {
        write!(stream, "HTTP/1.1 {status}\r\nContent-Type: application/json\r\nContent-Length: {}\r\nConnection: keep-alive\r\n\r\n{body}", body.len()).unwrap();
    }
    fn query(path: &str) -> HashMap<String, String> {
        path.split('?').nth(1).unwrap_or("").split('&').filter_map(|p| p.split_once('=')).map(|(k, v)| (k.to_string(), v.to_string())).collect()
    }
    /// Minimal phone: resumes from `start`, stores chunks, fails the first chunk once when `flaky`.
    fn fake_phone(start: u64, flaky: bool) -> (String, Log, std::thread::JoinHandle<()>) {
        let listener = TcpListener::bind("127.0.0.1:0").unwrap();
        let address = format!("http://{}", listener.local_addr().unwrap());
        let log: Log = Arc::new(Mutex::new(Vec::new()));
        let shared = log.clone();
        let handle = std::thread::spawn(move || {
            let mut failed = false; let mut received = start; let mut total = 0u64;
            while let Ok((mut stream, _)) = listener.accept() {
                while let Some((method, path, body)) = read_request(&mut stream) {
                    let q = query(&path);
                    if path.contains("/transfer/status") { total = q["size"].parse().unwrap(); reply(&mut stream, "200 OK", &format!("{{\"offset\":{received}}}")); }
                    else if path.contains("/transfer/chunk") && method == "POST" {
                        if flaky && !failed { failed = true; reply(&mut stream, "500 Internal Server Error", "boom"); continue; }
                        assert_eq!(q["offset"].parse::<u64>().unwrap(), received);
                        received += body.len() as u64; shared.lock().unwrap().push((q["offset"].clone(), body));
                        let done = received >= total; reply(&mut stream, "200 OK", &format!("{{\"offset\":{received},\"complete\":{done}}}"));
                        if done { return; }
                    } else { reply(&mut stream, "404 Not Found", "{}"); }
                }
            }
        });
        (address, log, handle)
    }
    fn temp_file(bytes: &[u8]) -> std::path::PathBuf {
        let file = std::env::temp_dir().join(format!("localdrop-test-{}.bin", rand::random::<u64>()));
        std::fs::write(&file, bytes).unwrap(); file
    }

    #[test]
    fn upload_works_on_a_small_windows_sized_stack() {
        let (address, log, server) = fake_phone(0, false);
        let file = temp_file(b"small-stack upload"); let upload_path = file.clone();
        let result = std::thread::Builder::new().stack_size(512 * 1024).spawn(move || {
            send_file(&address, "test-token", upload_path.to_str().unwrap(), None, &Ctl::default(), |_, _| {})
        }).unwrap().join().unwrap();
        std::fs::remove_file(file).unwrap(); server.join().unwrap();
        assert_eq!(result.unwrap()["complete"], true);
        assert_eq!(log.lock().unwrap()[0].1, b"small-stack upload");
    }

    #[test]
    fn upload_resumes_from_the_phones_partial_file() {
        let data: Vec<u8> = (0..(CHUNK_SIZE + 4096)).map(|i| (i % 251) as u8).collect();
        let (address, log, server) = fake_phone(CHUNK_SIZE as u64, false);
        let file = temp_file(&data);
        let result = send_file(&address, "t", file.to_str().unwrap(), None, &Ctl::default(), |_, _| {});
        std::fs::remove_file(file).unwrap(); server.join().unwrap();
        assert_eq!(result.unwrap()["complete"], true);
        let sent = log.lock().unwrap();
        assert_eq!(sent.len(), 1, "only the missing tail may be sent");
        assert_eq!(sent[0].0, CHUNK_SIZE.to_string());
        assert_eq!(sent[0].1, &data[CHUNK_SIZE..]);
    }

    #[test]
    fn upload_retries_a_failed_chunk() {
        let (address, log, server) = fake_phone(0, true);
        let file = temp_file(b"retry me");
        let result = send_file(&address, "t", file.to_str().unwrap(), None, &Ctl::default(), |_, _| {});
        std::fs::remove_file(file).unwrap(); server.join().unwrap();
        assert_eq!(result.unwrap()["complete"], true);
        assert_eq!(log.lock().unwrap().len(), 1);
    }

    #[test]
    fn cancelled_upload_stops_before_sending() {
        let (address, log, _server) = fake_phone(0, false);
        let file = temp_file(b"never sent"); let ctl = Ctl::default(); ctl.cancelled.store(true, Ordering::Relaxed);
        let result = send_file(&address, "t", file.to_str().unwrap(), None, &ctl, |_, _| {});
        std::fs::remove_file(file).unwrap();
        assert!(result.is_err() && log.lock().unwrap().is_empty());
    }

    #[test]
    fn resume_id_is_stable_and_path_specific() {
        let digest = "a".repeat(64);
        assert_eq!(resume_id(&digest, "a/b.txt"), resume_id(&digest, "a/b.txt"));
        assert_ne!(resume_id(&digest, "a/b.txt"), resume_id(&digest, "a/c.txt"));
    }

    #[test]
    fn sha256_of_known_input() {
        let file = temp_file(b"abc");
        let digest = sha256_file(&file, None).unwrap(); std::fs::remove_file(file).unwrap();
        assert_eq!(digest, "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    #[test]
    fn download_resumes_partial_file_and_verifies_hash() {
        let data: Vec<u8> = (0..300_000u32).map(|i| (i % 253) as u8).collect();
        let listener = TcpListener::bind("127.0.0.1:0").unwrap();
        let address = format!("http://{}", listener.local_addr().unwrap());
        let served = data.clone();
        let server = std::thread::spawn(move || {
            let (mut stream, _) = listener.accept().unwrap();
            while let Some((_, path, _)) = read_request(&mut stream) {
                let q = query(&path); let start: usize = q["offset"].parse().unwrap();
                let end = served.len().min(start + q["length"].parse::<usize>().unwrap());
                let body = &served[start..end];
                write!(stream, "HTTP/1.1 206 Partial Content\r\nContent-Length: {}\r\nContent-Range: bytes {}-{}/{}\r\nConnection: keep-alive\r\n\r\n", body.len(), start, end.saturating_sub(1), served.len()).unwrap();
                stream.write_all(body).unwrap();
                if end >= served.len() { return; }
            }
        });
        let destination = std::env::temp_dir().join(format!("localdrop-dl-{}.bin", rand::random::<u64>()));
        let dest = destination.to_str().unwrap().to_string();
        std::fs::write(format!("{dest}.part"), &data[..100_000]).unwrap();
        let result = receive_file(&address, "t", "x.bin", &dest, &Ctl::default(), |_, _| {}).unwrap();
        server.join().unwrap();
        assert_eq!(std::fs::read(&destination).unwrap(), data);
        assert!(!Path::new(&format!("{dest}.part")).exists());
        assert_eq!(result["sha256"], format!("{:x}", Sha256::digest(&data)));
        std::fs::remove_file(destination).unwrap();
    }
}
