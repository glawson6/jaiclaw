# Security Handshake Example

LLM-driven security handshake demo — an LLM autonomously performs the complete ECDH handshake ceremony using tools, obtains a session token, and calls a protected MCP endpoint. Two tool calls, zero human intervention.

## What This Demonstrates

- **LLM-autonomous security** — the model calls `security_handshake` then `protected_get_secret_data` with no human guidance
- **Spring AI ChatClient** with explicit tool callbacks (no agent runtime)
- **SpringAiToolBridge** — bridges JaiClaw `ToolCallback` to Spring AI `ToolCallback`
- **SecurityTools.all()** — bundles all security handshake tools from `jaiclaw-tools-security`
- **LOCAL handshake mode** — in-process cryptographic handshake (no HTTP server needed)
- **ApplicationRunner** startup — runs the demo immediately on boot

## Architecture

Where this example fits in JaiClaw:

```
┌──────────────────────────────────────────────────────────┐
│               SECURITY HANDSHAKE DEMO APP                  │
│                (standalone Spring Boot)                    │
├──────────────────┬───────────────────────────────────────┤
│ Startup          │  HandshakeDemoRunner (ApplicationRunner) │
├──────────────────┼───────────────────────────────────────┤
│ LLM              │  Spring AI ChatClient                    │
│                  │  → system prompt + user prompt            │
│                  │  → tool callbacks (security + protected)  │
├──────────────────┼───────────────────────────────────────┤
│ Tools (8 total)  │  SecurityTools.all() → 7 security tools  │
│                  │  ProtectedMcpTool → 1 protected tool     │
│                  │  → all bridged via SpringAiToolBridge     │
├──────────────────┼───────────────────────────────────────┤
│ Security         │  CryptoService (ECDH, HMAC)               │
│                  │  HandshakeSessionStore (in-memory)         │
│                  │  LOCAL mode (no HTTP)                      │
└──────────────────┴───────────────────────────────────────┘

Data flow:
  ApplicationRunner
        │
        ▼
  ChatClient.prompt()
        │
        ├──tool call──► security_handshake
        │                 (ECDH + HMAC internally)
        │◄──session token──┘
        │
        ├──tool call──► protected_get_secret_data
        │                 (validates Bearer token)
        │◄──secret data──┘
        │
        ▼
  LLM summarizes result
```

## Prerequisites

- Java 21+
- JaiClaw built and installed (`./mvnw install -DskipTests` from project root)
- Anthropic API key

## Build & Run

This example requires the [security-handshake-server](../security-handshake-server/) to be running for HTTP_CLIENT mode, or runs in LOCAL mode by default.

**LOCAL mode** (in-process, no server needed):

```bash
cd jaiclaw-examples/security-handshake
export JAVA_HOME=$HOME/.sdkman/candidates/java/21.0.9-oracle
ANTHROPIC_API_KEY=sk-ant-... ../../mvnw spring-boot:run
```

### Using MiniMax

To run with MiniMax instead of Anthropic:

```bash
AI_PROVIDER=minimax MINIMAX_ENABLED=true MINIMAX_API_KEY=your-key ../../mvnw spring-boot:run
```


**HTTP_CLIENT mode** (connects to security-handshake-server):

```bash
# Terminal 1: start the server
cd jaiclaw-examples/security-handshake-server
../../mvnw spring-boot:run

# Terminal 2: start the client
cd jaiclaw-examples/security-handshake
ANTHROPIC_API_KEY=sk-ant-... ../../mvnw spring-boot:run
```

The demo runs automatically on startup — check the console logs for the LLM's tool calls and final response.

## Testing It

This example runs automatically on boot (no REST endpoints). Watch the console output for:

1. List of available tools (8 total)
2. LLM calling `security_handshake`
3. LLM calling `protected_get_secret_data` with the session token
4. Final LLM response summarizing the secret data

## Related

- [security-handshake-server](../security-handshake-server/) — standalone REST server implementing the handshake protocol
- `jaiclaw-tools-security` module — reusable handshake protocol implementation
