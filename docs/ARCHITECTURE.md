# JClaw Architecture

## Overview

JClaw is a Java 21 / Spring Boot 3.5 / Spring AI personal AI assistant framework. It's an embeddable library with a gateway for multi-channel messaging (Telegram, Slack, Discord, Email, SMS), a plugin system, tool execution, skills, memory, document processing, audit logging, and MCP server hosting.

---

## High-Level Architecture

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                          RUNNABLE APPS  (Layer 7)                            │
│                                                                              │
│  ┌───────────────────┐  ┌──────────────────┐  ┌──────────────────────────┐  │
│  │ jclaw-gateway-app │  │    jclaw-shell   │  │     jclaw-examples       │  │
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
│  │                   jclaw-spring-boot-starter                           │  │
│  │                                                                       │  │
│  │  Phase 1: JClawAutoConfiguration          (core beans)                │  │
│  │  Phase 2: JClawGatewayAutoConfiguration   (gateway + MCP)             │  │
│  │  Phase 3: JClawChannelAutoConfiguration   (channel adapters)          │  │
│  └────────┬──────────────────────────────────────────────────────────────┘  │
├───────────┼─────────────────────────────────────────────────────────────────┤
│           │       GATEWAY + CHANNELS  (Layer 4)                             │
│                                                                              │
│  ┌────────┴──────────────┐  ┌────────────────────────────────────────────┐  │
│  │    jclaw-gateway      │  │            Channel Adapters                │  │
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
│  │          jclaw-agent                  │  │      jclaw-security         │ │
│  │  AgentRuntime, SessionManager         │  │  JWT auth, TenantResolver   │ │
│  │  SystemPromptBuilder, JClawAgent      │  └─────────────────────────────┘ │
│  └────────┬──────────────────────────────┘                                  │
│           │                                                                  │
│  ┌────────┴────────┐ ┌──────────────┐ ┌──────────────┐ ┌───────────────┐   │
│  │  jclaw-skills   │ │jclaw-plugin  │ │ jclaw-memory │ │ jclaw-config  │   │
│  │  SkillLoader    │ │  -sdk        │ │ SearchManager│ │ @ConfigProps  │   │
│  │  versioning     │ │ JClawPlugin  │ │ VectorStore  │ │               │   │
│  └─────────────────┘ │ PluginApi    │ └──────────────┘ └───────────────┘   │
│                      └──────────────┘                                       │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐ ┌──────────────────┐   │
│  │  jclaw-docs  │ │ jclaw-media  │ │ jclaw-audit  │ │jclaw-compaction │   │
│  │  PDF / HTML  │ │ vision/audio │ │ AuditLogger  │ │ context window  │   │
│  │  parsing     │ │ analysis     │ │              │ │ summarization   │   │
│  └──────────────┘ └──────────────┘ └──────────────┘ └──────────────────┘   │
│                                                                              │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐ ┌──────────────────┐   │
│  │jclaw-browser │ │ jclaw-cron   │ │ jclaw-voice  │ │ jclaw-identity  │   │
│  │ Playwright   │ │ scheduler    │ │ TTS / STT    │ │ cross-channel   │   │
│  │ automation   │ │ virtual thr  │ │              │ │ user linking    │   │
│  └──────────────┘ └──────────────┘ └──────────────┘ └──────────────────┘   │
│                                                                              │
│  ┌──────────────┐ ┌──────────────┐                                          │
│  │ jclaw-canvas │ │  jclaw-code  │                                          │
│  │ A2UI / HTML  │ │  file edit   │                                          │
│  │ artifacts    │ │  code tools  │                                          │
│  └──────────────┘ └──────────────┘                                          │
├──────────────────────────────────────────────────────────────────────────────┤
│                      TOOL LAYER  (Layer 2)                                   │
│                                                                              │
│  ┌──────────────────────────────────────┐  ┌─────────────────────────────┐  │
│  │           jclaw-tools               │  │     jclaw-tools-k8s        │  │
│  │  ToolRegistry, built-in tools       │  │  Fabric8 Kubernetes tools  │  │
│  │  SpringAiToolBridge, EmbabelBridge  │  └─────────────────────────────┘  │
│  └────────┬─────────────────────────────┘                                   │
├───────────┼──────────────────────────────────────────────────────────────────┤
│           │       CHANNEL SPI  (Layer 1)                                    │
│                                                                              │
│  ┌────────┴──────────────────────────────────────────────────────────────┐  │
│  │                      jclaw-channel-api                                │  │
│  │  ChannelAdapter SPI, ChannelMessage, attachments, ChannelRegistry     │  │
│  └────────┬──────────────────────────────────────────────────────────────┘  │
├───────────┼──────────────────────────────────────────────────────────────────┤
│           │       CORE  (Layer 0) — Pure Java, no Spring                    │
│                                                                              │
│  ┌────────┴──────────────────────────────────────────────────────────────┐  │
│  │                        jclaw-core                                     │  │
│  │  Records: Message, Session, CronJob, ToolResult, DeliveryResult       │  │
│  │  Sealed interfaces: Message, ToolResult, DeliveryResult               │  │
│  │  Enums: ToolProfile, PluginKind, HookName                             │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────────────────────┘
```

---

## Module Dependency Graph

```
jclaw-core  (pure Java — NO Spring dependency)
  |
  +---> jclaw-channel-api  (ChannelAdapter SPI, ChannelMessage, attachments)
  |       |
  |       +---> jclaw-channel-telegram  (Bot API polling + webhook + file downloads)
  |       +---> jclaw-channel-slack     (Socket Mode + Events API)
  |       +---> jclaw-channel-discord   (Gateway WebSocket + Interactions)
  |       +---> jclaw-channel-email     (IMAP polling + SMTP + MIME attachments)
  |       +---> jclaw-channel-sms       (Twilio REST API + webhook + MMS)
  |
  +---> jclaw-tools  (ToolRegistry, built-in tools, SpringAiToolBridge, Embabel bridge)
  |       |
  |       +---> jclaw-tools-k8s  (Fabric8 Kubernetes tools)
  |       +---> jclaw-agent  (AgentRuntime, SessionManager, SystemPromptBuilder)
  |
  +---> jclaw-skills  (SkillLoader, versioning, TenantSkillRegistry)
  +---> jclaw-plugin-sdk  (JClawPlugin SPI, PluginApi, HookRunner, PluginDiscovery)
  +---> jclaw-memory  (MemorySearchManager SPI, InMemorySearchManager, VectorStore)
  +---> jclaw-security  (JWT auth, TenantResolver, SecurityContext)
  +---> jclaw-documents  (PDF/HTML/text parsing, chunking pipeline)
  +---> jclaw-media  (async media analysis SPI, CompositeMediaAnalyzer)
  +---> jclaw-audit  (AuditEvent, AuditLogger SPI, InMemoryAuditLogger)
  +---> jclaw-compaction  (context window compaction via summarization)
  +---> jclaw-browser  (Playwright-based browser automation)
  +---> jclaw-cron  (cron job scheduling, JSON persistence, virtual threads)
  +---> jclaw-voice  (TTS/STT SPI, OpenAI provider)
  +---> jclaw-identity  (cross-channel identity linking, JSON persistence)
  +---> jclaw-canvas  (A2UI artifact rendering, HTML file management)
  +---> jclaw-code  (file editing, code generation tools)
  +---> jclaw-config  (@ConfigurationProperties records)
          |
          +---> jclaw-gateway  (REST + WS + webhooks + MCP hosting + observability)
          |
          +---> jclaw-spring-boot-starter  (auto-configuration wiring)
          |       |
          |       +---> jclaw-gateway-app  (standalone gateway server)
          |       +---> jclaw-shell  (Spring Shell CLI)
          |
          +---> jclaw-examples  (10 standalone example applications)
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
    │              JCLAW GATEWAY (Deployment)            │
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
| Email     | **IMAP polling** (configurable interval)         | SMTP `Transport.send`    | Username/pass  | `jclaw-channel-email`     |
| SMS       | **Twilio webhook** POST                          | Twilio Messages API      | Account SID    | `jclaw-channel-sms`       |
| Web UI    | WebSocket `/ws/session/{id}`                     | WebSocket                | JWT / session  | `jclaw-gateway` (built-in)|
| REST API  | `POST /api/chat`                                 | JSON response            | API key / JWT  | `jclaw-gateway` (built-in)|
| MCP       | `POST /mcp/{server}/tools/{tool}`                | JSON response            | API key / JWT  | `jclaw-gateway` (built-in)|

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

Using Eclipse JKube (`kubernetes-maven-plugin`) with `eclipse-temurin:21-jre` base image, activated via Maven profile. Two modules produce images:

- **`jclaw-gateway-app`** — production HTTP server (REST + WebSocket + all channels)
- **`jclaw-shell`** — interactive CLI (headless/scripted use, Docker-based access)

```bash
# Build both images
./mvnw package k8s:build -pl jclaw-gateway-app,jclaw-shell -am -Pk8s -DskipTests

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
  - name: JCLAW_SECURITY_MODE
    value: "api-key"
```

---

## What Exists vs. What's Needed

| Component                        | Status       | Module                       |
|----------------------------------|--------------|------------------------------|
| Core domain model                | Done         | `jclaw-core`                 |
| Agent runtime + sessions         | Done         | `jclaw-agent`                |
| Tool system + built-in tools     | Done         | `jclaw-tools`                |
| Embabel orchestration bridge     | Done         | `jclaw-tools` (bridge/embabel) |
| Skills system + versioning       | Done         | `jclaw-skills`               |
| Tenant-aware skill registry      | Done         | `jclaw-skills`               |
| Plugin system + hooks            | Done         | `jclaw-plugin-sdk`           |
| Memory search (in-memory + vector)| Done        | `jclaw-memory`               |
| Multi-tenancy + JWT auth         | Done         | `jclaw-core` + `jclaw-security` |
| Document parsing + chunking      | Done         | `jclaw-documents`            |
| Media analysis SPI               | Done         | `jclaw-media`                |
| Audit logging SPI                | Done         | `jclaw-audit`                |
| Auto-configuration               | Done         | `jclaw-spring-boot-starter`  |
| Spring Shell CLI                 | Done         | `jclaw-shell`                |
| Interactive onboarding wizard    | Done         | `jclaw-shell`                |
| Channel adapter SPI + attachments| Done         | `jclaw-channel-api`          |
| Gateway (REST + WS + webhooks)   | Done         | `jclaw-gateway`              |
| MCP server hosting               | Done         | `jclaw-gateway` (mcp/)       |
| Observability (metrics + health) | Done         | `jclaw-gateway` (observability/) |
| Telegram adapter (poll + webhook)| Done         | `jclaw-channel-telegram`     |
| Slack adapter                    | Done         | `jclaw-channel-slack`        |
| Discord adapter                  | Done         | `jclaw-channel-discord`      |
| Email adapter (IMAP + SMTP)      | Done         | `jclaw-channel-email`        |
| SMS adapter (Twilio)             | Done         | `jclaw-channel-sms`          |
| Standalone gateway app           | Done         | `jclaw-gateway-app`          |
| Docker image build (JKube)       | Done         | `-Pk8s` profile in POMs      |
| **Helm chart**                   | **Needed**   | `helm/spring-boot-app/`      |
| **Redis session store**          | **Planned**  | `jclaw-agent` (swap in-mem)  |
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

### Phase 2 — JClawAutoConfiguration

`@AutoConfigureAfter(ChatClientAutoConfiguration)` — core JClaw beans.

```
JClawAutoConfiguration
  │
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

### Phase 3 — JClawGatewayAutoConfiguration

`@AutoConfigureAfter(JClawAutoConfiguration)` — gateway HTTP/WS layer. Entire class is gated on:
- `@ConditionalOnClass(GatewayService)` — `jclaw-gateway` must be on the classpath
- `@ConditionalOnBean(AgentRuntime)` — an LLM provider must be configured

```
JClawGatewayAutoConfiguration
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
  ├── gatewayMetrics          GatewayMetrics               (atomic request/error counters)
  └── gatewayHealthIndicator  GatewayHealthIndicator       (UP/DEGRADED based on channel adapter status)
```

### Phase 4 — JClawChannelAutoConfiguration

`@AutoConfigureAfter(JClawGatewayAutoConfiguration)` — channel adapters. Each adapter is in a nested `@Configuration` gated on `@ConditionalOnClass` (adapter JAR on classpath) and `@ConditionalOnBean(WebhookDispatcher)` (gateway must be active).

```
JClawChannelAutoConfiguration
  │
  ├── TelegramAutoConfiguration   ─── creates ──→  TelegramAdapter     @ConditionalOnClass + @ConditionalOnBean(WebhookDispatcher)
  ├── SlackAutoConfiguration      ─── creates ──→  SlackAdapter         @ConditionalOnClass + @ConditionalOnBean(WebhookDispatcher)
  ├── DiscordAutoConfiguration    ─── creates ──→  DiscordAdapter       @ConditionalOnClass + @ConditionalOnBean(WebhookDispatcher)
  ├── EmailAutoConfiguration      ─── creates ──→  EmailAdapter         @ConditionalOnClass (no WebhookDispatcher needed)
  ├── SmsAutoConfiguration        ─── creates ──→  SmsAdapter           @ConditionalOnClass (no WebhookDispatcher needed)
  └── AuditAutoConfiguration      ─── creates ──→  InMemoryAuditLogger  @ConditionalOnClass(AuditLogger)
```

### Complete Bean Dependency Chain

```
ChatModel (Spring AI)
  └─→ ChatClient.Builder (Spring AI)
        └─→ AgentRuntime (Phase 2)
              └─→ GatewayService (Phase 3)
                    ├─→ GatewayController   (/api/chat, /api/health, /webhook/*)
                    ├─→ GatewayLifecycle    (starts channel adapters)
                    └─→ WebSocketSessionHandler (/ws/session/{id})
```

### Why Three Separate Auto-Configs?

`@ConditionalOnBean` only checks for beans that are already **defined** at evaluation time. Within a single `@AutoConfiguration` class, nested `@Configuration` classes are evaluated in the same pass — so a nested class cannot reliably `@ConditionalOnBean` a bean defined by the enclosing class or a sibling nested class. Splitting into separate `@AutoConfiguration` classes with `@AutoConfigureAfter` guarantees that each phase's beans are fully defined before the next phase's conditions are evaluated.

---

## Configuration

### application.yml (gateway profile)

```yaml
jclaw:
  identity:
    name: "JClaw"
  security:
    mode: api-key                  # api-key (default), jwt, or none
    # api-key: ${JCLAW_API_KEY}   # optional — auto-generated if not set
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
