# FISMA (Federal Information Security Modernization Act)

## What it is

The Federal Information Security Modernization Act of 2014 (44 U.S.C. § 3541 et seq.) requires federal agencies to develop, document, and implement an agency-wide information security program. FISMA mandates that federal information systems be categorized by impact level per **FIPS 199** (Low / Moderate / High) and secured with a control baseline drawn from **NIST SP 800-53**.

FISMA is a **program-level compliance regime**. Every substantive technical control comes from NIST 800-53. FISMA's contribution beyond NIST is the categorization procedure, the program documentation requirements, and the reporting cadence to OMB.

## What JaiClaw contributes

JaiClaw's relationship to FISMA is:

1. **Does not block FISMA compliance** at any impact level (Low, Moderate, or High)
2. **Baseline security posture** aligns with FISMA Moderate for the majority of technical controls (AC, AU, IA, SC, SI families — see [nist-800-53.md](nist-800-53.md) for the family-by-family table)
3. **Compliance profiles** flip on the settings a FISMA baseline expects:
   - `hipaa`, `both`, `fedramp-moderate`, `cmmc-l2` all set: `require-https=true`, `retention-enforcement=true`, `audit-chat-client=true`
   - `fedramp-moderate` and `fips` additionally set `fips-enforced=true` (see [fips-140-3.md](fips-140-3.md))
4. **Structured audit trail** feeds an adopter's FISMA reporting workflow — audit events carry compliance metadata (lawful basis, data categories, recipients, retention days) needed for OMB Circular A-130 reports

### Recommended profile for FISMA-authorized deployments

For a FISMA Moderate deployment, the closest single-flag configuration is:

```yaml
jaiclaw:
  compliance:
    profile: fedramp-moderate  # NIST 800-53 Moderate baseline, HTTPS + audit + FIPS enforced
```

FISMA-authorized deployments processing PHI additionally set:
```yaml
jaiclaw:
  compliance:
    profile: both  # gdpr + hipaa flags active
    # override for FIPS on top:
    fips-enforced: true
```

## FIPS 199 categorization

FIPS 199 categorization is the first step in a FISMA assessment. It's fundamentally a **data-classification exercise** and is entirely the adopter's responsibility. However, JaiClaw's `TenantContext` metadata makes it easy to encode the categorization at request time:

```java
TenantContext ctx = TenantContext.builder()
    .tenantId("agency-alpha")
    .metadata("fisma.impact_category", "moderate-moderate-moderate")  // C-I-A tuple
    .metadata("fedramp.impact_level", "moderate")
    .metadata("data.categories", Set.of("federal_info", "cui"))
    .build();
```

The audit trail preserves this metadata on every `AuditEvent`, providing an auditable record of what impact-level workloads were processed when.

## How to enable / disable

**Default:** off. FISMA-supporting flags inherit from the compliance profile — nothing activates until the operator opts in.

**Enable — via profile (recommended for FISMA Moderate):**
```yaml
jaiclaw:
  compliance:
    profile: fedramp-moderate   # closest single-flag mapping to FISMA Moderate:
                                # require-https + retention + audit + FIPS + fedramp-warnings
```

**Enable — for FISMA + PHI processing:**
```yaml
jaiclaw:
  compliance:
    profile: both              # gdpr + hipaa flags
    fips-enforced: true        # add FIPS enforcement on top
```

**Fully off:**
```yaml
jaiclaw:
  compliance:
    profile: none    # (default)
```

## What's adopter responsibility

- **The agency information security program itself** — governance, roles, incident response
- **FIPS 199 categorization** of the deployed system (data confidentiality, integrity, availability impact levels)
- **The System Security Plan (SSP)** — the document your assessor reads
- **The A&A (Assessment and Authorization) process** with the agency AO (Authorizing Official)
- **Continuous monitoring** — audit-log review, control-effectiveness testing, POA&M management
- **Annual reporting to OMB** — the FISMA annual report has specific data requirements
- **Training program** — the AT control family is entirely program-level (adopter's HR/training team)

## What's out of scope

- Automated SSP generation — JaiClaw provides the technical control evidence, not the SSP template
- Direct integration with a specific agency's GRC platform (e.g., CSAM, eMASS, RSAM, Xacta) — the audit-log format is documented for adopter integration
- FISMA metrics collection for OMB reporting — the audit log has the data; adopter aggregates

## How to verify

**Same technical verification as NIST 800-53 — FISMA inherits from that catalog.** See [nist-800-53.md](nist-800-53.md) for the full family-by-family check list.

**FISMA-specific verification:**

```bash
# Confirm the fedramp-moderate profile activates the right FISMA-appropriate flags
JAICLAW_COMPLIANCE_PROFILE=fedramp-moderate \
    ./mvnw spring-boot:run -pl :jaiclaw-gateway-app

# Boot log should include:
#   ComplianceEnvironmentPostProcessor: profile=fedramp-moderate, effective flags:
#     require-https=true, retention-enforcement=true, audit-chat-client=true,
#     fedramp-warnings=true, fips-enforced=true

# The audit trail should include structured metadata:
curl http://localhost:8080/actuator/audit | jq '.events[0].details'
# Should include lawfulBasis, dataCategories, recipients, retentionDays fields
```

## Related code files

FISMA-relevant code is entirely a subset of the NIST 800-53 file list — see [nist-800-53.md § Related code files](nist-800-53.md#related-code-files).

FISMA-specific configuration:
- `extensions/jaiclaw-compliance/src/main/java/io/jaiclaw/compliance/ComplianceProfile.java` (FEDRAMP_MODERATE profile)
- `extensions/jaiclaw-compliance/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

## References

- FISMA 2014: [https://csrc.nist.gov/Projects/risk-management/fisma-background](https://csrc.nist.gov/Projects/risk-management/fisma-background)
- FIPS 199 (Standards for Security Categorization): [https://nvlpubs.nist.gov/nistpubs/FIPS/NIST.FIPS.199.pdf](https://nvlpubs.nist.gov/nistpubs/FIPS/NIST.FIPS.199.pdf)
- NIST SP 800-60 (Guide for Mapping Types of Information and Information Systems to Security Categories): [https://csrc.nist.gov/publications/detail/sp/800-60/vol-1-rev-1/final](https://csrc.nist.gov/publications/detail/sp/800-60/vol-1-rev-1/final)
- OMB Circular A-130: [https://obamawhitehouse.archives.gov/omb/circulars_a130_a130trans4](https://obamawhitehouse.archives.gov/omb/circulars_a130_a130trans4)
