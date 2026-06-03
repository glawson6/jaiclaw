# JaiClaw App Modules Reference

[Back to Developer Guide](../JAICLAW-DEVELOPER-GUIDE.md)

---

## Table of Contents

1. [jaiclaw-gateway-app](#jaiclaw-gateway-app)
2. [jaiclaw-shell](#jaiclaw-shell)
3. [jaiclaw-cron-manager-app](#jaiclaw-cron-manager-app)

---

## jaiclaw-gateway-app

**Purpose**: Standalone Spring Boot gateway server providing REST API, WebSocket streaming, webhook receivers, MCP hosting, and all channel adapters.

**Package**: `io.jaiclaw.gateway.app`

### Class Reference

| Class | Type | Description |
|---|---|---|
| JaiClawGatewayApplication | class | Spring Boot entry point — `@SpringBootApplication` |

### Architecture

This is a thin launcher module. All functionality comes from the auto-configuration chain:

```
JaiClawGatewayApplication
  │
  └── @SpringBootApplication (triggers auto-config)
        │
        ├── Phase 1: Spring AI provider (ChatModel + ChatClient.Builder)
        ├── Phase 2: JaiClawAutoConfiguration (ToolRegistry, AgentRuntime, ...)
        ├── Phase 3: JaiClawGatewayAutoConfiguration (REST, WS, MCP, ...)
        └── Phase 4: JaiClawChannelAutoConfiguration (Telegram, Slack, ...)
```

### Endpoints

| Endpoint | Method | Description |
|---|---|---|
| `/api/chat` | POST | Synchronous chat (requires auth) |
| `/api/health` | GET | Health check |
| `/api/channels` | GET | List registered channels |
| `/webhook/{channel}` | POST | Inbound channel webhooks |
| `/ws/session/{key}` | WS | Streaming WebSocket |
| `/mcp/**` | Various | MCP server hosting (requires auth) |

### Running

```bash
# Local (default)
./start.sh

# Docker
./start.sh docker

# Maven
ANTHROPIC_API_KEY=sk-ant-... ./mvnw spring-boot:run -pl :jaiclaw-gateway-app
```

### Docker Image

Built via JKube with `-Pk8s` profile:

```bash
./mvnw package k8s:build -pl :jaiclaw-gateway-app -am -Pk8s -DskipTests
```

Base image: `eclipse-temurin:21-jre`

### JBang Profile

The `-Pjbang` profile repackages the fat JAR with `exec` classifier, leaving the original JAR as a normal Maven artifact for JBang resolution. This avoids the Spring Boot nested classloader issue where `BOOT-INF/classes/` is not visible to JBang's classpath.

---

## jaiclaw-shell

**Purpose**: Spring Shell CLI providing interactive multi-turn chat, system status, onboarding wizard, and all JaiClaw capabilities in a terminal interface.

**Package**: `io.jaiclaw.shell`

### Class Reference

| Class | Type | Description |
|---|---|---|
| JaiClawShellApplication | class | Spring Boot entry point |
| ChatCommands | class | Shell commands for multi-turn agent chat with session management |
| StatusCommands | class | Show system status (identity, agent, tools, plugins, sessions) |
| OnboardCommands | class | Shell command entry point for onboarding wizard |
| OnboardWizardOrchestrator | class | Orchestrates interactive onboarding with multiple steps |
| OnboardConfig | class | Configuration bean for RestTemplate with timeouts |
| OnboardResult | class | Mutable result accumulating user inputs during onboarding |
| OnboardResult.FlowMode | enum | Quickstart vs Manual flow mode |
| OnboardResult.ExistingConfigAction | enum | NONE, KEEP, MODIFY, RESET |
| OnboardResult.TelegramConfig | record | Telegram bot token and enabled flag |
| OnboardResult.SlackConfig | record | Slack bot token, signing secret, app token, enabled flag |
| OnboardResult.DiscordConfig | record | Discord bot token, application ID, enabled flag |
| OnboardResult.SkillsConfig | record | Enabled bundled skills and workspace directory |
| OnboardResult.McpTransportType | enum | STDIO, SSE, HTTP MCP transport |
| OnboardResult.McpServerConfig | record | MCP server config (name, transport, command/URL/auth) |
| WizardStep | interface | Single step in onboarding wizard |
| ConfigLocation | class | Utility for JaiClaw config directory and file paths |
| EnvFileWriter | class | Generates .env file with environment variables |
| YamlConfigWriter | class | Generates application-local.yml YAML configuration |

### Class Relationships

```
JaiClawShellApplication
  │
  ├── ChatCommands (@ShellComponent)
  │     └── ObjectProvider<AgentRuntime> (optional — graceful if no LLM)
  │           └── SessionManager
  │
  ├── StatusCommands (@ShellComponent)
  │     └── ObjectProvider<AgentRuntime>, ToolRegistry, PluginRegistry, ...
  │
  └── OnboardCommands (@ShellComponent)
        └── OnboardWizardOrchestrator
              ├── List<WizardStep> (ordered steps)
              ├── OnboardResult (accumulates inputs)
              ├── EnvFileWriter (writes .env)
              ├── YamlConfigWriter (writes application-local.yml)
              └── ConfigLocation (resolves paths)
```

### Shell Commands

| Command | Description |
|---|---|
| `chat <message>` | Send message to agent |
| `new-session` | Start fresh conversation |
| `sessions` | List all active sessions |
| `session-history` | Show messages in current session |
| `status` | System status (identity, tools, plugins, sessions) |
| `config` | Current configuration |
| `models` | Configured LLM providers |
| `tools` | List available tools |
| `skills` | List loaded skills |
| `plugins` | List loaded plugins |
| `onboard` | Interactive setup wizard |

### Onboarding Wizard

The wizard walks through:

1. **Flow mode**: Quickstart (sensible defaults) or Manual (full control)
2. **LLM provider**: Anthropic, OpenAI, Gemini, or Ollama + API key + model
3. **Security mode** (manual only): API key, JWT, or none + custom key
4. **Gateway settings** (manual only): Port, bind address, assistant name
5. **Channel setup**: Telegram, Slack, Discord bot tokens
6. **Skills**: Enable bundled skills, set workspace directory
7. **MCP servers**: Connect external MCP tool servers

Writes `application-local.yml` + `.env` to `~/.jaiclaw/` (or current directory).

### ObjectProvider Pattern

The shell uses `ObjectProvider<T>` for optional bean injection because `@ShellComponent` classes are component-scanned before auto-configuration runs:

```java
@ShellComponent
public class ChatCommands {
    private final ObjectProvider<AgentRuntime> runtimeProvider;

    @ShellMethod("chat")
    public String chat(String message) {
        AgentRuntime runtime = runtimeProvider.getIfAvailable();
        if (runtime == null) return "No LLM configured.";
        return runtime.run(message);
    }
}
```

### Running

```bash
# Local Java
./start.sh shell

# Docker (no Java needed)
./start.sh cli

# Maven
ANTHROPIC_API_KEY=sk-ant-... ./mvnw spring-boot:run -pl :jaiclaw-shell
```

### Docker Image

```bash
./mvnw package k8s:build -pl :jaiclaw-shell -am -Pk8s -DskipTests
```

---

## jaiclaw-cron-manager-app

**Purpose**: Standalone Spring Boot application (thin launcher) for the cron manager extension. Provides Spring Shell commands and standalone MCP hosting. All business logic lives in the `jaiclaw-cron-manager` extension module.

**ArtifactId**: `jaiclaw-cron-manager-app`
**Package**: `io.jaiclaw.cronmanager`
**Directory**: `apps/jaiclaw-cron-manager-app`
**Dependencies**: `jaiclaw-starter-cron` (POM starter), Spring Shell, Spring Boot Web

### Class Reference

| Class | Type | Description |
|---|---|---|
| CronManagerApplication | class | Spring Boot entry point (excludes Gateway and Channel auto-configs) |
| StandaloneCronManagerConfiguration | class | `@ConditionalOnMissingBean(McpServerRegistry.class)` -- provides MCP hosting beans when running standalone |
| CronJobCommands | class | Spring Shell commands for cron management |

### Class Relationships

```
CronManagerApplication
  │
  ├── (auto-discovered from jaiclaw-cron-manager extension)
  │     ├── CronManagerAutoConfiguration (property-gated)
  │     └── H2PersistenceAutoConfiguration
  │
  ├── StandaloneCronManagerConfiguration
  │     @ConditionalOnMissingBean(McpServerRegistry.class)
  │     ├── McpServerRegistry (collects McpToolProvider beans)
  │     └── McpController (REST /mcp/* endpoints)
  │
  └── CronJobCommands (@ShellComponent)
        └── CronJobManagerService
```

The `StandaloneCronManagerConfiguration` provides `McpServerRegistry` and `McpController` only when the gateway is not present. When embedded in the gateway, `JaiClawGatewayAutoConfiguration` already provides these beans, so `@ConditionalOnMissingBean` backs off.

### Shell Commands

| Command | Description |
|---|---|
| `cron list` | List all jobs |
| `cron create` | Create a new job |
| `cron run` | Manually trigger a job |
| `cron pause` | Pause a job |
| `cron resume` | Resume a job |
| `cron history` | View execution history |

### Running

```bash
./mvnw spring-boot:run -pl :jaiclaw-cron-manager-app
```

Excludes `JaiClawGatewayAutoConfiguration` and `JaiClawChannelAutoConfiguration` -- runs only the cron scheduling and execution engine with MCP hosting and shell commands.
