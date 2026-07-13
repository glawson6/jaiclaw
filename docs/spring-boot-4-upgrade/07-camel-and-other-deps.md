# 07 — Camel, State Machine, Drools, Test Stack, and Other Dependencies

## 1. Apache Camel 4.18.2 → 4.21.0+ (jaiclaw-camel, jaiclaw-pipeline, 7 pipeline examples)

- **Camel 4.19.0 (2026-04-16) is the first Boot-4 release and drops Boot 3 in the same release** — a hard cutover, no dual-support window. ([4.19 what's new](https://camel.apache.org/blog/2026/04/camel419-whatsnew/))
- 4.18.x is the current LTS but Boot-3-only. Boot-4-capable releases are all non-LTS: 4.19.0, 4.20.0 (expedited security release, JDK 25 compat), **4.21.0 (2026-07-01, latest)**. **Decision D6: go 4.21.0+, adopt the next LTS (likely 4.22) when it ships.** ([releases](https://camel.apache.org/releases/))
- Walk the per-version upgrade guides 4.18→4.19→4.20→4.21 ([guide](https://camel.apache.org/manual/camel-4x-upgrade-guide-4_19.html)) for our used components: **SEDA** (default pipeline transport), **Kafka**, **AMQP**, **file**, **quartz** (`camel-quartz-starter` — CRON triggers), `direct:`. No breaking changes flagged for these in 4.19/4.20 notes, but the pipeline module is `@Experimental` and heavily Camel-coupled — full `jaiclaw-pipeline` spec run + `pipeline-e2e` example run is the gate.
- Camel 4.19 adds Jackson 3 components — align `camel-jackson` usage (if any) with Phase 2.
- `camel.version` lives in `extensions/jaiclaw-camel/pom.xml` (4.18.2) — check for duplicate pins in pipeline examples.
- Verify: `PipelineValidator` startup validation, `/actuator/pipelines`, `POST /api/pipelines/{id}/trigger`, quartz cron trigger fire, DEAD_LETTER error strategy, transport auth (HMAC/Bearer) across SEDA and Kafka.

## 2. Spring Cloud 2025.0.2 → 2025.1.x

Boot 4.0 pairs with **2025.1 (Oakwood)**; latest 2025.1.2 (2026-06-11). Confirm 4.1 compatibility of the chosen patch at execution time ([matrix](https://github.com/spring-cloud/spring-cloud-release/wiki/Supported-Versions)). Audit what we actually consume from the BOM (it was bumped for CVE reasons in 0.9.2) — possibly nothing runtime-critical; if so this is a one-line change.

## 3. Spring State Machine — REMOVE (decision D4)

- Upstream is in maintenance mode; maintainer on [#1207](https://github.com/spring-projects/spring-statemachine/issues/1207): *"No, there is no plan to support Spring Boot 4."* On Boot 4 it fails with `ClassNotFoundException` through its Boot-3 transitive deps. Commercial-only continuation exists; not for us.
- JaiClaw impact: **7 files** in `jaiclaw-kanban` — SSM is an *optional* `TaskStateEngine` backend behind the default transition-graph engine, so removal is architecturally clean:
  1. Delete the SSM-backed `TaskStateEngine` implementation + its auto-config conditional + `spring-statemachine.version` root-pom property/dependencyManagement.
  2. Keep the `TaskStateEngine` SPI (it's the extension point; adopters can bring their own engine).
  3. Release-notes entry: breaking for anyone who opted into the SSM engine; migration = remove the opt-in property, default engine takes over (state definitions live in board YAML, not SSM config — confirm no data migration needed).
  4. Do this in **Phase 0** (works on Boot 3.5 today) to shrink the Boot-4 wave.

## 4. Drools 9.44 → 10.1.0 (jaiclaw-rules)

9.44.0.Final is the last 9.x; **10.x is the active Apache line** (Jakarta-complete, JDK 17+, latest 10.1.0). No explicit Boot-4 statement exists, but we use Drools as a plain library (text-analysis/decision/validation/tax rules + 3 LLM tools) with no kie-spring — risk is transitive Jackson/Jakarta only, and Boot 4 still manages Jackson 2 (deprecated) for coexistence. **Recommend 10.1.0 in Phase 1** + full `jaiclaw-rules` spec run; if 10.x churn is too large, staying on 9.44 as a plain lib is the documented fallback (flag for verification, not a blocker). ([releases](https://github.com/apache/incubator-kie-drools/releases))

## 5. Spock / Groovy / GMavenPlus (the 900-test question)

- **Spock 2.4** (2025-12-11) adds Groovy 5 support — variant **`2.4-groovy-5.0`**. Boot **4.0 removed** Spock integration (Groovy 5 clash); **Boot 4.1 restores it** — the deciding factor for targeting 4.1 (decision D2). Track [boot#48513](https://github.com/spring-projects/spring-boot/issues/48513) for whether Boot 4.1 *manages* Spock or we pin `spock-bom` ourselves (assume: pin it).
- Root-pom changes: `spock.version` `2.4-groovy-4.0` → `2.4-groovy-5.0`; `groovy.version` `4.0.30` → Boot-4.1-managed Groovy 5.0.x (drop our pin if the BOM manages it); `gmavenplus-plugin.version` `4.0.1` → **5.0.0** (removes `fork` param — check our plugin configs for it).
- Spock 2.4 breaking changes to audit in specs: `.with {}` no longer special-cased as a condition block (assertions inside silently stop asserting → **grep specs for `with(`/`verifyAll` usage and review**); global mocks need `@Isolated` under parallel execution (are we parallel? check surefire config — if not, no-op).
- `spock-spring` (11 poms) against Framework 7 + JUnit 6/Platform 2: no incompatibility reported upstream, but **smoke-test one context-loading spec module first** (Phase 7 step 1) before mass migration debugging.
- byte-buddy/objenesis (concrete-class mocking): bump to latest; optionally adopt Spock 2.4's Mockito-backed mock-maker for final classes.

## 6. Groovy 5 source compatibility

Our Groovy usage is test-only (specs). Groovy 4→5 is generally source-compatible for test code; watch: default-methods resolution, `@CompileStatic` strictness if used, and the Spock variant must match the Groovy line exactly (`groovy-5.0` variant ↔ Groovy 5.0.x jars) — mismatches fail at spec-compile time via GMavenPlus.

## 7. Testcontainers 2.x

Boot 4 manages **Testcontainers 2.0**: module artifacts renamed (`org.testcontainers:mysql` → `org.testcontainers:testcontainers-mysql`), container classes moved packages, JUnit 4 support removed. Audit repo usage (docker-compose based e2e may not use Testcontainers at all — verify; if unused, nothing to do).

## 8. JKube 1.17.0 → 1.19.0 (required)

1.19.0 fixes Boot-4 liveness/readiness probe generation (#3809) — with 1.17.0, `k8s:build` images for Boot 4 apps get broken probes (compounded by Boot 4 enabling probes by default). Bump `jkube.version`; rebuild both images (`jaiclaw-gateway-app`, `jaiclaw-shell`) with `-Pk8s`; verify generated deployment YAML probe paths (`/actuator/health/liveness|readiness`). Base image stays `eclipse-temurin:21-jre` (decision D5); revisit 25-jre post-1.0.

## 9. Routine bumps / no-ops

- **dependency-check-maven** 12.1.0 → 12.2.x (Boot-agnostic; keep OSS-Index disabled per pom notes).
- **jacoco** 0.8.12 → latest 0.8.x (coverage gates unchanged: 40% template, ≥50% goal on 10 core modules per RELEASE-PLAN).
- **maven-surefire** 3.5.5: verify JUnit Platform 2 launcher support for Spock 2.4; bump if needed. Keep `**/*Spec.java` includes.
- **central-publishing-maven-plugin / gpg / source / javadoc**: unaffected; re-run a `-Prelease` dry run in Phase 8 (fat-jar skip property against the new boot-maven-plugin especially).
- **resilience4j** 2.2.0, **jtokkit**, **github-api**, **line-bot-sdk**, **okhttp**: no Boot coupling; routine latest-check; confirm Jackson-2 transitive resolution still works (Boot 4 manages Jackson 2.21.x deprecated).
- **spring-security-* / tomcat / netty CVE pins**: see [01 § CVE overrides](01-dependency-matrix.md) — Tomcat 10.1 pin must be REMOVED (Boot 4 = Tomcat 11).
