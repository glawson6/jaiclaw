# JaiClaw 0.9.2 Release Notes

**Release Date:** TBD

> 0.9.2 is the **secrets baseline** release — one coherent theme threaded
> through nine commits. We introduce a generic **`SecretsProvider` SPI**
> in `jaiclaw-core` with reference implementations for the existing env-var
> pattern, a `.env` file, and 1Password (via the `op` CLI). On top of that
> we **flip five security hardening flags to default-on**, close two
> outstanding tenant-context bugs in the pipeline (SEV-006 + SEV-010),
> migrate the GitHub Actions secrets to 1Password, document the K8s
> secret-injection contract, ship a `--use-1password` launcher flag for
> standalone deploys, and pick up a small set of CVE-driven dependency
> bumps.
>
> Why this is the theme: 1.0 is a stability commitment, and you can't make
> a stability commitment on top of secrets scattered across GitHub repo
> secrets, hardcoded `application.yml` placeholders, and operator memory.
> 0.9.2 establishes the secrets posture that 1.0 will freeze.
>
> **One BREAKING CHANGE for operators**: security hardening flags now
> default to **on**. Operators upgrading `0.9.1 → 0.9.2` with
> `mode=none` on a non-localhost bind, or with webhook signature
> verification disabled by virtue of unset env vars, must read the
> Breaking Changes section below before upgrading production.

---

## Highlights

- **New `io.jaiclaw.core.secrets` SPI** — a pluggable resolver for
  secret values from arbitrary backing stores. Pure Java records and
  sealed interfaces in `jaiclaw-core` (no Spring dependency). Three
  reference implementations: `EnvironmentSecretsProvider` (reads
  `System.getenv()` — the default; preserves today's `${VAR}` resolution
  behavior), `FileSecretsProvider` (reads `.env`-style files), and
  `OnePasswordSecretsProvider` (shells out to the `op` CLI; lives in a
  new optional extension module `jaiclaw-secrets-1password`).
  `SecretsResolver` chains providers; `TenantSecretsResolver` adds
  per-tenant prefixing. Spring-side wiring: a new high-precedence
  `PropertySource` installed by `SecretsEnvironmentPostProcessor` so
  existing `${VAR}` placeholders in `application.yml` resolve through
  the chain transparently. Opt-in via `jaiclaw.secrets.provider`; unset
  defaults to today's pure-env behavior. 57 specs across the SPI + 16
  Spring autoconfig specs cover the contract.

- **Security hardening flags now default to ON** — five flags
  (`jaiclaw.security.timing-safe-api-key`,
  `jaiclaw.channels.slack.verify-signature`,
  `jaiclaw.channels.telegram.verify-webhook`,
  `jaiclaw.channels.telegram.mask-bot-token`,
  `jaiclaw.tools.web.ssrf-protection`,
  `jaiclaw.tools.code.workspace-boundary`) flip from default-off
  (requiring `SPRING_PROFILES_ACTIVE=security-hardened` to enable) to
  default-on in both `gateway-app` and `shell`'s `application.yml`. A
  new `application-dev.yml` profile turns them off for localhost. See
  Breaking Changes for the operator impact.

- **Fail-fast guard on `mode=none` + non-localhost bind** — when
  `jaiclaw.security.mode=none` AND `server.address` is not a loopback
  address, the application now refuses to start. The waiver is
  `jaiclaw.security.allow-none-on-public-bind=true`, with a documented
  WARN log on every startup. Live since 0.9.2 because we kept seeing
  staging incidents from operators who forgot to set `mode=api-key` in
  production.

- **`--use-1password` standalone runtime flag** — both `bin/jaiclaw`
  and `start.sh` accept a new `--use-1password` global flag (aliases:
  `--use-onepassword`, `--op`). When set, the launcher re-execs itself
  under `op run --env-file=<.env.op.tpl>` so 1Password-resolved secrets
  arrive as env vars before the JVM starts. Two complementary setup
  paths: a pre-JVM `jaiclaw setup-1password` bash command for fresh
  installs and per-key migration, plus a new step in the Spring Shell
  `jaiclaw setup` wizard that's silently skipped when `op` isn't on
  PATH. Both produce the same `.env.op.tpl` reference format; both
  preserve the existing `.env` non-destructively.

- **GitHub Actions secrets migrated to 1Password** — the
  `publish-central.yml` and `e2e-tests.yml` workflows now load Maven
  Central / GPG / Anthropic / OSS-Index secrets via
  `1password/load-secrets-action@v2`. The only GitHub-native repo
  secret that remains is `OP_SERVICE_ACCOUNT_TOKEN` (the bootstrap
  Service Account token). New operator-side tooling under `op-vault/`
  (gitignored): `op-upsert-maven-central.sh` and the general
  `op-upsert-ci-secrets.sh` for upserting items into the 1Password
  vault from `maven-central-deploy/.env`. Both scripts are
  idempotent and have a `--dry-run` mode.

- **SEV-006: `PipelineSyncCoordinator` tenant context propagation** —
  the coordinator dispatched future completions on a dedicated
  completion-pool executor. Continuations attached by callers via
  `.thenApply` / `.thenAccept` ran on that pool with no tenant
  context, so downstream multi-tenant code saw either nothing or
  whichever tenant the last task left behind. Fix: capture
  `TenantContext` at `register()` time and store it on the
  `PendingEntry`. `complete()` / `completeExceptionally()` wrap the
  scheduled task with `withTenant()` which sets the captured tenant
  before running. The wrapper deliberately doesn't clear in a
  `finally` block — that would defeat continuations that run on the
  same executor thread immediately after the supplier returns. Safe
  because every wrapped task SETs (replaces) the tenant, so no
  cross-tenant leak between successive tasks.

- **SEV-010: `PipelineActuatorEndpoint` cross-tenant data leak** —
  `/actuator/pipelines/{id}` returned recent executions across all
  tenants, and `/actuator/pipelines/{id}/{executionId}` returned the
  full execution detail (including failure reasons) for any tenant.
  Fix: inject `TenantGuard` (optionally, via `ObjectProvider` for
  back-compat). In MULTI mode, filter recent executions to the current
  tenant; treat cross-tenant by-id lookups exactly like not-found so
  we don't leak the existence of another tenant's execution.

- **K8s secret-injection contract documented** — new section in
  `docs/user/PRODUCTION-DEPLOYMENT.md` §3.1 listing every env var the
  production gateway expects, with a recommended 1Password Connect
  Operator wiring pattern using per-domain Secrets
  (`jaiclaw-gateway-auth`, `jaiclaw-llm-keys`, `jaiclaw-telegram`,
  etc.). Vendor-agnostic alternatives (Vault/ESO, AWS SM/Azure KV/GCP
  SM, sealed-secrets) covered as equivalents. The K8s Connect Operator
  install and the Helm chart that consumes the Secrets live in
  separate repos (taptech-sentinel and jaiclaw.io respectively); the
  JaiClaw repo documents the contract.

- **CVE quick wins** — `jsoup` bumped 1.18.3 → 1.22.2 (XSS / DOM
  parser fixes). Three confirmed CPE false positives documented in
  `.security/dependency-check-suppressions.xml` with explicit `<notes>`
  rationale: `com.linecorp.bot:line-bot-jackson`,
  `org.apache.camel:camel-telegram`,
  `org.apache.camel:camel-whatsapp`.

---

## Breaking Changes

### Security hardening flags default-on

**Who's affected:** any operator running 0.9.1 in production without
`SPRING_PROFILES_ACTIVE=security-hardened` AND relying on default-off
behavior of any of these flags.

**What changes:**

| Flag                                          | 0.9.1 default | 0.9.2 default |
|-----------------------------------------------|---------------|---------------|
| `jaiclaw.security.timing-safe-api-key`        | off           | **on**        |
| `jaiclaw.channels.slack.verify-signature`     | off           | **on**        |
| `jaiclaw.channels.telegram.verify-webhook`    | off           | **on**        |
| `jaiclaw.channels.telegram.mask-bot-token`    | off           | **on**        |
| `jaiclaw.tools.web.ssrf-protection`           | off           | **on**        |
| `jaiclaw.tools.code.workspace-boundary`       | off           | **on**        |

**Migration steps:**

1. **For production deployments:** verify your Slack signing secret and
   Telegram webhook token are correctly configured. If `SLACK_SIGNING_SECRET`
   is unset and you have inbound Slack webhooks, every webhook will be
   rejected after upgrade. Same for Telegram.
2. **For local development:** activate the new `dev` profile to restore
   pre-0.9.2 defaults: `SPRING_PROFILES_ACTIVE=dev`. Don't ship `dev`
   to production.
3. **For Docker / CI test runs:** the e2e harness sets these flags
   appropriately for the test scenarios; no operator action needed.

### `mode=none` requires loopback bind or explicit waiver

**Who's affected:** any operator running the gateway with
`jaiclaw.security.mode=none` AND a non-localhost `server.address` (the
default is empty — interpreted as "all interfaces" — which is treated
as public).

**What changes:** the app refuses to start with a clear error message
pointing at the three remediation options:

```
Refusing to start: jaiclaw.security.mode=none on bind 'all interfaces (default)'.
Unauthenticated access on a non-loopback bind is almost certainly a
configuration error. Either:
  - Set jaiclaw.security.mode=api-key (or jwt) for production, OR
  - Set server.address=127.0.0.1 to constrain to localhost, OR
  - If you really mean it, set jaiclaw.security.allow-none-on-public-bind=true.
```

**Migration steps:** pick one of the three options above. The shell
app is `web-application-type: none` so the guard doesn't trigger
there; the gateway app is the only one affected.

### `JaiClawSecurityProperties` constructor signature

**Who's affected:** code that directly constructs
`JaiClawSecurityProperties` via the positional record constructor
(typically test fixtures). External adopters who use the builder
(`JaiClawSecurityProperties.builder()...build()`) are unaffected.

**What changes:** a 9th field `allowNoneOnPublicBind` was added at the
end of the record. The previous 8-arg constructor was removed (no
back-compat shim — clean break).

**Migration steps:** add `, false` (or `, true` if you want the
waiver) as the 9th argument. The builder has a new
`.allowNoneOnPublicBind(boolean)` method.

---

## New Features

### `SecretsProvider` SPI (opt-in)

`io.jaiclaw.core.secrets.SecretsProvider` is the new pluggable
secrets-resolution interface. `SecretsResolver` consults a chain of
providers in order; misses fall through to the next provider; provider
errors are reported via the sealed `SecretResolution` sum type rather
than thrown. `TenantSecretsResolver` adds optional per-tenant prefixing.

Three reference implementations ship in 0.9.2:

| Provider                       | Module                          | Backing store              |
|--------------------------------|----------------------------------|----------------------------|
| `EnvironmentSecretsProvider`   | `jaiclaw-core`                   | `System.getenv()`          |
| `FileSecretsProvider`          | `jaiclaw-core`                   | `.env`-style file          |
| `OnePasswordSecretsProvider`   | `jaiclaw-secrets-1password` (new) | `op` CLI shell-out         |

Spring wiring in the existing `jaiclaw-spring-boot-starter`:

- `SecretsEnvironmentPostProcessor` (registered via `spring.factories`)
  runs at `Ordered.HIGHEST_PRECEDENCE + 10` and installs a high-
  precedence `PropertySource` so `${VAR}` placeholders in
  `application.yml` resolve through the configured provider chain.
- `JaiClawSecretsAutoConfiguration` (gated on
  `jaiclaw.secrets.provider`) exposes `SecretsResolver` and
  `TenantSecretsResolver` as beans for direct injection.

**Configuration:**

```yaml
jaiclaw:
  secrets:
    provider: composite           # one of: env, file, onepassword, composite
    chain: [env, file, onepassword]  # for composite only
    chain-on-error: continue      # or fail
    file:
      path: ${JAICLAW_PROFILE_DIR}/.env
    onepassword:
      vault: TapTech-Security
      service-account-token: ${OP_SERVICE_ACCOUNT_TOKEN}
```

**Opt-in.** Default behavior (no `jaiclaw.secrets.provider` set) is
identical to 0.9.1 — only `EnvironmentSecretsProvider` is consulted via
the standard Spring `Environment`.

### `--use-1password` launcher flag

Both `bin/jaiclaw` and `start.sh` accept a new global flag. When set,
the launcher re-execs itself under `op run --env-file=<.env.op.tpl>`
so 1Password-resolved secrets arrive as env vars before the JVM
starts. Setup wizard offers (only when `op` is on PATH) to scaffold
the template alongside the regular config; a pre-JVM bash command
`jaiclaw setup-1password` covers fresh installs and per-key migration.

See `docs/user/PRODUCTION-DEPLOYMENT.md` §3.1.3 for the full
template reference and operator playbook.

### K8s deployment contract

`docs/user/PRODUCTION-DEPLOYMENT.md` §3.1 now exhaustively lists every
env var the production gateway expects, with three documented paths
for getting them into K8s `Secret`s:

- §3.1.1 — Recommended: 1Password Connect Operator + per-domain
  `OnePasswordItem` CRDs.
- §3.1.2 — Vendor-agnostic alternatives (Vault/ESO, AWS SM, sealed-
  secrets).
- §3.1.3 — Standalone / non-K8s (`op run` via launcher).

The contract is stable: a K8s `Secret` whose data keys match the
documented env var names will Just Work, regardless of which path
populated it.

---

## Dependency Updates

| Dependency              | 0.9.1     | 0.9.2     | Reason                           |
|-------------------------|-----------|-----------|----------------------------------|
| `org.jsoup:jsoup`       | 1.18.3    | 1.22.2    | XSS / DOM parser CVE fixes       |

Spring Boot, Spring AI, Embabel Agent, Apache Camel, Spring Shell, and
Groovy are unchanged from 0.9.1 — all are line-current for the 3.5.x
Spring Boot ceiling that Embabel pins us to. See
`docs/dev/RELEASE-PLAN-1.0.0.md` §6 for the Spring Boot 4 migration
plan (post-1.0, conditional on Embabel publishing a 4.x-compatible
release).

---

## Bug Fixes

- **SEV-006**: `PipelineSyncCoordinator` async completions lost tenant
  context, causing downstream multi-tenant code to see either no
  tenant or whichever tenant the executor thread last carried. Fixed
  by capturing tenant at `register()` time, restoring it at
  `complete()` / `completeExceptionally()` time. New
  `PipelineSyncCoordinatorTenantPropagationSpec` covers the contract.

- **SEV-010**: `PipelineActuatorEndpoint` leaked cross-tenant execution
  data, including failure reasons. Fixed by injecting `TenantGuard`
  and filtering. Treats cross-tenant by-id lookups exactly like
  not-found so existence isn't leaked. New
  `PipelineActuatorEndpointTenantFilterSpec` covers the contract.

- **`PipelineHttpIntegrationSpec`** test fixture updated to set
  `server.address=127.0.0.1` so the new `mode=none` guard doesn't
  refuse to start the spec's `@SpringBootTest` slice.

---

## Security Fixes

- **SEV-006 and SEV-010** above are the two pipeline-side multi-tenant
  fixes from `security-report-2026-06-15.md`.

- `.security/dependency-check-suppressions.xml` gains three explicit
  false-positive entries:
  - `com.linecorp.bot:line-bot-jackson` — CPE confusion with the LINE
    messaging client app.
  - `org.apache.camel:camel-telegram` — CPE confusion with the
    Telegram Messenger consumer app.
  - `org.apache.camel:camel-whatsapp` — CPE confusion with the
    WhatsApp mobile client.

  Each entry includes a CDATA `<notes>` block documenting the
  rationale.

---

## Operator-side Tooling (`op-vault/`)

A new gitignored `op-vault/` directory at the repo root mirrors the
existing `maven-central-deploy/` convention. Holds operator-side
1Password upsert helpers that should not appear in fresh clones:

- `op-upsert-maven-central.sh` — single-item helper for the
  `Maven-Central` 1Password item. Reads
  `maven-central-deploy/.env` + `gpg-key-base64.txt`.
- `op-upsert-ci-secrets.sh` — general script that upserts every CI
  secret the workflows need. Supports `--only Item1,Item2` to scope
  to a subset, `--dry-run` to preview without writes. Idempotent via
  `op item get → op item create / op item edit` dispatch.

Both scripts include strict no-leakage rules: never persist a service-
account token, never display values from `.env`, NUL-delimited record
encoding so the multi-line GPG block survives intact.

---

## Documentation

- `docs/user/PRODUCTION-DEPLOYMENT.md` — substantial rewrite of §3.1
  (Secrets) covering the K8s injection contract, 1Password Connect
  Operator wiring, vendor-agnostic alternatives, and the standalone
  `op run` pattern.
- `docs/dev/RELEASE-PLAN-0.9.2.md` — the meta-plan that drove this
  release. Sections 1-8 cover the secrets baseline scope.
- `docs/dev/RELEASE-PLAN-1.0.0.md` — the umbrella plan from 0.9.3
  through 1.0.0 GA. 0.9.2 is the first concrete milestone toward it.

---

## What's Next (0.9.3 preview)

Per `docs/dev/RELEASE-PLAN-1.0.0.md` §2:

- Remove 8 deprecated constructors (`AgentRuntime` × 3,
  `GatewayService` × 5) + `SystemPromptBuilder.tools()`.
- `TemplateResolver.resolve()` removal date set.
- Add `jaiclaw.skills.allow-bundled: []` to
  `jaiclaw-examples/security-handshake-server` and
  `jaiclaw-examples/security-handshake`.
- Delete the broken `travel-planner` example's `live-api` provider
  (4 methods throwing `UnsupportedOperationException`).
- JaCoCo coverage closure ≥ 50% on the 10 core modules.
- `docs/dev/CI-SECRETS.md` documenting the `op-vault/` operator
  workflow.

---

## See Also

- [docs/dev/RELEASE-PLAN-0.9.2.md](../docs/dev/RELEASE-PLAN-0.9.2.md) —
  the planning document this release executed against.
- [docs/dev/RELEASE-PLAN-1.0.0.md](../docs/dev/RELEASE-PLAN-1.0.0.md) —
  the umbrella plan from 0.9.3 through 1.0.0 GA.
- [docs/user/PRODUCTION-DEPLOYMENT.md](../docs/user/PRODUCTION-DEPLOYMENT.md)
  §3.1 — the K8s + standalone secrets contract.
- [docs/ROAD-TO-1.0.md](../docs/ROAD-TO-1.0.md) — the API stability
  framing that 1.0.0 will commit to.
