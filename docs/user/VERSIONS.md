# Version History

This document tracks notable changes between JaiClaw releases.

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
