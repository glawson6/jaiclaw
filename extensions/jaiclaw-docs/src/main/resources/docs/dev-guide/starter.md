# JaiClaw Spring Boot Starter Reference

[Back to Developer Guide](../JAICLAW-DEVELOPER-GUIDE.md)

---

## Overview

The `jaiclaw-spring-boot-starter` module provides auto-configuration that wires all JaiClaw components into a Spring Boot application. It is split into **4 phases** across separate `@AutoConfiguration` classes to guarantee correct `@ConditionalOnBean` evaluation order.

**Module**: `jaiclaw-spring-boot-starter`
**Package**: `io.jaiclaw.starter`

---

## Auto-Configuration Classes

| Class | Phase | After | Purpose |
|---|---|---|---|
| Spring AI provider auto-configs | 1 | — | Create `ChatModel` + `ChatClient.Builder` |
| `JaiClawAutoConfiguration` | 2 | `ChatClientAutoConfiguration` | Core JaiClaw beans |
| `JaiClawGatewayAutoConfiguration` | 3 | `JaiClawAutoConfiguration` | Gateway HTTP/WS/MCP layer |
| `JaiClawChannelAutoConfiguration` | 4 | `JaiClawGatewayAutoConfiguration` | Channel adapters |

---

## Phase 1: Spring AI Provider Auto-Configs

These are provided by Spring AI starter dependencies. Only the **enabled** provider activates.

```
AnthropicChatAutoConfiguration      ─── creates ──→  ChatModel (Anthropic)
  or
OpenAiChatAutoConfiguration         ─── creates ──→  ChatModel (OpenAI)
  or
GoogleGenAiChatAutoConfiguration    ─── creates ──→  ChatModel (Gemini)
  or
OllamaChatAutoConfiguration         ─── creates ──→  ChatModel (Ollama)
        │
        ▼
ChatClientAutoConfiguration         ─── creates ──→  ChatClient.Builder
  @ConditionalOnClass(ChatClient)                     (requires ChatModel)
```

Controlled by `spring.ai.*.enabled` properties and classpath presence of the provider starter JARs.

---

## Phase 2: JaiClawAutoConfiguration

`@AutoConfigureAfter(ChatClientAutoConfiguration.class)`

Core JaiClaw beans. This is the foundation phase.

```
JaiClawAutoConfiguration
  │
  ├── proxyFactoryConfigurer    ProxyFactoryConfigurer (configures ProxyAwareHttpClientFactory)
  ├── proxyRestClientCustomizer RestClientCustomizer (proxy-aware JdkClientHttpRequestFactory)
  ├── toolRegistry              ToolRegistry (+ built-in tools; SSRF protection from config)
  ├── sessionManager            SessionManager (in-memory session store)
  ├── skillLoader               SkillLoader (loads bundled skills from classpath)
  ├── pluginRegistry            PluginRegistry (discovers + initializes plugins)
  │
  ├── vectorStoreSearchManager  VectorStoreSearchManager
  │     @ConditionalOnBean(VectorStore.class)
  │     ── or ──
  ├── inMemorySearchManager     InMemorySearchManager
  │     @ConditionalOnMissingBean(MemorySearchManager.class)
  │
  ├── agentRuntime              AgentRuntime
  │     @ConditionalOnBean(ChatClient.Builder.class)
  │     (SessionManager, ChatClient.Builder, ToolRegistry, SkillLoader, ...)
  │
  ├── channelRegistry           ChannelRegistry
  │     (auto-collects all ChannelAdapter beans via ObjectProvider)
  │
  ├── noOpOrchestrationPort     NoOpOrchestrationPort
  │     @ConditionalOnMissingBean(AgentOrchestrationPort.class)
  │
  └── resolveToolsFromEnvironment()  (internal)
        Reads jaiclaw.agent.agents.{name}.tools.profile from Environment
        as fallback for Spring Boot record binding failures with
        Map<String, Record> nesting. Follows existing llmOverride pattern.
```

### Key Conditions

| Bean | Condition | Effect if Missing |
|---|---|---|
| `proxyFactoryConfigurer` | Always | Configures `ProxyAwareHttpClientFactory` from YAML or env vars |
| `proxyRestClientCustomizer` | Always (no-op if no proxy resolved) | Injects proxy-aware `JdkClientHttpRequestFactory` into `RestClient.Builder` |
| `agentRuntime` | `@ConditionalOnBean(ChatClient.Builder)` | No agent — `chat` fails with "No LLM configured" |
| `vectorStoreSearchManager` | `@ConditionalOnBean(VectorStore)` | Falls back to `inMemorySearchManager` |
| `noOpOrchestrationPort` | `@ConditionalOnMissingBean(AgentOrchestrationPort)` | Backs off if Embabel bridge provides one |

---

## Phase 3: JaiClawGatewayAutoConfiguration

`@AutoConfigureAfter(JaiClawAutoConfiguration.class)`

Gateway HTTP/WS layer. **Entire class** is gated on:
- `@ConditionalOnClass(GatewayService.class)` — `jaiclaw-gateway` JAR must be on classpath
- `@ConditionalOnBean(AgentRuntime.class)` — an LLM provider must be configured

```
JaiClawGatewayAutoConfiguration
  │
  ├── webhookDispatcher           WebhookDispatcher
  │
  ├── jwtTenantResolver           JwtTenantResolver
  ├── botTokenTenantResolver      BotTokenTenantResolver
  ├── compositeTenantResolver     CompositeTenantResolver
  │     (aggregates all TenantResolver beans)
  │
  ├── loggingAttachmentRouter     LoggingAttachmentRouter
  │     @ConditionalOnMissingBean(AttachmentRouter.class)
  │
  ├── gatewayService              GatewayService
  │     (AgentRuntime, SessionManager, ChannelRegistry, WebhookDispatcher, ...)
  │
  ├── gatewayLifecycle            GatewayLifecycle or FilteredGatewayLifecycle
  │     (starts/stops channel adapters; uses FilteredGatewayLifecycle when a
  │      GatewayMessageFilter bean is present, e.g. TelegramUserIdFilter)
  │
  ├── gatewayController           GatewayController
  │     @RestController — /api/chat, /api/health, /webhook/*
  │
  ├── webSocketSessionHandler     WebSocketSessionHandler
  │     (WS /ws/session/{id})
  │
  ├── mcpServerRegistry           McpServerRegistry
  │     (collects McpToolProvider beans)
  │
  ├── mcpController               McpController
  │     @ConditionalOnBean(McpServerRegistry.class) — /mcp/*
  │
  ├── gatewayMetrics              GatewayMetrics
  │     (atomic request/error counters)
  │
  └── gatewayHealthIndicator      GatewayHealthIndicator
        (UP/DEGRADED based on channel adapter status)
```

### Key Conditions

| Bean | Condition | Effect if Missing |
|---|---|---|
| Entire phase | `@ConditionalOnClass(GatewayService)` | No gateway — shell-only mode |
| Entire phase | `@ConditionalOnBean(AgentRuntime)` | No gateway — no LLM configured |
| `loggingAttachmentRouter` | `@ConditionalOnMissingBean(AttachmentRouter)` | Backs off if custom router provided |
| `mcpController` | `@ConditionalOnBean(McpServerRegistry)` | No MCP endpoints |

---

## Phase 4: JaiClawChannelAutoConfiguration

`@AutoConfigureAfter(JaiClawGatewayAutoConfiguration.class)`

Channel adapters. Each adapter is in a nested `@Configuration` class.

```
JaiClawChannelAutoConfiguration
  │
  ├── TelegramAutoConfiguration
  │     @ConditionalOnClass(TelegramAdapter.class)
  │     @ConditionalOnBean(WebhookDispatcher.class)
  │     ─── creates ──→ TelegramAdapter
  │     (passes verifyWebhook, webhookSecretToken, maskBotToken from config)
  │     ─── creates ──→ DefaultTelegramHttpClient (if no TelegramHttpClient bean)
  │     ─── creates ──→ TelegramUserIdFilter + UserRateLimiter
  │           (when jaiclaw.channels.telegram.allowed-users is set)
  │
  ├── CamelPollingAutoConfiguration
  │     @ConditionalOnClass(CamelContext.class, TelegramComponent.class)
  │     ─── creates ──→ CamelTelegramPollingStrategy
  │     (auto-activates when Apache Camel + camel-telegram are on classpath)
  │
  ├── SlackAutoConfiguration
  │     @ConditionalOnClass(SlackAdapter.class)
  │     @ConditionalOnBean(WebhookDispatcher.class)
  │     ─── creates ──→ SlackAdapter
  │     (passes verifySignature from config)
  │
  ├── DiscordAutoConfiguration
  │     @ConditionalOnClass(DiscordAdapter.class)
  │     @ConditionalOnBean(WebhookDispatcher.class)
  │     ─── creates ──→ DiscordAdapter
  │
  ├── EmailAutoConfiguration
  │     @ConditionalOnClass(EmailAdapter.class)
  │     ─── creates ──→ EmailAdapter
  │     (no WebhookDispatcher needed — IMAP polling is self-contained)
  │
  ├── SmsAutoConfiguration
  │     @ConditionalOnClass(SmsAdapter.class)
  │     ─── creates ──→ SmsAdapter
  │     (no WebhookDispatcher needed — outbound only + Twilio webhook)
  │
  └── AuditAutoConfiguration
        @ConditionalOnClass(AuditLogger.class)
        ─── creates ──→ InMemoryAuditLogger
```

### Channel Activation Rules

A channel adapter activates when:
1. Its module JAR is on the classpath (`@ConditionalOnClass`)
2. The gateway is active (`@ConditionalOnBean(WebhookDispatcher)`) — for webhook-based channels
3. Required configuration is present (bot token, API key, etc.)

---

## Complete Bean Dependency Chain

```
ChatModel (Spring AI — Phase 1)
  └─→ ChatClient.Builder (Spring AI — Phase 1)
        └─→ AgentRuntime (Phase 2)
              └─→ GatewayService (Phase 3)
                    ├─→ GatewayController   (/api/chat, /api/health, /webhook/*)
                    ├─→ GatewayLifecycle / FilteredGatewayLifecycle (starts channel adapters)
                    └─→ WebSocketSessionHandler (/ws/session/{id})
                          │
                    Channel Adapters (Phase 4)
                    ├─→ TelegramAdapter
                    ├─→ SlackAdapter
                    ├─→ DiscordAdapter
                    ├─→ EmailAdapter
                    └─→ SmsAdapter
```

---

## Why Separate Auto-Configuration Classes?

`@ConditionalOnBean` only checks beans that are already **defined** at evaluation time:

- Within a single `@AutoConfiguration`, nested `@Configuration` classes are evaluated in the **same pass**
- A nested class cannot reliably `@ConditionalOnBean` a bean from the enclosing class or a sibling
- Splitting into separate `@AutoConfiguration` classes with `@AutoConfigureAfter` guarantees each phase's beans are fully defined before the next phase evaluates

### Anti-Pattern (Broken)

```java
@AutoConfiguration
public class SingleAutoConfig {
    @Configuration
    static class CoreConfig {
        @Bean ToolRegistry toolRegistry() { ... }
    }

    @Configuration
    @ConditionalOnBean(ToolRegistry.class)  // BROKEN: same pass as CoreConfig
    static class GatewayConfig { ... }
}
```

### Correct Pattern (JaiClaw's Approach)

```java
@AutoConfiguration(after = ChatClientAutoConfiguration.class)
public class JaiClawAutoConfiguration { ... }

@AutoConfiguration(after = JaiClawAutoConfiguration.class)
@ConditionalOnBean(AgentRuntime.class)
public class JaiClawGatewayAutoConfiguration { ... }

@AutoConfiguration(after = JaiClawGatewayAutoConfiguration.class)
public class JaiClawChannelAutoConfiguration { ... }
```

---

## ObjectProvider Pattern

For optional bean injection in component-scanned classes (like `@ShellComponent`), use `ObjectProvider<T>` instead of `@ConditionalOnBean`:

```java
@ShellComponent
public class ChatCommands {
    private final ObjectProvider<AgentRuntime> agentRuntimeProvider;

    public ChatCommands(ObjectProvider<AgentRuntime> agentRuntimeProvider) {
        this.agentRuntimeProvider = agentRuntimeProvider;
    }

    @ShellMethod("chat")
    public String chat(String message) {
        AgentRuntime runtime = agentRuntimeProvider.getIfAvailable();
        if (runtime == null) {
            return "No LLM configured.";
        }
        return runtime.run(message);
    }
}
```

**Why**: Component-scanned beans are registered **before** auto-configuration runs, so `@ConditionalOnBean` on component-scanned classes evaluates too early.

---

## Feature Starters

In addition to the central `jaiclaw-spring-boot-starter`, JaiClaw provides feature-specific POM starters that aggregate an extension module with its transitive dependencies.

### jaiclaw-starter-cron

**ArtifactId**: `jaiclaw-starter-cron`
**Packaging**: POM (no Java code)
**Directory**: `jaiclaw-starters/jaiclaw-starter-cron`

Aggregates the cron manager extension with all required infrastructure dependencies:

| Dependency | Purpose |
|---|---|
| `jaiclaw-cron-manager` | Extension module (auto-config, business logic, persistence, MCP tools) |
| `spring-boot-starter-batch` | Spring Batch for execution lifecycle tracking |
| `spring-boot-starter-jdbc` | JDBC for H2 persistence |
| `h2` (runtime) | H2 embedded database |

**Usage in standalone app** (`apps/jaiclaw-cron-manager-app`):

```xml
<dependency>
    <groupId>io.jaiclaw</groupId>
    <artifactId>jaiclaw-starter-cron</artifactId>
    <type>pom</type>
</dependency>
```

**Usage in gateway** (via `-Pcron` Maven profile in `jaiclaw-gateway-app/pom.xml`):

```xml
<profile>
    <id>cron</id>
    <dependencies>
        <dependency>
            <groupId>io.jaiclaw</groupId>
            <artifactId>jaiclaw-starter-cron</artifactId>
        </dependency>
    </dependencies>
</profile>
```

Set `jaiclaw.cron.manager.enabled=true` in application.yml to activate the extension.

---

## CLI Tool Auto-Configurations

CLI tool modules under `tools/` each ship their own `@AutoConfiguration` class that conditionally registers tools when embedded in a JaiClaw runtime (i.e., when `ToolRegistry` is available). These are independent of the 4-phase starter auto-config — they run automatically via Spring Boot's `AutoConfiguration.imports` discovery.

| Module | Auto-Configuration | Registered Tools | Condition |
|---|---|---|---|
| jaiclaw-perplexity | `PerplexityAutoConfiguration` | perplexity-search, perplexity-web-search, perplexity-research | `@ConditionalOnBean(ToolRegistry.class)` |
| jaiclaw-rest-cli-architect | `CliArchitectAutoConfiguration` | scaffold-project, parse-openapi, validate-spec, from-openapi | `@ConditionalOnBean(ToolRegistry.class)` |
| jaiclaw-prompt-analyzer | `PromptAnalyzerAutoConfiguration` | prompt_analyze | `@ConditionalOnBean(ToolRegistry.class)` |

To embed a CLI tool module's tools in the gateway, add it as a dependency — no configuration needed.

---

## Post-Edit Workflow

After modifying any class in `jaiclaw-spring-boot-starter`:

```bash
./mvnw install -pl :jaiclaw-spring-boot-starter -DskipTests
```

This is required because `spring-boot:run` resolves the starter from the local Maven repository, not from the source tree.
