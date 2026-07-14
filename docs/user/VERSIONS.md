# Version History

This document tracks notable changes between JaiClaw releases.

## 1.0.0-SNAPSHOT (pilot preview)

Published to TapTech's internal Nexus at
`https://tooling.taptech.net/repository/maven-snapshots/`. **Not on
Maven Central** — GA is gated on Embabel cutting a non-SNAPSHOT Boot-4
release (see
[docs/spring-boot-4-upgrade/02-embabel-gate.md](../spring-boot-4-upgrade/02-embabel-gate.md)).

Pilots wire two snapshot repos alongside the BOM import:

```xml
<repositories>
  <repository>
    <id>taptech-snapshots</id>
    <url>https://tooling.taptech.net/repository/maven-snapshots/</url>
    <snapshots><enabled>true</enabled></snapshots>
    <releases><enabled>false</enabled></releases>
  </repository>
  <repository>
    <id>embabel-snapshots</id>
    <url>https://repo.embabel.com/artifactory/libs-snapshot</url>
    <snapshots><enabled>true</enabled></snapshots>
    <releases><enabled>false</enabled></releases>
  </repository>
</repositories>

<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>io.jaiclaw</groupId>
      <artifactId>jaiclaw-bom</artifactId>
      <version>1.0.0-SNAPSHOT</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
```

### Stack upgrade

| Component | 0.9.x | 1.0.0-SNAPSHOT |
|---|---|---|
| Spring Boot | 3.5.16 | **4.1.0** |
| Spring Framework | 6.2.x | **7.0.8** |
| Spring AI | 1.1.7 | **2.0.0** |
| Spring Shell | 3.4.2 | **4.0.2** |
| Spring Cloud | 2024.x | **2025.1.2** |
| Embabel Agent | 0.5.0 | **2.0.0-SNAPSHOT** |
| Apache Camel | 4.18.2 | **4.21.0** |
| Jackson | `com.fasterxml.jackson.*` 2.x | **`tools.jackson.*` 3.x** |
| Groovy (tests) | 4.0.x | **5.0.7** |
| Spock | 2.3-groovy-4.0 | **2.4-groovy-5.0** |
| JKube | 1.18.x | **1.19.0** |
| Tomcat | 10.1 pin | **11 (Boot 4 default)** |
| Java | 21 | 21 |

### Breaking changes (Boot 4 stack)

- **Spring AI 2.0 property renames** — `spring.ai.*.chat.options.model` → `spring.ai.*.chat.model` (44 YAML files rewritten). The `.options.` segment is gone across all Spring AI providers.
- **Jackson 3 namespace** — every `com.fasterxml.jackson.*` import moves to `tools.jackson.*`. Custom serializers ported. `FAIL_ON_NULL_FOR_PRIMITIVES` default flipped and must be explicitly disabled where old behavior is required (fixed in `PipelineYamlParser`).
- **Spring Shell 4 rewrite** — `@ShellComponent` / `@ShellMethod` / `@ShellOption` were removed at 4.0. Migrated to `@Command` / `@Option` across ~35 files. `jline-reader` is no longer a transitive dep — added explicitly in `jaiclaw-perplexity` + `jaiclaw-skill-creator`. Quarantined pending Shell-4 component-model re-implementation: `OnboardWizardOrchestrator` + 16 wizard step classes, `PromptCommands`, `JaiClawPromptProvider`, `JaiClawShellPromptAutoConfiguration`, `YamlConfigWriter`.
- **`RestTemplate` → `RestClient` across channel adapters** — Discord, Slack, Signal, SMS, Teams adapters now take an HTTP-abstraction interface primary constructor (`SignalHttpClient`, `SmsHttpClient`, `DiscordHttpClient`, `SlackHttpClient`, `TeamsHttpClient`). Legacy `(…, RestTemplate)` constructors kept with `@Deprecated(since="1.0.0", forRemoval=true)` — they'll be removed at 1.1.
- **Spring Boot starter renames** — `spring-boot-starter-web` → `spring-boot-starter-webmvc` across 42 poms per Boot 4.
- **`EnvironmentPostProcessor` package move** — `org.springframework.boot.env.EnvironmentPostProcessor` → `org.springframework.boot.EnvironmentPostProcessor` in the 3 EPP classes (Banner, Secrets, Compliance) + both `spring.factories` key names.
- **`RestClientCustomizer` package move** — `org.springframework.boot.web.client.RestClientCustomizer` → `org.springframework.boot.restclient.RestClientCustomizer` in `JaiClawHttpAutoConfiguration`. `jaiclaw-spring-boot-starter` picks up `spring-boot-restclient` runtime.
- **Removed Spring AI starters** — `spring-ai-starter-model-oci-genai` (removed at 2.0 GA), `spring-ai-starter-model-minimax` (removed at 2.0 GA). `spring-ai-starter-model-vertex-ai-gemini` renamed to `spring-ai-starter-model-google-genai`. The `jaiclaw-starter-oci-genai`, `jaiclaw-starter-minimax`, and `jaiclaw-starter-vertex-ai` wrapper modules were deleted. MiniMax adopters route via the anthropic starter with `spring.ai.anthropic.base-url=https://api.minimax.io/anthropic`.
- **Spring State Machine kanban engine removed** — `jaiclaw-kanban` now ships only the default `TaskStateEngine` graph engine (7 files + pom dep dropped). SPI is unchanged.
- **`commons-logging` explicit dep** — Boot 4 / Spring 7 removed the `spring-jcl` drop-in. `jaiclaw-cli` and any fat-JAR consumer now needs `commons-logging` on the runtime classpath (bumped to 1.3.5).

### New / verified

- Full channel-adapter migration to `RestClient` with per-channel HTTP-abstraction interfaces (spec-mockable via Spock).
- Embabel `2.0.0-SNAPSHOT` (Boot-4 line) resolves from `repo.embabel.com/libs-snapshot`; `jaiclaw-embabel-delegate` recompiles clean, 19 tests green.
- JKube 1.19 image builds green for `jaiclaw-gateway-app` + `jaiclaw-shell`.
- Docker/agentmind/kanban/CLI/pipeline e2e suites green on the Boot-4 stack (pipeline-e2e `broken` YAML fixture triggers Boot 4's stricter property binding *before* `PipelineValidator` fires — framework validator still catches misconfiguration; fixture needs a rework to keep validator as the failure surface).

### Known limitations

- `1.0.0-SNAPSHOT` is **not on Maven Central**. Central rejects SNAPSHOT deps in release artifacts, so the GA tag waits on Embabel `1.5.0` or `2.0.0` (release-2.0 label on [issue #1052](https://github.com/embabel/embabel-agent/issues/1052)).
- Production consumers should stay on the latest `0.9.x` Maven Central release.

---

## 0.4.0

Released 2026-05-18.

### New Features

- **Pluggable Telegram HTTP Client** — `TelegramAdapter` now accepts an injectable `TelegramHttpClient` interface instead of using a hardcoded `RestTemplate`. The default implementation (`DefaultTelegramHttpClient`) uses JDK `HttpClient` with proxy support via `ProxyAwareHttpClientFactory`. Custom implementations can be provided as Spring beans.

- **Pluggable Telegram Polling Strategy** — A new `TelegramPollingStrategy` interface allows replacing the built-in `getUpdates` polling loop. The framework ships a `CamelTelegramPollingStrategy` that uses Apache Camel's Telegram component for polling, which is auto-configured by `CamelPollingAutoConfiguration` when Camel is on the classpath.

- **Gateway Message Filter Framework** — New `GatewayMessageFilter` interface in `jaiclaw-gateway` allows inserting message filters between channel adapters and `GatewayService`. When a filter bean is present, `FilteredGatewayLifecycle` is used instead of the default `GatewayLifecycle`. `TelegramUserIdFilter` now implements this interface for defense-in-depth user authorization and rate limiting.

- **Tools Configuration Environment Fallback** — Added `resolveToolsFromEnvironment()` in `JaiClawAutoConfiguration` to work around Spring Boot's silent failure when binding deeply nested records inside `Map<String, Record>`. The `jaiclaw.agent.agents.{name}.tools.profile` property is now read directly from the `Environment` as a fallback, following the same pattern used for `llmOverride` and `loopDelegateOverride`.

- **Telegram Markdown Fallback** — `TelegramAdapter.sendText()` now catches Telegram API "can't parse entities" errors and retries the message without `parse_mode`, ensuring delivery even when LLM responses contain malformed Markdown.

- **Empty Attachment Guards** — `TelegramAdapter.extractAttachments()` now checks `!fileId.isBlank()` before attempting downloads for document, photo, video, audio, and voice attachments. This prevents spurious 400 errors from Camel's `IncomingMessage` serialization which includes empty attachment objects.

### Changes

- **AgentRuntime Thread Pool** — `AgentRuntime` now uses a bounded virtual thread executor instead of an unbounded cached thread pool, preventing thread starvation under load.

- **TenantAgentRuntimeFactory Logging** — Added INFO-level logging for tool resolution, showing profile, registry size, resolved tool count, and tool names per tenant. Useful for diagnosing tool visibility issues.

- **SkillLoader JAR FileSystem Fix** — Fixed `SkillLoader` to handle JAR-based `FileSystem` instances correctly when scanning bundled skills from classpath resources.

### Configuration

New Telegram configuration properties:

| Property | Default | Description |
|---|---|---|
| `jaiclaw.channels.telegram.http-client` | `default` | HTTP client implementation: `default` (JDK HttpClient) or bean name |
| `jaiclaw.channels.telegram.polling-strategy` | `builtin` | Polling strategy: `builtin` (getUpdates loop) or `camel` (Apache Camel) |
| `jaiclaw.channels.telegram.rate-limit` | `10` | Max messages per minute per user (auto-configured `UserRateLimiter`) |
| `jaiclaw.channels.telegram.allowed-users` | — | Comma-separated Telegram user IDs for authorization |

### New Classes

| Class | Module | Description |
|---|---|---|
| `TelegramHttpClient` | jaiclaw-channel-telegram | Interface for pluggable HTTP transport |
| `DefaultTelegramHttpClient` | jaiclaw-channel-telegram | Default JDK HttpClient implementation |
| `TelegramPollingStrategy` | jaiclaw-channel-telegram | Interface for pluggable polling |
| `CamelTelegramPollingStrategy` | jaiclaw-channel-telegram | Apache Camel-based polling implementation |
| `CamelPollingAutoConfiguration` | jaiclaw-spring-boot-starter | Auto-configures Camel polling when Camel is on classpath |

---

## 0.3.0

Released 2026-05-10.

### New Features

- **Project Scaffolder** — New `jaiclaw-project-scaffolder` CLI tool for YAML manifest-driven project generation. Generates complete Spring Boot project structures from declarative specifications.

### Infrastructure

- Version bump to 0.3.0 release. All modules aligned to 0.3.0.

---

## 0.2.0

Released 2026-05-04.

### New Features

- **OAuth Resource Owner Password Flow** — Added `ResourceOwnerPasswordFlow` in `jaiclaw-identity` supporting Keycloak (`KeycloakOAuthProvider`) and Spring Authorization Server (`SpringAuthServerOAuthProvider`). `OAuthFlowManager` updated to handle the new flow type.

- **MiniMax Thinking Filter** — New `MiniMaxThinkingFilterAutoConfiguration` in `jaiclaw-spring-boot-starter` wraps all `ChatModel` beans via `BeanPostProcessor` to strip MiniMax's always-on thinking content blocks from responses. Enabled by default (`jaiclaw.models.minimax.filter-thinking: true`). Disable with `jaiclaw.models.minimax.filter-thinking: false`.

### Dependency Upgrades

- Embabel Agent: 0.3.4 → 0.3.5
- Spring Shell: 3.4.0 → 3.4.2
- Maven GPG Plugin: 3.2.7 → 3.2.8

### Infrastructure

- Added Spock test support to `jaiclaw-spring-boot-starter` (groovy, spock-core, byte-buddy, gmavenplus-plugin)
- New test specs for OAuth ROPC flow (3 specs) and MiniMax Thinking Filter (1 spec)
- Documentation updates to `OPERATIONS.md` covering MiniMax Thinking Filter configuration

## 0.1.0

Released 2026-04-22. Initial release.

### Baseline

- **10 core modules** — jaiclaw-core, jaiclaw-channel-api, jaiclaw-config, jaiclaw-tools, jaiclaw-agent, jaiclaw-skills, jaiclaw-plugin-sdk, jaiclaw-memory, jaiclaw-security, jaiclaw-gateway
- **7 channel adapters** — Telegram, Slack, Discord, Email, SMS, Signal, Teams
- **25 extension modules** — documents, media, audit, compaction, browser, code, cron, voice, identity, canvas, docstore, subscription, tools-k8s, tools-security, calendar, messaging, discord-tools, slack-tools, voice-call, camel, embabel-delegate, docs, and more
- **4 standalone CLI tools** — perplexity, rest-cli-architect, skill-creator, prompt-analyzer
- **3 runnable apps** — gateway-app, shell, cron-manager-app
- **19 starters** — auto-configuration modules for all major features
- **25 examples** — demonstrating channels, tools, skills, MCP, Embabel integration, and more
- **BOM** (`jaiclaw-bom`) for version alignment
- **Maven plugin** (`jaiclaw-maven-plugin`) with `jaiclaw:analyze` goal for CI token budget enforcement
- **Multi-tenancy** — single/multi mode with JWT-based tenant resolution
- **MCP hosting** — tool and resource providers via REST, SSE, and stdio
- **Security hardening** — opt-in profile with SSRF protection, webhook verification, timing-safe comparisons
- **500+ Spock tests** across 28+ modules
