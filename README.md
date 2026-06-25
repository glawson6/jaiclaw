<p align="center">
  <img src="jaiclaw-image.png" alt="JaiClaw Logo" width="200">
</p>

<h1 align="center">JaiClaw</h1>

<p align="center">
  <em>Production-grade Java framework for building AI agents — from a single-developer assistant to a multi-tenant enterprise platform.</em>
</p>

<p align="center">
  <strong>One framework. Every scale. Zero platform changes.</strong>
</p>

<p align="center">
  <a href="https://www.oracle.com/java/technologies/javase/jdk21-archive-downloads.html"><img src="https://img.shields.io/badge/Java-21-orange?logo=openjdk&logoColor=white" alt="Java 21"></a>
  <a href="https://spring.io/projects/spring-boot"><img src="https://img.shields.io/badge/Spring%20Boot-3.5.15-6DB33F?logo=springboot&logoColor=white" alt="Spring Boot 3.5.15"></a>
  <a href="https://docs.spring.io/spring-ai/reference/"><img src="https://img.shields.io/badge/Spring%20AI-1.1.7-6DB33F?logo=spring&logoColor=white" alt="Spring AI 1.1.7"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-Apache%202.0-blue" alt="License"></a>
  <a href="https://central.sonatype.com/artifact/io.jaiclaw/jaiclaw-bom"><img src="https://img.shields.io/maven-central/v/io.jaiclaw/jaiclaw-bom.svg?label=Maven%20Central" alt="Maven Central"></a>
</p>

<p align="center">
  <a href="#architecture"><img src="https://img.shields.io/badge/Modules-160-informational" alt="160 Modules"></a>
  <a href="jaiclaw-starters/"><img src="https://img.shields.io/badge/Starters-31-informational" alt="31 Starters"></a>
  <a href="#channels"><img src="https://img.shields.io/badge/Channels-11-blueviolet" alt="11 Channels"></a>
  <a href="#configuration"><img src="https://img.shields.io/badge/LLM%20Providers-11-green" alt="11 LLM Providers"></a>
  <a href="jaiclaw-examples/"><img src="https://img.shields.io/badge/Examples-43-yellow" alt="43 Examples"></a>
</p>

---

## What Is JaiClaw?

JaiClaw *(pronounced "Jay-Claw")* is a Java framework for building production AI agents. The same codebase scales from a developer running `curl | bash` on a laptop to a horizontally-scaled multi-tenant SaaS platform serving thousands of users across dozens of organizations.

Built on Java 21, Spring Boot 3.5.15, Spring AI 1.1.7, Embabel Agent 0.3.5, and Apache Camel 4.18 — JaiClaw treats the AI agent runtime the way Spring Boot treats the web tier: a Java library with explicit SPIs, a published BOM, conditional auto-configuration, and an API stability program. Bring it in via Maven Central, compose the starters you need, implement the SPIs your business requires, ship.

It started as a ground-up Java port of [OpenClaw](https://github.com/openclaw/openclaw) (TypeScript/Node.js) and has since grown well beyond the original — adding enterprise multi-tenancy, GOAP-based agent planning, MCP server hosting, declarative pipelines, scaffolding tooling, and security hardening that don't exist in the Node.js version.

## Three Ways to Use JaiClaw

### 1. As an enterprise library — build your own AI agent product

For Java teams building their own AI agent product on top of a proven foundation. Pull JaiClaw via the BOM, compose the [Spring Boot starters](jaiclaw-starters/) you need, implement the SPIs for your business domain. The framework gets out of your way.

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.jaiclaw</groupId>
            <artifactId>jaiclaw-bom</artifactId>
            <version>0.9.2</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

Don't want to wire up the project structure by hand? Skip it. The [scaffolding tool](#scaffolding-a-new-jaiclaw-project) generates a complete, runnable Maven project from a ~10-line YAML manifest.

[Stable SPIs](core/jaiclaw-core/src/main/java/io/jaiclaw/core/api/) for `ChannelAdapter`, `ToolCallback`, `MemoryProvider`, `SoulProvider`, `SecretsProvider`, `AuditLogger`, `TranscriptStore`, `ArtifactStore`, `McpToolProvider`, and more — each marked `@Stable`, `@Experimental`, or `@Internal` so you know what you're building against.

### 2. As a deployable multi-tenant agent platform

For SaaS teams running an agent platform for multiple customers. The `jaiclaw-gateway-app` is a stateless Spring Boot service — HTTP + WebSocket + webhook surface, horizontally scalable behind a Service. Flip `jaiclaw.tenant.mode=multi` and the framework isolates sessions, memory, skills, secrets, and audit per tenant from a single codebase.

```yaml
jaiclaw:
  tenant:
    mode: multi          # per-tenant isolation across the runtime
  security:
    mode: api-key        # or jwt
```

[Production Deployment Guide →](docs/user/PRODUCTION-DEPLOYMENT.md) covers JKube image builds, Kubernetes manifests, Helm values, observability, health probes, resource sizing, security hardening, and cloud-provider notes.

[From Personal to Enterprise →](docs/user/JAICLAW-FROM-PERSONAL-TO-ENTERPRISE.md) walks the spectrum across five concrete scales — personal assistant → team bot → departmental platform → enterprise multi-tenant → Embabel-orchestrated multi-agent workflows.

### 3. As a single-binary assistant

For developers and hobbyists who want a multi-channel AI agent on their laptop in under a minute. One curl line, then chat:

```bash
curl -fsSL https://jaiclaw.io/install.sh | bash
jaiclaw chat "hello"
```

Multi-channel out of the box — Telegram, Slack, Discord, Email, SMS, Signal, Teams, WhatsApp, Google Chat, LINE, Matrix — with local dev modes that need no public endpoint or webhook. See the [Quick Start](#quick-start) section below for details.

## Why JaiClaw

Surface-level evidence that this is a serious framework, not a demo:

- **API stability program** — `@Stable` / `@Experimental` / `@Internal` markers on every public top-level type. 20+ `@Stable` SPIs already committed-to for 1.0. JSpecify `@NullMarked` at the package level. ([details](core/jaiclaw-core/src/main/java/io/jaiclaw/core/api/))
- **31 Spring Boot starters** — take what you need, leave what you don't. The starter uses 84 `@ConditionalOnMissingBean`, 29 `@ConditionalOnClass`, and 20 `@ConditionalOnProperty` so starters don't fight each other or force unwanted dependencies. ([list](jaiclaw-starters/))
- **Multi-tenancy is wired at the framework level** — `jaiclaw.tenant.mode: single | multi` flips per-tenant isolation across sessions, memory, skills, audit, and secrets without code changes. ([details](docs/user/JAICLAW-FROM-PERSONAL-TO-ENTERPRISE.md))
- **Kubernetes-ready** — JKube produces multi-arch (`linux/amd64` + `linux/arm64`) container images. The gateway is stateless and horizontally scalable. Distributed memory backends supported. ([Production Deployment](docs/user/PRODUCTION-DEPLOYMENT.md))
- **22 plugin lifecycle hooks** — intercept before/after LLM calls, tool execution, message pipeline, context compaction, session events. Reshape the entire agent behavior without forking. ([hook events](core/jaiclaw-core/src/main/java/io/jaiclaw/core/hook/event/))
- **MCP server hosting** — expose JaiClaw tools to Claude Desktop, Cursor, or any MCP client. `McpToolProvider` SPI dogfooded by 22 in-repo implementations. ([details](core/jaiclaw-core/src/main/java/io/jaiclaw/core/mcp/))
- **Observability built in** — Spring Boot Actuator with custom endpoints (`/actuator/pipelines`, `/actuator/kanban`, `/actuator/agentmind-tendencies`), Micrometer instrumentation throughout, `AuditLogger` + `TranscriptStore` SPIs.
- **GOAP multi-agent planning** — [Embabel](https://github.com/embabel/embabel-agent) integration. Deterministic action sequences computed by A* search, with automatic parallelism detection and typed intermediate results.
- **11 channels, 11 LLM providers, 43 runnable examples** — the same agent code runs across Telegram, Slack, Discord, Email, SMS, Signal, Teams, WhatsApp, Google Chat, LINE, Matrix; the same code targets Anthropic, OpenAI, Gemini, Ollama, Bedrock, Azure OpenAI, DeepSeek, Mistral, MiniMax, Vertex AI, OCI GenAI.

## Architecture

160 Maven modules, organized so consumers can take what they need and leave what they don't:

```
core/                11 modules — pure-Java domain model (jaiclaw-core has no Spring dep)
channels/             7 modules — one per messaging platform
extensions/          41 modules — opt-in capabilities (voice, browser, identity, pipeline, …)
apps/                 5 modules — runnable Spring Boot apps (gateway, shell, CLI)
jaiclaw-starters/    31 modules — Spring Boot starters for downstream consumers
jaiclaw-examples/    43 modules — runnable example applications
```

The core module graph:

```
jaiclaw-core              Pure-Java domain model — no Spring dependency
jaiclaw-channel-api       ChannelAdapter SPI, attachments, channel registry
jaiclaw-config            @ConfigurationProperties records
jaiclaw-tools             Tool registry + built-in tools + Spring AI bridge + Embabel bridge
jaiclaw-agent             Agent runtime, session management, prompt building
jaiclaw-skills            Skill loader + versioning + tenant-aware registry
jaiclaw-plugin-sdk        Plugin SPI, lifecycle hooks, discovery
jaiclaw-memory            Memory search (in-memory + vector store)
jaiclaw-security          JWT auth, tenant resolution, SecurityContext
jaiclaw-documents         Document parsing (PDF, HTML, text) + chunking pipeline
jaiclaw-gateway           REST + WebSocket + webhook + MCP + observability (library)
jaiclaw-spring-boot-starter  Auto-configuration for all modules
jaiclaw-gateway-app       Standalone gateway server (runnable Spring Boot app)
jaiclaw-shell             Spring Shell CLI (runnable)
jaiclaw-cli               Unified standalone CLI (runnable)
```

See [docs/dev/ARCHITECTURE.md](docs/dev/ARCHITECTURE.md) for the full module graph, message flow diagrams, and deployment patterns.

## Scaffolding a New JaiClaw Project

JaiClaw ships a Spring-Initializr-class project generator with three entry points sharing one core engine. This is what convinces a team they can build their own AI agent product on top of JaiClaw without copy-pasting and stripping an example.

**Three entry points:**

1. **Maven goal** — `mvn io.jaiclaw:jaiclaw-maven-plugin:scaffold -Djaiclaw.scaffold.manifest=jaiclaw-manifest.yml`. CI-friendly. Implementation: [`ScaffoldMojo.java`](jaiclaw-maven-plugin/src/main/java/io/jaiclaw/maven/ScaffoldMojo.java) delegates to [`tools/jaiclaw-project-scaffolder/`](tools/jaiclaw-project-scaffolder/)'s `ProjectGenerator`.
2. **Spring Shell command** — `scaffold create <manifest>` inside the JaiClaw shell. Interactive, with manifest validation.
3. **Conversational skill** — the agent itself walks you through a requirements-gathering session and emits a valid `jaiclaw-manifest.yml`. "I want a Slack bot that summarizes PDFs using OpenAI" → produces a real manifest → produces a real Maven project. Skill: [`scaffold-project/SKILL.md`](core/jaiclaw-skills/src/main/resources/skills/scaffold-project/SKILL.md), loaded automatically.

**5 archetypes built in:** `gateway` (REST API bot, default), `embabel` (GOAP-planned multi-step agent), `camel` (Apache Camel routing pipelines), `comprehensive` (full-featured assistant), `minimal` (no gateway, for libraries/CLIs).

**Two parent modes:**
- `standalone` (default) — uses `spring-boot-starter-parent` + `jaiclaw-bom` import. Self-contained, no monorepo coupling. The right choice for external consumers.
- `jaiclaw` — uses `jaiclaw-parent` as Maven parent. Inherits managed versions + plugin config. The right choice for internal extensions of the ecosystem.

**Minimal manifest** — only `name` and `description` are required; everything else has sensible defaults:

```yaml
# jaiclaw-manifest.yml
name: pdf-summarizer
description: A Slack bot that summarizes PDFs on request
archetype: gateway
ai-provider:
  primary: anthropic
channels: [slack]
extensions: [documents]
```

```bash
mvn io.jaiclaw:jaiclaw-maven-plugin:scaffold \
    -Djaiclaw.scaffold.manifest=jaiclaw-manifest.yml \
    -Djaiclaw.scaffold.outputDir=./projects
```

→ produces a complete Maven project ready to `./mvnw spring-boot:run`.

See [`tools/jaiclaw-project-scaffolder/README.md`](tools/jaiclaw-project-scaffolder/README.md) for the full manifest schema, all archetypes, provider/channel options, and tool stub generation.

## Production Deployment

Three deployment topologies, same codebase:

### Topology 1 — Embedded in your Spring Boot app

Pull `jaiclaw-spring-boot-starter` into an existing Spring Boot service. Tenant mode = `single`. The agent runtime, tools, skills, and channel adapters all start with your app. Suited for: internal tools, embedded copilots, AI features inside an existing product.

### Topology 2 — Standalone gateway, single-tenant

Deploy `jaiclaw-gateway-app` as a Docker container (or K8s Deployment). Stateless HTTP + WebSocket + webhook surface. Multi-arch images via JKube (`./mvnw package k8s:build -pl :jaiclaw-gateway-app -am -Pk8s`). Drops into any general-purpose `spring-boot-app` Helm chart. Suited for: a dedicated agent microservice, a customer-facing assistant.

### Topology 3 — Standalone gateway, multi-tenant SaaS

Same gateway, `jaiclaw.tenant.mode=multi`. JWT-resolved tenant isolation across sessions, memory, skills, secrets, and audit. Stateful backends (Redis, Postgres, vector store) are partitioned per-tenant by the framework. Suited for: multi-customer SaaS, enterprise agent platforms serving multiple organizations.

```yaml
# values.yaml — minimal helm values for a multi-tenant gateway
image:
  repository: registry.example.com/io.jaiclaw/jaiclaw-gateway-app
  tag: "0.9.2"

replicaCount: 3

env:
  SPRING_PROFILES_ACTIVE: production,security-hardened
  JAICLAW_TENANT_MODE: multi
  JAICLAW_SECURITY_MODE: jwt

probes:
  liveness:
    path: /actuator/health/liveness
  readiness:
    path: /actuator/health/readiness
```

[Production Deployment Guide →](docs/user/PRODUCTION-DEPLOYMENT.md) — covers JKube image build, Kubernetes manifests, Helm values, secrets management (env vars, 1Password Connect, etc.), observability (Actuator + Micrometer), health probes, rolling upgrades, resource sizing, security hardening, and cloud-provider notes (GKE, EKS, AKS).

## Quick Start

### Option 1: Single binary via curl

```bash
curl -fsSL https://jaiclaw.io/install.sh | bash
jaiclaw chat "hello"
```

The installer detects whether Java 21+ is available, and offers to install it via SDKMAN if not. Persists configuration to `~/.jaiclaw/profiles/default/`. Run `jaiclaw setup` for an interactive wizard to configure LLM providers, channels, MCP servers, and the REPL prompt.

### Option 2: Docker

```bash
git clone https://github.com/jaiclaw/jaiclaw.git
cd jaiclaw
./quickstart.sh
```

Builds the Docker image and starts the gateway. If no API key is provided, it also starts Ollama and pulls a local LLM model (~3GB download). To use a cloud provider instead:

```bash
ANTHROPIC_API_KEY=sk-ant-... ./quickstart.sh    # Anthropic
OPENAI_API_KEY=sk-... ./quickstart.sh           # OpenAI
GEMINI_API_KEY=... ./quickstart.sh              # Google Gemini
```

Test with (the API key is auto-generated at `~/.jaiclaw/api-key` on first run):

```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -H "X-API-Key: $(cat ~/.jaiclaw/api-key)" \
  -d '{"content": "hello"}'
```

### Option 3: start.sh (daily driver)

After the initial build, use `start.sh` to run the gateway or interactive shell. It loads API keys from `docker-compose/.env` automatically.

```bash
vi docker-compose/.env       # edit API keys once
./start.sh                    # gateway (local Java)
./start.sh shell              # interactive shell (local)
./start.sh cli                # interactive shell (Docker, no Java)
./start.sh docker             # gateway via Docker Compose
./start.sh --force-build      # rebuild from source
./start.sh stop               # stop the Docker stack
./start.sh logs               # tail gateway logs
```

### Option 4: setup.sh (developer setup)

```bash
git clone https://github.com/jaiclaw/jaiclaw.git
cd jaiclaw
./setup.sh
```

Installs Java 21 via SDKMAN if needed, builds all modules, and launches the interactive shell. Run the onboarding wizard to configure your LLM provider:

```bash
jaiclaw> setup
```

### Option 5: Embed in your own Spring Boot app

If you want to add JaiClaw to an existing project rather than clone this repo, see the [Three Ways to Use JaiClaw § 1](#1-as-an-enterprise-library--build-your-own-ai-agent-product) section above for the BOM snippet. Smallest possible JaiClaw program: see [jaiclaw-examples/hello-world/](jaiclaw-examples/hello-world/) — ~30 lines of Java + one `application.yml`.

## Channels

All messaging channels support a **local dev mode** that requires no public endpoint:

| Channel  | Local Dev Mode       | Setup Time |
|----------|---------------------|------------|
| Telegram | Long polling        | ~2 min     |
| Slack    | Socket Mode         | ~5 min     |
| Discord  | Gateway WebSocket   | ~5 min     |
| Email    | IMAP polling        | ~3 min     |
| SMS      | Twilio webhook      | ~5 min     |
| Signal   | Signal CLI bridge   | ~10 min    |
| Teams    | Bot Framework       | ~10 min    |

Plus WhatsApp, Google Chat, LINE, and Matrix. Add channel tokens to `docker-compose/.env` and restart, or pass as environment variables:

```bash
# Telegram (polling mode — no webhook needed)
TELEGRAM_BOT_TOKEN=123456:ABC-DEF... \
ANTHROPIC_API_KEY=sk-ant-... \
./mvnw spring-boot:run -pl jaiclaw-gateway-app

# Slack (Socket Mode — no webhook needed)
SLACK_BOT_TOKEN=xoxb-... \
SLACK_APP_TOKEN=xapp-... \
ANTHROPIC_API_KEY=sk-ant-... \
./mvnw spring-boot:run -pl jaiclaw-gateway-app

# Discord (Gateway mode — no webhook needed)
DISCORD_BOT_TOKEN=... \
DISCORD_USE_GATEWAY=true \
ANTHROPIC_API_KEY=sk-ant-... \
./mvnw spring-boot:run -pl jaiclaw-gateway-app
```

See [docs/user/OPERATIONS.md](docs/user/OPERATIONS.md) for full channel setup instructions including Email, SMS, and production webhook configuration.

## Examples

**43 example applications** demonstrating JaiClaw capabilities. Each is a standalone Spring Boot app and builds under `./mvnw package -pl :<artifact-id> -am`. See [docs/user/EXAMPLES.md](docs/user/EXAMPLES.md) for the full catalog with build & run instructions.

Brand new? Start at [hello-world](jaiclaw-examples/hello-world/) — the smallest possible JaiClaw program (~30 LOC).

### Getting started

| Example | Description |
|---------|-------------|
| [hello-world](jaiclaw-examples/hello-world/) | Minimal chat app with one custom echo tool — the shortest path from clone to "it works" |

### Pipelines (declarative multi-stage workflows)

| Example | Description |
|---------|-------------|
| [pipeline-e2e](jaiclaw-examples/pipeline-e2e/) | Exercises every Pipeline UX surface — used by e2e scenario 6 |
| [support-triage-pipeline](jaiclaw-examples/support-triage-pipeline/) | Classify → route → respond customer-support workflow |
| [invoice-processor](jaiclaw-examples/invoice-processor/) | Extract → validate → approve invoice ingestion pipeline |
| [aiops-incident-responder](jaiclaw-examples/aiops-incident-responder/) | Alert → enrich → propose-remediation incident pipeline |
| [competitive-intel-briefing](jaiclaw-examples/competitive-intel-briefing/) | Scheduled multi-source competitor analysis briefing |
| [sales-enrichment-pipeline](jaiclaw-examples/sales-enrichment-pipeline/) | Lead → enrich → score sales pipeline |
| [contract-reviewer](jaiclaw-examples/contract-reviewer/) | Parse → flag → summarize contract review pipeline |
| [content-pipeline](jaiclaw-examples/content-pipeline/) | Multi-modal content analysis for images, audio, and PDFs |
| [data-pipeline](jaiclaw-examples/data-pipeline/) | ETL orchestrator with schema validation and human-in-the-loop approval |

### Apache Camel integration

| Example | Description |
|---------|-------------|
| [camel-html-summarizer](jaiclaw-examples/camel-html-summarizer/) | Camel route → JaiClaw agent → summary, end-to-end |
| [camel-html-summarizer-embabel](jaiclaw-examples/camel-html-summarizer-embabel/) | Same flow, GOAP-planned via Embabel |
| [camel-html-summarizer-telegram](jaiclaw-examples/camel-html-summarizer-telegram/) | Plus Telegram inbound/outbound channel |
| [camel-pdf-filler](jaiclaw-examples/camel-pdf-filler/) | Camel-driven PDF form filling pipeline |
| [camel-pdf-filler-telegram](jaiclaw-examples/camel-pdf-filler-telegram/) | PDF filler triggered from Telegram |

### Scheduling & automation

| Example | Description |
|---------|-------------|
| [daily-briefing](jaiclaw-examples/daily-briefing/) | Scheduled morning briefing with news and weather via Telegram/Email |
| [sales-report](jaiclaw-examples/sales-report/) | Weekly sales dashboard with HTML report via Canvas |
| [price-monitor](jaiclaw-examples/price-monitor/) | Hourly price checker with SMS alerts when prices drop |
| [system-monitor](jaiclaw-examples/system-monitor/) | Daily server health analysis with Telegram reporting |

### GOAP multi-agent (Embabel)

| Example | Description |
|---------|-------------|
| [code-review-bot](jaiclaw-examples/code-review-bot/) | GOAP-orchestrated PR code review with structured feedback |
| [travel-planner](jaiclaw-examples/travel-planner/) | Multi-step trip planning with parallel flight/hotel search |
| [compliance-checker](jaiclaw-examples/compliance-checker/) | Document compliance verification with full audit trail |
| [incident-responder](jaiclaw-examples/incident-responder/) | DevOps incident triage with health checks, log queries, and remediation |

### Business workflows

| Example | Description |
|---------|-------------|
| [support-triage](jaiclaw-examples/support-triage/) | Customer support ticket triage and routing |
| [procurement-approval](jaiclaw-examples/procurement-approval/) | Multi-step procurement workflow with approval chain |
| [tax-advisor](jaiclaw-examples/tax-advisor/) | Tax calculation and comparison tool |
| [onboarding-intake](jaiclaw-examples/onboarding-intake/) | Employee onboarding intake form workflow |
| [helpdesk-bot](jaiclaw-examples/helpdesk-bot/) | Multi-tenant support bot with FAQ search and ticket creation |

### Documents & knowledge

| Example | Description |
|---------|-------------|
| [document-qa](jaiclaw-examples/document-qa/) | PDF ingestion and semantic search Q&A with citations |
| [telegram-docstore](jaiclaw-examples/telegram-docstore/) | Telegram bot for document management and semantic search |
| [research-assistant](jaiclaw-examples/research-assistant/) | Multi-source research with structured report generation |

### Communication & voice

| Example | Description |
|---------|-------------|
| [meeting-assistant](jaiclaw-examples/meeting-assistant/) | Meeting transcription, speaker identification, and Slack summaries |
| [voice-call-demo](jaiclaw-examples/voice-call-demo/) | Telephony with outbound reminders and inbound customer service via Twilio |

### Security

| Example | Description |
|---------|-------------|
| [security-handshake](jaiclaw-examples/security-handshake/) | LLM-driven ECDH key exchange and session token bootstrap |
| [security-handshake-server](jaiclaw-examples/security-handshake-server/) | MCP server implementing full ECDH P-256 security handshake protocol |
| [oauth-provider-demo](jaiclaw-examples/oauth-provider-demo/) | OAuth-gated LLM access with PKCE and device code flows |

### Developer tools

| Example | Description |
|---------|-------------|
| [code-scaffolder](jaiclaw-examples/code-scaffolder/) | Project scaffolding agent that generates complete project structures from templates |
| [canvas-dashboard](jaiclaw-examples/canvas-dashboard/) | On-demand interactive HTML dashboards with Chart.js via Canvas (A2UI) |
| [mcp-docs-server](jaiclaw-examples/mcp-docs-server/) | MCP resource server exposing JaiClaw documentation to Claude Desktop, Cursor, etc. |

### Local LLM

| Example | Description |
|---------|-------------|
| [gemma4-local](jaiclaw-examples/gemma4-local/) | Gemma 4 chatbot running fully local via Ollama with native function calling |

## Configuration

Configuration lives in a `.env` file. By default this is `docker-compose/.env`, but you can choose `~/.jaiclaw/.env` (persists across projects) during first run or via `./quickstart.sh --reconfigure`. The chosen location is saved in `~/.jaiclawrc` and respected by all scripts.

You can also set `JAICLAW_ENV_FILE` directly to point to any `.env` file.

| Variable | Default | Description |
|----------|---------|-------------|
| `JAICLAW_SECURITY_MODE` | `api-key` | Security mode: `api-key`, `jwt`, or `none` |
| `JAICLAW_API_KEY` | (auto-generated) | Custom API key for `api-key` mode |
| `AI_PROVIDER` | `anthropic` | LLM provider: `anthropic`, `openai`, `ollama`, `google-genai`, `bedrock`, `azure-openai`, `deepseek`, `mistral-ai`, `minimax`, `vertex-ai`, `oci-genai` |
| `ANTHROPIC_API_KEY` | | Anthropic API key |
| `ANTHROPIC_MODEL` | `claude-sonnet-4-5` | Anthropic model name |
| `OPENAI_API_KEY` | | OpenAI API key |
| `GEMINI_API_KEY` | | Google Gemini API key |
| `OLLAMA_ENABLED` | `false` | Enable Ollama local LLM |
| `MINIMAX_ENABLED` | `false` | Enable MiniMax LLM |
| `GATEWAY_PORT` | `8080` | Gateway HTTP port |

See [docs/user/OPERATIONS.md](docs/user/OPERATIONS.md) for the full environment variable reference.

## Security

The gateway protects `/api/chat` and `/mcp/**` endpoints with API key authentication by default. On first run, a key is auto-generated at `~/.jaiclaw/api-key` and printed in the curl examples by the launcher scripts.

```bash
# Disable security for local development
JAICLAW_SECURITY_MODE=none ./start.sh local

# Use a custom API key
JAICLAW_API_KEY=my-custom-key ./start.sh local
```

For production deployments, enable the `security-hardened` Spring profile to turn on HMAC webhook verification, SSRF guards, workspace path boundaries, timing-safe API key comparison, and ECDH agent-to-agent key exchange. See [docs/user/PRODUCTION-DEPLOYMENT.md § 9 Security hardening](docs/user/PRODUCTION-DEPLOYMENT.md).

## Running the Interactive Shell

The shell provides a Spring Shell CLI for chatting with the agent directly in your terminal.

```bash
./start.sh shell       # local Java (requires Java 21)
./start.sh cli         # Docker (no Java needed)
```

Or with Maven directly:

```bash
ANTHROPIC_API_KEY=sk-ant-... ./mvnw spring-boot:run -pl jaiclaw-shell
```

### Shell Commands

| Command | Description |
|---------|-------------|
| `chat <message>` | Send a message to the agent |
| `new-session` | Start a fresh chat session |
| `sessions` | List active sessions |
| `session-history` | Show messages in the current session |
| `status` | Show system status |
| `config` | Show current configuration |
| `models` | Show configured LLM providers |
| `tools` | List available tools |
| `plugins` | List loaded plugins |
| `skills` | List loaded skills |
| `prompt` / `prompt-set` | Show or customize the REPL prompt format |
| `setup` | Interactive setup wizard |

## Scripts

| Script | Purpose |
|--------|---------|
| `start.sh` | **Daily driver** — start gateway (Docker or local), interactive shell (local or Docker). Reads `docker-compose/.env` for config. Use `--force-build` to rebuild Docker images. |
| `quickstart.sh` | **First-time Docker setup** — clones, builds image, starts stack, pulls Ollama if needed. Use `--force-build` to rebuild, `--reconfigure` to re-run interactive setup. |
| `setup.sh` | **First-time developer setup** — installs Java 21, builds from source, launches shell or gateway. |
| `install.sh` | **One-shot installer** — `curl -fsSL https://jaiclaw.io/install.sh \| bash`. Downloads the CLI jar from Nexus, installs to `~/.jaiclaw/`, offers SDKMAN-based Java install if missing. |

## Building from Source

```bash
export JAVA_HOME=$HOME/.sdkman/candidates/java/21.0.9-oracle

./mvnw compile                          # compile
./mvnw test                             # run tests (44+ modules, 900+ Spock specs)
./mvnw package -DskipTests              # package as JARs
./mvnw install -DskipTests              # install to local Maven repo
```

## Docker Images

Two modules produce Docker images via [Eclipse JKube](https://eclipse.dev/jkube/):

```bash
./mvnw package k8s:build -pl jaiclaw-gateway-app -am -Pk8s -DskipTests
./mvnw package k8s:build -pl jaiclaw-shell -am -Pk8s -DskipTests
```

Multi-arch (`linux/amd64` + `linux/arm64`) images use `eclipse-temurin:21-jre` base and follow `io.jaiclaw/<module>:<version>` naming.

## Documentation

| Document | Description |
|----------|-------------|
| [What Is Agentic AI?](docs/user/WHAT-IS-AGENTIC-AI.md) | Plain-English explainer for non-technical audiences |
| [Architecture](docs/dev/ARCHITECTURE.md) | Module graph, message flow, deployment patterns |
| [Operations Guide](docs/user/OPERATIONS.md) | Running, configuring, deploying, full env var reference |
| [Production Deployment](docs/user/PRODUCTION-DEPLOYMENT.md) | Kubernetes, JKube, Helm, secrets, observability, security hardening |
| [From Personal to Enterprise](docs/user/JAICLAW-FROM-PERSONAL-TO-ENTERPRISE.md) | Five-level spectrum from personal assistant to multi-tenant SaaS |
| [Agentic Workflow](docs/dev/AGENTIC-WORKFLOW.md) | Tool loop, human-in-the-loop, context compaction, memory |
| [Project Scaffolder](tools/jaiclaw-project-scaffolder/README.md) | Generate new JaiClaw projects from a YAML manifest |
| [Examples Guide](docs/user/EXAMPLES.md) | Detailed walkthrough of all 43 example applications |
| [Feature Comparison](docs/dev/FEATURE-COMPARISON.md) | OpenClaw vs JaiClaw vs Embabel — full feature matrix |
| [Telegram Setup](docs/user/TELEGRAM-SETUP.md) | Step-by-step Telegram bot configuration |
| [Gemma 4 Hardware Guide](docs/user/GEMMA4-HARDWARE-GUIDE.md) | Performance benchmarks, VRAM requirements, model selection for local Gemma 4 |

## Contributing

Contributions are welcome! Whether it's bug reports, feature requests, documentation improvements, or code — we appreciate it all.

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/my-feature`)
3. Commit your changes
4. Push to the branch and open a Pull Request

Please open an issue first for large changes so we can discuss the approach.

## License

Licensed under the [Apache License, Version 2.0](LICENSE).
