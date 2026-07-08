# Classifier Safety Audit — Bundled Skills (2026-07-07)

**Motivation:** Nous Research documented in the Hermes agent codebase (commit `fdc90346eaa3931fb357543b9224515728cac914`) that Anthropic's Claude output classifier returns empty content on ~95% of sessions whose system prompt advertises certain skills (specifically `red-teaming/godmode` and `mlops/inference/obliteratus`) — even when those skills are not actually loaded, just described in the bundled catalog. The classifier is triggered by the descriptions alone.

JaiClaw's design also injects the bundled-skill catalog into every session's system prompt (`SkillPromptBuilder`), so the same risk applies. This audit sweeps all 70 bundled skills for text patterns that plausibly trip safety classifiers.

**Result: PASS with two minor advisories.** No bundled skill's description contains the high-risk terms Hermes flagged. Two skills use the word `bypass` in benign contexts (recommend re-word for defensive over-caution); one skill (`healthcheck`) uses "security hardening" language that's benign but worth watching after 1.0.

---

## Methodology

Two grep sweeps across every `SKILL.md` file:

1. **Explicit red-flag terms** — the terms Hermes docs specifically named, plus adjacent safety-classifier trigger words:
   `jailbreak`, `godmode`, `obliterate`, `refusal`, `bypass`, `exploit`, `unrestricted`, `DAN` (as whole word), `uncensored`, `prompt injection`, `abliterate`, `rootkit`, `backdoor`, `keylogger`.

2. **Adjacent risk words in descriptions** — `kill`, `destroy`, `attack`, `curse`, and other terms that could appear ambiguously in a benign context but might combine with adjacent words to trip a classifier.

Command used (reproducible):
```bash
SKILL_DIR=core/jaiclaw-skills/src/main/resources/skills
for term in jailbreak godmode obliterate refusal bypass exploit unrestricted "\bDAN\b" uncensored "prompt injection" abliterate rootkit backdoor keylogger; do
  grep -rl -i "$term" $SKILL_DIR --include="SKILL.md"
done
```

---

## Findings

### Red-flag term hits

| Term | Skills hit | Verdict |
|---|---|---|
| `bypass` | `coding-agent`, `systematic-debugging`, `e2e-test` | ⚠️ ADVISORY — benign context (see below), consider rewording |
| `jailbreak` | none | ✅ PASS |
| `godmode` | none | ✅ PASS |
| `obliterate` | none | ✅ PASS |
| `refusal` | none | ✅ PASS |
| `exploit` | none | ✅ PASS |
| `unrestricted` | none | ✅ PASS |
| `DAN` (whole word) | none | ✅ PASS |
| `uncensored` | none | ✅ PASS |
| `prompt injection` | none | ✅ PASS |
| `abliterate` | none | ✅ PASS |
| `rootkit` | none | ✅ PASS |
| `backdoor` | none | ✅ PASS |
| `keylogger` | none | ✅ PASS |

### Descriptions with adjacent risk words

Only one hit worth mentioning: `healthcheck` — "Host security hardening and system health assessment. Use when asked for security audits, system hardening, secure baseline verification…" The term "security hardening" combined with "audit" is defensible in an enterprise sysadmin context and appears in mainstream infrastructure tooling. Low risk but flag for monitoring.

`1password` uses "secure credential access" — mainstream vendor phrasing, no risk.

`clawhub` — "install, update, and publish agent skills" — the word "install" combined with skill-marketplace vocabulary is unremarkable.

`camsnap` — RTSP camera capture. Benign for a home-automation context. Would be higher-risk if it mentioned "surveillance" or "espionage"; it does not.

`peekaboo` — macOS UI automation. Benign in context.

---

## The three `bypass` hits — recommended rewording

All three uses are pedestrian software-engineering references, not evasion vocabulary. Rewording is defensive over-caution, not correction of a real issue.

### `coding-agent/SKILL.md`

Current:
> "…allows you to bypass the coding tool loop entirely…"

Context is: delegating a coding task to a subagent, skipping the built-in tool loop. "Bypass" here means "skip" — pure software architecture vocabulary.

**Recommended rewrite:** replace "bypass the coding tool loop" with "delegate around the coding tool loop" or "hand off past the coding tool loop." Same meaning, no evasion vocabulary.

### `systematic-debugging/SKILL.md`

Current: `bypass` appears twice, both in the phrase "bypass the diagnostic process" as a warning against shortcuts.

**Recommended rewrite:** "skip the diagnostic process" or "shortcut the diagnostic process." Same meaning, more direct.

### `e2e-test/SKILL.md`

Current: bypass appears in the phrase "bypass Docker" (as in: run natively instead of in a container).

**Recommended rewrite:** "run outside Docker" or "skip the Docker step."

---

## What we did NOT find (and why that matters)

The Hermes rationale was that catalog descriptions of `red-teaming/godmode` and `mlops/inference/obliteratus` combined multiple trigger phrases — "jailbreak," "GODMODE," "ULTRAPLINIAN," "abliterate," "refusal removal," "system prompt templates per model" — into a single description block that read to the classifier as an instruction set for evading Claude.

**None of JaiClaw's 70 bundled skills come remotely close to that concentration of trigger vocabulary.** The catalog is dominated by mainstream productivity, developer, home-automation, and messaging vocabulary. The audit was worth doing but the outcome is that JaiClaw's bundled catalog is safe as-is.

---

## Recommendations

1. **Reword the three `bypass` occurrences** in `coding-agent`, `systematic-debugging`, and `e2e-test` (see suggested wording above). Cost: 5 minutes. Zero-regret because the descriptions become more direct anyway.

2. **Adopt the Hermes-style pre-1.0 policy**: any new bundled skill must pass this grep sweep before merge. Add the grep as a step in the `jaiclaw-maven-plugin analyze` goal — the same plugin that already catches missing `allow-bundled` config in examples.

3. **Preemptively define a "bundled vs optional" split** for skills that might get authored later. Move anything red-teaming / security-research / model-behavior-analysis into an optional catalog that operators must explicitly install. Never bundle those.

4. **Do a live-run A/B test after any bundled-skill addition** — run a benign query through Claude with the bundled catalog injected, compare block rate to a control without the catalog. Same test methodology Nous used. This is a 15-minute check, not a formal audit.

5. **Document the classifier-safety policy in `docs/user/AUTHORING-SKILLS.md`** as a "what NOT to write in a description" section for community skill authors.

---

## Follow-up: enforcement

The `bypass` rewrites are trivial and should ship in the same commit as this audit. Adding the grep to `jaiclaw-maven-plugin analyze` is a separate small task tracked as: **new task — extend `AnalyzeMojo` to grep bundled skill descriptions for classifier-safety terms and fail with a clear rewrite suggestion.**
