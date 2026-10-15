use reqwest::blocking::Client;
use serde::{Deserialize, Serialize};
use serde_json::{json, Value};
use sha2::{Digest, Sha256};
use std::{fs::File, io::{Read, Seek, Write}, net::{SocketAddr, UdpSocket}, path::Path};

pub const CHUNK_SIZE: usize = 1024 * 1024;

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct DeviceInfo { pub name: String, pub device_id: String, pub address: String, pub protocol: String }

fn url(base: &str, path: &str) -> String { format!("{}/api/v1/{}", base.trim_end_matches('/'), path.trim_start_matches('/')) }
fn client() -> Client { Client::builder().timeout(std::time::Duration::from_secs(60)).build().expect("HTTP client") }
fn auth(req: reqwest::blocking::RequestBuilder, token: &str) -> reqwest::blocking::RequestBuilder { if token.is_empty() { req } else { req.bearer_auth(token) } }
fn response(r: reqwest::blocking::Response) -> Result<Value, String> { let status = r.status(); let text = r.text().map_err(|e| e.to_string())?; if !status.is_success() { return Err(format!("HTTP {}: {}", status, text)); } serde_json::from_str(&text).or_else(|_| Ok(json!({"body": text}))) }

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
        let entry = entry.map_err(|e| e.to_string())?; let meta = entry.metadata().map_err(|e| e.to_string())?;
        let name = entry.file_name().to_string_lossy().to_string();
        items.push(json!({"name":name,"path":entry.path().to_string_lossy(),"type":if meta.is_dir(){"directory"}else{"file"},"size":if meta.is_file(){meta.len()}else{0}}));
    }
    items.sort_by(|a,b| (a["type"] != "directory").cmp(&(b["type"] != "directory")).then(a["name"].as_str().cmp(&b["name"].as_str())));
    Ok(json!({"path":base,"items":items}))
}
pub fn save_dialog(name: &str) -> Result<Value, String> {
    let home=std::env::var("USERPROFILE").map_err(|e|e.to_string())?;
    let dir=Path::new(&home).join("Downloads"); let _=std::fs::create_dir_all(&dir);
    Ok(json!(dir.join(Path::new(name).file_name().unwrap_or_default()).to_string_lossy().to_string()))
}

pub fn discover(address: Option<String>) -> Result<Vec<DeviceInfo>, String> {
    let mut candidates = Vec::new();
    if let Some(a) = address { candidates.push(a); }
    if let Ok(s) = UdpSocket::bind("0.0.0.0:0") { let _ = s.set_broadcast(true); let _ = s.send_to(b"LOCALDROP/1 DISCOVER\r\n", "255.255.255.255:42199"); }
    let http = client(); let mut found = Vec::new();
    for base in candidates {
        let normalized = if base.starts_with("http://") || base.starts_with("https://") { base.clone() } else { format!("http://{base}") };
        if let Ok(r) = http.get(url(&normalized, "health")).send() { if r.status().is_success() { if let Ok(v) = response(r) { found.push(DeviceInfo { name:v["name"].as_str().unwrap_or("LocalDrop").into(),device_id:v["device_id"].as_str().unwrap_or_default().into(),address:normalized,protocol:"v1".into() }); } } }
    }
    Ok(found)
}


pub fn pair_request(base: &str, fingerprint: &str, device_id: &str) -> Result<Value, String> {
    let name = std::env::var("COMPUTERNAME").unwrap_or_else(|_| "LocalDrop Desktop".into());
    response(client().post(url(base, "pair/request")).json(&json!({"fingerprint": fingerprint, "device_id": device_id, "name": name})).send().map_err(|e| e.to_string())?)
}
pub fn verify_pair(base: &str, request_id: &str, pin: &str) -> Result<Value, String> {
    response(client().post(url(base, "pair/verify")).json(&json!({"request_id": request_id, "pin": pin})).send().map_err(|e| e.to_string())?)
}

pub fn list_files(base: &str, token: &str, path: &str) -> Result<Value, String> { response(auth(client().get(url(base, "transfer/files")).query(&[("path", path)]), token).send().map_err(|e| e.to_string())?) }
pub fn create_folder(base: &str, token: &str, path: &str, name: &str) -> Result<Value, String> { let clean=Path::new(name).file_name().and_then(|n|n.to_str()).ok_or("invalid folder")?; let target=if path.is_empty(){clean.to_string()}else{format!("{path}/{clean}")}; response(auth(client().post(url(base, "transfer/folder")).json(&json!({"path":target})),token).send().map_err(|e| e.to_string())?) }
pub fn delete_path(base: &str, token: &str, path: &str) -> Result<Value, String> { response(auth(client().delete(url(base, "transfer/path")).query(&[("path",path)]),token).send().map_err(|e| e.to_string())?) }
pub fn send_file(base: &str, token: &str, path: &str, transfer_id: Option<&str>, remote_path: Option<&str>, paused: &std::sync::atomic::AtomicBool) -> Result<Value, String> {
    let p = Path::new(path);
    let mut f = File::open(p).map_err(|e| e.to_string())?;
    let size = f.metadata().map_err(|e| e.to_string())?.len();
    let name = p.file_name().and_then(|x| x.to_str()).ok_or("invalid filename")?;
    if size == 0 { return Err("empty files are not supported by the current Android chunk protocol".into()); }

    let mut hasher = Sha256::new();
    // The Windows UI thread has a small stack; keep the 1 MiB hashing buffer on the heap.
    let mut scan = vec![0u8; CHUNK_SIZE];
    loop {
        let n = f.read(&mut scan).map_err(|e| e.to_string())?;
        if n == 0 { break; }
        hasher.update(&scan[..n]);
    }
    let digest = format!("{:x}", hasher.finalize());
    f.rewind().map_err(|e| e.to_string())?;

    let id = transfer_id.unwrap_or("desktop-transfer");
    let mut offset = 0u64;
    let mut last = json!({"transfer_id": id, "offset": 0});
    loop {
        while paused.load(std::sync::atomic::Ordering::Relaxed) { std::thread::sleep(std::time::Duration::from_millis(100)); }
        let mut chunk = vec![0u8; CHUNK_SIZE];
        let n = f.read(&mut chunk).map_err(|e| e.to_string())?;
        if n == 0 { break; }
        chunk.truncate(n);
        let req = client().post(url(base, "transfer/chunk"))
            .query(&[("transfer_id", id), ("file_id", remote_path.unwrap_or(name)), ("offset", &offset.to_string())])
            .header("x-sha256", &digest)
            .header("x-file-size", size.to_string())
            .body(chunk);
        last = response(auth(req, token).send().map_err(|e| e.to_string())?)?;
        offset += n as u64;
    }
    Ok(last)
}
pub fn receive_file(base: &str, token: &str, file_id: &str, destination: &str, on_progress: impl Fn(u64, u64)) -> Result<Value, String> {
    let mut out = File::create(destination).map_err(|e| e.to_string())?; let mut offset = 0u64;
    // Total size from the first chunk response (Content-Range: bytes a-b/total).
    let mut total = 0u64;
    loop {
        let r = auth(client().get(url(base, "transfer/chunk")).query(&[("file_id", file_id), ("offset", &offset.to_string()), ("length", &CHUNK_SIZE.to_string())]), token).send().map_err(|e| e.to_string())?;
        if !r.status().is_success() { return Err(format!("HTTP {}", r.status())); }
        if total == 0 {
            if let Some(cr) = r.headers().get("content-range").and_then(|v| v.to_str().ok()) {
                total = cr.rsplit('/').next().and_then(|t| t.parse().ok()).unwrap_or(0);
            }
        }
        let bytes = r.bytes().map_err(|e| e.to_string())?;
        let n = bytes.len();
        if n == 0 { break; }
        out.write_all(&bytes).map_err(|e| e.to_string())?;
        offset += n as u64;
        on_progress(offset, total.max(offset));
        if n < CHUNK_SIZE { break; }
    }
    out.flush().map_err(|e| e.to_string())?;
    Ok(json!({"file_id": file_id, "path": destination, "size": offset, "sha256": format!("{:x}", Sha256::digest(&std::fs::read(destination).map_err(|e| e.to_string())?))}))
}

/// Streams one ZIP of many remote files (POST /transfer/zip) straight to disk.
/// Written to a .part file first so an interrupted transfer never leaves a half ZIP
/// with the final name. No total request timeout: 300 files can take minutes.
pub fn download_zip(base: &str, token: &str, paths: &[String], destination: &str, on_progress: impl Fn(u64)) -> Result<Value, String> {
    let http = Client::builder().connect_timeout(std::time::Duration::from_secs(15)).build().map_err(|e| e.to_string())?;
    let r = auth(http.post(url(base, "transfer/zip")).json(&json!({"paths": paths})), token).send().map_err(|e| e.to_string())?;
    let status = r.status();
    if !status.is_success() { let text = r.text().unwrap_or_default(); return Err(format!("HTTP {}: {}", status, text)); }
    let part = format!("{}.part", destination);
    let copied = (|| -> Result<u64, String> {
        let mut out = File::create(&part).map_err(|e| e.to_string())?;
        let mut reader = r;
        let mut buf = [0u8; 64 * 1024];
        let mut done = 0u64;
        loop {
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
            std::fs::rename(&part, destination).map_err(|e| e.to_string())?;
            Ok(json!({"path": destination, "size": done}))
        }
        Err(e) => { let _ = std::fs::remove_file(&part); Err(e) }
    }
}

pub fn socket_addr(base: &str) -> Option<SocketAddr> { base.parse().ok() }

// Frontend bridge shapes: discover({address?}), pair({base,fingerprint,deviceId,pin?}),
// listFiles({base,token}), sendFile({base,token,path,transferId?}), receiveFile({base,token,fileId,destination}).

#[cfg(test)]
mod tests {
    use super::*;
    use std::net::TcpListener;
    use std::sync::atomic::AtomicBool;
    #[test]
    fn upload_works_on_a_small_windows_sized_stack() {
        let listener = TcpListener::bind("127.0.0.1:0").unwrap();
        let address = format!("http://{}", listener.local_addr().unwrap());
        let server = std::thread::spawn(move || {
            let (mut stream, _) = listener.accept().unwrap();
            stream.set_read_timeout(Some(std::time::Duration::from_secs(5))).unwrap();
            let mut headers = Vec::new();
            while !headers.ends_with(b"\r\n\r\n") {
                let mut byte = [0u8; 1]; stream.read_exact(&mut byte).unwrap(); headers.push(byte[0]);
            }
            let text = String::from_utf8(headers).unwrap();
            let length: usize = text.lines().find_map(|line| line.to_ascii_lowercase().strip_prefix("content-length:").map(|n| n.trim().parse().unwrap())).unwrap();
            let mut bytes = vec![0; length]; stream.read_exact(&mut bytes).unwrap();
            assert_eq!(bytes, b"small-stack upload");
            let body = br#"{"complete":true}"#;
            write!(stream, "HTTP/1.1 201 Created\r\nContent-Type: application/json\r\nContent-Length: {}\r\nConnection: close\r\n\r\n", body.len()).unwrap();
            stream.write_all(body).unwrap();
        });
        let file = std::env::temp_dir().join(format!("localdrop-small-stack-{}.txt", rand::random::<u64>()));
        std::fs::write(&file, b"small-stack upload").unwrap();
        let upload_path = file.clone();
        let result = std::thread::Builder::new().stack_size(512 * 1024).spawn(move || {
            send_file(&address, "test-token", upload_path.to_str().unwrap(), Some("test"), None, &AtomicBool::new(false))
        }).unwrap().join().unwrap();
        std::fs::remove_file(file).unwrap(); server.join().unwrap();
        assert_eq!(result.unwrap()["complete"], true);
    }
}
