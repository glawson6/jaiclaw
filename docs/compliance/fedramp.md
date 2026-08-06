# FedRAMP (Federal Risk & Authorization Management Program)

## What it is

FedRAMP is the standardized security-authorization program for cloud services used by U.S. federal agencies. Impact tiers:

- **Low** — 156 NIST 800-53 controls; loss of confidentiality/integrity/availability has limited impact
- **Moderate** — 323 controls; serious adverse effect
- **High** — 421 controls; severe or catastrophic effect

All FedRAMP baselines are subsets or extensions of NIST SP 800-53 Rev. 5 with FedRAMP-specific parameters (e.g., specific audit-retention windows, MFA requirements).

## What JaiClaw contributes

JaiClaw is a **framework library**, not a cloud service. FedRAMP authorization is a property of a **deployed CSO** (Cloud Service Offering), which typically has three parties:

1. **Cloud Service Provider (CSP)** — the adopter operating the JaiClaw-based service
2. **Underlying Cloud** — AWS GovCloud, Azure Government, GCP Assured Workloads, etc.
3. **JaiClaw** — the framework layer

This three-way inheritance is standard for framework-built cloud services. JaiClaw's role is to **not block a FedRAMP ATO** and to provide the technical control primitives an adopter needs.

### Three-way control inheritance table

Legend:
- **JC** — JaiClaw framework contributes directly
- **AD** — Adopter's application layer + configuration
- **CSP** — Underlying cloud provider (already FedRAMP-authorized)

| Control area | JC | AD | CSP | JaiClaw evidence |
|---|---|---|---|---|
| Encryption at rest | ✅ | ✅ | ✅ | `AesGcmFieldEncryptor` + `EncryptedTranscriptStore` + `EncryptedAuditLogger`; adopter supplies key via `SecretsProvider`; CSP encrypts underlying volumes |
| Encryption in transit | ✅ | ✅ | ✅ | `RequireHttpsStartupGuard` refuses non-loopback plaintext; adopter provides certs; CSP provides TLS-terminating load balancer |
| Access control | ✅ | ✅ | — | `TenantGuard`, JWT/API-key auth, `RateLimitFilter`; adopter integrates with SSO/IAM |
| Audit logging | ✅ | ✅ | ✅ | `AuditLogger` SPI + `FileAuditLogger` + `HashChainedAuditLogger`; adopter routes to SIEM; CSP provides CloudTrail-equivalent |
| Tamper-evident audit | ✅ | — | — | `HashChainedAuditLogger` with per-tenant SHA-256 chain + `verifyChain()` |
| Multi-tenant isolation | ✅ | ✅ | ✅ | `TenantGuard` in MULTI mode fails closed; adopter manages tenant lifecycle; CSP provides VPC/subnet isolation |
| LLM-provider gating | ✅ | ✅ | — | `FedRampWarningChatModelDecorator` warns on non-FedRAMP-authorized providers (opt-in via `jaiclaw.compliance.effective.fedramp-warnings=true`) |
| Boundary definition | — | ✅ | ✅ | Adopter defines authorization boundary in SSP |
| Continuous monitoring | ✅ | ✅ | ✅ | Framework emits structured audit events; adopter runs SIEM + quarterly control reviews; CSP submits monthly ConMon package |
| Incident response | — | ✅ | ✅ | Framework emits `security.*` audit events; adopter runs IR runbook; CSP has IR SLA |
| Configuration mgmt | ✅ | ✅ | — | Opt-in modules, `spring.autoconfigure.exclude` support, versioned pom |
| Vulnerability mgmt | ✅ | ✅ | ✅ | OWASP dep-check plugin configured; adopter runs scans; CSP maintains OS patching |
| Physical controls | — | — | ✅ | Entirely CSP responsibility (already FedRAMP-authorized) |

## Compliance profile support

JaiClaw ships a `FEDRAMP_MODERATE` profile that flips defaults appropriately:

```yaml
jaiclaw:
  compliance:
    profile: fedramp-moderate
```

This activates:
- `require-https: true` (HTTPS mandatory)
- `retention-enforcement: true` (audit retention purge active)
- `audit-chat-client: true` (every LLM call audited)
- `fedramp-warnings: true` (FedRAMP-provider gating decorator active)
- `fips-enforced: true` (FIPS-provider startup check active — see [fips-140-3.md](fips-140-3.md))

Individual flags remain overridable — an operator can set any single flag to `false` if their deployment has a compensating control.

## TenantContext metadata

Each request's tenant context can carry a FedRAMP impact level:

```java
TenantContext ctx = TenantContext.builder()
    .tenantId("agency-alpha")
    .metadata("fedramp.impact_level", "moderate")  // low | moderate | high
    .metadata("data.residency_required", "us-gov-east")
    .build();
```

`FedRampWarningChatModelDecorator` reads `fedramp.impact_level`. If it's `moderate` or `high` and the resolved LLM provider is not marked FedRAMP-authorized, a WARN is logged. Adopters can extend the decorator into a hard refuser (throw) by subclassing.

## How to enable / disable

**Default:** off. Nothing FedRAMP-specific activates unless the operator opts in.

**Enable — via profile (recommended):**
```yaml
jaiclaw:
  compliance:
    profile: fedramp-moderate  # turns on: require-https, retention-enforcement,
                               # audit-chat-client, fips-enforced, fedramp-warnings
```

**Enable — individual flags (finer control):**
```yaml
jaiclaw:
  compliance:
    fedramp-warnings: true    # turn on the FedRAMP provider decorator only
    fips-enforced: true       # turn on the FIPS posture check only
    # (other compliance flags stay at profile default / off)
```

**Disable / override (turn a profile flag off):**
```yaml
jaiclaw:
  compliance:
    profile: fedramp-moderate
    fips-enforced: false      # bench deployment without FIPS — explicit override
```

**Fully off:**
```yaml
jaiclaw:
  compliance:
    profile: none    # (default; can be omitted entirely)
```

**Per-request FedRAMP labelling** (independent of profile — controls the decorator's trigger condition):
```java
TenantContext ctx = TenantContext.builder()
    .metadata("fedramp.impact_level", "moderate")  // low | moderate | high | null
    .build();
```

## What's adopter responsibility

- **CSP responsibilities** — operating the service, submitting the ATO package, ConMon reports, POA&M management, 3PAO relationship
- **Boundary definition** — the SSP defines what's in and out of scope
- **Underlying cloud selection** — AWS GovCloud (already FedRAMP High authorized), Azure Government (High), etc.
- **SSO integration** — mapping FedRAMP-required MFA to your identity provider
- **FedRAMP-authorized LLM providers** — as of writing, few LLM providers have direct FedRAMP authorizations; adopters typically wrap a self-hosted LLM (Llama variants) or an on-prem endpoint. Adopters must configure `jaiclaw.models.providers.<provider>.fedramp-authorized=true` on providers they've verified

## What's out of scope

- Any specific FedRAMP JAB or Agency ATO submission
- 3PAO assessment
- FedRAMP PMO relationship
- Continuous monitoring reports (adopter generates and submits)

## How to verify

**Check the FedRAMP-warning decorator is wired:**
```bash
# Boot with fedramp-moderate profile
JAICLAW_COMPLIANCE_PROFILE=fedramp-moderate \
    ./mvnw spring-boot:run -pl :jaiclaw-gateway-app

# In the logs, look for:
# "FedRampWarningChatModelDecorator loaded"
# "ComplianceEnvironmentPostProcessor: profile=fedramp-moderate, effective flags: ..."

# Manually trigger a warning by marking a request as fedramp-moderate:
# (in your test code)
TenantContextHolder.set(TenantContext.builder()
    .metadata("fedramp.impact_level", "moderate").build());
# Then invoke an LLM call — WARN log should appear if provider isn't marked authorized
```

**Verify effective properties:**
```bash
curl http://localhost:8080/actuator/env | jq '."propertySources"[] | select(.name | contains("jaiclawComplianceEffective"))'
# Should show require-https, fedramp-warnings, fips-enforced all set to true
```

**Cross-check against the assessment report:**
- Read the FedRAMP section of `docs/FEDERAL-COMPLIANCE-ASSESSMENT-2026-08-06.md`
- Then read `nist-800-53.md` in this directory — every FedRAMP control ultimately maps to a NIST 800-53 control

## Related code files

- `extensions/jaiclaw-compliance/src/main/java/io/jaiclaw/compliance/fedramp/FedRampWarningChatModelDecorator.java`
- `extensions/jaiclaw-compliance/src/main/java/io/jaiclaw/compliance/ComplianceProfile.java` (FEDRAMP_MODERATE enum value)
- `extensions/jaiclaw-compliance/src/main/java/io/jaiclaw/compliance/ComplianceProperties.java` (fedrampWarnings field)
- `core/jaiclaw-core/src/main/java/io/jaiclaw/core/tenant/TenantContext.java` (KEY_FEDRAMP_IMPACT)
- Plus every file listed in [nist-800-53.md](nist-800-53.md) — NIST 800-53 is the FedRAMP source

## References

- FedRAMP: [https://www.fedramp.gov/](https://www.fedramp.gov/)
- FedRAMP Moderate Baseline: [https://www.fedramp.gov/assets/resources/documents/FedRAMP_Rev_5_Moderate_Baseline.xlsx](https://www.fedramp.gov/assets/resources/documents/FedRAMP_Rev_5_Moderate_Baseline.xlsx)
- FedRAMP Marketplace (CSP + 3PAO listings): [https://marketplace.fedramp.gov/](https://marketplace.fedramp.gov/)
- FedRAMP Authorized LLM providers: check current status at [https://marketplace.fedramp.gov/products?status=Compliant&category=AI](https://marketplace.fedramp.gov/products)
