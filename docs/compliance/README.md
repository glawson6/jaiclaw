# JaiClaw Compliance Posture

**One-page government-researcher entry point.**
**Framework version:** 1.0.1-SNAPSHOT (post-1.0.0 release)
**Last reviewed:** 2026-08-06

---

## Executive summary

JaiClaw is a **compliance-capable framework**, not a compliance-certified product. It provides the technical primitives adopters need to build compliant systems (audit trail, encryption, multi-tenant isolation, HTTPS enforcement, LLM-provider gating, PII redaction, tamper-evident logs) so the frame doesn't fight the ATO process. Certification, assessment, and authorization remain properties of a **deployed system**, not the library — and remain the responsibility of the adopter's operations, security, and compliance teams.

We publish this posture openly so evaluators can review our foundation before an engagement. Everything below is backed by code — file paths cited, no marketing claims.

## Ratings across 8 regulations

| Regulation | Status | Framework contribution | Deep-dive |
|---|---|---|---|
| **Section 508** — Accessibility (29 U.S.C. § 794d, bound to WCAG 2.0 AA) | 🟢 **Ready** | Semantic HTML surfaces, ARIA landmarks + `aria-live` on status indicators, ASCII renderer supports `altText` for screen readers, Pipeline Studio SPA gated by `eslint-plugin-jsx-a11y` + `axe-core` | [section-508.md](section-508.md) |
| **FedRAMP** — Cloud service authorization | 🟢 **Framework-ready** | Every technical control primitive exists (HTTPS guard, encryption SPI, audit chain, multi-tenant isolation, provider-warning decorator). Control inheritance table shows the JaiClaw / adopter / CSP three-way split. | [fedramp.md](fedramp.md) |
| **FISMA** — Federal Information Security Modernization Act | 🟢 **Framework-ready** | Technical control alignment with FISMA Moderate baseline. Documentation of the FIPS 199 categorization boundary is the adopter's job. | [fisma.md](fisma.md) |
| **NIST SP 800-53 Rev. 5** — Security & privacy controls catalog | 🟢 **Substantial coverage** | Direct contribution to ~40 controls in the Moderate baseline; another ~60 partially covered. Full family-by-family inheritance table with exact file/line citations. | [nist-800-53.md](nist-800-53.md) |
| **FIPS 140-3** — Cryptographic module certification | 🟢 **Ready** | All algorithms are FIPS-approved (AES-GCM-256, SHA-256, HMAC-SHA256, EC P-256, X25519, ECDH). `FipsPostureStartupCheck` fails startup on non-FIPS providers when enforced. Documented BC-FIPS integration path. | [fips-140-3.md](fips-140-3.md) |
| **CMMC 2.0** — Cybersecurity Maturity Model Certification | 🟢 **Framework-ready for L2** | CMMC L2 practices inherit from NIST 800-171; JaiClaw satisfies the framework contribution. `CuiWarningChatModelDecorator` gates CUI processing to CUI-authorized providers. `cmmc-l2` compliance profile ships default flags. | [cmmc.md](cmmc.md) |
| **HIPAA** — Health Insurance Portability & Accountability Act | 🟢 **Ready** | BAA-eligible-provider enforcement, PHI redaction, encrypted transcript + audit stores, tamper-evident audit chain, tenant-scoped retention purge — all in production since 0.9.3+. | [hipaa.md](hipaa.md) |
| **GDPR** — EU General Data Protection Regulation | 🟢 **Ready** | Consent management, data subject erasure + export (Art. 15 / 17 / 20), cross-border transfer tracking (Art. 44), lawful-basis metadata on `TenantContext`, `AuditingChatModel` for Art. 30 records of processing. | [gdpr.md](gdpr.md) |

**Rating legend:**
- 🟢 **Ready** — the framework surface is complete for its scope; adopter integrates and configures
- 🟢 **Framework-ready** — every technical control exists; adopter provides deployment specifics (SSP, POA&M, boundary definition)
- 🟢 **Substantial coverage** — a substantial portion of the applicable controls are directly satisfied; remainder documented as adopter or organization scope
- ⚠ **Partial** — some framework work needed before formal assessment; gaps enumerated in deep-dive
- ❌ **Gap** — a blocker exists; deep-dive documents the plan to close it

## How JaiClaw contributes vs. adopter responsibility

```
┌─────────────────────────────────────────────────────────────────┐
│                    ADOPTER RESPONSIBILITY                       │
│  ATO package (SSP, SAR, POA&M) │ 3PAO assessment │ CSP inherit │
│  FIPS 199 categorization       │ IR program      │ Training    │
│  Incident response runbook     │ HR / PS         │ DR / BCP    │
│  Continuous monitoring reports │ Physical sec    │ Boundary def│
├─────────────────────────────────────────────────────────────────┤
│               FRAMEWORK CONTRIBUTION (JaiClaw)                  │
│  Audit trail with tamper-evident hash chain                     │
│  Encryption at rest (AES-GCM-256) via SPI                       │
│  Encryption in transit (RequireHttpsStartupGuard)               │
│  Multi-tenant isolation (TenantGuard, fail-closed in MULTI)     │
│  JWT / API-key auth with rate limiting                          │
│  LLM-call audit (AuditingChatModel decorator)                   │
│  BAA / FedRAMP / CUI provider warnings                          │
│  PHI redaction (RegexPromptRedactor + PromptRedactor SPI)       │
│  FIPS posture startup check                                     │
│  Retention enforcement (scheduled purge with audit event)       │
│  Data subject export + erasure (GDPR Art. 15/17/20)             │
│  Consent management SPI                                         │
│  Compliance profiles (gdpr, hipaa, fedramp-moderate, cmmc-l2,   │
│  fips, both, none) — one property flips a bundle of flags       │
└─────────────────────────────────────────────────────────────────┘
```

## Where to look next

**If you're an ATO / 3PAO assessor:**
1. Start with the [NIST 800-53 deep-dive](nist-800-53.md) — the family-by-family inheritance table is the shape you already know
2. Then read the specific-framework deep-dive for your program: [FedRAMP](fedramp.md), [FISMA](fisma.md), [CMMC](cmmc.md)
3. For crypto: [FIPS 140-3](fips-140-3.md) documents the exact algorithms, providers, and how to enable BC-FIPS

**If you're an agency architect evaluating fit:**
1. Read this landing page + skim the rating table
2. Look at the section-508 doc if you're building end-user-facing federal apps
3. Look at the HIPAA / GDPR docs if you're processing regulated data alongside federal workloads

**If you're an adopter operationalizing JaiClaw:**
1. `docs/user/COMPLIANCE.md` is still the GDPR/HIPAA operator guide (this landing page defers to it)
2. Set `jaiclaw.compliance.profile` to the tier that matches your deployment
3. Wire your own `SecretsProvider`, `DataSubjectErasureSpi` extensions, and audit-log destination

## Source-of-truth references

- **This landing page** — reviewed on every release that touches compliance modules
- **[docs/user/COMPLIANCE.md](../user/COMPLIANCE.md)** — canonical GDPR/HIPAA operator guide (0.9.3+)
- **[docs/FEDERAL-COMPLIANCE-ASSESSMENT-2026-08-06.md](../FEDERAL-COMPLIANCE-ASSESSMENT-2026-08-06.md)** — point-in-time assessment; the six federal deep-dives extend from this
- **`extensions/jaiclaw-compliance/`** — code for every capability listed above
- **`core/jaiclaw-security/`** — auth, HTTPS guard, FIPS posture check
- **`extensions/jaiclaw-audit/`** — audit trail, retention purge, hash chain

## Verification

Every claim on this page and its deep-dives is grep-able. The `jaiclaw:compliance-report` Maven goal reads these docs and cross-checks against current code state:

```bash
export JAVA_HOME=/path/to/java21
./mvnw jaiclaw:compliance-report -pl :jaiclaw-cli-github
# Report at target/jaiclaw-compliance-report.md
```

A read-only `/compliance-scan` skill (backlog: `docs/issues/compliance-scan-skill.md`) will perform the same verification interactively for release-engineering workflows.

## Adopter-responsibility disclaimer

**None of these eight frameworks are properties of a library.** JaiClaw provides infrastructure that satisfies technical controls. Certification, assessment, and authorization are properties of a deployed system and are the responsibility of the adopter's operations, security, and compliance teams. This landing page and its deep-dives are an accurate description of the framework's contribution — not a substitute for a formal control assessment against a concrete deployment.

## Contact

Compliance-related inquiries, control-inheritance questions, or ATO planning conversations: file an issue at [github.com/glawson6/jaiclaw/issues](https://github.com/glawson6/jaiclaw/issues) with the `compliance` label. Include the framework and control number (where applicable) — makes the response actionable.
