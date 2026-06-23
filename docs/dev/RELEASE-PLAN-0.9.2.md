# JaiClaw 0.9.2 Release Plan

> **Status**: in flight (0.9.2-SNAPSHOT).
> **Theme**: secrets baseline.
> **Companion plan**: [RELEASE-PLAN-1.0.0.md](./RELEASE-PLAN-1.0.0.md) covers 0.9.3 + 1.0.0 and supersedes [../ROAD-TO-1.0.md](../ROAD-TO-1.0.md).

## TL;DR

0.9.2 ships a single coherent theme: **secrets handling end-to-end**. We introduce a generic `SecretsProvider` SPI in `jaiclaw-core` with reference implementations for the existing environment-variable pattern, a `.env` file, and 1Password. We migrate CI workflows, Helm deployments, and standalone runtime to read secrets through that SPI. On top of that we flip security hardening flags to default-on, close two outstanding tenant-context bugs, and pick up a handful of CVE-driven dependency bumps.

Why this is the theme:

- **1.0 is a stability commitment.** You can't make a stability commitment on top of secrets scattered across GitHub Actions repo secrets, hardcoded `application.yml` placeholders, and operator memory. 0.9.2 establishes the secrets posture that 1.0 will freeze.
- **Hardening flags default-off is a footgun.** Users who skip the `security-hardened` Spring profile silently run with unverified webhooks and unprotected SSRF. Flipping defaults before 1.0 is far easier than after.
- **CVE quick wins are already costed.** The 2026-06-15 security report identified them; they cost <1 day total.

What 0.9.2 explicitly is NOT:

- Spring Boot 4 migration (blocked by Embabel — see the 1.0.0 plan for the ceiling argument).
- New channels, providers, or OpenClaw parity work.
- The `AgentRuntime` refactor (post-1.0).
- Anything from the 0.9.3 list (deprecated removals, JaCoCo gate, example cleanup) — those are next-release.

---

## Section 1 — SecretsProvider SPI (architectural piece, highest priority)

This is the biggest piece of code work in 0.9.2 and the reason this release has a dedicated plan doc.

### 1.1 Design

A new package `io.jaiclaw.core.secrets` in `jaiclaw-core`. Per project convention, `jaiclaw-core` has zero Spring dependency — the SPI is pure Java records and sealed interfaces. Spring wiring lives in the starter.

**Public types** (all `@Stable` from the moment they're committed):

```java
package io.jaiclaw.core.secrets;

public interface SecretsProvider {
    /** Look up a single secret by logical key. */
    Optional<String> get(String key);

    /** Look up all secrets whose keys start with the given prefix. */
    Map<String, String> getAll(String prefix);

    /** Provider name for telemetry / chain ordering. */
    String name();

    /** Optional refresh hook — providers that can re-read backing state implement this. */
    default void refresh() { /* no-op */ }
}

public record SecretReference(String provider, String vault, String item, String field) {
    /** Parse the canonical 'provider://vault/item/field' notation. */
    public static SecretReference parse(String reference) { ... }
}

public sealed interface SecretResolution permits Resolved, Missing, ProviderError {
    record Resolved(String value, String providerName) implements SecretResolution {}
    record Missing(String key) implements SecretResolution {}
    record ProviderError(String key, String providerName, Throwable cause) implements SecretResolution {}
}
```

**Resolver**: `SecretsResolver` (also in `jaiclaw-core`) consults a chain of providers in order. First non-`Missing` resolution wins. Providers that throw produce `ProviderError`; the resolver decides whether to continue or fail based on `jaiclaw.secrets.chain-on-error: fail|continue` (default: `continue` to preserve today's behavior of "missing secrets aren't fatal at lookup time").

**Property-source bridge**: `JaiClawSecretsPropertySource` (in `jaiclaw-spring-boot-starter`) wraps the resolver and registers itself with Spring's `Environment` at high precedence — above `application.yml`, below explicit JVM `-D` properties. This means existing `${VAR}` placeholders in `application.yml` resolve through the chain transparently. Adopters don't have to change their config files; they change one property (`jaiclaw.secrets.provider`) and existing references re-resolve.

### 1.2 Three reference implementations

| Module | Class | Backing store |
|---|---|---|
| `jaiclaw-core` | `EnvironmentSecretsProvider` | `System.getenv()` |
| `jaiclaw-core` | `FileSecretsProvider` | `.env`-style file (KEY=value per line, `#` comments) |
| `extensions/jaiclaw-secrets-1password` (new module) | `OnePasswordSecretsProvider` | `op` CLI shell-out |

**Why `op` CLI shell-out instead of the 1Password Connect SDK?** Three reasons:
1. Works identically locally (developer with `op signin`), in CI (with `OP_SERVICE_ACCOUNT_TOKEN`), and on K8s nodes that have `op` baked into the image.
2. No JVM-side SDK lock-in; the SDK changes faster than `op`.
3. The 1Password Operator (for K8s) handles the SDK path itself — our runtime adapter doesn't need to duplicate that.

We can add a Connect-SDK implementation later as `OnePasswordConnectSecretsProvider` without breaking the SPI contract.

**`FileSecretsProvider` consolidates existing behavior**: the `bin/jaiclaw` launcher already sources a `.env` from `$JAICLAW_HOME/profiles/<profile>/.env`. After 0.9.2 that file becomes the canonical secrets source for the file provider, and the launcher's manual sourcing becomes redundant (kept for back-compat through 0.9.x, removed in 1.0).

### 1.3 Spring auto-configuration

`JaiClawSecretsAutoConfiguration` in `jaiclaw-spring-boot-starter`. Gated on `@ConditionalOnProperty(name="jaiclaw.secrets.provider")` so it's opt-in by default — adopters who do nothing get today's pure `${VAR}` behavior because the chain resolves to a single `EnvironmentSecretsProvider` only when no provider is configured.

Configuration shape:

```yaml
jaiclaw:
  secrets:
    provider: composite              # one of: env, file, onepassword, composite
    chain: [env, file, onepassword]  # only consulted when provider=composite
    chain-on-error: continue         # or fail
    file:
      path: ${JAICLAW_PROFILE_DIR}/.env
    onepassword:
      vault: JaiClaw-Prod
      service-account-token: ${OP_SERVICE_ACCOUNT_TOKEN}
      op-binary: /usr/local/bin/op   # optional, defaults to PATH lookup
      cache-ttl: 300s                # optional, defaults to no caching
```

### 1.4 Tenant binding

`TenantSecretsResolver` in `jaiclaw-core` is a per-tenant lookup that delegates to the configured provider with a tenant-prefixed key (e.g., `tenant-acme-corp/anthropic-api-key`). Called at `TenantContext` creation time; bound secrets become per-tenant `Environment` overrides via `TenantEnvironmentBinder`.

**Scope decision for 0.9.2**: static tenant list resolved at startup. Lazy/dynamic tenant onboarding (resolve on first request) is out of scope and goes in a future minor (probably 1.1 once 1.0 stability is committed).

**Conformance**: the `TenantSecretsResolver` injects `TenantGuard` per the project's multi-tenancy convention (CLAUDE.md § Multi-Tenancy Conformance), so single-mode deployments get the same code path with tenant prefixing disabled.

### 1.5 Test plan

- **Unit specs** per provider, one Spock spec each. `EnvironmentSecretsProviderSpec`, `FileSecretsProviderSpec` (uses `tempDir`), `OnePasswordSecretsProviderSpec` (mocks `ProcessBuilder` for `op` invocations).
- **Chain spec**: `SecretsResolverChainSpec` exercises lookup order, `chain-on-error` semantics, and metric emission.
- **Tenant binding spec**: `TenantSecretsResolverSpec` covers single-mode pass-through and multi-mode key prefixing.
- **Property-source integration spec**: `JaiClawSecretsPropertySourceSpec` boots a minimal Spring context and confirms `${VAR}` references resolve through the chain.
- **Coverage gate**: ≥70% line coverage on `io.jaiclaw.core.secrets.*` (above the 50% core-module floor, because this is new code we control).

### 1.6 Backwards compatibility

Zero breakage. The default (no `jaiclaw.secrets.provider` set) is equivalent to today's behavior. Adopters who set `provider: env` get explicit-but-identical behavior. Setting `provider: file` or `onepassword` is an upgrade decision.

---

## Section 2 — GitHub Actions secrets → 1Password (CI dimension)

### 2.1 Inventory

13 distinct GitHub Actions repo secrets across 3 workflows today:

| Workflow | Secret | Notes |
|---|---|---|
| `.github/workflows/publish-central.yml` | `GPG_PRIVATE_KEY` | Maven Central signing key |
| | `GPG_PASSPHRASE` | Maven Central signing key passphrase |
| | `MAVEN_CENTRAL_USERNAME` | Sonatype Central Portal token user |
| | `MAVEN_CENTRAL_PASSWORD` | Sonatype Central Portal token password |
| | `GITHUB_TOKEN` | Auto-issued; leave native (don't migrate) |
| `.github/workflows/e2e-tests.yml` | `ANTHROPIC_API_KEY` | E2E LLM key |
| | `ANTHROPIC_BASE_URL` | Optional MiniMax endpoint override |
| | `ANTHROPIC_MODEL` | Optional model override |
| | `OPENAI_API_KEY` | E2E LLM key |
| | `GOOGLE_API_KEY` | E2E LLM key |
| `.github/workflows/security-deps.yml` | `NVD_API_KEY` | OWASP dep-check NVD lookup token |
| | `OSS_USERNAME` | OSS Index user |
| | `OSS_API_TOKEN` | OSS Index token |

After migration, **only** `OP_SERVICE_ACCOUNT_TOKEN` (the bootstrap) + `GITHUB_TOKEN` (auto-issued) remain as GitHub-native secrets.

### 2.2 Implementation pattern

Add a "Load secrets" step at the top of each workflow:

```yaml
- name: Load secrets from 1Password
  uses: 1password/load-secrets-action@v2
  with:
    export-env: true
  env:
    OP_SERVICE_ACCOUNT_TOKEN: ${{ secrets.OP_SERVICE_ACCOUNT_TOKEN }}
    GPG_PRIVATE_KEY: op://JaiClaw-CI/maven-central/gpg-private-key
    GPG_PASSPHRASE: op://JaiClaw-CI/maven-central/gpg-passphrase
    MAVEN_CENTRAL_USERNAME: op://JaiClaw-CI/maven-central/username
    MAVEN_CENTRAL_PASSWORD: op://JaiClaw-CI/maven-central/password
```

Downstream steps read from `env`, not `${{ secrets.* }}`.

### 2.3 Delivery sequence

1. Create a 1Password vault `JaiClaw-CI` (or reuse a shared `taptech-ai-ci` vault if one already exists across the org's repos).
2. Create vault items: one logical item per credential bundle. E.g., one `maven-central` item with `gpg-private-key`, `gpg-passphrase`, `username`, `password` fields; one `oss-index` item with `username` and `token` fields.
3. Create a 1Password Service Account scoped read-only to that vault. Capture the service-account token.
4. Add `OP_SERVICE_ACCOUNT_TOKEN` as a GitHub repo secret. **This is the only secret that stays native.**
5. Edit `publish-central.yml`, `e2e-tests.yml`, `security-deps.yml` to use `1password/load-secrets-action@v2`.
6. Trigger each workflow manually. Verify env vars are populated (use `env | wc -l` for sanity; do NOT print values).
7. After the next clean run of each workflow, **delete the migrated secrets from GitHub Actions** so accidental re-introduction is caught by a workflow failure.
8. Document the pattern in `docs/dev/CI-SECRETS.md`: vault layout, who has write access, rotation procedure, what to do if the service-account token is compromised.

### 2.4 Acceptance

All three workflows pass on the next CI run. `grep -r 'secrets\.' .github/workflows/` shows only `OP_SERVICE_ACCOUNT_TOKEN` and `GITHUB_TOKEN`.

---

## Section 3 — Kubernetes deployment contract (documentation only)

> **Scope correction (re-planned mid-0.9.2).** The original plan called for
> Helm chart authoring against `apps/jaiclaw-gateway-app/src/main/helm/`,
> but that directory doesn't exist — the gateway-app uses JKube
> (`kubernetes-maven-plugin`), not a checked-in Helm chart. The actual
> production chart for the marketing site lives in the separate
> `jaiclaw.io` repo, and the cluster-side 1Password Connect Operator
> install lives in `taptech-sentinel`. Both are deployed already, and
> both are out of scope for this branch.
>
> What remains in JaiClaw's repo for 0.9.2 is the **contract**: the
> exhaustive list of env vars the gateway expects, with a recommended
> 1Password Connect wiring pattern that consuming charts can mirror.

### 3.1 Approach

JaiClaw doesn't speak to any secret store. The application reads
`${VAR}` placeholders in `application.yml` at startup; the environment
provides those values as env vars. How the env vars get there is the
cluster's concern. This means the deploying environment can use the
1Password Connect Operator (recommended), External Secrets + Vault,
plain literal Secrets, sealed-secrets, SOPS — anything that produces
K8s `Secret`s whose data keys match JaiClaw's env-var names.

The JaiClaw repo documents the **contract** (which env vars exist,
which Secret names hold them, how to wire them in a Deployment) and
recommends the 1Password Connect approach with concrete copy-pasteable
examples. The chart that consumes the contract lives elsewhere.

### 3.2 Changes

**One doc file touched:** `docs/user/PRODUCTION-DEPLOYMENT.md` §3.1.

  - The full env-var contract as a table.
  - A new §3.1.1 "Recommended: 1Password Connect Operator" with the
    per-domain Secret naming convention and an `OnePasswordItem`
    example.
  - A new §3.1.2 "Alternatives (vendor-agnostic)" covering Vault/ESO,
    cloud-native CSI, sealed-secrets, and the legacy literal-Secret
    fallback (preserved from the pre-0.9.2 doc).
  - Updates to downstream sections (§4 Deployment manifest, §5 Helm
    values stub, §11 runbook) so the per-domain Secret naming
    (`jaiclaw-gateway-auth`, `jaiclaw-llm-keys`, `jaiclaw-telegram`, …)
    is internally consistent.

**Out of scope for the JaiClaw repo (handled in other repos):**

  - 1Password Connect Operator install + cluster-side configuration —
    `taptech-sentinel`.
  - Helm chart `values-prod.yaml` that consumes the Secrets —
    `jaiclaw.io/deployment/helm/jaiclaw-io/`.
  - `OnePasswordItem` resource templates — same as the chart, lives
    with the chart that needs them.
  - `taptech-ai-agent-parent/helm/helm-deploy.sh` macOS shim — already
    works against `OnePasswordItem` CRDs because the operator is
    cluster-side; no shim change needed.

### 3.3 Verification

- Read the updated §3.1 from top to bottom and confirm a fresh
  deployer can pick a strategy (Connect / Vault / literal) and execute
  it without leaving the doc.
- Spot-check that downstream sections (§4, §5, §11) reference the new
  per-domain Secret names consistently — no stray `jaiclaw-secrets`
  monolith references that aren't called out as legacy.
- Confirm the cross-reference to `taptech-sentinel` matches the actual
  location of the Operator install procedure.

---

## Section 4 — Docker Compose / standalone runtime (deploy dimension, secondary)

### 4.1 Approach

Use `op run --` to wrap the JVM. This injects secrets as env vars at process start without writing them to disk. Works identically for `java -jar`, `start.sh local`, and `start.sh` (Docker).

The 0.9.2 work ships **two complementary entry points** because they cover different scenarios:

- **Bash fast-path command** (`jaiclaw setup-1password`) — pre-JVM, runs even on a fresh install before the wizard would work. Handles per-key prompts so each env var can map to a different vault item/field. Also useful for adopting 1Password after JaiClaw is already installed and running with plaintext `.env`.
- **Java wizard step** (`OnePasswordStep` inside `jaiclaw setup`) — folded into the standard onboarding flow so new users discover the option organically. Skipped silently when `op` isn't on PATH. Uses sensible defaults (item title = lowercase prefix of env-var name, field = hyphenated suffix) to keep the wizard short; operators wanting finer control are pointed at the bash command.

Both write the same `.env.op.tpl` format. Both are non-destructive — the regular `.env` is left intact, and the launcher's "only set if not already in env" semantics make op-resolved values win over `.env` literals when `--use-1password` is on.

### 4.2 What shipped

- **`bin/jaiclaw`**:
  - New `--use-1password` (and aliases `--use-onepassword`, `--op`) flag on the global flag parser. When set, re-execs the script under `op run --env-file=<tpl>` so injected env vars arrive before the per-profile `.env` source.
  - Template precedence: `$JAICLAW_OP_ENV_TPL` → `$JAICLAW_PROFILE_DIR/.env.op.tpl` → `$JAICLAW_HOME/.env.op.tpl`.
  - New `setup-1password` fast-path subcommand (`cmd_setup_1password`): detects `op`, reads existing `.env` to enumerate key names (values never displayed), prompts for selection + per-key vault/item/field, smoke-tests against `op run`, writes/merges `.env.op.tpl`. Strict no-leakage: never asks for a service-account token, never writes plaintext, leaves existing `.env` untouched.
  - The new subcommand lives inside `bin/jaiclaw` (not a sibling file) so the installer's single-file copy still picks it up.

- **`start.sh`**:
  - Same `--use-1password` flag with the same alias set. Re-execs through `op run --env-file=<tpl>` before sourcing the regular `.env`. Template precedence: `$JAICLAW_OP_ENV_TPL` → `<dir of $ENV_FILE>/.env.op.tpl` → `docker-compose/.env.op.tpl`.
  - `JAICLAW_OP_RUN_ACTIVE=1` sentinel env var prevents re-exec loops.
  - Flag stripped from the `PASSTHROUGH_ARGS` list before the re-exec so the second invocation proceeds normally.

- **Setup wizard (Java)**:
  - New `OnePasswordStep` in `OnboardWizardOrchestrator` inserted between MCP Servers and Config Location. Manual-mode only; silent skip when `op` is absent.
  - New `OnePasswordTemplateWriter` (Spring `@Component`) writes `.env.op.tpl` next to `.env` during `FinalizationStep`. 0600 perms on POSIX.
  - New `OnboardResult.OnePasswordConfig` record carries the vault name + selected env keys through the wizard. Contains no secrets.
  - 9 new Spock specs in `OnePasswordTemplateWriterSpec` cover the writer; existing `OnboardWizardOrchestratorSpec` updated for the new step (76/76 specs pass).

- **`docs/user/PRODUCTION-DEPLOYMENT.md` §3.1.3**:
  - New "Standalone / non-K8s deployments (1Password CLI)" section.
  - Documents the template format, location precedence, both setup paths (wizard vs bash), no-leakage guarantees, and the `start.sh`/`bin/jaiclaw` invocations.

### 4.3 Acceptance

- A fresh developer with `op` installed can run `bin/jaiclaw setup` and the wizard offers (only when `op` is on PATH) to scaffold the 1Password template alongside the regular config.
- An existing user who installs `op` *after* JaiClaw can run `bin/jaiclaw setup-1password` to migrate at any time.
- Either path produces a `.env.op.tpl` consumable by `jaiclaw --use-1password <cmd>` and `start.sh --use-1password`.
- The regular `.env` workflow continues to work unchanged for users who don't adopt 1Password.

### 4.4 Deferred to 0.9.3

- **`op-vault/` operator-side helpers**: `op-upsert-ci-secrets.sh` and `op-upsert-maven-central.sh` remain gitignored. The general `op-upsert-ci-secrets.sh` is operator-tool, not user-facing. Documenting them in `docs/dev/CI-SECRETS.md` is a 0.9.3 follow-up.

---

## Section 5 — Security hardening default-flip

Move these five flags from `application-security-hardened.yml` to default-on in the main `application.yml`. Create `application-dev.yml` to disable them for localhost development.

| Flag | New default | Why |
|---|---|---|
| `jaiclaw.channels.slack.verify-signature` | `true` | Unsigned Slack webhooks are a footgun |
| `jaiclaw.channels.telegram.verify-webhook` | `true` | Same for Telegram |
| `jaiclaw.tools.web.ssrf-protection` | `true` | SSRF on `WebFetchTool` is a real attack surface |
| `jaiclaw.tools.code.workspace-boundary` | `true` | Path-traversal protection in code tools |
| `jaiclaw.security.timing-safe-api-key` | `true` | Constant-time API key comparison |

Plus: `JaiClawSecurityAutoConfiguration.NoneSecurityConfiguration` gets a `@PostConstruct` check. If `jaiclaw.security.mode=none` AND the bind address is not localhost (127.0.0.1, ::1, or the loopback aliases), throw `IllegalStateException` at startup. The waiver flag is `jaiclaw.security.allow-none-on-public-bind=true`, with a documented warning.

**Migration note for adopters**: this is the most user-visible breaking change in 0.9.2. The 0.9.1 → 0.9.2 migration guide must call it out clearly — operators upgrading without reading release notes will get webhook signature failures on first deploy if they were relying on the off-default.

---

## Section 6 — SEV-006 + SEV-010 tenant-context fixes

Both from `security-report-2026-06-15.md`. Small, targeted.

**SEV-006**: `PipelineSyncCoordinator` async completions lose `ThreadLocal` tenant context. Wrap with `TenantContextPropagator.wrap(...)` matching the established `AgentRuntime` / `HookRunner` pattern. New Spock spec asserting tenant context flows through async chains.

**SEV-010**: `/actuator/pipelines/{id}/{executionId}` leaks failure reasons across tenants in multi-tenant mode. Add tenant filter in `PipelineActuatorEndpoint`. New unit test verifying cross-tenant reads return 404 (not the failure body).

---

## Section 7 — CVE quick wins

| Action | Detail |
|---|---|
| Suppress confirmed false positives | Populate `.security/dependency-check-suppressions.xml` for `line-bot-jackson` (CPE matches WhatsApp mobile client, not the Camel adapter), `camel-telegram`, `camel-whatsapp`. Each suppression has a `<notes>` element explaining the false-positive reasoning. |
| pdfbox bump | 3.0.7 → latest 3.0.x (run `mvn versions:display-dependency-updates` to confirm latest at release time). Used by `jaiclaw-documents`. |
| jsoup bump | 1.11.2 → 1.22.2. Generally backward-compatible; verify the HTML-summarizer example still works. |
| Spring Cloud BOM bump | 2025.0.2 → latest 2025.0.x patch. Picks up Spring Cloud Vault CVE fixes. |

Each is a single-line POM edit + a sanity test. No expected behavioral changes.

---

## Section 8 — Pre-cut gates

Before tagging 0.9.2:

```bash
export JAVA_HOME=/Users/tap/.sdkman/candidates/java/21.0.9-oracle
./mvnw verify                                       # full reactor tests
E2E_SCENARIOS=all ./e2e/run-e2e-tests.sh            # full e2e suite
# Then rerun the skills:
.claude/skills/dep-check                            # produces dependency-update-report.md
.claude/skills/security-scan                        # produces security-report.md
```

Diff both produced reports against the 2026-06-15 baselines. **Zero new CRITICAL findings.**

Also required before tagging:
- 0.9.1 → 0.9.2 migration guide written (`releases/release-0.9.2.md` with the security defaults flip prominently called out).
- All three GitHub workflows pass green using only `OP_SERVICE_ACCOUNT_TOKEN` + `GITHUB_TOKEN`.
- A test deploy to a staging K8s cluster confirms 1Password Connect Operator integration works end-to-end.
- Standalone smoke test: `bin/jaiclaw setup` + `op run -- bin/jaiclaw chat hello` from a clean profile.

---

## Section 9 — Out of scope for 0.9.2

All of the following land in 0.9.3, 1.0, or post-1.0 per [RELEASE-PLAN-1.0.0.md](./RELEASE-PLAN-1.0.0.md):

- Removal of deprecated `AgentRuntime`/`GatewayService` constructors → **0.9.3**.
- `TemplateResolver` removal date → **0.9.3**.
- Missing `allow-bundled: []` in `security-handshake*` examples → **0.9.3**.
- Broken `travel-planner` Amadeus provider cleanup → **0.9.3**.
- JaCoCo coverage closure to ≥50% on 10 core modules → **0.9.3**.
- `@Experimental → @Stable` review for ~7 surfaces → **1.0.0**.
- Consolidated 0.x → 1.0 migration guide → **1.0.0**.
- 30-day pilot deployment window → **between 0.9.3 and 1.0.0**.
- Spring Boot 4 / Spring Framework 7 migration → **2.0** (blocked by Embabel).
- OpenClaw parity, pipeline hot reload, AgentMind backlog → **post-1.0**.

---

## Files this release will touch (representative)

- `core/jaiclaw-core/src/main/java/io/jaiclaw/core/secrets/*` (new package).
- `jaiclaw-spring-boot-starter/src/main/java/io/jaiclaw/autoconfigure/JaiClawSecretsAutoConfiguration.java` (new).
- `extensions/jaiclaw-secrets-1password/` (new module).
- `apps/jaiclaw-gateway-app/src/main/resources/application.yml` + `application-dev.yml` (new) (defaults flip).
- `apps/jaiclaw-shell/src/main/resources/application.yml` + `application-dev.yml` (new) (defaults flip).
- `core/jaiclaw-security/src/main/java/io/jaiclaw/security/JaiClawSecurityAutoConfiguration.java` (fail-fast on `mode=none` public bind).
- `extensions/jaiclaw-pipeline/src/main/java/io/jaiclaw/pipeline/sync/PipelineSyncCoordinator.java` (SEV-006).
- `extensions/jaiclaw-pipeline/src/main/java/io/jaiclaw/pipeline/actuator/PipelineActuatorEndpoint.java` (SEV-010).
- `.github/workflows/publish-central.yml`, `e2e-tests.yml`, `security-deps.yml` (1Password load-secrets-action).
- `.security/dependency-check-suppressions.xml` (new).
- `docs/dev/CI-SECRETS.md` (new — 1Password vault layout).
- `docs/user/DEPLOYMENT.md` (1Password Connect Operator + `op run --` patterns).
- `bin/jaiclaw` (verify `--use-1password` plumbing).
- `start.sh` (1Password detection).
- `releases/release-0.9.2.md` (new — migration guide).
- `pom.xml` (pdfbox, jsoup, spring-cloud version bumps).

---

## Pointer

For everything not in 0.9.2, see [RELEASE-PLAN-1.0.0.md](./RELEASE-PLAN-1.0.0.md).
