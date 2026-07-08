# JaiClaw Compliance Guide (GDPR + HIPAA)

**Framework version:** 0.9.4+
**Audience:** operators and architects evaluating JaiClaw for deployments subject to GDPR (EU personal data) or HIPAA (US Protected Health Information).

**Position:** JaiClaw is a **compliance-capable** framework. It provides the multi-tenant isolation, audit SPI, secrets management, retention enforcement, BAA-eligible-provider metadata, and LLM-call audit trail adopters need to build defensible GDPR + HIPAA deployments. **It is not a compliance-certified product.** Both frameworks are properties of a deployment, not of a framework.

## What JaiClaw provides today

Sections below map each capability to the article/safeguard it helps with. Everything listed here has code + tests in the repo.

| Capability | GDPR | HIPAA |
|---|---|---|
| Multi-tenant isolation (`TenantContext` + `TenantGuard`) | Art. 5, 32 | §164.312(a), (e) |
| Compliance metadata on tenant context (lawful basis, retention days, restriction flags, data residency, PHI processing, consent token) | Art. 6, 7, 18, 44 | §164.502 |
| Audit SPI with structured GDPR/HIPAA fields (lawful basis, data categories, recipients, retention days, consent token) | Art. 30 | §164.312(b) |
| LLM-call audit trail — every `model.inference.request` records recipient, data categories, and caller's compliance metadata | Art. 30, 44 | §164.312(b), (e) |
| BAA-eligible LLM provider enforcement — warn when a PHI-processing tenant uses a non-BAA provider | — | §164.502 |
| Retention enforcement — scheduled purge of transcripts + audit entries per per-tenant TTL | Art. 5(e) | §164.316(b)(2) |
| HTTPS startup guard — refuse to start on a public bind without TLS | Art. 32 | §164.312(e) |
| `security-hardened` profile — HMAC webhook verification, SSRF guards, workspace path boundaries, timing-safe API key comparison | Art. 32 | §164.312(a), (d) |
| Pluggable `SecretsProvider` SPI (env / file / 1Password / custom) | Art. 32 | §164.312(a)(2)(iv) — key material only, not payload encryption |

Tier 2 SPIs shipped alongside 0.9.4 (opt-in — see § "Tier 2 SPIs" below):

- `DataSubjectErasureSpi` (GDPR Art. 17 cascade delete)
- `DataSubjectExportService` + `/api/gdpr/export/{id}` (Art. 15, 20)
- `PromptRedactor` SPI (PHI/PII masking before LLM dispatch)
- `FieldEncryptor` SPI + `EncryptedTranscriptStore` / `EncryptedAuditLogger` decorators (AES-GCM at rest)
- `ConsentManager` SPI (Art. 6, 7)
- `HashChainedAuditLogger` decorator — cryptographic chain-of-hashes (§164.312(b), (c))
- `PrivacyNoticeService` (Art. 13/14)

Tier 3 governance-layer SPIs (self-contained; ship when adopter demand asks):

- `RopaGenerator` — Art. 30 machine-readable RoPA reconstructed from the audit trail (`AuditBasedRopaGenerator` reference impl)
- `ObjectionService` — Art. 21 objection recording (mirrors `ConsentManager`; `InMemoryObjectionService` reference impl)
- `AnomalyDetector` SPI — mass-read / bulk-export / off-hours-access detection over the audit trail (`MassReadDetector` reference impl; adopters implement additional detectors)
- `ProcessorMetadataExporter` — DPA-ready sub-processor + security-measures export (`DefaultProcessorMetadataExporter` reference impl over `ModelsProperties` + BAA-eligible-provider catalog)

## Enabling compliance

Pull `jaiclaw-compliance` onto the classpath (usually via `jaiclaw-starter-compliance`), then set one property in `application.yml`:

```yaml
jaiclaw:
  compliance:
    profile: hipaa    # one of: none | gdpr | hipaa | both  (default: none)
```

The `profile` knob turns on a coherent bundle of individual flags. Individual flags can override any single element of the bundle in either direction:

```yaml
jaiclaw:
  compliance:
    profile: hipaa
    require-https: false        # override HIPAA default for a bench deployment
```

### Profile → flag mapping

| Flag | `none` | `gdpr` | `hipaa` | `both` |
|---|---|---|---|---|
| `require-https` | off | on | on | on |
| `retention-enforcement` | off | on | on | on |
| `audit-chat-client` | off | on | on | on |
| `baa-warnings` | off | off | on | on |
| `prompt-redaction` (T2) | off | off | on | on |

Effective flags are surfaced at `jaiclaw.compliance.effective.*`, so an operator can inspect what the runtime is doing:

```
jaiclaw.compliance.effective.profile=hipaa
jaiclaw.compliance.effective.require-https=true
jaiclaw.compliance.effective.retention-enforcement=true
jaiclaw.compliance.effective.audit-chat-client=true
jaiclaw.compliance.effective.baa-warnings=true
jaiclaw.compliance.effective.prompt-redaction=true
```

When `profile: none` (the default), zero compliance code loads — no scheduled retention task, no LLM-call decorator, no BAA warning check. The module can sit on the classpath at zero cost.

### Deployment note

A tenant subject to full GDPR or HIPAA typically will not share persistence, audit logs, or LLM configuration with other tenants. **Compliance mode is a per-deployment decision, not per-request.** A fully-compliant tenant gets its own JVM instance. This keeps the code paths simple and the compliance story auditable.

## Per-tenant compliance metadata

Even with a compliance profile enabled, some behavior is per-tenant. Set on `TenantContext.getMetadata()`:

| Metadata key | Type | Effect |
|---|---|---|
| `gdpr.lawful_basis` | string | Written to every `AuditEvent.lawfulBasis` for this tenant. Required for GDPR Art. 30 RoPA reconstruction. Common values: `consent`, `contract`, `legal_obligation`, `vital_interests`, `public_task`, `legitimate_interests`. |
| `data.retention_days` | integer | Retention TTL for transcripts + audit — enforced by `RetentionEnforcementService`. HIPAA §164.316(b)(2) requires audit ≥ 6 years (2190 days). |
| `data.restriction_flags` | set of strings | GDPR Art. 18. Downstream code short-circuits the disallowed operation. Known flags: `no_llm_calls`, `no_memory_writes`. |
| `data.residency_required` | string | Required residency (e.g. `eu-west`, `us-east`). LLM provider resolution and storage backend selection SHOULD refuse to route data outside the declared region. |
| `hipaa.phi_processing` | boolean | True when the tenant handles PHI. Drives BAA-eligible-provider enforcement. |
| `gdpr.consent_token` | string | Reference to a `ConsentManager` record (T2). Written to every `AuditEvent.consentToken` for this tenant. |

Typical enterprise deployment flow: an operator's onboarding tool creates a `TenantContext` with the metadata pre-populated from the customer's compliance intake form. The framework does the rest.

## BAA-eligible LLM providers

For HIPAA — the framework classifies known providers as BAA-eligible or not by default. A tenant marked `hipaa.phi_processing=true` on a non-BAA provider triggers a WARN at LLM-call time.

### Default classification

**BAA-eligible** (cloud routes that offer a Business Associate Agreement):

- `bedrock` — Anthropic + others via AWS Bedrock (BAA with AWS)
- `azure-openai` — OpenAI models via Azure OpenAI (with a contract rider)
- `vertex-ai` — Google models via GCP Vertex AI (BAA with GCP)
- `ollama` — self-hosted; deployer controls the data plane

**Not BAA-eligible** (direct public API routes):

- `anthropic` — direct `api.anthropic.com`
- `openai` — direct `api.openai.com`
- `gemini`, `google-genai` — direct public Gemini API
- `minimax`, `deepseek`, `mistral`, `oci-genai` — direct public APIs

### Overriding the default

If you have a signed BAA with a provider that's not BAA-eligible by default (or need to force non-BAA on a normally-eligible route), set the property:

```yaml
jaiclaw:
  models:
    providers:
      anthropic:
        baa-eligible: true          # explicit override — you have a BAA with Anthropic
      bedrock:
        baa-eligible: false         # override — awaiting AWS contract
```

The classification catalog is `io.jaiclaw.config.BaaEligibleProviders`. Rows in the default table reflect the state at 0.9.4 cut; if a future BAA offering changes any classification, update the property (no code change required).

## HTTPS enforcement

Enabling any profile above `none` sets `jaiclaw.security.require-https=true`. On startup, `RequireHttpsStartupGuard` refuses to start when:

1. `server.ssl.enabled` is false or unset, AND
2. `server.address` is unset or is a non-loopback address (i.e. the server would accept traffic on the network)

Loopback binds (`127.0.0.1`, `::1`, `localhost`) still start fine, so dev workflows are unaffected.

An operator who genuinely needs plaintext on a non-loopback bind (behind a private TLS-terminating proxy, for example) can override:

```yaml
jaiclaw:
  compliance:
    profile: hipaa
    require-https: false     # explicit opt-out — you MUST have a TLS-terminating layer in front
```

An explicit `jaiclaw.security.require-https=false` at the security-property namespace ALSO wins over the compliance profile default. This preserves the operator's ability to opt out at the target subsystem.

## Retention enforcement

When `profile != none`, `RetentionEnforcementService` is materialized and runs on a scheduled tick. On each tick, for every registered `TenantContext` with a `data.retention_days` metadata value:

1. Compute cutoff = now - TTL
2. Purge every transcript with `startTime` < cutoff (via `TranscriptStore.purgeOlderThan`)
3. Purge every audit event with `timestamp` < cutoff (via `AuditLogger.purgeOlderThan`)
4. Emit a `data.retention_purge` audit event summarizing what was removed

Purges never cross tenant boundaries — a purge for tenant `acme` never touches tenant `beta`'s data.

The default TranscriptStore / AuditLogger implementations do a linear-scan purge that's fine for modest data volumes. Store implementations with a real index (Lucene, SQLite FTS5, Postgres partition drop) should override `purgeOlderThan` for O(1) purges.

## LLM-call audit trail

When `audit-chat-client` is on, every `ChatModel` bean in the Spring context is wrapped by `AuditingChatModel`. Every LLM `call()` and `stream()` emits an `AuditEvent`:

```json
{
  "action": "model.inference.request",
  "resource": "openai/chat-model",
  "recipients": ["openai"],
  "dataCategories": ["user_utterance", "system_prompt"],
  "lawfulBasis": "contract",
  "retentionDays": 2190,
  "consentToken": "cnst_abc123",
  "tenantId": "acme",
  "outcome": "SUCCESS",
  "details": { "durationMs": 480, "messageCount": 2, "errorMessage": "" }
}
```

Deployers can reconstruct a GDPR Art. 30 RoPA from the audit trail without instrumenting individual call sites. Every cross-border transfer (Art. 44) has a matching event with the recipient identifier.

**Recipient resolution.** The recipient string is currently `<providerName>` (e.g. `openai`, `anthropic`, `bedrock`) derived from the `ChatModel` class name. Region-tagged recipients (`bedrock-us-east-1`) are on the T2 roadmap; adopters who need region tagging today can wrap `AuditingChatModel` again with their own region-aware layer.

## Tier 2 SPIs

The compliance module ships SPIs an adopter can plug in to satisfy specific GDPR / HIPAA requirements. Reference implementations are wired automatically when the compliance profile is active; adopters replace them by registering their own `@Bean` of the same SPI type.

| SPI | Reference impl | Wired when | Solves |
|---|---|---|---|
| `DataSubjectErasureSpi` | `AggregateDataSubjectErasureSpi` | profile != none | GDPR Art. 17 cascade delete across transcript + audit stores |
| `DataSubjectExportService` | `AggregateDataSubjectExportService` | profile != none | GDPR Art. 15 / 20 export (JSON, JSON-LD, CSV bundle) |
| `PromptRedactor` | `RegexPromptRedactor` (SSN, MRN, phone, email, DOB, credit card) | `jaiclaw.compliance.prompt-redaction=true` (default on for HIPAA + both profiles) | HIPAA §164.502 PHI masking before LLM dispatch |
| `FieldEncryptor` | `AesGcmFieldEncryptor` (AES-GCM 256, random per-call nonce) | manually — adopter wires the key material | Payload encryption for transcripts + audit + memory decorators |
| `ConsentManager` | `InMemoryConsentManager` (production adopters should replace with a durable store) | profile != none | GDPR Art. 6 / 7 consent + Art. 21 withdrawal recording |
| `AuditLogger` chain-of-hashes | `HashChainedAuditLogger` decorator | manually — adopter wraps the underlying `AuditLogger` | §164.312(b), (c) tamper-evident audit trail with `verifyChain()` |
| `PrivacyNoticeService` | `DefaultPrivacyNoticeService` | profile != none | GDPR Art. 13 / 14 first-message notice + acceptance |

**REST surface (T2-2):** `GdprController` exposes the export + erasure SPIs at `/api/gdpr/export/{dataSubjectId}` and `/api/gdpr/subject/{dataSubjectId}` when Spring Web is on the classpath. The controller resolves tenant scope from `TenantContextHolder`; requests without tenant context receive `403`. Adopters MUST front the controller with a rate-limiter + a role-guarded auth layer (`gdpr.operator`).

**Encryption key management (T2-4):** the framework does NOT resolve encryption keys — the adopter wires the 32-byte key from a `SecretsProvider` (env, file, 1Password, Vault). Losing the key means losing the encrypted data. Maintain a key-rotation runbook + backup-encryption-key pattern. Ciphertext format is `base64(nonce || tag_and_ciphertext)`; decrypting a ciphertext with the wrong key raises `EncryptionException` without leaking which of {wrong key, corrupted blob, tampered auth tag} caused the failure.

**Prompt redaction (T2-3):** `RegexPromptRedactor` is best-effort — regex-based patterns will miss free-form PHI expressions. The plan's risk callout #3 says explicitly: use redaction as a risk reduction, not a HIPAA safeguard on its own. A covered entity should still contract-restrict the LLM provider via a BAA.

**Chain-of-hashes audit (T2-6):** `HashChainedAuditLogger` wraps any `AuditLogger` and stamps every event with `prevHash` + `chainHash` under `details`. `verifyChain(tenantId)` replays the tenant's chain and reports the first break (or emits an `audit.integrity_violation` event and returns a report). Adopters SHOULD run `verifyChain` at startup + on a scheduled tick.

**Erasure boundary (T2-1):** the aggregate erasure cascades over registered `TranscriptStore` + `AuditLogger` beans. Erasure on the primary — replicas + backups remain the deployer's responsibility (per plan risk callout #4). Audit erasure is a soft-delete + tombstone: the event shell survives so GDPR Art. 30 + HIPAA §164.312(b) still see that erasure happened.

## Tier 3 governance SPIs

Tier 3 items are self-contained additions the plan tags as "ship when there's adopter demand or a specific sales requirement". All are wired automatically when the compliance profile is active; adopters override with a `@Bean` of the same SPI type.

| SPI | Reference impl | Solves |
|---|---|---|
| `RopaGenerator` | `AuditBasedRopaGenerator` | GDPR Art. 30 machine-readable RoPA per tenant from the audit stream. Default window is 30 days; caller supplies `from`/`to` for arbitrary reporting periods. Groups events by `action`; aggregates distinct lawful bases, data categories, recipients, and per-basis retention. |
| `ObjectionService` | `InMemoryObjectionService` | GDPR Art. 21 objection recording. Adopters use `hasObjection(...)` to short-circuit downstream processing (skip profiling for a subject who opted out). Every record + rescind emits an `objection.recorded` / `objection.rescinded` audit event. |
| `AnomalyDetector` | `MassReadDetector` (5-subject default threshold; adopters implement additional detectors: bulk export, off-hours access, cross-tenant probe) | Emits `security.event` audit entries when suspicious patterns appear. Alerting itself is external (SIEM); the framework's job is to emit structured signals. |
| `ProcessorMetadataExporter` | `DefaultProcessorMetadataExporter` | Exports processor + sub-processor + retention + security-measures metadata for auto-filling a DPA template. Sub-processor list is built from `ModelsProperties` cross-referenced with the BAA-eligible-provider catalog; security-measures list reflects the currently-enabled compliance modules. |

None of these obviate the operator's legal work — the auto-generated RoPA / DPA fields still need counsel review, and anomaly detections still need triage. The framework provides the raw material.

## What's still the operator's responsibility

Framework can't do these; you must:

- **BAA legal negotiation** with the cloud LLM provider. The framework flags the routing risk; the contract is on you.
- **TLS termination, cipher suite selection, HSTS**. Configure at the reverse proxy or via `server.ssl.*`.
- **SIEM integration, log aggregation, alerting**. Ingest the audit stream into Splunk / Datadog / ELK.
- **IAM lifecycle**. Rotate service accounts, API keys, and JWT signing secrets on your own cadence.
- **MFA at the application boundary**. JaiClaw is a Bearer-token service; put MFA upstream at the identity provider.
- **DPA generation, RoPA maintenance**. The audit trail is the raw material; the legal artifacts are yours.
- **Breach notification orchestration**. The framework emits structured events for suspicious activity; you monitor and route the notifications.

## Related documentation

- `docs/dev/COMPLIANCE-IMPLEMENTATION-PLAN.md` — the roadmap that produced 0.9.4's Tier 1 delivery + the SPI additions coming in 1.0.
- `docs/user/PRODUCTION-DEPLOYMENT.md` — production deployment topology.
- `docs/user/OPERATIONS.md` — operator runbook.
- `SECURITY.md` — security posture and vulnerability reporting.
