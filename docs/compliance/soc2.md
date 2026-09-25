# SOC 2 (AICPA Trust Services Criteria)

## What it is

SOC 2 is an **attestation report** issued by a licensed CPA firm about a service
organization's controls, evaluated against the AICPA Trust Services Criteria
(TSC). Two report types exist:

- **Type I** — controls are suitably designed at a point in time.
- **Type II** — controls operated effectively over a period (typically 3–12
  months). This is what customers normally mean when they ask for "a SOC 2".

The TSC are organised into five categories. **Security (the Common Criteria,
CC1–CC9) is mandatory in every SOC 2 report**; Availability, Processing
Integrity, Confidentiality and Privacy are optional and scoped by the
organization.

### ⚠️ JaiClaw cannot be "SOC 2 compliant" — and neither can any library

This matters more here than for the other regulations in this directory, because
the question is asked in exactly the wrong form so often.

SOC 2 audits **an organization's operating controls** — access reviews, change
management, vendor risk, personnel onboarding and offboarding, incident
response, monitoring cadence. Those are properties of a company and its
processes. A Maven artifact has none of them.

So:

- **JaiClaw is not, and cannot be, SOC 2 compliant.** There is no such thing as
  a SOC 2 certified library.
- **Your organization can hold a SOC 2 report while running JaiClaw**, if your
  controls are designed and operating effectively.
- JaiClaw's only role is to make the *technical* half of that easier: to emit
  the evidence your auditor asks for, and not to undermine the controls you
  assert.

This document maps TSC criteria to what the framework actually contributes, and
is explicit about the (large) remainder that is yours. Everything cited is
backed by a file path.

For a JaiClaw-based deployment, SOC 2 applies when you are a service
organization whose customers ask for assurance over the system you operate —
which, for anything multi-tenant and commercial, is usually.

## What JaiClaw contributes

This deep-dive extracts the SOC-2-specific criterion mapping. The operator guide
for the compliance machinery itself is
[`docs/user/COMPLIANCE.md`](../user/COMPLIANCE.md).

Column legend, following [fedramp.md](fedramp.md)'s inheritance model:

- **JC** — JaiClaw framework contributes directly
- **AD** — Adopter's application layer + configuration
- **ORG** — Your organization's process, people, and policy

### Common Criteria mapping

| Criterion | JC | AD | ORG | JaiClaw evidence |
|---|---|---|---|---|
| **CC1** Control environment (governance, ethics, structure) | — | — | ✅ | Nothing. Board oversight, org structure, code of conduct, competence — entirely organizational |
| **CC2** Communication and information | — | ✅ | ✅ | Structured audit events are an input to internal reporting; policy communication is organizational |
| **CC3** Risk assessment | — | ✅ | ✅ | Nothing framework-level. `docs/compliance/` documents the framework's posture as an input to your assessment |
| **CC4** Monitoring of controls | ✅ | ✅ | ✅ | `AuditLogger` SPI emits structured events; `HashChainedAuditLogger.verifyChain(tenantId)` is a runnable control test. **Nothing schedules it** — adopter wires the cadence |
| **CC5** Control activities (policy into practice) | ✅ | ✅ | ✅ | Compliance profiles turn policy decisions into enforced configuration (`ComplianceEnvironmentPostProcessor`); the policy itself is organizational |
| **CC6.1** Logical access — identity and authorization | ✅ | ✅ | — | `jaiclaw.security.api-keys[]` binds a key to exactly one tenant and one role (`ApiKeyStore`, `ConfigApiKeyStore`); `mode=oidc` validates against an IdP's JWKS (`jaiclaw-security-oidc`); `JaiClawAuthentication` carries principal + tenant + profile |
| **CC6.2** Registration and authorization of new users | ✅ | ✅ | ✅ | Verified channel identity (`ChannelLinkService`) proves a channel user controls an external identity; provisioning and approval workflow is yours |
| **CC6.3** Role-based access, least privilege | ✅ | ✅ | — | `ToolProfile` gates which tools a run may call; `RoleToolProfileResolver` maps roles/scopes to profiles. **See "Known weak defaults" — the default is `FULL`** |
| **CC6.6** Boundary protection | ✅ | ✅ | — | `RequireHttpsStartupGuard` aborts startup on plaintext non-loopback binds; `RateLimitFilter`; `SsrfGuard` in `jaiclaw-tools` |
| **CC6.7** Transmission and movement of data | ✅ | ✅ | — | HTTPS guard above; `AuditingChatModelBeanPostProcessor` records every LLM call with recipient, so egress to model providers is evidenced |
| **CC6.8** Prevention of unauthorized software | — | ✅ | ✅ | Opt-in module design and `spring.autoconfigure.exclude` support; OWASP dependency-check is configured in the build |
| **CC7.1** Detection of configuration changes | ✅ | ✅ | — | Effective compliance flags are inspectable at `/actuator/env`; startup logs the resolved profile |
| **CC7.2** Monitoring for anomalies — **the audit-integrity criterion** | ✅ | ✅ | ✅ | `HashChainedAuditLogger` maintains a per-tenant SHA-256 chain over audit events and `verifyChain()` returns an `IntegrityReport`, emitting `audit.integrity_violation` on the first break. This is the control that answers *"how do you know these logs weren't altered?"* — **within the log**; see the truncation limitation below |
| **CC7.3 / CC7.4** Incident evaluation and response | ✅ | ✅ | ✅ | Framework emits structured audit events; `EmergencyStop` (`/actuator/jaiclaw-estop`) halts new work during an incident. The IR runbook is yours |
| **CC7.5** Recovery from incidents | — | ✅ | ✅ | Nothing framework-level |
| **CC8.1** Change management | — | ✅ | ✅ | Your CI/CD, review, and release process. JaiClaw is versioned semantically with release notes under `releases/` |
| **CC9.1 / CC9.2** Risk mitigation and vendor management | — | ✅ | ✅ | LLM providers are **your** subprocessors. `AuditingChatModel` records which provider received which request, which supports vendor review; the diligence is yours |

### Confidentiality (C1)

| Criterion | JC | AD | ORG | JaiClaw evidence |
|---|---|---|---|---|
| **C1.1** Identification and maintenance of confidential information | ✅ | ✅ | — | `TenantContext` metadata carries `data.categories`, `data.retention_days`, `data.residency_required`; the framework accepts the labels, the adopter decides what is confidential |
| **C1.2** Disposal of confidential information | ✅ | ✅ | — | `RetentionEnforcementService` purges on a per-tenant TTL and emits an audit event; `AggregateDataSubjectErasureSpi` cascades deletion across `TranscriptStore` and `AuditLogger` beans |
| **C1.x** Encryption at rest | ✅ | ✅ | — | `AesGcmFieldEncryptor` (AES-GCM-256, fresh 12-byte nonce per call, 128-bit tag) behind `EncryptedAuditLogger` and `EncryptedTranscriptStore` |
| **C1.x** Multi-tenant isolation | ✅ | ✅ | — | `TenantGuard` fails closed in MULTI mode; every persistence layer resolves tenant-scoped keys/paths. See [`docs/dev/multi-tenancy-architecture.md`](../dev/multi-tenancy-architecture.md) |

> **Limitation of the hash chain: tail truncation.** The chain detects any
> *modification* or *deletion* within the log — an edited field, a removed
> record — and names the offending event. It cannot detect **truncation of the
> most recent events**, because the remaining prefix is internally consistent
> and nothing anchors the expected chain head. That is inherent to hash
> chaining, not a defect in this implementation: detecting truncation requires
> an external anchor. Ship audit events off-host (SIEM, append-only store) or
> counter-sign the chain head on a schedule, and treat that as the compensating
> control. Do not present the chain to an auditor as complete coverage of CC7.2
> on its own. Covered by `AuditChainTamperSpec`, which asserts the limitation
> explicitly so it cannot be forgotten.

> **Not claimed: prompt redaction.** `RegexPromptRedactor` is registered as a
> bean when `prompt-redaction` is on, but **no framework code invokes it** — it
> is an SPI the adopter must call. It is also constructed non-strict, so it only
> acts when `TenantContext.isPhiProcessing()` is true, which a non-PHI SOC 2
> deployment never sets. Do not present it to an auditor as an operating control
> unless your application calls `redact()` itself.

### Compliance profile support

`jaiclaw.compliance.profile=soc2` assembles the hardened posture in one switch.
See [`docs/user/COMPLIANCE.md`](../user/COMPLIANCE.md#profile--flag-mapping) for
the full profile → flag matrix.

| Effective flag | `soc2` | Why |
|---|---|---|
| `audit-hash-chain` | on | CC7.2 — tamper-evident audit trail |
| `encrypt-at-rest` | on | C1 — requires an operator-supplied key; **startup aborts without one** |
| `require-https` | on | CC6.6 / CC6.7 |
| `retention-enforcement` | on | C1.2 |
| `audit-chat-client` | on | CC6.7 / CC9 — evidences egress to model providers |
| *(cross-subsystem)* `jaiclaw.security.default-tool-profile` | `MINIMAL` | CC6.3 — closes the fail-open default |
| *(cross-subsystem)* `jaiclaw.security.rate-limit.enabled` | `true` | CC6.6 |
| `baa-warnings` | off | HIPAA-specific |
| `fips-enforced` | off | Separate concern — set `jaiclaw.compliance.fips-enforced=true` to add it |
| `fedramp-warnings` / `cui-warnings` | off | Federal-specific |

Any individual flag overrides the profile in either direction, and an explicit
operator value for a cross-subsystem property is never overwritten.

**FISMA Moderate adopters:** `profile=soc2` plus
`jaiclaw.compliance.fips-enforced=true` covers the posture recommended in
[`docs/FEDERAL-COMPLIANCE-ASSESSMENT-2026-08-06.md`](../FEDERAL-COMPLIANCE-ASSESSMENT-2026-08-06.md).

## How to enable / disable

```yaml
jaiclaw:
  compliance:
    profile: soc2
    encryption:
      key: ${JAICLAW_ENCRYPTION_KEY}   # 32 bytes, base64-encoded
```

**Disabling** is the default. `profile: none` (or omitting it) loads zero
compliance beans and changes no behaviour — the module is safe to keep on the
classpath at zero cost.

> **The encryption key is not optional under `soc2`.** `encrypt-at-rest` with no
> resolvable key **aborts startup** rather than running unencrypted. Silently
> processing plaintext while an operator believes otherwise is worse than
> refusing to boot. Losing the key means losing the ciphertext — maintain a
> rotation runbook and a backup-key pattern before enabling in production.

## Known weak defaults

Stated plainly, because an auditor will find them and it is better that you do
first. These are the framework's defaults **without** the `soc2` profile.

- **`jaiclaw.security.default-tool-profile` defaults to `FULL`.** Any request
  arriving without an authenticated tool profile — which is every request in
  `api-key` mode, every channel-originated message, and the `permitAll`
  `/webhook/**` path — reaches the complete tool surface, including shell,
  filesystem and browser. `profile=soc2` sets `MINIMAL`. The framework default
  becomes `MINIMAL` in 1.3.0.
- **Actuator endpoints perform no authorization of their own.**
  `/actuator/jaiclaw-estop` is state-mutating and can pause every agent in the
  deployment. Front `/actuator/**` with the same authentication as the rest of
  your admin surface.
- **`/webhook/**` is `permitAll`** by design, since platforms cannot present a
  key. Sessions it creates are clamped to `WEBHOOK_SAFE`, but per-platform
  signature verification is opt-in via `SPRING_PROFILES_ACTIVE=security-hardened`
  — enable it.
- **`AdminController` and `GdprController` default their role to `""`**, which
  `hasAuthorityOrBlank` treats as "any authenticated principal". Set
  `jaiclaw.gateway.admin.roles.admin` and
  `jaiclaw.compliance.gdpr.roles.operator` explicitly.
- **Upgrade note.** Releases before 1.2.0 had a tenant-isolation defect in which
  an unverified JWT could establish tenant context. If a SOC 2 report period
  covers a deployment on 1.1.x or earlier and asserts tenant isolation as a
  control, raise it with your auditor rather than hoping it does not surface.
  See [`releases/release-1.2.0.md`](../../releases/release-1.2.0.md).

## What's adopter responsibility

- **The audit itself** — engaging a CPA firm, scoping the report, the observation
  period.
- **Control narratives** — describing each control in the language your auditor
  tests against.
- **Access reviews** — periodic recertification of who holds which API key or
  IdP role. The framework stores the binding; the review is a process.
- **Key management** — generation, storage, rotation, and backup of the
  encryption key. The framework consumes a key; it does not manage its
  lifecycle.
- **Scheduling `verifyChain()`** — the integrity check exists but nothing runs
  it. Wire it to a startup check and a periodic tick, and retain the results as
  evidence.
- **Log retention and SIEM** — routing audit events off-host, retaining them for
  the observation period, alerting on `audit.integrity_violation`.
- **Incident response** — the runbook, the on-call, the post-incident review.
- **Change management** — code review, approval, deployment records.
- **Vendor management** — your LLM providers are subprocessors; diligence,
  DPAs/BAAs where applicable, and periodic review are yours.
- **Personnel controls** — background checks, onboarding/offboarding, training.
- **Availability controls** — backup, DR, capacity, uptime monitoring. JaiClaw
  contributes nothing here; if your report scopes Availability, it is entirely
  infrastructure and process.

## What's out of scope

- **Any assertion that the framework is certified.** It is not, and the
  distinction is not pedantic — a SOC 2 report names an organization.
- **Organizational controls** (CC1, CC3, CC8, most of CC9) — no library can
  contribute.
- **Availability and Processing Integrity criteria** — deployment and
  application concerns respectively.
- **Privacy criteria (P1–P8)** — overlaps GDPR; see [gdpr.md](gdpr.md) for the
  data-subject-rights machinery that supports them.
- **Evidence collection tooling** — JaiClaw emits evidence; it does not assemble
  an audit package.

## How to verify

**Activate the profile and inspect the effective flags:**

```bash
JAICLAW_COMPLIANCE_PROFILE=soc2 \
JAICLAW_ENCRYPTION_KEY="$(openssl rand -base64 32)" \
    ./mvnw spring-boot:run -pl :jaiclaw-gateway-app

# Boot log should include:
#   Compliance profile 'SOC2' active — effective flags: httpsGuard=true,
#     retention=true, chatAudit=true, auditHashChain=true, encryptAtRest=true ...
```

```bash
curl http://localhost:8888/actuator/env/jaiclaw.compliance.effective.audit-hash-chain
curl http://localhost:8888/actuator/env/jaiclaw.security.default-tool-profile
# expect: true, and MINIMAL
```

**Confirm the default is untouched** — the control that matters most, since every
non-`soc2` deployment depends on it:

```bash
./mvnw spring-boot:run -pl :jaiclaw-gateway-app | grep -c "Compliance profile"
# expect: 0
```

**Test the audit-integrity control (CC7.2)** — this is the auditor's question, so
it is worth running by hand at least once:

```bash
# 1. generate audit events through normal operation
# 2. verify the chain
#    HashChainedAuditLogger.verifyChain(tenantId) -> IntegrityReport, clean
# 3. tamper with one record in the audit JSONL
# 4. re-verify -> the report names the break, and an
#    audit.integrity_violation event is emitted
```

**Confirm encryption fails closed:**

```bash
JAICLAW_COMPLIANCE_PROFILE=soc2 ./mvnw spring-boot:run -pl :jaiclaw-gateway-app
# no key supplied -> startup MUST abort naming
# jaiclaw.compliance.encryption.key
```

## Related code files

- `extensions/jaiclaw-compliance/src/main/java/io/jaiclaw/compliance/ComplianceProfile.java`
- `extensions/jaiclaw-compliance/src/main/java/io/jaiclaw/compliance/ComplianceProperties.java`
- `extensions/jaiclaw-compliance/src/main/java/io/jaiclaw/compliance/ComplianceEnvironmentPostProcessor.java`
- `extensions/jaiclaw-compliance/src/main/java/io/jaiclaw/compliance/JaiClawComplianceAutoConfiguration.java`
- `extensions/jaiclaw-compliance/src/main/java/io/jaiclaw/compliance/audit/HashChainedAuditLogger.java`
- `extensions/jaiclaw-compliance/src/main/java/io/jaiclaw/compliance/audit/AuditingChatModelBeanPostProcessor.java`
- `extensions/jaiclaw-compliance/src/main/java/io/jaiclaw/compliance/encryption/AesGcmFieldEncryptor.java`
- `extensions/jaiclaw-compliance/src/main/java/io/jaiclaw/compliance/encryption/EncryptionKeyResolver.java`
- `extensions/jaiclaw-compliance/src/main/java/io/jaiclaw/compliance/encryption/EncryptionBeanPostProcessor.java`
- `extensions/jaiclaw-compliance/src/main/java/io/jaiclaw/compliance/encryption/JaiClawEncryptionAutoConfiguration.java`
- `extensions/jaiclaw-compliance/src/main/java/io/jaiclaw/compliance/audit/HashChainedAuditLoggerBeanPostProcessor.java`
- `extensions/jaiclaw-compliance/src/main/java/io/jaiclaw/compliance/encryption/EncryptedAuditLogger.java`
- `extensions/jaiclaw-compliance/src/main/java/io/jaiclaw/compliance/encryption/EncryptedTranscriptStore.java`
- `extensions/jaiclaw-audit/src/main/java/io/jaiclaw/audit/AuditLogger.java`
- `extensions/jaiclaw-audit/src/main/java/io/jaiclaw/audit/RetentionEnforcementService.java`
- `core/jaiclaw-core/src/main/java/io/jaiclaw/core/tenant/TenantGuard.java`
- `core/jaiclaw-core/src/main/java/io/jaiclaw/core/tool/ToolProfile.java`
- `core/jaiclaw-security/src/main/java/io/jaiclaw/security/ApiKeyStore.java`
- `core/jaiclaw-security/src/main/java/io/jaiclaw/security/RequireHttpsStartupGuard.java`
- `core/jaiclaw-security/src/main/java/io/jaiclaw/security/RateLimitFilter.java`
- `core/jaiclaw-security-oidc/src/main/java/io/jaiclaw/security/oidc/JaiClawOidcAutoConfiguration.java`
- `extensions/jaiclaw-identity/src/main/java/io/jaiclaw/identity/link/ChannelLinkService.java`
- `core/jaiclaw-gateway/src/main/java/io/jaiclaw/gateway/admin/EstopActuatorEndpoint.java`

## References

- [AICPA Trust Services Criteria (2017, rev. 2022)](https://www.aicpa-cima.com/resources/download/2017-trust-services-criteria-with-revised-points-of-focus-2022)
- [AICPA SOC 2 overview](https://www.aicpa-cima.com/topic/audit-assurance/audit-and-assurance-greater-than-soc-2)
- [`docs/user/COMPLIANCE.md`](../user/COMPLIANCE.md) — operator guide to the compliance machinery
- [`docs/user/API-KEY-AUTHENTICATION.md`](../user/API-KEY-AUTHENTICATION.md), [`OIDC-AUTHENTICATION.md`](../user/OIDC-AUTHENTICATION.md), [`VERIFIED-IDENTITY.md`](../user/VERIFIED-IDENTITY.md)
- [`SECURITY.md`](../../SECURITY.md) — vulnerability policy and known weak defaults
- [fedramp.md](fedramp.md), [nist-800-53.md](nist-800-53.md) — overlapping control families
