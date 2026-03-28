# JaiClaw OAuth Implementation Plan

> Port of OpenClaw's `auth-profiles` system to the `jaiclaw-identity` module.

## Background

OpenClaw manages multi-provider OAuth credentials via a file-based `auth-profiles.json` store with PKCE authorization code flows, device code flows, token refresh, multi-account rotation, and per-session profile overrides. JaiClaw currently has no OAuth support — `jaiclaw-security` handles JWT/API-key *gateway authentication*, but not upstream *provider credential management*.

### What Exists Today

| Module | Current State |
|--------|--------------|
| `jaiclaw-identity` | Cross-channel identity linking only (3 classes, no Spring beans, no auto-config) |
| `jaiclaw-security` | HTTP gateway auth: API key, JWT (HMAC), or none — protects inbound requests |
| `jaiclaw-core` | `TenantContext`, `TenantGuard`, `Session` record, `IdentityLink` record |
| `jaiclaw-config` | `TenantConfigProperties`, `IdentityProperties` (bot persona, not user identity) |

### What We're Building

A provider credential management system that:
1. Stores API keys, static tokens, and OAuth credentials per-agent
2. Refreshes expired OAuth tokens transparently
3. Supports PKCE authorization code and device code flows
4. Rotates credentials across sessions (round-robin with cooldowns)
5. Detects remote/headless environments and falls back to manual URL paste
6. Integrates with the existing `jaiclaw-security` and `jaiclaw-shell` modules

---

## Architecture

### Module Placement

The OAuth credential system spans three layers:

```
jaiclaw-core (pure Java — new types)
  └─ AuthProfileCredential (sealed interface)
  └─ AuthProfileStore (record)
  └─ CredentialState, ProfileUsageStats, etc.

jaiclaw-identity (extended — credential store + refresh + OAuth flows)
  └─ AuthProfileStoreManager (file I/O, locking, merge)
  └─ TokenRefresher SPI + provider impls
  └─ OAuthFlowManager (PKCE + device code)
  └─ SessionAuthProfileResolver (round-robin override)

jaiclaw-spring-boot-starter (auto-config wiring)
  └─ JaiClawIdentityAutoConfiguration (new, after Gateway)
```

**Rationale:** The identity module already handles "who is this user" — OAuth credentials answer "how does this user authenticate with upstream providers." The two concerns are complementary and share the same file-store patterns.

### Package Structure

```
io.jaiclaw.core.auth/                    ← new package in jaiclaw-core
  AuthProfileCredential.java              (sealed interface)
  ApiKeyCredential.java                   (record, permits)
  TokenCredential.java                    (record, permits)
  OAuthCredential.java                    (record, permits)
  AuthProfileStore.java                   (record — the on-disk structure)
  ProfileUsageStats.java                  (record)
  AuthProfileFailureReason.java           (enum)
  CredentialState.java                    (enum: VALID, EXPIRED, MISSING, INVALID)
  CredentialEligibility.java              (record: eligible + reasonCode)

io.jaiclaw.identity.auth/                 ← new package in jaiclaw-identity
  AuthProfileStoreManager.java            (load/save/merge with file locking)
  AuthProfileStoreSerializer.java         (Jackson ser/deser for sealed types)
  CredentialStateEvaluator.java           (pure functions: expiry check, eligibility)
  TokenRefresher.java                     (SPI interface)
  GenericOAuthTokenRefresher.java         (standard refresh_token grant)
  ProviderTokenRefresherRegistry.java     (dispatches to provider-specific refreshers)
  AuthProfileResolver.java               (resolves API key/token for a profile ID)
  SessionAuthProfileOverride.java         (round-robin rotation per session)

io.jaiclaw.identity.oauth/                ← new package in jaiclaw-identity
  OAuthFlowManager.java                  (orchestrates PKCE + device code flows)
  PkceGenerator.java                     (PKCE verifier + S256 challenge)
  AuthorizationCodeFlow.java             (loopback HTTP server, code exchange)
  DeviceCodeFlow.java                    (polling loop)
  OAuthCallbackServer.java               (lightweight HTTP server for redirect)
  RemoteEnvironmentDetector.java          (SSH/VPS/Codespaces detection)
  OAuthFlowResult.java                   (record: tokens + email + metadata)
  OAuthProviderConfig.java               (record: authorizeUrl, tokenUrl, scopes, etc.)

io.jaiclaw.identity.oauth.provider/       ← provider-specific configs
  ChutesOAuthProvider.java
  OpenAiCodexOAuthProvider.java
  GoogleGeminiOAuthProvider.java
  QwenDeviceCodeProvider.java
  MiniMaxDeviceCodeProvider.java
```

---

## Phase 1: Core Types (jaiclaw-core)

**Goal:** Define the credential model as pure Java records/sealed interfaces with zero Spring dependency.

### Types to Add

```java
// Sealed credential hierarchy
public sealed interface AuthProfileCredential
    permits ApiKeyCredential, TokenCredential, OAuthCredential {
    String provider();
    String email();  // nullable
}

public record ApiKeyCredential(
    String provider, String key, String keyRef,
    String email, Map<String, String> metadata
) implements AuthProfileCredential {}

public record TokenCredential(
    String provider, String token, String tokenRef,
    Long expires, String email
) implements AuthProfileCredential {}

public record OAuthCredential(
    String provider, String access, String refresh,
    long expires, String email, String clientId,
    String accountId, String projectId, String enterpriseUrl
) implements AuthProfileCredential {}
```

```java
// Store structure
public record AuthProfileStore(
    int version,
    Map<String, AuthProfileCredential> profiles,
    Map<String, List<String>> order,       // provider → ordered profileIds
    Map<String, String> lastGood,          // provider → last-known-good profileId
    Map<String, ProfileUsageStats> usageStats
) {}

// Usage tracking per profile
public record ProfileUsageStats(
    Long lastUsed, Long cooldownUntil, Long disabledUntil,
    AuthProfileFailureReason disabledReason,
    int errorCount, Map<AuthProfileFailureReason, Integer> failureCounts,
    Long lastFailureAt
) {}

// Failure reasons
public enum AuthProfileFailureReason {
    AUTH, AUTH_PERMANENT, FORMAT, OVERLOADED, RATE_LIMIT,
    BILLING, TIMEOUT, MODEL_NOT_FOUND, SESSION_EXPIRED, UNKNOWN
}

// Expiry evaluation
public enum CredentialState { VALID, EXPIRED, MISSING, INVALID }

public record CredentialEligibility(boolean eligible, String reasonCode) {}
```

### Profile ID Convention

`"{provider}:{name}"` — e.g., `"anthropic:default"`, `"openai-codex:user@example.com"`.

---

## Phase 2: Store Manager (jaiclaw-identity)

**Goal:** File-based credential persistence with locking, legacy migration, and merge semantics.

### File Locations

| File | Path |
|------|------|
| Primary store | `~/.jaiclaw/agents/{agentId}/auth-profiles.json` |
| Lock file | `~/.jaiclaw/agents/{agentId}/auth-profiles.json.lock` |
| Base dir override | `$JAICLAW_STATE_DIR` env var |

### AuthProfileStoreManager

```java
public class AuthProfileStoreManager {
    // Load: read JSON → coerce → validate → return
    public AuthProfileStore load(Path agentDir);

    // Save: strip inline secrets if ref exists → lock → write
    public void save(Path agentDir, AuthProfileStore store);

    // Merge: agent store overrides main store (shallow merge)
    public AuthProfileStore merge(AuthProfileStore base, AuthProfileStore override);

    // Upsert: load → set profile → save (under lock)
    public void upsertProfile(Path agentDir, String profileId, AuthProfileCredential credential);

    // Order management
    public void setProfileOrder(Path agentDir, String provider, List<String> order);
    public void markProfileGood(Path agentDir, String provider, String profileId);
}
```

### File Locking Strategy

Use `java.nio.channels.FileLock` via `FileChannel`:
- Exclusive lock for writes
- Retry with exponential backoff (10 retries, 100ms base, 30s stale timeout)
- Lock file is separate from data file (`auth-profiles.json.lock`)

### Serialization

Jackson polymorphic deserialization for the sealed `AuthProfileCredential`:
- `@JsonTypeInfo(use = Id.NAME, property = "type")`
- `@JsonSubTypes` on the sealed interface
- Alias `"mode"` → `"type"` and `"apiKey"` → `"key"` for OpenClaw compat (if cross-compat needed)

---

## Phase 3: Credential Resolution & Refresh (jaiclaw-identity)

**Goal:** Transparent credential resolution — callers ask for "the API key for profile X" and get back a valid token.

### AuthProfileResolver

```java
public class AuthProfileResolver {
    // Returns { apiKey, provider, email } or throws
    public ResolvedCredential resolve(String profileId, Path agentDir);
}
```

Resolution logic:
1. Load store for `agentDir`
2. Look up `profiles[profileId]`
3. Dispatch by credential type:
   - `ApiKeyCredential` → return key (or resolve `keyRef`)
   - `TokenCredential` → check expiry → return token (or resolve `tokenRef`)
   - `OAuthCredential` → if `now < expires`, return `access`; else refresh

### TokenRefresher SPI

```java
public interface TokenRefresher {
    String providerId();
    OAuthCredential refresh(OAuthCredential current) throws TokenRefreshException;
}
```

Built-in implementations:
- `GenericOAuthTokenRefresher` — standard `grant_type=refresh_token` POST to token URL
- Provider-specific refreshers registered via `ProviderTokenRefresherRegistry`

### Refresh Under Lock

```java
public OAuthCredential refreshWithLock(String profileId, Path agentDir) {
    // 1. Acquire file lock
    // 2. Re-read store (another process may have refreshed)
    // 3. If still valid → return (no-op)
    // 4. Call TokenRefresher.refresh()
    // 5. Update store and save
    // 6. Release lock
}
```

### CredentialStateEvaluator

Port of OpenClaw's `credential-state.ts` — pure static methods:

```java
public static CredentialState resolveTokenExpiryState(Long expires);
public static CredentialEligibility evaluateEligibility(AuthProfileCredential credential);
```

---

## Phase 4: OAuth Flows (jaiclaw-identity)

**Goal:** Interactive OAuth flows for acquiring initial credentials.

### PKCE Authorization Code Flow

```
1. Generate PKCE verifier + S256 challenge
2. Build authorize URL with code_challenge, state, redirect_uri
3. Detect environment:
   a. Local desktop → open browser + start loopback HTTP server
   b. Remote/headless → print URL + prompt user to paste redirect URL
4. Receive authorization code (via callback or paste)
5. Exchange code for tokens (POST to token URL)
6. Fetch userinfo (optional, provider-specific)
7. Store credentials in auth-profiles.json
```

### PkceGenerator

```java
public record PkceChallenge(String verifier, String challenge) {}

public static PkceChallenge generate() {
    byte[] random = new byte[32];
    new SecureRandom().nextBytes(random);
    String verifier = HexFormat.of().formatHex(random);
    byte[] sha256 = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(UTF_8));
    String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(sha256);
    return new PkceChallenge(verifier, challenge);
}
```

### OAuthCallbackServer

Lightweight loopback HTTP server using `com.sun.net.httpserver.HttpServer`:
- Binds to `127.0.0.1:{port}` (provider-specific port)
- Handles single GET request to callback path
- Extracts `code` and `state` query parameters
- Validates `state` matches expected value (CSRF protection)
- Returns HTML success page, then signals completion via `CompletableFuture`
- Shuts down after receiving the callback (or on timeout)

### DeviceCodeFlow

```
1. POST device/code request → receive { device_code, user_code, verification_uri, interval }
2. Display user_code + verification_uri to user
3. Poll token endpoint every {interval} seconds
4. Handle: authorization_pending (continue), slow_down (increase interval), access_denied (abort)
5. On success: return tokens
```

### RemoteEnvironmentDetector

```java
public static boolean isRemote() {
    // SSH session
    if (System.getenv("SSH_CLIENT") != null) return true;
    if (System.getenv("SSH_TTY") != null) return true;
    if (System.getenv("SSH_CONNECTION") != null) return true;
    // Dev containers
    if (System.getenv("REMOTE_CONTAINERS") != null) return true;
    if (System.getenv("CODESPACES") != null) return true;
    // Headless Linux (not WSL)
    if (isHeadlessLinux()) return true;
    return false;
}
```

### OAuthProviderConfig

```java
public record OAuthProviderConfig(
    String providerId,
    String authorizeUrl,
    String tokenUrl,
    String userinfoUrl,        // nullable
    String clientId,
    String clientSecret,       // nullable
    String redirectUri,
    int callbackPort,
    String callbackPath,
    List<String> scopes,
    OAuthFlowType flowType     // AUTHORIZATION_CODE or DEVICE_CODE
) {}
```

Provider configs are registered as beans or loaded from YAML:

```yaml
jaiclaw:
  oauth:
    providers:
      chutes:
        authorize-url: https://api.chutes.ai/idp/authorize
        token-url: https://api.chutes.ai/idp/token
        userinfo-url: https://api.chutes.ai/idp/userinfo
        callback-port: 1456
        callback-path: /oauth-callback
        scopes: [openid, profile, "chutes:invoke"]
        flow-type: AUTHORIZATION_CODE
      qwen-portal:
        token-url: https://chat.qwen.ai/api/v1/oauth2/token
        device-code-url: https://chat.qwen.ai/api/v1/oauth2/device/code
        client-id: f0304373b74a44d2b584a3fb70ca9e56
        scopes: [openid, profile, email, "model.completion"]
        flow-type: DEVICE_CODE
```

---

## Phase 5: Session Profile Rotation (jaiclaw-identity)

**Goal:** Round-robin credential rotation per session with cooldown awareness.

### SessionAuthProfileOverride

Extends the existing `Session` record (or uses a companion map in `SessionManager`):

```java
public class SessionAuthProfileResolver {
    // Pick the right profile for a new or existing session
    public Optional<String> resolve(
        String provider, Path agentDir,
        String currentOverride, String overrideSource,
        boolean isNewSession
    );

    // Clear manual override
    public void clearOverride(String sessionKey);
}
```

Logic mirrors OpenClaw's `session-override.ts`:
- New session → advance to next profile in order
- User-pinned profiles (`source = "user"`) are sticky
- Profiles in cooldown are skipped
- Wraps around to first profile after reaching end of order list

### Session Record Extension

Add optional fields to `Session` or use a separate `SessionAuthState` record:

```java
public record SessionAuthState(
    String authProfileOverride,
    String overrideSource,        // "user" | "auto"
    Integer compactionCount
) {}
```

---

## Phase 6: Shell Integration (jaiclaw-shell)

**Goal:** CLI commands for managing credentials and running OAuth flows.

### New Shell Commands

```
login <provider>          — Start OAuth flow for provider
login --list              — Show available providers
logout <profileId>        — Remove stored credentials
auth status               — Show all profiles with expiry state
auth rotate <provider>    — Manually advance to next profile
auth pin <profileId>      — Pin profile for current session
auth unpin                — Clear session profile override
```

### Login Flow (Shell)

```
1. User runs: login chutes
2. Shell resolves OAuthProviderConfig for "chutes"
3. Detect environment (local vs remote)
4. If local: open browser, start callback server, wait for code
5. If remote: print URL, prompt for redirect URL paste
6. Exchange code for tokens
7. Fetch userinfo
8. Store in auth-profiles.json via AuthProfileStoreManager
9. Print success: "Logged in as user@example.com (chutes)"
```

### SecurityStep Enhancement (Onboard Wizard)

Extend the existing onboard wizard `SecurityStep` to offer OAuth login alongside API key and JWT options when applicable.

---

## Phase 7: Auto-Configuration (jaiclaw-spring-boot-starter)

### New Auto-Config Class

```java
@AutoConfiguration
@AutoConfigureAfter(JaiClawAutoConfiguration.class)
@ConditionalOnClass(name = "io.jaiclaw.identity.auth.AuthProfileStoreManager")
public class JaiClawIdentityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public AuthProfileStoreManager authProfileStoreManager() { ... }

    @Bean
    @ConditionalOnMissingBean
    public ProviderTokenRefresherRegistry tokenRefresherRegistry(
        List<TokenRefresher> refreshers) { ... }

    @Bean
    @ConditionalOnMissingBean
    public AuthProfileResolver authProfileResolver(
        AuthProfileStoreManager storeManager,
        ProviderTokenRefresherRegistry refresherRegistry) { ... }

    @Bean
    @ConditionalOnMissingBean
    public SessionAuthProfileResolver sessionAuthProfileResolver(
        AuthProfileStoreManager storeManager) { ... }

    // Identity linking beans (currently not auto-configured)
    @Bean
    @ConditionalOnMissingBean
    public IdentityLinkStore identityLinkStore(TenantGuard tenantGuard) { ... }

    @Bean
    @ConditionalOnMissingBean
    public IdentityLinkService identityLinkService(IdentityLinkStore store) { ... }

    @Bean
    @ConditionalOnMissingBean
    public IdentityResolver identityResolver(IdentityLinkStore store) { ... }
}
```

### Configuration Properties

```java
@ConfigurationProperties(prefix = "jaiclaw.oauth")
public record OAuthProperties(
    boolean enabled,                              // default false
    String stateDir,                              // default ~/.jaiclaw
    Map<String, OAuthProviderConfig> providers,   // provider configs
    boolean readOnly                              // default false
) {}
```

---

## Phase 8: Dependencies & Build

### New Dependencies for jaiclaw-identity

```xml
<!-- Already present -->
<dependency><groupId>io.jaiclaw</groupId><artifactId>jaiclaw-core</artifactId></dependency>
<dependency><groupId>com.fasterxml.jackson.core</groupId><artifactId>jackson-databind</artifactId></dependency>

<!-- New — for HTTP token exchange and callback server -->
<!-- No new external deps needed: java.net.http.HttpClient (Java 11+) for token exchange -->
<!-- com.sun.net.httpserver.HttpServer (JDK built-in) for callback server -->
<!-- java.security.MessageDigest for PKCE SHA-256 -->
<!-- java.nio.channels.FileLock for file locking -->
```

**Zero new external dependencies.** All required functionality is available in the JDK:
- `java.net.http.HttpClient` — token exchange HTTP requests
- `com.sun.net.httpserver.HttpServer` — loopback callback server
- `java.security.MessageDigest` + `java.util.Base64` — PKCE S256
- `java.security.SecureRandom` — PKCE verifier generation
- `java.nio.channels.FileLock` — file locking
- `java.awt.Desktop` — open browser (with headless fallback)

---

## Implementation Order

| Step | Phase | Scope | Est. Files |
|------|-------|-------|-----------|
| 1 | Core Types | `jaiclaw-core`: sealed credential types, store record, enums | 8-10 |
| 2 | Serialization | `jaiclaw-identity`: Jackson serializer for sealed types | 2 |
| 3 | Store Manager | `jaiclaw-identity`: file I/O, locking, merge, upsert | 3-4 |
| 4 | Credential Eval | `jaiclaw-identity`: expiry check, eligibility evaluation | 1-2 |
| 5 | Token Refresh | `jaiclaw-identity`: SPI + generic refresher + resolver | 4-5 |
| 6 | PKCE + Device | `jaiclaw-identity`: OAuth flow implementations | 6-8 |
| 7 | Remote Detection | `jaiclaw-identity`: environment detection | 1 |
| 8 | Provider Configs | `jaiclaw-identity`: Chutes, Qwen, MiniMax, etc. | 5 |
| 9 | Session Rotation | `jaiclaw-identity`: round-robin override resolver | 2-3 |
| 10 | Auto-Config | `jaiclaw-spring-boot-starter`: wire identity beans | 1-2 |
| 11 | Shell Commands | `jaiclaw-shell`: login/logout/auth commands | 2-3 |
| 12 | Tests | Spock specs for each phase | 8-12 |

**Total: ~45-60 new files across 3 modules.**

---

## Testing Strategy

Each phase gets Spock specs:

| Phase | Test Focus |
|-------|-----------|
| Core Types | Sealed interface permits, record equality, JSON round-trip |
| Store Manager | Load/save/merge, file locking contention, legacy migration |
| Credential Eval | Expiry state transitions, eligibility edge cases |
| Token Refresh | Mock HTTP for refresh_token grant, lock contention, double-check |
| OAuth Flows | PKCE generation correctness, callback server lifecycle, state validation |
| Remote Detection | Env var combinations, headless Linux detection |
| Session Rotation | Round-robin ordering, cooldown skip, user-pinned sticky |
| Auto-Config | Bean presence/absence based on classpath and properties |

---

## Open Questions

1. **Cross-compatibility with OpenClaw?** Should `auth-profiles.json` be read/write compatible with OpenClaw's format? If so, the Jackson serializer needs alias support (`mode` ↔ `type`, `apiKey` ↔ `key`).

2. **SecretRef abstraction?** OpenClaw supports `keyRef` for indirect secret storage (env var, keychain). Do we need this in v1, or is inline-only sufficient?

3. **External CLI sync?** Should JaiClaw sync credentials from Claude CLI / Codex CLI like OpenClaw does? This adds complexity but improves UX for users who already have those CLIs.

4. **Browser opening strategy?** `java.awt.Desktop.browse()` is flaky on some Linux distros. Consider `xdg-open` / `open` (macOS) as fallback, or always default to URL paste.

5. **Multi-agent inheritance?** OpenClaw supports sub-agents inheriting credentials from a main agent. Is this needed for JaiClaw's architecture, or is single-agent-per-deployment sufficient?
