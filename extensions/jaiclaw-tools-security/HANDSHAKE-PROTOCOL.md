# JaiClaw Security Handshake Protocol Specification

**Version:** 1.0
**Status:** Draft

## Overview

The JaiClaw Security Handshake Protocol establishes an authenticated, encrypted session between a client and a server using ECDH key exchange, challenge-response authentication, and JWT session tokens.

The protocol is transport-agnostic but is typically carried over HTTP/JSON. A compliant server exposes 5 endpoints; a compliant client calls them in sequence.

### Design Goals

- **Language-agnostic** — any language with ECDH, HMAC-SHA256, and JWT support can implement it
- **Forward secrecy** — ephemeral ECDH key pairs per session
- **No LLM in the loop** — all crypto operations are deterministic
- **Bootstrap trust** — optional pre-shared credential validation before key exchange

## Session State Machine

```
                ┌──────────────┐
                │   (start)    │
                └──────┬───────┘
                       │
                POST /capabilities  (optional — discovery)
                       │
                       ▼
                ┌──────────────┐
                │  NEGOTIATE   │ ── POST /negotiate
                └──────┬───────┘     (ECDH key exchange + bootstrap validation)
                       │
                       ▼
                ┌──────────────┐
                │  CHALLENGE   │ ── POST /challenge
                └──────┬───────┘     (server issues nonce)
                       │
                       ▼
                ┌──────────────┐
                │   VERIFY     │ ── POST /verify
                └──────┬───────┘     (client proves identity via HMAC)
                       │
                       ▼
                ┌──────────────┐
                │  ESTABLISH   │ ── POST /establish
                └──────┬───────┘     (server issues JWT session token)
                       │
                       ▼
                ┌──────────────┐
                │  ESTABLISHED │
                └──────────────┘
```

Each step MUST complete successfully before the next step can proceed. The server MUST reject out-of-order requests (e.g., `/verify` before `/challenge`).

## HTTP Endpoint Mapping

All endpoints accept `POST` with `Content-Type: application/json` and return `200 OK` with a JSON body on success.

| Endpoint | Path | Description |
|---|---|---|
| Capabilities | `POST /capabilities` | Returns supported cipher suites, auth methods, and bootstrap trust level |
| Negotiate | `POST /negotiate` | Validates bootstrap credential, performs ECDH key exchange, creates session |
| Challenge | `POST /challenge` | Issues a random nonce for identity verification |
| Verify | `POST /verify` | Verifies the client's HMAC signature of the challenge nonce |
| Establish | `POST /establish` | Creates and returns a JWT session token |

The base URL is implementation-defined (e.g., `https://example.com/mcp/security`).

## Endpoint Specifications

### 1. POST /capabilities

Returns the server's supported algorithms, authentication methods, and bootstrap trust level. This endpoint is optional — a client that already knows the server's capabilities may skip it.

**Request body:** empty object `{}`

**Response:**

```json
{
  "cipherSuites": [
    "ECDH-X25519-AES256-GCM-SHA384",
    "ECDH-P256-AES128-GCM-SHA256",
    "DH-AES256-CBC-SHA256"
  ],
  "authMethods": ["HMAC-SHA256", "JWT"],
  "keyExchangeAlgorithms": ["ECDH", "XDH"],
  "bootstrapTrust": "API_KEY",
  "serverSide": true
}
```

| Field | Type | Required | Description |
|---|---|---|---|
| `cipherSuites` | `string[]` | Yes | Supported cipher suite identifiers |
| `authMethods` | `string[]` | Yes | Supported authentication methods |
| `keyExchangeAlgorithms` | `string[]` | Yes | Supported key exchange algorithms |
| `bootstrapTrust` | `string` | Yes | Bootstrap trust level: `"API_KEY"`, `"CLIENT_CERT"`, `"MUTUAL"`, or `"NONE"` |
| `serverSide` | `boolean` | Yes | Always `true` for server implementations |

---

### 2. POST /negotiate

Performs ECDH key exchange and (optionally) validates bootstrap credentials. This is the first required step in the handshake.

**Request:**

```json
{
  "clientPublicKey": "<base64url-encoded X.509 DER public key>",
  "cipherSuite": "ECDH-P256-AES128-GCM-SHA256",
  "authMethod": "HMAC-SHA256",
  "clientId": "my-agent",
  "clientNonce": "<base64url-encoded 32-byte nonce>",
  "apiKey": "<pre-shared key>",
  "clientSignature": "<base64url HMAC signature>"
}
```

| Field | Type | Required | Description |
|---|---|---|---|
| `clientPublicKey` | `string` | Yes | Base64url-encoded X.509 DER public key (EC P-256 or X25519) |
| `cipherSuite` | `string` | Yes | Selected cipher suite (must be one from `/capabilities`) |
| `authMethod` | `string` | Yes | Selected auth method: `"HMAC-SHA256"` or `"JWT"` |
| `clientId` | `string` | Yes | Client identifier |
| `clientNonce` | `string` | No | Client-generated nonce (Base64url, 32 bytes). Required for `MUTUAL` bootstrap. |
| `apiKey` | `string` | No | Pre-shared API key. Required when bootstrap is `API_KEY` or `MUTUAL`. |
| `clientSignature` | `string` | No | Client nonce signature. Required when bootstrap is `CLIENT_CERT` or `MUTUAL`. |

**Bootstrap validation:**

- `API_KEY`: Server compares `apiKey` against its configured key using constant-time comparison.
- `CLIENT_CERT`: Server verifies `clientSignature` against its registry of allowed client public keys.
- `MUTUAL`: Both `apiKey` and `clientSignature` are validated.
- `NONE` (no bootstrap configured): No credential validation; proceed directly to key exchange.

If bootstrap validation fails, the server MUST return an error and NOT create a session.

**Key exchange logic:**

1. Determine key algorithm from `cipherSuite`:
   - Contains `"X25519"` → X25519/XDH
   - Otherwise → EC P-256/ECDH
2. Decode `clientPublicKey` from Base64url X.509 DER format
3. Generate an ephemeral server key pair (same algorithm)
4. Perform key agreement (ECDH or XDH) to derive the shared secret
5. Compute SHA-256 fingerprint of the shared secret (hex-encoded)

**Response:**

```json
{
  "handshakeId": "550e8400-e29b-41d4-a716-446655440000",
  "serverPublicKey": "<base64url-encoded X.509 DER server public key>",
  "keyFingerprint": "<hex-encoded SHA-256 of shared secret>",
  "algorithm": "ECDH",
  "serverNonce": "<base64url-encoded 32-byte nonce>",
  "sharedSecretEstablished": true,
  "serverSignature": "<base64url HMAC signature>"
}
```

| Field | Type | Required | Description |
|---|---|---|---|
| `handshakeId` | `string` | Yes | Server-assigned session ID (UUID recommended) |
| `serverPublicKey` | `string` | Yes | Base64url-encoded X.509 DER server public key |
| `keyFingerprint` | `string` | Yes | Hex-encoded SHA-256 hash of the raw shared secret |
| `algorithm` | `string` | Yes | Key agreement algorithm used: `"ECDH"` or `"XDH"` |
| `serverNonce` | `string` | Yes | Server-generated nonce (Base64url, 32 bytes) |
| `sharedSecretEstablished` | `boolean` | Yes | Always `true` on success |
| `serverSignature` | `string` | No | HMAC-SHA256 signature of `serverNonce + clientNonce` using the shared secret. Present only when bootstrap is `MUTUAL`. |

**MUTUAL bootstrap — server identity proof:**

When bootstrap trust is `MUTUAL`, the server MUST sign `serverNonce || clientNonce` (string concatenation) with HMAC-SHA256 using the shared secret, and include it as `serverSignature`. The client SHOULD verify this to prevent MITM attacks.

---

### 3. POST /challenge

Issues a random nonce challenge for identity verification. Requires a completed key exchange (shared secret established).

**Request:**

```json
{
  "handshakeId": "550e8400-e29b-41d4-a716-446655440000"
}
```

| Field | Type | Required | Description |
|---|---|---|---|
| `handshakeId` | `string` | Yes | Handshake session ID from `/negotiate` |

**Preconditions:**

- Session MUST exist for the given `handshakeId`
- Key exchange MUST be completed (shared secret established)

**Response:**

```json
{
  "handshakeId": "550e8400-e29b-41d4-a716-446655440000",
  "challenge": "<base64url-encoded 32-byte nonce>",
  "authMethod": "HMAC-SHA256"
}
```

| Field | Type | Required | Description |
|---|---|---|---|
| `handshakeId` | `string` | Yes | Handshake session ID |
| `challenge` | `string` | Yes | Base64url-encoded random nonce (32 bytes) |
| `authMethod` | `string` | Yes | Authentication method to use for verification |

---

### 4. POST /verify

Verifies the client's response to the challenge. The client signs the challenge nonce with HMAC-SHA256 using the shared secret.

**Request:**

```json
{
  "handshakeId": "550e8400-e29b-41d4-a716-446655440000",
  "method": "HMAC-SHA256",
  "credential": "<base64url-encoded HMAC-SHA256 signature>"
}
```

| Field | Type | Required | Description |
|---|---|---|---|
| `handshakeId` | `string` | Yes | Handshake session ID |
| `method` | `string` | Yes | Verification method: `"HMAC-SHA256"` or `"JWT"` |
| `credential` | `string` | Yes | HMAC-SHA256 signature of the challenge nonce, Base64url-encoded. The signing key is the raw shared secret bytes. |

**Verification logic:**

1. Retrieve the challenge nonce from the session
2. Compute `HMAC-SHA256(sharedSecret, challengeNonce)` → Base64url encode
3. Compare with `credential` using constant-time comparison
4. If match: mark identity as verified, set subject to `clientId`

**Preconditions:**

- Session MUST exist for the given `handshakeId`
- Key exchange MUST be completed
- A challenge MUST have been issued

**Response:**

```json
{
  "handshakeId": "550e8400-e29b-41d4-a716-446655440000",
  "authMethod": "HMAC-SHA256",
  "verified": true,
  "subject": "my-agent",
  "verificationDetails": "HMAC-SHA256 signature verified against challenge nonce"
}
```

| Field | Type | Required | Description |
|---|---|---|---|
| `handshakeId` | `string` | Yes | Handshake session ID |
| `authMethod` | `string` | Yes | Verification method used |
| `verified` | `boolean` | Yes | `true` if verification succeeded |
| `subject` | `string\|null` | Yes | Verified identity (clientId) if verified, `null` otherwise |
| `verificationDetails` | `string` | Yes | Human-readable description of the verification outcome |

Note: verification failure is NOT an HTTP error — the server returns `200` with `"verified": false`. The client decides whether to retry or abort.

---

### 5. POST /establish

Creates a JWT session token for the authenticated client. Requires successful identity verification.

**Request:**

```json
{
  "handshakeId": "550e8400-e29b-41d4-a716-446655440000",
  "ttlSeconds": 3600
}
```

| Field | Type | Required | Description |
|---|---|---|---|
| `handshakeId` | `string` | Yes | Handshake session ID |
| `ttlSeconds` | `integer` | No | Token TTL in seconds. Server uses its configured default if omitted. |

**Preconditions:**

- Session MUST exist for the given `handshakeId`
- Key exchange MUST be completed
- Identity MUST be verified (`verified == true`)

**JWT claims:**

The session token is a signed JWT (HS256) with these claims:

| Claim | Description |
|---|---|
| `sub` | The verified subject (clientId) |
| `iat` | Issued-at timestamp |
| `exp` | Expiration timestamp (`iat + ttlSeconds`) |
| `handshakeId` | The handshake session ID |
| `cipherSuite` | The negotiated cipher suite |
| `keyFingerprint` | SHA-256 fingerprint of the shared secret |

The JWT is signed with HMAC-SHA256 using the raw shared secret bytes as the signing key.

**Response:**

```json
{
  "handshakeId": "550e8400-e29b-41d4-a716-446655440000",
  "sessionToken": "eyJhbGciOiJIUzI1NiJ9...",
  "cipherSuite": "ECDH-P256-AES128-GCM-SHA256",
  "expiresInSeconds": 3600,
  "subject": "my-agent",
  "summary": "Security handshake complete. Use this sessionToken as a Bearer token for subsequent MCP tool calls."
}
```

| Field | Type | Required | Description |
|---|---|---|---|
| `handshakeId` | `string` | Yes | Handshake session ID |
| `sessionToken` | `string` | Yes | Signed JWT session token |
| `cipherSuite` | `string` | Yes | Negotiated cipher suite |
| `expiresInSeconds` | `integer` | Yes | Token TTL in seconds |
| `subject` | `string` | Yes | Verified identity |
| `summary` | `string` | Yes | Human-readable summary |

After establishment, the client includes the session token as a `Bearer` token in the `Authorization` header of subsequent requests.

---

## Cryptographic Requirements

### Key Exchange

| Algorithm | Curve | Use When |
|---|---|---|
| ECDH | P-256 (secp256r1) | Cipher suite does NOT contain "X25519" |
| XDH | X25519 | Cipher suite contains "X25519" |

**Public key encoding:** X.509 DER format, then Base64url (no padding).

**Key agreement:** Standard ECDH/XDH — the output is the raw shared secret bytes.

### HMAC-SHA256

Used for:
1. **Challenge-response** — sign the challenge nonce with the shared secret
2. **Server identity proof** (MUTUAL) — sign `serverNonce || clientNonce` with the shared secret
3. **JWT signing** — HS256 using the shared secret as the key

All HMAC inputs are UTF-8 encoded strings. Outputs are Base64url-encoded (no padding).

### SHA-256 Fingerprint

The key fingerprint is `SHA-256(rawSharedSecret)` encoded as a lowercase hex string.

### Nonces

All nonces are 32 bytes of cryptographically secure random data, Base64url-encoded (no padding).

### Base64url

All binary-to-string encoding uses Base64url (RFC 4648 §5) **without padding** (`=` characters omitted).

---

## Error Handling

Errors are returned as JSON with an `error` field:

```json
{
  "error": "Bootstrap authentication failed: API key mismatch"
}
```

### Error Conditions by Endpoint

| Endpoint | Error Condition | Description |
|---|---|---|
| `/negotiate` | Bootstrap validation failed | Invalid API key, signature, or missing required credential |
| `/negotiate` | Missing required parameter | `clientPublicKey`, `cipherSuite`, `authMethod`, or `clientId` missing |
| `/challenge` | Unknown handshake session | `handshakeId` not found |
| `/challenge` | Key exchange not completed | Shared secret not yet established |
| `/verify` | Unknown handshake session | `handshakeId` not found |
| `/verify` | No challenge issued | `/challenge` was not called first |
| `/verify` | Key exchange not completed | Shared secret not yet established |
| `/establish` | Unknown handshake session | `handshakeId` not found |
| `/establish` | Identity not verified | `/verify` did not succeed |
| `/establish` | Key exchange not completed | Shared secret not yet established |

Servers SHOULD use descriptive error messages. Servers MUST NOT leak cryptographic material in error responses.

---

## Bootstrap Trust Modes

Bootstrap trust provides pre-ECDH authentication to prevent unauthorized key exchange.

### API_KEY

The client sends a pre-shared API key in the `/negotiate` request. The server validates using constant-time comparison.

```json
{ "apiKey": "my-secret-key", ... }
```

### CLIENT_CERT

The client signs its nonce with a pre-registered key pair. The server verifies against a registry of allowed client public keys using EC-DSA signature verification.

```json
{ "clientSignature": "<base64url EC-DSA signature>", ... }
```

### MUTUAL

Combines `API_KEY` (client authenticates to server) and server identity proof (server signs its nonce back to the client). Prevents MITM.

```json
{
  "apiKey": "my-secret-key",
  "clientSignature": "<base64url signature>",
  ...
}
```

Server response includes `serverSignature` — the client SHOULD verify it.

### NONE

No bootstrap validation. Any client can initiate a key exchange. Suitable for development/testing or when transport-level security (mTLS) provides sufficient trust.

---

## Complete Client Flow Example

```
1. POST /capabilities → {}
   ← { cipherSuites: [...], authMethods: [...], bootstrapTrust: "API_KEY" }

2. Client generates P-256 key pair
   clientPublicKey = base64url(keyPair.public.encoded)

3. POST /negotiate → {
     clientPublicKey, cipherSuite: "ECDH-P256-AES128-GCM-SHA256",
     authMethod: "HMAC-SHA256", clientId: "my-agent", apiKey: "secret"
   }
   ← { handshakeId, serverPublicKey, keyFingerprint, algorithm, serverNonce }

4. Client performs ECDH:
   sharedSecret = keyAgreement(clientPrivateKey, decode(serverPublicKey), "ECDH")

5. POST /challenge → { handshakeId }
   ← { handshakeId, challenge, authMethod: "HMAC-SHA256" }

6. Client signs challenge:
   credential = base64url(HMAC-SHA256(sharedSecret, challenge))

7. POST /verify → { handshakeId, method: "HMAC-SHA256", credential }
   ← { handshakeId, verified: true, subject: "my-agent" }

8. POST /establish → { handshakeId }
   ← { handshakeId, sessionToken: "eyJ...", expiresInSeconds: 3600 }

9. Client uses sessionToken as Bearer token:
   Authorization: Bearer eyJ...
```

---

## Supported Cipher Suites

| Cipher Suite | Key Exchange | Encryption | Hash |
|---|---|---|---|
| `ECDH-X25519-AES256-GCM-SHA384` | X25519/XDH | AES-256-GCM | SHA-384 |
| `ECDH-P256-AES128-GCM-SHA256` | P-256/ECDH | AES-128-GCM | SHA-256 |
| `DH-AES256-CBC-SHA256` | DH | AES-256-CBC | SHA-256 |

Implementors MAY support additional cipher suites. The client selects one from the server's advertised list in the `/negotiate` request.

---

## Conformance

A compliant handshake server MUST:

1. Implement all 5 endpoints (`/capabilities`, `/negotiate`, `/challenge`, `/verify`, `/establish`)
2. Enforce the session state machine (reject out-of-order requests)
3. Use cryptographically secure random nonces (32 bytes minimum)
4. Use constant-time comparison for HMAC verification and API key validation
5. Never expose the shared secret in any response
6. Sign JWTs with HS256 using the shared secret
7. Support at least `ECDH-P256-AES128-GCM-SHA256` cipher suite and `HMAC-SHA256` auth method

A compliant handshake client MUST:

1. Generate ephemeral key pairs per handshake session
2. Call endpoints in the correct order
3. Use the shared secret only for HMAC signing and JWT verification — never transmit it
4. Verify `serverSignature` when bootstrap trust is `MUTUAL`
