# `/compliance-scan` skill: audit codebase against 8 government regulations

**Area:** `.claude/skills/compliance-scan/SKILL.md` (new); reads from `docs/compliance/*.md` (source of truth); mirrors `.claude/skills/security-scan/SKILL.md` structure
**Severity:** operational readiness — closes the loop between the compliance docs and the actual codebase state
**Trigger:** the `docs/FEDERAL-COMPLIANCE-ASSESSMENT-2026-08-06.md` report + subsequent gap-remediation implementation (Sept 2026) established a documented compliance posture across Section 508, FedRAMP, FISMA, NIST 800-53, FIPS 140-3, CMMC, HIPAA, and GDPR. We need an automated way to detect regressions: a control we documented as "implemented" that no longer has the code path we claimed.

## Problem statement

After Phase A + B of the federal-compliance plan land:
- `docs/compliance/README.md` and 8 per-regulation deep-dive files claim JaiClaw provides specific capabilities (`HashChainedAuditLogger`, `BaaWarningChatModelDecorator`, `FipsPostureStartupCheck`, `RegexPromptRedactor`, `TenantContext.KEY_FEDRAMP_IMPACT`, etc.)
- Adopters use those docs to build their System Security Plans and inheritance tables
- If a refactor removes or renames one of those classes, the docs silently become wrong
- The `jaiclaw:compliance-report` Maven goal (Phase A.7) provides one machine-check but only exposes surface-level facts, and only runs when someone invokes `verify`

A read-only skill parallels the existing `/security-scan` + `/dep-check` skills to give operators + release engineers an on-demand compliance-posture audit in the same familiar shape.

## Design

**Naming:** `compliance-scan` (kebab-case, matches `.claude/skills/` convention)

**Frontmatter shape** (copies `.claude/skills/security-scan/SKILL.md` layout):
```yaml
name: compliance-scan
description: Comprehensive government-compliance audit — verifies JaiClaw code state matches the claims in docs/compliance/*.md for Section 508, FedRAMP, FISMA, NIST 800-53, FIPS 140-3, CMMC, HIPAA, GDPR
platforms: [darwin, linux]
version: 1.0.0
alwaysInclude: false
requiredBins: []
tenantIds: []
```

**Invocation:** `/compliance-scan` from a Claude Code session at the repo root

**Output:** `compliance-scan-report-<YYYY-MM-DD>.md` in project root (mirrors `security-scan-report-<DATE>.md`)

## Phases

The skill runs 10 sequential phases. Each phase reads the corresponding source-of-truth doc + verifies the code claims still hold.

### Phase 1 — Read the compliance corpus (source of truth)

Read `docs/compliance/README.md` + all 8 deep-dives. Extract every explicit code claim (file path + class name / property key / algorithm). Build a claims manifest. This is what the rest of the phases verify against.

### Phase 2 — Section 508

- Verify `extensions/jaiclaw-pipeline-dashboard/src/main/resources/jaiclaw-pipeline-dashboard/dashboard.html` still has `aria-label` + `aria-live` on `#connection-status`
- Verify `apps/jaiclaw-pipeline-studio/frontend/package.json` still declares `eslint-plugin-jsx-a11y`, `axe-core`
- If `pnpm` available, run `pnpm run test:a11y` in `apps/jaiclaw-pipeline-studio/frontend/` and record axe-core findings
- Grep for ANSI color-only patterns in shell commands (color set but no symbolic/text backup)
- Verify `AsciiRenderTool.INPUT_SCHEMA` still contains `altText` field

### Phase 3 — FedRAMP

- Verify `FedRampWarningChatModelDecorator` class exists in `extensions/jaiclaw-compliance/src/main/java/`
- Verify auto-config bean is gated on `jaiclaw.compliance.effective.fedramp-warnings=true`
- Verify `TenantContext.KEY_FEDRAMP_IMPACT` constant exists
- Verify `ComplianceProfile.FEDRAMP_MODERATE` enum value exists

### Phase 4 — FISMA

- Verify `security-hardened` profile flags in application yamls default appropriately
- Verify `HashChainedAuditLogger` bean is available when profile requires it
- Cross-check with NIST 800-53 phase (FISMA inherits from NIST for technical controls)

### Phase 5 — NIST 800-53 control-family sweep

For each family the docs claim (AC, AU, IA, SC, SI, CM):
- Grep for the specific class / method / property the doc names as evidence
- Report OK / REGRESSION per control

### Phase 6 — FIPS 140-3

- Verify `FipsPostureStartupCheck` bean exists + is wired in `JaiClawSecurityAutoConfiguration`
- Verify `ComplianceProfile.FIPS` enum value exists
- Optional live check: run a tiny Java process that dumps `Security.getProviders()` — record output for operator inspection
- Grep every module's crypto call sites (`Cipher.getInstance`, `KeyPairGenerator.getInstance`, etc.) and cross-reference against the algorithm list in `docs/compliance/fips-140-3.md` — flag any new algorithm not on the FIPS-approved list

### Phase 7 — CMMC

- Verify `CuiWarningChatModelDecorator` exists + wired
- Verify `ComplianceProfile.CMMC_L2` enum value exists
- Verify `TenantContext.KEY_CUI_PROCESSING` constant exists
- Spot-check CMMC L2 practice-area code claims from `docs/compliance/cmmc.md`

### Phase 8 — HIPAA

- Verify `BaaWarningChatModelDecorator` still present + wired
- Verify `RegexPromptRedactor` still present + wired
- Verify `TenantContext.KEY_PHI_PROCESSING` still declared
- Verify `ComplianceProfile.HIPAA` / `BOTH` effective flag mapping unchanged
- Verify `AesGcmFieldEncryptor` uses AES-GCM-256 with 12-byte nonce (no algorithm downgrade)

### Phase 9 — GDPR

- Verify `AggregateDataSubjectErasureSpi` still present
- Verify `AggregateDataSubjectExportService` + `GdprController` still exposing `/api/gdpr/*`
- Verify `InMemoryConsentManager` still present (or an adopter override registered)
- Verify `AuditingChatModelBeanPostProcessor` still wired

### Phase 10 — Write the report

Produce `compliance-scan-report-<DATE>.md` at project root with:
- Executive summary (X OK / Y REGRESSION / Z GAP counts per regulation)
- Table of findings by regulation, each row citing file path + line number
- Adopter-responsibility summary (things the docs mark as "adopter's job" — the scan cannot verify these but they are enumerated so operators know what still requires manual review)

## Severity taxonomy

| Rating | Meaning |
|---|---|
| **REGRESSION** | Doc claims a control exists, code path is missing/renamed. Must be fixed before shipping any release that references the doc. |
| **GAP** | Doc explicitly marks it "adopter responsibility". Not a JaiClaw defect, listed for adopter awareness. |
| **OK** | Doc claim verified against code. |
| **INFO** | Ancillary observation (e.g., "5 new algorithms added to `Cipher.getInstance` since last scan — verify all are FIPS-approved"). |

## Dependencies

**Must land before the skill is implemented:**
- Phase A of the federal-compliance plan (all code additions) — the skill needs `FedRampWarningChatModelDecorator`, `CuiWarningChatModelDecorator`, `FipsPostureStartupCheck`, `TenantContext.KEY_FEDRAMP_IMPACT`, `KEY_CUI_PROCESSING`, `ComplianceProfile.FEDRAMP_MODERATE|CMMC_L2|FIPS`, `AsciiRenderTool.altText` all to exist so the phase-by-phase checks have real targets
- Phase B of the federal-compliance plan (all docs) — the skill reads these as the source of truth

**Order of implementation:** the skill is the last artifact — it's the audit tool that closes the loop. Do not implement until adopters have started referencing the docs.

## Reference implementations to copy

- `.claude/skills/security-scan/SKILL.md` — 8-phase structure, severity table, output-path convention (`security-report-<DATE>.md`)
- `.claude/skills/dep-check/SKILL.md` — tier-based reporting shape, statistics table format
- `docs/FEDERAL-COMPLIANCE-ASSESSMENT-2026-08-06.md` — the executive-summary + rating-table shape to replicate in the report output

## Not in scope for the skill (still adopter responsibility)

- Running a real 3PAO assessment (skill can't emulate an assessor's judgment)
- Producing an SSP or SAR (deploy-specific documents)
- Live crypto-module certification verification (adopter's runtime concern, not framework code)
- Multi-agency federated evidence gathering (out of scope for a repo-local skill)

## Ownership + rollout

- **Owner at implementation time:** whichever engineer takes the follow-up ticket after Phase A + B ship
- **Testing plan:** run the skill against the current codebase, capture the report, review with a compliance/security-focused reviewer, iterate the phase checks based on false-positive / false-negative feedback
- **Cadence once shipped:** run on every release-candidate branch as a manual step (like `/security-scan` today); consider a scheduled monthly run against `main`

## Rollback

Skill lives entirely under `.claude/skills/compliance-scan/`. `git rm -rf` removes it. Zero framework impact.
