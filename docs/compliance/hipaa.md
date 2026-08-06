# HIPAA (Health Insurance Portability & Accountability Act)

## What it is

The Health Insurance Portability and Accountability Act of 1996 (Pub. L. 104–191) establishes national standards for the protection of Protected Health Information (PHI). JaiClaw compliance targets:

- **§164.302–318 Security Rule** — administrative, physical, and technical safeguards
- **§164.500–534 Privacy Rule** — use and disclosure requirements
- **§164.400–414 Breach Notification** — notification obligations

For LLM-based applications, the HIPAA question is usually: *"can we send patient data to a model provider without violating the safeguards?"* JaiClaw's answer is yes, with the right combination of BAA-eligible provider selection, encryption, audit trail, and access controls.

## What JaiClaw contributes

JaiClaw has shipped HIPAA-relevant infrastructure since **0.9.3**. The full operator guide is at [`docs/user/COMPLIANCE.md`](../user/COMPLIANCE.md); this deep-dive extracts the HIPAA-specific mapping.

### §164.312 Technical Safeguards mapping

| Safeguard | Requirement | JaiClaw contribution |
|---|---|---|
| **§164.312(a)(1) Access Control** | Unique user identification, emergency access, automatic logoff, encryption/decryption | JWT / API-key auth (`ApiKeyAuthenticationFilter`, `JwtAuthenticationFilter`) — unique user identification; `TenantGuard` enforces per-tenant isolation; session TTL via `SessionManager` provides automatic logoff; `AesGcmFieldEncryptor` provides encryption |
| **§164.312(a)(2)(i) Unique User Identification** | Assign unique name/number for tracking | JWT `subject` claim + API-key ID; every audit event records the actor |
| **§164.312(a)(2)(iv) Encryption / Decryption** | Implement mechanism to encrypt/decrypt ePHI | `AesGcmFieldEncryptor` (AES-GCM-256, 12-byte nonce); `EncryptedTranscriptStore` + `EncryptedAuditLogger` decorators |
| **§164.312(b) Audit Controls** | Hardware, software, and procedural mechanisms that record and examine activity | `AuditLogger` SPI + `FileAuditLogger` (JSON-lines, tenant-partitioned) + `HashChainedAuditLogger` (SHA-256 tamper evidence); `AuditingChatModelBeanPostProcessor` audits every LLM call |
| **§164.312(c)(1) Integrity** | Protect ePHI from improper alteration or destruction | `HashChainedAuditLogger.verifyChain()` detects any modification to the audit log |
| **§164.312(d) Person or Entity Authentication** | Verify the person seeking access is claimed | JWT signature verification (HMAC-SHA256, 32+ char secret enforced at bean-creation time); API-key timing-safe comparison via `MessageDigest.isEqual()` |
| **§164.312(e)(1) Transmission Security** | Guard against unauthorized access to ePHI during transmission | `RequireHttpsStartupGuard` refuses to start on non-loopback plaintext; `HIPAA` and `BOTH` compliance profiles activate this automatically |
| **§164.312(e)(2)(ii) Encryption in Transit** | Implement mechanism to encrypt ePHI | HTTPS via TLS (adopter configures certs); LLM calls to BAA-eligible providers use TLS |

### BAA-eligible LLM provider enforcement

The `BaaWarningChatModelDecorator` is a passive audit that warns when a tenant marked as PHI-processing resolves to a non-BAA-eligible LLM provider:

```yaml
jaiclaw:
  models:
    providers:
      anthropic-bedrock-us-east-1:
        baa-eligible: true   # AWS Bedrock has a BAA option
      anthropic-direct:
        baa-eligible: false  # Direct Anthropic API — no BAA option as of writing
```

```java
// Client sets phi_processing on the tenant:
TenantContext ctx = TenantContext.builder()
    .metadata("hipaa.phi_processing", true)
    .build();

// If ctx.isPhiProcessing() and resolved provider isn't baa-eligible,
// BaaWarningChatModelDecorator emits a WARN log with actionable guidance.
```

Adopters requiring hard refusal (not just warn) can subclass `BaaWarningChatModelDecorator` and throw.

### PHI redaction (PromptRedactor SPI)

`RegexPromptRedactor` provides a starter implementation that redacts common PHI patterns before LLM dispatch:

- SSN (`XXX-XX-XXXX`)
- MRN (Medical Record Number)
- Phone (US, international formats)
- Email
- Date of birth
- Credit card numbers

```yaml
jaiclaw:
  compliance:
    profile: hipaa   # sets prompt-redaction=true
```

Matches are replaced with `[REDACTED-<CLASS>]` and metadata about redactions is preserved in the audit event for later analysis. Adopters can extend `PromptRedactor` with organization-specific patterns.

### Retention enforcement (§164.316(b)(2) — 6-year minimum)

`RetentionEnforcementService` runs a scheduled purge that respects per-tenant retention policies:

```java
TenantContext ctx = TenantContext.builder()
    .metadata("data.retention_days", 2190)  // 6 years, HIPAA minimum
    .build();
```

Emits a `data.retention_purge` audit event with counts per tenant per day.

### Compliance profile support

```yaml
jaiclaw:
  compliance:
    profile: hipaa  # or 'both' if also processing EU personal data
```

Activates:
- `require-https: true`
- `retention-enforcement: true`
- `audit-chat-client: true`
- `baa-warnings: true`
- `prompt-redaction: true`

## How to enable / disable

**Default:** off. Nothing HIPAA-specific activates unless the operator opts in.

**Enable — via profile (recommended):**
```yaml
jaiclaw:
  compliance:
    profile: hipaa      # or 'both' if also processing EU personal data
```

**Enable — individual flag:**
```yaml
jaiclaw:
  compliance:
    baa-warnings: true      # BAA-provider warning decorator only
    prompt-redaction: true  # PHI redaction only
```

**Disable / override:**
```yaml
jaiclaw:
  compliance:
    profile: hipaa
    prompt-redaction: false   # input is already sanitized upstream
```

**Fully off:**
```yaml
jaiclaw:
  compliance:
    profile: none    # (default)
```

**Per-request PHI labelling** (independent of profile — triggers the decorators):
```java
TenantContext ctx = TenantContext.builder()
    .metadata("hipaa.phi_processing", true)
    .metadata("data.retention_days", 2190)   // 6-year HIPAA minimum
    .build();
```

## What's adopter responsibility

- **Executing BAAs** — Business Associate Agreements with each BAA-eligible provider (Anthropic, AWS Bedrock, Azure OpenAI GovCloud) plus any sub-contractors handling ePHI
- **Physical safeguards** (§164.310) — data center controls, workstation security
- **Administrative safeguards** (§164.308) — designated security officer, workforce training, sanction policy, IR program
- **Breach notification** (§164.400) — 60-day notification obligation post-breach
- **Business continuity plan** (§164.308(a)(7)) — data backup, disaster recovery
- **Access management** — provisioning, deprovisioning, periodic access review
- **Sanction policy** — for workforce members who violate HIPAA rules
- **Configuring per-tenant retention** — `TenantContext.KEY_RETENTION_DAYS` set to at least 2190 for full HIPAA compliance

## What's out of scope

- **HITECH Act enforcement provisions** — specific to the Office for Civil Rights process
- **State-level health-privacy laws** — CCPA, TX HB4, WA My Health My Data Act (adopters manage per-jurisdiction)
- **Medical-device regulations** — FDA / SaMD (Software as a Medical Device) is a different regime

## How to verify

**Activate the HIPAA profile + inspect effective flags:**
```bash
JAICLAW_COMPLIANCE_PROFILE=hipaa \
    ./mvnw spring-boot:run -pl :jaiclaw-gateway-app

# Boot log should include:
#   ComplianceEnvironmentPostProcessor: profile=hipaa, effective flags:
#     require-https=true, retention-enforcement=true, audit-chat-client=true,
#     baa-warnings=true, prompt-redaction=true
```

**Verify PHI redaction:**
```bash
# In a test:
TenantContextHolder.set(TenantContext.builder()
    .metadata("hipaa.phi_processing", true).build());
// Send a message: "Patient SSN 123-45-6789 needs follow-up"
// The LLM should see: "Patient SSN [REDACTED-SSN] needs follow-up"
// Audit event includes: redactions: {"ssn": 1}
```

**Verify BAA-warning fires:**
```bash
# Set a non-BAA-eligible provider as default
# In application.yml:
#   spring.ai.model.chat: anthropic (with baa-eligible=false)
# Invoke an LLM call with phi_processing=true
# Expected WARN log: "BAA-eligible provider required for PHI processing..."
```

**Verify audit chain integrity:**
```bash
# Programmatic:
HashChainedAuditLogger.verifyChain(tenantId);
# Returns Boolean; emits audit.integrity_violation event if chain is broken
```

## Related code files

- `extensions/jaiclaw-compliance/src/main/java/io/jaiclaw/compliance/audit/BaaWarningChatModelDecorator.java`
- `extensions/jaiclaw-compliance/src/main/java/io/jaiclaw/compliance/audit/HashChainedAuditLogger.java`
- `extensions/jaiclaw-compliance/src/main/java/io/jaiclaw/compliance/audit/AuditingChatModelBeanPostProcessor.java`
- `extensions/jaiclaw-compliance/src/main/java/io/jaiclaw/compliance/encryption/AesGcmFieldEncryptor.java`
- `extensions/jaiclaw-compliance/src/main/java/io/jaiclaw/compliance/encryption/EncryptedTranscriptStore.java`
- `extensions/jaiclaw-compliance/src/main/java/io/jaiclaw/compliance/encryption/EncryptedAuditLogger.java`
- `extensions/jaiclaw-compliance/src/main/java/io/jaiclaw/compliance/gdpr/RegexPromptRedactor.java`
- `extensions/jaiclaw-compliance/src/main/java/io/jaiclaw/compliance/ComplianceProfile.java` (HIPAA / BOTH profile)
- `extensions/jaiclaw-audit/src/main/java/io/jaiclaw/audit/RetentionEnforcementService.java`
- `core/jaiclaw-core/src/main/java/io/jaiclaw/core/tenant/TenantContext.java` (KEY_PHI_PROCESSING)
- `core/jaiclaw-security/src/main/java/io/jaiclaw/security/RequireHttpsStartupGuard.java`

## References

- HIPAA Security Rule: [https://www.hhs.gov/hipaa/for-professionals/security/index.html](https://www.hhs.gov/hipaa/for-professionals/security/index.html)
- HIPAA Privacy Rule: [https://www.hhs.gov/hipaa/for-professionals/privacy/index.html](https://www.hhs.gov/hipaa/for-professionals/privacy/index.html)
- OCR Guidance on Cloud Computing: [https://www.hhs.gov/hipaa/for-professionals/special-topics/health-information-technology/cloud-computing/index.html](https://www.hhs.gov/hipaa/for-professionals/special-topics/health-information-technology/cloud-computing/index.html)
- NIST SP 800-66 (HIPAA implementation guide): [https://nvlpubs.nist.gov/nistpubs/SpecialPublications/NIST.SP.800-66r2.pdf](https://nvlpubs.nist.gov/nistpubs/SpecialPublications/NIST.SP.800-66r2.pdf)
- **Full JaiClaw operator guide:** [`docs/user/COMPLIANCE.md`](../user/COMPLIANCE.md) — includes concrete YAML examples and step-by-step deployment recipes
