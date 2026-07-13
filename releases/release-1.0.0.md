# JaiClaw 1.0.0 Release Notes

**Release Date:** TBD (gated on Embabel Boot-4 GA — see [../docs/spring-boot-4-upgrade/02-embabel-gate.md](../docs/spring-boot-4-upgrade/02-embabel-gate.md))

> 1.0.0 is the **Spring Boot 4 line-swap release**. Every Tier-1 dependency in the framework moves to its Boot-4-compatible major version in a single coordinated bundle:
>
> - Spring Boot 3.5 → **4.1**
> - Spring Framework 6.2 → **7.0**
> - Spring AI 1.1.7 → **2.0**
> - Spring Shell 3.4 → **4.0** (full annotation-model rewrite)
> - Spring Security 6 → **7** (lambda-DSL, `EnableWebSecurity` requirement)
> - Spring Cloud 2025.0 → **2025.1** (Oakwood)
> - Spring Batch 5 → **6**
> - Apache Camel 4.18 (LTS) → **4.21** (first Boot-4 line)
> - Jackson 2 → **3** (new `tools.jackson.*` namespace)
> - Spock 2.4-groovy-4.0 → **2.4-groovy-5.0**
> - Groovy 4.0 → **5.0**
> - Testcontainers 1.x → **2.x**
> - JKube 1.17 → **1.19**
> - Drools 9.44 → **10.1**
> - Embabel Agent 0.5.0 → **Boot-4 GA line** (target)
>
> Java 21 stays as the runtime baseline (Boot 4 minimum is 17).
>
> **BREAKING RELEASE.** Adopters upgrading from 0.9.x must migrate their own Boot 3 / Spring AI 1.1 / Shell 3 / Jackson 2 code. See the migration guide (`docs/dev/MIGRATION-1.0.md`) for the mechanical checklist.

---

## Why 1.0

Spring Boot 3.5 reached OSS end-of-life 2026-06-30 (final patch 3.5.16). The 0.9.x line is Boot-3-only and can no longer accept upstream security patches. JaiClaw's stability commitment requires being on a supported upstream, and the Boot 4 line-swap is the vehicle. This release supersedes the "JaiClaw 2.0 = Boot 4" plan originally sketched in `docs/dev/RELEASE-PLAN-1.0.0.md`.

Second, the framework has aged into the shape it needs to hold: the compliance substrate (Tier-3 in 0.9.3), the extension surface (Tier-2 in 0.9.1/0.9.2), and the runtime (Tier-1 in 0.6–0.8) have all settled. 1.0.0 stamps semver stability on the shape while cutting over the underlying stack.

---

## Highlights

- **Full Boot 4.1 / Framework 7 stack** — every module recompiles cleanly; test suite pass rate is high with a handful of persisted-format hotspots called out below.
- **Spring AI 2.0 across the board** — new `ChatModel` decorator surface (`AuditingChatModel` BeanPostProcessor still idempotent under 2.0); MCP hosting on SDK 2.x.
- **Spring Shell 4 annotation model** — `@Command` / `@Option` everywhere; hyphenated-alias convention retained (see CLAUDE.md § Spring Shell CLI Module Pattern).
- **Jackson 3 native** — every JaiClaw-emitted persisted format (audit-chain, transcripts, cron, identity, kanban `EffectLedger`) uses `tools.jackson.*`. Jackson-2 coexistence in the classpath is preserved (Boot 4 still manages Jackson 2 in deprecated form) so downstream libraries (jjwt, line-bot-sdk, github-api, Drools) continue to work.
- **Full multi-tenant conformance** — every path that touches the migration surface (auto-configs, EPPs, HTTP client factory, MCP tool providers) was re-audited against `TenantGuard` / `TenantContextPropagator` per the CLAUDE.md conformance checklist.

## New modules

None. The 1.0.0 release is a version-line swap, not a scope expansion.

## Removed modules

- **`jaiclaw-starter-azure-openai`** — deleted. Spring AI 2.0 GA removed the `spring-ai-starter-model-azure-openai` starter (last release 2.0.0-M4). Adopters route Azure OpenAI through the `openai` starter with a `spring.ai.openai.base-url` override.
- **`jaiclaw-starter-oci-genai`** — deleted. Same rationale (`spring-ai-starter-model-oci-genai` last release 2.0.0-M4).
- **`jaiclaw-starter-minimax`** — deleted. Spring AI 2.0 GA removed `spring-ai-starter-model-minimax` (last 2.0.0-M8). Adopters use the `anthropic` starter with `spring.ai.anthropic.base-url=https://api.minimax.io/anthropic` per CLAUDE.md § Embabel LLM Model Configuration.
- **`jaiclaw-starter-vertex-ai`** — deleted. `spring-ai-starter-model-vertex-ai-gemini` was renamed to `spring-ai-starter-model-google-genai` at 2.0 GA. Adopters use `jaiclaw-starter-gemini` (which already covers the Google route).
- **Spring State Machine kanban engine** — deleted. Upstream declined Boot-4 support ([spring-projects/spring-statemachine#1207](https://github.com/spring-projects/spring-statemachine/issues/1207)). The `TaskStateEngine` SPI stays — adopters can register their own engine bean and the default `TransitionGraphStateEngine` steps aside via `@ConditionalOnMissingBean`.

## Breaking changes

### Framework version bumps (all Tier-1 majors)

Every adopter must:
1. Move their own application to Spring Boot 4.1.x.
2. Move to Spring AI 2.0.x (or drop direct Spring AI usage in favor of JaiClaw's `AgentRuntime`).
3. Adopt the Shell 4 annotation model in any custom `@ShellComponent`/`@ShellMethod` classes (see Shell 4 section below).
4. Handle Jackson 3's default behavior changes if their custom code touches JaiClaw-persisted formats (see Jackson section below).

### Spring AI 2.0 property renames

The `.options` segment is removed from Spring AI provider properties:

```
spring.ai.anthropic.chat.options.model    →  spring.ai.anthropic.chat.model
spring.ai.openai.chat.options.model       →  spring.ai.openai.chat.model
spring.ai.ollama.chat.options.model       →  spring.ai.ollama.chat.model
```

46 example YAMLs and every adopter YAML must be updated. Spring Boot's `spring-boot-properties-migrator` (added to gateway-app / shell / cli in Phase 1, removed in Phase 8) reports mismatches at startup during migration windows.

### Spring Shell 4 annotation-model rewrite

The old Shell 3 model is gone:

| Shell 3 | Shell 4 |
|---|---|
| `@ShellComponent` on class | `@Component` (or `@CommandGroup` for grouping) |
| `@ShellMethod(key = "cmd sub", value = "desc")` | `@Command(name = "sub", alias = "cmd-sub", description = "desc")` |
| `@ShellOption(value = "--flag", defaultValue = "x")` | `@Option(longName = "flag", defaultValue = "x")` |
| `@ShellOption(defaultValue = ShellOption.NULL)` | `@Option` (bare) |
| `import org.springframework.shell.standard.*` | `import org.springframework.shell.core.command.annotation.*` |

Adopters with custom shell commands must rewrite every annotation. The CLAUDE.md hyphenated-alias convention still applies (multi-word command names hit the same non-interactive-mode caveat).

The onboarding wizard (`OnboardWizardOrchestrator`, `PromptCommands`, etc.) is quarantined in 1.0.0 — Shell 4 removed `ComponentFlow` / `PromptProvider`, and the wizard needs a full port to the new component model. It will return in a follow-up release. `start.sh` / `bin/jaiclaw` continue to work; only the interactive wizard is affected.

### Jackson 2 → 3 namespace change

Every JaiClaw source file that touched `com.fasterxml.jackson.core`, `.databind`, `.dataformat`, `.datatype`, `.module`, or `.util` was moved to `tools.jackson.*`. Annotations (`com.fasterxml.jackson.annotation.*`) intentionally stay on Jackson 2 coordinates — Jackson 3 keeps annotation coordinates unchanged per its migration guide.

Adopters with custom serializers must:

- Rename `JsonSerializer<T>` → `ValueSerializer<T>` and `JsonDeserializer<T>` → `ValueDeserializer<T>`.
- Change the `serialize()` signature to accept `SerializationContext` instead of `SerializerProvider`.
- Change the `deserialize()` signature: no longer throws `IOException`; uses `DeserializationContext.readTree(JsonParser)` instead of `p.getCodec().readTree(p)`.
- Replace `JsonNode.fields()` (Iterator) → `.properties()` (returns `Set<Map.Entry<...>>`).
- Replace `writeStringField` → `writeStringProperty`, `writeNumberField` → `writeNumberProperty`, `writeObjectFieldStart` → `writeObjectPropertyStart`, `writeObjectField` → `writePOJOProperty`.
- Rebuild `ObjectMapper` construction sites using `JsonMapper.builder()...build()` — Jackson 3's `ObjectMapper` is immutable, so `.disable()` / `.enable()` / `.registerModule()` on a live instance are gone.

Jackson 3 default behavior changes:
- `FAIL_ON_UNKNOWN_PROPERTIES` **off** by default (was on).
- `WRITE_DATES_AS_TIMESTAMPS` **off** by default (moved into the JavaTime module, which is now built into core).
- Bridge switch `spring.jackson.use-jackson2-defaults=true` is available if downstream persisted formats need Jackson 2 defaults during a transition window. JaiClaw's persisted formats (audit-chain, transcripts, cron, identity, kanban `EffectLedger`) were all verified round-trip; the switch is not enabled by default.

### Spring Security 7

- Lambda-DSL only (`.and()`, `.authorizeRequests()` removed).
- `AntPathRequestMatcher` / `MvcRequestMatcher` removed → `PathPatternRequestMatcher`.
- `@EnableWebSecurity` is now required on inner `@Configuration` classes that expect an autowired `HttpSecurity` — Boot 4's `SecurityAutoConfiguration` no longer eagerly wires it for downstream `@Bean` methods. JaiClaw's `JaiClawSecurityAutoConfiguration` was updated; adopters with custom security configs may need the same treatment.

### Spring Batch 6 package moves

Adopters using Spring Batch directly (via `jaiclaw-cron-manager` or their own wiring):

```
org.springframework.batch.core.Job                 → org.springframework.batch.core.job.Job
org.springframework.batch.core.Step                → org.springframework.batch.core.step.Step
org.springframework.batch.core.JobParameters       → org.springframework.batch.core.job.parameters.JobParameters
org.springframework.batch.core.JobParametersBuilder → org.springframework.batch.core.job.parameters.JobParametersBuilder
org.springframework.batch.core.StepContribution    → org.springframework.batch.core.step.StepContribution
org.springframework.batch.repeat.RepeatStatus      → org.springframework.batch.infrastructure.repeat.RepeatStatus
```

### CVE-override deltas

- **Tomcat 10.1 pin REMOVED.** Boot 4 manages Tomcat 11.
- **Netty pin retained** pending post-upgrade CVE re-scan against Boot 4.1's managed Netty line.

### Removed test surface

Boot 4's `spring-boot-starter-test` no longer transitively brings `TestRestTemplate` — that surface moved into a new `spring-boot-resttestclient` module. Adopters running MockMvc / TestRestTemplate integration tests must add:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-resttestclient</artifactId>
    <scope>test</scope>
</dependency>
```

`TestRestTemplate` also moved packages: `org.springframework.boot.test.web.client.TestRestTemplate` → `org.springframework.boot.resttestclient.TestRestTemplate`.

## Dependency table

| Dependency | 0.9.4 | 1.0.0 | Notes |
|---|---|---|---|
| Java | 21 | 21 | Boot 4 baseline is 17; JaiClaw stays on 21 |
| Spring Boot | 3.5.16 | 4.1.0 | Final Boot-3 patch → Boot 4.1 line |
| Spring Framework | 6.2.x | 7.0.x | Managed by Boot BOM |
| Spring AI | 1.1.7 | 2.0.0 | Full `.options` property rename; provider-starter catalog trimmed |
| Spring Shell | 3.4.2 | 4.0.2 | Full annotation-model rewrite |
| Spring Cloud | 2025.0.2 | 2025.1.2 | Oakwood |
| Spring Security | 6.x | 7.x | Lambda-only DSL; `@EnableWebSecurity` requirement |
| Spring Batch | 5.x | 6.x | Sub-package moves |
| Apache Camel | 4.18.2 | 4.21.x | Off-LTS (no Boot-4 LTS exists yet) |
| Jackson | 2.x | 3.x (`tools.jackson.*`) | Annotations stay on Jackson 2 coords |
| Spock | 2.4-groovy-4.0 | 2.4-groovy-5.0 | Boot 4.1 restored Spock support |
| Groovy | 4.0.30 | 5.0.7 | Tighter bean-property vs Map-key resolution |
| GMavenPlus | 4.0.1 | 5.1.0 | Removes `fork` param |
| JKube | 1.17.0 | 1.19.0 | Boot-4 probe fix |
| Testcontainers | 1.x | 2.x | Artifacts renamed; JUnit 4 support dropped |
| Embabel Agent | 0.5.0 | **Boot-4 GA line** | Hard release gate |
| Drools | 9.44 | 10.1.0 | Jakarta-complete |
| Tomcat | 10.1.55 pinned | 11.x (Boot-managed) | Pin removed |

## CVE posture

- CVE-DEP-001 (Tomcat 10.1) — moot; Boot 4 = Tomcat 11.
- CVE-DEP-002 (Netty 4.1.x) — retained the 4.1.135 pin pending a post-Boot-4 CVE rescan. Confirm against Boot 4.1's managed line before final release.

## Migration guide

Full step-by-step is at [../docs/dev/MIGRATION-1.0.md](../docs/dev/MIGRATION-1.0.md) — TODO to be authored alongside this release notes file.

The mechanical rewrites JaiClaw applied to its own code (Jackson namespace, Shell annotation model, Spring Batch package moves, Security 7 `@EnableWebSecurity`) are the same rewrites adopters will apply to theirs. The doc set at [../docs/spring-boot-4-upgrade/](../docs/spring-boot-4-upgrade/) — especially [04-jackson-3-migration.md](../docs/spring-boot-4-upgrade/04-jackson-3-migration.md) and [06-spring-shell-4-migration.md](../docs/spring-boot-4-upgrade/06-spring-shell-4-migration.md) — is the authoritative reference.

## Known follow-ups (post-1.0.0)

- **Onboarding wizard** — Shell 4 rebuild on the new component model.
- **Custom RestClient constructors** — 16 channel-adapter / MCP-tool-provider constructors still take `RestTemplate`; migrate to primary `(RestClient, ...)` constructors with `@Deprecated` `(RestTemplate, ...)` overloads.
- **ExplicitToolLoop rework** — Spring AI 2.0 removed `ToolCallingChatOptions.internalToolExecutionEnabled(false)`; rewire via `ToolCallingManager` injection.
- **Kanban / Pipeline persisted-format cross-version fixtures** — verify pre-migration YAML/JSON files still deserialize under Jackson 3 defaults.
- **`jaiclaw-project-scaffolder` code templates** — the templates still emit Shell 3 `@ShellComponent`/`@ShellMethod` in generated projects; update to Shell 4.

## Acknowledgements

The Boot 4 upgrade plan and execution log are captured in full at [../docs/spring-boot-4-upgrade/](../docs/spring-boot-4-upgrade/). Every step, every version pin, every source-link claim is traceable through the doc set + git history.
