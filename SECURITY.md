# Security Policy

## Supported Versions

JaiClaw is pre-1.0. Security fixes ship on the latest minor release.
Older minor versions are best-effort.

| Version | Supported          |
| ------- | ------------------ |
| 0.9.x   | :white_check_mark: |
| 0.8.x   | :warning: best-effort |
| < 0.8   | :x: upgrade required |

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
- Dependency CVEs that we've already triaged as false-positives
  (tracked under `.security/dependency-check-suppressions.xml` once
  CI hardening lands — see the
  [codebase-analysis remediation plan](docs/CODEBASE-ANALYSIS-2026-06-10.md)).

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

If you find a default-on configuration that should be hardened, please
report it via the email above.
