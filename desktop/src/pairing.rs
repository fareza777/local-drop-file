use rand::{distributions::Uniform, Rng};
use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};
use std::time::{Duration, SystemTime, UNIX_EPOCH};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PairingRequest { pub peer_name: String, pub fingerprint: String, pub one_time_pin: String, pub expires_unix: u64 }

pub fn fingerprint(public_key: &[u8]) -> String {
    Sha256::digest(public_key).iter().map(|b| format!("{b:02x}")).collect::<Vec<_>>().chunks(4).map(|c| c.join("")).collect::<Vec<_>>().join(":")
}
pub fn create_pairing_request(peer_name: String, public_key: &[u8], lifetime: Duration) -> PairingRequest {
    let pin: String = rand::thread_rng().sample_iter(Uniform::new(0u8, 10)).take(6).map(|n| char::from(b'0' + n)).collect();
    let expires_unix = SystemTime::now().duration_since(UNIX_EPOCH).unwrap_or_default().as_secs().saturating_add(lifetime.as_secs());
    PairingRequest { peer_name, fingerprint: fingerprint(public_key), one_time_pin: pin, expires_unix }
}
pub fn verify_fingerprint(expected: &str, presented: &str) -> bool {
    if expected.len() != presented.len() { return false; }
    expected.as_bytes().iter().zip(presented.as_bytes()).fold(0u8, |d, (a,b)| d | (a ^ b)) == 0
}
pub fn verify_pin(request: &PairingRequest, presented: &str, now_unix: u64) -> bool {
    now_unix <= request.expires_unix && verify_fingerprint(&request.one_time_pin, presented)
}
