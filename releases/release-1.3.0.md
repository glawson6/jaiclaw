# JaiClaw 1.3.0 Release Notes

**Release Date:** unreleased (1.3.0-SNAPSHOT)
**Distribution:** Maven Central + TapTech Nexus (`tooling.taptech.net`)

> 1.3.0 is the **fail-closed release.** It closes four controls that existed but
> could not be reached, three that silently did nothing, and two provider
> defects found by wire-level inspection of live traffic. The pattern throughout:
> a security control that cannot be performed now *refuses*, where it previously
> proceeded.

## Highlights — compliance

- **`soc2` compliance profile** — one property
  (`jaiclaw.compliance.profile=soc2`) assembles the controls a SOC 2 auditor
  asks to see: tamper-evident audit chain, encryption at rest, a `MINIMAL`
  default tool profile, TLS-required-at-startup, rate limiting. The framework
  default is unchanged — `profile=none` still loads zero compliance beans.
  [`docs/compliance/soc2.md`](../docs/compliance/soc2.md) maps CC1–CC9 + C1 and
  is explicit that **no library can be SOC 2 compliant** — the attestation is
  about an organisation.
- **Encryption at rest is auto-wired.** `EncryptionKeyResolver` reads
  `jaiclaw.compliance.encryption.key` (hex or base64) through the core
  `SecretsProvider` SPI and builds the first framework-supplied
  `FieldEncryptor`; `EncryptionBeanPostProcessor` then decorates `AuditLogger`
  and `TranscriptStore`. Previously those decorators existed with **no path to
  being used** short of an adopter writing their own `@Bean`. Startup **aborts**
  when `encrypt-at-rest` is on and no key resolves — running unencrypted while
  an operator believes otherwise is the worst available outcome.
- 🔴 **Audit-chain verification now runs.** `verifyChain()` had **zero
  callers**: the hash chain was maintained on every write and never read, so
  tampering would surface only if an operator ran the check by hand. A
  tamper-evident log that nothing reads is not evidence of anything.
  `AuditChainVerifier` runs it per tenant on a configurable interval
  (`jaiclaw.compliance.audit.verify.interval`, daily by default).

## Highlights — human-in-the-loop approval

- 🔴 **The tool approval gate failed open.** `ExplicitToolLoop` gated on
  `approvalRequired && approvalHandler != null`, so a deployment with no
  `ToolApprovalHandler` executed approval-required tools with **no gate at
  all**. Headless deployments register no handler by default, which made
  `PROMPT_ALWAYS` *looser* than `DENY` and contradicted `ApprovalFloor`'s own
  documented contract. An approval that cannot be obtained is now a denial. The
  same fail-open existed on the exception path and is fixed identically.
- **Approval over chat** — the first `ToolApprovalHandler` that actually uses
  the SPI's asynchronous contract. Both shipped console handlers block on
  `readLine()` and are inert in any deployment without a TTY, i.e. every server.
  Text replies rather than buttons, because the Telegram adapter has no
  `reply_markup` support and drops `callback_query` updates. Opt in via
  `jaiclaw.approval.chat.enabled`.
- **Per-tool approval timeouts** with a per-tool action on silence
  (`deny` default, `approve` available). Timeouts are per tool because blast
  radius and human latency differ — `shell_exec` answered in two minutes or not
  at all is reasonable; "reply to this review" may deserve half an hour.
- **`auto-approve`** — a top-level switch for unattended operation, so an
  operator need not neutralise every tool individually. A `DENY` floor still
  wins: the loop evaluates `DENY` before computing whether approval is required,
  so auto-approve structurally cannot execute a denied tool. It logs a warning
  at startup, because silently disabling a security control is how fail-open
  settings survive review.

## Highlights — webhook verification

- 🔴 **Discord verified nothing.** No Ed25519 signature check and no config
  flag — while still answering Discord's PING challenge, so the endpoint passed
  the developer-portal setup check *and* accepted forged interactions from
  anyone who knew the URL. An interaction carries a command the agent executes.
  Now verifies `X-Signature-Ed25519` over `timestamp || rawBody` before parsing
  and before answering PING. No new dependency: the JDK has shipped Ed25519
  since Java 15.
- 🔴 **Slack and Telegram failed open on a blank secret.** Verification was
  skipped entirely when the secret was unset, *even with the verify flag on* —
  the likely state for anyone who activated `security-hardened` and stopped
  there, since the profile sets the flag but cannot set the secret. Both now
  reject.
- **LINE used a non-constant-time signature compare** (`String.equals`), which
  short-circuits on the first differing character. Now `MessageDigest.isEqual`.
- **WhatsApp's `verifyToken` and the SMS/WhatsApp `webhookPath`s are inert** —
  no controller serves them. Documented as such rather than left reading like
  protection. SMS additionally **cannot** verify Twilio signatures: the
  signature covers the request URL plus sorted params and `processWebhook`
  receives neither headers nor URL. Recorded against the method; closing it
  properly is a signature change.

## Highlights — actuator and tool profiles

- **The emergency-stop endpoint is off by default** and its write operation is
  role-guarded (`jaiclaw.gateway.admin.estop.{endpoint-enabled,role}`). It
  pauses every agent in the deployment and a bare POST engages it. A blank role
  **denies** here — deliberately unlike the admin controllers, whose
  blank-is-allow-all default `SECURITY.md` lists as a known weakness. Enabling
  it under `security.mode=none` without a role **refuses to start**, because
  that mode's filter chain is `permitAll`. `bin/jaiclaw pause` remains the
  mechanism; the endpoint was only ever the convenience.
- **`MINIMAL` and `WEBHOOK_SAFE` are now usable profiles.** `WEBHOOK_SAFE`
  granted **nothing** — no tool in the repo carried the tag, so the
  `GatewayController` webhook clamp was a no-op despite the enum documenting
  what it should allow. Read-only tools are retagged: `file_read`, `web_search`,
  `web_fetch`, ASCII rendering, and the read-only task and wiki tools.
- **1Password lookups are cached.** `SecretsPropertySource` is `addFirst`-ed
  into the Spring `Environment`, so it answers *every* property lookup the
  application makes — and each **miss** cost an `op read` subprocess plus a
  network round-trip. Misses are the common case, since most properties are not
  secrets. `CachingSecretsProvider` caches misses as well as hits;
  `SecretsProvider.refresh()` finally does something, as the rotation hook the
  SPI always declared.
- **A missing `op` binary is now reported at startup.** It previously produced
  silently unresolved `${...}` placeholders: the `IOException` became a
  `ProviderError`, which the default `chain-on-error=continue` turned into
  `null`. **No JaiClaw container image ships `op`.**

## Breaking changes

### Approval requirements now deny instead of executing

If you set `requireApproval: true` or any `PROMPT_ALWAYS` floor **and** have no
`ToolApprovalHandler` bean, those tool calls are now **denied** rather than
executed. This only affects configurations that were already broken — the
control never worked there. `requireApproval` defaults to `false`, and both
bundled examples that enable it register a `@Component ConsoleApprovalHandler`.

Either register a handler (`jaiclaw.approval.chat.enabled=true` is the shipped
one), or set `approval.auto-approve: true` for unattended operation.

### Webhook verification rejects when its secret is missing

`jaiclaw.channels.slack.verify-signature=true` without `signing-secret`, or
`jaiclaw.channels.telegram.verify-webhook=true` without `webhook-secret-token`,
now returns 401 instead of accepting the request. If you activated
`security-hardened` without setting the secrets, inbound webhooks will start
failing — which is the point; they were never being verified.

### `ToolProfileHolder.getOrDefault()` removed

Deprecated `forRemoval = true` in 1.2.0. It returned `FULL` when unset, which
was every request in api-key mode. Use `getOrDefault(ToolProfile)` with your
deployment's configured default. No main-code callers remained.

### The estop actuator endpoint is off by default

Set `jaiclaw.gateway.admin.estop.endpoint-enabled=true` **and**
`jaiclaw.gateway.admin.estop.role` to restore it. `bin/jaiclaw pause|resume` is
unaffected and needs no HTTP surface.

## Deferred to 1.4.0

**`jaiclaw.security.default-tool-profile` still defaults to `FULL`.** The 1.2.0
notes promised this would flip to `MINIMAL` in 1.3.0. It now flips in **1.4.0**,
and the reason is worth stating plainly: flipping it here would have removed
**7 of the 8 default built-in tools**, because `ToolDefinition`'s convenience
constructors default the profile tag set to `Set.of(FULL)` — most tools were
FULL-only by accident rather than by decision. 1.3.0 retags the read-only tools
so `MINIMAL` grants a usable working set first.

**Set the property explicitly now.** `MINIMAL` is viable today, and the startup
warning fires only when the value is unset, so a deliberate choice silences it.

## Known limitations

- **The audit hash chain still cannot detect tail truncation.** `verifyChain`
  replays the records present and has no persisted chain head, so a deleted
  tail leaves a chain that verifies clean. Scheduling the check does not change
  that. Ship audit records to append-only storage and alert on volume gaps.
- **SMS cannot verify Twilio signatures** — see above. Do not expose the SMS
  webhook without a proxy that verifies for you.
- **Approval requests are not persisted.** One in flight is lost on restart,
  which is honest for a human-latency gate: after a restart the agent run is
  gone too, so a late approval would have nothing to authorise.
- **Prompt redaction is still not claimed as a control.** `PromptRedactor` has
  no framework call sites; it is an SPI the adopter must invoke.

## Dependency updates

None. 1.3.0 adds no third-party dependencies — Ed25519 verification uses the
JDK's `SunEC` provider, and `CachingSecretsProvider` is a bounded map in
`jaiclaw-core`, which remains dependency-free down to having no logger.

## Documentation corrections

Three claims in shipped docs contradicted the code and are corrected in place:

- `releases/release-0.9.2.md` said six hardening flags flipped default-on. They
  flipped in the gateway app's YAML only; library defaults are still `false`, so
  every starter consumer and the shell app inherit off.
- `docs/user/COMPLIANCE-HOWTO.md` handed adopters sample code calling
  `tenantRegistry.listAllTenants()`. No such API has ever existed.
- `RetentionEnforcementService`'s Javadoc and `docs/user/OPERATIONS.md` both
  claimed it runs on a scheduled tick. `enforceForTenant` has zero call sites;
  the claim is now marked as unimplemented rather than left standing.
