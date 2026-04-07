# Gemma 4 Local Example

Conversational chatbot running Google Gemma 4 locally via Ollama, demonstrating function calling with a fully open-source model. Includes both a REST chat API and an interactive shell.

## What This Demonstrates

- Running **Gemma 4** (Google's open model, Apache 2.0) locally via **Ollama**
- **Native function calling** with Gemma 4 E4B+ variants
- **Two interfaces** — REST API (`/api/chat`) and interactive Spring Shell CLI
- Zero cloud API dependencies — fully offline-capable
- Custom tool registration (current_time, calculate)

## Architecture

```
┌───────────────────────────────────────────────────────────  ┐
│                   GEMMA 4 LOCAL APP                         │
│                (standalone Spring Boot)                     │
├──────────────────┬───────���────────────────────────────────┤
│ Gateway          │  REST API (/api/chat, /api/health)       │
├──────────────────┼──────────────���─────────────────────────┤
│ Shell            │  Spring Shell CLI (interactive REPL)     │
├──────────────────┼─────────────────────────────���──────────┤
│ Agent Runtime    │  AgentRuntime → Ollama → Gemma 4         │
├──────────────────┼─────────────────────────���──────────────┤
│ Custom Tools     │  [CurrentTimeTool]  [CalculateTool]      │
├───��─────────────┼────────────────────────────────────────  ┤
│ Core             │  jaiclaw-core (records, SPI)             │
└──────────────────┴─────────────���──────────────────────────┘

Data flow:
  User ──► Shell or REST API ──► AgentRuntime ─��► Ollama (localhost:11434)
                                      │                    │
                                      ▼                    ▼
                                CurrentTimeTool      Gemma 4 E4B
                                CalculateTool        (function calling)
```

## Prerequisites

- Java 21+
- JaiClaw built and installed (`./mvnw install -DskipTests` from project root)
- **Ollama** installed locally OR Docker

> **Hardware guidance:** See the [Gemma 4 Hardware Guide](../../docs/GEMMA4-HARDWARE-GUIDE.md) for VRAM requirements, expected inference speeds, and model selection advice for your specific hardware.

## Gemma 4 Ollama Models

All variants support text and image input. "E" prefix = effective parameters (optimized for edge devices). Source: [ollama.com/library/gemma4/tags](https://ollama.com/library/gemma4/tags)

### Recommended (default quantization — best size/quality tradeoff)

| Tag | Download | Context | Notes |
|-----|----------|---------|-------|
| `gemma4:e2b` | 7.2 GB | 128K | Smallest, fast inference, limited tool use |
| `gemma4:e4b` | 9.6 GB | 128K | **Default** — good balance, reliable function calling |
| `gemma4:26b` | 18 GB | 256K | 26B MoE (4B active), strong reasoning |
| `gemma4:31b` | 20 GB | 256K | 31B Dense, best quality, needs beefy GPU |
| `gemma4:latest` | 9.6 GB | 128K | Alias for `e4b` |

### All quantization variants

| Tag | Download | Context | Quantization |
|-----|----------|---------|-------------|
| `gemma4:e2b-it-q4_K_M` | 7.2 GB | 128K | Q4_K_M (same as `e2b`) |
| `gemma4:e2b-it-q8_0` | 8.1 GB | 128K | Q8 — higher precision |
| `gemma4:e2b-it-bf16` | 10 GB | 128K | BF16 — full precision |
| `gemma4:e4b-it-q4_K_M` | 9.6 GB | 128K | Q4_K_M (same as `e4b`) |
| `gemma4:e4b-it-q8_0` | 12 GB | 128K | Q8 — higher precision |
| `gemma4:e4b-it-bf16` | 16 GB | 128K | BF16 — full precision |
| `gemma4:26b-a4b-it-q4_K_M` | 18 GB | 256K | Q4_K_M (same as `26b`) |
| `gemma4:26b-a4b-it-q8_0` | 28 GB | 256K | Q8 — higher precision |
| `gemma4:31b-it-q4_K_M` | 20 GB | 256K | Q4_K_M (same as `31b`) |
| `gemma4:31b-it-q8_0` | 34 GB | 256K | Q8 — higher precision |
| `gemma4:31b-it-bf16` | 63 GB | 256K | BF16 — full precision |
| `gemma4:31b-cloud` | — | 256K | Cloud-hosted variant |

The default is `gemma4:e4b` (9.6 GB download). Pull any variant with `ollama pull gemma4:<tag>`.

## Setup — Option A: Ollama (Recommended)

```bash
# Install Ollama (macOS)
brew install ollama

# Start Ollama
ollama serve

# Pull Gemma 4 (in another terminal)
ollama pull gemma4:e4b

# Verify
ollama list | grep gemma4
```

## Setup — Option B: Docker Compose

```bash
cd jaiclaw-examples/gemma4-local

# Start Ollama + pull Gemma 4 model
docker compose up -d

# Watch the model download progress
docker logs -f gemma4-pull
```

> **Note:** GPU passthrough in Docker requires Linux + NVIDIA. On macOS, use Option A — Ollama uses Metal acceleration natively.

## Build & Run

### Interactive Shell (recommended for trying it out)

```bash
cd jaiclaw-examples/gemma4-local
export JAVA_HOME=$HOME/.sdkman/candidates/java/21.0.9-oracle
../../mvnw spring-boot:run
```

The app starts with an interactive shell prompt. Type a message and press Enter:

```
shell:> chat What time is it in Tokyo?
shell:> chat Calculate 15% tip on a $85 dinner bill
shell:> chat Summarize the benefits of local LLMs
```

### Shell Commands

| Command | Description |
|---------|-------------|
| `chat <message>` | Send a message to the Gemma 4 agent |
| `new-session` | Start a fresh chat session |
| `sessions` | List active sessions |
| `session-history` | Show messages in the current session |
| `status` | Show system status |
| `tools` | List available tools |
| `skills` | List loaded skills |

### REST API

While the shell is running, the chat API is also available on port 8080:

```bash
# Chat with Gemma 4 via REST
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -H "X-API-Key: $(cat ~/.jaiclaw/api-key)" \
  -d '{"content": "What time is it in Tokyo?"}'

# Test the calculator tool
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -H "X-API-Key: $(cat ~/.jaiclaw/api-key)" \
  -d '{"content": "Calculate 15% tip on a $85 dinner bill"}'

# Health check
curl http://localhost:8080/api/health
```

### Using a Different Model Size

```bash
# Use the smaller E2B model (less VRAM, faster responses)
OLLAMA_MODEL=gemma4:e2b ../../mvnw spring-boot:run

# Use the larger 26B MoE model (better quality, needs ~20GB VRAM)
OLLAMA_MODEL=gemma4:26b ../../mvnw spring-boot:run
```

### Using a Remote Ollama Server

```bash
OLLAMA_BASE_URL=http://your-server:11434 ../../mvnw spring-boot:run
```

## Configuration

| Variable | Default | Description |
|----------|---------|-------------|
| `OLLAMA_BASE_URL` | `http://localhost:11434` | Ollama API endpoint |
| `OLLAMA_MODEL` | `gemma4:e4b` | Gemma 4 model variant |
| `OLLAMA_TEMPERATURE` | `0.7` | Response creativity (0.0–1.0) |
| `OLLAMA_NUM_CTX` | `8192` | Context window size |

## Troubleshooting

**"Connection refused on port 11434"** — Ollama isn't running. Start it with `ollama serve`.

**"Model not found"** — Run `ollama pull gemma4:e4b` first.

**"Out of memory"** — Try a smaller model: `OLLAMA_MODEL=gemma4:e2b`.

**Slow first response** — Ollama loads the model into memory on first request. Subsequent responses are faster.
