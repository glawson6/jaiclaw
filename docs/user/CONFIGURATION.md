# Configuration Reference

> **Audience:** anyone running JaiClaw beyond the quickstart. For the
> first 10 minutes, see [GETTING-STARTED.md](GETTING-STARTED.md). For
> deep operational topics (deployment, k8s, profiles), see
> [OPERATIONS.md](OPERATIONS.md).

This page covers what to put in `application.yml` (or
`SPRING_APPLICATION_JSON`, or env vars) for the most common
deployment shapes.

## Minimal viable config

Everything below is optional except these five lines:

```yaml
jaiclaw:
  skills:
    allow-bundled: []           # don't ship ~26K tokens of skills per call
  identity:
    name: My Agent
spring:
  ai:
    model:
      chat: anthropic
    anthropic:
      api-key: ${ANTHROPIC_API_KEY:not-set}
```

That's a working JaiClaw — gateway boots, `/api/chat` accepts
requests, the agent calls Anthropic with no extra context.

## Provider configuration

JaiClaw delegates LLM calls to Spring AI. Each provider has a
namespace under `spring.ai.<provider>` and is selected via
`spring.ai.model.chat`. The provider's starter must be on the
classpath.

### Anthropic (most common)

```yaml
spring:
  ai:
    model:
      chat: anthropic
    anthropic:
      api-key: ${ANTHROPIC_API_KEY:not-set}
      base-url: ${ANTHROPIC_BASE_URL:https://api.anthropic.com}
      chat:
        options:
          model: ${ANTHROPIC_MODEL:claude-sonnet-4-5}
          max-tokens: 4096
```

**Gotcha:** Spring AI 1.1.x defaults to the retired
`claude-3-7-sonnet-latest` if `chat.options.model` is unset. Always
specify the model explicitly.

### OpenAI

```yaml
spring:
  ai:
    model:
      chat: openai
    openai:
      api-key: ${OPENAI_API_KEY:not-set}
      chat:
        options:
          model: ${OPENAI_MODEL:gpt-4o-mini}
```

### Google Gemini

```yaml
spring:
  ai:
    model:
      chat: vertex-ai-gemini
    vertex:
      ai:
        gemini:
          project-id: ${GCP_PROJECT_ID}
          location: us-central1
          chat:
            options:
              model: gemini-2.0-flash
```

### Ollama (local)

```yaml
spring:
  ai:
    model:
      chat: ollama
    ollama:
      base-url: ${OLLAMA_BASE_URL:http://localhost:11434}
      chat:
        options:
          model: ${OLLAMA_MODEL:gemma3:12b}
```

See [OLLAMA-TUNING-GUIDE.md](OLLAMA-TUNING-GUIDE.md) for parameter
tuning, and [GEMMA4-HARDWARE-GUIDE.md](GEMMA4-HARDWARE-GUIDE.md) for
VRAM budgets and model selection.

### MiniMax (via the Anthropic-compatible endpoint)

The cleanest way to use MiniMax is **as an Anthropic provider** with
a redirected base URL — don't add the MiniMax starter at the same
time as the Anthropic starter (they create competing model beans;
see CLAUDE.md for the three-layer architecture details):

```yaml
embabel:
  models:
    default-llm: claude-sonnet-4-5      # the Anthropic registered name

spring:
  ai:
    anthropic:
      api-key: ${MINIMAX_API_KEY}
      base-url: https://api.minimax.io/anthropic
      chat:
        options:
          model: M2-her                  # MiniMax model id sent in the request
```

### Other providers

Available via dedicated Spring Boot starters and the matching
`jaiclaw-starter-<provider>` BOM entry: AWS Bedrock, Azure OpenAI,
DeepSeek, Mistral, OCI GenAI. Add the `jaiclaw-starter-<provider>`
dependency and set `spring.ai.model.chat` to the provider key.

## Channel configuration recipes

The channel adapter you don't depend on adds nothing at runtime — the
starter is the on/off switch.

### Recipe 1 — Anthropic + Telegram (single-channel chat bot)

```xml
<dependency>
  <groupId>io.jaiclaw</groupId>
  <artifactId>jaiclaw-spring-boot-starter</artifactId>
</dependency>
<dependency>
  <groupId>io.jaiclaw</groupId>
  <artifactId>jaiclaw-channel-telegram</artifactId>
</dependency>
<dependency>
  <groupId>org.springframework.ai</groupId>
  <artifactId>spring-ai-starter-model-anthropic</artifactId>
</dependency>
```

```yaml
jaiclaw:
  skills:
    allow-bundled: []
  channels:
    telegram:
      enabled: true
      bot-token: ${TELEGRAM_BOT_TOKEN}
      mode: ${TELEGRAM_MODE:polling}     # 'polling' for dev, 'webhook' for prod

spring:
  ai:
    model:
      chat: anthropic
    anthropic:
      api-key: ${ANTHROPIC_API_KEY}
```

Full walkthrough — including how to set up the bot itself — is in
[TELEGRAM-SETUP.md](TELEGRAM-SETUP.md).

### Recipe 2 — OpenAI + Slack

```yaml
jaiclaw:
  channels:
    slack:
      enabled: true
      bot-token: ${SLACK_BOT_TOKEN}
      app-token: ${SLACK_APP_TOKEN}        # for socket-mode
      socket-mode: true
      verify-signature: true              # opt-in HMAC check

spring:
  ai:
    model:
      chat: openai
    openai:
      api-key: ${OPENAI_API_KEY}
```

### Recipe 3 — Ollama-only (local, no cloud)

```yaml
jaiclaw:
  skills:
    allow-bundled: []
  identity:
    name: Local Agent

spring:
  ai:
    model:
      chat: ollama
    ollama:
      base-url: http://localhost:11434
      chat:
        options:
          model: gemma3:12b
```

No channel adapter — drives the agent purely through `/api/chat`.
Useful for testing and CI.

### Recipe 4 — Multi-channel gateway

The gateway can listen on every adapter at once. Same wiring as the
single-channel recipes; just add more `jaiclaw.channels.X.enabled:
true` entries.

```yaml
jaiclaw:
  channels:
    telegram:
      enabled: true
      bot-token: ${TELEGRAM_BOT_TOKEN}
    slack:
      enabled: true
      bot-token: ${SLACK_BOT_TOKEN}
    discord:
      enabled: true
      bot-token: ${DISCORD_BOT_TOKEN}
      use-gateway: true
```

## Persistence

Three sessions storage modes, set via `jaiclaw.sessions.mode`:

| Mode | When to use |
|---|---|
| `memory` (default) | Single-process dev. Sessions die on restart. |
| `json-file` | Single-process dev/prod with restart-survivability. Path: `jaiclaw.sessions.json-file.path` |
| `redis` | Multi-replica prod. Requires `spring.data.redis.host`. |

The same modes are available for the artifact store, audit log, and
docstore — each independently configurable.

## Multi-tenancy mode

```yaml
jaiclaw:
  tenant:
    mode: single       # or 'multi'
    default-tenant-id: ${JAICLAW_TENANT_ID:default}    # override in production!
    strict-default-tenant-id: false  # set true to fail-fast on weak default
```

In `multi` mode the gateway resolves tenant from the JWT (configure
`jaiclaw.security.jwt.tenant-claim`); all storage is tenant-scoped.

**Production gotcha:** `default-tenant-id` defaults to the literal
string `"default"` for SINGLE mode. This is **predictable** — an
attacker who can supply tenant-id headers may probe under the
`"default:"` namespace. Override it to a high-entropy value in
production (UUID or random hex). Enable
`strict-default-tenant-id: true` to fail-fast on weak values at
startup.

## Skills

Whole topic. See [SKILLS.md](SKILLS.md) for the cost story
(default loads ~26K tokens per call) and the `allow-bundled` /
`workspace-dir` config.

## Security modes

```yaml
jaiclaw:
  security:
    mode: none           # or 'api-key' or 'jwt'
    api-key: ${JAICLAW_API_KEY:}    # for mode=api-key
    api-key-file: ${HOME}/.jaiclaw/api-key   # alternative
    jwt:
      secret: ${JAICLAW_JWT_SECRET}             # >= 32 chars for HS256
      issuer: jaiclaw
      tenant-claim: tenant_id
      role-claim: roles
    timing-safe-api-key: true                 # constant-time compare
    rate-limit:
      enabled: false                            # off by default
```

All three modes emit the same response headers (HSTS,
X-Frame-Options, Referrer-Policy, X-Content-Type-Options).

Optional `security-hardened` Spring profile flips on all hardening
flags at once. Use `SPRING_PROFILES_ACTIVE=security-hardened` in
production.

Full reference — including the rate-limit knobs and the six
individual hardening flags — in [OPERATIONS.md § Security](OPERATIONS.md#security).

## Environment-variable cheat sheet

Spring Boot binds every `jaiclaw.*` property to a `JAICLAW_*` env
var (uppercase, `.` → `_`, `-` → `_`). The shortest practical setup:

| Env var | Purpose |
|---|---|
| `ANTHROPIC_API_KEY` | Anthropic provider |
| `OPENAI_API_KEY` | OpenAI provider |
| `JAICLAW_API_KEY` | API-key auth mode |
| `JAICLAW_TENANT_DEFAULT_TENANT_ID` | Override SINGLE-mode tenant id |
| `GATEWAY_PORT` | Override default 8080 |
| `TELEGRAM_BOT_TOKEN` | Telegram channel |
| `SLACK_BOT_TOKEN`, `SLACK_APP_TOKEN` | Slack channel |
| `DISCORD_BOT_TOKEN` | Discord channel |
| `OLLAMA_BASE_URL` | Local Ollama endpoint |

Full env-var reference: [OPERATIONS.md § Environment Variables](OPERATIONS.md#environment-variables).
