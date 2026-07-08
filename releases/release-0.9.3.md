# JaiClaw 0.9.3 Release Notes

**Release Date:** TBD

> 0.9.3 is the **compliance substrate** release — one coherent theme threaded
> through 21 focused commits across three tracks. The 0.9.x line freezes the
> compliance surface JaiClaw's 1.0 stability commitment can rest on: GDPR
> Articles 5, 6, 7, 15, 17, 18, 20, 21, 30, 44 and HIPAA §164.308 / .310 /
> .312 / .316 all now have concrete SPIs or reference implementations in the
> framework. Nothing turns on unless the operator sets
> `jaiclaw.compliance.profile={gdpr|hipaa|both}` — profile=none loads zero
> compliance code, so upgrading from 0.9.2 is behavior-preserving.
>
> Why this is the theme: JaiClaw's positioning ("compliance-capable
> framework") required a compliance surface real enough that an enterprise
> architect can answer "can I deploy this for our EU health portal" in 15
> minutes with a specific config recipe. That gap is closed in 0.9.3.
>
> **No breaking changes.** Every new SPI method is a default method,
> every new record field has a backward-compat constructor, and the new
> `jaiclaw-compliance` extension module is opt-in.

---

## Highlights

- **New `jaiclaw-compliance` extension module** — profile-driven orchestration
  of retention enforcement, LLM-call auditing, BAA-eligible-provider warnings,
  and the HTTPS startup guard. `ComplianceEnvironmentPostProcessor` runs at
  the earliest environment stage and translates `jaiclaw.compliance.profile`
  into a coherent bundle of individual flags at
  `jaiclaw.compliance.effective.*`. Individual flags override the profile
  default in either direction. All beans are `@ConditionalOnMissingBean` so
  adopters can swap in their own production impls.

- **Structured GDPR / HIPAA fields on `AuditEvent`** — `lawfulBasis`,
  `dataCategories`, `recipients`, `retentionDays`, `consentToken`. Deployers
  can reconstruct a GDPR Art. 30 Record of Processing (RoPA) from the audit
  stream without instrumenting individual call sites. Every cross-border
  transfer (Art. 44) has a matching event with the recipient identifier.

- **`AuditingChatModel` decorator wraps every ChatModel bean** when
  `jaiclaw.compliance.effective.audit-chat-client=true`. Every LLM `call()`
  and `stream()` emits a `model.inference.request` audit event populated
  from the caller's `TenantContext`. Idempotent BeanPostProcessor —
  already-wrapped models are returned as-is.

- **BAA-eligible LLM provider catalog** (`BaaEligibleProviders`) with
  hard-coded defaults: `bedrock`, `azure-openai`, `vertex-ai`, `ollama`
  BAA-eligible; `anthropic`, `openai`, `gemini`, `minimax`, `deepseek`,
  `mistral`, `oci-genai` not eligible. Overridable via
  `jaiclaw.models.providers.<name>.baa-eligible=true|false` when the
  adopter has a signed BAA (or the reverse).

- **`RetentionEnforcementService`** — scheduled per-tenant purge of
  transcripts + audit entries. Sources per-tenant TTL from
  `TenantContext.getRetentionDays()`, delegates to the new
  `purgeOlderThan(tenantId, cutoff)` default methods on `TranscriptStore` +
  `AuditLogger`. Every purge emits a `data.retention_purge` audit event
  with counts.

- **`RequireHttpsStartupGuard`** — refuses to start when the process
  would accept plaintext traffic on a non-loopback bind and
  `jaiclaw.security.require-https=true`. Loopback binds (`127.0.0.1`,
  `::1`, `localhost`) still start fine so dev workflows are unaffected.

- **12 new SPIs across GDPR + encryption**:
  - Tier 2 (planned for 1.0): `DataSubjectErasureSpi`,
    `DataSubjectExportService`, `PromptRedactor`, `FieldEncryptor`,
    `ConsentManager`, `PrivacyNoticeService`, plus the
    `HashChainedAuditLogger` decorator.
  - Tier 3 (planned post-1.0): `RopaGenerator`, `ObjectionService`,
    `AnomalyDetector`, `ProcessorMetadataExporter`.
  - All 12 marked `@Stable`. All 12 have working reference implementations
    in `jaiclaw-compliance`. Reference-impl line coverage: 91%.

- **New `docs/user/COMPLIANCE.md`** — the adopter-facing GDPR + HIPAA
  overview. Article-to-capability mapping, profile → flag matrix,
  per-tenant metadata reference, BAA-eligible provider table, Tier 2 + 3
  SPI reference, operator-responsibility checklist.

- **SEV-004 series closed** — six store-level tenant-context gaps + three
  async-context propagation gaps closed. `InMemoryCalendarProvider`
  no longer leaks availability across tenants in MULTI mode.

---

## Compliance substrate

The plan document (`docs/dev/COMPLIANCE-IMPLEMENTATION-PLAN.md`) organizes
compliance work into three tiers. All three shipped in 0.9.3.

### Tier 1 — non-breaking foundation (8 items)

| Item | Delivered |
|---|---|
| T1-1 | `TenantContext` compliance-metadata accessors (`getLawfulBasis`, `getRetentionDays`, `getRestrictionFlags`, `getDataResidencyRequired`, `isPhiProcessing`, `getConsentToken`) |
| T1-2 | `AuditEvent` GDPR/HIPAA fields + `purgeOlderThan` on `AuditLogger` + `TranscriptStore` SPIs |
| T1-3 | `AuditingChatModel` decorator + `AuditingChatModelBeanPostProcessor` |
| T1-4 | `BaaEligibleProviders` catalog + `baaEligible` override + `BaaWarningChatModelDecorator` |
| T1-5 | Six store tenant-context fixes + `InMemoryCalendarProvider.getAvailableSlots` fix |
| T1-6 | `RetentionPolicy` record + `RetentionEnforcementService` |
| T1-7 | `RequireHttpsStartupGuard` |
| T1-8 | `docs/user/COMPLIANCE.md` + `PRODUCTION-DEPLOYMENT.md` cross-links + `SECURITY.md` update |

### Tier 2 — 1.0 SPI additions (7 items)

| Item | SPI | Reference impl |
|---|---|---|
| T2-1 | `DataSubjectErasureSpi` | `AggregateDataSubjectErasureSpi` |
| T2-2 | `DataSubjectExportService` | `AggregateDataSubjectExportService` + `GdprController` at `/api/gdpr/export/*`, `/api/gdpr/subject/*` |
| T2-3 | `PromptRedactor` | `RegexPromptRedactor` (SSN, MRN, phone, email, DOB, credit card) |
| T2-4 | `FieldEncryptor` | `AesGcmFieldEncryptor` + `EncryptedTranscriptStore` + `EncryptedAuditLogger` |
| T2-5 | `ConsentManager` | `InMemoryConsentManager` |
| T2-6 | (decorator) | `HashChainedAuditLogger` — SHA-256 chain-of-hashes with `verifyChain(tenantId)` |
| T2-7 | `PrivacyNoticeService` | `DefaultPrivacyNoticeService` |

### Tier 3 — post-1.0 governance (4 items)

| Item | SPI | Reference impl |
|---|---|---|
| T3-1 | `RopaGenerator` | `AuditBasedRopaGenerator` — reconstructs Art. 30 RoPA from audit trail |
| T3-2 | `ObjectionService` | `InMemoryObjectionService` — mirrors `ConsentManager` for Art. 21 |
| T3-3 | `AnomalyDetector` | `MassReadDetector` — emits `security.event` audit entries |
| T3-4 | `ProcessorMetadataExporter` | `DefaultProcessorMetadataExporter` — DPA-ready processor + sub-processor + retention + security-measures export |

---

## Migration

Full migration guide at [docs/MIGRATION-0.9.3.md](../docs/MIGRATION-0.9.3.md).

**TL;DR for an existing 0.9.3 adopter:**

1. No action required to upgrade. Default `jaiclaw.compliance.profile=none`
   loads zero compliance code.
2. To turn on GDPR: add
   ```yaml
   jaiclaw:
     compliance:
       profile: gdpr
   ```
3. To turn on HIPAA: add `profile: hipaa`. Ensure your LLM route is BAA-eligible
   (or override `jaiclaw.models.providers.<name>.baa-eligible=true`) or expect
   a startup WARN.
4. To turn on both: `profile: both`.
5. Individual flags at `jaiclaw.compliance.require-https`,
   `jaiclaw.compliance.retention-enforcement`,
   `jaiclaw.compliance.audit-chat-client`, `jaiclaw.compliance.baa-warnings`,
   `jaiclaw.compliance.prompt-redaction` override the profile default.

---

## Breaking Changes

**None for the 0.9.2 → 0.9.3 upgrade.**

Every new SPI method is a default method. Every extended record has a
backward-compat constructor. The new `jaiclaw-compliance` module is
opt-in via the profile.

Downstream projects that override `AuditEvent` construction with the
8-arg positional form or `TranscriptStore.purgeOlderThan` explicitly
continue to work.

---

## New Modules

- **`extensions/jaiclaw-compliance`** — profile-driven GDPR + HIPAA
  orchestration. Opt-in via `jaiclaw.compliance.profile`. `provided`-scope
  dependencies on `spring-ai-model`, `spring-ai-client-chat`, `reactor-core`,
  and `spring-web` so it never inflates a consumer's classpath.

---

## Dependency Updates

None. 0.9.3 does not bump Spring Boot, Spring AI, Embabel, or Camel — all
compliance work is additive.

---

## Bug Fixes

- **SEV-004 series** — six store-level tenant-context gaps + three async
  propagation gaps closed. Notable: `InMemoryCalendarProvider.getAvailableSlots`
  no longer passes `tenantId=null` and no longer leaks availability across
  tenants in MULTI mode.

---

## Security Fixes

- `RequireHttpsStartupGuard` refuses to start on a non-loopback bind
  without TLS when `jaiclaw.security.require-https=true` (implicit under
  any active compliance profile).
- `HashChainedAuditLogger` provides tamper-evident audit for
  HIPAA §164.312(b), (c) via a per-tenant SHA-256 chain-of-hashes.
  `verifyChain(tenantId)` emits `audit.integrity_violation` events on any
  break.

---

## Acceptance Gate Status

| Gate | Status |
|---|---|
| Tier 1 §1 — All T1 Spock specs green in `./mvnw test` | ✅ |
| Tier 1 §2 — `jaiclaw:analyze compliance` sub-check | ⚠️ Deferred to 0.9.5 (advisory, not blocking) |
| Tier 1 §3 — External counsel review of `COMPLIANCE.md` | ⏳ External step, tracked separately |
| Tier 1 §4 — `release-0.9.3.md` calls out compliance substrate | ✅ (this document) |
| Tier 2 §1 — All T2 SPIs marked `@Stable` | ✅ Verified across `core/jaiclaw-core/src/main/java/io/jaiclaw/core/gdpr/` + `core/jaiclaw-core/src/main/java/io/jaiclaw/core/encryption/` |
| Tier 2 §2 — Reference impls ≥ 80% JaCoCo line coverage | ✅ 91% line coverage on reference impls; 84% bundle-wide |
| Tier 2 §3 — Migration guide entry | ✅ `docs/MIGRATION-0.9.3.md` |
| Tier 2 §4 — `COMPLIANCE.md` "compliance-capable" wording | ✅ Wording in place since T1-8 |
| Tier 2 §5 — External counsel review | ⏳ External step, tracked separately |
| 1.0 §5 — 30-day pilot deployment | ⏳ External — starts when 0.9.3 ships |
