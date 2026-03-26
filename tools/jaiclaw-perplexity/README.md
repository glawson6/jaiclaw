# jaiclaw-perplexity

Perplexity AI CLI and LLM tool. Wraps all three Perplexity APIs (Sonar, Search, Agent) as Spring Shell commands and JaiClaw tool callbacks.

## Design Goals

- **Library by default** — plugs into JaiClaw as a standard module with tools the LLM can call (`perplexity_search`, `perplexity_web_search`, `perplexity_research`)
- **Standalone with profile** — build with `-Pstandalone` for an executable JAR with Spring Shell REPL
- **Three APIs** — Sonar (chat/completions), Search (raw web results), Agent (deep research)
- **Terminal image rendering** — iTerm2 OSC 1337 inline images, chafa fallback, text-only fallback
- **Streaming** — interactive chat mode streams responses token-by-token via SSE

## Prerequisites

- Java 21
- A Perplexity API key — get one at https://www.perplexity.ai/settings/api

Set the API key as an environment variable:

```bash
export PERPLEXITY_API_KEY=pplx-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
```

## Build

### Library (default)

```bash
./mvnw install -pl jaiclaw-perplexity -DskipTests
```

Produces a regular JAR. When on the classpath of `jaiclaw-gateway-app` or `jaiclaw-shell`, `PerplexityAutoConfiguration` auto-registers tools into the `ToolRegistry`.

### Standalone (`-Pstandalone`)

```bash
./mvnw package -pl jaiclaw-perplexity -am -Pstandalone -DskipTests
```

Produces an executable fat JAR with Spring Shell. Adds Spring AI Anthropic for use as a standalone CLI.

## Run (Standalone)

```bash
java -jar jaiclaw-perplexity/target/jaiclaw-perplexity-0.1.0-SNAPSHOT.jar
```

This drops you into a Spring Shell REPL where all `pplx` commands are available.

### Non-interactive (single command)

Spring Shell supports running a single command and exiting:

```bash
java -jar jaiclaw-perplexity/target/jaiclaw-perplexity-0.1.0-SNAPSHOT.jar \
  "pplx ask 'What is quantum computing?'"
```

```bash
java -jar jaiclaw-perplexity/target/jaiclaw-perplexity-0.1.0-SNAPSHOT.jar \
  "pplx search 'JaiClaw framework' --num-results 5"
```

```bash
java -jar jaiclaw-perplexity/target/jaiclaw-perplexity-0.1.0-SNAPSHOT.jar \
  "pplx research 'Compare React vs Vue in 2026' --preset deep-research"
```

## Commands

| Command | Description |
|---------|-------------|
| `pplx ask <query>` | One-shot Sonar query with cited answer |
| `pplx search <query>` | Raw web search (Search API) — ranked results without LLM synthesis |
| `pplx research <query>` | Deep research via Agent API — multi-step investigation |
| `pplx chat` | Interactive multi-turn chat with streaming |
| `pplx models` | List available models and presets |
| `pplx config` | Show current configuration |

### `pplx ask`

```bash
pplx ask "What is quantum computing?" --model sonar-pro --recency month --images
```

| Option | Default | Description |
|--------|---------|-------------|
| `--model` | `sonar-pro` | Sonar model: `sonar`, `sonar-pro`, `sonar-reasoning`, `sonar-reasoning-pro` |
| `--domains` | — | Comma-separated domain filter (e.g. `wikipedia.org,arxiv.org`) |
| `--recency` | — | Recency filter: `month`, `week`, `day`, `hour` |
| `--images` | `false` | Include images in results |
| `--format` | `text` | Output format: `text` or `json` |

### `pplx search`

```bash
pplx search "latest Java 21 features" --num-results 5 --recency week
```

| Option | Default | Description |
|--------|---------|-------------|
| `--num-results` | `10` | Number of results to return |
| `--recency` | — | Recency filter: `month`, `week`, `day`, `hour` |
| `--domains` | — | Comma-separated domain filter |

### `pplx research`

```bash
pplx research "What are the security implications of WebAssembly?" --preset deep-research
```

| Option | Default | Description |
|--------|---------|-------------|
| `--preset` | `pro-search` | Research depth: `fast-search`, `pro-search`, `deep-research`, `advanced` |
| `--model` | — | Model override |

### `pplx chat`

Enters an interactive multi-turn chat session with streaming responses.

```bash
pplx chat --model sonar-pro --system "You are a helpful coding assistant"
```

In-chat commands:

| Command | Description |
|---------|-------------|
| `/exit` | Leave chat |
| `/clear` | Reset conversation history |
| `/images on\|off` | Toggle image rendering |
| `/model <name>` | Switch model mid-conversation |

## Configuration

All settings can be configured via environment variables or `application.yml`:

| Property | Env Variable | Default | Description |
|----------|-------------|---------|-------------|
| `perplexity.api-key` | `PERPLEXITY_API_KEY` | — | API key (required) |
| `perplexity.default-model` | `PERPLEXITY_MODEL` | `sonar-pro` | Default Sonar model |
| `perplexity.default-preset` | `PERPLEXITY_PRESET` | `pro-search` | Default Agent preset |
| `perplexity.max-tokens` | `PERPLEXITY_MAX_TOKENS` | `4096` | Max response tokens |
| `perplexity.temperature` | `PERPLEXITY_TEMPERATURE` | `0.2` | Sampling temperature |
| `perplexity.images-enabled` | `PERPLEXITY_IMAGES` | `false` | Enable image rendering globally |

## Terminal Image Rendering

When `--images` is used (or `perplexity.images-enabled=true`), images from Perplexity responses are rendered inline in the terminal. The renderer auto-detects the best available protocol:

1. **iTerm2 / WezTerm** — inline images via OSC 1337 escape sequences (best quality)
2. **chafa** — renders images as colored Unicode characters (install with `brew install chafa`)
3. **Text-only** — prints `[Image: description] URL` as a fallback

## LLM Tools (Library Mode)

When the JAR is on the classpath alongside JaiClaw, these tools are registered in `ToolRegistry` and available to the LLM agent:

| Tool | Description |
|------|-------------|
| `perplexity_search` | Search the web with Perplexity AI and get cited answers |
| `perplexity_web_search` | Raw web search returning ranked results without LLM synthesis |
| `perplexity_research` | Deep multi-step research on complex topics |

All tools are in the `perplexity` section and available in the `FULL` tool profile.

## Tests

```bash
./mvnw test -pl jaiclaw-perplexity -o
```

26 tests across 3 Spock specs:

- `PerplexityClientSpec` — HTTP client request/response handling, error cases
- `TerminalImageRendererSpec` — protocol detection, rendering fallbacks
- `PerplexityAutoConfigurationSpec` — tool registration, definitions, profile filtering
