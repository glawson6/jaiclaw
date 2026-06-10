# Identity Linking

Module: `jaiclaw-identity`

## Overview

Links user identities across channels so the agent recognizes that the same person on Slack, Telegram, and Discord is one user. Uses a canonical user ID that maps to channel-specific user IDs.

## Usage

```java
// Create the service
IdentityLinkStore store = new IdentityLinkStore(Path.of("data/identity-links.json"));
IdentityLinkService service = new IdentityLinkService(store);

// Link a Telegram user to a canonical ID (auto-generates canonical ID if null)
IdentityLink link1 = service.link(null, "telegram", "12345");
String canonicalId = link1.canonicalUserId(); // e.g., "a1b2c3d4-..."

// Link a Slack user to the same canonical ID
IdentityLink link2 = service.link(canonicalId, "slack", "U98765");

// Now both channel users resolve to the same canonical ID
IdentityResolver resolver = new IdentityResolver(store);
String resolved1 = resolver.resolve("telegram", "12345");  // → canonicalId
String resolved2 = resolver.resolve("slack", "U98765");    // → canonicalId (same)

// Unknown users return their channel-specific ID
String unknown = resolver.resolve("discord", "999");        // → "999"

// Unlink
service.unlink("telegram", "12345");
```

## Key Classes

| Class | Purpose |
|-------|---------|
| `IdentityLinkService` | Link/unlink operations, auto UUID generation |
| `IdentityLinkStore` | JSON file persistence, ConcurrentHashMap for fast lookup |
| `IdentityResolver` | Resolves channel+userId → canonical user ID |
| `IdentityLink` | Record: canonicalUserId, channel, channelUserId (in `jaiclaw-core`) |

## Storage

Links persist to a JSON file (e.g., `data/identity-links.json`):

```json
[
  {"canonicalUserId": "a1b2c3d4-...", "channel": "telegram", "channelUserId": "12345"},
  {"canonicalUserId": "a1b2c3d4-...", "channel": "slack", "channelUserId": "U98765"}
]
```

The store uses a `ConcurrentHashMap` keyed by `channel:channelUserId` for O(1) lookups.

## OAuth Credential Management

Beyond identity linking, this module manages upstream provider credentials via OAuth:

- **PKCE Authorization Code Flow** — Browser-based login for Chutes, OpenAI Codex, Google Gemini
- **Device Code Flow (RFC 8628)** — Headless login for Qwen, MiniMax
- **Token Refresh** — Transparent refresh with provider-specific refreshers
- **Session Rotation** — Round-robin with cooldown across credentials
- **CLI Sync** — Reads credentials from Claude CLI, Codex CLI, Qwen CLI, MiniMax CLI
- **Multi-Agent Inheritance** — Sub-agents inherit credentials from the main agent

### Key OAuth Classes

| Class | Purpose |
|-------|---------|
| `OAuthFlowManager` | Orchestrates login flows, stores credentials |
| `AuthorizationCodeFlow` | PKCE auth code: URL construction, code exchange, userinfo |
| `DeviceCodeFlow` | Device code request + polling (RFC 8628) |
| `OAuthCallbackServer` | Loopback HTTP server for redirect callbacks |
| `AuthProfileStoreManager` | File-based credential store with locking and merge |
| `OAuthProviderConfig` | Provider endpoints, client credentials, scopes |

## Script Integration

OAuth credential management is accessible from startup scripts (no Java REPL required):

```bash
# Check all auth profiles and external CLI credentials
./start.sh auth                  # colored table
./start.sh auth json             # machine-readable JSON

# OAuth login for upstream providers
./start.sh login                 # list providers
./start.sh login chutes          # browser-based OAuth (Chutes AI)
./start.sh login qwen-portal     # device code flow (Qwen)

# Standalone tools
./scripts/auth-status.sh simple  # one-line status (exit codes: 0=OK, 1=EXPIRED, 2=EXPIRING, 3=MISSING)
jbang JaiClawAuth.java status    # profile status via JBang (no Spring context)
jbang JaiClawAuth.java logout chutes:user@email.com  # remove a profile

# Monitoring (cron)
./scripts/setup-auth-monitor.sh  # install cron/systemd timer for expiry alerts
```

All launch commands (`./start.sh local`, `shell`, `cli`, `docker`) automatically check auth status on startup and warn about expiring/expired tokens.

**Credential files checked:**
- JaiClaw profiles: `~/.jaiclaw/agents/default/agent/auth-profiles.json`
- Claude Code: `~/.claude/.credentials.json`
- Codex: `~/.codex/auth.json`
- Qwen: `~/.qwen/oauth_creds.json`
- MiniMax: `~/.minimax/oauth_creds.json`

## Integration

When a message arrives on any channel, use `IdentityResolver.resolve(channel, userId)` to get the canonical ID. This canonical ID can then be used for:

- Cross-channel session continuity
- Unified user preferences
- Consistent audit trails
- Per-user rate limiting across all channels

## Testing

- **90 unit tests** (Spock specs) covering all classes in isolation
- **18 integration tests** running under the `integration-test` Maven profile, exercising full OAuth flows end-to-end against a local mock HTTP server

```bash
./mvnw test -pl :jaiclaw-identity -o                         # unit tests
./mvnw verify -pl :jaiclaw-identity -Pintegration-test -o    # unit + integration
```

See [OAuth Integration Tests Architecture](../OAUTH-INTEGRATION-TESTS.md) for details on the mock server pattern and test coverage.
