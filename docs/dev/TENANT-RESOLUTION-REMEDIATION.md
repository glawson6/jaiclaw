# Tenant Resolution Remediation

> **STATUS: IMPLEMENTED in 1.2.0.** This document records the design and its
> rationale. Two things differ from the plan as first drafted, both settled
> during review — read these before the body:
>
> 1. **No `HeaderTenantResolver`, no `jaiclaw.tenant.trust-header`.** The
>    `X-Tenant-Id` header is consumed by `ApiKeyAuthenticationFilter` at the
>    authentication boundary and becomes part of the principal, so there is no
>    tenant-bearing resolver reading raw headers and no separate trust opt-in.
> 2. **A missing tenant header is `401`, not `400`.** A missing header means the
>    credential is incomplete, so the response carries a `WWW-Authenticate`
>    challenge. `403` is reserved for a *known* key naming a tenant it is not
>    bound to.
>
> The API-key model is **one key → one tenant → one role**, with `role`
> required. See §3 and `docs/dev/LOGTO-IMPLEMENTATION-PLAN.md`.

*Created: September 17, 2026 · Target: 1.2.0 (fixes) + 1.3.0 (default flip)*

> **Scope.** Five defects in tenant resolution and tool authorization, two of
> them critical. All are independent of any identity-provider work and should
> ship regardless of whether the Logto integration
> ([LOGTO-OIDC-INTEGRATION-ANALYSIS](/Users/tap/dev/docs/jaiclaw/LOGTO-OIDC-INTEGRATION-ANALYSIS.md),
> [LOGTO-IMPLEMENTATION-PLAN](LOGTO-IMPLEMENTATION-PLAN.md)) proceeds.
> Nothing here adds a dependency or a module.

---

## 1. Summary

| # | Defect | Severity | Fix lands in |
|---|---|---|---|
| **D1** | `JwtTenantResolver` establishes tenant context from an **unverified** JWT payload | 🔴 Critical | 1.2.0 |
| **D2** | `ToolProfileHolder.getOrDefault()` returns `FULL` when unset — fail-**open** | 🔴 Critical | 1.2.0 (knob) / 1.3.0 (default) |
| **D3** | `ApiKeyAuthenticationFilter`'s multi-tenant branch is dead code (`tenantGuard` hardcoded `null`) | 🟠 High | 1.2.0 |
| **D4** | `jaiclaw.tenant.tenant-header` is configurable but never read | 🟡 Medium | 1.2.0 |
| **D5** | `RoleToolProfileResolver` ranks by `ordinal()`, contradicting `ToolProfile.privilege()` | 🟡 Medium | 1.2.0 |

D1 and D3 are the same story from two ends: the *intended* tenant mechanism for
api-key mode was never wired up, and an *unintended* one silently took its place.

### The organising principle

Every fix below follows one rule:

> **Authentication produces a principal that carries the tenant. Everything
> downstream reads the principal. Nothing re-derives trust from raw request
> attributes.**

This holds in *all* modes, including api-key. An API key authenticates the
**caller**; an `X-Tenant-Id` header names the **tenant that caller is acting
for**. Combining them at the authentication boundary produces a complete
principal — exactly as a JWT does when it carries both `sub` and a tenant claim.
There is no second trust path and no tenant-bearing resolver reading raw headers.

---

## 2. D1 — Unverified JWT establishes tenant context

### 2.1 What the code does

`core/jaiclaw-gateway/.../tenant/JwtTenantResolver.java:49-73`:

```java
String[] parts = jwt.split("\\.");
if (parts.length < 2) return Optional.empty();
String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
String tenantId = extractJsonValue(payload, tenantClaimName);
```

The signature segment (`parts[2]`) is never examined. `extractJsonValue`
(`:79-107`) is a hand-rolled string scanner, not a JSON parser — it returns the
**first** textual match for `"tenantId"` anywhere in the decoded payload.

The class Javadoc states the precondition it relies on:

> *"This resolver only parses the JWT payload to extract claims — actual
> signature validation is handled by jaiclaw-security's Spring Security filter."*

The class cannot enforce that precondition, and does not check it.

### 2.2 Why the precondition does not hold

`JwtTenantResolver` is registered **unconditionally** — there is no
`@ConditionalOnProperty` tying it to `jaiclaw.security.mode`:

```java
// jaiclaw-spring-boot-starter/.../JaiClawGatewayAutoConfiguration.java:58-61
@Bean
@ConditionalOnMissingBean(name = "jwtTenantResolver")
public JwtTenantResolver jwtTenantResolver() {
    return new JwtTenantResolver();
}
```

`CompositeTenantResolver` sorts by `order()`, so it is tried **first**
(`JwtTenantResolver.order() == 10` vs `BotTokenTenantResolver.order() == 20`).

Consumers, all of which pass raw request headers:

| Endpoint | Call site |
|---|---|
| `POST /api/chat` | `GatewayController.java:45` |
| `POST /v1/chat/completions` | `OpenAiCompatController.java:72` |
| `POST /mcp/{s}/tools/{t}` | `McpController.java:92` |
| `POST /mcp/{s}/resources/read` | `McpController.java:152` |
| `GET /mcp/{s}/sse`, `POST /mcp/{s}/jsonrpc` | `McpSseServerController.java:195,252` |

### 2.3 Two distinct exploit paths

**Path A — `mode=api-key` (the default).** No JWT-validating filter exists in
this mode at all. The attacker does not need to forge a signature; they need only
omit a valid one. Holding the single shared API key:

```http
POST /api/chat
X-API-Key: <the one shared key>
Authorization: Bearer eyJhbGciOiJub25lIn0.eyJ0ZW5hbnRJZCI6InZpY3RpbSJ9.x
```

The middle segment decodes to `{"tenantId":"victim"}`. Every downstream store —
sessions, memory, transcripts, cron, documents, audit — scopes to `victim`.

**Path B — `mode=jwt`.** `TenantContextHolder.set()` overwrites unconditionally
(`TenantContextHolder.java:27-29`), and the controller runs *after* the filter:

```
JwtAuthenticationFilter  → jjwt verifies signature → set(REAL tenant)
GatewayController:45     → resolveTenant(headers)  → set(SCRAPED tenant)   ← wins
```

Normally both read the same header and agree. They diverge under **parser
confusion**: jjwt performs a real JSON parse, while `extractJsonValue` substring-
matches the first `"tenantId"` occurrence. A token legitimately signed for tenant
`A` whose payload contains an attacker-controlled string or nested object bearing
`"tenantId":"B"` yields `A` from jjwt and `B` from the scraper.

**Conclusion: both authenticated modes are affected**, by different mechanisms.

### 2.4 Root cause — an inverted abstraction

```java
Optional<TenantContext> resolve(Map<String, String> attributes);
```

`Map<String,String>` of raw headers is a **pre-authentication** input type. The
SPI was shaped for `BotTokenTenantResolver` (map a bot token to a tenant, which
legitimately needs raw attributes). `JwtTenantResolver` was fitted into the same
shape even though its input had already been authenticated one layer up — forcing
it to re-derive trust from the wire format.

The correct model: **authentication produces a principal; tenancy is a claim read
off that principal.** Once a signature is verified there is no reason to revisit
the wire format.

### 2.5 The fix

Delete `JwtTenantResolver`. The replacement chain has only **two** resolvers,
because tenancy for every authenticated HTTP path is now carried by the principal:

| Resolver | `order()` | Input | Available when |
|---|---|---|---|
| `SecurityContextTenantResolver` | 5 | the validated `Authentication` principal | `mode=api-key`, `jwt`, `oidc` |
| `BotTokenTenantResolver` | 20 | channel identifiers | always (channel path) |

There is deliberately **no** header-reading resolver. The `X-Tenant-Id` header is
consumed by `ApiKeyAuthenticationFilter` at the authentication boundary (§3) and
becomes part of the principal — so by the time the resolver chain runs, the header
has already been converted into validated state.

#### `JaiClawAuthentication` — the tenant-carrying principal

A single `Authentication` type carries everything authentication established.
Today nothing implements `Authentication` in the codebase, so this is additive.

```java
package io.jaiclaw.security.authn;

/**
 * The authenticated caller, together with the tenant they are acting for and the
 * tool profile their credentials grant.
 *
 * <p>Produced by whichever authentication filter is active — API key, JWT, or
 * OIDC — so that every downstream consumer reads tenancy and authorisation from
 * one validated place instead of re-parsing request attributes.
 */
public class JaiClawAuthentication extends AbstractAuthenticationToken {

    private final String principalName;
    private final TenantContext tenantContext;   // nullable in SINGLE mode
    private final ToolProfile toolProfile;       // nullable — falls back to configured default
    private final AuthSource source;             // API_KEY | JWT | OIDC

    public enum AuthSource { API_KEY, JWT, OIDC }

    public JaiClawAuthentication(String principalName,
                                 TenantContext tenantContext,
                                 ToolProfile toolProfile,
                                 AuthSource source,
                                 Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.principalName = Objects.requireNonNull(principalName, "principalName");
        this.tenantContext = tenantContext;
        this.toolProfile = toolProfile;
        this.source = Objects.requireNonNull(source, "source");
        setAuthenticated(true);
    }

    @Override public Object getPrincipal()   { return principalName; }
    @Override public Object getCredentials() { return null; }

    public Optional<TenantContext> tenantContext() { return Optional.ofNullable(tenantContext); }
    public Optional<ToolProfile> toolProfile()     { return Optional.ofNullable(toolProfile); }
    public AuthSource source()                     { return source; }
}
```

#### `SecurityContextTenantResolver`

```java
/**
 * Resolves the tenant from the *already validated* security principal.
 *
 * <p>Reads only from {@link SecurityContextHolder} — never from raw request
 * attributes. It is therefore structurally incapable of establishing a tenant
 * from unverified input.
 */
public class SecurityContextTenantResolver implements TenantResolver {

    @Override
    public Optional<TenantContext> resolve(Map<String, String> attributes) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return Optional.empty();
        }
        if (auth instanceof JaiClawAuthentication jc) {
            return jc.tenantContext();
        }
        // Adopter-supplied Authentication: nothing we can safely read.
        return Optional.empty();
    }

    @Override
    public int order() { return 5; }
}
```

> **Why no `Jwt` branch here.** `spring-security-oauth2-jose` is not a dependency
> of `jaiclaw-gateway`, and adding one for this would pull Nimbus onto every
> classpath. Instead the OIDC module (Phase 1 of the Logto plan) supplies a
> `JwtAuthenticationConverter` that produces a `JaiClawAuthentication` — so this
> resolver needs no change when OIDC lands.

**Additionally, stop the overwrite at the call sites.** Independent of which
resolver wins, a validated context must never be replaced:

```java
// GatewayController.java:45, OpenAiCompatController.java:72 — same shape
if (TenantContextHolder.get() == null) {
    gatewayService.resolveTenant(headers).ifPresent(TenantContextHolder::set);
}
```

This two-line change alone closes Path B and is safely backportable on its own.

---

## 3. D3/D4 — Make the API key produce a complete principal

### 3.1 The dead code

`ApiKeyAuthenticationFilter:127-138` contains the *intended* tenant mechanism for
api-key mode:

```java
if (tenantGuard != null && tenantGuard.isMultiTenant()) {
    String tenantId = request.getHeader(TENANT_ID_HEADER);
    if (tenantId == null || tenantId.isBlank()) { /* 400 missing_tenant_id */ }
    TenantContextHolder.set(new DefaultTenantContext(tenantId, tenantId));
}
```

But the filter is constructed with `null`:

```java
// JaiClawSecurityAutoConfiguration.java:126
return new ApiKeyAuthenticationFilter(apiKeyProvider, null, ...);
```

The condition is permanently false. The block has never executed in any shipped
release. This is why nothing establishes tenancy in api-key mode — and why the
unverified JWT path in §2 became the de-facto mechanism.

Separately, `TenantConfigProperties.tenantHeader` (default `X-Tenant-Id`) is
configurable but read by nothing; the filter hardcodes the constant (**D4**).

### 3.2 The design: an API key *can* produce a full principal

The instinct to treat api-key mode as "authenticated but tenant-less" is what
created the gap. It is unnecessary. An API key authenticates the **caller**; the
`X-Tenant-Id` header names the **tenant that caller is acting for**. Both are
available at the filter, so the filter can emit a complete principal:

```
X-API-Key   → proves the caller is trusted        → principal identity
X-Tenant-Id → names the tenant being acted for    → TenantContext
                      ↓
            JaiClawAuthentication(source = API_KEY)
```

This is structurally identical to what a JWT gives you (`sub` + tenant claim in
one validated object) — the difference is only *how* the two facts were
established, which `AuthSource` records.

**Why this is sound.** The header is caller-controlled, but it is only read
**after** the API key has been verified. The trust statement is therefore: *"a
caller who holds this key is authorised to act for any tenant it names."* That is
the standard trusted-service-to-service model and is correct for a mesh-internal
gateway, a BFF, or a single-tenant appliance.

**Where it is not sound**, and must be stated plainly in the docs: if the API key
is distributed to parties who should *not* be able to act across tenant lines,
this model is wrong and `mode=jwt`/`oidc` is required — there, the tenant is
signed by the IdP and the caller cannot choose it.

Because the whole mechanism now lives behind a verified credential, it needs no
separate `trust-header` opt-in. The trust decision is already expressed by
choosing `mode=api-key` in a multi-tenant deployment.

### 3.3 The fix

Wire the `TenantGuard` that the constructor already accepts, read the configured
header name, and emit a `JaiClawAuthentication`:

```java
// ApiKeyAuthenticationFilter — after the key comparison succeeds

TenantContext tenant = null;
if (tenantGuard != null && tenantGuard.isMultiTenant()) {
    String tenantId = request.getHeader(tenantHeaderName);   // configurable (D4)
    if (tenantId == null || tenantId.isBlank()) {
        sendMissingTenantId(response);                        // 400
        return;
    }
    tenant = new DefaultTenantContext(tenantId, tenantId);
}

Authentication authentication = new JaiClawAuthentication(
        "api-key-user",
        tenant,                       // null in SINGLE mode — correct
        null,                         // profile falls back to configured default (§4)
        AuthSource.API_KEY,
        apiKeyAuthorities);           // see below
SecurityContextHolder.getContext().setAuthentication(authentication);
```

Two supporting changes:

**(a) Fix the bean wiring** — pass the real `TenantGuard`:

```java
@Bean
@ConditionalOnMissingBean(ApiKeyAuthenticationFilter.class)
ApiKeyAuthenticationFilter apiKeyAuthenticationFilter(
        ApiKeyProvider apiKeyProvider,
        JaiClawSecurityProperties properties,
        ObjectProvider<TenantGuard> tenantGuard,
        TenantConfigProperties tenantConfig) {
    return new ApiKeyAuthenticationFilter(
            apiKeyProvider,
            tenantGuard.getIfAvailable(),
            properties.timingSafeApiKey(),
            properties.apiKeyFilter().skipPaths(),
            tenantConfig.tenantHeader());
}
```

`ObjectProvider` keeps `jaiclaw-security` usable without `jaiclaw-config` on the
classpath; a null guard degrades to today's single-tenant behaviour.

**(b) Give the api-key principal configurable authorities.** Today it is created
with `List.of()` — zero authorities — which is why `@PreAuthorize` on
`AdminController` and `GdprController` can never pass once a real role is set
(and why both default to a blank role that allows everyone). Add:

```yaml
jaiclaw:
  security:
    api-key-roles: [ ]      # e.g. [jaiclaw.admin, gdpr.operator]
```

mapped to `SimpleGrantedAuthority` on the principal. This does not change the
default behaviour (empty list == today) but makes the method-security surface
usable in api-key deployments for the first time.

The filter no longer touches `TenantContextHolder` directly — it sets the
`Authentication`, and `SecurityContextTenantResolver` (§2.5) surfaces the tenant
to the gateway. One mechanism, one direction of trust.

### 3.4 Operating with no security configured

`mode=none` remains a first-class supported case: no filter runs, so no principal
and no tenant are established.

| `security.mode` | `tenant.mode` | Result |
|---|---|---|
| `none` | `single` | **Works.** No tenant needed; `TenantGuard` returns `"default"`. |
| `none` | `multi` | **Fails closed** — `TenantGuard.requireTenantIfMulti()` throws. Startup WARN (§3.5). |
| `api-key` | `single` | **Works.** Principal set, tenant null, `TenantGuard` returns `"default"`. |
| `api-key` | `multi` | **Works** — `X-Tenant-Id` required, 400 if absent. The intended D3 behaviour, now live. |
| `jwt` / `oidc` | `single` | Works; tenant claim ignored for storage keying. |
| `jwt` / `oidc` | `multi` | **Works, and recommended** — tenant is signed by the IdP; the caller cannot choose it. |

The `none` + `multi` combination is deliberately unsupported: with no
authentication there is no credential to bind a tenant claim to, and accepting a
bare header there would recreate exactly the D1 vulnerability. Deployments that
terminate authentication upstream (mesh, ingress) and want header-based tenancy
should use `mode=api-key` with a shared key — which is precisely the trusted
service-to-service model §3.2 describes.

### 3.5 Startup diagnostic

The `none` + `multi` combination currently fails at request time with an opaque
`IllegalStateException`. Emit a WARN at startup instead:

```java
// TenantModeStartupCheck — new, in jaiclaw-gateway
if (tenantProps.mode() == TenantMode.MULTI && "none".equals(securityMode)) {
    log.warn("jaiclaw.tenant.mode=multi with jaiclaw.security.mode=none: no "
           + "authentication means no tenant can be established, and every "
           + "request requiring tenant context will fail. Use security.mode="
           + "api-key (with X-Tenant-Id) or jwt/oidc.");
}
```

## 4. D2 — `ToolProfileHolder` fails open

### 4.1 The defect

```java
// core/jaiclaw-core/.../ToolProfileHolder.java:28-31
public static ToolProfile getOrDefault() {
    ToolProfile p = PROFILE.get();
    return p != null ? p : ToolProfile.FULL;   // fail-OPEN
}
```

`GatewayService` calls this at `:221`, `:269`, `:290`. Only
`JwtAuthenticationFilter:66-69` ever sets a narrower profile, and only for HTTP
requests in `jwt` mode. Therefore agents run with **`FULL` tool access** — which
includes `kubectl`, code execution, and browser automation — in all of:

- `mode=api-key` (the default) — every request
- `mode=none` — every request
- **every channel-originated message**, in all modes
- the `permitAll` `/webhook/**` path, in all modes

A `ToolProfile.WEBHOOK_SAFE` value exists for precisely the last case and is never
applied anywhere.

### 4.2 Fix — configurable default now, safe default later

Flipping the constant outright would silently strip tool access from every
existing api-key deployment. Introduce a knob first (**option b**), then change
its default in the next minor (**option a**):

**1.2.0** — add the property, keep today's behaviour as the default:

```yaml
jaiclaw:
  security:
    default-tool-profile: FULL    # 1.2.0 default (unchanged behaviour)
                                  # 1.3.0 default: MINIMAL
```

`getOrDefault()` is deprecated in favour of an explicitly-supplied fallback, so
`jaiclaw-core` stays free of configuration concerns:

```java
/** @deprecated fails open. Use {@link #getOrDefault(ToolProfile)}. */
@Deprecated(since = "1.2.0", forRemoval = true)
public static ToolProfile getOrDefault() {
    return getOrDefault(ToolProfile.FULL);
}

/** Returns the current profile, or {@code fallback} when none is set. */
public static ToolProfile getOrDefault(ToolProfile fallback) {
    ToolProfile p = PROFILE.get();
    return p != null ? p : Objects.requireNonNull(fallback, "fallback");
}
```

`GatewayService` holds the configured default and passes it explicitly at all
three call sites. A WARN is logged once at startup when the effective default is
`FULL` **and** security mode is not `none`:

```
jaiclaw.security.default-tool-profile=FULL — unauthenticated and api-key
callers receive unrestricted tool access. This default changes to MINIMAL in
1.3.0. Set it explicitly to silence this warning.
```

**1.3.0** — default becomes `MINIMAL`; note as a breaking change in the release
notes with the one-line remedy (`default-tool-profile: FULL`).

### 4.3 Apply `WEBHOOK_SAFE` on the webhook path

Independent of the default, `GatewayController.webhook()` should narrow the
profile for the duration of the call, since `/webhook/**` is `permitAll`:

```java
ToolProfileHolder.set(ToolProfile.narrowest(
        configuredDefault, ToolProfile.WEBHOOK_SAFE));
try { ... } finally { ToolProfileHolder.clear(); }
```

`ToolProfile.narrowest(a, b)` already exists (`ToolProfile.java:50-54`).

---

## 5. D5 — `RoleToolProfileResolver` uses the wrong ordering

```java
// RoleToolProfileResolver.java:39
if (mapped != null && (best == null || mapped.ordinal() > best.ordinal())) {
```

`ordinal()` reflects declaration order
(`NONE, MINIMAL, CODING, MESSAGING, WEBHOOK_SAFE, FULL`), but the intended lattice
is `ToolProfile.privilege()`
(`NONE 0 < MINIMAL 1 < WEBHOOK_SAFE 2 < MESSAGING 3 < CODING 4 < FULL 5`).

They disagree: by `ordinal()`, `MESSAGING` outranks `CODING`; by `privilege()`,
`CODING` outranks `MESSAGING`. A user holding both roles gets the wrong profile.

Fix: compare `mapped.privilege() > best.privilege()`.

---

## 6. Work Breakdown

| # | Task | Module | Est. |
|---|---|---|---|
| R1 | `JaiClawAuthentication` (principal + tenant + profile + source) | `jaiclaw-security` | 0.5d |
| R2 | `SecurityContextTenantResolver` | `jaiclaw-gateway` | 0.5d |
| R3 | Delete `JwtTenantResolver`; remove its bean definition | `jaiclaw-gateway`, starter | 0.25d |
| R4 | Guard all `resolveTenant` call sites against overwriting a set context | `GatewayController`, `OpenAiCompatController`, `McpController`, `McpSseServerController` | 0.5d |
| R5 | **`ApiKeyAuthenticationFilter` emits `JaiClawAuthentication`**; honour configured tenant header; fix the `null` `TenantGuard` wiring | `jaiclaw-security`, starter | 1d |
| R6 | `jaiclaw.security.api-key-roles` → principal authorities | `jaiclaw-security` | 0.25d |
| R7 | `JwtAuthenticationFilter` emits `JaiClawAuthentication` (parity with R5) | `jaiclaw-security` | 0.5d |
| R8 | `TenantModeStartupCheck` WARN (`none` + `multi`) | `jaiclaw-gateway` | 0.25d |
| R9 | `ToolProfileHolder.getOrDefault(fallback)` + deprecate no-arg | `jaiclaw-core` | 0.25d |
| R10 | `default-tool-profile` property; thread through `GatewayService`; startup WARN | `jaiclaw-security`, `jaiclaw-gateway` | 0.5d |
| R11 | Apply `WEBHOOK_SAFE` on `/webhook/**` | `GatewayController` | 0.25d |
| R12 | `RoleToolProfileResolver` → `privilege()` | `jaiclaw-security` | 0.1d |
| R13 | Spock specs (see §7) | test | 1.5d |
| R14 | Docs + release notes | docs | 0.5d |

**Total ≈ 6.5 days.** No new dependencies, no new modules.

**Suggested ordering.** R1 → R5 → R7 → R2 → R3 → R4 closes D1/D3/D4 as one
coherent change (the principal exists before anything reads it). R9–R12 are
independent and can land in parallel.

---

## 7. Required Tests

Negative tests are the point of this work — each must fail against `main` today.

| Spec | Asserts |
|---|---|
| `UnsignedJwtRejectedSpec` | `Bearer <b64header>.<b64{"tenantId":"victim"}>.garbage` with a valid API key does **not** yield tenant `victim` |
| `ValidatedTenantNotOverwrittenSpec` | In `mode=jwt`, a signed token for tenant `A` yields `A` even when the payload also contains a second `"tenantId":"B"` (parser-confusion case) |
| `ApiKeyTenantFromHeaderSpec` | `mode=api-key` + `multi`: valid key + `X-Tenant-Id: acme` yields tenant `acme` on the `JaiClawAuthentication` |
| `ApiKeyMissingTenantHeaderSpec` | `mode=api-key` + `multi`: valid key, no `X-Tenant-Id` → **400 `missing_tenant_id`**, request never reaches the agent |
| `ApiKeyIgnoresAuthorizationHeaderSpec` | `mode=api-key`: an `Authorization: Bearer` header has **no effect** on the resolved tenant |
| `ApiKeySingleTenantSpec` | `mode=api-key` + `single`: no header required; `TenantGuard` yields `"default"` |
| `ConfigurableTenantHeaderSpec` | `jaiclaw.tenant.tenant-header: X-Org` is honoured (D4) |
| `ApiKeyRolesSpec` | `api-key-roles: [jaiclaw.admin]` makes `@PreAuthorize("hasAuthority('jaiclaw.admin')")` pass |
| `ToolProfileDefaultSpec` | Configured `default-tool-profile` is honoured; `MINIMAL` yields a restricted tool set |
| `WebhookProfileNarrowingSpec` | `/webhook/**` never exceeds `WEBHOOK_SAFE` |
| `RoleToolProfilePrivilegeSpec` | Roles mapping to `{CODING, MESSAGING}` resolve to `CODING` |

---

## 8. Upgrade Notes (for release notes)

**1.2.0**

- **`JwtTenantResolver` is removed.** Tenant is now carried by the authenticated
  principal (`JaiClawAuthentication`) in every mode. Nothing reads a tenant claim
  from an unverified `Authorization` header any more.
- **🔴 Behaviour change — `mode=api-key` + `tenant.mode=multi` now requires an
  `X-Tenant-Id` header**, returning **400 `missing_tenant_id`** when it is absent.
  This is the documented-but-never-executed behaviour from `ApiKeyAuthenticationFilter`
  being wired correctly for the first time. Callers that previously relied on
  sending a `tenantId` inside an unsigned `Authorization` header must switch to
  the header. Single-tenant deployments are unaffected.
- **`jaiclaw.tenant.tenant-header` is now honoured** (previously ignored).
- `jaiclaw.security.api-key-roles` added (default empty) — grants authorities to
  the api-key principal so `@PreAuthorize` on `AdminController` /
  `GdprController` becomes usable. Empty list preserves current behaviour.
- **`ToolProfileHolder.getOrDefault()` is deprecated**; prefer
  `getOrDefault(ToolProfile)`. Behaviour unchanged in 1.2.0.
- `jaiclaw.security.default-tool-profile` added, defaulting to `FULL`
  (unchanged behaviour). **This default becomes `MINIMAL` in 1.3.0.**
- Startup WARN for `tenant.mode=multi` with `security.mode=none`.

**1.3.0 (planned, breaking)**

- `jaiclaw.security.default-tool-profile` defaults to `MINIMAL`. Deployments
  relying on implicit `FULL` access must set it explicitly.

---

## 9. Relationship to the Logto Plan

`JaiClawAuthentication` (R1) is the contract the OIDC work builds on: Phase 1 of
[LOGTO-IMPLEMENTATION-PLAN](LOGTO-IMPLEMENTATION-PLAN.md) supplies a
`JwtAuthenticationConverter` that produces the same type with
`source = OIDC`, so `SecurityContextTenantResolver` and every downstream consumer
need **no change** when OIDC lands.

Everything else here is terminal — no part of this document depends on the
Logto decision.
