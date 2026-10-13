use serde::{Deserialize, Serialize};
use std::net::{IpAddr, UdpSocket};

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub enum NetworkKind { Lan, Tailscale, ZeroTier }

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct NetworkInterface { pub name: String, pub address: IpAddr, pub kind: NetworkKind, pub preferred: bool }

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Peer { pub name: String, pub address: String, pub interface: NetworkKind, pub fingerprint: Option<String> }

/// Returns usable local addresses without opening a listener. Overlay addresses can be
/// supplied by the host integration through LOCALDROP_TAILSCALE_ADDRESS/LOCALDROP_ZEROTIER_ADDRESS.
pub fn discover_interfaces() -> Vec<NetworkInterface> {
    let mut result = Vec::new();
    if let Ok(socket) = UdpSocket::bind("0.0.0.0:0") {
        let _ = socket.connect("1.1.1.1:80");
        if let Ok(address) = socket.local_addr().map(|a| a.ip()) {
            if !address.is_loopback() { result.push(NetworkInterface { name: "LAN".into(), address, kind: NetworkKind::Lan, preferred: true }); }
        }
    }
    for (name, kind, key) in [("Tailscale", NetworkKind::Tailscale, "LOCALDROP_TAILSCALE_ADDRESS"), ("ZeroTier", NetworkKind::ZeroTier, "LOCALDROP_ZEROTIER_ADDRESS")] {
        if let Ok(value) = std::env::var(key) { if let Ok(address) = value.parse() { result.push(NetworkInterface { name: name.into(), address, kind, preferred: result.is_empty() }); } }
    }
    result
}

pub fn discover_peers() -> Vec<Peer> {
    discover_interfaces().into_iter().map(|i| Peer { name: "This device".into(), address: i.address.to_string(), interface: i.kind, fingerprint: None }).collect()
}
