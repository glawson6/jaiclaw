# GDPR (General Data Protection Regulation)

## What it is

The EU General Data Protection Regulation (Regulation (EU) 2016/679) governs the processing of personal data of individuals in the European Union. It applies to any organization processing EU-personal-data regardless of the organization's location.

Key concepts:
- **Data subject** — a natural person to whom the data relates
- **Controller** — the entity determining purposes and means of processing
- **Processor** — the entity processing data on behalf of the controller
- **Lawful basis** (Art. 6) — every processing operation must have one
- **Data subject rights** (Art. 15–22) — access, rectification, erasure, portability, restriction, objection

For a JaiClaw-based deployment, GDPR applies when:
- Any user is in the EU
- Any tenant processes EU-personal-data
- The deployment sends data to any recipient in the EU or transfers data outside the EU

## What JaiClaw contributes

JaiClaw has shipped GDPR infrastructure since **0.9.3**. The full operator guide is at [`docs/user/COMPLIANCE.md`](../user/COMPLIANCE.md); this deep-dive extracts the GDPR-specific Article-level mapping.

### Article-level mapping

| Article | Requirement | JaiClaw contribution |
|---|---|---|
| **Art. 5(1)(a) Lawfulness, fairness, transparency** | Processing must be lawful and transparent | Every request's `TenantContext.KEY_LAWFUL_BASIS` records the Art. 6 basis; `AuditingChatModelBeanPostProcessor` records every LLM call with the basis attached |
| **Art. 5(1)(b) Purpose limitation** | Data collected for specified purpose | `TenantContext` metadata carries the collection purpose (adopter sets); audit trail preserves it per event |
| **Art. 5(1)(c) Data minimization** | Adequate, relevant, limited | `RegexPromptRedactor` (PromptRedactor SPI) removes SSN/DOB/etc. before LLM dispatch |
| **Art. 5(1)(e) Storage limitation** | Kept no longer than necessary | `RetentionEnforcementService` runs scheduled purge; per-tenant `KEY_RETENTION_DAYS` sets TTL |
| **Art. 5(1)(f) Integrity + confidentiality** | Protected against unauthorized access | `AesGcmFieldEncryptor`, `EncryptedTranscriptStore`, `EncryptedAuditLogger`, `HashChainedAuditLogger` |
| **Art. 6 Lawful basis** | One of consent / contract / legal obligation / vital interests / public task / legitimate interests | `TenantContext.KEY_LAWFUL_BASIS = "gdpr.lawful_basis"` — enum in application code, string in audit log |
| **Art. 7 Conditions for consent** | Consent must be freely given, specific, informed | `InMemoryConsentManager` (reference) + `ConsentManager` SPI; emits `consent.recorded` / `consent.withdrawn` audit events |
| **Art. 15 Right of access** | Data subject can request info about processing | `AggregateDataSubjectExportService` + `GdprController` at `/api/gdpr/export/{dataSubjectId}` |
| **Art. 17 Right to erasure ("right to be forgotten")** | Data subject can request deletion | `AggregateDataSubjectErasureSpi` — cascades over `TranscriptStore` + `AuditLogger` beans; emits `data.subject_erasure` audit event; `DELETE /api/gdpr/subject/{id}` |
| **Art. 18 Right to restriction** | Data subject can request restricted processing | `TenantContext.KEY_RESTRICTION_FLAGS = "data.restriction_flags"` — adopter sets flags like `no_llm_calls`, `no_memory_writes`; framework code respects them |
| **Art. 20 Right to data portability** | Machine-readable export | `AggregateDataSubjectExportService` — same as Art. 15 |
| **Art. 30 Records of processing activities** | Controller maintains ROPA | Every `AuditEvent` includes `lawfulBasis`, `dataCategories`, `recipients`, `retentionDays`; `AuditingChatModel` records every model.inference.request |
| **Art. 32 Security of processing** | Encryption, pseudonymisation, integrity | See §164.312 mappings in [hipaa.md](hipaa.md) — same primitives |
| **Art. 33/34 Breach notification** | 72-hour notification | Audit trail feeds detection; adopter's IR program handles notification |
| **Art. 44 Transfers to third countries** | Cross-border transfers gated | `TenantContext.KEY_DATA_RESIDENCY = "data.residency_required"` records residency requirement; audit event's `recipients` list every provider called (per Art. 30 record of processing) — adopter's DPO reviews |

### GDPR REST surface

`GdprController` provides adopter-facing endpoints:

- **`GET /api/gdpr/export/{dataSubjectId}`** — Art. 15 / 20 data export
- **`DELETE /api/gdpr/subject/{dataSubjectId}`** — Art. 17 erasure

**Adopters MUST front these endpoints with a rate limiter + role-guarded auth layer.** The framework does not include ready-made rate limiting or role gates on these endpoints — adopter's Spring Security config applies. See [`docs/user/COMPLIANCE.md`](../user/COMPLIANCE.md) § "GDPR REST surface" for full example config.

### PromptRedactor SPI (Art. 5(1)(c) data minimization)

`RegexPromptRedactor` runs on all LLM inputs when either:
- `TenantContext.KEY_PHI_PROCESSING = true`, OR
- Strict mode is on (`jaiclaw.compliance.effective.prompt-redaction=true`)

Redacts common personal-data patterns. Match metadata is preserved in the audit event.

### Compliance profile support

```yaml
jaiclaw:
  compliance:
    profile: gdpr  # or 'both' if also processing PHI
```

Activates:
- `require-https: true`
- `retention-enforcement: true`
- `audit-chat-client: true`
- `baa-warnings: false` (HIPAA-specific)
- `prompt-redaction: false` (opt-in even under GDPR — controllers make their own data-minimization decisions)

## How to enable / disable

**Default:** off. Nothing GDPR-specific activates unless the operator opts in.

**Enable — via profile (recommended):**
```yaml
jaiclaw:
  compliance:
    profile: gdpr        # or 'both' if also processing PHI
```

**Enable — individual flag:**
```yaml
jaiclaw:
  compliance:
    retention-enforcement: true  # per-tenant retention purge only
    audit-chat-client: true      # LLM-call audit only
    prompt-redaction: true       # opt-in — even GDPR-strict adopters choose
```

**Disable / override:**
```yaml
jaiclaw:
  compliance:
    profile: gdpr
    require-https: false   # bench deployment — explicit override
```

**Fully off:**
```yaml
jaiclaw:
  compliance:
    profile: none    # (default)
```

**Per-request lawful-basis labelling** (independent of profile — populates audit records):
```java
TenantContext ctx = TenantContext.builder()
    .metadata("gdpr.lawful_basis", "consent")   // Art. 6 basis
    .metadata("data.residency_required", "eu-west")
    .metadata("gdpr.consent_token", "consent-record-abc123")
    .build();
```

## What's adopter responsibility

- **Legal basis determination** — the Art. 6 lawful basis for each processing activity
- **Data Protection Impact Assessment (DPIA)** — Art. 35 required for high-risk processing
- **Appointing a DPO** — Art. 37 if criteria met
- **Data Processing Agreements (DPAs)** — Art. 28 with every processor
- **Breach notification** — 72-hour clock; adopter's IR runbook
- **Data subject request workflow** — the framework provides the API; the adopter operates the process (identity verification, response drafting)
- **Cross-border transfer mechanisms** — Standard Contractual Clauses (SCCs), adequacy decisions, or BCRs
- **Cookie consent + privacy notice** — for the deployed UI
- **Records of processing activities** — the audit log is one input; the adopter's ROPA document is the artifact

## What's out of scope

- **Data classification** — the framework accepts `data.categories` labels; the adopter decides what's personal data
- **Cookies / tracking on adopter's UI** — outside framework scope
- **Marketing consent workflows** — separate from processing consent
- **DPO advice** — legal function

## How to verify

**Activate the GDPR profile + inspect effective flags:**
```bash
JAICLAW_COMPLIANCE_PROFILE=gdpr \
    ./mvnw spring-boot:run -pl :jaiclaw-gateway-app

# Boot log should include:
#   ComplianceEnvironmentPostProcessor: profile=gdpr, effective flags:
#     require-https=true, retention-enforcement=true, audit-chat-client=true
```

**Test Art. 17 erasure:**
```bash
# Assumes the adopter has front-loaded auth on /api/gdpr/**
curl -X DELETE https://your-deployment/api/gdpr/subject/alice@example.com \
    -H "Authorization: Bearer $OPERATOR_TOKEN"

# Audit log should show data.subject_erasure event with tenant + subject + cascaded stores
```

**Test Art. 15 export:**
```bash
curl -X GET https://your-deployment/api/gdpr/export/alice@example.com \
    -H "Authorization: Bearer $OPERATOR_TOKEN"

# Returns a JSON document with everything the framework has about the subject
```

**Verify audit trail carries lawful basis:**
```bash
curl -X GET http://localhost:8080/actuator/audit | jq '.events[0].details'
# Should include lawfulBasis, dataCategories, recipients, retentionDays
```

## Related code files

- `extensions/jaiclaw-compliance/src/main/java/io/jaiclaw/compliance/gdpr/AggregateDataSubjectErasureSpi.java`
- `extensions/jaiclaw-compliance/src/main/java/io/jaiclaw/compliance/gdpr/AggregateDataSubjectExportService.java`
- `extensions/jaiclaw-compliance/src/main/java/io/jaiclaw/compliance/gdpr/GdprController.java`
- `extensions/jaiclaw-compliance/src/main/java/io/jaiclaw/compliance/consent/InMemoryConsentManager.java`
- `extensions/jaiclaw-compliance/src/main/java/io/jaiclaw/compliance/audit/AuditingChatModelBeanPostProcessor.java`
- `extensions/jaiclaw-compliance/src/main/java/io/jaiclaw/compliance/gdpr/RegexPromptRedactor.java`
- `extensions/jaiclaw-compliance/src/main/java/io/jaiclaw/compliance/ComplianceProfile.java` (GDPR / BOTH profile)
- `extensions/jaiclaw-audit/src/main/java/io/jaiclaw/audit/RetentionEnforcementService.java`
- `core/jaiclaw-core/src/main/java/io/jaiclaw/core/tenant/TenantContext.java` (KEY_LAWFUL_BASIS, KEY_RESTRICTION_FLAGS, KEY_DATA_RESIDENCY, KEY_CONSENT_TOKEN)

## References

- GDPR full text: [https://gdpr-info.eu/](https://gdpr-info.eu/)
- EDPB (European Data Protection Board) guidelines: [https://www.edpb.europa.eu/our-work-tools/general-guidance/gdpr-guidelines-recommendations-best-practices_en](https://www.edpb.europa.eu/)
- Schrems II ruling (cross-border transfers): [https://curia.europa.eu/juris/document/document.jsf?text=&docid=228677&pageIndex=0&doclang=EN](https://curia.europa.eu/juris/document/document.jsf?text=&docid=228677)
- **Full JaiClaw operator guide:** [`docs/user/COMPLIANCE.md`](../user/COMPLIANCE.md)
