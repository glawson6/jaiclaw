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
6. Syncs credentials from external CLIs (Claude CLI, Codex CLI, Qwen CLI, MiniMax CLI)
7. Supports multi-agent credential inheritance (sub-agents adopt from main agent)
8. Resolves secrets via env vars, files, or exec commands (SecretRef)
9. Full read/write compatibility with OpenClaw's `auth-profiles.json` format
10. Integrates with the existing `jaiclaw-security` and `jaiclaw-shell` modules

### Resolved Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| OpenClaw format compat | **Full read/write** | Shared `auth-profiles.json` across both projects |
| SecretRef | **Included in v1** | Env var, file, and exec backends |
| External CLI sync | **Included** | Claude CLI, Codex CLI, Qwen CLI, MiniMax CLI |
| Multi-agent inheritance | **Included** | Sub-agents inherit/adopt from main agent |
| Browser opening | **Platform-specific** | `open` (macOS), `xdg-open` (Linux), `Desktop.browse()` fallback |

---

## Architecture

### Module Placement

The OAuth credential system spans three layers:

```
jaiclaw-core (pure Java — new types)
  └─ AuthProfileCredential (sealed interface)
  └─ AuthProfileStore (record)
  └─ SecretRef, CredentialState, ProfileUsageStats, etc.

jaiclaw-identity (extended — credential store + refresh + OAuth flows + CLI sync)
  └─ AuthProfileStoreManager (file I/O, locking, merge, multi-agent)
  └─ SecretRefResolver (env, file, exec backends)
  └─ TokenRefresher SPI + provider impls
  └─ OAuthFlowManager (PKCE + device code)
  └─ ExternalCliSync (Claude, Codex, Qwen, MiniMax)
  └─ SessionAuthProfileResolver (round-robin override)

jaiclaw-spring-boot-starter (auto-config wiring)
  └─ JaiClawIdentityAutoConfiguration (new, after Gateway)
```

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
  SecretRef.java                          (record: source + provider + id)
  SecretRefSource.java                    (enum: ENV, FILE, EXEC)

io.jaiclaw.identity.auth/                 ← new package in jaiclaw-identity
  AuthProfileStoreManager.java            (load/save/merge with file locking)
  AuthProfileStoreSerializer.java         (Jackson ser/deser with OpenClaw aliases)
  CredentialStateEvaluator.java           (pure functions: expiry check, eligibility)
  TokenRefresher.java                     (SPI interface)
  GenericOAuthTokenRefresher.java         (standard refresh_token grant)
  ProviderTokenRefresherRegistry.java     (dispatches to provider-specific refreshers)
  AuthProfileResolver.java               (resolves API key/token for a profile ID)
  ResolvedCredential.java                 (record: apiKey + provider + email)
  SessionAuthProfileResolver.java         (round-robin rotation per session)
  SessionAuthState.java                   (record: override + source + compactionCount)

io.jaiclaw.identity.secret/               ← new package in jaiclaw-identity
  SecretRefResolver.java                  (dispatches to source-specific backends)
  EnvSecretProvider.java                  (reads env vars, validates names)
  FileSecretProvider.java                 (reads JSON/single-value files, validates perms)
  ExecSecretProvider.java                 (spawns command, reads JSON from stdout)
  SecretProviderConfig.java               (config record for each provider)
  SecretRefResolveCache.java              (per-cycle cache to avoid re-reads)

io.jaiclaw.identity.sync/                 ← new package in jaiclaw-identity
  ExternalCliSyncManager.java             (orchestrates sync from all CLIs)
  ClaudeCliCredentialReader.java          (reads ~/.claude/.credentials.json or macOS Keychain)
  CodexCliCredentialReader.java           (reads ~/.codex/auth.json or macOS Keychain)
  QwenCliCredentialReader.java            (reads ~/.qwen/oauth_creds.json)
  MiniMaxCliCredentialReader.java         (reads ~/.minimax/oauth_creds.json)
  KeychainReader.java                     (macOS `security` command wrapper)
  CachedCredentialReader.java             (TTL-based in-process cache wrapper)

io.jaiclaw.identity.oauth/                ← new package in jaiclaw-identity
  OAuthFlowManager.java                  (orchestrates PKCE + device code flows)
  PkceGenerator.java                     (PKCE verifier + S256 challenge)
  AuthorizationCodeFlow.java             (loopback HTTP server, code exchange)
  DeviceCodeFlow.java                    (polling loop)
  OAuthCallbackServer.java               (lightweight HTTP server for redirect)
  RemoteEnvironmentDetector.java          (SSH/VPS/Codespaces detection)
  BrowserLauncher.java                    (platform-specific open URL)
  OAuthFlowResult.java                   (record: tokens + email + metadata)
  OAuthProviderConfig.java               (record: authorizeUrl, tokenUrl, scopes, etc.)
  OAuthFlowType.java                     (enum: AUTHORIZATION_CODE, DEVICE_CODE)

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

### 1a. Sealed Credential Hierarchy

```java
// Sealed credential type — matches OpenClaw's "type" discriminator
public sealed interface AuthProfileCredential
    permits ApiKeyCredential, TokenCredential, OAuthCredential {
    String provider();
    String email();  // nullable
}

public record ApiKeyCredential(
    String provider,
    String key,           // inline plaintext (nullable if keyRef set)
    SecretRef keyRef,     // indirect reference (nullable if key set)
    String email,
    Map<String, String> metadata
) implements AuthProfileCredential {}

public record TokenCredential(
    String provider,
    String token,         // inline plaintext (nullable if tokenRef set)
    SecretRef tokenRef,   // indirect reference (nullable if token set)
    Long expires,         // ms-since-epoch (nullable = no expiry)
    String email
) implements AuthProfileCredential {}

public record OAuthCredential(
    String provider,
    String access,        // access token (required)
    String refresh,       // refresh token (required)
    long expires,         // ms-since-epoch (required)
    String email,
    String clientId,      // stored for refresh (nullable)
    String accountId,     // provider-specific (nullable)
    String projectId,     // Google Cloud project (nullable)
    String enterpriseUrl  // custom base URL (nullable)
) implements AuthProfileCredential {}
```

### 1b. SecretRef

```java
public enum SecretRefSource { ENV, FILE, EXEC }

public record SecretRef(
    SecretRefSource source,
    String provider,      // named provider config or "default"
    String id             // env var name, JSON pointer, or command ref ID
) {}
```

### 1c. Store Structure

```java
public record AuthProfileStore(
    int version,
    Map<String, AuthProfileCredential> profiles,     // profileId → credential
    Map<String, List<String>> order,                 // provider → ordered profileIds
    Map<String, String> lastGood,                    // provider → last-known-good profileId
    Map<String, ProfileUsageStats> usageStats        // profileId → stats
) {
    public static final int CURRENT_VERSION = 1;

    public static AuthProfileStore empty() {
        return new AuthProfileStore(CURRENT_VERSION, Map.of(), Map.of(), Map.of(), Map.of());
    }
}

public record ProfileUsageStats(
    Long lastUsed,
    Long cooldownUntil,
    Long disabledUntil,
    AuthProfileFailureReason disabledReason,
    int errorCount,
    Map<AuthProfileFailureReason, Integer> failureCounts,
    Long lastFailureAt
) {}
```

### 1d. Enums and Result Types

```java
public enum AuthProfileFailureReason {
    AUTH, AUTH_PERMANENT, FORMAT, OVERLOADED, RATE_LIMIT,
    BILLING, TIMEOUT, MODEL_NOT_FOUND, SESSION_EXPIRED, UNKNOWN
}

public enum CredentialState { VALID, EXPIRED, MISSING, INVALID }

public record CredentialEligibility(boolean eligible, String reasonCode) {
    public static final String OK = "ok";
    public static final String MISSING_CREDENTIAL = "missing_credential";
    public static final String INVALID_EXPIRES = "invalid_expires";
    public static final String EXPIRED = "expired";
    public static final String UNRESOLVED_REF = "unresolved_ref";
}
```

### 1e. Profile ID Convention

`"{provider}:{name}"` — e.g. `"anthropic:default"`, `"openai-codex:user@example.com"`, `"anthropic:claude-cli"`.

---

## Phase 2: Serialization (jaiclaw-identity)

**Goal:** Jackson ser/deser for the sealed `AuthProfileCredential` with full OpenClaw format compatibility.

### Jackson Annotations on Sealed Interface

```java
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = ApiKeyCredential.class, name = "api_key"),
    @JsonSubTypes.Type(value = TokenCredential.class, name = "token"),
    @JsonSubTypes.Type(value = OAuthCredential.class, name = "oauth")
})
public sealed interface AuthProfileCredential ...
```

### OpenClaw Alias Handling

The `AuthProfileStoreSerializer` handles these OpenClaw compatibility rules:

| OpenClaw Field | JaiClaw Field | Rule |
|----------------|---------------|------|
| `mode` | `type` | Accept `mode` as alias for `type` on read |
| `apiKey` | `key` | Accept `apiKey` as alias for `key` in `ApiKeyCredential` |
| `${ENV_VAR}` inline string | `SecretRef` | Auto-coerce `${VAR}` to `SecretRef(ENV, "default", "VAR")` |

On save:
- If `keyRef` is present, strip inline `key` from JSON output (secrets stored by ref)
- If `tokenRef` is present, strip inline `token` from JSON output

Implementation uses a custom `JsonDeserializer<AuthProfileCredential>` that:
1. Reads the JSON tree
2. Checks for `type` or `mode` field
3. Handles `apiKey` → `key` alias
4. Coerces `${VAR}` strings to `SecretRef`
5. Delegates to the appropriate record constructor

---

## Phase 3: Store Manager (jaiclaw-identity)

**Goal:** File-based credential persistence with locking, merge, and multi-agent inheritance.

### File Locations

| File | Path | Override Env |
|------|------|-------------|
| Main agent store | `~/.jaiclaw/agents/{agentId}/auth-profiles.json` | `JAICLAW_STATE_DIR` |
| Sub-agent store | `~/.jaiclaw/agents/{agentId}/auth-profiles.json` | `JAICLAW_AGENT_DIR` |
| Lock file | `{store-path}.lock` | — |
| Legacy store | `~/.jaiclaw/agents/{agentId}/auth.json` | — |

### AuthProfileStoreManager

```java
public class AuthProfileStoreManager {

    // --- Loading ---

    // Load store for main agent (syncs external CLI creds)
    public AuthProfileStore loadMainStore();

    // Load store for a specific agent dir
    public AuthProfileStore loadForAgent(Path agentDir);

    // Load merged view: main + sub-agent (sub overrides main)
    public AuthProfileStore loadForRuntime(Path agentDir);

    // --- Saving ---

    // Save store (strips inline secrets where ref exists, under lock)
    public void save(Path agentDir, AuthProfileStore store);

    // --- Mutations (load + modify + save under lock) ---

    public void upsertProfile(Path agentDir, String profileId, AuthProfileCredential credential);
    public void removeProfile(Path agentDir, String profileId);
    public void setProfileOrder(Path agentDir, String provider, List<String> order);
    public void markProfileGood(Path agentDir, String provider, String profileId);

    // --- Merge ---

    // Sub-agent overrides main (shallow merge per map)
    public AuthProfileStore merge(AuthProfileStore base, AuthProfileStore override);

    // --- Multi-agent ---

    // Sync credential to all sibling agent dirs (best-effort)
    public void syncToSiblings(Path primaryAgentDir, String profileId, AuthProfileCredential credential);

    // Adopt newer credential from main agent (pre-refresh check)
    public Optional<OAuthCredential> adoptFromMain(Path subAgentDir, String profileId);
}
```

### File Locking

```java
public class AuthProfileFileLock {
    private static final int MAX_RETRIES = 10;
    private static final long BASE_DELAY_MS = 100;
    private static final long MAX_DELAY_MS = 10_000;
    private static final long STALE_TIMEOUT_MS = 30_000;

    // Acquire exclusive lock with exponential backoff
    public static <T> T withLock(Path lockFile, Supplier<T> action);
    public static void withLock(Path lockFile, Runnable action);
}
```

Uses `java.nio.channels.FileChannel.tryLock()` with retry. Lock file is `{storePath}.lock` (separate from data file). Stale lock detection: if lock file `lastModified` > `STALE_TIMEOUT_MS` ago, delete and retry.

### Merge Semantics

```java
public AuthProfileStore merge(AuthProfileStore base, AuthProfileStore override) {
    // Empty override is a no-op
    if (override.profiles().isEmpty() && override.order().isEmpty()
            && override.lastGood().isEmpty() && override.usageStats().isEmpty()) {
        return base;
    }
    return new AuthProfileStore(
        Math.max(base.version(), override.version()),
        mergeMaps(base.profiles(), override.profiles()),     // override wins
        mergeMaps(base.order(), override.order()),
        mergeMaps(base.lastGood(), override.lastGood()),
        mergeMaps(base.usageStats(), override.usageStats())
    );
}
```

### Multi-Agent Inheritance Flow

```
Sub-agent first boot (no auth-profiles.json)
  → loadForAgent() copies main agent's store to sub-agent dir

Every OAuth resolution for sub-agent
  → adoptFromMain() checks if main has fresher token; copies proactively

OAuth refresh fails for sub-agent
  → Fallback: check main agent for non-expired token; copy and use

New OAuth login completes
  → syncToSiblings() writes credential to all sibling agent dirs

Runtime (gateway startup)
  → loadForRuntime() merges main + sub-agent (sub overrides main)
```

### Legacy Migration

On first `load()`:
1. If `auth-profiles.json` doesn't exist, check for `auth.json` (legacy flat format)
2. Migrate each entry to profile ID `"{provider}:default"`
3. Save as `auth-profiles.json`, delete `auth.json`

---

## Phase 4: SecretRef Resolution (jaiclaw-identity)

**Goal:** Resolve indirect secret references from env vars, files, or exec commands.

### SecretRefResolver

```java
public class SecretRefResolver {
    // Resolve a SecretRef to its plaintext value
    public String resolve(SecretRef ref);

    // Resolve inline key: returns value directly, or resolves ${VAR} shorthand
    public String resolveInlineOrRef(String inlineValue, SecretRef ref);
}
```

### Three Source Backends

#### ENV — Environment Variable

```java
public class EnvSecretProvider {
    // Validates var name: ^[A-Z][A-Z0-9_]{0,127}$
    // Reads System.getenv(id)
    // Supports optional allowlist from provider config
    public String resolve(String id, SecretProviderConfig config);
}
```

#### FILE — File-backed Secret

```java
public class FileSecretProvider {
    // Modes: "json" (default) or "singleValue"
    // JSON mode: id is a JSON Pointer (e.g. "/providers/openai/apiKey")
    // singleValue mode: reads entire file content as the secret
    // Validates: absolute path, not world-readable, not symlink (unless allowed)
    // Default timeout: 5000ms, max: 1MB
    public String resolve(String id, SecretProviderConfig config);
}
```

#### EXEC — External Command

```java
public class ExecSecretProvider {
    // Spawns command with stdin JSON: { protocolVersion: 1, provider: "...", ids: [...] }
    // Expects stdout JSON: { protocolVersion: 1, values: { id: value }, errors: { id: { message } } }
    // Command must be absolute path, owned by current user
    // Default timeout: 5000ms, max output: 1MB
    public String resolve(String id, SecretProviderConfig config);
}
```

### SecretProviderConfig

```java
public record SecretProviderConfig(
    SecretRefSource source,
    String name,                    // provider alias
    // ENV-specific
    List<String> allowlist,         // permitted env var names (null = any)
    // FILE-specific
    String path,                    // absolute file path
    String mode,                    // "json" or "singleValue"
    boolean allowSymlinkPath,       // default false
    long timeoutMs,                 // default 5000
    long maxBytes,                  // default 1MB
    // EXEC-specific
    String command,                 // absolute command path
    List<String> args,              // command arguments
    boolean jsonOnly,               // default true
    Map<String, String> env,        // extra env vars for subprocess
    List<String> trustedDirs        // allowed command directories
) {}
```

### Inline `${VAR}` Coercion

When resolving an `ApiKeyCredential` or `TokenCredential`:
- If inline value matches `^\$\{([A-Z][A-Z0-9_]*)\}$`, coerce to `SecretRef(ENV, "default", "$1")`
- This allows `"key": "${OPENAI_API_KEY}"` in the JSON to resolve via env var

---

## Phase 5: Credential Resolution & Refresh (jaiclaw-identity)

**Goal:** Transparent credential resolution — callers ask for "the credential for profile X" and get back a valid token.

### AuthProfileResolver

```java
public class AuthProfileResolver {
    private final AuthProfileStoreManager storeManager;
    private final SecretRefResolver secretRefResolver;
    private final ProviderTokenRefresherRegistry refresherRegistry;

    public ResolvedCredential resolve(String profileId, Path agentDir);
}

public record ResolvedCredential(String apiKey, String provider, String email) {}
```

### Resolution Logic

```
1. Load store via storeManager.loadForRuntime(agentDir)
2. Look up profiles[profileId] → credential
3. Switch on credential type:

   ApiKeyCredential:
     → If keyRef present: resolve via SecretRefResolver
     → Else if key present: check for ${VAR} coercion → return
     → Else: throw MISSING_CREDENTIAL

   TokenCredential:
     → Evaluate expiry state
     → If EXPIRED or INVALID: throw
     → If tokenRef present: resolve via SecretRefResolver
     → Else: return token

   OAuthCredential:
     → If sub-agent: call storeManager.adoptFromMain() first
     → If now < expires: return access token directly
     → Else: call refreshWithLock()
     → On refresh failure + sub-agent: fallback to main agent's credential
```

### TokenRefresher SPI

```java
public interface TokenRefresher {
    // Which provider this refresher handles (e.g. "chutes", "qwen-portal")
    String providerId();

    // Refresh the credential, returning updated tokens
    OAuthCredential refresh(OAuthCredential current) throws TokenRefreshException;
}
```

### GenericOAuthTokenRefresher

Standard `grant_type=refresh_token` implementation using `java.net.http.HttpClient`:

```java
public class GenericOAuthTokenRefresher implements TokenRefresher {
    private final OAuthProviderConfig config;
    private final HttpClient httpClient;

    @Override
    public OAuthCredential refresh(OAuthCredential current) throws TokenRefreshException {
        // POST to config.tokenUrl() with:
        //   grant_type=refresh_token
        //   refresh_token=current.refresh()
        //   client_id=current.clientId() or config.clientId()
        //   client_secret=config.clientSecret() (if present)
        // Parse response: { access_token, refresh_token?, expires_in }
        // Per RFC 6749 §6: if new refresh_token returned, use it; else keep old
        // Compute expires: now + max(0, expires_in) * 1000 - 5_minutes (floor at now + 30s)
    }
}
```

### Refresh Under Lock

```java
public OAuthCredential refreshWithLock(String profileId, Path agentDir) {
    return AuthProfileFileLock.withLock(lockPath(agentDir), () -> {
        // 1. Re-read store (another process may have refreshed)
        AuthProfileStore fresh = storeManager.loadForAgent(agentDir);
        OAuthCredential cred = (OAuthCredential) fresh.profiles().get(profileId);

        // 2. Double-check: if still valid, return (no-op)
        if (System.currentTimeMillis() < cred.expires()) return cred;

        // 3. Find refresher for provider
        TokenRefresher refresher = refresherRegistry.get(cred.provider());

        // 4. Refresh
        OAuthCredential updated = refresher.refresh(cred);

        // 5. Update store and save
        Map<String, AuthProfileCredential> profiles = new HashMap<>(fresh.profiles());
        profiles.put(profileId, updated);
        storeManager.save(agentDir, fresh.withProfiles(profiles));

        return updated;
    });
}
```

### CredentialStateEvaluator

```java
public final class CredentialStateEvaluator {

    public static CredentialState resolveTokenExpiryState(Long expires) {
        if (expires == null) return CredentialState.MISSING;
        if (!Double.isFinite(expires) || expires <= 0) return CredentialState.INVALID;
        if (System.currentTimeMillis() >= expires) return CredentialState.EXPIRED;
        return CredentialState.VALID;
    }

    public static CredentialEligibility evaluateEligibility(AuthProfileCredential credential) {
        return switch (credential) {
            case ApiKeyCredential c -> {
                boolean hasKey = c.key() != null && !c.key().isBlank();
                boolean hasRef = c.keyRef() != null;
                yield hasKey || hasRef
                    ? new CredentialEligibility(true, CredentialEligibility.OK)
                    : new CredentialEligibility(false, CredentialEligibility.MISSING_CREDENTIAL);
            }
            case TokenCredential c -> {
                boolean hasToken = (c.token() != null && !c.token().isBlank()) || c.tokenRef() != null;
                if (!hasToken) yield new CredentialEligibility(false, CredentialEligibility.MISSING_CREDENTIAL);
                CredentialState state = resolveTokenExpiryState(c.expires());
                yield switch (state) {
                    case EXPIRED -> new CredentialEligibility(false, CredentialEligibility.EXPIRED);
                    case INVALID -> new CredentialEligibility(false, CredentialEligibility.INVALID_EXPIRES);
                    default -> new CredentialEligibility(true, CredentialEligibility.OK);
                };
            }
            case OAuthCredential c -> {
                boolean hasAccess = c.access() != null && !c.access().isBlank();
                boolean hasRefresh = c.refresh() != null && !c.refresh().isBlank();
                yield hasAccess || hasRefresh
                    ? new CredentialEligibility(true, CredentialEligibility.OK)
                    : new CredentialEligibility(false, CredentialEligibility.MISSING_CREDENTIAL);
            }
        };
    }
}
```

---

## Phase 6: External CLI Sync (jaiclaw-identity)

**Goal:** Sync credentials from Claude CLI, Codex CLI, Qwen CLI, and MiniMax CLI.

### Supported CLIs

| CLI | Profile ID | Source | Format |
|-----|-----------|--------|--------|
| Claude CLI | `anthropic:claude-cli` | macOS Keychain or `~/.claude/.credentials.json` | `{ claudeAiOauth: { accessToken, refreshToken, expiresAt } }` |
| Codex CLI | `openai-codex:codex-cli` | macOS Keychain or `~/.codex/auth.json` | `{ tokens: { access_token, refresh_token, account_id }, last_refresh }` |
| Qwen CLI | `qwen-portal:qwen-cli` | `~/.qwen/oauth_creds.json` | `{ access_token, refresh_token, expiry_date }` |
| MiniMax CLI | `minimax-portal:minimax-cli` | `~/.minimax/oauth_creds.json` | `{ access_token, refresh_token, expiry_date }` |

### KeychainReader (macOS only)

```java
public class KeychainReader {
    // Reads a generic password from macOS Keychain
    // Command: security find-generic-password -s "{service}" -a "{account}" -w
    // Returns: Optional<String> (the password, typically JSON)
    public Optional<String> readGenericPassword(String service, String account);
}
```

| CLI | Keychain Service | Keychain Account |
|-----|-----------------|------------------|
| Claude CLI | `Claude Code-credentials` | `Claude Code` |
| Codex CLI | `Codex Auth` | `cli\|` + SHA-256(`$CODEX_HOME`).hex[0..15] |

### CachedCredentialReader

Wraps each CLI reader with a 15-minute in-process TTL cache:

```java
public class CachedCredentialReader<T> {
    private static final long TTL_MS = 15 * 60 * 1000; // 15 minutes

    private final Supplier<Optional<T>> reader;
    private volatile T cached;
    private volatile long readAt;

    public Optional<T> read();
    public void invalidate();
}
```

### ExternalCliSyncManager

```java
public class ExternalCliSyncManager {
    private static final long NEAR_EXPIRY_MS = 10 * 60 * 1000; // 10 minutes

    // Sync all external CLIs into the store. Returns true if any profile mutated.
    public boolean syncAll(AuthProfileStore store);
}
```

**Sync logic per provider:**
1. Skip if existing profile is fresh: `expires > now + NEAR_EXPIRY_MS`
2. Read from cached reader
3. Update store if: no existing cred, OR existing is expired, OR new has later expiry
4. Return true if mutated

### Credential Type Produced

All CLI readers produce `OAuthCredential` or `TokenCredential`:
- Claude CLI with refresh token → `OAuthCredential`
- Claude CLI without refresh token → `TokenCredential`
- Codex CLI → `OAuthCredential` (expiry computed as `lastRefresh + 1 hour`)
- Qwen CLI → `OAuthCredential`
- MiniMax CLI → `OAuthCredential`

---

## Phase 7: OAuth Flows (jaiclaw-identity)

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
8. Sync to sibling agents
```

### PkceGenerator

```java
public final class PkceGenerator {
    public record PkceChallenge(String verifier, String challenge) {}

    public static PkceChallenge generate() {
        byte[] random = new byte[32];
        new SecureRandom().nextBytes(random);
        String verifier = HexFormat.of().formatHex(random);
        byte[] sha256 = MessageDigest.getInstance("SHA-256")
            .digest(verifier.getBytes(StandardCharsets.UTF_8));
        String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(sha256);
        return new PkceChallenge(verifier, challenge);
    }
}
```

### OAuthCallbackServer

Lightweight loopback HTTP server using `com.sun.net.httpserver.HttpServer`:

```java
public class OAuthCallbackServer implements AutoCloseable {
    private final CompletableFuture<OAuthCallbackResult> resultFuture;

    // Start server, bind to 127.0.0.1:{port}, listen on {path}
    public OAuthCallbackServer(int port, String path, String expectedState, Duration timeout);

    // Block until callback received or timeout
    public OAuthCallbackResult awaitCallback() throws TimeoutException;

    @Override
    public void close(); // stops the server
}

public record OAuthCallbackResult(String code, String state) {}
```

- Validates `state` matches expected (CSRF protection)
- Returns HTML success page to browser
- Signals `CompletableFuture` completion
- Auto-shuts down after callback or timeout

### DeviceCodeFlow

```java
public class DeviceCodeFlow {
    public record DeviceCodeResponse(
        String deviceCode, String userCode,
        String verificationUri, int interval, int expiresIn
    ) {}

    // Step 1: Request device code
    public DeviceCodeResponse requestDeviceCode(OAuthProviderConfig config);

    // Step 2: Poll for token (blocks until authorized or timeout)
    public OAuthFlowResult pollForToken(OAuthProviderConfig config, DeviceCodeResponse deviceCode);
}
```

Polling handles:
- `authorization_pending` → continue polling
- `slow_down` → increase interval by 5 seconds
- `access_denied` → throw
- `expired_token` → throw
- Success → return tokens

### RemoteEnvironmentDetector

```java
public final class RemoteEnvironmentDetector {
    public static boolean isRemote() {
        if (System.getenv("SSH_CLIENT") != null) return true;
        if (System.getenv("SSH_TTY") != null) return true;
        if (System.getenv("SSH_CONNECTION") != null) return true;
        if (System.getenv("REMOTE_CONTAINERS") != null) return true;
        if (System.getenv("CODESPACES") != null) return true;
        if (isHeadlessLinux()) return true;
        return false;
    }

    private static boolean isHeadlessLinux() {
        if (!"Linux".equalsIgnoreCase(System.getProperty("os.name"))) return false;
        if (isWsl()) return false;
        return System.getenv("DISPLAY") == null && System.getenv("WAYLAND_DISPLAY") == null;
    }

    private static boolean isWsl() {
        // Check /proc/version for "microsoft" or "WSL"
    }
}
```

### BrowserLauncher

```java
public final class BrowserLauncher {
    public static boolean open(String url) {
        String os = System.getProperty("os.name", "").toLowerCase();
        try {
            if (os.contains("mac")) {
                new ProcessBuilder("open", url).start();
            } else if (os.contains("linux")) {
                new ProcessBuilder("xdg-open", url).start();
            } else {
                Desktop.getDesktop().browse(URI.create(url));
            }
            return true;
        } catch (Exception e) {
            return false;  // caller should fall back to URL paste
        }
    }
}
```

### OAuthProviderConfig

```java
public record OAuthProviderConfig(
    String providerId,
    String authorizeUrl,          // for auth code flow
    String tokenUrl,              // token endpoint
    String userinfoUrl,           // optional userinfo endpoint
    String deviceCodeUrl,         // for device code flow
    String clientId,
    String clientSecret,          // nullable
    String redirectUri,           // default: http://127.0.0.1:{port}{path}
    int callbackPort,             // loopback port for redirect
    String callbackPath,          // path on loopback server
    List<String> scopes,
    OAuthFlowType flowType        // AUTHORIZATION_CODE or DEVICE_CODE
) {}

public enum OAuthFlowType { AUTHORIZATION_CODE, DEVICE_CODE }
```

### Built-in Provider Configs

| Provider | Flow | Port | Scopes |
|----------|------|------|--------|
| Chutes | Auth Code + PKCE | 1456 | `openid profile chutes:invoke` |
| OpenAI Codex | Auth Code + PKCE | 1455 | (via pi-ai library equiv) |
| Google Gemini | Auth Code + PKCE | 8085 | `cloud-platform userinfo.email userinfo.profile` |
| Qwen Portal | Device Code | — | `openid profile email model.completion` |
| MiniMax Portal | Device Code | — | `group_id profile model.completion` |

### OAuthFlowResult

```java
public record OAuthFlowResult(
    String accessToken,
    String refreshToken,
    long expiresAt,           // ms-since-epoch
    String email,             // from userinfo (nullable)
    String accountId,         // provider-specific (nullable)
    String projectId,         // Google Cloud project (nullable)
    String clientId           // stored for refresh
) {}
```

### Expiry Computation

Port of OpenClaw's `coerceExpiresAt`:

```java
public static long computeExpiresAt(int expiresInSeconds) {
    long now = System.currentTimeMillis();
    long fiveMinutes = 5 * 60 * 1000;
    long thirtySeconds = 30 * 1000;
    long value = now + Math.max(0, expiresInSeconds) * 1000L - fiveMinutes;
    return Math.max(value, now + thirtySeconds);
}
```

---

## Phase 8: Session Profile Rotation (jaiclaw-identity)

**Goal:** Round-robin credential rotation per session with cooldown awareness.

### SessionAuthState

```java
public record SessionAuthState(
    String authProfileOverride,       // active profileId override
    String overrideSource,            // "user" or "auto"
    Integer compactionCount           // compaction cycle when set
) {
    public static final String SOURCE_USER = "user";
    public static final String SOURCE_AUTO = "auto";
}
```

### SessionAuthProfileResolver

```java
public class SessionAuthProfileResolver {
    private final AuthProfileStoreManager storeManager;

    // Resolve the auth profile for a session
    public Optional<String> resolve(
        String provider,
        Path agentDir,
        SessionAuthState currentState,
        boolean isNewSession,
        Integer currentCompactionCount
    );

    // Clear manual override
    public SessionAuthState clearOverride();
}
```

### Round-Robin Logic

```
1. Validate current override:
   - Clear if profile no longer exists in store
   - Clear if profile is for wrong provider
   - Clear if profile not in configured order list

2. If no order configured → return empty (no rotation)

3. pickFirstAvailable(): first profile in order not in cooldown (fallback: order[0])

4. pickNextAvailable(active): next profile after active not in cooldown (round-robin wraparound)

5. Rotation triggers:
   - isNewSession → advance to next
   - compactionCount advanced since last stored → rotate
   - current profile in cooldown → pick first available

6. User-set overrides (source = "user") are sticky — not auto-rotated
```

---

## Phase 9: Shell Integration (jaiclaw-shell)

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

### LoginCommand

```java
@ShellComponent
public class LoginCommand {
    @ShellMethod(key = {"login"}, value = "Authenticate with an OAuth provider")
    public void login(
        @ShellOption(defaultValue = "") String provider,
        @ShellOption(value = "--list", defaultValue = "false") boolean listProviders
    );
}
```

### AuthCommand

```java
@ShellComponent
public class AuthCommand {
    @ShellMethod(key = {"auth status", "auth-status"}, value = "Show auth profile status")
    public void status();

    @ShellMethod(key = {"auth rotate", "auth-rotate"}, value = "Rotate to next profile")
    public void rotate(@ShellOption String provider);

    @ShellMethod(key = {"auth pin", "auth-pin"}, value = "Pin a profile for this session")
    public void pin(@ShellOption String profileId);

    @ShellMethod(key = {"auth unpin", "auth-unpin"}, value = "Clear session profile override")
    public void unpin();

    @ShellMethod(key = {"logout"}, value = "Remove stored credentials")
    public void logout(@ShellOption String profileId);
}
```

### SecurityStep Enhancement

Extend the existing onboard wizard `SecurityStep` to offer OAuth login as an option when applicable:
- Quick mode: skip (unchanged)
- Manual mode: add "OAuth (login with browser)" to security mode selection

---

## Phase 10: Auto-Configuration (jaiclaw-spring-boot-starter)

### New Auto-Config Class

```java
@AutoConfiguration
@AutoConfigureAfter(JaiClawAutoConfiguration.class)
@ConditionalOnClass(name = "io.jaiclaw.identity.auth.AuthProfileStoreManager")
public class JaiClawIdentityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public AuthProfileStoreManager authProfileStoreManager(OAuthProperties oauthProperties) { ... }

    @Bean
    @ConditionalOnMissingBean
    public SecretRefResolver secretRefResolver() { ... }

    @Bean
    @ConditionalOnMissingBean
    public ExternalCliSyncManager externalCliSyncManager() { ... }

    @Bean
    @ConditionalOnMissingBean
    public ProviderTokenRefresherRegistry tokenRefresherRegistry(
        ObjectProvider<List<TokenRefresher>> refreshers) { ... }

    @Bean
    @ConditionalOnMissingBean
    public AuthProfileResolver authProfileResolver(
        AuthProfileStoreManager storeManager,
        SecretRefResolver secretRefResolver,
        ProviderTokenRefresherRegistry refresherRegistry) { ... }

    @Bean
    @ConditionalOnMissingBean
    public SessionAuthProfileResolver sessionAuthProfileResolver(
        AuthProfileStoreManager storeManager) { ... }

    // Existing identity linking beans (currently not auto-configured)
    @Bean
    @ConditionalOnMissingBean
    public IdentityLinkStore identityLinkStore(ObjectProvider<TenantGuard> tenantGuard) { ... }

    @Bean
    @ConditionalOnMissingBean
    public IdentityLinkService identityLinkService(IdentityLinkStore store) { ... }

    @Bean
    @ConditionalOnMissingBean
    public IdentityResolver identityResolver(IdentityLinkStore store) { ... }
}
```

### OAuthProperties

```java
@ConfigurationProperties(prefix = "jaiclaw.oauth")
public record OAuthProperties(
    boolean enabled,                                  // default false
    String stateDir,                                  // default ~/.jaiclaw
    String agentId,                                   // default "default"
    Map<String, OAuthProviderConfig> providers,       // provider configs
    boolean readOnly,                                 // default false
    boolean cliSyncEnabled,                           // default true
    Map<String, SecretProviderConfig> secretProviders // named secret providers
) {}
```

### Registration

Add `io.jaiclaw.starter.JaiClawIdentityAutoConfiguration` to:
```
META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

---

## Dependencies & Build

### New Dependencies for jaiclaw-identity pom.xml

```xml
<!-- Already present -->
<dependency><groupId>io.jaiclaw</groupId><artifactId>jaiclaw-core</artifactId></dependency>
<dependency><groupId>com.fasterxml.jackson.core</groupId><artifactId>jackson-databind</artifactId></dependency>
<dependency><groupId>org.slf4j</groupId><artifactId>slf4j-api</artifactId></dependency>
```

**Zero new external dependencies.** All required functionality is available in the JDK:
- `java.net.http.HttpClient` — token exchange and userinfo HTTP requests
- `com.sun.net.httpserver.HttpServer` — loopback callback server
- `java.security.MessageDigest` + `java.util.Base64` — PKCE S256
- `java.security.SecureRandom` — PKCE verifier generation
- `java.nio.channels.FileLock` + `java.nio.channels.FileChannel` — file locking
- `java.lang.ProcessBuilder` — browser launch, keychain read, exec secret provider
- `java.util.HexFormat` — hex encoding (Java 17+)

---

## Implementation Order

| Step | Phase | Scope | Est. Files |
|------|-------|-------|-----------|
| 1 | Core Types | `jaiclaw-core`: sealed credential types, store record, SecretRef, enums | 11 |
| 2 | Serialization | `jaiclaw-identity`: Jackson ser/deser with OpenClaw aliases | 2 |
| 3 | Store Manager | `jaiclaw-identity`: file I/O, locking, merge, multi-agent | 4 |
| 4 | SecretRef | `jaiclaw-identity`: env/file/exec backends, resolver, cache | 6 |
| 5 | Credential Eval | `jaiclaw-identity`: expiry check, eligibility evaluation | 1 |
| 6 | Token Refresh | `jaiclaw-identity`: SPI + generic refresher + resolver | 5 |
| 7 | CLI Sync | `jaiclaw-identity`: Claude/Codex/Qwen/MiniMax readers + sync manager | 7 |
| 8 | OAuth Flows | `jaiclaw-identity`: PKCE + device code + callback server + browser | 8 |
| 9 | Provider Configs | `jaiclaw-identity`: Chutes, OpenAI, Google, Qwen, MiniMax | 5 |
| 10 | Session Rotation | `jaiclaw-identity`: round-robin override resolver + state record | 2 |
| 11 | Auto-Config | `jaiclaw-spring-boot-starter`: wire identity beans + properties | 2 |
| 12 | Shell Commands | `jaiclaw-shell`: login/logout/auth commands | 2 |
| 13 | Tests | Spock specs for phases 1-12 | 12-15 |

**Total: ~65-70 new files across 3 modules.**

---

## Testing Strategy

Each phase gets Spock specs:

| Phase | Test Focus |
|-------|-----------|
| Core Types | Sealed interface permits, record equality, JSON round-trip, SecretRef construction |
| Serialization | OpenClaw alias handling (mode↔type, apiKey↔key), `${VAR}` coercion, round-trip |
| Store Manager | Load/save/merge, file locking contention, legacy migration, multi-agent inheritance |
| SecretRef | Env resolution, file read (JSON pointer + single value), exec command, permission checks |
| Credential Eval | Expiry state transitions, eligibility for each credential type, edge cases |
| Token Refresh | Mock HTTP for refresh_token grant, lock contention, double-check-under-lock |
| CLI Sync | Mock file reads per CLI, keychain mock, cache TTL, near-expiry skip logic |
| OAuth Flows | PKCE correctness, callback server lifecycle, state validation, device code polling |
| Remote Detection | Env var combinations, headless Linux detection, WSL exclusion |
| Session Rotation | Round-robin ordering, cooldown skip, user-pinned sticky, compaction rotation |
| Auto-Config | Bean presence/absence based on classpath and properties |
| Shell Commands | Login flow mock, auth status output, pin/unpin state |

### Integration Tests (18 tests)

In addition to unit tests, the module has 18 integration tests that exercise full OAuth flows end-to-end against a local mock HTTP server (`MockOAuthServer`). These run under the `integration-test` Maven profile via `maven-failsafe-plugin`.

| Spec | Tests | Flow |
|------|-------|------|
| `AuthorizationCodeFlowIT` | 6 | Auth code + PKCE + userinfo + credential storage |
| `DeviceCodeFlowIT` | 7 | Device code request + polling (pending/slow_down/success/denied) |
| `OAuthCallbackServerIT` | 5 | Loopback callback + CSRF + error + timeout + e2e token exchange |

**Key patterns:**
- `MockOAuthServer` wraps `com.sun.net.httpserver.HttpServer` on random port with configurable JSON responses and request history tracking
- Real `java.net.http.HttpClient` makes actual HTTP calls (not mocked interfaces)
- `pendingThenSuccess(path, pendingCount, successJson)` simulates device code polling
- `OAuthFlowManager` has a testable constructor accepting injected `AuthorizationCodeFlow` / `DeviceCodeFlow`

**Running:**
```bash
./mvnw verify -pl :jaiclaw-identity -Pintegration-test -o  # 90 unit + 18 IT
```

See [OAuth Integration Tests Architecture](OAUTH-INTEGRATION-TESTS.md) for full details.

---

## Constants

```java
public final class AuthProfileConstants {
    public static final int AUTH_STORE_VERSION = 1;
    public static final String AUTH_PROFILE_FILENAME = "auth-profiles.json";
    public static final String LEGACY_AUTH_FILENAME = "auth.json";

    // External CLI profile IDs
    public static final String CLAUDE_CLI_PROFILE_ID = "anthropic:claude-cli";
    public static final String CODEX_CLI_PROFILE_ID = "openai-codex:codex-cli";
    public static final String QWEN_CLI_PROFILE_ID = "qwen-portal:qwen-cli";
    public static final String MINIMAX_CLI_PROFILE_ID = "minimax-portal:minimax-cli";

    // Sync timing
    public static final long EXTERNAL_CLI_SYNC_TTL_MS = 15 * 60 * 1000;     // 15 min
    public static final long EXTERNAL_CLI_NEAR_EXPIRY_MS = 10 * 60 * 1000;  // 10 min

    // External CLI file paths (relative to user home)
    public static final String CLAUDE_CLI_CREDS_PATH = ".claude/.credentials.json";
    public static final String CODEX_CLI_AUTH_FILENAME = "auth.json";
    public static final String QWEN_CLI_CREDS_PATH = ".qwen/oauth_creds.json";
    public static final String MINIMAX_CLI_CREDS_PATH = ".minimax/oauth_creds.json";

    // macOS Keychain entries
    public static final String CLAUDE_KEYCHAIN_SERVICE = "Claude Code-credentials";
    public static final String CLAUDE_KEYCHAIN_ACCOUNT = "Claude Code";
    public static final String CODEX_KEYCHAIN_SERVICE = "Codex Auth";

    // File lock settings
    public static final int LOCK_MAX_RETRIES = 10;
    public static final long LOCK_BASE_DELAY_MS = 100;
    public static final long LOCK_MAX_DELAY_MS = 10_000;
    public static final long LOCK_STALE_TIMEOUT_MS = 30_000;

    // Default state directory
    public static final String DEFAULT_STATE_DIR = ".jaiclaw";
    public static final String AGENTS_DIR = "agents";
}
```
