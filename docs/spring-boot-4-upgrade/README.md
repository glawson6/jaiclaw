# Spring Boot 4 Upgrade Plan — JaiClaw 1.0.0

> **Status**: PLANNING — research complete 2026-07-13, execution not started.
> **Branch**: `spring-boot-4-upgrade` (currently identical to `main` @ 0.9.4-SNAPSHOT).
> **Decision**: The 1.0.0 release includes the Spring Boot 4 upgrade. This **supersedes the "2.0 ceiling" section of [../dev/RELEASE-PLAN-1.0.0.md](../dev/RELEASE-PLAN-1.0.0.md)**, which deferred Boot 4 to a post-1.0 "JaiClaw 2.0". That section must be updated when this plan is accepted.

## How to use these docs

This is a hub-and-satellite doc set designed to be executed **phase by phase, each phase in its own working session** (human or AI agent). Rules of engagement:

- **Start here**, then open [08-execution-plan.md](08-execution-plan.md) — it is the operational document. Every phase there is self-contained: goal, preconditions, steps, files affected, and a verification block you must run before marking the phase done.
- The numbered satellite docs (01–07) are **reference material** for the phases: read the ones the phase links to, not all of them.
- When executing a phase, **update the phase's status line in 08-execution-plan.md** (`NOT STARTED → IN PROGRESS → DONE + date`) so the next session can pick up where you left off.
- All external claims in these docs carry source links captured 2026-07-13. If you are reading this significantly later, re-verify the **Embabel gate** ([02-embabel-gate.md](02-embabel-gate.md)) first — it is the only thing we are waiting on.

## Executive summary

JaiClaw today: **Spring Boot 3.5.15 / Spring Framework 6.2 / Spring AI 1.1.7 / Embabel 0.3.5 / Spring Shell 3.4.2 / Camel 4.18.2 / Java 21**, 162 Maven modules, 900+ Spock tests.

Target: **Spring Boot 4.1.x / Spring Framework 7.0.x / Spring AI 2.0.x / Embabel (Boot-4 GA line) / Spring Shell 4.0.x / Camel 4.21+ / Java 21** (Boot 4 baseline is Java 17; we stay on 21).

Why now (and why 4.1.x, not 4.0.x):

1. **Spring Boot 3.5 OSS support ended 2026-06-30.** The current stack is on an unsupported OSS line; 3.5.16 (2026-06-25) was the final OSS patch. ([endoflife.date](https://endoflife.date/spring-boot), [support policy](https://spring.io/support-policy/))
2. **Boot 4.1.0 (GA 2026-06-10) restores Spock support** (dropped in 4.0 over Groovy 5). With a 100%-Spock test suite, 4.1 is the only sensible target. ([4.1 release notes](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.1-Release-Notes))
3. **Embabel's own Boot-4 branch pins Boot 4.1.0 + Spring AI 2.0.0 GA** — targeting 4.1 keeps us aligned with our binding Tier-1 dependency. ([embabel-build 2.0.0 pom](https://raw.githubusercontent.com/embabel/embabel-build/2.0.0/pom.xml))
4. Spring AI 2.0.0 GA shipped 2026-06-12 and is the **only** Spring AI line that supports Boot 4. ([announcement](https://spring.io/blog/2026/06/12/spring-ai-2-0-0-GA-available-now/))

## The one hard gate

**Embabel has no Boot-4-compatible release as of 2026-07-13.** Latest GA is 0.5.0 (2026-06-21), which targets Boot 3.5.14 / Spring AI 1.1.7. Boot 4 support exists only on unreleased draft branches (`1.5.0`, `2.0.0`); [issue #1052](https://github.com/embabel/embabel-agent/issues/1052) is open with label `release-2.0`. A Maven Central release **cannot ship with a SNAPSHOT dependency**, so:

> **JaiClaw 1.0.0 with Boot 4 cannot be cut until Embabel publishes a Boot-4 GA (or at minimum a non-SNAPSHOT milestone).**

Everything else in this plan is executable now. The strategy is therefore: do all Boot-3-compatible prep immediately (Phase 0), build and validate the full Boot 4 migration on this branch against Embabel snapshots (Phases 1–6), and cut 1.0.0 the moment Embabel GAs. Full detail and monitoring instructions: [02-embabel-gate.md](02-embabel-gate.md).

## Target version matrix (summary)

| Dependency | Current | Target | Status |
|---|---|---|---|
| Java | 21 | 21 (runtime: consider JDK 25 LTS later) | ✅ compatible |
| Spring Boot | 3.5.15 | **4.1.0** (latest 4.1.x at execution time) | ✅ available |
| Spring Framework | 6.2.x (managed) | 7.0.x (managed by Boot 4.1) | ✅ available |
| Spring AI | 1.1.7 | **2.0.0** | ✅ available |
| Embabel Agent | 0.3.5 | **Boot-4 GA line (1.5.0 or 2.0.0)** | 🔴 **GATE — unreleased** |
| Spring Shell | 3.4.2 | **4.0.2+** (full annotation rewrite) | ✅ available |
| Spring Cloud | 2025.0.2 | **2025.1.2+** (Oakwood) | ✅ available |
| Apache Camel | 4.18.2 (LTS) | **4.21.0+** (non-LTS; first Boot-4 line is 4.19) | ⚠️ off-LTS |
| Spring State Machine | 4.0.1 (optional kanban engine) | **REMOVE** — "no plan to support Spring Boot 4" | 🔴 drop |
| Jackson | 2.x (`com.fasterxml.jackson`) | **3.x (`tools.jackson`)** — annotations stay Jackson 2 coords | ✅ available |
| Spock / Groovy | 2.4-groovy-4.0 / 4.0.30 | **2.4 (groovy-5.0 variant) / Groovy 5.0.x** | ✅ available (4.1 only) |
| GMavenPlus | 4.0.1 | **5.0.0** | ✅ available |
| JKube | 1.17.0 | **1.19.0** (Boot-4 probe fix) | ✅ available |
| Tomcat (CVE override) | 10.1.55 pinned | Tomcat 11 via Boot BOM — **re-evaluate override** | ✅ |
| Netty (CVE override) | 4.1.135.Final pinned | re-evaluate vs Boot 4.1 BOM | ✅ |
| Drools | 9.44 (last 9.x) | **10.1.0** (Jakarta-complete line) — recommended | ✅ available |

Full matrix with every affected coordinate and source links: [01-dependency-matrix.md](01-dependency-matrix.md).

## Migration surfaces in this repo (audit of 2026-07-13)

| Surface | Size | Doc |
|---|---|---|
| Jackson 2 → 3 imports | **225 files** import `com.fasterxml.jackson`; 42 poms declare `jackson-databind`; 13 declare `jackson-datatype-jsr310` (built into Jackson 3 — remove); 2 `jjwt-jackson` (verify Jackson-3 support) | [04](04-jackson-3-migration.md) |
| Own auto-configuration | 61 `@AutoConfiguration` files, 33 `@AutoConfigureAfter`, 41 `AutoConfiguration.imports` files, 2 `spring.factories` (`EnvironmentPostProcessor` package moved in Boot 4) | [03](03-spring-boot-4-core-changes.md) |
| `spring-boot-starter-web` → `webmvc` rename | 42 poms | [03](03-spring-boot-4-core-changes.md) |
| Spring AI API usage | 37 files (hotspots: `SpringAiToolBridge`, `AgentRuntime`, `AuditingChatModel` + BeanPostProcessor, `MiniMaxThinkingFilterAutoConfiguration`, MCP hosting); 46 ymls with `spring.ai.*.options.*` properties (`.options` segment removed in 2.0) | [05](05-spring-ai-2-migration.md) |
| Embabel integration | 27 source files, 16 poms (`jaiclaw-embabel-delegate`, `jaiclaw-starter-embabel`, `jaiclaw-agent`, examples) | [02](02-embabel-gate.md) |
| Spring Shell commands | ~35 files with `@ShellComponent`/`@ShellMethod` across `jaiclaw-shell-commands` (25), `jaiclaw-cli`, `cron-manager-app`, 5 tools, 8 examples — **every one rewritten** for Shell 4's `@Command` model | [06](06-spring-shell-4-migration.md) |
| RestTemplate (deprecated in Framework 7) | 38 files (→ `RestClient`, can start on Boot 3.5) | [03](03-spring-boot-4-core-changes.md) |
| Actuator endpoints | 3 custom `@Endpoint` classes (pipeline, kanban, tendencies) — JSpecify `@Nullable` required for optional params | [03](03-spring-boot-4-core-changes.md) |
| Spring Security 7 | 2 files using `HttpSecurity` (verify lambda-DSL-only) + JWT/timing-safe auth code | [03](03-spring-boot-4-core-changes.md) |
| Spring State Machine | 7 files in `jaiclaw-kanban` (optional `TaskStateEngine` backend) — remove/deprecate | [07](07-camel-and-other-deps.md) |
| Tests | 900+ Spock specs, `spock-spring` in 11 poms, `spring-boot-starter-test` in 18 poms; `@SpringBootTest` no longer auto-configures MockMvc/TestRestTemplate | [09](09-validation-and-rollback.md) |
| CI / Docker | 5 GitHub workflows (temurin 21), JKube images `eclipse-temurin:21-jre`, e2e skill | [09](09-validation-and-rollback.md) |

## Phase overview

Detail, checklists, and verification commands: [08-execution-plan.md](08-execution-plan.md).

| Phase | Name | Boot version | Gated on Embabel? |
|---|---|---|---|
| 0 | Boot-3-compatible prep (Embabel 0.5.0, Boot 3.5.16, RestClient migration, drop Spring State Machine, jjwt/Testcontainers checks) | 3.5.x | No |
| 1 | Core version-bump wave: poms, BOMs, starter renames, CVE-override re-evaluation | 4.1.x | No (snapshots OK on branch) |
| 2 | Jackson 3 migration (225 files) | 4.1.x | No |
| 3 | Spring AI 2.0 migration (bridge, decorators, MCP, properties) | 4.1.x | No |
| 4 | Embabel adoption (delegate, starter, examples) | 4.1.x | **Snapshot now; GA to release** |
| 5 | Spring Shell 4 rewrite | 4.1.x | No |
| 6 | Security 7 / actuator / compliance / gateway fixes | 4.1.x | No |
| 7 | Test suite, examples, CI, Docker/JKube | 4.1.x | No |
| 8 | Docs, release notes, 1.0.0 mechanics | 4.1.x | **Yes — GA required to cut** |

## Decision log

| # | Decision | Rationale | Date |
|---|---|---|---|
| D1 | Boot 4 ships in 1.0.0 (supersedes RELEASE-PLAN-1.0.0.md "2.0 ceiling") | Owner decision; 3.5 OSS EOL passed 2026-06-30 | 2026-07-13 |
| D2 | Target Spring Boot **4.1.x**, not 4.0.x | Spock 2.4 support restored in 4.1; Embabel 2.0.0 branch pins 4.1.0; owner preference | 2026-07-13 |
| D3 | Embabel GA is a hard gate for the 1.0.0 cut; snapshots allowed on the branch only | Central rejects SNAPSHOT deps; Embabel is Tier-1 binding | 2026-07-13 |
| D4 | Remove the Spring State Machine kanban engine | Upstream: "no plan to support Spring Boot 4" ([#1207](https://github.com/spring-projects/spring-statemachine/issues/1207)); default transition-graph engine already exists | 2026-07-13 |
| D5 | Stay on Java 21 for build/runtime baseline | Boot 4 minimum is 17; no consumer-facing reason to force 25; revisit post-1.0 | 2026-07-13 |
| D6 | Camel moves off LTS (4.18 → 4.21+) | Camel 4.19 is the first Boot-4 line and drops Boot 3 in the same release; no Boot-4 LTS exists yet — adopt next Camel LTS when it ships | 2026-07-13 |

## Doc map

| Doc | Contents |
|---|---|
| [01-dependency-matrix.md](01-dependency-matrix.md) | Full current→target version matrix, every affected Maven coordinate, source links |
| [02-embabel-gate.md](02-embabel-gate.md) | Issue #1052 history, Embabel migration wiki digest, gate criteria, snapshot strategy, monitoring |
| [03-spring-boot-4-core-changes.md](03-spring-boot-4-core-changes.md) | Boot 4 / Framework 7 changes mapped to JaiClaw code: modularization, starter renames, EnvironmentPostProcessor move, JSpecify, Security 7, actuator, HTTP clients, testing |
| [04-jackson-3-migration.md](04-jackson-3-migration.md) | Jackson 2→3 rules, what stays, pom changes, mechanical recipe |
| [05-spring-ai-2-migration.md](05-spring-ai-2-migration.md) | Spring AI 1.1.7→2.0.0: tool-calling, ChatModel/ChatClient, MCP hosting, provider starters, property renames, MiniMax routing |
| [06-spring-shell-4-migration.md](06-spring-shell-4-migration.md) | Shell 3.4→4.0 annotation rewrite, affected modules, non-interactive alias caveat |
| [07-camel-and-other-deps.md](07-camel-and-other-deps.md) | Camel 4.21, Spring Cloud, State Machine removal, Drools 10, JKube, Spock/Groovy 5, Testcontainers 2, dependency-check |
| [08-execution-plan.md](08-execution-plan.md) | **The operational plan**: phases 0–8 with steps, file lists, and verification blocks |
| [09-validation-and-rollback.md](09-validation-and-rollback.md) | Test strategy, module build order, token-budget gates, e2e, CI, rollback and branch strategy |
