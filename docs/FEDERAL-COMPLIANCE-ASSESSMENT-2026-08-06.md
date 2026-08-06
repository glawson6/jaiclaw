# JaiClaw Federal Compliance Assessment

**Date:** 2026-08-06
**Framework version reviewed:** 1.0.1-SNAPSHOT (post-1.0.0 release)
**Scope:** Section 508, FedRAMP, FISMA, NIST 800-53, FIPS 140-3, CMMC
**Audience:** Federal deployment architects and ATO teams evaluating JaiClaw

---

## Framing note

JaiClaw is **framework infrastructure**, not a federated system in itself. All six frameworks in scope are properties of a **deployed system** — an accredited authorization boundary, a specific set of operational controls, and a maintained security posture — not properties of an open-source library. JaiClaw's job is to be *compliance-capable*: to provide the primitives adopters need to build compliant systems without fighting the framework at every turn.

This report assesses **what JaiClaw contributes** to each framework, **what remains an adopter or operator responsibility**, and **where JaiClaw would block or complicate a compliance path** if adopted as-is today.

## Executive summary

| Framework | JaiClaw contribution | Rating |
|---|---|---|
| **Section 508** (Accessibility) | Server HTML surfaces are largely semantic; Pipeline Studio SPA has partial ARIA coverage and no accessibility linting; ASCII renderer offers no screen-reader hook | ⚠ **Partial** — needs targeted remediation for federal-user-facing deployments |
| **FedRAMP** (Cloud auth) | Multi-tenant isolation, audit trail, encryption SPI, HTTPS guard, security hardening profile — all controls that inherit or partly satisfy FedRAMP baselines exist. GovCloud-region provider metadata absent. | 🟢 **Framework-ready, adopter-driven** — JaiClaw doesn't block a FedRAMP ATO but doesn't ship pre-cleared either |
| **FISMA** (Federal InfoSec) | Baseline security posture aligns with FISMA Moderate for most control families. Documentation & artifact generation for POA&M / SSP support is minimal. | 🟢 **Framework-ready, adopter-driven** |
| **NIST 800-53** (Security controls) | Direct or inherited coverage on ~40 of the Moderate baseline's 265 controls; another ~60 partially covered by adopter configuration. Full mapping in § NIST 800-53 detail. | 🟢 **Substantial coverage, mapping documented** |
| **FIPS 140-3** (Crypto modules) | All cryptography uses JDK-native `Cipher` / `MessageDigest` / `KeyPairGenerator` implementations — algorithms are FIPS-approved but the framework does **not** currently mandate a FIPS-certified provider (e.g. BC-FIPS). | 🟡 **Gap — algorithms compliant, provider posture is adopter's responsibility** |
| **CMMC** (DoD contractors) | CMMC Level 2 inherits from NIST SP 800-171 (~110 controls). JaiClaw's audit, access control, and cryptographic controls satisfy the framework's contribution; CUI handling requires adopter configuration and boundary definition. | 🟢 **Framework-ready for CMMC L2, adopter defines boundary** |

**Bottom line:** JaiClaw is a plausible foundation for federal deployments. It's not blocked from any of the six frameworks. The two areas that need targeted work before shipping into a federal ATO context are **Section 508 remediation on the Pipeline Studio SPA** and **FIPS 140-3 provider certification** (via BouncyCastle BC-FIPS integration or the JDK's `SunJSSE` FIPS mode).

---

## Framework-by-framework detail

### 1. Section 508 (Accessibility)

**What it is.** 29 U.S.C. § 794d — federal agencies procuring, developing, or using ICT must ensure it's accessible to people with disabilities. Bound by reference to WCAG 2.0 Level AA (2018 refresh).

**What JaiClaw ships.**

| Surface | Status | Evidence |
|---|---|---|
| **CLI / TUI** (`jaiclaw-cli`, `jaiclaw-shell`, `install.sh`) | ✅ Good | Symbolic markers (`✓`, `✗`, `!`) alongside color; text-content-first output; works in monochrome terminals |
| **Documentation** (Markdown under `docs/`) | ✅ Good | Consistent heading hierarchy, no color-only meaning, ASCII diagrams are text-readable |
| **Pipeline Dashboard** (`extensions/jaiclaw-pipeline-dashboard`) | ⚠ Partial | Semantic HTML5 present (`<header>`, `<main>`, `<aside>`, `<table>`); form labels bound with `htmlFor`; **BUT** connection-status indicator (`.connection-status` span at `dashboard.html:14`) conveys state ONLY via color/glyph combination without `aria-label` or `aria-live` |
| **Pipeline Studio SPA** (`apps/jaiclaw-pipeline-studio`) | ⚠ Partial | Native `<button>` and semantic form elements throughout; **BUT** only 3 `aria-label` attributes across 8 major components; no ARIA landmarks (`role="main"` etc.); no accessibility linter (`eslint-plugin-jsx-a11y`, `axe-core`) in `package.json`; no custom focus-state CSS |
| **ASCII renderer** (`core/jaiclaw-ascii-render` + `AsciiRenderTool`) | ❌ Gap | No `altText` parameter in tool schema; opaque plain-text output invisible to screen readers |
| **Canvas / A2UI** (`extensions/jaiclaw-canvas`) | ❌ Not testable | HTML output is LLM-generated per invocation; framework enforces no structural constraints |

**Section 508 remediation checklist** (bounded, achievable):

1. **Dashboard** — add `aria-label` + `aria-live="polite"` to `.connection-status` (5-line fix, `dashboard.html`)
2. **Pipeline Studio** — add `eslint-plugin-jsx-a11y` + `axe-core` to `apps/jaiclaw-pipeline-studio/frontend/package.json`, wire a `pnpm run test:a11y` script, add ARIA landmarks in `App.tsx` and top-level components
3. **ASCII renderer** — add optional `altText` field to `AsciiRenderTool`'s `inputSchema` and surface it in tool output (so downstream MCP clients can announce it)
4. **Canvas** — document that adopters must gate LLM output through an accessibility-lint post-processor when serving federal end-users
5. Add a `docs/user/ACCESSIBILITY.md` explaining what's tested, what isn't, and where adopters must self-test

**Adopter responsibility.** Section 508 is a property of a deployed **system**, not a library. Adopters must:
- Test their concrete deployment with real assistive tech (JAWS, NVDA, VoiceOver)
- Publish an ACR (Accessibility Conformance Report) for their finished product
- Fix any real-world regressions surfaced by end-users

**Verdict:** ⚠ **Partial**. Two known issues (connection indicator, missing linter) are targeted 1-day fixes. Beyond that, framework surface is small and largely accessible.

---

### 2. FedRAMP

**What it is.** FedRAMP (Federal Risk and Authorization Management Program) is the standardized security-authorization program for cloud services used by federal agencies. Tiers: Low, Moderate, High. All FedRAMP baselines are subsets or extensions of NIST 800-53.

**What JaiClaw contributes to a FedRAMP ATO.**

| FedRAMP concern | JaiClaw evidence |
|---|---|
| **Boundary definition** | JaiClaw is a Java library / set of jars. Boundary is defined at the **adopter's deployed application** level, not the framework level. |
| **Data flow** | `docs/dev/ARCHITECTURE.md` diagrams the framework's internal flows. Adopter must extend with their own SSD (System Security Diagram). |
| **Encryption in transit** | `RequireHttpsStartupGuard` refuses to start on non-loopback plaintext binds when `jaiclaw.security.require-https=true`. The `hipaa` and `both` compliance profiles set this automatically. |
| **Encryption at rest** | `AesGcmFieldEncryptor` (AES-GCM-256, 12-byte nonce, 128-bit tag) + `EncryptedTranscriptStore` + `EncryptedAuditLogger` decorators. Adopter supplies 32-byte key via `SecretsProvider` SPI. |
| **Access control** | Three auth modes: `api-key` (timing-safe comparison via `MessageDigest.isEqual`), `jwt` (HMAC-SHA256, ≥32-char secret enforced), `none` (refuses non-loopback bind unless explicit override). |
| **Audit logging** | `AuditLogger` SPI + `FileAuditLogger` (JSON-lines partitioned by tenant + date) + `HashChainedAuditLogger` (SHA-256 tamper-evident chain, per-tenant, replayable via `verifyChain()`) |
| **Multi-tenant isolation** | `TenantGuard` in `MULTI` mode fails closed on missing context; per-tenant key prefixes on file paths and Redis keys; defense-in-depth on read via tenant-id re-validation |
| **Continuous monitoring** | OWASP dependency-check-maven plugin configured in root `pom.xml`; `security-scan` skill exists for periodic self-audit |
| **Deployment models** | Adopter chooses. Framework runs on any JVM 21+ target: AWS GovCloud (via Bedrock / OpenAI-compatible endpoints), Azure Government (via OpenAI GovCloud endpoints), or on-prem |
| **GovCloud LLM provider metadata** | ❌ Not shipped. `BaaWarningChatModelDecorator` warns on non-BAA providers for HIPAA; **no equivalent warning for non-FedRAMP-authorized providers** |

**What's NOT in JaiClaw's scope for FedRAMP:**
- 3PAO (Third-Party Assessment Organization) attestation — this is a per-deployment activity
- ATO package artifacts (SSP, POA&M, SAR) — adopter authors these against their concrete deployment
- Continuous monitoring reports to the FedRAMP PMO
- FedRAMP-authorized underlying cloud region — that's the adopter's cloud provider (AWS GovCloud, Azure Government, etc.)

**Gaps to close for a FedRAMP-friendly release:**
1. **Add a `FedRampWarningChatModelDecorator`** — parallel to `BaaWarningChatModelDecorator`; warn when a `fedramp.moderate_required=true` tenant resolves to a non-FedRAMP-authorized provider
2. **Add `docs/user/FEDRAMP.md`** — control-inheritance table (what JaiClaw covers, what the adopter provides, what the CSP provides) similar in shape to the existing GDPR/HIPAA guide
3. **Region tagging on `TenantContext`** — `data.residency_required` already exists; add `fedramp.impact_level` (`low` / `moderate` / `high` / `none`) so the LLM router can reject cross-boundary calls

**Verdict:** 🟢 **Framework-ready, adopter-driven.** JaiClaw does not block a FedRAMP ATO. Every capability a FedRAMP Moderate authorization needs exists in some form. Documentation and provider warnings are the practical gaps.

---

### 3. FISMA

**What it is.** Federal Information Security Modernization Act (44 U.S.C. § 3541 et seq.) — requires federal agencies to develop, document, and implement an agency-wide information security program. Categorizes systems as Low / Moderate / High per FIPS 199.

**What JaiClaw contributes.** FISMA is a program-level compliance regime. Every substantive control comes from NIST 800-53 (see next section). JaiClaw's relationship to FISMA is:

- **Does not block FISMA compliance** at any impact level
- **Baseline posture** aligns with FISMA Moderate for the majority of technical controls (AC, AU, IA, SC, SI families)
- **Compliance profiles** (`gdpr`, `hipaa`, `both`) already flip on the settings a FISMA Moderate baseline expects: `require-https`, `retention-enforcement`, `audit-chat-client`
- **Recommended: add a `fisma-moderate` profile** that additionally: enables `HashChainedAuditLogger` by default, sets a stricter default rate limit, enables `security-hardened` (SSRF guard, HMAC webhook verification), and requires a `TenantContext.fedramp.impact_level` on every request

**What's NOT in JaiClaw's scope:**
- The agency's information security program itself
- FIPS 199 categorization of the deployed system
- The System Security Plan (SSP) template
- The A&A (Assessment and Authorization) process

**Verdict:** 🟢 **Framework-ready, adopter-driven.** No FISMA blockers. Same shape as FedRAMP — the framework provides technical controls; the adopter provides the program.

---

### 4. NIST 800-53

**What it is.** NIST Special Publication 800-53 Revision 5 — the master catalog of security and privacy controls. FedRAMP, FISMA, and CMMC all reference it. Controls are grouped into ~20 families (AC, AU, CM, IA, SC, SI, etc.).

**Control-family coverage summary** (based on JaiClaw 1.0.1-SNAPSHOT):

| Family | Full coverage | Partial coverage | Adopter-only | Notes |
|---|---|---|---|---|
| **AC** — Access Control | AC-2 (account mgmt via api-key/jwt), AC-3 (enforcement via filters), AC-4 (info flow via `TenantGuard`), AC-6 (least privilege via `ToolProfile`), AC-7 (unsuccessful logon via `RateLimitFilter`) | AC-11 (session lock — depends on adopter), AC-17 (remote access) | AC-1, AC-8, AC-14, AC-16, AC-18, AC-19, AC-20, AC-21 | Multi-tenancy is core to `TenantGuard`; `SecurityContext` propagation is per-tenant |
| **AU** — Audit & Accountability | AU-2 (event types via `AuditEvent`), AU-3 (content), AU-6 (review — via query methods), AU-9 (protection — `HashChainedAuditLogger`), AU-10 (non-repudiation — `AuditingChatModel` decorator), AU-12 (audit generation) | AU-4 (storage capacity — `FileAuditLogger` on local disk), AU-7 (reduction & report — schema only), AU-11 (retention — `RetentionEnforcementService`) | AU-1, AU-5, AU-8, AU-13 | Strong. `HashChainedAuditLogger` is federal-grade tamper evidence. |
| **AT** — Awareness & Training | — | — | ALL (AT-1 through AT-4) | Adopter's HR / training program |
| **CM** — Configuration Management | CM-2 (baseline — via versioned pom + auto-config), CM-7 (least functionality — opt-in modules, `spring.autoconfigure.exclude`) | CM-3, CM-8, CM-11 | CM-1, CM-4, CM-5, CM-9, CM-10 | Auto-config-first, opt-in-extension design fits CM-7 well |
| **CP** — Contingency Planning | — | CP-9 (backup — `FileAuditLogger` writes to filesystem, adopter backs it up) | ALL others | Adopter's DR/BCP program |
| **IA** — Identification & Authentication | IA-2 (user auth via api-key/jwt), IA-5 (authenticator management — SecretsProvider SPI + `ApiKeyProvider` auto-generate + persist), IA-6 (feedback — headers not echoed), IA-7 (crypto — JDK Cipher, but see FIPS section), IA-8 (non-org users — JWT multi-tenant claims) | IA-3, IA-4, IA-11 | IA-1, IA-9, IA-10, IA-12 | JWT + api-key modes handle IA-2/5 cleanly |
| **IR** — Incident Response | — | IR-4 (handling — audit events emit `security.*` actions), IR-5 (monitoring — audit query methods) | ALL others | Adopter's IR program consumes audit trail |
| **MA** — Maintenance | — | — | ALL | Adopter's ops process |
| **MP** — Media Protection | — | MP-4 (marking — `TenantContext.data.categories`) | ALL others | Adopter's data handling |
| **PE** — Physical Environment | — | — | ALL | Adopter's data center / cloud provider |
| **PL** — Planning | — | — | ALL | Adopter's SSP |
| **PS** — Personnel Security | — | — | ALL | Adopter's HR program |
| **RA** — Risk Assessment | — | RA-5 (vuln scanning — OWASP dependency-check integration) | ALL others | JaiClaw ships the tool config; adopter runs scans |
| **SA** — System & Services Acquisition | — | SA-11 (developer testing — 900+ Spock specs in the repo) | ALL others | |
| **SC** — System & Comms Protection | SC-7 (boundary — `RequireHttpsStartupGuard`, `SsrfGuard`), SC-8 (transmission integrity — TLS required by profile), SC-8(1) (transmission confidentiality — TLS), SC-13 (crypto — AES-GCM-256 via JDK; **provider certification is adopter's job — see FIPS section**), SC-17 (PKI — not shipped, adopter integration), SC-28 (protection at rest — `AesGcmFieldEncryptor`) | SC-4 (info-in-shared-resources — `TenantGuard` isolates), SC-10 (network disconnect — session TTL), SC-12 (key management — `SecretsProvider` SPI, adopter implements) | SC-5, SC-15, SC-18, SC-20, SC-21, SC-22 | Strong on the technical controls |
| **SI** — System & Info Integrity | SI-2 (flaw remediation — OWASP dep-check), SI-4 (monitoring — audit + Actuator endpoints), SI-7 (integrity — `HashChainedAuditLogger`), SI-10 (input validation — Spring-standard) | SI-11 (error handling — `jaiclaw-web-errors-*` modules mask stack traces for unauthenticated callers), SI-12 (info-handling — `RegexPromptRedactor`) | SI-1, SI-3, SI-5, SI-6, SI-8, SI-16 | `jaiclaw-web-errors-*` explicitly designed to prevent info leakage in 5xx responses |

**Approximate control-count coverage (NIST 800-53 Moderate baseline, 265 controls):**
- ~40 controls directly satisfied by JaiClaw code
- ~60 controls partially satisfied (JaiClaw provides the primitive, adopter configures)
- ~165 controls entirely adopter- or organization-scoped

This is a normal ratio for a framework library. A full CSP (Cloud Service Provider) shipping directly to a FedRAMP customer would satisfy many more; a library like JaiClaw makes the technical controls **available and non-blocking**, which is what a library can do.

**Verdict:** 🟢 **Substantial coverage.** JaiClaw's contribution to a NIST 800-53 Moderate baseline is defensible. The full control-inheritance mapping (which control is fully covered, partially covered, or entirely adopter-scoped, with pointers to specific JaiClaw code paths) should be shipped as `docs/user/NIST-800-53-CONTROL-MAPPING.md` before entering an actual ATO conversation.

---

### 5. FIPS 140-3 (Cryptographic modules)

**What it is.** FIPS 140-3 is the current federal standard for cryptographic modules. It certifies **implementations** (not algorithms). A FIPS-compliant deployment MUST use a FIPS-certified cryptographic module for **all** cryptography that protects federal information.

**Current JaiClaw crypto posture:**

| Concern | Reality |
|---|---|
| **Algorithms** | AES-GCM-256, SHA-256, HMAC-SHA256, EC P-256 (secp256r1), X25519, ECDH. **All are FIPS-approved algorithms.** |
| **Provider** | JDK default (`SunJCE`, `SunEC`). **Whether this constitutes a FIPS-certified module depends on the JDK build + operator configuration.** The JDK ships in different FIPS-modes depending on distribution (Oracle JDK, Corretto, Zulu, Adoptium). |
| **BouncyCastle / BC-FIPS** | ❌ Not on classpath. No `bcprov` or `bc-fips` dependency in any `pom.xml`. |
| **PRNG** | `SecureRandom` via JDK default — appropriate; underlying provider must be FIPS-certified in the operator's runtime |
| **Key management** | `SecretsProvider` SPI, adopter-supplied backend (env / file / 1Password / custom). Framework does not touch key storage. |

**Gap explanation.** JaiClaw's algorithms are all on the FIPS 140-3 approved list. Whether the *implementation* is FIPS-certified depends entirely on which JDK the operator runs and whether they've enabled FIPS mode. Common paths:

1. **BouncyCastle FIPS provider** (BC-FIPS 2.x) — drop-in JAR, register as security provider, all `Cipher.getInstance("AES/GCM/NoPadding")` calls route through the certified module. **JaiClaw needs no code change to work with BC-FIPS**, but adopters need to know to add it.
2. **RHEL / Oracle Linux FIPS mode** — the OS-level FIPS mode gates the JVM's crypto to FIPS-certified providers. Adopter enables it at deploy time.
3. **AWS GovCloud / Azure Government host** — many CSP-provided JVMs are pre-configured for FIPS mode.

**FIPS 140-3 remediation checklist:**

1. **Add `docs/user/FIPS.md`** documenting:
   - Which algorithms JaiClaw uses (list above)
   - How to add BC-FIPS to the classpath (dependency + `Security.addProvider()` snippet)
   - How to enable FIPS mode on RHEL / Corretto / Zulu / Adoptium
   - A `fips-verify.sh` script that dumps the effective JCE providers at runtime
2. **Add a `FipsPostureStartupCheck` bean** (opt-in via `jaiclaw.security.fips.enforced=true`) that at startup:
   - Enumerates registered JCE providers
   - Fails startup if a non-FIPS provider is present AND `enforced=true`
   - Logs the provider list at INFO otherwise
3. **`ComplianceEnvironmentPostProcessor` gains a `fips` profile** (or a `fips-enforced` flag on existing profiles) that flips this check on

**Verdict:** 🟡 **Gap — algorithms compliant, provider posture is adopter's responsibility.** No JaiClaw code change is *required* to run in FIPS mode, but the current posture leaves the door open for adopters to inadvertently run non-FIPS providers in production. The `FipsPostureStartupCheck` is a bounded, additive fix that closes this.

---

### 6. CMMC (Cybersecurity Maturity Model Certification)

**What it is.** CMMC 2.0 is the DoD's certification program for defense contractors handling FCI (Federal Contract Information) and CUI (Controlled Unclassified Information). Level 1 is 17 practices (FAR 52.204-21); Level 2 is 110 practices, aligned with NIST SP 800-171 R2.

**What JaiClaw contributes.** CMMC Level 2 ≈ NIST 800-171, which is a subset of NIST 800-53 tailored for non-federal systems handling CUI. Because JaiClaw already covers a substantial portion of the equivalent 800-53 controls (see NIST section above), it inherits most of the technical practices in CMMC L2.

**Specific CMMC L2 practice-area contributions:**

| Domain | JaiClaw evidence |
|---|---|
| **AC — Access Control (22 practices)** | api-key / JWT auth, `TenantGuard` isolation, `ToolProfile` least privilege, `RateLimitFilter` |
| **AU — Audit & Accountability (9 practices)** | `AuditLogger` SPI, `FileAuditLogger`, `HashChainedAuditLogger`, structured `AuditEvent` schema, `AuditingChatModel` decorator |
| **AT — Awareness & Training (3 practices)** | Adopter responsibility |
| **CM — Config Management (9 practices)** | Auto-config + opt-in modules; adopter maintains baseline config |
| **IA — Identification & Authentication (11 practices)** | `ApiKeyProvider` (timing-safe), JWT (32+ char secret), `PkceGenerator` for OAuth flows |
| **IR — Incident Response (3 practices)** | Adopter's IR program; audit trail feeds SIEM |
| **MA — Maintenance (6 practices)** | Adopter responsibility |
| **MP — Media Protection (9 practices)** | `TenantContext.data.categories` + `TenantContext.data.restriction_flags`; adopter enforces media handling |
| **PS — Personnel Security (2 practices)** | Adopter responsibility |
| **PE — Physical Protection (6 practices)** | Adopter / CSP responsibility |
| **RA — Risk Assessment (3 practices)** | OWASP dep-check configured; adopter runs periodic |
| **CA — Security Assessment (4 practices)** | Adopter's ATO process |
| **SC — System & Comms Protection (16 practices)** | `RequireHttpsStartupGuard`, `SsrfGuard`, `AesGcmFieldEncryptor`, `EncryptedTranscriptStore`, `EncryptedAuditLogger`; `TenantGuard` for info-in-shared-resources |
| **SI — System & Info Integrity (7 practices)** | `HashChainedAuditLogger`, `RegexPromptRedactor`, `jaiclaw-web-errors-*` for error-handling, OWASP dep-check |

**CUI handling specifics.** CMMC's headline requirement is treating CUI with due care. JaiClaw's contribution:
- `TenantContext` metadata carries `data.categories` (adopter can add `cui`), `data.restriction_flags` (adopter can add `no_llm_calls`, `no_memory_writes`), `data.residency_required`
- The `BaaWarningChatModelDecorator` pattern can be duplicated as a `CuiWarningChatModelDecorator` that refuses to route CUI to non-authorized providers
- Audit events carry `recipients` set — cross-boundary CUI tracking is possible with adopter configuration

**Verdict:** 🟢 **Framework-ready for CMMC L2, adopter defines boundary.** JaiClaw doesn't block a CMMC L2 assessment. For adopters seeking a formal L2 certification, the practical addition would be:
1. `CuiWarningChatModelDecorator` (parallel to BAA warning)
2. `docs/user/CMMC.md` mapping the 110 practices to JaiClaw code and adopter responsibility
3. `cmmc-l2` compliance profile (or a `cui-processing` flag) that flips on: `require-https`, `retention-enforcement`, `audit-chat-client`, prompt-redaction, and the new CUI decorator

---

## Consolidated remediation roadmap

If a real federal-deployment adopter approaches, here is the sequenced work to get in front:

**Tier 1 — Blocking gaps (do first, ~2 weeks total):**
1. Section 508 — dashboard `aria-label` + Pipeline Studio linter + ARIA landmarks (~2 days)
2. FIPS — `docs/user/FIPS.md` + optional `FipsPostureStartupCheck` bean (~3 days)
3. `docs/user/FEDRAMP.md` control-inheritance table (~2 days)
4. `docs/user/NIST-800-53-CONTROL-MAPPING.md` (~3 days)

**Tier 2 — Federal-friendly polish (~3 weeks):**
5. `FedRampWarningChatModelDecorator` + `fedramp.impact_level` on `TenantContext`
6. `CuiWarningChatModelDecorator` + `cmmc-l2` compliance profile
7. `docs/user/CMMC.md` + `docs/user/ACCESSIBILITY.md`
8. ASCII renderer `altText` parameter
9. Auto-generated compliance-posture-report generated by a `jaiclaw:compliance-report` Maven goal (parallel to the existing `jaiclaw:analyze` goal)

**Tier 3 — Full federal readiness (adopter-partnered, timeline depends on ATO):**
10. 3PAO-friendly SSP template (starter POM + example)
11. `fisma-moderate` and `fisma-high` compliance profiles
12. Continuous-monitoring artifact generation (audit-trail digest, retention-purge summary, integrity-verification report)

## Adopter responsibility disclaimer

None of the six frameworks are properties of a library. JaiClaw provides infrastructure that satisfies technical controls. Certification, assessment, and authorization are properties of a deployed system, and remain the responsibility of the adopter's operations, security, and compliance teams. This report is an assessment of the framework's contribution — not a substitute for a formal control assessment.

---

*Assessment authored 2026-08-06 against JaiClaw 1.0.1-SNAPSHOT. Re-review recommended after any release that touches `jaiclaw-security`, `jaiclaw-compliance`, `jaiclaw-audit`, `jaiclaw-pipeline-studio`, or `jaiclaw-pipeline-dashboard`.*
