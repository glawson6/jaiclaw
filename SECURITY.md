# Security Policy

## Supported Versions

Security fixes ship on the latest minor release. The previous minor is
best-effort; older lines require an upgrade.

| Version | Supported             |
| ------- | --------------------- |
| 1.2.x   | :white_check_mark:    |
| 1.1.x   | :warning: best-effort |
| 1.0.x   | :warning: best-effort |
| < 1.0   | :x: upgrade required  |

**1.2.0 fixed two critical defects present in every earlier release.** If you
are on 1.1.x or below, treat the upgrade as security-relevant rather than
routine:

- Tenant context could be established from an **unverified** JWT. In the
  default `api-key` mode no JWT-validating filter existed, so an attacker
  needed only to *omit* a valid signature to name another tenant.
- Tool authorization failed **open** — agents ran with the full tool surface
  (shell, filesystem, browser) on every request in api-key mode, on every
  channel-originated message, and on the `permitAll` `/webhook/**` path.

See [`releases/release-1.2.0.md`](releases/release-1.2.0.md) for the breaking
changes the upgrade carries.

## Reporting a Vulnerability

**Please do not open public GitHub issues for security vulnerabilities.**

Email **gregory.lawson@taptech.net** with:

1. A description of the issue and the affected modules / version(s).
2. Steps to reproduce, ideally with a minimal failing example.
3. The impact you observed (information disclosure, RCE, privilege
   escalation, denial-of-service, etc.).
4. Any suggested remediation, if you have one.

You should receive an acknowledgement within **3 business days**. If you
don't, please follow up — the email may have been caught by spam
filtering.

## Disclosure Timeline

We aim to:

- Acknowledge the report within 3 business days.
- Provide an initial assessment (severity, scope, planned fix) within
  10 business days.
- Release a patch within 30 days for high-severity issues, and within
  90 days for medium-severity issues.
- Credit the reporter in the release notes (unless they prefer to
  remain anonymous).

For critical issues (active exploitation, RCE in default
configuration), we will aim for a same-week patch and coordinate
disclosure.

## Scope

In-scope:

- Any module under `core/`, `channels/`, `extensions/`, `apps/`,
  `tools/`, `jaiclaw-starters/`, `jaiclaw-spring-boot-starter/`, or
  `jaiclaw-bom/`.
- The Maven Central artifacts published from this repository.
- Default-on behavior of `start.sh`, `quickstart.sh`, and the supplied
  Docker images.

Out of scope:

- Vulnerabilities in third-party LLM provider APIs themselves
  (Anthropic, OpenAI, etc.) — report those to the upstream vendor.
- Issues that require an attacker to already have administrative
  control of the JaiClaw process or the host it runs on.
- Issues in example apps under `jaiclaw-examples/` unless they
  demonstrate a flaw in JaiClaw itself (examples are documentation,
  not production-shaped).
- Dependency CVEs already triaged as false-positives, tracked in
  [`.security/dependency-check-suppressions.xml`](.security/dependency-check-suppressions.xml).

## Hardening Recommendations

Several security knobs are **opt-in** by design:

- `SPRING_PROFILES_ACTIVE=security-hardened` enables HMAC webhook
  verification, SSRF guards, timing-safe API-key comparison, and other
  hardening — see `docs/user/OPERATIONS.md`.
- `jaiclaw.compliance.profile={gdpr|hipaa|both}` layers GDPR + HIPAA
  orchestration on top of `security-hardened`: retention enforcement,
  LLM-call auditing, BAA-eligible provider warnings, and an HTTPS
  startup guard. Individual flags at `jaiclaw.compliance.*` override
  any element of the profile bundle. See
  [docs/user/COMPLIANCE.md](docs/user/COMPLIANCE.md) for the full
  article-to-capability mapping.
- `jaiclaw.tenant.strict-default-tenant-id=true` rejects weak
  `default-tenant-id` values at startup; required for production
  multi-tenant deployments.
- `jaiclaw.security.rate-limit.enabled=true` enables the built-in
  rate limiter (off by default — typically lives upstream at the CDN /
  gateway).
- HTTPS termination, secret storage, and inbound network policy are
  the operator's responsibility.

### Authentication (1.2.0)

- **`jaiclaw.security.default-tool-profile`** — the profile applied when no
  authenticated tool profile is present. **Defaults to `FULL` in 1.2.0**,
  which is fail-*open*: api-key callers and every channel-originated message
  reach the complete tool surface. The permissive default was kept for one
  minor so the fix did not silently strip access from existing deployments.
  **Set it explicitly.** It becomes `MINIMAL` in 1.3.0.
- **`jaiclaw.security.api-keys[]`** — replaces the single shared key. Each key
  binds to exactly one tenant and one role, so revoking a capability is
  deleting one key and a leaked key exposes one (tenant, role) pair. The
  legacy `jaiclaw.security.api-key` scalar still works in single-tenant mode.
- **`jaiclaw.security.mode=oidc`** — validates tokens against an identity
  provider's JWKS (`jaiclaw-security-oidc`, opt-in dependency). Preferred over
  `mode=jwt`, whose shared HMAC secret means every verifier can also mint
  tokens. Provider-agnostic.
- **`jaiclaw.identity.link.enabled=true`** — proves a channel user controls an
  external identity, so tenancy and authorization derive from the person
  rather than the bot. Without it, channel identity is asserted and never
  verified.

See [`docs/user/API-KEY-AUTHENTICATION.md`](docs/user/API-KEY-AUTHENTICATION.md),
[`docs/user/OIDC-AUTHENTICATION.md`](docs/user/OIDC-AUTHENTICATION.md) and
[`docs/user/VERIFIED-IDENTITY.md`](docs/user/VERIFIED-IDENTITY.md).

### Known weak defaults

Stated plainly so operators can decide rather than discover:

- **`default-tool-profile` is `FULL`** in 1.2.0 — see above.
- **Actuator endpoints perform no authorization of their own.**
  `/actuator/jaiclaw-estop` is state-mutating and can pause every agent in the
  deployment. Front `/actuator/**` with the same auth as the rest of your
  admin surface.
- **`/webhook/**` is `permitAll`** by design, since platforms cannot present a
  key. Sessions it creates are clamped to `WEBHOOK_SAFE`, and per-platform
  signature verification is **opt-in** via the `security-hardened` profile —
  enable it.
- **`AdminController` and `GdprController` default their role to `""`**, which
  the authorization helper treats as "any authenticated principal". Set
  `jaiclaw.gateway.admin.roles.admin` and
  `jaiclaw.compliance.gdpr.roles.operator` explicitly in production.

If you find a default-on configuration that should be hardened, please
report it via the email above.
