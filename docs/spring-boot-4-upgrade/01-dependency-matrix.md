# 01 — Dependency Matrix

> All versions verified 2026-07-13. Re-check "latest patch" columns at execution time.
> Legend: ✅ ready · ⚠️ ready with caveats · 🔴 blocker/gate

## Platform

| Dependency | Current (root pom) | Target | Notes / source |
|---|---|---|---|
| Java (`java.version`) | 21 | **21** | Boot 4 baseline is Java 17; 4.0 supports 17–25, 4.1 adds 26. JDK 25 LTS is Spring's recommended runtime — optional post-migration bump for Docker images. [Boot 4.0 release notes](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Release-Notes) |
| Spring Boot (`spring-boot.version`) | 3.5.15 | **4.1.0+** ✅ | 4.0 GA 2025-11-20 (latest 4.0.7); 4.1 GA 2026-06-10. **3.5 OSS support ended 2026-06-30** (final OSS patch 3.5.16). [endoflife.date](https://endoflife.date/spring-boot) · [4.1 announcement](https://spring.io/blog/2026/06/10/spring-boot-4/) |
| Spring Framework | 6.2.x (managed) | **7.0.x** (7.0.8 ships with Boot 4.1.0) ✅ | Managed by Boot BOM. [Framework 7.0 release notes](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-7.0-Release-Notes) |
| Jakarta EE | EE 10 | **EE 11** (Servlet 6.1, Validation 3.1, Annotation 3.0, WebSocket 2.2) ✅ | Managed by Boot. JaiClaw's only `javax.*` imports are JDK packages (`javax.crypto`, `javax.sql`) — no Jakarta work needed in our code. |

## The gate

| Dependency | Current | Target | Notes |
|---|---|---|---|
| Embabel Agent (`embabel-agent.version`) | 0.3.5 | 🔴 **Boot-4 GA line — does not exist yet** | Latest GA **0.5.0** (2026-06-21) targets Boot 3.5.14 / Spring AI 1.1.7. Boot-4 work lives on unreleased `1.5.0` / `2.0.0` branches (Boot 4.1.0 + Spring AI 2.0.0 + Kotlin 2.2.21 + MCP SDK 2.0). Interim move: **0.3.5 → 0.5.0 now** (same Boot 3.5 / Spring AI 1.1.7 stack — drop-in). See [02-embabel-gate.md](02-embabel-gate.md). |

## Spring portfolio

| Dependency | Current | Target | Notes / source |
|---|---|---|---|
| Spring AI (`spring-ai.version`) | 1.1.7 | **2.0.0** ✅ | GA 2026-06-12; the only Boot-4 line (1.1.x is Boot-3-only, tops out at 1.1.8). Major API + property migration — see [05](05-spring-ai-2-migration.md). [Upgrade notes](https://docs.spring.io/spring-ai/reference/upgrade-notes.html) |
| Spring Shell (`spring-shell.version`) | 3.4.2 | **4.0.2+** ⚠️ | Boot-4 line; full annotation-model rewrite (`@ShellComponent`/`@ShellMethod`/`@ShellOption` **removed**). Prerequisite "be on 3.4.x first" — we are. See [06](06-spring-shell-4-migration.md). [v4 migration guide](https://github.com/spring-projects/spring-shell/wiki/v4-migration-guide) |
| Spring Cloud (`spring-cloud.version`) | 2025.0.2 (Northfields) | **2025.1.2+** (Oakwood) ✅ | 2025.1.x ↔ Boot 4.0.x per the [compatibility matrix](https://github.com/spring-cloud/spring-cloud-release/wiki/Supported-Versions). Verify a 4.1-compatible patch at execution time. |
| Spring Security | 6.x (managed) | **7.0/7.1** (managed by Boot 4.x) ✅ | Lambda-only DSL; `AntPathRequestMatcher` removed; legacy access API split into `spring-security-access`. See [03 §5](03-spring-boot-4-core-changes.md). |
| Spring State Machine (`spring-statemachine.version`) | 4.0.1 | 🔴 **REMOVE** | Maintenance mode; maintainer: *"No, there is no plan to support Spring Boot 4."* ([#1207](https://github.com/spring-projects/spring-statemachine/issues/1207)). Kanban keeps the default transition-graph `TaskStateEngine`. See [07 §3](07-camel-and-other-deps.md). |

## Integration stack

| Dependency | Current | Target | Notes / source |
|---|---|---|---|
| Apache Camel (`camel.version`, in `extensions/jaiclaw-camel/pom.xml`) | 4.18.2 (LTS) | **4.21.0+** (non-LTS) ⚠️ | Camel **4.19.0** (2026-04-16) is the first Boot-4 release **and drops Boot 3 in the same release** — hard cutover. 4.18.x LTS is Boot-3-only (latest 4.18.3). Boot-4-capable: 4.19.0, 4.20.0, 4.21.0 (2026-07-01, latest). **No Boot-4 LTS yet** — plan to hop to the next LTS (likely 4.22) when it ships. Walk the [4.19](https://camel.apache.org/manual/camel-4x-upgrade-guide-4_19.html)→4.20→4.21 upgrade guides for SEDA/Kafka/AMQP/file/quartz. [4.19 what's new](https://camel.apache.org/blog/2026/04/camel419-whatsnew/) · [releases](https://camel.apache.org/releases/) |
| Jackson | 2.x via Boot BOM (`com.fasterxml.jackson`) | **3.x via Boot BOM (`tools.jackson`)** ✅ | Annotations (`jackson-annotations`, `com.fasterxml.jackson.annotation.*`) intentionally stay on Jackson-2 coordinates. Deprecated `spring-boot-jackson2` bridge exists (Jackson-2 auto-detection dies in Framework 7.2 — bridge only). See [04](04-jackson-3-migration.md). |
| jjwt-jackson (2 poms) | 0.x (Jackson 2) | ⚠️ **verify** | jjwt serializes via Jackson 2. Check latest jjwt for Jackson 3 support at execution time; fallback: keep `jackson-databind` 2.21.x for jjwt only (Boot 4 still manages Jackson 2 "in deprecated form") or switch to `jjwt-gson`. |
| Drools / KIE | 9.44.0.Final (last 9.x) | **10.1.0** (recommended) ⚠️ | 10.x is the active Apache line, Jakarta-complete. No explicit Boot-4 statement; JaiClaw uses it as a plain library (no kie-spring) so risk is transitive-deps only. Validate `jaiclaw-rules` specs. [releases](https://github.com/apache/incubator-kie-drools/releases) |
| resilience4j-circuitbreaker | 2.2.0 | check latest | Plain library; no Boot coupling in our usage. |
| okhttp / mockwebserver | 4.12.0 | keep | Framework 7 removed *Spring's* OkHttp3 client support; standalone OkHttp usage unaffected. Embabel's Boot-4 line itself pulls `anthropic-java-client-okhttp`. |
| line-bot-sdk, github-api, jtokkit | current pins | check latest | No Spring coupling; verify transitive Jackson-2 usage still resolves (Boot 4 manages Jackson 2.21.x deprecated). |

## Test stack

| Dependency | Current | Target | Notes / source |
|---|---|---|---|
| Spock (`spock.version`) | 2.4-groovy-4.0 | **2.4 (groovy-5.0 variant)** ⚠️ | Boot **4.0 dropped** Spock support (Groovy 5); **4.1 restores it via Spock 2.4** — this drove decision D2 (target 4.1). Boot may not manage Spock — pin `spock-bom` ourselves. Breaking: `.with {}` no longer a condition block; global mocks need `@Isolated` under parallel execution. [Spock 2.4 notes](https://spockframework.org/spock/docs/2.4/release_notes.html) · [boot#48513](https://github.com/spring-projects/spring-boot/issues/48513) |
| Groovy (`groovy.version`) | 4.0.30 | **5.0.x** (aligned with Boot 4.1's managed Groovy 5) ✅ | |
| GMavenPlus (`gmavenplus-plugin.version`) | 4.0.1 | **5.0.0** ✅ | Removes `fork` param; Groovy 5 support. [release](https://github.com/groovy/GMavenPlus/releases/tag/5.0.0) |
| byte-buddy / objenesis | managed / 3.5 | latest | Needed for mocking concrete classes; Spock 2.4's Mockito-backed mock-maker can also mock final classes — optional improvement. |
| Testcontainers | (if/where used) | **2.x** ⚠️ | Boot 4 manages Testcontainers 2.0 — artifacts renamed (`org.testcontainers:mysql` → `org.testcontainers:testcontainers-mysql`), JUnit 4 support removed. Audit usage during Phase 7. |
| Mockito | managed | 5.20+ (managed) | `@MockBean`/`@SpyBean` removed → `@MockitoBean`/`@MockitoSpyBean` — repo grep found **0 uses** ✅ nothing to do. |

## Build & ops tooling

| Dependency | Current | Target | Notes / source |
|---|---|---|---|
| spring-boot-maven-plugin | 3.5.15 | **4.1.x** (same as `spring-boot.version`) ⚠️ | Boot 4: optional deps excluded from fat jars (`<includeOptional>true</includeOptional>` to restore — audit gateway-app/shell for optional-dep reliance); CLASSIC loader removed. 4.1: `-DskipTests` no longer skips AOT processing (use `-Dmaven.test.skip`). Verify `${spring-boot.repackage.skip}` release-profile trick still works. |
| JKube (`jkube.version`) | 1.17.0 | **1.19.0** ✅ **required** | 1.19.0 fixes "Actuator liveness and readiness probe not getting generated with Spring Boot 4.x.x" (#3809) — 1.17.0 generates broken probes for Boot 4 images. [releases](https://github.com/eclipse-jkube/jkube/releases) |
| dependency-check-maven | 12.1.0 | 12.2.x | Boot-agnostic; routine bump. |
| jacoco-maven-plugin | 0.8.12 | latest 0.8.x | Verify Java 21 class-file support unchanged; no Boot coupling. |
| maven-compiler / surefire | 3.13.0 / 3.5.5 | keep or routine bump | Surefire must run JUnit Platform 2 (JUnit 6) launchers for Spock 2.4 — verify in Phase 7. |
| spring-boot-properties-migrator | — | **add temporarily** (4.1.x, runtime scope) | Reports/remaps renamed properties at startup during migration; **remove before release**. |
| OpenRewrite (`rewrite-spring` ≥ 6.34.0) | — | **use during Phases 1–3** | `org.openrewrite.java.spring.boot4.UpgradeSpringBoot_4_0` (deps, starter renames, property migrations); Spring AI ships 2.0 recipes too. [recipe](https://docs.openrewrite.org/recipes/java/spring/boot4/upgradespringboot_4_0-community-edition) |

## CVE overrides in the root pom (re-evaluate in Phase 1)

The root pom pins `tomcat.version` 10.1.55, `netty-bom` 4.1.135.Final, `log4j2.version` 2.26.0 above the Boot 3.5 BOM (first-match-wins ordering; see security-report-2026-06-15.md). Boot 4.1 manages **Tomcat 11** and newer Netty lines, so:

1. **Remove the Tomcat 10.1 pin** — it would downgrade/conflict with Boot 4's Tomcat 11. Confirm Boot 4.1's managed Tomcat covers DEP-001.
2. **Re-evaluate the Netty pin** against Boot 4.1's managed version; keep only if the managed version is still vulnerable per the next CVE scan.
3. Keep the pattern (overrides *before* BOM imports) documented — it remains the correct mechanism.

## Unchanged / no action

- `jaiclaw-core` — zero Spring dependency by design; only Jackson imports (if any) and pure-JDK `javax.crypto`/`javax.sql` usage. Verify, don't migrate.
- i18n (`ResourceBundle`), virtual-threads usage, `ServiceLoader` plugin discovery — no Boot 4 impact.
- Micrometer counters (`InstrumentedSoulProvider` etc.) — Boot 4 ships Micrometer 1.16.x/Tracing 1.6.x, no 2.0 jump; source-compatible. Note: Micrometer Tracing 1.15+ stops kebab-casing observation names — re-check dashboards that key on `JaiClawObservations` names.
