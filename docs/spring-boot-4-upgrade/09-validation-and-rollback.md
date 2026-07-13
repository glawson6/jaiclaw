# 09 — Validation, Test Strategy, and Rollback

## 1. Module build order (drive the reactor green in this sequence)

Per the dependency graph in CLAUDE.md — each group must compile (and ideally pass specs) before the next:

1. `jaiclaw-core` (Spring-free — Jackson-only changes; if it compiles with zero Spring deps still, the architecture invariant holds)
2. `jaiclaw-channel-api`, `jaiclaw-config`, `jaiclaw-ascii-render`
3. `jaiclaw-tools` (SpringAiToolBridge — first Spring AI 2.0 contact)
4. `jaiclaw-agent`, `jaiclaw-skills`, `jaiclaw-plugin-sdk`, `jaiclaw-memory`, `jaiclaw-security`
5. `jaiclaw-gateway` + remaining core
6. `channels/*` (7)
7. `extensions/*` (43 — camel, pipeline, kanban, compliance, agentmind, rules are the heavy ones)
8. `jaiclaw-spring-boot-starter` → `jaiclaw-starters/*` (31) — **install starter before app testing**
9. `tools/*`, `apps/*` (Shell 4 dependent)
10. `jaiclaw-examples/*` (41)

```bash
export JAVA_HOME=/Users/tap/.sdkman/candidates/java/21.0.9-oracle
./mvnw compile -pl :jaiclaw-core,:jaiclaw-channel-api,:jaiclaw-config -am      # group 1–2 gate
./mvnw install -DskipTests                                                      # once green, enables -pl -o everywhere
```

## 2. Expected test-failure classes (triage cheat-sheet for Phase 7)

| Symptom | Cause | Fix |
|---|---|---|
| `MockMvc`/`TestRestTemplate` bean missing in `@SpringBootTest` specs | Boot 4 no longer auto-configures them | `@AutoConfigureMockMvc` / `@AutoConfigureTestRestTemplate` per spec |
| `ClassNotFoundException` on Boot auto-config class in slice tests/exclusions | modularization moved it | new module dep + updated FQN ([03 §1](03-spring-boot-4-core-changes.md)) |
| JSON assertion diffs (dates as ISO strings, unknown props silently accepted) | Jackson 3 default changes | fixture updates or `use-jackson2-defaults` decision ([04 §4](04-jackson-3-migration.md)) |
| "unreachable catch" compile errors near Jackson calls | checked → unchecked `JacksonException` | rewrite catch blocks |
| Spec assertions inside `.with {}` blocks silently pass/fail oddly | Spock 2.4 dropped `.with{}` condition-block magic | convert to `verifyAll` or explicit asserts |
| Context-load failures naming `ObservationRegistryCustomizer`, `Jackson2ObjectMapperBuilder` | Boot 4 module split / removal | `spring-boot-micrometer-observation` dep; `JsonMapper.builder()` ([02](02-embabel-gate.md), [04](04-jackson-3-migration.md)) |
| Shell specs: command not found / key mismatch | Shell 4 annotation + runner model | [06](06-spring-shell-4-migration.md) |
| Missing test libs (JSONassert etc.) | slimmed `spring-boot-starter-test` | explicit dep or `-test-classic` (temporary) |
| `spring-retry` unresolved version | dropped from Boot BOM | explicit 2.0.12 pin |

## 3. Behavioral (non-compile) verification checklist

These can pass compilation and still be broken — each needs an explicit check:

- [ ] **Audit hash chains**: `HashChainedAuditLogger.verifyChain(tenantId)` over a pre-migration JSON-lines fixture file (Jackson 3 serialization drift breaks tamper-evidence claims).
- [ ] **Persisted JSON re-reads**: cron jobs, identity links, kanban `EffectLedger` + jsonl journal, transcripts, docstore metadata — load files written by 0.9.x.
- [ ] **MCP wire compatibility**: Claude Code `.mcp.json` → docs server (`resources/list`, `resources/read`, `search_docs`) on port 8888; messaging/calendar/kanban MCP tools against strict input validation (now on by default).
- [ ] **Token budget INFO log** still emitted by `AgentRuntime` (CLAUDE.md skills discipline depends on it) + `jaiclaw:analyze` runs in every example verify phase.
- [ ] **Three-layer Embabel model routing**: `default-llm` registry name resolution, MiniMax Anthropic-endpoint call, thinking-filter behavior.
- [ ] **Actuator**: `/actuator/pipelines`, `/actuator/kanban`, health liveness/readiness groups; `/actuator/env` shows `jaiclaw.compliance.effective.*`.
- [ ] **Security hardening flags** (per-flag smoke under `security-hardened` profile): Slack HMAC verify, Telegram secret token, SSRF guard, workspace boundary, timing-safe API key.
- [ ] **GDPR endpoints**: export + erasure round-trip with tenant context; 403 without.
- [ ] **Channel webhooks**: one inbound+outbound echo per adapter (telegram, slack, discord, email, sms) — RestClient migration + Boot 4 MVC together.
- [ ] **Pipelines**: trigger each type (MANUAL/HTTP/FILE/CRON-quartz/CAMEL_URI) once on Camel 4.21; DEAD_LETTER path; SEDA + Kafka transport with HMAC auth.
- [ ] **WebSocket** gateway session end-to-end.
- [ ] **OAuth logins** (`start.sh login chutes|openai-codex|google-gemini-cli|qwen-portal|minimax-portal`) — the OpenAI-path base-url changes make these regression-prone.
- [ ] **Docker images**: gateway-app + shell boot in Docker; JKube probe YAML correct; `start.sh` / `start.sh cli` flows.
- [ ] **e2e-test skill** full run (bootstrap, scaffolding via quickstart.sh, provider connectivity) — note quickstart/scaffolder templates must emit Boot-4 poms after this migration (scaffolder output is part of the product!).
- [ ] **i18n**: one non-English locale smoke (ResourceBundle loading unaffected in theory).

## 4. CI

- 5 workflows (`unit-tests`, `coverage`, `e2e-tests`, `security-deps`, `bom-version-guard`, `publish-central`): temurin 21 stays valid. Check: surefire JUnit-Platform version, `bom-version-guard` rules vs the new version set, dependency-check 12.2.x behavior on the new tree (expect new CVE noise from non-LTS Camel — budget triage time).
- Add a temporary CI job on the branch running the reactor against the Embabel **snapshot** so upstream drift is caught weekly while gated.

## 5. Rollback & branch strategy

- **`main` stays Boot 3.5-releasable at all times** until Phase 8. All Boot-4 work on `spring-boot-4-upgrade`; rebase onto main regularly (Phase 0 lands on main directly — it's Boot-3-safe).
- Phase 0 output optionally ships as **0.9.5** (Embabel 0.5.0, RestClient, SSM removal) — de-risks the big branch AND gives pilots the breaking SSM change early.
- If the Embabel gate drags past the 1.0 window: **fallback = cut 1.0.0 from main on Boot 3.5.16** (the original RELEASE-PLAN path), re-scope this plan as 2.0. The phase structure survives either way; only the version number changes. Owner decision point — revisit if no Embabel GA by **2026-09-01** (Boot 3.5 being OSS-EOL argues against waiting indefinitely; commercial-support or HeroDevs-style options exist as stopgaps but are off-strategy).
- Tag before each phase merge (`sb4-phase-N-done`) so bisecting a behavioral regression has clean anchors.
- Nothing in Phases 1–7 is force-pushed over; every phase is revertable as a commit range.

## 6. Definition of done (1.0.0 with Boot 4)

1. All phases DONE in [08-execution-plan.md](08-execution-plan.md); Embabel GA pinned (no snapshot repos required to build).
2. Full reactor `./mvnw verify` green (tests + JaCoCo + jaiclaw:analyze budgets).
3. §3 behavioral checklist fully ticked.
4. Docs sweep done (CLAUDE.md, ARCHITECTURE, OPERATIONS, dev guide, MIGRATION-1.0, release-1.0.0.md).
5. `-Prelease` staging dry run clean; then the RELEASE-PLAN cut sequence.
