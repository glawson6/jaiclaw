# 03 — Spring Boot 4 / Framework 7 Core Changes Mapped to JaiClaw

> Sources: [Boot 4.0 Migration Guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide) · [Boot 4.0 Release Notes](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Release-Notes) · [Boot 4.1 Release Notes](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.1-Release-Notes) · [Framework 7.0 Release Notes](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-7.0-Release-Notes) · [Modularizing Spring Boot](https://spring.io/blog/2025/10/28/modularizing-spring-boot/)
> Jackson is big enough to get its own doc: [04-jackson-3-migration.md](04-jackson-3-migration.md). Spring AI: [05](05-spring-ai-2-migration.md).

## 1. Modularization — what it means for our 31 starters and 41 auto-config modules

Boot 4 splits the monolithic `spring-boot-autoconfigure` / `spring-boot-actuator` / `spring-boot-test-autoconfigure` into per-technology modules (`spring-boot-<tech>`, package `org.springframework.boot.<tech>`, starter `spring-boot-starter-<tech>`, test module `spring-boot-<tech>-test`).

**What does NOT change (verified against Boot 4 reference docs)** — our auto-config machinery survives structurally:
- Registration file stays `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` (41 files in repo — no rename).
- `@AutoConfiguration` (with `before/beforeName/after/afterName`), `@AutoConfigureBefore/After/Order`, all `@ConditionalOn*` — same packages (`org.springframework.boot.autoconfigure[.condition]`).
- `spring-boot-autoconfigure-processor` + `spring-autoconfigure-metadata.properties` generation unchanged.
- Third-party starter naming convention unchanged (`jaiclaw-spring-boot-starter`, `jaiclaw-starter-*` are fine).

**What DOES change:**

1. **Any reference to a Boot auto-configuration class that moved into a tech module breaks** — both `@AutoConfigureAfter(SomeBootAutoConfig.class)` class references and `afterName = "org.springframework.boot.autoconfigure...."` strings, plus `@SpringBootTest`/slice exclusions in specs. Audit all 61 `@AutoConfiguration` + 33 `@AutoConfigureAfter` files for references to **Boot's** (not JaiClaw's) auto-configs. Known concrete case from Embabel's wiki: `JacksonAutoConfiguration` → `spring-boot-jackson` module; `ObservationRegistryCustomizer` → `spring-boot-micrometer-observation`; `SecurityAutoConfiguration` → `spring-boot-security`; OAuth2 resource server → `spring-boot-security-oauth2-resource-server`; `@AutoConfigureMockMvc` → `spring-boot-webmvc-test`.
2. **Boot auto-config classes' public members are now hidden** — any code reaching into Boot auto-config internals breaks (grep in Phase 1: `import org.springframework.boot.autoconfigure.` outside annotations).
3. **`EnvironmentPostProcessor` moved packages**: `org.springframework.boot.env.EnvironmentPostProcessor` → `org.springframework.boot.EnvironmentPostProcessor` (old kept, deprecated). **Directly hits us** — the 2 `spring.factories` files:
   - `jaiclaw-spring-boot-starter/src/main/resources/META-INF/spring.factories` → `SecretsEnvironmentPostProcessor`
   - `extensions/jaiclaw-compliance/src/main/resources/META-INF/spring.factories` → `ComplianceEnvironmentPostProcessor`
   Update the import in both classes; the `spring.factories` key name changes with the interface FQN — per the migration guide, "deep integrations … may need to update both your code and your spring.factories files".
4. **`BootstrapRegistry`** and friends → `org.springframework.boot.bootstrap`; `@EntityScan` → `org.springframework.boot.persistence.autoconfigure` — grep for both.

### Starter renames in our 162 poms

| Old | New | Repo hits |
|---|---|---|
| `spring-boot-starter-web` | **`spring-boot-starter-webmvc`** | **42 poms** |
| `spring-boot-starter-aop` | `spring-boot-starter-aspectj` | audit |
| `spring-boot-starter-oauth2-{client,resource-server}` | `spring-boot-starter-security-oauth2-*` | audit (oauth-provider-demo example) |
| `spring-boot-starter-test` | slimmed; per-tech test starters (`spring-boot-starter-webmvc-test`, …); `spring-boot-starter-test-classic` bridge | 18 poms |
| (new, interim) | `spring-boot-starter-classic` restores the old monolithic classpath | migration aid only |

**Strategy**: use `spring-boot-starter-classic` as a *temporary* Phase-1 crutch to get the reactor compiling, then remove it module-by-module in favor of precise modular starters (Phase 6). Never release with `-classic` — it drags every auto-config module into consumers' classpaths, which is exactly what the CLAUDE.md prompt-size discipline is about, but for jars.

## 2. Framework 7 API removals affecting us

| Change | Repo impact | Action |
|---|---|---|
| `@MockBean`/`@SpyBean` removed → `@MockitoBean`/`@MockitoSpyBean` | **0 files** (Spock mocks instead) | ✅ none |
| `RestTemplate` deprecated (docs in 7.0, `@Deprecated` in 7.1) → `RestClient` | **38 files** | Migrate in **Phase 0** (RestClient exists since Framework 6.1 — do it on Boot 3.5 where behavior can be A/B-verified). Channel adapters (Telegram/Slack/Discord/SMS REST calls) are the bulk. |
| `HttpHeaders` no longer extends `MultiValueMap` | grep `HttpHeaders` usage passed as maps | `asMultiValueMap()` where needed |
| OkHttp3 client support removed from Framework | we use OkHttp directly, not via Spring | ✅ none (keep okhttp 4.12) |
| `ListenableFuture` removed | grep — expect 0 (virtual threads/CompletableFuture) | verify |
| Spring Retry dependency management dropped from Boot BOM (Framework 7 absorbs retry as `org.springframework.core.retry`, `@Retryable`) | audit for spring-retry usage | pin explicit `spring-retry` 2.0.12 if any module needs the legacy API; consider Framework-native retry |
| JUnit 4 support deprecated; JUnit 6 is the platform | Spock runs on JUnit Platform | see [09](09-validation-and-rollback.md) |
| `javax.annotation`/`javax.inject` support removed | our `javax.*` = JDK crypto/sql only | ✅ none |

## 3. JSpecify null-safety

Framework 7/Boot 4 annotate everything with JSpecify (`org.jspecify.annotations.*`), replacing `org.springframework.lang.*`.

- **Actuator endpoints (mandatory)**: optional `@Selector`/param arguments on `@Endpoint` operations must use `org.jspecify.annotations.Nullable` — `org.springframework.lang.Nullable` **no longer works** there. Fix in: `PipelineActuatorEndpoint`, `KanbanActuatorEndpoint`, `TendenciesActuatorEndpoint`.
- Where JaiClaw overrides/implements Spring interfaces (`ChannelAdapter` webhook MVC controllers, `BeanPostProcessor`s, converters), signatures may need JSpecify annotations to match contracts — compiler/IDE will surface these.
- We are pure Java (no Kotlin) — no K2 strictness fallout. Embabel handles its own via `-Xjspecify-annotations=ignore`.
- Optional post-1.0: adopt `@NullMarked` per-package in jaiclaw-core for API polish. Not in scope.

## 4. HTTP clients & SSRF

- Boot 4 auto-configured JDK-HttpClient-based builders honor `spring.threads.virtual.enabled=true` — aligns with our virtual-thread usage.
- 4.1 ships **`InetAddressFilter`** (blocks outgoing requests to given addresses). Our `SsrfGuard` (`io.jaiclaw.tools.exec`, flag `jaiclaw.tools.web.ssrf-protection`) predates it. **Keep SsrfGuard** (it guards tool-level fetches regardless of client), but evaluate wiring `InetAddressFilter` into Boot-built clients as defense-in-depth. Document in `docs/user/OPERATIONS.md`.
- `HttpMessageConverters` deprecated → `ClientHttpMessageConvertersCustomizer`/`ServerHttpMessageConvertersCustomizer` — grep during Phase 6.

## 5. Spring Security 7 (gateway, compliance, oauth demo)

([7.0 migration](https://docs.spring.io/spring-security/reference/migration/index.html))

- **Lambda-only DSL**: non-lambda `HttpSecurity` methods and `.and()` removed; `authorizeRequests()` gone → `authorizeHttpRequests(...)`. Repo has **2 files** using `HttpSecurity` — verify they're already lambda-style (post-6.1 code likely is); fix if not.
- **`AntPathRequestMatcher`/`MvcRequestMatcher` removed** → `PathPatternRequestMatcher`. Grep for both + `antMatchers`.
- Legacy `AccessDecisionManager`/`AccessDecisionVoter` moved to separate `spring-security-access` artifact — expect 0 uses (we're on `AuthorizationManager`-era APIs); verify.
- JWT auth in `jaiclaw-security` uses jjwt, not Spring's resource-server — unaffected by Security 7 API churn, but see the jjwt/Jackson-3 note in [01](01-dependency-matrix.md).
- New `/fonts/**` in `PathRequest.toStaticResources().atCommonLocations()` — only relevant if a security config uses common-locations permits (canvas/A2UI static serving — check).

## 6. Actuator

- **Liveness/readiness probes on by default** (not just K8s). Affects: `/actuator/health` shape in gateway-app, JKube-generated probes (JKube 1.19.0 required — [01](01-dependency-matrix.md)), `start.sh`/`auth-status.sh` if they parse health output, e2e skill assertions.
- `management.tracing.enabled` → `management.tracing.export.enabled`; `ConditionalOnEnabledTracing` → `ConditionalOnEnabledTracingExport` — grep ymls + code.
- Custom endpoints (`/actuator/pipelines`, `/actuator/kanban`, tendencies) — mechanism unchanged, but JSpecify rule in §3 applies, and `spring-boot-actuator` dependency coordinates may shift to tech modules — the 14 poms depending on `spring-boot-starter-actuator` keep working (starter remains) per release notes; verify.
- Micrometer now usable without actuator — no action, but simplifies `jaiclaw-observability` module deps if desired later.

## 7. Testing changes (summary — full strategy in [09](09-validation-and-rollback.md))

- `@SpringBootTest` **no longer auto-configures MockMvc/TestRestTemplate** → add `@AutoConfigureMockMvc` / `@AutoConfigureTestRestTemplate` / `@AutoConfigureRestTestClient` per spec that uses them. Grep: `MockMvc|TestRestTemplate` in `src/test/groovy`.
- `spring-boot-starter-test` slimmed — specs relying on transitively-provided libs (JSONassert, XMLUnit, etc.) need explicit deps or the `-test-classic` bridge.
- Test context pausing is new (`spring.test.context.cache.pause`); `SpringExtension` `ExtensionContext` scope changed — only relevant if we have custom `TestExecutionListener`s (grep; expect 0, Spock uses its own lifecycle).
- Spock 2.4 + Groovy 5 required (Boot 4.1 restored Spock support). Details: [07 §5](07-camel-and-other-deps.md).

## 8. Properties & config files

Renames relevant to our 49 `application*.yml` files (beyond `spring.ai.*` — see [05](05-spring-ai-2-migration.md), and `spring.jackson.*` — see [04](04-jackson-3-migration.md)):

- `management.tracing.enabled` → `management.tracing.export.enabled` (observability configs).
- Session/Mongo/Kafka renames — likely 0 hits; the properties-migrator will catch stragglers.
- **Add `spring-boot-properties-migrator` (runtime scope) to gateway-app + shell + cli during Phases 1–7; remove in Phase 8.** Every startup logs renamed/removed properties.
- `spring.jackson.use-jackson2-defaults=true` is available as a behavior-compat switch during Phase 2 — decide, don't drift (see [04 §4](04-jackson-3-migration.md)).

## 9. Servlet container

- Tomcat 11 via Boot 4 BOM (drop our 10.1.55 CVE pin — [01](01-dependency-matrix.md)). Undertow removed (we don't use it). Jetty 12.1 fine (not used).
- WebSocket: Jakarta WebSocket 2.2 — 5 files use `WebSocketHandler`/`EnableWebSocket` (gateway WebSocket surface); Spring's WebSocket API is stable across this, recompile + integration-test in Phase 6.
- `spring-boot-starter-webmvc` pulls the same Tomcat default; WAR-deploy changes irrelevant (we ship fat jars/images).
