# hello-world

> The smallest possible JaiClaw program. ~30 lines of Java, one custom
> tool, one `application.yml`. Use this as a starting point or as the
> reference shape when you read other examples.

## Problem

You want to understand how a JaiClaw application is wired without
wading through a real-world example's domain logic. Every other
example under `jaiclaw-examples/` is production-shaped — custom tools
with real business semantics, multi-service orchestration, channel
adapters. None of them shows the **smallest viable JaiClaw**.

## Solution

This module is intentionally the simplest end-to-end shape:

- **One Spring Boot main class** (`HelloWorldApplication`).
- **One `@Configuration` class** (`HelloWorldConfig`) that exposes a
  single `ToolCallback` bean — the `echo` tool, which takes a `text`
  parameter and returns it.
- **One `application.yml`** that points at Anthropic and disables
  every default that would obscure the cost of a single LLM call
  (most importantly, `jaiclaw.skills.allow-bundled: []`).

Boot it, hit `POST /api/chat`, and an LLM call returns the echoed
string via the custom tool.

## Architecture

```
              ┌─────────────────────────────────────────┐
              │ HelloWorldApplication (@SpringBootApp)  │
              └────┬────────────────────────────────────┘
                   │ component scans
                   ▼
              ┌─────────────────────────────────────────┐
              │ HelloWorldConfig                        │
              │   @Bean echoTool : ToolCallback         │
              └────┬────────────────────────────────────┘
                   │ registered via SpringAiToolBridge
                   ▼
   HTTP POST /api/chat
   ──────────────────► JaiClaw GatewayService
                              │
                              ▼
                         AgentRuntime
                              │ Spring AI ChatClient call
                              ▼
                         Anthropic
                              │ tool_use: echo({text:"hi"})
                              ▼
                         echoTool.execute()
                              │ returns "hi"
                              ▼
                         Assistant message → HTTP response
```

Components:

| Class | Role |
|---|---|
| `HelloWorldApplication` | Spring Boot bootstrap |
| `HelloWorldConfig` | `@Bean` factory for the `echo` tool |
| `application.yml` | Minimal config: provider, model, agent identity, security mode |

## Design

A few decisions that are worth calling out:

- **`@Configuration` not `@Component` on tool wiring.** JaiClaw's
  convention is explicit, factory-style bean wiring (see `CLAUDE.md`).
  Tool classes are plain POJOs; the `@Bean` method does the
  registration. This makes the dependency graph visible in one place
  and avoids component-scan ordering surprises in larger apps.
- **`jaiclaw.skills.allow-bundled: []`.** The framework's default is
  `["*"]` — every bundled skill (~62 of them, ~26K input tokens) gets
  shipped to the LLM with every request. Fine for the general-purpose
  shell, ruinous for a hello-world demo. Every example app must set
  this explicitly. See `docs/user/OPERATIONS.md` § Skills Configuration.
- **`mode: none` for security.** Localhost only. Production
  deployments use `api-key` or `jwt`. The audit's mode=none chain
  still applies the standard security response headers.
- **`ToolDefinition.builder()`** is used rather than the 5-arg
  constructor — the builder is the recommended API and matches the
  style of `PluginDefinition` and `SkillMetadata`.

## Build & Run

### Prerequisites

- Java 21 (set `JAVA_HOME` before building).
- An Anthropic API key. (Swap to another provider by editing
  `application.yml`'s `spring.ai.model.chat` and the matching
  provider block.)

### Build

```bash
export JAVA_HOME=/path/to/jdk-21
./mvnw package -pl :jaiclaw-example-hello-world -am -DskipTests
```

The first build downloads the framework + Spring AI + Anthropic
client; subsequent builds are fast.

### Run

```bash
ANTHROPIC_API_KEY=sk-ant-... \
  ./mvnw spring-boot:run -pl :jaiclaw-example-hello-world
```

You should see a JaiClaw banner and then:

```
Tomcat started on port 8080
Started HelloWorldApplication in 4.123 seconds
```

### Verify

```bash
curl -X POST http://localhost:8080/api/chat \
  -H 'Content-Type: application/json' \
  -d '{
    "sessionId":"hello",
    "message":"Use the echo tool to echo back: hi there!"
  }'
```

You should get an assistant message that includes `hi there!` — the
LLM picked the `echo` tool, JaiClaw executed it, and the result came
back through the gateway.

### Where to go next

- Edit `HelloWorldConfig.echoTool()` to take an extra parameter and
  see how the JSON Schema propagates to the LLM.
- Compare with `jaiclaw-examples/helpdesk-bot/` for a more realistic
  shape (security mode, two tools, multiple providers).
- Read `docs/user/AUTHORING-TOOLS.md` (added by the same remediation
  PR series as this example) for the full tool-authoring walkthrough.
