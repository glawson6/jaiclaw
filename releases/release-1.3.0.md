# JaiClaw 1.3.0 Release Notes

**Release Date:** 2026-10-07
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
  - **Every request carries a code the approver must quote back** —
    `yes K7Q4`, not `yes`. This binds the answer to the question: a reply meant
    for a harmless `wiki_read` cannot be applied to a `shell_exec` that asked a
    moment later, which is exactly the confirmation-hijack a prompt-injected
    model would attempt. Several requests may be open per conversation; none
    supersedes another, so inducing a gated call cannot cancel someone else's
    pending approval. Codes use an alphabet with no vowels and no look-alike
    glyphs, so one can never read as a verdict word.
  - **`user-id` restricts who may answer.** On Telegram and Discord the
    configured `peer-id` is the *conversation*, which may be a group; without
    `user-id` any member could approve. Adapters now publish the sender as
    `platformData["sender_id"]`, the filter checks it, and startup warns when an
    approver has no `user-id`.
  - **The key the reply is matched on carries no tenant.** The reply filter
    runs *before* tenant resolution, so a tenant-scoped key made the two halves
    disagree and no approval could ever be redeemed in multi-tenant mode. The
    requesting tenant is recorded on the pending request for audit instead.
  - **The vocabulary is narrow on purpose**: `yes approve approved confirm
    confirmed` and `no deny denied reject rejected cancel abort stop refuse
    veto`. `ok`, `sure`, `go` and `y` are everyday acknowledgements and no
    longer count as consent.

  These four points are the 2026-10-01 security review's findings 1.1–1.5,
  each pinned by a spec in `ChatApprovalE2ESpec`.
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
  and before answering PING, and **rejects a signed timestamp more than five
  minutes from now** — the signature proves origin, the window proves
  freshness, and without the window a captured request replays forever. No new
  dependency: the JDK has shipped Ed25519 since Java 15.
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
  SPI always declared — and refreshes the delegate *before* clearing the cache,
  so a lookup that races the rotation cannot re-pin the old value for a TTL.
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

### `DiscordConfig`'s five-argument constructor is deprecated

It has nowhere to take the interaction public key, so it can only produce a
config with signature verification **off**. Use the canonical constructor or
`DiscordConfig.builder().publicKey(..).verifySignature(true)`. It still
compiles; the adapter warns at startup when verification is off in webhook mode.

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

Also carried to 1.4.0, unchanged from the 1.2.0 notes: the Part E Logto adapter
(`IdentityProviderClient` / `IdentityProviderWebhookVerifier` implementations),
per-tool OAuth scopes, and the A2A / checkpoint / session-primitive scope in
`docs/issues/IMPLEMENTATION-PLAN-1.3.0.md`, which that file now says.

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
- **The approval prompt shows every tool argument verbatim** to the approver
  conversation, with no redaction hook. In multi-tenant mode that conversation
  is deployment-wide, so one tenant's arguments reach a tenant-agnostic
  operator chat. Treat the approver chat as operator-only.
- **An approver without `user-id` is any participant of that conversation.**
  Startup warns; the fix is configuration.
- **`CachingSecretsProvider` caches a tenant-scoped miss** while the shared
  fallback resolves, so a `tenant:KEY` added after the first lookup is unseen
  for up to `miss-ttl` and the tenant transiently reads the shared value. Call
  `refresh()` after adding a tenant override, or keep `miss-ttl` short.
- **Prompt redaction is still not claimed as a control.** `PromptRedactor` has
  no framework call sites; it is an SPI the adopter must invoke.

## Dependency updates

| Dependency | 1.2.0 | 1.3.0 | Why |
|---|---|---|---|
| Spring Boot | 4.1.0 | **4.1.1** | Moves managed Netty 4.2.15 → 4.2.17; `netty-bom` now lists `codec-http3`, so the whole Netty stack versions together |
| Spring AI | 2.0.0 | **2.0.1** | Patch; BOM aligned |
| Embabel Agent | 1.5.0 | **1.5.3** | Patch line. Taking it surfaced a pre-existing JaiClaw bug, fixed below |
| Spring Shell | 4.0.2 | **4.0.3** | Patch |
| Spring Cloud | 2025.1.2 | **2025.1.3** | Patch |
| Playwright | 1.49.0 | **1.63.0** | **CVE-2025-59288** (fixed at 1.61.0); twelve minors of drift that a stale report had called "latest" |
| okio | 3.6.0 (transitive) | **3.18.2** (pinned) | **CVE-2020-29582** via `kotlin-stdlib-common:1.9.10`, which okio ≥ 3.16 no longer depends on |
| Netty | 4.1.135 (pinned) | *unpinned* | The pin dragged 15 artifacts backwards off Boot's 4.2 line while 4 escaped it — a mixed, binary-incompatible classpath. **CVE-2026-42582** is now fixed by composition rather than suppressed |
| jsoup | 1.22.2 | **1.23.2** | Minor |
| PDFBox | 3.0.7 | **3.0.8** | Patch |
| dependency-check-maven | 12.1.0 | **13.0.0** | Scanner current line |

No new third-party runtime dependencies. Ed25519 verification uses the JDK's
`SunEC` provider, and `CachingSecretsProvider` is a bounded map in
`jaiclaw-core`, which remains dependency-free down to having no logger.

Not taken, deliberately: Groovy 6 / Spock 2.5, Drools 10, fabric8 8,
Testcontainers 2, Jedis 8, OkHttp 5, Camel 4.22, jjwt 0.13 — each a major or
behaviour-changing line that belongs in its own cycle, not a release day.

## Bug fixes

- 🔴 **`runtime: EMBABEL` pipeline stages failed on first run from a Spring Boot
  fat jar** with `ClassNotFoundException` for the application's own `@Action`
  types. Embabel's `JvmType` resolves types through the *thread context class
  loader*; `EmbabelAgentOrchestrationPort` ran on `ForkJoinPool.commonPool`,
  whose threads carry the JDK application loader, which cannot see
  `BOOT-INF/classes`. Present in 1.2.0 (reproduced against the released jar);
  invisible to unit tests, which run on a flat classpath. `EmbabelInvocations`
  now pins the context class loader for the duration of every invocation.
  Found by e2e scenario 6f/6g, which had never been run against a fat jar.
- **`jaiclaw-example-pipeline-e2e` fed `ANTHROPIC_MODEL` into
  `embabel.models.default-llm`** — the wire model name into the registry
  lookup. With a MiniMax-routed key the app refused to start. Pinned to a
  registered id.

## Security fixes

- **CVE-2025-59288** (Playwright) — upgraded.
- **CVE-2020-29582** (kotlin-stdlib-common via okio) — the artifact is gone from
  the classpath, not suppressed.
- **CVE-2026-42582** (netty-codec-http3) — fixed by removing the Netty pin; the
  suppression that argued it away is deleted.
- **CVE-2026-6860** (Vert.x) — suppressed **with reachability evidence and an
  expiry**: Vert.x arrives only as the fabric8 Kubernetes client's runtime HTTP
  transport, JaiClaw runs no Vert.x server, and no source under
  `jaiclaw-tools-k8s` references it. No fix exists upstream yet.
- **The CVE gate is now in the POM.** `failBuildOnCVSS=7.0` and the suppression
  file were passed only as CLI flags in one CI workflow; every other invocation
  used the plugin's default of 11, i.e. failed on nothing. A new `security`
  Maven profile binds the scan to `verify`.
- Discord Ed25519 verification; Slack/Telegram blank-secret rejection; LINE
  constant-time compare; approval gate fail-closed; estop endpoint off by
  default — all above.

## Documentation corrections

Claims in shipped docs that contradicted the code are corrected in place:

- `releases/release-0.9.2.md` said six hardening flags flipped default-on. They
  flipped in the gateway app's YAML only; library defaults are still `false`, so
  every starter consumer and the shell app inherit off.
- `docs/user/COMPLIANCE-HOWTO.md` handed adopters sample code calling
  `tenantRegistry.listAllTenants()`. No such API has ever existed.
- `RetentionEnforcementService`'s Javadoc and `docs/user/OPERATIONS.md` both
  claimed it runs on a scheduled tick. `enforceForTenant` has zero call sites;
  the claim is now marked as unimplemented rather than left standing.
- `CLAUDE.md` labelled `jaiclaw-security-oidc`, the RFC 9728 metadata endpoint
  and verified channel identity as 1.3.0 / 1.4.0 work. All three shipped in
  **1.2.0**; `docs/user/VERIFIED-IDENTITY.md` said the same and is corrected.
- `JaiClawSecurityProperties` Javadoc still promised the `MINIMAL` flip for
  1.3.0. It now says 1.4.0, matching these notes and `SECURITY.md`.
