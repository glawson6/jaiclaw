# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Test Commands

```bash
# Set JAVA_HOME (required — Maven wrapper needs it)
export JAVA_HOME=/Users/tap/.sdkman/candidates/java/21.0.9-oracle

# Compile all modules
./mvnw compile

# Run all tests (use -o for offline if Nexus is unreachable)
./mvnw test -o

# Run tests for a single module
./mvnw test -pl jclaw-tools -o

# Install to local repo (needed before -pl without -am works offline)
./mvnw install -DskipTests

# Run a single Spock spec
./mvnw test -pl jclaw-tools -Dtest=ToolRegistrySpec -o
```

**Note:** A Nexus repo at 10.92.7.128:8081 is configured in Maven settings. When unreachable, it causes long timeouts on dependency resolution. Use `-o` (offline) flag after initial dependency download to avoid this. When adding new external dependencies, run without `-o` first to download them.

## Architecture

JClaw is a Java 21 / Spring Boot 3.5 / Spring AI / Embabel port of OpenClaw, designed as an embeddable library with a gateway for multi-channel messaging and a Spring Shell CLI.

See `docs/ARCHITECTURE.md` for full diagrams and `docs/OPERATIONS.md` for running/deploying.

### Module Dependency Graph

```
jclaw-core  (pure Java — NO Spring dependency)
  ↑
jclaw-channel-api (ChannelAdapter SPI, ChannelMessage, ChannelRegistry)
  ↑
jclaw-tools (ToolRegistry, built-in tools, SpringAiToolBridge)
  ↑
jclaw-agent (AgentRuntime, SessionManager, SystemPromptBuilder, JClawAgent)
jclaw-skills (SkillLoader, SkillMarkdownParser, SkillPromptBuilder)
jclaw-plugin-sdk (JClawPlugin SPI, PluginApi, HookRunner, PluginDiscovery)
jclaw-memory (InMemorySearchManager, MemorySearchManager SPI)
jclaw-config (@ConfigurationProperties records)
  ↑
jclaw-gateway (GatewayService, GatewayController, WebhookDispatcher, WebSocket)
jclaw-channel-telegram (TelegramAdapter — Bot API webhook + sendMessage)
jclaw-channel-slack (SlackAdapter — Events API + chat.postMessage)
jclaw-channel-discord (DiscordAdapter — Interactions webhook + REST API)
  ↑
jclaw-spring-boot-starter (JClawAutoConfiguration — wires everything)
  ↑
jclaw-gateway-app (standalone gateway server — runnable Spring Boot app)
jclaw-shell (Spring Shell CLI — runnable Spring Boot app)
```

### Key Design Decisions

- **jclaw-core has zero Spring dependency** — pure Java records, sealed interfaces, enums
- **Java records everywhere** for value types (immutable, pattern-matchable)
- **Sealed interfaces** for `Message` (User/Assistant/System/ToolResult), `ToolResult` (Success/Error), `DeliveryResult` (Success/Failure)
- **Dual tool bridge**: JClaw `ToolCallback` (SPI) ↔ Spring AI `ToolCallback` via `SpringAiToolBridge`
- **ToolProfile filtering**: `isAvailableIn(profile)` — FULL profile grants access to all tools; other profiles only see tools tagged with that profile
- **Virtual threads** for void hook execution in `HookRunner`
- **Plugin discovery** merges Spring component scanning + `ServiceLoader` + explicit registration
- **Channel adapter pattern**: `ChannelAdapter` SPI with webhook-based inbound and REST-based outbound per platform
- **Session key convention**: `{agentId}:{channel}:{accountId}:{peerId}` for session isolation

### Version Alignment (Embabel 0.3.4 compatibility)

- Spring Boot 3.5.6, Spring AI 1.1.1, Spring Shell 3.4.0, Embabel Agent 0.3.4
- Spring AI artifact names use the new scheme: `spring-ai-model`, `spring-ai-starter-model-openai`, etc.

## Testing

- All tests use **Spock Framework** (Groovy) — specs are in `src/test/groovy/`
- Test classes must end with `Spec` (configured in surefire includes)
- Modules need `gmavenplus-plugin` + `spock-core` + `groovy` dependencies for Spock support
- Modules mocking concrete classes need `byte-buddy` + `objenesis` test dependencies
- 17 modules, ~165 tests across 9 modules: tools (36), agent (21), skills (21), plugin-sdk (18), memory (11), channel-api (17), gateway (11), telegram (10), slack (11), discord (9)
- Docker images: `jclaw-gateway-app` and `jclaw-shell` — built via JKube with `-Pk8s` profile
