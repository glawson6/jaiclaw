# OIDC Authentication

*Applies to 1.3.0 and later. Module: `jaiclaw-security-oidc`.*

Authenticate callers with an OAuth 2.0 / OpenID Connect identity provider
instead of API keys. **Provider-agnostic** — nothing in the module names a
vendor; every value that differs between providers is configuration.

---

## 1. When to use it

| Mode | Use when |
|---|---|
| `api-key` | Service-to-service calls, single-user CLI, local development |
| **`oidc`** | **Real users, SSO, per-user authorization, or an existing IdP** |
| `jwt` | Legacy shared-secret HMAC tokens. Superseded by `oidc`. |
| `none` | Local development on a loopback bind only |

OIDC gives you what API keys structurally cannot: a **verified user identity**,
tenancy signed by the provider rather than asserted by the caller, per-user
authorization via scopes, and key rotation without redeploying.

---

## 2. Setup

Add the module — it is **not** pulled by `jaiclaw-spring-boot-starter`, so the
OAuth2 stack stays off the classpath of deployments that do not use it:

```xml
<dependency>
    <groupId>io.jaiclaw</groupId>
    <artifactId>jaiclaw-security-oidc</artifactId>
</dependency>
```

```yaml
jaiclaw:
  security:
    mode: oidc
    oidc:
      issuer-uri: https://idp.example.com/oidc
      audience: https://jaiclaw.example.com/api
      tenant-claim: organization_id
      scope-to-profile:
        "jaiclaw:tools:full": FULL
        "jaiclaw:tools:coding": CODING
        "jaiclaw:tools:minimal": MINIMAL
```

### Reference

| Property | Default | Notes |
|---|---|---|
| `issuer-uri` | — | **Required.** Validates `iss`; derives the JWKS location. |
| `jwk-set-uri` | `{issuer-uri}/jwks` | Set explicitly when your provider differs. |
| `audience` | — | **Strongly recommended** — see §4. |
| `tenant-claim` | `organization_id` | Claim carrying the tenant id. |
| `jws-algorithms` | `[RS256, ES256, ES384]` | Allowlist. Asymmetric only. |
| `accepted-types` | `[at+jwt, JWT]` | JOSE `typ` values. |
| `required-scope` | *(none)* | Optional deployment-wide gate. |
| `scope-to-profile` | `{}` | Scope → `ToolProfile`; highest privilege wins. |

---

## 3. Provider examples

**Logto** — EC P-384 keypair by default, so `ES384`:

```yaml
      issuer-uri: https://logto.example.com/oidc
      jwk-set-uri: https://logto.example.com/oidc/jwks
      tenant-claim: organization_id
      jws-algorithms: [ES384, RS256]
```

**Keycloak** — note the non-conventional JWKS path:

```yaml
      issuer-uri: https://kc.example.com/realms/jaiclaw
      jwk-set-uri: https://kc.example.com/realms/jaiclaw/protocol/openid-connect/certs
      tenant-claim: organization
      jws-algorithms: [RS256]
```

**Okta:**

```yaml
      issuer-uri: https://example.okta.com/oauth2/default
      jwk-set-uri: https://example.okta.com/oauth2/default/v1/keys
      tenant-claim: org_id
```

**Microsoft Entra ID** — scopes arrive in `scp` rather than `scope`; both are read:

```yaml
      issuer-uri: https://login.microsoftonline.com/{tenant}/v2.0
      jwk-set-uri: https://login.microsoftonline.com/{tenant}/discovery/v2.0/keys
      tenant-claim: tid
```

---

## 4. Four things that will cost you a day each

These are the non-obvious details. Each produces a failure that looks like
something else.

### 4.1 The signing algorithm is probably not RS256

Spring defaults to RS256. Several providers — Logto among them — use an **EC
P-384 keypair, signing with ES384**. The symptom is every token failing
validation as though the signature were bad.

```yaml
      jws-algorithms: [ES384, RS256]
```

Only asymmetric algorithms are accepted. `HS*` and `none` are refused at
startup: a resource server validating against a *public* JWKS must never accept
a symmetric algorithm, or anyone who can read the public key can mint tokens.

### 4.2 Access tokens carry `typ: at+jwt`, which Spring rejects twice

RFC 9068 access tokens use `typ: at+jwt`. Spring applies **two independent**
checks that accept only `JWT` — a JOSE-level type verifier and a
`JwtTypeValidator` in the default validator chain. JaiClaw handles both; the
detail matters if you supply your own `JwtDecoder` bean, because configuring one
and not the other still fails, with an error naming `typ` either way.

### 4.3 Set the audience, or accept other services' tokens

Without `audience`, a token the same issuer minted for a **different service** is
accepted here: valid signature, valid issuer, wrong recipient. A confused-deputy
hole. JaiClaw warns at startup when it is unset.

```yaml
      audience: https://jaiclaw.example.com/api
```

The value must match the resource indicator your provider stamps into `aud`.

### 4.4 Clients must request the audience (RFC 8707)

Most providers only mint a token audienced to JaiClaw when the authorization or
token request carries `resource={your-indicator}`. **Spring Security has no
first-class support for that parameter** — a browser or CLI login flow needs a
custom `OAuth2AuthorizationRequestResolver`.

This is a *client-side* concern; JaiClaw is the resource server. But a resource
server that never receives correctly-audienced tokens looks broken for a reason
that is not visible from its own logs.

---

## 5. Authorization: scopes, not roles

JaiClaw maps **scopes** to tool profiles:

```yaml
      scope-to-profile:
        "jaiclaw:tools:coding": CODING
```

Roles are commonly an ID-token or userinfo claim and are **frequently absent
from access tokens**. The space-delimited `scope` claim is the one authorization
signal an OAuth resource server can rely on.

To drive this from roles, have the provider project roles into scopes at token
issuance — most support a claim-mapping or token-customizer hook. That keeps the
decision at the issuer, where it belongs, and off JaiClaw's hot path.

When several mapped scopes are held the **highest privilege wins**. Unmapped
scopes fall back to `jaiclaw.security.default-tool-profile`.

Scopes also become Spring authorities prefixed `SCOPE_`:

```java
@PreAuthorize("hasAuthority('SCOPE_jaiclaw:admin')")
```

---

## 6. Multi-tenancy

The tenant comes from a **signed claim**, so unlike api-key mode the caller
cannot choose it:

```yaml
jaiclaw:
  tenant:
    mode: multi
  security:
    mode: oidc
    oidc:
      tenant-claim: organization_id
```

A token with no tenant claim still authenticates, with no tenant — `TenantGuard`
decides whether that is acceptable. This is deliberate: single-tenant
deployments must work with a stock provider token that carries no organization.

---

## 7. Key rotation

Handled automatically. `NimbusJwtDecoder` caches the JWKS and refetches on an
unrecognised `kid`, so providers doing staged rotation need no coordination and
no restart.

---

## 8. Troubleshooting

| Symptom | Likely cause |
|---|---|
| Every token 401s, signature looks fine | Wrong `jws-algorithms` — see §4.1 |
| `the given typ value needs to be one of [JWT]` | `at+jwt` handling — §4.2 |
| `invalid_token` / audience error | `aud` does not contain your indicator — §4.3, §4.4 |
| Authenticated but no tools available | No scope mapped; check `default-tool-profile` |
| Multi-tenant requests fail with no context | Token carries no `tenant-claim` |

Raise logging to see the resolved principal per request:

```yaml
logging:
  level:
    io.jaiclaw.security.oidc: DEBUG
```

The startup line reports the effective configuration:

```
OIDC resource server configured: issuer=..., jwks=..., audience=..., algorithms=[ES384, RS256]
```

---

## 9. See also

- [`API-KEY-AUTHENTICATION.md`](API-KEY-AUTHENTICATION.md) — the default mode
- [`docs/dev/LOGTO-IMPLEMENTATION-PLAN.md`](../dev/LOGTO-IMPLEMENTATION-PLAN.md) — roadmap

---

## 10. MCP client authorization (RFC 9728)

*Added in 1.3.0.*

MCP clients discover which authorization server protects a resource by fetching
a **Protected Resource Metadata** document from the resource server. Identity
providers do not publish this on a resource's behalf — so JaiClaw serves it.

```yaml
jaiclaw:
  mcp:
    auth:
      enabled: true
      # Both default to the jaiclaw.security.oidc values below, so a correctly
      # configured OIDC deployment usually needs only the flag above.
      resource: https://jaiclaw.example.com/api
      authorization-servers:
        - https://idp.example.com/oidc
      scopes-supported:
        - jaiclaw:tools:coding
        - jaiclaw:tools:full
      # Needed when the public address differs from the resource identifier's
      # origin — behind an ingress or a port mapping.
      public-base-url: https://jaiclaw.example.com
```

`GET /.well-known/oauth-protected-resource` then returns:

```json
{
  "resource": "https://jaiclaw.example.com/api",
  "authorization_servers": ["https://idp.example.com/oidc"],
  "scopes_supported": ["jaiclaw:tools:coding", "jaiclaw:tools:full"],
  "bearer_methods_supported": ["header"]
}
```

The endpoint is unauthenticated **by necessity** — a client cannot authenticate
until it has read it — and is added to the permit list in every security mode.
It discloses only issuer URLs and scope names, never who is registered or what
they may access. Publication is still opt-in.

### The discovery handshake

Publishing the document is only half of it. A client that receives a bare
`WWW-Authenticate: Bearer` has no way to know where the document lives, so 401s
from `/mcp/**` carry a pointer:

```
WWW-Authenticate: Bearer error="invalid_token",
                  resource_metadata="https://jaiclaw.example.com/.well-known/oauth-protected-resource"
```

Any `error` detail from the authentication layer is preserved; only the pointer
is appended.

### Choosing a client type

| MCP caller | Register as | Why |
|---|---|---|
| Interactive, user-delegated (IDE, desktop agent) | Public client, or an unregistered client where the provider supports it | The user authenticates and consents |
| Service-to-service, unattended | Confidential / machine-to-machine client | Needs `client_credentials` |

Some providers support unregistered clients that use an HTTPS URL as their
`client_id` (Logto implements the CIMD draft for this), which suits interactive
MCP clients well. Such clients are typically restricted to
`authorization_code` + `refresh_token` and **cannot** use `client_credentials`,
so unattended callers still need a registered machine-to-machine client.

> **Not yet implemented: per-tool scope enforcement.** Authorization is
> currently profile-level — a scope maps to a `ToolProfile`, which gates a
> group of tools. Per-tool scopes are a core API change across `ToolDefinition`
> and `ToolRegistry` and are deliberately deferred to their own design note.
