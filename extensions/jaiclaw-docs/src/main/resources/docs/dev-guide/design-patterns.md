# JaiClaw Design Patterns & Conventions

[Back to Developer Guide](../JAICLAW-DEVELOPER-GUIDE.md)

---

## Table of Contents

1. [Sealed Interfaces](#sealed-interfaces)
2. [Java Records for Value Types](#java-records-for-value-types)
3. [SPI Pattern](#spi-pattern)
4. [ThreadLocal Context Holders](#threadlocal-context-holders)
5. [Tool Registration & Bridging](#tool-registration--bridging)
6. [Plugin Discovery & Hooks](#plugin-discovery--hooks)
7. [Multi-Tenancy Pattern](#multi-tenancy-pattern)
8. [Channel Adapter Dual-Mode](#channel-adapter-dual-mode)
9. [Session Key Convention](#session-key-convention)
10. [Auto-Configuration Ordering](#auto-configuration-ordering)
11. [Dual-Mode CLI Build](#dual-mode-cli-build)
12. [Composable App Assembly](#composable-app-assembly)
13. [Mock HTTP Server Integration Testing](#mock-http-server-integration-testing)

---

## Sealed Interfaces

JaiClaw uses sealed interfaces extensively in `jaiclaw-core` for type-safe algebraic data types. This eliminates invalid states and enables exhaustive pattern matching.

### Message Hierarchy

```
sealed interface Message
  ├── record UserMessage(String content, Map<String,String> metadata)
  ├── record AssistantMessage(String content, Map<String,String> metadata)
  ├── record SystemMessage(String content)
  └── record ToolResultMessage(String toolCallId, String content, boolean success)
```

### ToolResult Hierarchy

```
sealed interface ToolResult
  ├── record Success(String output)
  └── record Error(String error)
```

### DeliveryResult Hierarchy

```
sealed interface DeliveryResult
  ├── record Success(String messageId)
  └── record Failure(String error, Throwable cause)
```

### Why Sealed Interfaces

- **Exhaustive switch**: The compiler warns if a switch doesn't cover all permitted subtypes
- **No invalid states**: Only the explicitly permitted records can exist
- **Pattern matching**: Java 21 pattern matching works naturally with sealed hierarchies
- **Immutability**: Records are inherently immutable, making the domain model thread-safe

---

## Java Records for Value Types

All value types in JaiClaw are Java records. This provides:

- Immutable state (no setters)
- Auto-generated `equals()`, `hashCode()`, `toString()`
- Compact constructors for validation
- Pattern matching support

### Examples

```java
// Domain model
public record Session(String sessionKey, List<Message> messages, Instant createdAt) {}
public record CronJob(String id, String cron, String command, boolean enabled) {}
public record SkillMetadata(String name, String version, List<String> tenantIds) {}

// Configuration
public record JaiClawProperties(String name, SecurityProperties security, ...) {}

// Channel messages
public record ChannelMessage(String channel, String senderId, String content,
                             List<Attachment> attachments, Map<String,String> metadata) {}
```

### Convention

- Domain objects in `jaiclaw-core`: pure Java records, no Spring annotations
- Configuration records: annotated with `@ConfigurationProperties`
- Channel messages: records with optional attachments and metadata maps

---

## SPI Pattern

JaiClaw follows a consistent SPI (Service Provider Interface) pattern:

```
interface FooSpi              ← abstract contract (in core or SPI module)
  └── class DefaultFooImpl    ← sensible default implementation
  └── class CustomFooImpl     ← pluggable alternative (user-provided or extension module)
```

### Key SPIs

| SPI Interface | Default Implementation | Module |
|---|---|---|
| `ChannelAdapter` | Per-channel adapters | `jaiclaw-channel-api` |
| `MemorySearchManager` | `InMemorySearchManager` | `jaiclaw-memory` |
| `AuditLogger` | `InMemoryAuditLogger` | `jaiclaw-audit` |
| `MediaAnalyzer` | `CompositeMediaAnalyzer` | `jaiclaw-media` |
| `JaiClawPlugin` | (user-provided) | `jaiclaw-plugin-sdk` |
| `McpToolProvider` | (user-provided) | `jaiclaw-gateway` |
| `TenantResolver` | `BotTokenTenantResolver`, `JwtTenantResolver` | `jaiclaw-security` |
| `AttachmentRouter` | `LoggingAttachmentRouter` | `jaiclaw-gateway` |
| `TtsProvider` / `SttProvider` | `OpenAiTtsProvider` / `OpenAiSttProvider` | `jaiclaw-voice` |
| `IdentityStore` | `JsonFileIdentityStore` | `jaiclaw-identity` |
| `AgentOrchestrationPort` | `NoOpOrchestrationPort` | `jaiclaw-agent` |

### Wiring Convention

SPIs are wired via Spring's `@ConditionalOnMissingBean`:

```java
@Bean
@ConditionalOnMissingBean(AuditLogger.class)
public AuditLogger auditLogger() {
    return new InMemoryAuditLogger();
}
```

Users override by declaring their own `@Bean` of the same type. The auto-configuration's `@ConditionalOnMissingBean` backs off when a user-provided bean exists.

---

## ThreadLocal Context Holders

JaiClaw uses ThreadLocal-based context holders for cross-cutting concerns that flow through the call stack without being passed as explicit parameters.

### TenantContext

```java
public class TenantContext {
    private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();

    public static void setTenantId(String tenantId) { ... }
    public static String getTenantId() { ... }
    public static void clear() { ... }
}
```

Set by `TenantResolver` implementations in the gateway filter chain. Cleared after request processing.

### SecurityContext

```java
public class SecurityContext {
    private static final ThreadLocal<SecurityPrincipal> CURRENT_PRINCIPAL = new ThreadLocal<>();

    public static void setPrincipal(SecurityPrincipal principal) { ... }
    public static SecurityPrincipal getPrincipal() { ... }
    public static void clear() { ... }
}
```

### Virtual Thread Safety

JaiClaw uses virtual threads for hook execution (`HookRunner`). ThreadLocal values are inherited by virtual threads created within the same scope, but care must be taken to clear them after use to avoid leaks across request boundaries.

---

## Tool Registration & Bridging

JaiClaw has its own tool SPI that bridges to Spring AI's tool system.

### Registration Flow

```
1. ToolCallback implementations (JaiClaw SPI)
   ├── Built-in tools (registered by ToolRegistry at startup)
   ├── Extension tools (contributed by extension modules)
   └── Plugin tools (registered dynamically by JaiClawPlugin instances)
         │
         ▼
2. ToolRegistry (central registry, holds all JaiClaw ToolCallbacks)
         │
         ▼
3. SpringAiToolBridge (converts JaiClaw ToolCallback → Spring AI ToolCallback)
         │
         ▼
4. Spring AI ChatClient (sends available tools to LLM)
```

### ToolProfile Filtering

Tools declare which profiles they're available in:

```java
public interface ToolCallback {
    String name();
    String description();
    ToolResult execute(Map<String, Object> args);
    boolean isAvailableIn(ToolProfile profile);
}
```

- `ToolProfile.FULL` — grants access to all tools
- Other profiles (e.g., `SAFE`, `READONLY`) — tools must explicitly opt in
- The active profile is set per agent/session

### Embabel Bridge

For Embabel Agent integration, `EmbabelOrchestrationBridge` converts JaiClaw tool invocations into Embabel's orchestration format, enabling JaiClaw tools to participate in Embabel's goal-driven agent loops.

---

## Plugin Discovery & Hooks

### Discovery

Plugins are discovered through three mechanisms, merged into a single registry:

```
1. Spring component scanning    → @Component classes implementing JaiClawPlugin
2. ServiceLoader (META-INF)     → SPI-declared plugins (no Spring required)
3. Explicit registration        → PluginRegistry.register(plugin)
```

### Plugin API

```java
public interface JaiClawPlugin {
    String name();
    PluginKind kind();
    void initialize(PluginApi api);
    void shutdown();
}
```

`PluginApi` provides access to register tools, listen for hooks, access session state, and interact with the agent runtime.

### Hook System

Hooks are lifecycle events that plugins can listen to:

```java
public enum HookName {
    BEFORE_AGENT_RUN,
    AFTER_AGENT_RUN,
    BEFORE_TOOL_EXECUTE,
    AFTER_TOOL_EXECUTE,
    ON_SESSION_CREATED,
    ON_SESSION_DESTROYED,
    ON_MESSAGE_RECEIVED,
    ON_MESSAGE_SENT
}
```

`HookRunner` executes hook listeners on virtual threads for non-blocking, fire-and-forget execution. Hooks with return values (gate hooks) run synchronously.

---

## Multi-Tenancy Pattern

JaiClaw supports multi-tenant deployments where a single gateway serves multiple isolated tenants.

### Tenant Resolution Chain

```
Inbound request
  │
  ├── JwtTenantResolver: extracts tenantId from JWT claims
  ├── BotTokenTenantResolver: maps channel bot tokens to tenants
  └── CompositeTenantResolver: tries each resolver in order
        │
        ▼
  TenantContext.setTenantId(tenantId)
        │
        ▼
  Per-tenant isolation:
    - SessionManager scopes sessions by tenant
    - SkillLoader filters skills by tenantIds
    - MemorySearchManager isolates memory per tenant
    - AuditLogger tags events with tenantId
```

### Skill Tenant Filtering

```java
public record SkillMetadata(String name, String version, List<String> tenantIds) {
    public boolean isAvailableForTenant(String tenantId) {
        return tenantIds.isEmpty() || tenantIds.contains(tenantId);
    }
}
```

Skills with empty `tenantIds` are available to all tenants. Skills with specific tenant IDs are restricted.

---

## Channel Adapter Dual-Mode

Every messaging channel adapter supports two modes:

| Mode | Use Case | How It Works |
|---|---|---|
| **Local dev** | No public endpoint | Adapter initiates connection (polling/WebSocket) |
| **Production** | Webhook-based | Platform pushes events to gateway endpoint |

### Mode Selection

| Channel | Local Dev Mode | Trigger |
|---|---|---|
| Telegram | Long polling (`getUpdates`) | `webhookUrl` is blank |
| Slack | Socket Mode (WebSocket) | `appToken` is set |
| Discord | Gateway WebSocket | `useGateway` is `true` |
| Email | IMAP polling | Always polling-based |
| SMS | N/A (always webhook) | Requires ngrok for local dev |

### Implementation Pattern

Each adapter follows this structure:

```java
public class TelegramAdapter implements ChannelAdapter {

    @Override
    public void start() {
        if (webhookUrl != null && !webhookUrl.isBlank()) {
            registerWebhook();         // production mode
        } else {
            startPolling();            // local dev mode
        }
    }

    @Override
    public void sendMessage(ChannelMessage message) {
        // Same outbound logic regardless of mode
        telegramApi.sendMessage(message.peerId(), message.content());
    }
}
```

The key principle: **inbound** varies by mode, **outbound** is always the same.

---

## Session Key Convention

Session keys follow a composite format for isolation:

```
{agentId}:{channel}:{accountId}:{peerId}
```

| Segment | Description | Example |
|---|---|---|
| `agentId` | Agent identifier (supports multi-agent) | `default` |
| `channel` | Channel adapter ID | `telegram`, `slack`, `shell` |
| `accountId` | Account/workspace/bot identifier | `bot123`, `workspace1` |
| `peerId` | End-user or conversation identifier | `user456`, `C04ABCDEF` |

### Examples

```
default:telegram:bot123:user456
default:slack:workspace1:C04ABCDEF
default:discord:guild789:user012
default:shell:local:user
default:email:bot@example.com:sender@client.com
default:sms:+15551234567:+15559876543
```

This ensures complete session isolation: same user on different channels gets separate sessions, same channel with different bots gets separate sessions.

**Security note**: When `jaiclaw.channels.telegram.mask-bot-token=true`, the Telegram `accountId` uses a SHA-256 hash prefix (`tg_<12 hex chars>`) instead of the raw bot token, preventing token leakage in session keys and logs.

---

## Auto-Configuration Ordering

Spring Boot auto-configuration classes must be carefully ordered because `@ConditionalOnBean` only evaluates against beans that are already defined.

### The Problem

```java
// THIS DOES NOT WORK RELIABLY
@AutoConfiguration
public class SingleAutoConfig {

    @Configuration
    static class CoreConfig {
        @Bean ToolRegistry toolRegistry() { ... }
    }

    @Configuration
    @ConditionalOnBean(ToolRegistry.class)  // ← evaluated in SAME PASS as CoreConfig!
    static class GatewayConfig {
        @Bean GatewayService gateway(ToolRegistry tr) { ... }
    }
}
```

### The Solution

Split into separate `@AutoConfiguration` classes with explicit ordering:

```java
@AutoConfiguration(after = ChatClientAutoConfiguration.class)
public class JaiClawAutoConfiguration {
    @Bean ToolRegistry toolRegistry() { ... }
    @Bean AgentRuntime agentRuntime() { ... }
}

@AutoConfiguration(after = JaiClawAutoConfiguration.class)
@ConditionalOnBean(AgentRuntime.class)
public class JaiClawGatewayAutoConfiguration {
    @Bean GatewayService gateway() { ... }
}

@AutoConfiguration(after = JaiClawGatewayAutoConfiguration.class)
public class JaiClawChannelAutoConfiguration {
    // Each channel gated on @ConditionalOnClass + @ConditionalOnBean(WebhookDispatcher)
}
```

See [starter.md](starter.md) for the full 4-phase auto-configuration reference.

---

## Dual-Mode CLI Build

Standalone CLI modules (e.g., `jaiclaw-perplexity`, `jaiclaw-rest-cli-architect`) support two build modes:

| Mode | Profile | Output | Use Case |
|---|---|---|---|
| Library | (default) | Regular JAR | Dependency in other modules |
| Standalone | `-Pstandalone` | Executable fat JAR | Direct CLI usage |

### Key Rules

1. **Do NOT add `spring-ai-starter-model-anthropic`** to the standalone profile unless the module actually uses Spring AI. It triggers `AnthropicChatAutoConfiguration` which fails without API keys.

2. **Always add hyphenated CLI aliases** for multi-word commands:
   ```java
   @ShellMethod(key = {"pplx search", "pplx-search"})
   ```
   Spring Shell's `NonInteractiveShellRunner` joins CLI args with spaces, making multi-word keys unreliable outside the REPL.

3. **Set `spring.main.web-application-type: none`** in `application.yml` for CLI modules.

### Invocation

```bash
# Non-interactive (command line)
java -jar jaiclaw-perplexity.jar pplx-search "query" --num-results 5

# Interactive (REPL)
java -jar jaiclaw-perplexity.jar
> pplx search query
```

---

## Composable App Assembly

JaiClaw apps are composed from three layers: a reusable **extension module** (library), a **starter POM** (dependency aggregator), and a **thin app module** (Spring Boot entry point). This pattern enables extensions to run standalone *or* be embedded into other apps like the gateway.

### The Three Layers

```
┌─────────────────────────────────────────────────────────┐
│  Layer 3: THIN APP MODULE (apps/)                        │
│  - Spring Boot @SpringBootApplication entry point        │
│  - App-specific components (shell commands, standalone   │
│    config for McpServerRegistry/@ConditionalOnMissingBean)│
│  - Depends on starter POM                                │
└─────────────────────┬───────────────────────────────────┘
                      │ depends on
┌─────────────────────▼───────────────────────────────────┐
│  Layer 2: STARTER POM (jaiclaw-starters/)                  │
│  - <packaging>pom</packaging>                            │
│  - Aggregates extension + transitive dependencies        │
│  - No Java code — pure dependency management             │
└─────────────────────┬───────────────────────────────────┘
                      │ depends on
┌─────────────────────▼───────────────────────────────────┐
│  Layer 1: EXTENSION MODULE (extensions/)                  │
│  - Reusable library with all business logic              │
│  - @AutoConfiguration + @ConditionalOnProperty for       │
│    opt-in activation                                     │
│  - Implements McpToolProvider for MCP tool exposure       │
│  - Zero coupling to any specific app                     │
└─────────────────────────────────────────────────────────┘
```

### Extension Module Pattern

The extension module contains all business logic and auto-configures itself when enabled:

```java
@AutoConfiguration
@ConditionalOnProperty(name = "jaiclaw.cron.manager.enabled", havingValue = "true")
public class CronManagerAutoConfiguration {

    @Bean
    public CronJobManagerService cronJobManagerService(...) { ... }

    @Bean
    public CronManagerMcpToolProvider cronManagerMcpToolProvider(CronJobManagerService svc) {
        return new CronManagerMcpToolProvider(svc);
    }
}
```

Key points:
- `@ConditionalOnProperty` gates activation — the extension does nothing unless explicitly enabled
- Implements `McpToolProvider` — beans are auto-discovered by the gateway's `McpServerRegistry(List<McpToolProvider>)`
- No dependency on gateway-app or shell — pure library

### Starter POM Pattern

```xml
<project>
    <artifactId>jaiclaw-starter-cron</artifactId>
    <packaging>pom</packaging>
    <dependencies>
        <dependency>
            <groupId>io.jaiclaw</groupId>
            <artifactId>jaiclaw-cron-manager</artifactId>  <!-- extension -->
        </dependency>
        <!-- transitive: jaiclaw-cron, jaiclaw-gateway, Spring Batch, H2, etc. -->
    </dependencies>
</project>
```

### Thin App Module Pattern

The app module is a minimal launcher with app-specific components:

```java
// Entry point
@SpringBootApplication
public class CronManagerApplication {
    public static void main(String[] args) {
        SpringApplication.run(CronManagerApplication.class, args);
    }
}

// Shell commands (app-specific — not in the extension)
@ShellComponent
public class CronJobCommands { ... }

// Standalone config — provides beans that the gateway normally provides
@Configuration
@ConditionalOnMissingBean(McpServerRegistry.class)
public class StandaloneCronManagerConfiguration {
    @Bean
    public McpServerRegistry mcpServerRegistry(List<McpToolProvider> providers) {
        return new McpServerRegistry(providers);
    }
    @Bean
    public McpController mcpController(McpServerRegistry registry) {
        return new McpController(registry);
    }
}
```

### Embedding Pattern

The same extension auto-activates inside the gateway via a Maven profile:

```xml
<!-- in jaiclaw-gateway-app/pom.xml -->
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

With `jaiclaw.cron.manager.enabled=true` in application.yml, the extension auto-config fires and its `McpToolProvider` bean is collected by the gateway's existing `McpServerRegistry`.

### Two Deployment Modes

```
Standalone Mode:                     Embedded Mode:
┌─────────────────────┐              ┌──────────────────────────────┐
│ cron-manager app    │              │ jaiclaw-gateway-app (-Pcron)   │
│                     │              │                              │
│  CronManagerApp     │              │  JaiClawGatewayApplication     │
│  CronJobCommands    │              │  (all gateway beans)         │
│  StandaloneCron-    │              │                              │
│    ManagerConfig    │              │  CronManagerAutoConfig       │
│                     │              │    (embedded, property-gated)│
│  CronManager-       │              │  CronManagerMcpToolProvider  │
│    AutoConfig       │              │    → auto-discovered by      │
│  (extension beans)  │              │      McpServerRegistry       │
└─────────────────────┘              └──────────────────────────────┘
     ↕ MCP                                   ↕ in-process
 Remote clients                          No network hop
```

### When to Use This Pattern

Use Composable App Assembly when a feature:
1. Has its own lifecycle (scheduling, persistence, background processing)
2. Benefits from standalone deployment (independent scaling, isolation)
3. Can also run embedded for simpler deployments (single JVM)
4. Exposes tools or services via MCP

### Example: cron-manager

| Layer | Module | Contents |
|---|---|---|
| Extension | `extensions/jaiclaw-cron-manager` | `CronJobManagerService`, `CronManagerAutoConfiguration`, `CronManagerMcpToolProvider`, H2 persistence, Spring Batch |
| Starter | `jaiclaw-starters/jaiclaw-starter-cron` | POM aggregating extension + dependencies |
| App | `apps/jaiclaw-cron-manager-app` | `CronManagerApplication`, `CronJobCommands`, `StandaloneCronManagerConfiguration` |

### Example: system-monitor

The `jaiclaw-examples/system-monitor` example (`jaiclaw-example-system-monitor`) demonstrates the Composable App Assembly pattern in a real-world scenario: a gateway with embedded cron manager that sends daily Linux health reports to a Telegram user.

**What it composes:**

| Starter / Dependency | What It Brings |
|---|---|
| `jaiclaw-starter-gateway` | REST + WebSocket + Telegram channel adapter |
| `jaiclaw-starter-cron` | Embedded cron manager with MCP tools, batch, H2 |
| `spring-ai-starter-model-anthropic` | Anthropic LLM provider |

**Custom tools wired by the example:**

| Tool | Class | Purpose |
|---|---|---|
| `system_command` | `WhitelistedCommandTool` | Safe execution of whitelisted Linux monitoring commands |
| `show_system_commands` | `ShowAllowedCommandsTool` | Discovery tool listing allowed commands |
| `send_telegram` | `SendTelegramTool` | Delivers formatted reports to a Telegram chat |

**How it works:**

1. `SystemMonitorConfig` wires the tools and registers a cron job on startup via `CronJobManagerService`
2. The cron job fires on a configurable schedule (default: 7 AM daily)
3. The agent runs the job's prompt: discovers available commands, collects system metrics, formats a health report, and sends it to the configured Telegram chat via `send_telegram`
4. The gateway also accepts ad-hoc Telegram messages for interactive queries

**Configuration:**

```yaml
jaiclaw:
  cron:
    manager:
      enabled: true      # activates CronManagerAutoConfiguration
  channels:
    telegram:
      enabled: true
      bot-token: ${TELEGRAM_BOT_TOKEN}
sysmonitor:
  schedule: ${MONITOR_SCHEDULE:0 7 * * *}
  timezone: ${MONITOR_TIMEZONE:America/New_York}
  telegram:
    chat-id: ${TELEGRAM_CHAT_ID}
```

This example validates that the extension + starter + thin app pattern scales to real applications: a single `@SpringBootApplication` class, a `@Configuration` class for tool wiring and job registration, and no boilerplate infrastructure code.

---

## Mock HTTP Server Integration Testing

For modules that make outbound HTTP calls (OAuth flows, webhook dispatchers, external API clients), JaiClaw uses a local mock HTTP server pattern that exercises real HTTP without external network calls.

### The Pattern

```
┌──────────────────────────────────┐
│       Spock IT Spec              │
│  1. Start MockServer (port 0)   │
│  2. Configure JSON responses    │
│  3. Build config → mock baseUrl │
│  4. Exercise code under test    │
│  5. Assert results + requests   │
└──────────┬───────────────────────┘
           │ real java.net.http.HttpClient
           ▼
┌──────────────────────────────────┐
│  com.sun.net.httpserver.HttpServer│
│  127.0.0.1:{random-port}        │
│  • Configurable per-path JSON   │
│  • Request history recording    │
│  • Stateful endpoint sequences  │
└──────────────────────────────────┘
```

### Key Properties

| Property | Choice | Rationale |
|----------|--------|-----------|
| Server tech | JDK `com.sun.net.httpserver.HttpServer` | Zero dependencies, JDK-bundled |
| Port allocation | Random (port 0) | CI-safe, parallel-safe |
| HTTP client | Real `java.net.http.HttpClient` | Tests actual serialization, not mocked interfaces |
| Maven phase | `verify` via `maven-failsafe-plugin` | ITs separate from unit tests |
| Activation | Maven profile (`-Pintegration-test`) | Opt-in, doesn't slow default builds |

### Implementation: `MockOAuthServer`

The first instance of this pattern is `MockOAuthServer` in `jaiclaw-identity`, which provides:

- `staticEndpoint(path, statusCode, json)` — fixed response
- `pendingThenSuccess(path, pendingCount, successJson)` — stateful polling simulation
- `errorEndpoint(path, statusCode, errorCode, description)` — OAuth error responses
- `getRequests(path)` → `List<RecordedRequest>` — request history for assertions
- `RecordedRequest.formParams()` — parsed `application/x-www-form-urlencoded` body

### When to Use This Pattern

Use mock HTTP server integration tests when:

1. The code makes real HTTP calls via `HttpClient` / `HttpURLConnection`
2. You need to verify request serialization (headers, body encoding, form params)
3. The protocol has stateful sequences (polling, retry, backoff)
4. Unit-level mocking would bypass too much of the HTTP stack

Don't use this pattern for:
- Simple request/response that can be tested by mocking the HTTP client
- Tests that only need to verify business logic unrelated to HTTP

### Maven Setup

Add to the module's `pom.xml`:

```xml
<profiles>
    <profile>
        <id>integration-test</id>
        <build>
            <plugins>
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-failsafe-plugin</artifactId>
                    <version>3.5.2</version>
                    <configuration>
                        <includes>
                            <include>**/*IT.java</include>
                        </includes>
                    </configuration>
                    <executions>
                        <execution>
                            <goals>
                                <goal>integration-test</goal>
                                <goal>verify</goal>
                            </goals>
                        </execution>
                    </executions>
                </plugin>
            </plugins>
        </build>
    </profile>
</profiles>
```

Name IT specs with the `*IT.groovy` suffix (e.g., `AuthorizationCodeFlowIT.groovy`). Failsafe's `**/*IT.java` include picks up compiled Groovy classes.

See [OAuth Integration Tests Architecture](../../jaiclaw/docs/dev/OAUTH-INTEGRATION-TESTS.md) for the full reference implementation.
