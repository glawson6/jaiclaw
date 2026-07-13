# 08 — Execution Plan

> **The operational document.** Each phase is designed to be executed in a single focused session (human or AI agent). Update the STATUS line as you go. Do not start a phase whose preconditions aren't met.
>
> Global conventions for every phase:
> - Work on branch `spring-boot-4-upgrade`. Commit per completed step with plain messages (repo rule: no AI attribution).
> - `export JAVA_HOME=/Users/tap/.sdkman/candidates/java/21.0.9-oracle` before any `./mvnw`.
> - First build after new deps: run **online** (no `-o`) to populate the local repo; use `-o` afterwards. Nexus (tooling.taptech.net) timeouts → `-o`.
> - After touching `jaiclaw-spring-boot-starter`: `./mvnw install -pl :jaiclaw-spring-boot-starter -DskipTests` before testing apps (CLAUDE.md rule).
> - Every phase ends with the **multi-tenancy conformance check** (CLAUDE.md checklist) over its diff and an update to this file's STATUS lines.

## Phase 0 — Boot-3-compatible prep (do now; no Boot 4 anywhere)

**STATUS: NOT STARTED**
**Preconditions:** none. Everything here lands on Boot 3.5 and is releasable even if the Boot-4 gate drags.

| # | Step | Verify |
|---|---|---|
| 0.1 | Bump Spring Boot 3.5.15 → **3.5.16** (final OSS patch) | `./mvnw compile && ./mvnw test` full reactor |
| 0.2 | **Embabel 0.3.5 → 0.5.0** (`embabel-agent.version`) — same Boot 3.5/Spring AI 1.1.7 stack ([02 §3a](02-embabel-gate.md)). Re-verify Layer-1 registered model names vs 0.5.0 | `./mvnw test -pl :jaiclaw-embabel-delegate,:jaiclaw-agent -o`; run `camel-html-summarizer-embabel` example smoke |
| 0.3 | **Remove Spring State Machine engine** from jaiclaw-kanban (7 files) per [07 §3](07-camel-and-other-deps.md); keep `TaskStateEngine` SPI; drop root-pom property | `./mvnw test -pl :jaiclaw-kanban -o`; kanban-demo example + `.claude/skills/kanban-e2e` run |
| 0.4 | **RestTemplate → RestClient** (38 files; RestClient exists since Framework 6.1). Do it here so behavior diffs are attributable to the client swap, not Boot 4. Keep SsrfGuard wiring intact on web-fetch paths | per-module specs; channel-adapter integration smoke (telegram/slack webhook echo) |
| 0.5 | Audit + fix stragglers cheaply fixable on 3.5: `spring.factories` `EnvironmentPostProcessor` FQNs (can adopt new-style once on 4.x only — just inventory here), spring-retry usage inventory, jjwt/Jackson-3 support check, Testcontainers usage inventory, `HttpMessageConverters`/`ListenableFuture`/`AntPathRequestMatcher` greps ([03](03-spring-boot-4-core-changes.md)) | grep outputs recorded below in "Phase 0 findings" |
| 0.6 | Optional-but-smart: release these as **0.9.5** so pilots get them under Boot 3.5 | release checklist per releases/ convention |

**Phase 0 findings (recorded during execution 2026-07-13):**
- **spring-retry users**: **0** — no `spring-retry` deps, `@Retryable`, `RetryTemplate`, or `@EnableRetry` usage anywhere in the repo. **Nothing to pin in Phase 1.**
- **jjwt Jackson-3 status**: jjwt **0.12.6** used in `core/jaiclaw-security` (BOM-managed) and `extensions/jaiclaw-tools-security` (explicit `<jjwt.version>0.12.6</jjwt.version>`) — 3 modules × `jjwt-api`/`jjwt-impl`/`jjwt-jackson`. **Verify 0.12.6 compatibility with Jackson 3 in Phase 2**; if incompatible, bump to a jjwt release that ships a Jackson-3 variant (or swap the `jjwt-jackson` bridge module).
- **Testcontainers usage**: only `extensions/jaiclaw-tasks` — `org.testcontainers:testcontainers` + `com.redis:testcontainers-redis` + `PostgreSQLContainer`. Specs: `RedisTaskStoreContractSpec`, `JdbcPostgresTaskStoreContractSpec`, `RedisTaskStoreProviderSpec`. **Phase 7 must handle Testcontainers 2.x module renames** (`org.testcontainers:mysql` → `org.testcontainers:testcontainers-mysql` pattern) if we adopt 2.x with Boot 4.
- **`ListenableFuture` / `HttpMessageConverters` / `AntPathRequestMatcher` / `antMatchers`**: **0 hits each** — Framework 7 API removals are non-issues for this repo.
- **`EnvironmentPostProcessor` old-package FQN**: 3 files use `org.springframework.boot.env.EnvironmentPostProcessor` — must move to `org.springframework.boot.EnvironmentPostProcessor` in Phase 1.4:
  - `extensions/jaiclaw-compliance/src/main/java/io/jaiclaw/compliance/ComplianceEnvironmentPostProcessor.java`
  - `jaiclaw-spring-boot-starter/src/main/java/io/jaiclaw/autoconfigure/JaiClawBannerEnvironmentPostProcessor.java`
  - `jaiclaw-spring-boot-starter/src/main/java/io/jaiclaw/autoconfigure/secrets/SecretsEnvironmentPostProcessor.java`
  Plus their `META-INF/spring.factories` registrations (2 files: `jaiclaw-spring-boot-starter/src/main/resources/META-INF/spring.factories`, `extensions/jaiclaw-compliance/src/main/resources/META-INF/spring.factories`) — the `spring.factories` key name is the interface FQN, so it also changes.
- **JSpecify `@Nullable` on actuator endpoints (Phase 6b preview)**: `PipelineActuatorEndpoint` already uses `org.jspecify.annotations.Nullable` ✅. `KanbanActuatorEndpoint` and `TendenciesActuatorEndpoint` have no nullable `@Selector`/params — nothing to convert.

## Phase 1 — Boot 4.1 version wave (branch goes red, then green module-by-module)

**STATUS: NOT STARTED**
**Preconditions:** Phase 0 done. Read [01](01-dependency-matrix.md) + [03](03-spring-boot-4-core-changes.md). Accept that the reactor will not fully compile until Phases 2–5 land — drive it green in dependency order: `jaiclaw-core → channel-api → config → tools → agent/skills/plugin-sdk/memory/security → gateway → channels → extensions → starter(s) → apps → examples`.

| # | Step |
|---|---|
| 1.1 | Root pom: `spring-boot.version` → 4.1.x, `spring-cloud.version` → 2025.1.x, `spring-shell.version` → 4.0.x, `spock.version` → 2.4-groovy-5.0, `groovy.version` → 5.0.x, `gmavenplus` → 5.0.0, `jkube.version` → 1.19.0, `spring-ai.version` → 2.0.0, `embabel-agent.version` → Boot-4 snapshot ([02 §3b](02-embabel-gate.md)); **remove Tomcat 10.1 pin**, re-evaluate Netty pin |
| 1.2 | Run OpenRewrite `UpgradeSpringBoot_4_0` (rewrite-spring ≥ 6.34.0) as a first pass — review diff hard, it doesn't know 4.1 or our conventions |
| 1.3 | Starter renames repo-wide: `spring-boot-starter-web` → `spring-boot-starter-webmvc` (42 poms) + the other renames table in [03 §1](03-spring-boot-4-core-changes.md). Option: temporary `spring-boot-starter-classic` in stubborn leaf modules, tracked for removal in Phase 6 |
| 1.4 | `EnvironmentPostProcessor` package move: `SecretsEnvironmentPostProcessor`, `ComplianceEnvironmentPostProcessor` + both `spring.factories` files |
| 1.5 | Fix Boot auto-config class references that moved into tech modules (grep `org.springframework.boot.autoconfigure` beyond annotations; add `spring-boot-jackson`, `spring-boot-micrometer-observation`, etc. as needed) |
| 1.6 | Add `spring-boot-properties-migrator` (runtime) to gateway-app, shell, cli |
| 1.7 | Drools → 10.1.0 ([07 §4](07-camel-and-other-deps.md)); Camel → 4.21.x in jaiclaw-camel (compile-only here; behavior in Phase 6/7) |

**Verify:** `./mvnw compile -pl :jaiclaw-core,:jaiclaw-channel-api,:jaiclaw-config -am` green; record remaining red modules as the Phase 2–5 worklist.

## Phase 2 — Jackson 3 (225 files)

**STATUS: NOT STARTED**
**Preconditions:** Phase 1. Read [04](04-jackson-3-migration.md) fully — especially the §2.6 persisted-format hotspots and the §4 defaults decision.

Steps: poms (42+13+5) → imports (guarded rewrite, annotations excepted) → `ObjectMapper`→`JsonMapper.builder()` sites → delete JavaTime registrations → exception-type fixes → **cross-version fixtures for audit chain / transcripts / cron / identity / kanban ledger** → decide `use-jackson2-defaults` (record in README decision log as D7).

**Verify:** verification block in [04 §5](04-jackson-3-migration.md); all named hotspot module specs green.

## Phase 3 — Spring AI 2.0

**STATUS: NOT STARTED**
**Preconditions:** Phases 1–2. Read [05](05-spring-ai-2-migration.md).

Steps: run Spring AI OpenRewrite recipes → `SpringAiToolBridge` + tool-calling (§3) → ChatModel decorators/BPPs (§4) → provider starters incl. azure/oci/minimax decisions (§2) → MCP hosting + SDK 2.x (§5) → property renames across 46 ymls + `start.sh` env plumbing (§1) → `AgentRuntime` nullability + token INFO log (§4).

**Verify:** block in [05 §7](05-spring-ai-2-migration.md). Record the azure-openai/oci-genai starter decisions in the README decision log.

## Phase 4 — Embabel adoption

**STATUS: NOT STARTED (gated for release; snapshot-executable now)**
**Preconditions:** Phases 1–3; Boot-4 Embabel snapshot resolvable ([02 §3b](02-embabel-gate.md)) — else build Embabel's branch locally from the sibling checkout.

Steps: pin snapshot → recompile `jaiclaw-embabel-delegate` (AgentPlatform APIs unverified across the 0.x→1.5/2.0 boundary) → `jaiclaw-starter-embabel` + starter auto-config → three-layer config re-verification incl. **MiniMax-via-Anthropic base-url** + registered model names → `MiniMaxThinkingFilter` keep/simplify decision → spring-retry explicit pin if needed → examples (`camel-html-summarizer-embabel`, travel-planner, code-review-bot, pipeline-e2e EMBABEL stages).

**Verify:** `./mvnw test -pl :jaiclaw-embabel-delegate -o`; live GOAP round-trip via the summarizer example; document any Embabel-snapshot workarounds needed (they become gate-criteria items in [02 §2](02-embabel-gate.md)).

## Phase 5 — Spring Shell 4 rewrite (~35 files)

**STATUS: NOT STARTED**
**Preconditions:** Phase 1 (Shell 4 needs Boot 4 on the classpath). Read [06](06-spring-shell-4-migration.md).

Order: `jaiclaw-shell-commands` (25 files) → shell/cli/cron-manager apps → 5 tools (dual-mode `-Pstandalone` retest) → 8 examples → **non-interactive alias matrix retest** (the CLAUDE.md multi-word-key caveat) → `start.sh` / `bin/jaiclaw` / e2e script compatibility.

**Verify:** invocation matrix in [06 §4](06-spring-shell-4-migration.md).

## Phase 6 — Security 7, actuator, web, cleanup

**STATUS: NOT STARTED**
**Preconditions:** Phases 1–3.

Steps: 2 `HttpSecurity` files → lambda-DSL/`PathPatternRequestMatcher` ([03 §5](03-spring-boot-4-core-changes.md)) → actuator endpoints JSpecify `@Nullable` (3 endpoints) → `management.tracing.*` renames → probes-by-default review (gateway health groups, e2e assertions) → WebSocket surface integration test → gateway `/mcp/*` + SSE + stdio bridge smoke → **remove every `spring-boot-starter-classic`** introduced in Phase 1 → GDPR/compliance surfaces (`/api/gdpr/*`, `AuditingChatModelBeanPostProcessor` idempotency, `HashChainedAuditLogger.verifyChain` on pre-migration fixture) → security-hardened profile YAML re-check.

**Verify:** `./mvnw test -pl :jaiclaw-security,:jaiclaw-gateway,:jaiclaw-compliance,:jaiclaw-audit -o`; gateway boots via `./start.sh local`; `/actuator/health` shows liveness/readiness groups; `curl http://localhost:8888/mcp` docs-server flow works.

## Phase 7 — Test suite, examples, CI, images

**STATUS: NOT STARTED**
**Preconditions:** Phases 1–6 compiling. Read [09](09-validation-and-rollback.md).

Steps: smoke one `spock-spring` context spec module first → full reactor test run, triage by failure class (MockMvc auto-config removals, Jackson defaults, Spock `.with{}` semantics) → 41 examples: build + `jaiclaw:analyze` token budgets (INFO-log check per CLAUDE.md) → CI workflows (5) still temurin-21-valid; surefire/JUnit-Platform-2 check → JKube 1.19 image builds `-Pk8s` for gateway-app + shell; probe YAML verification → e2e skill (`e2e-test`) full pass → `./mvnw verify` JaCoCo gates hold (≥50% core modules).

**Verify:** `./mvnw test` full reactor green; `./mvnw package k8s:build -pl :jaiclaw-gateway-app,:jaiclaw-shell -am -Pk8s -DskipTests`; e2e pass.

## Phase 8 — Docs, release notes, 1.0.0 mechanics

**STATUS: NOT STARTED**
**Preconditions:** Phases 0–7 done; **Embabel GA published (gate — [02 §2](02-embabel-gate.md))**; pin GA version, delete snapshot repo need; remove `spring-boot-properties-migrator`.

| # | Step |
|---|---|
| 8.1 | Docs sweep: **CLAUDE.md** (build commands, version-alignment section, Embabel three-layer config, Shell 4 pattern, directory notes), `docs/dev/ARCHITECTURE.md`, `docs/user/OPERATIONS.md`, `docs/user/OLLAMA-TUNING-GUIDE.md` (spring.ai property shapes), developer guide + satellites (per CLAUDE.md doc-maintenance rule), **`docs/dev/RELEASE-PLAN-1.0.0.md` "2.0 ceiling" section superseded → point here** |
| 8.2 | `releases/release-1.0.0.md` per repo template: highlights, breaking changes (Boot 4.1/Framework 7, Jackson 3, Spring AI 2.0 property renames, Shell 4 command syntax, SSM engine removal, starter renames for adopters), dependency table, CVE posture (Tomcat 11, dropped pins) |
| 8.3 | Adopter migration guide: `docs/dev/MIGRATION-1.0.md` (consolidates 0.9.x→1.0 per RELEASE-PLAN + the Boot-4 adopter-facing changes: our starters now require Boot 4.1+, property renames, Shell 4) |
| 8.4 | Reconcile with the 1.0 gates in RELEASE-PLAN-1.0.0.md (30-day pilot window, Central verification) — decide whether the pilot window re-runs on the Boot-4 build (recommended: yes, it's a bigger break than 0.9.3) — **owner decision, record as D8** |
| 8.5 | Version cut per RELEASE-PLAN §"The cut itself" (`versions:set 1.0.0`, tag, `maven-central-deploy/release.sh`), `-Prelease` dry run first (repackage-skip behavior vs boot-maven-plugin 4.x) |

**Verify:** release dry run publishes cleanly to staging; `release-1.0.0.md` complete; all STATUS lines in this file DONE.
