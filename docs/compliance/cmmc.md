# CMMC 2.0 (Cybersecurity Maturity Model Certification)

## What it is

CMMC 2.0 is the U.S. Department of Defense's certification program for defense contractors handling FCI (Federal Contract Information) and CUI (Controlled Unclassified Information). Three levels:

- **Level 1 (Foundational)** — 17 practices from FAR 52.204-21; self-assessment
- **Level 2 (Advanced)** — 110 practices aligned with NIST SP 800-171 R2; C3PAO assessment for select programs, self-assessment otherwise
- **Level 3 (Expert)** — Level 2 + additional NIST SP 800-172 controls; DIBCAC assessment

Most defense contractors handling CUI need Level 2.

## What JaiClaw contributes

CMMC Level 2 aligns with **NIST SP 800-171 R2**, which is a subset of NIST SP 800-53 R5 tailored for non-federal systems processing CUI. Because JaiClaw substantially covers the equivalent 800-53 controls (see [nist-800-53.md](nist-800-53.md)), it inherits most of the technical practices needed for CMMC L2.

### CMMC L2 domain contribution table

Legend:
- ✅ **Direct** — JaiClaw satisfies with in-code implementation
- 🟡 **Partial** — JaiClaw provides the primitive; adopter completes
- ⬜ **Adopter/Org** — Not addressed by a framework library

| CMMC Domain | Practices | JaiClaw Status | Evidence |
|---|---|---|---|
| **AC** — Access Control | 22 | ✅ Direct | `ApiKeyAuthenticationFilter`, `JwtAuthenticationFilter`, `TenantGuard` (fail-closed in MULTI), `ToolProfile` least privilege, `RateLimitFilter` |
| **AU** — Audit & Accountability | 9 | ✅ Direct | `AuditLogger` SPI + `FileAuditLogger` + `HashChainedAuditLogger` (SHA-256 tamper evidence) + `AuditingChatModelBeanPostProcessor` (every LLM call audited) |
| **AT** — Awareness & Training | 3 | ⬜ Adopter | HR / training program |
| **CM** — Config Management | 9 | 🟡 Partial | Auto-config + opt-in modules; adopter maintains baseline config docs |
| **IA** — Identification & Auth | 11 | ✅ Direct | `ApiKeyProvider` (timing-safe compare), JWT (32+ char secret enforced), `PkceGenerator` for OAuth flows |
| **IR** — Incident Response | 3 | 🟡 Partial | Audit trail emits `security.*` action events; adopter runs IR program |
| **MA** — Maintenance | 6 | ⬜ Adopter | Ops process |
| **MP** — Media Protection | 9 | 🟡 Partial | `TenantContext.KEY_DATA_CATEGORIES` + `KEY_DATA_RESTRICTIONS` + `KEY_CUI_PROCESSING` labels; adopter enforces media handling |
| **PS** — Personnel Security | 2 | ⬜ Adopter | HR |
| **PE** — Physical Protection | 6 | ⬜ Adopter/CSP | Data center / cloud provider |
| **RA** — Risk Assessment | 3 | 🟡 Partial | OWASP dep-check plugin configured; adopter runs periodic scans |
| **CA** — Security Assessment | 4 | ⬜ Adopter | ATO process |
| **SC** — System & Comms Protection | 16 | ✅ Direct | `RequireHttpsStartupGuard`, `SsrfGuard`, `AesGcmFieldEncryptor`, `EncryptedTranscriptStore`, `EncryptedAuditLogger`; `TenantGuard` for info-in-shared-resources; `CuiWarningChatModelDecorator` for provider gating |
| **SI** — System & Info Integrity | 7 | ✅ Direct | `HashChainedAuditLogger`, `RegexPromptRedactor`, `jaiclaw-web-errors-*` modules for error-handling, OWASP dep-check |

**Total: 110 practices; JaiClaw directly contributes to ~50, partially to ~15, adopter-only ~45.**

## CUI handling specifics

CMMC's headline requirement is treating CUI with due care. JaiClaw's contribution:

### 1. Label CUI on the request context

```java
TenantContext ctx = TenantContext.builder()
    .tenantId("dod-contractor-alpha")
    .metadata("cui.processing", true)  // ← this label
    .metadata("data.categories", Set.of("cui", "cti"))  // CUI + Controlled Technical Info
    .metadata("data.restriction_flags", Set.of("no_llm_export", "no_offshore_transfer"))
    .build();
```

### 2. `CuiWarningChatModelDecorator` gates provider selection

Reads `cui.processing` from `TenantContext`. If `true` and the resolved LLM provider is not marked CUI-authorized:

```yaml
jaiclaw:
  models:
    providers:
      anthropic:
        cui-authorized: false  # commercial Anthropic — NOT CUI-authorized
      minimax-cui-tenant:
        cui-authorized: true   # your on-prem MiniMax deployment behind DoD boundary
```

A WARN is logged (or, if the operator subclasses `CuiWarningChatModelDecorator`, an exception is thrown to hard-refuse the call).

### 3. Audit-trail cross-boundary tracking

Every `AuditEvent` for a CUI-processing request includes:
- `dataCategories` — includes `cui`
- `recipients` — the LLM provider name (e.g., `on-prem-mistral-secure-enclave`)
- `retentionDays` — from `TenantContext.KEY_RETENTION_DAYS`

Assessors can grep the audit log to prove no CUI was ever sent to a non-authorized recipient.

### 4. Encryption at rest for CUI

`EncryptedTranscriptStore` + `EncryptedAuditLogger` decorate the corresponding SPIs with AES-GCM-256 field encryption. Adopter supplies key via `SecretsProvider` — typically an HSM-backed KMS in a CMMC L2 deployment.

## Compliance profile support

JaiClaw ships a `CMMC_L2` profile that flips defaults appropriately:

```yaml
jaiclaw:
  compliance:
    profile: cmmc-l2
```

Activates:
- `require-https: true`
- `retention-enforcement: true`
- `audit-chat-client: true`
- `cui-warnings: true`
- `prompt-redaction: true` (`RegexPromptRedactor` runs on all LLM inputs)

## How to enable / disable

**Default:** off. Nothing CMMC-specific activates unless the operator opts in.

**Enable — via profile (recommended):**
```yaml
jaiclaw:
  compliance:
    profile: cmmc-l2    # turns on: require-https, retention-enforcement,
                        # audit-chat-client, cui-warnings, prompt-redaction
```

**Enable — individual flag:**
```yaml
jaiclaw:
  compliance:
    cui-warnings: true    # turn on the CUI provider decorator only
    # (other compliance flags stay at profile default / off)
```

**Disable / override (turn a profile flag off):**
```yaml
jaiclaw:
  compliance:
    profile: cmmc-l2
    prompt-redaction: false   # your input contains no personal data — skip redaction
```

**Fully off:**
```yaml
jaiclaw:
  compliance:
    profile: none    # (default)
```

**Per-request CUI labelling** (independent of profile — triggers the CUI decorator):
```java
TenantContext ctx = TenantContext.builder()
    .metadata("cui.processing", true)
    .metadata("data.categories", Set.of("cui", "cti"))
    .build();
```

## What's adopter responsibility

- **Boundary definition** — what network zone contains CUI, where the enclave boundary is
- **CUI marking + handling procedures** — DoD 5200.48 procedures
- **C3PAO or self-assessment** — depending on the contract level
- **SPRS score submission** — the Supplier Performance Risk System score
- **CUI-authorized LLM provider** — adopters typically self-host (Llama, Mistral in a DoD-approved enclave) or use a CMMC-authorized cloud provider
- **Personnel training** — CUI-handler training program
- **IR runbook** — CUI incident notification within 72 hours per DFARS

## What's out of scope

- **Level 3 practices** — additional NIST SP 800-172 controls (adopter-specific)
- **CMMC L1 (Foundational)** — much narrower; JaiClaw's L2 coverage over-satisfies L1
- **Specific DoD program overlays** — SAP (Special Access Program) requirements, etc.
- **DFARS 252.204-7012 direct compliance** — this is the contract clause; CMMC is the assessment process against it

## How to verify

**Confirm the cmmc-l2 profile activates the right flags:**
```bash
JAICLAW_COMPLIANCE_PROFILE=cmmc-l2 \
    ./mvnw spring-boot:run -pl :jaiclaw-gateway-app

# Boot log should include:
#   ComplianceEnvironmentPostProcessor: profile=cmmc-l2, effective flags:
#     require-https=true, retention-enforcement=true, audit-chat-client=true,
#     cui-warnings=true, prompt-redaction=true
```

**Verify `CuiWarningChatModelDecorator` fires on CUI-labeled requests:**
```bash
# In a test client:
TenantContextHolder.set(TenantContext.builder()
    .metadata("cui.processing", true).build());
# Invoke a LLM call using a provider without cui-authorized=true
# Expected WARN log: "CUI-processing tenant resolved to non-CUI-authorized provider"
```

**Cross-check every AU practice:**
```bash
grep -n "AuditEvent\|AuditLogger" extensions/jaiclaw-audit/src/main/java/io/jaiclaw/audit/*.java
# Should show FileAuditLogger, InMemoryAuditLogger, AuditEvent, plus HashChainedAuditLogger decorator
```

## Related code files

- `extensions/jaiclaw-compliance/src/main/java/io/jaiclaw/compliance/cui/CuiWarningChatModelDecorator.java`
- `extensions/jaiclaw-compliance/src/main/java/io/jaiclaw/compliance/ComplianceProfile.java` (CMMC_L2 enum value)
- `extensions/jaiclaw-compliance/src/main/java/io/jaiclaw/compliance/ComplianceProperties.java` (cuiWarnings field)
- `core/jaiclaw-core/src/main/java/io/jaiclaw/core/tenant/TenantContext.java` (KEY_CUI_PROCESSING)
- Plus every file listed in [nist-800-53.md](nist-800-53.md) — CMMC L2 ⊂ NIST 800-171 ⊂ NIST 800-53

## References

- CMMC 2.0 Program: [https://dodcio.defense.gov/CMMC/](https://dodcio.defense.gov/CMMC/)
- CMMC L2 Assessment Guide: [https://dodcio.defense.gov/Portals/0/Documents/CMMC/AssessmentGuideL2v2.pdf](https://dodcio.defense.gov/Portals/0/Documents/CMMC/AssessmentGuideL2v2.pdf)
- NIST SP 800-171 R2: [https://nvlpubs.nist.gov/nistpubs/SpecialPublications/NIST.SP.800-171r2.pdf](https://nvlpubs.nist.gov/nistpubs/SpecialPublications/NIST.SP.800-171r2.pdf)
- DFARS 252.204-7012: [https://www.acquisition.gov/dfars/252.204-7012-safeguarding-covered-defense-information-and-cyber-incident-reporting.](https://www.acquisition.gov/dfars/252.204-7012)
- DoD CUI Registry: [https://www.dodcui.mil/](https://www.dodcui.mil/)
