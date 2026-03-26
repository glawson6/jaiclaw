# JaiClaw :: Tools :: Security

LLM-driven security handshake tools — ECDH key exchange, challenge-response authentication, and JWT session establishment.

## Architecture

The calling LLM sees a **single tool** (`security_handshake`) and gets back a session token. All crypto operations happen deterministically inside the tool — no LLM in the loop for the handshake itself.

```
Calling LLM                       SecurityHandshakeTool             MCP Server
    │                                     │                            │
    ├─ security_handshake ───────────────►│                            │
    │  (mcpServerUrl, clientId)           │                            │
    │                                     ├─ 1. GET /capabilities ────►│
    │                                     │◄── cipher suites ──────────┤
    │                                     ├─ 2. generate keypair       │
    │                                     ├─ 3. POST /negotiate ──────►│
    │                                     │◄── serverPublicKey ────────┤
    │                                     ├─ 4. key agreement (local)  │
    │                                     ├─ 5. POST /challenge ──────►│
    │                                     │◄── challenge nonce ────────┤
    │                                     ├─ 6. HMAC sign (local)      │
    │                                     ├─ 7. POST /verify ─────────►│
    │                                     │◄── verified ───────────────┤
    │                                     ├─ 8. POST /establish ──────►│
    │                                     │◄── sessionToken ───────────┤
    │◄── { sessionToken, ... } ───────────┤                            │
    │                                     │                            │
    ├─ protected_tool(token) ─────────────────────────────────────────►│
```

## Modes

| Mode | Description |
|------|-------------|
| **LOCAL** | Both client and server in-process. For demos, testing, single-process deployments. |
| **HTTP_CLIENT** | Tools call a remote MCP server's handshake endpoints. |
| **ORCHESTRATED** | Same as HTTP_CLIENT (deterministic — no LLM orchestration). |

## Embabel Agent

When Embabel is on the classpath, a `SecurityHandshakeAgent` GOAP agent is registered. It uses the same deterministic crypto operations but provides observable, traceable execution via the Embabel blackboard:

```
HandshakeRequest → ServerHello → KeyExchangeResult → AuthResult → SessionEstablished
```

## Configuration

```yaml
jaiclaw:
  security:
    handshake:
      mode: LOCAL                    # LOCAL, HTTP_CLIENT, ORCHESTRATED
      mcp-server-url: http://...     # MCP server URL (HTTP_CLIENT mode)
      api-key: ...                   # Pre-shared API key for bootstrap
      server:
        enabled: true                # Enable server-side endpoints
        token-ttl-seconds: 3600      # Session token TTL
```

## Protocol Specification

See [`HANDSHAKE-PROTOCOL.md`](HANDSHAKE-PROTOCOL.md) for the language-agnostic protocol specification. Any developer (Java, Python, Go, TypeScript, etc.) can use it to implement a compliant handshake server or client.

For Java server implementors, the [`HandshakeServerEndpoint`](src/main/java/io/jaiclaw/tools/security/HandshakeServerEndpoint.java) interface provides typed request/response records matching the protocol spec.

## Usage

The LLM calls one tool:

```json
{
  "name": "security_handshake",
  "parameters": {
    "clientId": "my-agent"
  }
}
```

Response:

```json
{
  "handshakeId": "abc-123",
  "sessionToken": "eyJhbG...",
  "cipherSuite": "ECDH-P256-AES128-GCM-SHA256",
  "expiresInSeconds": 3600,
  "summary": "Security handshake complete. Use sessionToken as a Bearer token."
}
```
