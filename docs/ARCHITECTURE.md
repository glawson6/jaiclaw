# JaiClaw Architecture

## Overview

JaiClaw is a Java 21 / Spring Boot 3.5 / Spring AI personal AI assistant framework. It's an embeddable library with a gateway for multi-channel messaging (Telegram, Slack, Discord, Email, SMS), a plugin system, tool execution, skills, memory, document processing, audit logging, and MCP server hosting.

---

## High-Level Architecture

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                          RUNNABLE APPS  (Layer 7)                            │
│                                                                              │
│  ┌───────────────────┐  ┌──────────────────┐  ┌──────────────────────────┐  │
│  │ jaiclaw-gateway-app │  │    jaiclaw-shell   │  │     jaiclaw-examples       │  │
│  │ REST + WS + Chans │  │ Spring Shell CLI │  │  10 standalone apps      │  │
│  └────────┬──────────┘  └────────┬─────────┘  └────────────┬─────────────┘  │
├───────────┼──────────────────────┼──────────────────────────┼────────────────┤
│           │            STARTERS  (Layer 6)                  │                │
│                                                                              │
│  ┌──────────────────┐  ┌──────────────────┐  ┌────────────────────────┐     │
│  │ starter-gateway  │  │  starter-shell   │  │  starter-anthropic     │     │
│  │ starter-embabel  │  │ starter-personal │  │  starter-openai        │     │
│  │                  │  │   -assistant     │  │  starter-gemini        │     │
│  │                  │  │                  │  │  starter-ollama        │     │
│  └────────┬─────────┘  └────────┬─────────┘  │  starter-k8s-monitor  │     │
│           │                     │             └────────────┬───────────┘     │
├───────────┼─────────────────────┼──────────────────────────┼────────────────┤
│           │          AUTO-CONFIG  (Layer 5)                 │                │
│                                                                              │
│  ┌────────┴─────────────────────┴──────────────────────────┴─────────────┐  │
│  │                   jaiclaw-spring-boot-starter                           │  │
│  │                                                                       │  │
│  │  Phase 1: JaiClawAutoConfiguration          (core beans)                │  │
│  │  Phase 2: JaiClawGatewayAutoConfiguration   (gateway + MCP)             │  │
│  │  Phase 3: JaiClawChannelAutoConfiguration   (channel adapters)          │  │
│  └────────┬──────────────────────────────────────────────────────────────┘  │
├───────────┼─────────────────────────────────────────────────────────────────┤
│           │       GATEWAY + CHANNELS  (Layer 4)                             │
│                                                                              │
│  ┌────────┴──────────────┐  ┌────────────────────────────────────────────┐  │
│  │    jaiclaw-gateway      │  │            Channel Adapters                │  │
│  │                       │  │                                            │  │
│  │  REST API + WebSocket │  │  ┌──────────┐ ┌────────┐ ┌─────────┐     │  │
│  │  MCP hosting          │  │  │ Telegram │ │ Slack  │ │ Discord │     │  │
│  │  WebhookDispatcher    │  │  └──────────┘ └────────┘ └─────────┘     │  │
│  │  Tenant resolution    │  │  ┌──────────┐ ┌────────┐                 │  │
│  │  Observability        │  │  │  Email   │ │  SMS   │                 │  │
│  └────────┬──────────────┘  │  └──────────┘ └────────┘                 │  │
│           │                 └──────────────────────┬─────────────────────┘  │
├───────────┼────────────────────────────────────────┼────────────────────────┤
│           │       FEATURE MODULES  (Layer 3)       │                        │
│                                                                              │
│  ┌────────┴──────────────────────────────┐  ┌──────┴──────────────────────┐ │
│  │          jaiclaw-agent                  │  │      jaiclaw-security         │ │
│  │  AgentRuntime, SessionManager         │  │  JWT auth, TenantResolver   │ │
│  │  SystemPromptBuilder, JaiClawAgent      │  └─────────────────────────────┘ │
│  └────────┬──────────────────────────────┘                                  │
│           │                                                                  │
│  ┌────────┴────────┐ ┌──────────────┐ ┌──────────────┐ ┌───────────────┐   │
│  │  jaiclaw-skills   │ │jaiclaw-plugin  │ │ jaiclaw-memory │ │ jaiclaw-config  │   │
│  │  SkillLoader    │ │  -sdk        │ │ SearchManager│ │ @ConfigProps  │   │
│  │  versioning     │ │ JaiClawPlugin  │ │ VectorStore  │ │               │   │
│  └─────────────────┘ │ PluginApi    │ └──────────────┘ └───────────────┘   │
│                      └──────────────┘                                       │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐ ┌──────────────────┐   │
│  │  jaiclaw-docs  │ │ jaiclaw-media  │ │ jaiclaw-audit  │ │jaiclaw-compaction │   │
│  │  PDF / HTML  │ │ vision/audio │ │ AuditLogger  │ │ context window  │   │
│  │  parsing     │ │ analysis     │ │              │ │ summarization   │   │
│  └──────────────┘ └──────────────┘ └──────────────┘ └──────────────────┘   │
│                                                                              │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐ ┌──────────────────┐   │
│  │jaiclaw-browser │ │ jaiclaw-cron   │ │ jaiclaw-voice  │ │ jaiclaw-identity  │   │
│  │ Playwright   │ │ scheduler    │ │ TTS / STT    │ │ cross-channel   │   │
│  │ automation   │ │ virtual thr  │ │              │ │ user linking    │   │
│  └──────────────┘ └──────────────┘ └──────────────┘ └──────────────────┘   │
│                                                                              │
│  ┌──────────────┐ ┌──────────────┐ ┌────────────────┐                       │
│  │ jaiclaw-canvas │ │  jaiclaw-code  │ │jaiclaw-messaging │                       │
│  │ A2UI / HTML  │ │  file edit   │ │ MCP channel    │                       │
│  │ artifacts    │ │  code tools  │ │ messaging tools│                       │
│  └──────────────┘ └──────────────┘ └────────────────┘                       │
├──────────────────────────────────────────────────────────────────────────────┤
│                      TOOL LAYER  (Layer 2)                                   │
│                                                                              │
│  ┌──────────────────────────────────────┐  ┌─────────────────────────────┐  │
│  │           jaiclaw-tools               │  │     jaiclaw-tools-k8s        │  │
│  │  ToolRegistry, built-in tools       │  │  Fabric8 Kubernetes tools  │  │
│  │  SpringAiToolBridge, EmbabelBridge  │  └─────────────────────────────┘  │
│  └────────┬─────────────────────────────┘                                   │
├───────────┼──────────────────────────────────────────────────────────────────┤
│           │       CHANNEL SPI  (Layer 1)                                    │
│                                                                              │
│  ┌────────┴──────────────────────────────────────────────────────────────┐  │
│  │                      jaiclaw-channel-api                                │  │
│  │  ChannelAdapter SPI, ChannelMessage, attachments, ChannelRegistry     │  │
│  └────────┬──────────────────────────────────────────────────────────────┘  │
├───────────┼──────────────────────────────────────────────────────────────────┤
│           │       CORE  (Layer 0) — Pure Java, no Spring                    │
│                                                                              │
│  ┌────────┴──────────────────────────────────────────────────────────────┐  │
│  │                        jaiclaw-core                                     │  │
│  │  Records: Message, Session, CronJob, ToolResult, DeliveryResult       │  │
│  │  Sealed interfaces: Message, ToolResult, DeliveryResult               │  │
│  │  Enums: ToolProfile, PluginKind, HookName                             │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────────────────────┘
```

---

## Module Dependency Graph

```
jaiclaw-core  (pure Java — NO Spring dependency)
  |
  +---> jaiclaw-channel-api  (ChannelAdapter SPI, ChannelMessage, attachments)
  |       |
  |       +---> jaiclaw-channel-telegram  (Bot API polling + webhook + file downloads)
  |       +---> jaiclaw-channel-slack     (Socket Mode + Events API)
  |       +---> jaiclaw-channel-discord   (Gateway WebSocket + Interactions)
  |       +---> jaiclaw-channel-email     (IMAP polling + SMTP + MIME attachments)
  |       +---> jaiclaw-channel-sms       (Twilio REST API + webhook + MMS)
  |
  +---> jaiclaw-tools  (ToolRegistry, built-in tools, SpringAiToolBridge, Embabel bridge)
  |       |
  |       +---> jaiclaw-tools-k8s  (Fabric8 Kubernetes tools)
  |       +---> jaiclaw-agent  (AgentRuntime, SessionManager, SystemPromptBuilder)
  |
  +---> jaiclaw-skills  (SkillLoader, versioning, TenantSkillRegistry)
  +---> jaiclaw-plugin-sdk  (JaiClawPlugin SPI, PluginApi, HookRunner, PluginDiscovery)
  +---> jaiclaw-memory  (MemorySearchManager SPI, InMemorySearchManager, VectorStore)
  +---> jaiclaw-security  (JWT auth, TenantResolver, SecurityContext)
  +---> jaiclaw-documents  (PDF/HTML/text parsing, chunking pipeline)
  +---> jaiclaw-media  (async media analysis SPI, CompositeMediaAnalyzer)
  +---> jaiclaw-audit  (AuditEvent, AuditLogger SPI, InMemoryAuditLogger)
  +---> jaiclaw-compaction  (context window compaction via summarization)
  +---> jaiclaw-browser  (Playwright-based browser automation)
  +---> jaiclaw-cron  (cron job scheduling, JSON persistence, virtual threads)
  +---> jaiclaw-voice  (TTS/STT SPI, OpenAI provider)
  +---> jaiclaw-identity  (cross-channel identity linking, JSON persistence)
  +---> jaiclaw-canvas  (A2UI artifact rendering, HTML file management)
  +---> jaiclaw-code  (file editing, code generation tools)
  +---> jaiclaw-messaging  (MCP server: channel messaging, sessions, agent-routed chat)
  +---> jaiclaw-config  (@ConfigurationProperties records)
          |
          +---> jaiclaw-gateway  (REST + WS + webhooks + MCP hosting + observability)
          |
          +---> jaiclaw-spring-boot-starter  (auto-configuration wiring)
          |       |
          |       +---> jaiclaw-gateway-app  (standalone gateway server)
          |       +---> jaiclaw-shell  (Spring Shell CLI)
          |
          +---> jaiclaw-examples  (10 standalone example applications)
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
                        │  OpenAI  │ │Anthropic│ │ Gemini │ │  Ollama  │
                        └──────────┘ └────────┘ └────────┘ └──────────┘
```

### Multi-Process Mode (production / gateway)

Two deployments: **gateway** (handles all channel I/O) and **app** (handles AI/tool execution). Both are stateless and horizontally scalable. Redis provides shared session state.

```
                           EXTERNAL CHANNELS
    ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐
    │ Telegram │ │  Slack   │ │ Discord  │ │  Email   │ │   SMS    │ │  Web UI  │
    │ webhook  │ │  events  │ │ gateway  │ │   IMAP   │ │  Twilio  │ │    WS    │
    └────┬─────┘ └────┬─────┘ └────┬─────┘ └────┬─────┘ └────┬─────┘ └────┬─────┘
         │            │            │             │            │             │
         ▼            ▼            ▼             ▼            ▼             ▼
    ┌────────────────────────────────────────────────────┐
    │              JAICLAW GATEWAY (Deployment)            │
    │              Replicas: 2+, stateless               │
    │                                                    │
    │  ┌──────────────────────────────────────────────┐  │
    │  │          Channel Adapter Layer                │  │
    │  │                                              │  │
    │  │  Telegram  Slack  Discord  Email  SMS Adapters│  │
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
    │              JAICLAW APP (Deployment)                  │
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

Each messaging platform is integrated via a **ChannelAdapter** — a simple interface in `jaiclaw-channel-api`:

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
| Telegram  | **Polling** (local) or Webhook (prod)            | Bot API `sendMessage`    | Bot token      | `jaiclaw-channel-telegram`  |
| Slack     | **Socket Mode** (local) or Events API (prod)     | `chat.postMessage`       | Bot + app token| `jaiclaw-channel-slack`     |
| Discord   | **Gateway WebSocket** (local) or Webhook (prod)  | REST `channels/{id}/msg` | Bot token      | `jaiclaw-channel-discord`   |
| Email     | **IMAP polling** (configurable interval)         | SMTP `Transport.send`    | Username/pass  | `jaiclaw-channel-email`     |
| SMS       | **Twilio webhook** POST                          | Twilio Messages API      | Account SID    | `jaiclaw-channel-sms`       |
| Web UI    | WebSocket `/ws/session/{id}`                     | WebSocket                | JWT / session  | `jaiclaw-gateway` (built-in)|
| REST API  | `POST /api/chat`                                 | JSON response            | API key / JWT  | `jaiclaw-gateway` (built-in)|
| MCP       | `POST /mcp/{server}/tools/{tool}`                | JSON response            | API key / JWT  | `jaiclaw-gateway` (built-in)|
| MCP SSE   | `GET /mcp/{server}/sse` + `POST /jsonrpc`        | JSON-RPC 2.0 / SSE       | API key / JWT  | `jaiclaw-gateway` (built-in)|
| MCP stdio | stdin JSON-RPC (standalone JAR `--stdio`)         | stdout JSON-RPC          | Env vars       | `jaiclaw-messaging` (standalone)|

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
│  │ jaiclaw-gateway        │     │ jaiclaw-app             │         │
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
│  │ jaiclaw.taptech.net    │                 │                     │
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

Using Eclipse JKube (`kubernetes-maven-plugin`) with `eclipse-temurin:21-jre` base image, activated via Maven profile. Two modules produce images:

- **`jaiclaw-gateway-app`** — production HTTP server (REST + WebSocket + all channels)
- **`jaiclaw-shell`** — interactive CLI (headless/scripted use, Docker-based access)

```bash
# Build both images
./mvnw package k8s:build -pl jaiclaw-gateway-app,jaiclaw-shell -am -Pk8s -DskipTests

# Push to registry
./mvnw k8s:push -pl jaiclaw-gateway-app -Pk8s

# Deploy to k8s
./mvnw k8s:resource k8s:apply -pl jaiclaw-gateway-app -Pk8s
```

### Helm Chart

Shared Helm chart at `helm/spring-boot-app/` with `workloadType` toggle:

```yaml
# values-jaiclaw-gateway-app.yaml
workloadType: deployment
replicaCount: 2
image:
  repository: registry.taptech.net/jaiclaw-gateway-app
  tag: latest
service:
  port: 8080
ingress:
  enabled: true
  host: jaiclaw.taptech.net
env:
  - name: SPRING_PROFILES_ACTIVE
    value: "gateway"
  - name: JAICLAW_SECURITY_MODE
    value: "api-key"
```

---

## What Exists vs. What's Needed

| Component                        | Status       | Module                       |
|----------------------------------|--------------|------------------------------|
| Core domain model                | Done         | `jaiclaw-core`                 |
| Agent runtime + sessions         | Done         | `jaiclaw-agent`                |
| Tool system + built-in tools     | Done         | `jaiclaw-tools`                |
| Embabel orchestration bridge     | Done         | `jaiclaw-tools` (bridge/embabel) |
| Skills system + versioning       | Done         | `jaiclaw-skills`               |
| Tenant-aware skill registry      | Done         | `jaiclaw-skills`               |
| Plugin system + hooks            | Done         | `jaiclaw-plugin-sdk`           |
| Memory search (in-memory + vector)| Done        | `jaiclaw-memory`               |
| Multi-tenancy + JWT auth         | Done         | `jaiclaw-core` + `jaiclaw-security` |
| Document parsing + chunking      | Done         | `jaiclaw-documents`            |
| Media analysis SPI               | Done         | `jaiclaw-media`                |
| Audit logging SPI                | Done         | `jaiclaw-audit`                |
| Auto-configuration               | Done         | `jaiclaw-spring-boot-starter`  |
| Spring Shell CLI                 | Done         | `jaiclaw-shell`                |
| Interactive onboarding wizard    | Done         | `jaiclaw-shell`                |
| Channel adapter SPI + attachments| Done         | `jaiclaw-channel-api`          |
| Gateway (REST + WS + webhooks)   | Done         | `jaiclaw-gateway`              |
| MCP server hosting               | Done         | `jaiclaw-gateway` (mcp/)       |
| MCP SSE server transport         | Done         | `jaiclaw-gateway` (mcp/transport/server/) |
| MCP stdio bridge transport       | Done         | `jaiclaw-gateway` (mcp/transport/server/) |
| MCP channel messaging tools      | Done         | `jaiclaw-messaging`            |
| Observability (metrics + health) | Done         | `jaiclaw-gateway` (observability/) |
| Telegram adapter (poll + webhook)| Done         | `jaiclaw-channel-telegram`     |
| Slack adapter                    | Done         | `jaiclaw-channel-slack`        |
| Discord adapter                  | Done         | `jaiclaw-channel-discord`      |
| Email adapter (IMAP + SMTP)      | Done         | `jaiclaw-channel-email`        |
| SMS adapter (Twilio)             | Done         | `jaiclaw-channel-sms`          |
| Standalone gateway app           | Done         | `jaiclaw-gateway-app`          |
| Docker image build (JKube)       | Done         | `-Pk8s` profile in POMs      |
| HTTP proxy support               | Done         | `jaiclaw-core` + `jaiclaw-config` + starter |
| **Helm chart**                   | **Needed**   | `helm/spring-boot-app/`      |
| **Redis session store**          | **Planned**  | `jaiclaw-agent` (swap in-mem)  |
| **Kafka event bus**              | **Optional** | cross-cutting                |

---

## Auto-Configuration Bean Ordering

The starter splits auto-configuration across three `@AutoConfiguration` classes to guarantee correct `@ConditionalOnBean` evaluation order. Spring Boot evaluates conditions on nested `@Configuration` classes in the same pass as the enclosing auto-config, so `@ConditionalOnBean` only works reliably **across separate** auto-configuration classes linked by `@AutoConfigureAfter`.

### Phase 1 — Spring AI Provider Auto-Configs

These are provided by Spring AI starter dependencies. Only the **enabled** provider activates (controlled by `spring.ai.*.enabled` properties).

```
AnthropicChatAutoConfiguration        ─── creates ──→  ChatModel (Anthropic)
   or
OpenAiChatAutoConfiguration           ─── creates ──→  ChatModel (OpenAI)
   or
GoogleGenAiChatAutoConfiguration      ─── creates ──→  ChatModel (Gemini)
   or
OllamaChatAutoConfiguration           ─── creates ──→  ChatModel (Ollama)
          │
          ▼
ChatClientAutoConfiguration           ─── creates ──→  ChatClient.Builder
  @ConditionalOnClass(ChatClient)                       (requires ChatModel)
```

### Phase 2 — JaiClawAutoConfiguration

`@AutoConfigureAfter(ChatClientAutoConfiguration)` — core JaiClaw beans.

```
JaiClawAutoConfiguration
  │
  ├── proxyFactoryConfigurer    ProxyFactoryConfigurer (configures ProxyAwareHttpClientFactory)
  ├── proxyRestClientCustomizer RestClientCustomizer (proxy-aware JdkClientHttpRequestFactory)
  ├── toolRegistry            ToolRegistry (+ built-in tools)
  ├── sessionManager          SessionManager
  ├── skillLoader             SkillLoader (loads bundled skills from classpath)
  ├── pluginRegistry          PluginRegistry
  ├── vectorStoreSearchManager  VectorStoreSearchManager  @ConditionalOnBean(VectorStore)
  │     or
  ├── inMemorySearchManager   InMemorySearchManager       (fallback)
  ├── agentRuntime            AgentRuntime                @ConditionalOnBean(ChatClient.Builder)
  │                             (SessionManager, ChatClient.Builder, ToolRegistry, SkillLoader)
  ├── channelRegistry         ChannelRegistry             (auto-collects all ChannelAdapter beans)
  └── noOpOrchestrationPort   NoOpOrchestrationPort       @ConditionalOnMissingBean(AgentOrchestrationPort)
```

### Phase 3 — JaiClawGatewayAutoConfiguration

`@AutoConfigureAfter(JaiClawAutoConfiguration)` — gateway HTTP/WS layer. Entire class is gated on:
- `@ConditionalOnClass(GatewayService)` — `jaiclaw-gateway` must be on the classpath
- `@ConditionalOnBean(AgentRuntime)` — an LLM provider must be configured

```
JaiClawGatewayAutoConfiguration
  │
  ├── webhookDispatcher       WebhookDispatcher
  ├── jwtTenantResolver       JwtTenantResolver
  ├── botTokenTenantResolver  BotTokenTenantResolver
  ├── compositeTenantResolver CompositeTenantResolver     (aggregates all TenantResolvers)
  ├── loggingAttachmentRouter LoggingAttachmentRouter      @ConditionalOnMissingBean(AttachmentRouter)
  ├── gatewayService          GatewayService               (AgentRuntime, SessionManager, ChannelRegistry, ...)
  ├── gatewayLifecycle        GatewayLifecycle             (starts/stops channel adapters on app lifecycle)
  ├── gatewayController       GatewayController            @RestController — /api/chat, /api/health, /webhook/*
  ├── webSocketSessionHandler WebSocketSessionHandler      (WS /ws/session/{id})
  ├── mcpServerRegistry       McpServerRegistry            (collects McpToolProvider beans)
  ├── mcpController           McpController                @ConditionalOnBean(McpServerRegistry) — /mcp/*
  ├── mcpSseServerController  McpSseServerController       @ConditionalOnProperty(jaiclaw.mcp.sse-server.enabled) — /mcp/{server}/sse + /jsonrpc
  ├── gatewayMetrics          GatewayMetrics               (atomic request/error counters)
  └── gatewayHealthIndicator  GatewayHealthIndicator       (UP/DEGRADED based on channel adapter status)
```

### Phase 4 — JaiClawChannelAutoConfiguration

`@AutoConfigureAfter(JaiClawGatewayAutoConfiguration)` — channel adapters. Each adapter is in a nested `@Configuration` gated on `@ConditionalOnClass` (adapter JAR on classpath) and `@ConditionalOnBean(WebhookDispatcher)` (gateway must be active).

```
JaiClawChannelAutoConfiguration
  │
  ├── TelegramAutoConfiguration   ─── creates ──→  TelegramAdapter     @ConditionalOnClass + @ConditionalOnBean(WebhookDispatcher)
  ├── SlackAutoConfiguration      ─── creates ──→  SlackAdapter         @ConditionalOnClass + @ConditionalOnBean(WebhookDispatcher)
  ├── DiscordAutoConfiguration    ─── creates ──→  DiscordAdapter       @ConditionalOnClass + @ConditionalOnBean(WebhookDispatcher)
  ├── EmailAutoConfiguration      ─── creates ──→  EmailAdapter         @ConditionalOnClass (no WebhookDispatcher needed)
  ├── SmsAutoConfiguration        ─── creates ──→  SmsAdapter           @ConditionalOnClass (no WebhookDispatcher needed)
  └── AuditAutoConfiguration      ─── creates ──→  InMemoryAuditLogger  @ConditionalOnClass(AuditLogger)
```

### Extension Auto-Configurations

Extensions provide their own `@AutoConfiguration` classes registered via `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`. They are `@AutoConfigureAfter(JaiClawGatewayAutoConfiguration)` so gateway beans are available.

```
JaiClawCalendarAutoConfiguration     @ConditionalOnProperty(jaiclaw.calendar.enabled=true)
  └── CalendarMcpToolProvider        (8 tools: event CRUD, scheduling, availability)

JaiClawMessagingAutoConfiguration    @ConditionalOnProperty(jaiclaw.messaging.enabled=true)
  └── MessagingMcpToolProvider       (8 tools: channel messaging, sessions, agent chat)
```

### Complete Bean Dependency Chain

```
ChatModel (Spring AI)
  └─→ ChatClient.Builder (Spring AI)
        └─→ AgentRuntime (Phase 2)
              └─→ GatewayService (Phase 3)
                    ├─→ GatewayController   (/api/chat, /api/health, /webhook/*)
                    ├─→ GatewayLifecycle    (starts channel adapters)
                    ├─→ WebSocketSessionHandler (/ws/session/{id})
                    └─→ McpServerRegistry → MessagingMcpToolProvider, CalendarMcpToolProvider, etc.
```

### Why Three Separate Auto-Configs?

`@ConditionalOnBean` only checks for beans that are already **defined** at evaluation time. Within a single `@AutoConfiguration` class, nested `@Configuration` classes are evaluated in the same pass — so a nested class cannot reliably `@ConditionalOnBean` a bean defined by the enclosing class or a sibling nested class. Splitting into separate `@AutoConfiguration` classes with `@AutoConfigureAfter` guarantees that each phase's beans are fully defined before the next phase's conditions are evaluated.

---

## Configuration

### application.yml (gateway profile)

```yaml
jaiclaw:
  identity:
    name: "JaiClaw"
  security:
    mode: api-key                  # api-key (default), jwt, or none
    # api-key: ${JAICLAW_API_KEY}   # optional — auto-generated if not set
  agent:
    default-agent: default
  channels:
    telegram:
      enabled: true
      bot-token: ${TELEGRAM_BOT_TOKEN}
      webhook-url: https://jaiclaw.taptech.net/webhook/telegram
    slack:
      enabled: true
      bot-token: ${SLACK_BOT_TOKEN}
      signing-secret: ${SLACK_SIGNING_SECRET}
    discord:
      enabled: true
      bot-token: ${DISCORD_BOT_TOKEN}
    email:
      enabled: ${EMAIL_USERNAME:false}
      provider: imap
      host: ${EMAIL_IMAP_HOST:}
      smtp-host: ${EMAIL_SMTP_HOST:}
      username: ${EMAIL_USERNAME:}
      password: ${EMAIL_PASSWORD:}
    sms:
      enabled: ${TWILIO_ACCOUNT_SID:false}
      account-sid: ${TWILIO_ACCOUNT_SID:}
      auth-token: ${TWILIO_AUTH_TOKEN:}
      from-number: ${TWILIO_FROM_NUMBER:}
  # http:
  #   proxy:
  #     host: ${HTTP_PROXY_HOST:}
  #     port: ${HTTP_PROXY_PORT:0}
  #     username: ${HTTP_PROXY_USERNAME:}
  #     password: ${HTTP_PROXY_PASSWORD:}
  #     non-proxy-hosts: ${NO_PROXY:localhost,127.0.0.1}

spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY:}
    anthropic:
      api-key: ${ANTHROPIC_API_KEY:}
    google:
      genai:
        api-key: ${GEMINI_API_KEY:}
    ollama:
      base-url: http://ollama.infra:11434
```
