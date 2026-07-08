# Compliance Implementation Plan — GDPR + HIPAA

**Status:** Draft, 2026-07-07.
**Target milestones:** Tier 1 → 0.9.4. Tier 2 → 1.0. Tier 3 → post-1.0.
**Scope:** Framework-level SPIs and reference implementations. Deployer-side items (TLS termination, SIEM integration, BAA legal negotiation, IAM lifecycle) are called out explicitly as out-of-scope so we don't overpromise.

## Context

JaiClaw is being evaluated for enterprise deployments that touch personal data (GDPR-covered) and Protected Health Information (HIPAA-covered). The current framework has a strong compliance *substrate* — multi-tenant isolation, audit SPI, `SecretsProvider`, security-hardened profile, JWT + timing-safe crypto — but does not yet ship the compliance-specific data structures and enforcement machinery adopters need to build defensible deployments.

This plan enumerates the SPIs, records, config properties, and reference implementations we'll add. Every item names the file it touches, the acceptance criterion, and whether it's shippable in 0.9.4 without breaking existing consumers.

### Positioning after Tier 1 ships

> "JaiClaw is a **compliance-capable framework**: it provides the multi-tenant isolation, audit SPI, per-tenant compliance metadata, and BAA-eligible provider enforcement adopters need to build GDPR and HIPAA deployments. It does not ship a turnkey compliance controller — those flows differ per organization — but it ships the SPIs and reference implementations so you don't reinvent them."

This is a defensible statement to a controller / covered entity's compliance review. We deliberately do NOT plan to claim "GDPR-compliant" or "HIPAA-compliant" as certifications — those are properties of a deployment, not a framework.

---

## Tier 1 — 0.9.4 (non-breaking, ~1–2 weeks)

Every Tier 1 item is optional metadata / opt-in behavior. Existing consumers see no change.

### T1-1. Extend `TenantContext` with optional compliance metadata

**File:** `core/jaiclaw-core/src/main/java/io/jaiclaw/core/tenant/TenantContext.java`

Add four optional accessors backed by the existing `metadata()` map (so this is a source-compatible change to the interface — no impl needs to override):

```java
public interface TenantContext {
    // existing accessors …

    /** Optional GDPR Art. 6 lawful basis. Null when the tenant hasn't declared one. */
    default @Nullable String getLawfulBasis() { return metadataString("gdpr.lawful_basis"); }

    /** Optional retention policy in days. Null when unlimited (framework default). */
    default @Nullable Integer getRetentionDays() { return metadataInt("data.retention_days"); }

    /** Optional set of processing restriction flags (e.g. "no_llm_calls", "no_memory_writes"). */
    default Set<String> getRestrictionFlags() { return metadataStringSet("data.restriction_flags"); }

    /** Optional data-residency requirement (e.g. "eu-west", "us-east"). Null when unconstrained. */
    default @Nullable String getDataResidencyRequired() { return metadataString("data.residency_required"); }

    /** True when this tenant handles PHI (HIPAA §164.502). Drives BAA-eligible-provider enforcement. */
    default boolean isPhiProcessing() { return metadataBool("hipaa.phi_processing", false); }
}
```

**Rationale:** Every downstream compliance decision (retention purge, BAA enforcement, data residency check, redaction depth) needs to know these five things. Putting them on `TenantContext` means they propagate through the same thread-local + `TenantContextPropagator` machinery as tenant id, so we don't invent parallel state.

**Acceptance:** Spock spec on the existing `TenantContext` reference impls: set the metadata via the builder, assert the accessors return the values. Round-trip through `TenantContextPropagator.wrap()` in a virtual thread.

**Effort:** 2 days.

### T1-2. Extend `AuditEvent` with GDPR + HIPAA fields

**File:** `extensions/jaiclaw-audit/src/main/java/io/jaiclaw/audit/AuditEvent.java`

Add five optional fields to the record (compact constructor defaults them to null / empty, so existing callers are unaffected):

```java
public record AuditEvent(
        String id,
        Instant timestamp,
        String tenantId,
        String actor,
        String action,
        String resource,
        Outcome outcome,
        Map<String, Object> details,
        // NEW — all optional / null-tolerant
        @Nullable String lawfulBasis,        // GDPR Art. 30 — basis at time of processing
        Set<String> dataCategories,          // GDPR Art. 30 — "user_utterance", "session_context", "attachment"
        Set<String> recipients,              // GDPR Art. 30 — "anthropic-bedrock-us-east-1"
        @Nullable Integer retentionDays,     // GDPR Art. 5(e) — snapshot of policy in force
        @Nullable String consentToken        // GDPR Art. 6(1)(a) — reference to consent record
) { }
```

Add convenience factories to `AuditEvent.Builder` so structured emit sites stay concise.

**Rationale:** Reconstructing a GDPR Art. 30 Record of Processing (RoPA) from the audit trail is the highest-leverage compliance win the audit SPI enables. Today's audit is structurally sufficient for "who did what when" but lacks "under what basis with what data going where." These five fields close that gap without changing the SPI shape.

**Acceptance:** Existing `AuditEventSpec` continues to pass unmodified. New spec covers: null values serialize as omitted (not `"null"`), non-null values round-trip via `FileAuditLogger`, RoPA reconstruction from a corpus of audit JSONL is demonstrable.

**Effort:** 3 days.

### T1-3. `AuditingChatClientDecorator` — audit every LLM call with recipient

**File:** `core/jaiclaw-agent/src/main/java/io/jaiclaw/agent/audit/AuditingChatClientDecorator.java` (new)

Wrap Spring AI's `ChatClient` bean via `BeanPostProcessor`. Before every `.call()` or `.stream()`, emit an `AuditEvent` with:

- `action = "model.inference.request"`
- `resource = "<providerName>/<modelName>"` (e.g. `"anthropic/claude-sonnet-4-5"`)
- `recipients = { "<providerName>-<region>" }` (e.g. `"anthropic-bedrock-us-east-1"`)
- `dataCategories = { "user_utterance", "system_prompt" }` (derived from the message list; attachments add `"attachment"`)
- `lawfulBasis`, `retentionDays`, `consentToken` from the current `TenantContext`

The decorator must be **transparent** — same return type, same streaming semantics, same error propagation. It only observes.

**Rationale:** Every cross-border data transfer (GDPR Art. 44) and every third-party disclosure (HIPAA §164.502) needs an audit trail entry. Without a decorator, adopters would have to instrument every call site by hand.

**Config:** Opt-in via `jaiclaw.audit.chat-client.enabled=true`. Default false in 0.9.4 (safe rollout), flip to `true` in 1.0 after telemetry.

**Acceptance:** Spock spec: mock `ChatModel`, inject via `ChatClient.Builder`, verify one `AuditEvent` per call with the expected recipient string. Streaming variant tested via a mock `Flux` publisher. Negative test: with the flag off, zero audit events.

**Effort:** 3 days.

### T1-4. `@BaaEligible` metadata + startup warning for PHI tenants

**Files:**
- `core/jaiclaw-config/src/main/java/io/jaiclaw/config/ModelsProperties.java` (extend `ModelProviderConfig` with `boolean baaEligible` — default `false`)
- `core/jaiclaw-agent/src/main/java/io/jaiclaw/agent/tenant/DefaultTenantChatModelFactory.java` (startup + runtime check)
- Static list of known-BAA-eligible provider names in a new `BaaEligibleProviders.java` constant

Behavior:

1. Every `ModelProviderConfig` picks up `baaEligible` from YAML. Defaults per known provider name:
   - `bedrock`, `azure-openai`, `vertex-ai`, `ollama` (self-hosted) → `true`
   - `anthropic` (direct), `openai` (direct), `gemini` (direct), `minimax`, `deepseek`, `mistral`, `oci-genai` → `false`
   - Any custom provider → `false` (must be explicitly set)
2. When a `TenantContext.isPhiProcessing()` tenant resolves a provider that is NOT BAA-eligible:
   - Startup: log `WARN` with the tenant id + provider name + a link to the BAA docs section.
   - Runtime: same warning on first `ChatClient` call in that session.
   - **Non-blocking** in 0.9.4 (opt-in strict mode via `jaiclaw.compliance.strict-baa=true` in 1.0 → throws instead of warns).

**Rationale:** The single most consequential HIPAA failure mode is silently routing PHI to a non-BAA LLM. A default-on warning eliminates that class of accident without breaking dev workflows.

**Acceptance:** Two Spock specs: (1) PHI tenant + non-BAA provider → warning logged, call proceeds; (2) PHI tenant + BAA provider → no warning. Strict-mode spec deferred to 1.0.

**Effort:** 2 days.

### T1-5. Fix SEV-004 series — the 6 in-memory-store + 3 async tenant-context gaps

**Files** (per the existing `docs/CODEBASE-ANALYSIS-2026-06-10.md §4.2`):

Store leaks — apply `TenantGuard.resolveStorageKey()` to key prefixing:
- `InMemoryCalendarProvider`
- `InMemoryCallStore`
- `JsonFileTaskStore`
- `BrowserService`
- `VectorDocStoreSearch`
- `FullTextDocStoreSearch`

Async context loss — wrap with `TenantContextPropagator.wrap()`:
- `SubscriptionExpiryScheduler`
- `JsonlCallStore` async writes
- `CallManager` scheduled tasks

**Rationale:** These are documented cross-tenant data leaks. Under HIPAA multi-tenant they'd be a breach-notification event; under GDPR they'd be an Art. 33 breach. All fixes are ~5 minutes each per the prior audit — but they need to actually ship.

**Acceptance:** Each fix gets a Spock spec that sets two tenant contexts in sequence, exercises the store or scheduled task, and asserts zero cross-tenant reads. Reuse the pattern from `SessionManagerMultiTenantSpec`.

**Effort:** 2 days.

### T1-6. `RetentionPolicy` + `RetentionEnforcementService`

**Files:**
- `core/jaiclaw-core/src/main/java/io/jaiclaw/core/data/RetentionPolicy.java` (new record)
- `extensions/jaiclaw-audit/src/main/java/io/jaiclaw/audit/RetentionEnforcementService.java` (new)

```java
public record RetentionPolicy(
        Duration transcriptTtl,      // null = unlimited
        Duration memoryTtl,          // null = unlimited
        Duration auditTtl,           // null = unlimited (but HIPAA requires ≥ 6y)
        Action onExpiry              // DELETE | ANONYMIZE | ARCHIVE
) { }
```

Read policy from `TenantContext.getRetentionDays()` when set, else from `jaiclaw.compliance.default-retention-*` properties, else unlimited. A `@Scheduled` task runs daily, walks `TranscriptStore` and `AuditLogger`, purges entries older than TTL, and emits an `AuditEvent` with `action = "data.retention_purge"` for each session/event purged.

**Rationale:** GDPR Art. 5(e) requires storage limitation; HIPAA §164.316(b)(2) requires ≥ 6-year audit retention. Both are policy-driven — the framework enforces whatever policy the adopter declares.

**Acceptance:** Spock spec with a fixed clock: seed transcripts at t=0 with TTL=7 days, advance clock to t=8 days, run enforcement, verify transcripts deleted + audit events emitted.

**Effort:** 3 days.

### T1-7. `jaiclaw.security.require-https=true` startup guard

**File:** `core/jaiclaw-security/src/main/java/io/jaiclaw/security/JaiClawSecurityAutoConfiguration.java`

New Spring `@Bean` — an `ApplicationListener<ApplicationReadyEvent>` — that:
1. Reads `server.address` and `server.ssl.enabled`.
2. If SSL is off AND `server.address` is not loopback (`127.0.0.1` / `::1` / `localhost`) AND `jaiclaw.security.require-https=true`:
   - Throw `IllegalStateException` at startup with a clear message.
3. Default `require-https` to `false` in 0.9.4 for backward compatibility.

**Rationale:** GDPR Art. 32 and HIPAA §164.312(e) both require transmission encryption. The framework can't force TLS termination (that's the reverse proxy's job) but it can refuse to start on a public bind without it.

**Acceptance:** Spock spec loading a minimal Spring context with three combinations: (1) SSL enabled → OK, (2) HTTP + localhost bind → OK (dev workflow preserved), (3) HTTP + `0.0.0.0` bind + require-https=true → startup exception.

**Effort:** 1 day.

### T1-8. Documentation package

Files:
- `docs/user/COMPLIANCE.md` (new) — the adopter-facing compliance overview: what JaiClaw provides, what it doesn't, adopter responsibilities, BAA-eligible provider table, GDPR + HIPAA operator checklists.
- `docs/user/PRODUCTION-DEPLOYMENT.md` (extend) — a "Compliance-aware deployment" section pointing at COMPLIANCE.md.
- `SECURITY.md` (extend) — reference the compliance-aware modes from `security-hardened` profile.

The BAA-eligible provider table is the single most valuable doc artifact — it tells a healthcare architect in one glance which of the 11 supported providers they can legally use with PHI (with a BAA, via which cloud).

**Effort:** 2 days.

### Tier 1 acceptance gate

For 0.9.4 to be considered compliance-ready-substrate:

- [ ] All Tier 1 Spock specs green in `./mvnw test`.
- [ ] `jaiclaw:analyze` extended with a `compliance` sub-check that verifies every bundled example's `TenantContext` metadata (advisory, not blocking).
- [ ] `docs/user/COMPLIANCE.md` reviewed by counsel (external step, tracked separately).
- [ ] `release-0.9.4.md` calls out the Tier 1 delivery under a "Compliance substrate" section.

---

## Tier 2 — 1.0 (SPI additions, ~4–8 weeks)

Tier 2 lands after the 30-day 0.9.3 pilot window described in `docs/dev/RELEASE-PLAN-1.0.0.md`. Each item introduces a new SPI that adopters implement to plug into the compliance runtime.

### T2-1. `DataSubjectErasureSpi` — cascading erasure

**Files:**
- `core/jaiclaw-core/src/main/java/io/jaiclaw/core/gdpr/DataSubjectErasureSpi.java` (new SPI)
- Reference impls under `extensions/jaiclaw-audit/`, `core/jaiclaw-memory/`, `core/jaiclaw-agent/session/`

```java
public interface DataSubjectErasureSpi {
    /** Delete every stored artifact for a data subject. Emits an audit event per deletion. */
    ErasureResult eraseForDataSubject(String tenantId, String dataSubjectId, ErasureReason reason);

    record ErasureResult(int transcriptsDeleted, int memoryEntriesDeleted,
                          int auditEventsDeleted, int sessionsDeleted, Duration elapsed) {}

    enum ErasureReason { ART_17_REQUEST, RETENTION_EXPIRY, CONSENT_WITHDRAWAL, OPERATOR_INITIATED }
}
```

The SPI is implemented once at `AggregateDataSubjectErasureSpi` which discovers all registered `TranscriptStore`, `MemoryProvider`, `AuditLogger`, `SessionManager` beans and cascades the delete. Reason is captured in the audit trail — the audit deletion is a soft-delete + tombstone (delete the content, keep the shell) so the audit-of-erasure isn't itself erased. This is required by GDPR Art. 30 and HIPAA §164.312(b).

**Acceptance:** Integration test seeds a data subject's traces across all four stores, calls `eraseForDataSubject`, verifies all four stores return zero for that subject, verifies the erasure audit event exists with the reason + counts.

**Effort:** 1–2 weeks.

### T2-2. `DataSubjectExportService` — DSAR REST endpoint

**Files:**
- `core/jaiclaw-core/src/main/java/io/jaiclaw/core/gdpr/DataSubjectExportService.java` (new SPI)
- `core/jaiclaw-gateway/src/main/java/io/jaiclaw/gateway/gdpr/GdprController.java` (new REST controller)

```java
public interface DataSubjectExportService {
    DataSubjectExport exportForDataSubject(String tenantId, String dataSubjectId, ExportFormat format);
    enum ExportFormat { JSON, JSON_LD, CSV_BUNDLE }
}
```

REST endpoint: `GET /api/gdpr/export/{dataSubjectId}?format=json` — authenticated, tenant-scoped, rate-limited. Returns a bundle containing transcripts + memory + audit events + session metadata, in the requested format. `JSON_LD` binds fields to schema.org / W3C DPV vocab where possible for GDPR Art. 20 portability.

**Acceptance:** REST integration test seeds a data subject's data, calls the endpoint, validates the returned bundle round-trips through a JSON schema and contains all four data classes. Deny test: tenant A cannot request tenant B's data even with tenant B's data-subject-id.

**Effort:** 1–2 weeks.

### T2-3. `PromptRedactor` SPI — PHI/PII masking before LLM dispatch

**Files:**
- `core/jaiclaw-agent/src/main/java/io/jaiclaw/agent/redaction/PromptRedactor.java` (new SPI)
- `core/jaiclaw-agent/src/main/java/io/jaiclaw/agent/redaction/RegexPromptRedactor.java` (reference impl with configurable patterns for SSN, MRN, phone, email, DOB)
- Wired into `AgentRuntime` before the `SystemPromptBuilder`+`UserMessage` handoff to the ChatClient.

```java
public interface PromptRedactor {
    RedactionResult redact(String content, TenantContext context);
    record RedactionResult(String redactedContent, List<RedactionMatch> matches) {}
    record RedactionMatch(String pattern, int start, int end, String replacement) {}
}
```

Redaction runs when `TenantContext.isPhiProcessing()` OR `RedactionMode = STRICT`. Matches are stored in the audit event's `dataCategories` as `redacted.<pattern>` so operators can see what was masked without seeing the content.

**Acceptance:** Spock spec: with `phi_processing=true`, a message containing an SSN pattern arrives redacted before ChatClient sees it; audit event lists the redaction. Deny test: with `phi_processing=false`, no redaction runs.

**Effort:** 2 weeks.

### T2-4. Encryption decorators for `TranscriptStore`, `MemoryProvider`, `AuditLogger`

**Files:**
- `core/jaiclaw-core/src/main/java/io/jaiclaw/core/encryption/FieldEncryptor.java` (new SPI — AES-GCM by default, `SecretsProvider`-sourced key)
- `extensions/jaiclaw-audit/src/main/java/io/jaiclaw/audit/EncryptedTranscriptStore.java` (decorator over any `TranscriptStore`)
- `extensions/jaiclaw-audit/src/main/java/io/jaiclaw/audit/EncryptedAuditLogger.java` (decorator)
- `core/jaiclaw-memory/src/main/java/io/jaiclaw/memory/EncryptedMemoryProvider.java` (decorator)

Opt-in via `jaiclaw.security.encryption.enabled=true` + key-source config (`env:JAICLAW_ENCRYPTION_KEY`, `file:/etc/…`, `secretsprovider:my-key`). Encryption is at the payload level (JSON body encrypted, metadata like timestamps + tenantId in clear so queries still work). Decryption on read is transparent to callers — no downstream code changes.

**Acceptance:** Spock spec: write a transcript through the decorator, read the file on disk, assert content is base64-ciphertext (not clear JSON); read back through the decorator, assert plaintext round-trip.

**Effort:** 2–3 weeks. Includes a written crypto rationale + review checklist.

### T2-5. `ConsentManager` SPI

**Files:**
- `core/jaiclaw-core/src/main/java/io/jaiclaw/core/gdpr/ConsentManager.java` (new SPI)
- `extensions/jaiclaw-audit/src/main/java/io/jaiclaw/audit/consent/FileConsentStore.java` (reference impl)

```java
public interface ConsentManager {
    ConsentToken recordConsent(String tenantId, String dataSubjectId, String consentType, String proof);
    void withdrawConsent(String tenantId, String dataSubjectId, String consentType);
    Map<String, Instant> getConsentStatus(String tenantId, String dataSubjectId);
}
```

Every recorded consent + withdrawal emits an audit event. `TenantContext.getConsentToken()` (T1-1 metadata) becomes the reference from a session back to the consent record.

**Acceptance:** Spock spec: record consent, verify audit event; withdraw, verify second audit event; query status returns both. Integration test: with a withdrawn consent, `PromptRedactor` (T2-3) refuses to send content to the LLM.

**Effort:** 1–2 weeks.

### T2-6. Immutable append-only audit log (cryptographic chain-of-hashes)

**File:** `extensions/jaiclaw-audit/src/main/java/io/jaiclaw/audit/FileAuditLogger.java`

Extend the JSONL format with a `prevHash` field on each event — `SHA-256(previousEvent + currentEvent)`. On startup, `FileAuditLogger` verifies the chain of every date-partitioned file it opens. Tampering (an event modified or removed) breaks the chain and is logged + reported via a new `AuditIntegrityViolation` event.

**Acceptance:** Spock spec: seed a log with 100 events, verify chain green; manually edit the middle event; re-open the log, assert a violation is reported with the offending event index.

**Effort:** 1–2 weeks.

### T2-7. `PrivacyNoticeService` SPI + channel-adapter hook

**Files:**
- `core/jaiclaw-core/src/main/java/io/jaiclaw/core/gdpr/PrivacyNoticeService.java`
- Hook into `ChannelAdapter` lifecycle: on first message from a new data subject, deliver the notice + record acceptance before the message proceeds.

**Acceptance:** Spock spec: mock a Slack adapter, verify that a first-time-user message triggers a `PRIVACY_NOTICE_DISPLAYED` audit event and the notice text is delivered via `sendMessage` before the agent runs.

**Effort:** 1 week.

### Tier 2 acceptance gate for 1.0

- [ ] All T2 SPIs marked `@Stable` in `core/jaiclaw-core/src/main/java/io/jaiclaw/core/api/`.
- [ ] Reference implementations covered by ≥ 80% JaCoCo line coverage.
- [ ] Migration guide entry in `docs/user/MIGRATION.md` explaining how a 0.9.x adopter opts in.
- [ ] `docs/user/COMPLIANCE.md` upgraded from "compliance-aware" wording to "compliance-capable" wording.
- [ ] External counsel review of `COMPLIANCE.md` (tracked separately, gates the marketing claim).

---

## Tier 3 — post-1.0 (governance layer)

Ship these when there's adopter demand or a specific sales requirement. Each is self-contained.

### T3-1. `RopaGenerator` — GDPR Art. 30 export

Emit a machine-readable JSON (and pre-filled PDF template) reconstructing a Record of Processing Activities from the audit trail + tenant config. Effort: 1–2 weeks.

### T3-2. `ObjectionService` — GDPR Art. 21

Similar shape to `ConsentManager`. Effort: 1 week.

### T3-3. `AnomalyDetector` SPI + reference detectors

Mass-read, bulk-export, off-hours-access detection. Emits `SecurityEvent` audit entries. Actual alerting is external (SIEM). Effort: 1–2 weeks per detector.

### T3-4. DPA template + processor-metadata export

Auto-fill a Data Processing Agreement template with processor name, sub-processor list, retention, security measures — pulled from JaiClaw's own config. Effort: 1–2 weeks.

---

## Out of framework scope (deployer responsibility)

Called out explicitly so the plan doesn't overpromise and reviewers know what's still theirs:

- **Breach-notification orchestration** (GDPR Art. 33/34, HIPAA §164.400). JaiClaw emits structured events; deployers monitor and route.
- **BAA legal negotiation** with cloud LLM providers.
- **TLS termination, cipher suite selection, HSTS, private-network deployment**. Reverse proxy or Spring Boot `server.ssl.*`.
- **SIEM integration, log aggregation, alerting** (Splunk, Datadog, ELK). JaiClaw emits; deployers ingest.
- **IAM lifecycle, credential rotation, secrets vault operations**. `SecretsProvider` connects; deployers own the rotation.
- **MFA at the application boundary**. JaiClaw is a Bearer-token service; put MFA at the reverse proxy or upstream identity provider.

---

## Dependency graph (for scheduling)

```
T1-1 TenantContext metadata ──┬── T1-3 AuditingChatClient   ── T2-1 Erasure SPI
                              ├── T1-4 BAA enforcement      ── T2-3 PromptRedactor
                              └── T1-6 RetentionPolicy       ── T2-5 ConsentManager

T1-2 AuditEvent extension  ──── T2-6 Immutable audit log

T1-5 SEV-004 fixes  ─────  independent, ship any time in 0.9.4

T1-7 require-https  ─────  independent, ship any time in 0.9.4

T2-4 Encryption decorators  ── independent of T1 items

T2-2 DataSubjectExport      ── depends on T2-1 (uses same erasure infra for tenant scoping)

T2-7 PrivacyNotice          ── depends on T2-5 (writes into ConsentManager)
```

Suggested 0.9.4 order: T1-1, T1-2, T1-5, T1-7 first (independent + low-risk), then T1-6, T1-4, T1-3, T1-8 (build on the metadata + audit extensions).

---

## Risk callouts

1. **The BAA-eligible provider defaults are opinionated.** Marking `anthropic` (direct) as non-BAA is factually correct today, but a future Anthropic BAA offering would need us to update the default. Publish the default list in `docs/user/COMPLIANCE.md` and version-bump on change.
2. **Encryption key management is a real hazard.** If an adopter loses the key sourced by `SecretsProvider`, their encrypted transcripts are lost forever. The T2-4 doc must include a key-rotation runbook and a backup-encryption-key pattern.
3. **`PromptRedactor` is best-effort.** Regex-based redaction will miss free-form PHI expressions. Document that the SPI is a *risk reduction*, not a HIPAA safeguard on its own — an adopter deploying to a covered entity should still contract-restrict the LLM provider via a BAA.
4. **`DataSubjectErasureSpi` cascade must handle backup + replica state.** If an adopter's `TranscriptStore` is backed by Postgres with hot-standby replicas, deleting the primary row doesn't erase the replica. Doc must call this out; framework enforces primary erasure only.
5. **Counsel review is not on this plan's critical path.** But the marketing claim ("compliance-capable") is gated on external legal sign-off of `docs/user/COMPLIANCE.md`. Track separately.

---

## Success criteria

**0.9.4 ships successfully if:**

1. All T1 Spock specs green.
2. `docs/user/COMPLIANCE.md` exists and is fair (audit-checked internally).
3. An enterprise architect reading the compliance doc can, in 15 minutes, answer: "Can I deploy JaiClaw for our EU health portal?" — with a specific yes/no + a specific config recipe.

**1.0 ships successfully if:**

1. All T2 SPIs marked `@Stable`.
2. Reference implementations at ≥ 80% coverage.
3. `docs/user/COMPLIANCE.md` upgraded to "compliance-capable" wording.
4. External counsel review complete.
5. At least one pilot deployment running with T2 SPIs enabled in production for ≥ 30 days.
