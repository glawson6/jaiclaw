# JClaw Architecture

## Overview

JClaw is a Java 21 / Spring Boot 3.5 / Spring AI personal AI assistant framework. It's an embeddable library with a gateway for multi-channel messaging (Telegram, Slack, Discord, etc.), a plugin system, tool execution, skills, and memory.

---

## Module Dependency Graph

```
jclaw-core  (pure Java — NO Spring dependency)
  |
  +---> jclaw-channel-api  (ChannelAdapter SPI, ChannelMessage — NO Spring)
  |       |
  |       +---> jclaw-channel-telegram
  |       +---> jclaw-channel-slack
  |       +---> jclaw-channel-discord
  |
  +---> jclaw-tools  (ToolRegistry, built-in tools, SpringAiToolBridge)
  |       |
  |       +---> jclaw-agent  (AgentRuntime, SessionManager, SystemPromptBuilder)
  |
  +---> jclaw-skills  (SkillLoader, SkillMarkdownParser, SkillPromptBuilder)
  +---> jclaw-plugin-sdk  (JClawPlugin SPI, PluginApi, HookRunner, PluginDiscovery)
  +---> jclaw-memory  (MemorySearchManager SPI, InMemorySearchManager)
  +---> jclaw-config  (@ConfigurationProperties records)
          |
          +---> jclaw-spring-boot-starter  (auto-configuration wiring)
          |       |
          |       +---> jclaw-gateway-app  (standalone gateway server)
          |       +---> jclaw-shell  (Spring Shell CLI)
          |
```

---

## Process Architecture

### Single-Process Mode (dev / Spring Shell)

One JVM runs everything. The Spring Shell CLI is the user interface.

```
┌──────────────────────────────────────────────────────┐
│                  JVM (Spring Boot)                    │
│                                                      │
│  ┌────────────┐  ┌──────────┐  ┌─────────────────┐  │
│  │ Spring     │  │ Agent    │  │ Tool Registry   │  │
│  │ Shell CLI  │→ │ Runtime  │→ │ + Spring AI     │  │
│  │            │  │          │  │   Tool Bridge   │  │
│  └────────────┘  └─────┬────┘  └────────┬────────┘  │
│                        │                │            │
│                        ▼                ▼            │
│                  ┌──────────┐    ┌────────────┐      │
│                  │ Session  │    │ Spring AI  │      │
│                  │ Manager  │    │ ChatClient │      │
│                  │(in-mem)  │    └──────┬─────┘      │
│                  └──────────┘           │            │
└─────────────────────────────────────────┼────────────┘
                                          │
                              ┌───────────┼───────────┐
                              ▼           ▼           ▼
                        ┌──────────┐ ┌────────┐ ┌──────────┐
                        │  OpenAI  │ │Anthropic│ │  Ollama  │
                        └──────────┘ └────────┘ └──────────┘
```

### Multi-Process Mode (production / gateway)

Two deployments: **gateway** (handles all channel I/O) and **app** (handles AI/tool execution). Both are stateless and horizontally scalable. Redis provides shared session state.

```
                    EXTERNAL CHANNELS
    ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐
    │ Telegram │ │  Slack   │ │ Discord  │ │  Web UI  │
    │ webhook  │ │  events  │ │ gateway  │ │    WS    │
    └────┬─────┘ └────┬─────┘ └────┬─────┘ └────┬─────┘
         │            │            │             │
         ▼            ▼            ▼             ▼
    ┌────────────────────────────────────────────────────┐
    │              JCLAW GATEWAY (Deployment)            │
    │              Replicas: 2+, stateless               │
    │                                                    │
    │  ┌──────────────────────────────────────────────┐  │
    │  │          Channel Adapter Layer                │  │
    │  │                                              │  │
    │  │  TelegramAdapter  SlackAdapter  DiscordAdapter│  │
    │  │                                              │  │
    │  │  Each adapter:                               │  │
    │  │  - Receives platform-native inbound message  │  │
    │  │  - Normalizes to ChannelMessage              │  │
    │  │  - Sends ChannelMessage outbound via API     │  │
    │  └──────────────────┬───────────────────────────┘  │
    │                     │                              │
    │  ┌──────────────────▼───────────────────────────┐  │
    │  │           Session Router                      │  │
    │  │                                              │  │
    │  │  session key = {agentId}:{channel}:{acct}:{peer}│
    │  │  Maps each conversation to an agent session  │  │
    │  └──────────────────┬───────────────────────────┘  │
    │                     │                              │
    │  ┌──────────────────▼───────────────────────────┐  │
    │  │     REST + WebSocket Control Plane            │  │
    │  │                                              │  │
    │  │  POST /api/chat        - sync message        │  │
    │  │  WS   /ws/session/{id} - streaming           │  │
    │  │  POST /webhook/{channel} - inbound webhook   │  │
    │  └──────────────────┬───────────────────────────┘  │
    └─────────────────────┼──────────────────────────────┘
                          │
                          ▼
    ┌─────────────────────────────────────────────────────┐
    │              JCLAW APP (Deployment)                  │
    │              Replicas: 2+, stateless                │
    │                                                     │
    │  ┌───────────────────────────────────────────────┐  │
    │  │              Agent Runtime                     │  │
    │  │  SessionManager + SystemPromptBuilder          │  │
    │  │  AgentRuntime (orchestrates LLM + tools)      │  │
    │  └───────────────────┬───────────────────────────┘  │
    │                      │                              │
    │  ┌──────────┐ ┌──────┴─────┐ ┌──────────┐ ┌──────┐│
    │  │  Tool    │ │  Skill     │ │  Plugin  │ │Memory││
    │  │ Registry │ │  Loader    │ │ Registry │ │Search││
    │  └────┬─────┘ └────────────┘ └──────────┘ └──────┘│
    │       │                                            │
    │  ┌────▼──────────────────────────────────────────┐ │
    │  │        Spring AI ChatClient + Tool Bridge     │ │
    │  └───────────────────┬───────────────────────────┘ │
    └──────────────────────┼─────────────────────────────┘
                           │
               ┌───────────┼───────────┐
               ▼           ▼           ▼
         ┌──────────┐ ┌────────┐ ┌──────────┐
         │  OpenAI  │ │Anthropic│ │  Ollama  │
         └──────────┘ └────────┘ └──────────┘
```

---

## Channel Adapter Architecture

Each messaging platform is integrated via a **ChannelAdapter** — a simple interface in `jclaw-channel-api`:

```java
public interface ChannelAdapter {

    String channelId();              // "telegram", "slack", "discord"

    void sendMessage(ChannelMessage message);

    default boolean supportsStreaming() { return false; }
}
```

Adapters are discovered via Spring component scanning and registered in a `ChannelRegistry`. The gateway routes inbound messages to the correct agent session and dispatches outbound responses back through the originating adapter.

### Per-Channel Details

| Channel   | Inbound                                          | Outbound                 | Auth           | Module                    |
|-----------|--------------------------------------------------|--------------------------|----------------|---------------------------|
| Telegram  | **Polling** (local) or Webhook (prod)            | Bot API `sendMessage`    | Bot token      | `jclaw-channel-telegram`  |
| Slack     | **Socket Mode** (local) or Events API (prod)     | `chat.postMessage`       | Bot + app token| `jclaw-channel-slack`     |
| Discord   | **Gateway WebSocket** (local) or Webhook (prod)  | REST `channels/{id}/msg` | Bot token      | `jclaw-channel-discord`   |
| Web UI    | WebSocket `/ws/session/{id}`                     | WebSocket                | JWT / session  | `jclaw-gateway` (built-in)|
| REST API  | `POST /api/chat`                                 | JSON response            | None (dev)     | `jclaw-gateway` (built-in)|

**Dual-mode adapters**: All three messaging adapters support a local-dev mode that requires no public endpoint:
- **Telegram**: `webhookUrl` blank → long polling via `getUpdates`
- **Slack**: `appToken` set → Socket Mode via WebSocket to `apps.connections.open`
- **Discord**: `useGateway` true → Gateway WebSocket with heartbeat + IDENTIFY

### Session Key Convention

Each conversation is identified by a composite session key:

```
{agentId}:{channel}:{accountId}:{peerId}

Examples:
  default:telegram:bot123:user456
  default:slack:workspace1:C04ABCDEF
  default:discord:guild789:user012
  default:shell:local:user
```

This ensures session isolation per user per channel per agent.

---

## Message Flow

### Inbound (e.g., Telegram user sends "hello")

```
1. Telegram sends POST /webhook/telegram with Update JSON
2. GatewayController receives request, delegates to TelegramAdapter
3. TelegramAdapter normalizes Update → ChannelMessage
4. Session Router computes key: "default:telegram:bot123:user456"
5. SessionManager finds or creates session for that key
6. AgentRuntime.run(channelMessage.content(), runtimeContext)
7. SystemPromptBuilder builds prompt (identity + skills + tools + context)
8. Spring AI ChatClient sends prompt to LLM provider
9. LLM may invoke tools via ToolCallback → SpringAiToolBridge → ToolRegistry
10. LLM returns final response text
11. AgentRuntime wraps response as AssistantMessage, appends to session
12. Gateway receives AssistantMessage, routes to TelegramAdapter
13. TelegramAdapter calls Telegram Bot API sendMessage
14. User sees response in Telegram
```

### WebSocket Streaming (Web UI)

```
1. Client connects to WS /ws/session/{sessionKey}
2. Client sends JSON: {"type": "message", "content": "hello"}
3. Gateway resolves session, calls AgentRuntime
4. AgentRuntime streams tokens via Spring AI ChatClient streaming API
5. Gateway forwards each token chunk over WebSocket
6. Client renders streaming response in real time
```

---

## Kubernetes Deployment View

Following the taptech-ai-agent-parent patterns (JKube, shared Helm chart, ngrok ingress):

```
┌────────────────────────── k8s cluster ──────────────────────────┐
│                                                                 │
│  ┌──────────────────────┐     ┌──────────────────────┐         │
│  │ jclaw-gateway        │     │ jclaw-app             │         │
│  │ (Deployment)         │     │ (Deployment)          │         │
│  │                      │     │                       │         │
│  │ - webhook receivers  │────▶│ - agent runtime       │         │
│  │ - WS control plane   │     │ - tools + skills      │         │
│  │ - channel adapters   │     │ - plugins + memory    │         │
│  │ - session routing    │     │ - Spring AI clients   │         │
│  │                      │     │                       │         │
│  │ Ports: 8080          │     │ Ports: 8081           │         │
│  │ Replicas: 2+         │     │ Replicas: 2+          │         │
│  └──────────┬───────────┘     └───────────┬───────────┘         │
│             │                             │                     │
│  ┌──────────▼───────────┐                 │                     │
│  │ ngrok Ingress        │                 │                     │
│  │ jclaw.taptech.net    │                 │                     │
│  │ (webhooks + WS)      │                 │                     │
│  └──────────────────────┘                 │                     │
│                                           │                     │
│  ┌────────────────────────────────────────┼───────────────────┐ │
│  │ ConfigMap / Secrets                    │                   │ │
│  │ - OPENAI_API_KEY                       │                   │ │
│  │ - ANTHROPIC_API_KEY                    │                   │ │
│  │ - TELEGRAM_BOT_TOKEN                   │                   │ │
│  │ - SLACK_BOT_TOKEN + SLACK_SIGNING_SECRET                   │ │
│  │ - DISCORD_BOT_TOKEN                    │                   │ │
│  └────────────────────────────────────────┼───────────────────┘ │
└───────────────────────────────────────────┼─────────────────────┘
                                            │
                          ┌─────────────────┼─────────────────┐
                          ▼                 ▼                 ▼
                  ┌──────────────┐  ┌────────────┐  ┌──────────────┐
                  │    Redis     │  │   Ollama   │  │    Kafka     │
                  │  (sessions)  │  │ (local LLM)│  │  (events)    │
                  │              │  │            │  │  (optional)  │
                  │  bare-metal  │  │ bare-metal │  │  bare-metal  │
                  │ 10.92.7.164  │  │ 10.92.7.164│  │ 10.92.7.164 │
                  └──────────────┘  └────────────┘  └──────────────┘
```

### Docker Image Build

Using Eclipse JKube (`kubernetes-maven-plugin`) with `eclipse-temurin:21-jre` base image, activated via Maven profile:

```bash
# Build Docker image
./mvnw package k8s:build -pl jclaw-gateway-app -Pk8s -DskipTests

# Push to registry
./mvnw k8s:push -pl jclaw-gateway-app -Pk8s

# Deploy to k8s
./mvnw k8s:resource k8s:apply -pl jclaw-gateway-app -Pk8s
```

### Helm Chart

Shared Helm chart at `helm/spring-boot-app/` with `workloadType` toggle:

```yaml
# values-jclaw-gateway-app.yaml
workloadType: deployment
replicaCount: 2
image:
  repository: registry.taptech.net/jclaw-gateway-app
  tag: latest
service:
  port: 8080
ingress:
  enabled: true
  host: jclaw.taptech.net
env:
  - name: SPRING_PROFILES_ACTIVE
    value: "gateway"
```

---

## What Exists vs. What's Needed

| Component                        | Status       | Module                       |
|----------------------------------|--------------|------------------------------|
| Core domain model                | Done         | `jclaw-core`                 |
| Agent runtime + sessions         | Done         | `jclaw-agent`                |
| Tool system + 5 built-in tools   | Done         | `jclaw-tools`                |
| Skills system                    | Done         | `jclaw-skills`               |
| Plugin system + hooks            | Done         | `jclaw-plugin-sdk`           |
| Memory search (in-memory)        | Done         | `jclaw-memory`               |
| Auto-configuration               | Done         | `jclaw-spring-boot-starter`  |
| Spring Shell CLI                 | Done         | `jclaw-shell`                |
| Interactive onboarding wizard    | Done         | `jclaw-shell`                |
| Channel adapter SPI              | Done         | `jclaw-channel-api`          |
| Gateway (REST + WS + webhooks)   | Done         | `jclaw-gateway`              |
| Telegram adapter (poll + webhook)| Done         | `jclaw-channel-telegram`     |
| Slack adapter                    | Done         | `jclaw-channel-slack`        |
| Discord adapter                  | Done         | `jclaw-channel-discord`      |
| Standalone gateway app           | Done         | `jclaw-gateway-app`          |
| Docker image build (JKube)       | Done         | `-Pk8s` profile in POMs      |
| **Helm chart**                   | **Needed**   | `helm/spring-boot-app/`      |
| **Redis session store**          | **Planned**  | `jclaw-agent` (swap in-mem)  |
| **Kafka event bus**              | **Optional** | cross-cutting                |

---

## Configuration

### application.yml (gateway profile)

```yaml
jclaw:
  identity:
    name: "JClaw"
  agent:
    default-agent: default
  channels:
    telegram:
      enabled: true
      bot-token: ${TELEGRAM_BOT_TOKEN}
      webhook-url: https://jclaw.taptech.net/webhook/telegram
    slack:
      enabled: true
      bot-token: ${SLACK_BOT_TOKEN}
      signing-secret: ${SLACK_SIGNING_SECRET}
    discord:
      enabled: true
      bot-token: ${DISCORD_BOT_TOKEN}

spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY:}
    anthropic:
      api-key: ${ANTHROPIC_API_KEY:}
    ollama:
      base-url: http://ollama.infra:11434
```
