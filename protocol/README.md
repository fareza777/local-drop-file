# Pushvia protocol (v1 draft)

Transfers require user authorization on a trusted local network. Bind only to a LAN interface, use an unpredictable 256-bit URL-safe bearer token, and stop at the configured deadline. Do not expose via port forwarding.

- `GET /api/v1/files` — authenticated listing.
- `POST /api/v1/files` — authenticated multipart upload (`file` field).
- `GET /api/v1/files/{id}` — authenticated download by opaque server-generated ID.

Reject traversal, separators, controls, oversized requests, and malformed authorization. Stream bounded buffers, upload to a temporary file before atomic publication, and redact tokens from logs. TLS is required beyond a user-authorized trusted LAN; local HTTP is observable by LAN peers. Pairing requires explicit confirmation of a public-key fingerprint and supports revocation. Never trust IP address alone.

QR payloads contain origin, short-lived token, and protocol version. Show expiry and rotate on server restart. Android SAF operates on content URIs and retains only user-authorized grants.

## Remote private mode

When a Tailscale or ZeroTier interface is present, the desktop companion may advertise and connect through that private address. Pushvia does not provide a relay, identity provider, or cloud storage. The user remains responsible for the overlay network ACL and device authorization. Prefer direct LAN addresses first, then overlay addresses, and reject public interfaces unless explicitly enabled.

## Planned direct-transfer extensions

Desktop pairing uses a device public-key fingerprint plus one-time QR/PIN confirmation. Large files use fixed-size chunks with `(transfer_id, file_id, offset, sha256)` and HTTP range-style retry/resume. The receiver commits only after final SHA-256 verification.
