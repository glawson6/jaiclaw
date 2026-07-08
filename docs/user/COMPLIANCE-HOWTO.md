# How To — Make a JaiClaw Deployment Compliance-Ready

**Companion to:** [COMPLIANCE.md](COMPLIANCE.md) (reference), [MIGRATION-0.9.3.md](../MIGRATION-0.9.3.md) (upgrade path)

This document is a **step-by-step playbook** for taking a JaiClaw deployment from "not compliance-configured" to "GDPR-ready" or "HIPAA-ready" or both. It's task-oriented — each section is a concrete thing you do, in order, with the YAML / Java / kubectl commands you'll actually run.

If you want to understand *what* the framework provides and *why* (article-to-capability mapping, SPI reference), read [COMPLIANCE.md](COMPLIANCE.md) instead.

---

## Prerequisites

You need all of these on hand before starting:

- [ ] JaiClaw 0.9.3 or later (`./mvnw help:evaluate -Dexpression=project.version -q -DforceStdout` from repo root, or check the BOM version your project imports)
- [ ] Multi-tenant deployment already working (`jaiclaw.tenant.mode: multi` and JWT auth set up)
- [ ] A `SecretsProvider` wired (env / file / 1Password / vault)
- [ ] TLS-terminating layer in front of the gateway — reverse proxy or `server.ssl.*`
- [ ] SIEM ingestion path for the audit stream (Splunk, Datadog, ELK — pick one)
- [ ] Legal counsel on tap for the DPA / RoPA review and BAA negotiation (the framework can't do these; you can)

If any of these is missing, stop and set it up before continuing. Trying to layer compliance on top of a broken foundation produces false confidence.

---

## Path A — I need GDPR

Follow this path if any of the following are true:
- You process EU residents' personal data
- You process UK / EEA / Swiss data (post-Brexit regs mirror GDPR)
- Your customer contracts obligate GDPR compliance regardless of jurisdiction

### A.1 — Enable the GDPR profile

Add one property to `application.yml`:

```yaml
jaiclaw:
  compliance:
    profile: gdpr
```

Restart the gateway. Verify the profile is active:

```bash
curl -s http://localhost:8888/actuator/env/jaiclaw.compliance.effective.profile
# → {"property":{"value":"gdpr", ...}}
```

At this point five things are on:

- `require-https` — startup guard blocks non-TLS non-loopback binds
- `retention-enforcement` — scheduled per-tenant purge of transcripts + audit
- `audit-chat-client` — every LLM call emits a `model.inference.request` audit event
- `GdprController` at `/api/gdpr/*` — REST surface for Art. 15/17/20 requests
- `RopaGenerator` / `ObjectionService` / `ProcessorMetadataExporter` beans are in the context

### A.2 — Set per-tenant compliance metadata

Every tenant subject to GDPR needs metadata on its `TenantContext`. Do this in your onboarding tool or JWT resolver — wherever you construct the context:

```java
TenantContext ctx = new DefaultTenantContext(
    "acme",                            // tenantId
    "Acme Corp",                       // tenantName
    Map.of(
        TenantContext.KEY_LAWFUL_BASIS, "contract",     // Art. 6 basis
        TenantContext.KEY_RETENTION_DAYS, 365,          // Data retained 1 year
        TenantContext.KEY_DATA_RESIDENCY, "eu-west",    // Routing hint
        TenantContext.KEY_CONSENT_TOKEN, "cnst_abc123"  // Ref to ConsentManager record
    )
);
TenantContextHolder.set(ctx);
```

`gdpr.lawful_basis` values (pick one per tenant based on how you contract with them):
- `consent` — data subject gave explicit consent (Art. 6(1)(a))
- `contract` — processing is necessary for the contract (Art. 6(1)(b))
- `legal_obligation` — required by law (Art. 6(1)(c))
- `vital_interests` — protecting life (Art. 6(1)(d))
- `public_task` — public authority function (Art. 6(1)(e))
- `legitimate_interests` — your own legitimate interest, with LIA on file (Art. 6(1)(f))

### A.3 — Wire the GdprController behind auth + rate-limiting

The controller is auto-wired at `/api/gdpr/*` but does **no** role auth and **no** rate-limiting. You must add both.

**Role auth** — the calling principal must have a `gdpr.operator` role (or your equivalent). If you use Spring Security, add:

```java
@Configuration
public class GdprSecurityConfig {
    @Bean
    public SecurityFilterChain gdprChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/api/gdpr/**")
            .authorizeHttpRequests(a -> a.anyRequest().hasRole("gdpr.operator"))
            .oauth2ResourceServer(o -> o.jwt(java.util.function.Function.identity()));
        return http.build();
    }
}
```

**Rate-limiting** — DSAR endpoints attract abuse. Two options:

- **Upstream** (recommended) — Cloudflare / ALB / nginx with `limit_req` for `/api/gdpr/*` set to something like 10/min per IP.
- **Application-level** — enable `jaiclaw.security.rate-limit.enabled=true` and configure `jaiclaw.security.rate-limit.rules[]` for `/api/gdpr/*`.

### A.4 — Test the DSAR export path

Seed a test subject, then verify the endpoint works:

```bash
# As a gdpr.operator with a tenant-scoped JWT for tenant 'acme':
curl -H "Authorization: Bearer <jwt>" \
     -H "X-Tenant-Id: acme" \
     "http://localhost:8888/api/gdpr/export/user-42?format=json_ld"
```

Expected: JSON-LD bundle with `@type: schema.org/Conversation` entries per transcript and `@type: w3.org/ns/dpv#PersonalDataHandling` entries per audit event.

Deny test — a different tenant's JWT must NOT get this data:

```bash
# JWT for tenant 'beta' asking for tenant 'acme's subject
curl -H "Authorization: Bearer <beta-jwt>" \
     -H "X-Tenant-Id: beta" \
     "http://localhost:8888/api/gdpr/export/user-42?format=json_ld"
# → returns beta's data (empty for user-42 if user-42 is only known to acme),
#   NOT acme's data. This is the isolation contract.
```

### A.5 — Test the erasure path

```bash
curl -X DELETE \
     -H "Authorization: Bearer <jwt>" \
     -H "X-Tenant-Id: acme" \
     "http://localhost:8888/api/gdpr/subject/user-42?reason=ART_17_REQUEST"
# → { "transcriptsDeleted": 3, "auditEventsDeleted": 12, ... }
```

The erasure emits a `data.subject_erasure` audit event with reason + counts. Query it back to confirm:

```bash
curl -H "Authorization: Bearer <jwt>" \
     "http://localhost:8888/api/gdpr/export/user-42?format=json"
# → should now show no transcripts; the erasure event itself survives as a tombstone
```

### A.6 — Wire consent recording

The framework ships `InMemoryConsentManager` as the default `ConsentManager` bean — **not durable**. For production, register your own:

```java
@Bean
@Primary
public ConsentManager consentManager(AuditLogger auditLogger) {
    return new PostgresConsentManager(dataSource, List.of(auditLogger));
    // Your impl. Records consent to a durable store. Emits audit events on every write.
}
```

Then wire your onboarding / opt-in flow to call it:

```java
ConsentManager.ConsentToken token = consentManager.recordConsent(
    "acme", "user-42", "processing", "signed:cookie-hash-abc123");
// Persist token.token() alongside the user record so later sessions can reference it
// via TenantContext.KEY_CONSENT_TOKEN.
```

### A.7 — Generate a RoPA on demand

For Art. 30 record-of-processing generation:

```java
@Autowired RopaGenerator ropaGenerator;

Instant from = Instant.now().minus(30, ChronoUnit.DAYS);
Instant to = Instant.now();
RopaGenerator.Ropa ropa = ropaGenerator.generate("acme", from, to);
// Feed ropa into your PDF template / Notion page / DPA form
```

The bundle contains one `ProcessingActivity` per audit action (`model.inference.request`, `data.subject_erasure`, etc.) with aggregated lawful bases, data categories, recipients, and retention.

### A.8 — GDPR verification checklist

- [ ] `/actuator/env/jaiclaw.compliance.effective.profile` returns `gdpr`
- [ ] Every tenant has `gdpr.lawful_basis` metadata
- [ ] `GdprController` is behind auth + rate-limiting
- [ ] Export path returns tenant-scoped data
- [ ] Erasure path returns non-zero counts + emits the tombstone event
- [ ] `ConsentManager` is a durable impl (not `InMemoryConsentManager`) for production
- [ ] TLS terminates before the gateway
- [ ] SIEM ingests `AuditEvent` entries (verify a sample event lands there)
- [ ] Counsel has reviewed `docs/user/COMPLIANCE.md` and your DPA template

---

## Path B — I need HIPAA

Follow this path if you handle Protected Health Information (PHI) for a covered entity or its business associate.

HIPAA compliance is Path A **plus** three additional pieces:

### B.1 — Enable the HIPAA profile

```yaml
jaiclaw:
  compliance:
    profile: hipaa    # or 'both' if you also need GDPR
```

This turns on everything from Path A plus:

- `baa-warnings` — WARN emitted at LLM-call time when a PHI-processing tenant lands on a non-BAA-eligible provider
- `prompt-redaction` — `PromptRedactor` runs before LLM dispatch to mask SSN / MRN / phone / email / DOB / credit card patterns

### B.2 — Mark PHI-processing tenants

For every tenant handling PHI, set the metadata:

```java
Map<String, Object> metadata = Map.of(
    TenantContext.KEY_LAWFUL_BASIS, "contract",
    TenantContext.KEY_RETENTION_DAYS, 2190,     // HIPAA §164.316(b)(2) requires ≥ 6 years for audit
    TenantContext.KEY_PHI_PROCESSING, true,     // ← Drives BAA warnings + redaction
    TenantContext.KEY_DATA_RESIDENCY, "us-east"
);
```

### B.3 — Route through a BAA-eligible LLM provider

The framework ships a BAA-eligible catalog at `io.jaiclaw.config.BaaEligibleProviders`:

**BAA-eligible by default:**
- `bedrock` (Anthropic + others via AWS Bedrock with the AWS BAA)
- `azure-openai` (with a signed contract rider)
- `vertex-ai` (Google models via GCP with the GCP BAA)
- `ollama` (self-hosted — you control the data plane)

**Not BAA-eligible by default:**
- `anthropic` (direct `api.anthropic.com`)
- `openai` (direct `api.openai.com`)
- `gemini`, `google-genai`, `minimax`, `deepseek`, `mistral`, `oci-genai` (direct public APIs)

Reconfigure your LLM provider to route through a BAA-eligible endpoint:

```yaml
jaiclaw:
  models:
    providers:
      bedrock:
        # ... your bedrock config
spring:
  ai:
    bedrock:
      aws:
        region: us-east-1
      # etc.
```

**If you have a signed BAA with an otherwise-ineligible provider**, override the default:

```yaml
jaiclaw:
  models:
    providers:
      anthropic:
        baa-eligible: true    # You have a signed BAA with Anthropic
```

Restart. Verify the warning is gone:

```bash
# Look for this WARN in the log — it should NOT appear:
# HIPAA warning: tenant 'acme' is marked hipaa.phi_processing=true but
# provider 'anthropic' is not BAA-eligible in this deployment.
```

### B.4 — Verify prompt redaction is running

Send a test message with a fake SSN and check the LLM sees the redacted form:

```java
@Autowired PromptRedactor redactor;

TenantContext ctx = /* your PHI-marked context */;
PromptRedactor.RedactionResult r = redactor.redact("SSN 123-45-6789", ctx);
assert r.redactedContent().equals("SSN [REDACTED-SSN]");
assert r.matches().size() == 1;
assert r.matches().get(0).pattern().equals("ssn");
```

Or, since redaction happens inside `AgentRuntime` when compliance is on, monitor the audit trail — the `dataCategories` field on `model.inference.request` events will show `redacted.ssn` etc. entries.

**Redaction is best-effort.** Regex-based patterns will miss free-form PHI. Do NOT treat this as your only PHI safeguard — the BAA with your LLM provider is the primary protection.

### B.5 — Enable at-rest encryption (recommended, not required by HIPAA but strongly advised for §164.312(a)(2)(iv))

Not auto-wired — you supply the 32-byte key from your SecretsProvider:

```java
@Bean
public FieldEncryptor fieldEncryptor(SecretsResolver secrets) {
    String keyBase64 = secrets.get("JAICLAW_ENCRYPTION_KEY");
    byte[] key = Base64.getDecoder().decode(keyBase64);
    return new AesGcmFieldEncryptor(key);
}

@Bean
@Primary
public TranscriptStore encryptedTranscriptStore(
        FileTranscriptStore backing, FieldEncryptor enc) {
    return new EncryptedTranscriptStore(backing, enc);
}

@Bean
@Primary
public AuditLogger encryptedAuditLogger(FileAuditLogger backing, FieldEncryptor enc) {
    return new EncryptedAuditLogger(backing, enc);
}
```

Provision the key:

```bash
# Generate a fresh key (do this ONCE and store securely — losing it means losing the ciphertext)
openssl rand -base64 32
# → e.g. "kJgN2vRHXf...=" — 44 chars base64 = 32 bytes decoded

# Store in your vault / 1Password / K8s secret with a rotation policy
kubectl -n jaiclaw create secret generic jaiclaw-encryption-key \
    --from-literal=JAICLAW_ENCRYPTION_KEY=kJgN2vRHXf...=
```

**Key rotation runbook** (essential — write this down before enabling encryption):

1. Generate the new key `K2`.
2. Deploy a `KeyRotatingFieldEncryptor` that decrypts with both `K1` and `K2` but encrypts only with `K2`.
3. Run a batch job that reads every transcript + audit event through the rotating encryptor and writes it back. All ciphertext is now under `K2`.
4. Retire `K1` (but keep a copy in cold storage for regulatory recovery).

If you can't commit to a rotation runbook, don't enable encryption yet. Losing the key means losing the data — worse than never encrypting.

### B.6 — Enable tamper-evident audit

Wrap your `AuditLogger` bean with `HashChainedAuditLogger`:

```java
@Bean
@Primary
public AuditLogger chainedAuditLogger(FileAuditLogger backing) {
    return new HashChainedAuditLogger(backing);
    // If you're also encrypting: layer the two decorators —
    // return new HashChainedAuditLogger(new EncryptedAuditLogger(backing, encryptor));
}
```

Add a startup + scheduled verification:

```java
@Scheduled(cron = "0 0 * * * *")   // Hourly
public void verifyAuditChain() {
    for (String tenantId : tenantRegistry.listAllTenants()) {
        HashChainedAuditLogger.IntegrityReport report =
                ((HashChainedAuditLogger) auditLogger).verifyChain(tenantId);
        if (!report.valid()) {
            log.error("Audit chain break for tenant {}: {} at index {}",
                    tenantId, report.reason(), report.brokenAt());
            // The break has already emitted audit.integrity_violation; your SIEM alerts on that.
        }
    }
}
```

### B.7 — Wire anomaly detection

```java
@Bean
public MassReadDetector massReadDetector(List<AuditLogger> loggers) {
    return new MassReadDetector(loggers, 20);   // 20-subject threshold
}

@Scheduled(cron = "0 */15 * * * *")   // Every 15 min
public void detectAnomalies() {
    for (String tenantId : tenantRegistry.listAllTenants()) {
        Instant now = Instant.now();
        massReadDetector.detect(tenantId, now.minus(15, ChronoUnit.MINUTES), now);
        // Detected anomalies land as security.event audit entries → SIEM
    }
}
```

Additional detectors (bulk export, off-hours access, cross-tenant probe) are yours to implement against the `AnomalyDetector` SPI — the framework ships only the one reference impl.

### B.8 — HIPAA verification checklist

- [ ] Path A checklist complete (HIPAA includes GDPR baseline)
- [ ] `/actuator/env/jaiclaw.compliance.effective.baa-warnings` returns `true`
- [ ] `/actuator/env/jaiclaw.compliance.effective.prompt-redaction` returns `true`
- [ ] LLM route is BAA-eligible (or has an explicit `baa-eligible: true` override with a signed BAA)
- [ ] Prompt redaction verified on a test message with a fake SSN
- [ ] At-rest encryption enabled OR the risk of unencrypted storage is documented + accepted
- [ ] Key rotation runbook exists
- [ ] `HashChainedAuditLogger` is wrapping the primary `AuditLogger`
- [ ] `verifyChain` is scheduled + alerting on `audit.integrity_violation`
- [ ] At least one anomaly detector is scheduled
- [ ] `data.retention_days ≥ 2190` for PHI-processing tenants (HIPAA §164.316(b)(2))
- [ ] BAA in place with your LLM provider (signed, filed, dated)
- [ ] Counsel has reviewed the deployment against §164.308 / .310 / .312 / .316

---

## Path C — I need both (GDPR + HIPAA)

```yaml
jaiclaw:
  compliance:
    profile: both
```

Complete both checklists (§ A.8 and § B.8). No shortcuts — the two frameworks overlap in intent but diverge in obligations (e.g. GDPR Art. 17 erasure vs HIPAA §164.316(b)(2) retention floor — the tenant metadata must reconcile these per-subject, not blanket).

---

## Common gotchas

### "Nothing is happening after I set the profile"

Check `/actuator/env` — if the effective flags aren't set, the module isn't on the classpath. Add to your POM:

```xml
<dependency>
    <groupId>io.jaiclaw</groupId>
    <artifactId>jaiclaw-compliance</artifactId>
    <version>${jaiclaw.version}</version>
</dependency>
```

### "The gateway won't start after enabling the profile"

Most likely `RequireHttpsStartupGuard` is refusing to start on a non-loopback bind without TLS. Two options:

- **Correct fix**: put a TLS-terminating proxy in front (Caddy, nginx, ALB, Ingress with cert-manager).
- **Bench-only fix**: `jaiclaw.compliance.require-https: false` — but log-flag this as a known deviation.

### "My audit stream is huge — how do I keep costs down?"

`AuditingChatModel` writes an audit event per LLM call. For a busy deployment this is a lot. Options:

- **Sample** the audit stream at your ingestion layer (Vector, Fluent Bit): keep 100% of `outcome=FAILURE` + `outcome=DENIED`, sample `SUCCESS` at 1%.
- **Filter fields** — the `details` map on `SUCCESS` events carries duration + messageCount; drop the `details` map at ingestion for `SUCCESS` events.
- **Do NOT drop the events entirely** — GDPR Art. 30 needs a complete record of processing activities.

### "Prompt redaction is masking things it shouldn't"

`RegexPromptRedactor` is intentionally opinionated. Override with your own:

```java
@Bean
@Primary
public PromptRedactor customRedactor() {
    Map<String, Pattern> patterns = new LinkedHashMap<>();
    patterns.put("ssn", Pattern.compile(/* your stricter SSN regex */));
    patterns.put("mrn", Pattern.compile(/* your MRN format */));
    // Skip email + DOB entirely if they don't apply to your data flow
    return new RegexPromptRedactor(patterns, false);
}
```

### "How do I test my HIPAA config without shipping to real users?"

Spin up a test tenant, mark it `hipaa.phi_processing=true`, send it a message containing fake PHI (`SSN 111-22-3333`, `MRN 1234567890`), and verify:

1. The audit trail shows the redacted content, not the original
2. The `dataCategories` field includes `redacted.ssn`, `redacted.mrn`
3. If routed through a non-BAA provider, the WARN appears in the log
4. The DSAR export path returns this test conversation
5. The DSAR erasure path removes it (and the erasure event survives)

If all five pass, ship. If any fails, dig in — a partial pass gives false confidence.

---

## Related

- [COMPLIANCE.md](COMPLIANCE.md) — reference guide (what & why)
- [MIGRATION-0.9.3.md](../MIGRATION-0.9.3.md) — upgrade path from 0.9.2
- [PRODUCTION-DEPLOYMENT.md § 9.1](PRODUCTION-DEPLOYMENT.md) — deployment topology for compliance-aware setups
- [OPERATIONS.md § Compliance](OPERATIONS.md) — operator runbook
- [dev/COMPLIANCE-IMPLEMENTATION-PLAN.md](../dev/COMPLIANCE-IMPLEMENTATION-PLAN.md) — internal plan document
- [releases/release-0.9.3.md](../../releases/release-0.9.3.md) — release notes
