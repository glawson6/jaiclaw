# Migration Guide — JaiClaw 0.9.2 → 0.9.3

**Nothing breaks.** Every 0.9.3 change is additive: new SPI methods have default implementations, extended records have backward-compat constructors, and the new `jaiclaw-compliance` module is opt-in. An adopter can bump the version and redeploy without touching code or config.

This guide is for adopters who want to **opt into** the new compliance surface (GDPR + HIPAA) that 0.9.3 unlocks.

## 1. Bump the version

Update your BOM import or root property:

```xml
<jaiclaw.version>0.9.3</jaiclaw.version>
```

## 2. Pull the compliance extension onto the classpath

Add one dependency (the compliance module is a `jar` in `extensions/`, not a starter):

```xml
<dependency>
    <groupId>io.jaiclaw</groupId>
    <artifactId>jaiclaw-compliance</artifactId>
    <version>${jaiclaw.version}</version>
</dependency>
```

Or via the starter, once shipped:

```xml
<dependency>
    <groupId>io.jaiclaw</groupId>
    <artifactId>jaiclaw-starter-compliance</artifactId>
    <version>${jaiclaw.version}</version>
</dependency>
```

`jaiclaw-compliance` has `provided`-scope dependencies on `spring-ai-model`, `spring-ai-client-chat`, `reactor-core`, and `spring-web`. These are already on the classpath of any project using `jaiclaw-spring-boot-starter` + an LLM provider starter, so there's no additional footprint.

**Redeploy at this step and nothing changes** — the module doesn't wire any beans until you set a profile in step 3.

## 3. Pick a compliance profile

Add one property to `application.yml`:

```yaml
jaiclaw:
  compliance:
    profile: hipaa    # one of: none | gdpr | hipaa | both  (default: none)
```

| Profile | Flags turned on |
|---|---|
| `none` (default) | nothing — the module is dead weight, safe to leave on classpath |
| `gdpr` | require-https, retention-enforcement, audit-chat-client |
| `hipaa` | require-https, retention-enforcement, audit-chat-client, baa-warnings, prompt-redaction |
| `both` | union of GDPR + HIPAA |

Individual flags override the profile default in either direction. For example, to run the HIPAA profile on a bench deployment where you can't front the gateway with TLS:

```yaml
jaiclaw:
  compliance:
    profile: hipaa
    require-https: false      # explicit opt-out; requires you MUST have TLS terminating upstream
```

## 4. Populate per-tenant metadata

Some compliance behavior is per-tenant. Set on `TenantContext.getMetadata()` at the point where you construct the context (typically your onboarding tool or JWT resolver):

| Metadata key | Type | Purpose |
|---|---|---|
| `gdpr.lawful_basis` | string | Written to every `AuditEvent.lawfulBasis` for this tenant (`consent`, `contract`, `legal_obligation`, `vital_interests`, `public_task`, `legitimate_interests`) |
| `data.retention_days` | int | Retention TTL — enforced by `RetentionEnforcementService`. HIPAA §164.316(b)(2) requires audit ≥ 6 years (2190 days) |
| `data.restriction_flags` | set of string | GDPR Art. 18 processing restrictions (`no_llm_calls`, `no_memory_writes`) |
| `data.residency_required` | string | Required residency (`eu-west`, `us-east`) — LLM + storage routing should refuse cross-region dispatch |
| `hipaa.phi_processing` | boolean | Drives BAA-eligible provider enforcement + `PromptRedactor` activation |
| `gdpr.consent_token` | string | Reference to a `ConsentManager` record. Written to every `AuditEvent.consentToken` |

The framework provides typed accessors on `TenantContext` (default methods since T1-1) so downstream code reads these without touching the raw map:

```java
tenantContext.getLawfulBasis();       // e.g. "contract"
tenantContext.getRetentionDays();     // e.g. 2190
tenantContext.isPhiProcessing();      // boolean
tenantContext.hasRestriction("no_llm_calls");
```

## 5. Verify the runtime is doing what you think

Effective flags are surfaced at `jaiclaw.compliance.effective.*`. Inspect via the `/actuator/env` endpoint or a log at startup:

```
jaiclaw.compliance.effective.profile=hipaa
jaiclaw.compliance.effective.require-https=true
jaiclaw.compliance.effective.retention-enforcement=true
jaiclaw.compliance.effective.audit-chat-client=true
jaiclaw.compliance.effective.baa-warnings=true
jaiclaw.compliance.effective.prompt-redaction=true
```

If your LLM route is not BAA-eligible and the tenant is marked `hipaa.phi_processing=true`, you'll see a `WARN` at LLM-call time:

```
HIPAA warning: tenant 'acme' is marked hipaa.phi_processing=true but
provider 'anthropic' is not BAA-eligible in this deployment.
```

Override with either:

```yaml
jaiclaw:
  models:
    providers:
      anthropic:
        baa-eligible: true    # you have a signed BAA with Anthropic
```

or route through a BAA-eligible endpoint (Bedrock, Azure OpenAI, Vertex AI).

## 6. Wire the Tier 2 + Tier 3 SPIs (optional, for full adoption)

The auto-config registers reference implementations for most SPIs when the profile is active. Adopters can override any of them with a `@Bean` of the same type — the auto-config uses `@ConditionalOnMissingBean`.

### Data-subject request handling (Art. 15 / 17 / 20)

The `GdprController` at `/api/gdpr/*` exposes export + erasure via HTTP. **Front this with a rate-limiter + auth layer** — the controller resolves tenant scope from `TenantContextHolder` (rejects with 403 if unset) but does no role-based authorization.

### At-rest encryption (Tier 2 T2-4)

Not auto-wired — you supply the 32-byte key from a `SecretsProvider`:

```java
@Bean
FieldEncryptor fieldEncryptor(SecretsResolver secrets) {
    return new AesGcmFieldEncryptor(secrets.get("JAICLAW_ENCRYPTION_KEY").getBytes(StandardCharsets.UTF_8));
}

@Bean
TranscriptStore encryptedTranscriptStore(TranscriptStore backing, FieldEncryptor enc) {
    return new EncryptedTranscriptStore(backing, enc);
}
```

**Losing the key means losing the ciphertext.** Maintain a key-rotation runbook + backup-encryption-key pattern before enabling in production.

### Tamper-evident audit (Tier 2 T2-6)

Wrap your existing `AuditLogger`:

```java
@Bean
AuditLogger auditLogger(FileAuditLogger backing) {
    return new HashChainedAuditLogger(backing);
}
```

Call `verifyChain(tenantId)` at startup and on a scheduled tick. Chain breaks emit an `audit.integrity_violation` event.

### Anomaly detection (Tier 3 T3-3)

Register your detector as a `@Bean` and schedule its `detect(...)` call:

```java
@Bean
MassReadDetector massReadDetector(List<AuditLogger> loggers) {
    return new MassReadDetector(loggers, 20);  // 20-subject threshold
}
```

The framework doesn't auto-schedule; that's a deployment concern.

## 7. Production checklist

Before enabling any profile above `none` in production:

- [ ] TLS terminating layer in front of the gateway (or `require-https: false` with explicit acknowledgement)
- [ ] SIEM ingesting the audit stream (Splunk, Datadog, ELK)
- [ ] Signed BAA in place with your LLM provider, OR provider is default BAA-eligible per the framework catalog
- [ ] Encryption key (if T2-4 enabled) is in a real secrets vault, with backup + rotation runbook
- [ ] Consent + objection stores replaced with durable impls if you're processing consent at scale (reference impls are in-memory)
- [ ] `PromptRedactor` deployment tested against your real prompt shapes — regex-based redaction is best-effort, not a HIPAA safeguard on its own
- [ ] Counsel review of `docs/user/COMPLIANCE.md` complete

## What's still the operator's responsibility

The framework provides the substrate. Legal artifacts (DPA, RoPA, breach notifications), BAA negotiation, TLS termination + cipher suite selection, IAM lifecycle, MFA at the application boundary, and SIEM alert routing all remain deployer responsibilities. See [docs/user/COMPLIANCE.md § What's still the operator's responsibility](user/COMPLIANCE.md) for the full list.

## Related documentation

- [docs/user/COMPLIANCE.md](user/COMPLIANCE.md) — full compliance reference
- [docs/user/PRODUCTION-DEPLOYMENT.md § 9.1](user/PRODUCTION-DEPLOYMENT.md) — compliance-aware deployment topology
- [docs/dev/COMPLIANCE-IMPLEMENTATION-PLAN.md](dev/COMPLIANCE-IMPLEMENTATION-PLAN.md) — full plan document (Tier 1 + Tier 2 + Tier 3 spec)
- [releases/release-0.9.3.md](../releases/release-0.9.3.md) — full 0.9.3 release notes
