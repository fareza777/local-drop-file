use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};
use std::{fs::OpenOptions, io::{self, Read, Seek, SeekFrom, Write}, path::Path};

pub const CHUNK_SIZE: u64 = 1024 * 1024;
#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct TransferItem { pub id: String, pub name: String, pub size: u64, pub transferred: u64, pub sha256: Option<String>, pub direction: String, pub status: String }
#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct TrustedPeer { pub name: String, pub fingerprint: String, pub added_unix: u64 }
#[derive(Debug, thiserror::Error)]
pub enum TransferError { #[error("invalid transfer name")] InvalidName, #[error("transfer transport is not configured")] NotConfigured, #[error("I/O error: {0}")] Io(#[from] io::Error) }
pub fn validate_name(name: &str) -> Result<(), TransferError> { if name.is_empty() || name == "." || name == ".." || name.contains('/') || name.contains('\\') || name.chars().any(char::is_control) { Err(TransferError::InvalidName) } else { Ok(()) } }
pub fn chunk_count(size: u64) -> u64 { size.div_ceil(CHUNK_SIZE) }
pub fn read_chunk(path: &Path, offset: u64, length: usize) -> Result<Vec<u8>, TransferError> { let mut file = OpenOptions::new().read(true).open(path)?; file.seek(SeekFrom::Start(offset))?; let mut bytes = vec![0; length]; let n = file.read(&mut bytes)?; bytes.truncate(n); Ok(bytes) }
pub fn chunk_sha256(bytes: &[u8]) -> String { Sha256::digest(bytes).iter().map(|b| format!("{b:02x}")).collect() }
pub fn write_chunk(path: &Path, offset: u64, bytes: &[u8]) -> Result<(), TransferError> { let mut file = OpenOptions::new().create(true).write(true).open(path)?; file.seek(SeekFrom::Start(offset))?; file.write_all(bytes)?; file.flush()?; Ok(()) }
#[cfg(test)] mod tests { use super::*; #[test] fn chunks_round_up() { assert_eq!(chunk_count(CHUNK_SIZE + 1), 2); } }
