# Ollama Tuning Guide for JaiClaw

Comprehensive reference for configuring Ollama-backed LLMs in JaiClaw, including all tunable parameters, their impact on behavior and resource usage, and model-specific tuning guidance for Google Gemma 4.

---

## Table of Contents

1. [Spring AI Ollama Configuration](#spring-ai-ollama-configuration)
2. [Token Generation Parameters](#token-generation-parameters)
3. [Context Window Configuration](#context-window-configuration)
4. [Sampling Parameters](#sampling-parameters)
5. [Repetition Control](#repetition-control)
6. [Advanced Parameters](#advanced-parameters)
7. [JaiClaw application.yml Reference](#jaiclaw-applicationyml-reference)
8. [Gemma 4 Tuning Guide](#gemma-4-tuning-guide)
9. [Troubleshooting](#troubleshooting)

---

## Spring AI Ollama Configuration

JaiClaw uses Spring AI's Ollama integration. All Ollama parameters are configured under the `spring.ai.ollama.chat.options` prefix in your `application.yml`. Spring AI maps these to Ollama's native API options.

### Enabling Ollama

```yaml
spring:
  ai:
    model:
      chat: ollama                              # select Ollama as the chat provider
    ollama:
      enabled: true
      base-url: ${OLLAMA_BASE_URL:http://localhost:11434}
      chat:
        options:
          model: ${OLLAMA_MODEL:gemma4:e4b}     # model tag from Ollama library
```

### Environment Variable Overrides

Every property can be overridden via environment variables:

| Property | Env Var | Default |
|----------|---------|---------|
| `spring.ai.ollama.base-url` | `OLLAMA_BASE_URL` | `http://localhost:11434` |
| `spring.ai.ollama.chat.options.model` | `OLLAMA_MODEL` | (none — required) |
| `spring.ai.ollama.chat.options.temperature` | `OLLAMA_TEMPERATURE` | `0.8` |
| `spring.ai.ollama.chat.options.num-ctx` | `OLLAMA_NUM_CTX` | `2048` |
| `spring.ai.ollama.chat.options.num-predict` | `OLLAMA_NUM_PREDICT` | `128` |

---

## Token Generation Parameters

### num-predict (Max Output Tokens)

Controls the maximum number of tokens the model will generate in a single response.

```yaml
spring:
  ai:
    ollama:
      chat:
        options:
          num-predict: 4096
```

| Value | Behavior |
|-------|----------|
| `128` | **Ollama default.** Limits output to 128 tokens. Responses get cut off mid-sentence for anything beyond a short answer. This is almost certainly too low for agentic use. |
| `1024` | Good for concise responses — chat, Q&A, short summaries. |
| `2048` | Recommended starting point for JaiClaw agents. Enough for tool call responses and structured output. |
| `4096` | Recommended for code generation, detailed explanations, multi-step reasoning. |
| `8192` | Long-form content — documents, reports, multi-tool workflows. |
| `-1` | **Unlimited.** Model generates until it emits a stop token or fills the context window. Use with caution — can produce very long outputs and consume significant time/memory. |
| `-2` | **Fill context.** Generates tokens until the context window (`num-ctx`) is completely filled. Rarely useful in practice. |

**Impact on JaiClaw agents:**

- **Tool calling**: When the model generates a tool call, `num-predict` limits the total JSON output. If set too low, tool call JSON may be truncated, causing parse errors in `AgentRuntime`. Set to at least `2048` for tool-using agents.
- **Multi-turn conversations**: Each agent turn generates a response. The limit applies per turn, not across the conversation.
- **Memory/speed**: Higher values don't consume more VRAM — they only affect how long generation can run. The KV cache grows with `num-ctx`, not `num-predict`.

### stop (Stop Sequences)

Custom strings that cause the model to stop generating. Useful for controlling output format.

```yaml
spring:
  ai:
    ollama:
      chat:
        options:
          stop:
            - "<|end|>"
            - "```"
```

---

## Context Window Configuration

### num-ctx (Context Window Size)

The total number of tokens (input + output) the model can process in a single request. This is the most impactful parameter for both capability and resource usage.

```yaml
spring:
  ai:
    ollama:
      chat:
        options:
          num-ctx: 8192
```

| Value | Use Case | VRAM Impact |
|-------|----------|-------------|
| `2048` | **Ollama default.** Short conversations, simple Q&A. | Minimal |
| `4096` | Basic chat with some history. Adequate for simple agents. | Low |
| `8192` | **Recommended for JaiClaw.** Good balance of context and resources. Fits system prompt + conversation history + tool results. | Moderate |
| `16384` | Long conversations, document analysis, multi-tool workflows. | Significant |
| `32768` | Extended context work — large document Q&A, code review. | High |
| `131072` | Full Gemma 4 E4B capability (128K). Only if you have the VRAM. | Very high |

**How num-ctx affects behavior:**

1. **System prompt + skills**: JaiClaw's system prompt with skills can consume 500–5,000+ tokens. If `num-ctx` is too small, conversation history gets truncated.
2. **Tool results**: Tool call results (especially `web_fetch`, `file_read`) can be large. Each result consumes context tokens.
3. **Conversation memory**: With `num-ctx: 2048`, you may only fit 2–3 exchanges before the model loses earlier context.
4. **VRAM usage**: The KV (key-value) cache scales linearly with `num-ctx`. Doubling the context roughly doubles the KV cache memory. This is the primary driver of VRAM usage beyond the model weights themselves.

**VRAM budget rule of thumb:**

```
Total VRAM = Model weights + KV cache (scales with num-ctx) + ~2 GB overhead
```

For Gemma 4 E4B (Q4 quantization):
- `num-ctx: 4096` → ~6 GB total
- `num-ctx: 8192` → ~7 GB total
- `num-ctx: 32768` → ~10 GB total
- `num-ctx: 131072` → ~20 GB total

---

## Sampling Parameters

These parameters control how the model selects the next token. They directly affect response creativity, consistency, and quality.

### temperature

Controls randomness. Lower = more deterministic, higher = more creative.

```yaml
spring:
  ai:
    ollama:
      chat:
        options:
          temperature: 0.7
```

| Value | Behavior |
|-------|----------|
| `0.0` | Fully deterministic — always picks the highest-probability token. Best for structured output, tool calls, factual Q&A. |
| `0.3` | Low creativity. Good for code generation, JSON output, following instructions precisely. |
| `0.7` | **Recommended for JaiClaw agents.** Good balance of coherence and natural variation. |
| `0.8` | Ollama default. Slightly more creative. |
| `1.0` | High creativity. Good for brainstorming, creative writing. May produce unexpected tool calls. |
| `>1.0` | Very high randomness. Outputs become increasingly incoherent. Not recommended. |

**For agentic use (tool calling)**: Use `0.0`–`0.3` for reliability. Higher temperatures can cause the model to hallucinate tool names or produce malformed JSON.

### top-k

Limits token selection to the top K most probable tokens before sampling.

```yaml
spring:
  ai:
    ollama:
      chat:
        options:
          top-k: 40
```

| Value | Behavior |
|-------|----------|
| `1` | Greedy decoding — equivalent to `temperature: 0`. |
| `10` | Very focused. Only considers the 10 most likely tokens. |
| `40` | **Ollama default.** Good balance. |
| `100` | More diverse outputs. |

### top-p (Nucleus Sampling)

Selects tokens from the smallest set whose cumulative probability exceeds `top-p`.

```yaml
spring:
  ai:
    ollama:
      chat:
        options:
          top-p: 0.9
```

| Value | Behavior |
|-------|----------|
| `0.5` | Conservative — only high-probability tokens. |
| `0.9` | **Ollama default.** Recommended. Filters out very unlikely tokens while keeping diversity. |
| `1.0` | No filtering — considers all tokens. |

### min-p

Minimum probability threshold. Tokens below this probability (relative to the top token) are filtered out.

```yaml
spring:
  ai:
    ollama:
      chat:
        options:
          min-p: 0.05
```

| Value | Behavior |
|-------|----------|
| `0.0` | **Default.** Disabled. |
| `0.05` | Filters tokens with less than 5% of the top token's probability. Good for reducing nonsense. |
| `0.1` | More aggressive filtering. |

### seed

Sets a random seed for reproducible outputs. Same seed + same input = same output (with `temperature > 0`).

```yaml
spring:
  ai:
    ollama:
      chat:
        options:
          seed: 42
```

Useful for testing and debugging. Set to `0` (default) for non-deterministic behavior.

---

## Repetition Control

### repeat-penalty

Penalizes tokens that have already appeared in the output, reducing repetition.

```yaml
spring:
  ai:
    ollama:
      chat:
        options:
          repeat-penalty: 1.1
```

| Value | Behavior |
|-------|----------|
| `1.0` | No penalty — repetition allowed. |
| `1.1` | **Ollama default.** Mild penalty. Recommended. |
| `1.3` | Strong penalty. May cause the model to avoid necessary repetition (e.g., JSON keys). |
| `>1.5` | Aggressive. Can degrade output quality. |

**Warning**: High repeat-penalty can break structured output (JSON, tool calls) where repeating keys/patterns is required.

### repeat-last-n

How many tokens to look back when applying repeat-penalty.

```yaml
spring:
  ai:
    ollama:
      chat:
        options:
          repeat-last-n: 64
```

| Value | Behavior |
|-------|----------|
| `0` | Disabled — no repetition penalty applied. |
| `64` | **Ollama default.** Looks at last 64 tokens. |
| `-1` | Looks at the entire context window. Expensive for large contexts. |

---

## Advanced Parameters

### Mirostat (Adaptive Sampling)

Mirostat dynamically adjusts sampling to maintain a target "perplexity" (surprise level). It can produce more natural-feeling text than fixed temperature/top-k/top-p.

```yaml
spring:
  ai:
    ollama:
      chat:
        options:
          mirostat: 2          # 0=disabled, 1=Mirostat, 2=Mirostat 2.0
          mirostat-eta: 0.1    # learning rate (default: 0.1)
          mirostat-tau: 5.0    # target perplexity (default: 5.0)
```

| Parameter | Default | Description |
|-----------|---------|-------------|
| `mirostat` | `0` | Algorithm version. `2` (Mirostat 2.0) is recommended if enabled. |
| `mirostat-eta` | `0.1` | Learning rate. Lower = slower adaptation, more stable. |
| `mirostat-tau` | `5.0` | Target perplexity. Lower = more focused/predictable output. Higher = more creative. |

**Note**: When Mirostat is enabled (`mirostat: 1` or `2`), `temperature`, `top-k`, and `top-p` are ignored.

### keep-alive

Controls how long the model stays loaded in VRAM after the last request.

```yaml
spring:
  ai:
    ollama:
      chat:
        options:
          keep-alive: "10m"    # 10 minutes (default: 5m)
```

| Value | Behavior |
|-------|----------|
| `"5m"` | **Default.** Unloads after 5 minutes of inactivity. |
| `"30m"` | Keeps model warm for 30 minutes. Good for interactive use. |
| `"-1"` | Never unload. Model stays in VRAM until Ollama is restarted. |
| `"0"` | Unload immediately after each request. Saves VRAM but slow. |

For JaiClaw agents that make multiple tool calls per conversation, set to at least `"10m"` to avoid repeated model loading.

---

## JaiClaw application.yml Reference

### Minimal Ollama Configuration

```yaml
spring:
  ai:
    model:
      chat: ollama
    ollama:
      enabled: true
      base-url: ${OLLAMA_BASE_URL:http://localhost:11434}
      chat:
        options:
          model: ${OLLAMA_MODEL:gemma4:e4b}
```

### Recommended Agent Configuration

```yaml
spring:
  ai:
    model:
      chat: ollama
    ollama:
      enabled: true
      base-url: ${OLLAMA_BASE_URL:http://localhost:11434}
      chat:
        options:
          model: ${OLLAMA_MODEL:gemma4:e4b}
          temperature: 0.3            # low for reliable tool calling
          num-ctx: 8192               # enough for system prompt + history + tool results
          num-predict: 4096           # enough for tool call JSON + explanations
          top-k: 40
          top-p: 0.9
          repeat-penalty: 1.1
          keep-alive: "10m"
```

### High-Context Document Analysis

```yaml
spring:
  ai:
    ollama:
      chat:
        options:
          model: gemma4:e4b
          temperature: 0.0
          num-ctx: 32768
          num-predict: 8192
          keep-alive: "30m"
```

---

## Gemma 4 Tuning Guide

Google Gemma 4 is a family of open models (Apache 2.0) with native function calling, structured JSON output, vision support, and large context windows — making it one of the best local models for JaiClaw agentic workflows.

### Model Variants

| Variant | Tag | Parameters | Active Params | Context | Download | Min VRAM | Best For |
|---------|-----|-----------|---------------|---------|----------|----------|----------|
| **E2B** | `gemma4:e2b` | 2.3B | 2.3B | 128K | ~7.2 GB | 4 GB | Edge devices, quick chat, resource-constrained |
| **E4B** | `gemma4:e4b` | 4.5B | 4.5B | 128K | ~9.6 GB | 6 GB | **Recommended default.** Best quality/resource ratio |
| **26B MoE** | `gemma4:26b` | 26B | 3.8B | 256K | ~18 GB | 10 GB | High quality with MoE efficiency |
| **31B Dense** | `gemma4:31b` | 31B | 31B | 256K | ~20 GB | 20 GB | Highest quality, production agentic use |

**Architecture notes:**
- **E2B/E4B** use Per-Layer Embeddings (PLE) — achieves representational depth of larger models with low memory usage.
- **26B** is a Mixture-of-Experts (MoE) model — 26B total parameters but only activates ~3.8B during inference, making it surprisingly efficient.
- **31B Dense** — all parameters active during inference. Highest quality but highest resource requirements.

### Recommended JaiClaw Configuration by Variant

#### Gemma 4 E4B (Default — 6 GB VRAM)

```yaml
spring:
  ai:
    model:
      chat: ollama
    ollama:
      enabled: true
      base-url: ${OLLAMA_BASE_URL:http://localhost:11434}
      chat:
        options:
          model: gemma4:e4b
          temperature: 0.3
          num-ctx: 8192
          num-predict: 4096
          top-p: 0.9
          keep-alive: "10m"
```

#### Gemma 4 E2B (Minimal — 4 GB VRAM)

```yaml
spring:
  ai:
    ollama:
      chat:
        options:
          model: gemma4:e2b
          temperature: 0.2            # lower for small model reliability
          num-ctx: 4096               # conservative context for low VRAM
          num-predict: 2048
          top-p: 0.85
          keep-alive: "5m"
```

#### Gemma 4 26B MoE (High Quality — 10+ GB VRAM)

```yaml
spring:
  ai:
    ollama:
      chat:
        options:
          model: gemma4:26b
          temperature: 0.3
          num-ctx: 16384              # larger context, MoE handles it efficiently
          num-predict: 4096
          top-p: 0.9
          keep-alive: "15m"           # MoE model loads slower, keep warm
```

#### Gemma 4 31B Dense (Production — 20+ GB VRAM)

```yaml
spring:
  ai:
    ollama:
      chat:
        options:
          model: gemma4:31b
          temperature: 0.3
          num-ctx: 16384
          num-predict: 8192           # 31B can produce longer, higher-quality output
          top-p: 0.9
          keep-alive: "30m"           # large model, expensive to reload
```

### Function Calling Configuration

Gemma 4 has **native function calling** — no prompt engineering needed. JaiClaw's `AgentRuntime` uses Spring AI's tool calling protocol, which Ollama maps to Gemma 4's native format.

**Key settings for reliable tool calling:**

```yaml
spring:
  ai:
    ollama:
      chat:
        options:
          model: gemma4:e4b
          temperature: 0.0            # deterministic for tool calls
          num-predict: 4096           # enough for complex tool call JSON
          num-ctx: 8192               # system prompt + tool definitions + history
```

**Best practices:**

1. **Use temperature 0.0–0.3** for tool calling. Higher temperatures can cause malformed JSON or hallucinated tool names.
2. **Keep tool count under 15** for best accuracy. Gemma 4 handles tool selection well, but accuracy degrades with too many tools. Use JaiClaw's `ToolProfile` filtering to limit exposed tools.
3. **Set num-predict >= 2048** — tool call JSON can be verbose, especially with nested arguments.
4. **Do not use the `/v1` (OpenAI-compatible) endpoint** for tool calling. Use the native Ollama API URL (`http://localhost:11434`).
5. **Disable reasoning/thinking mode** if available — it can interfere with the expected tool call JSON format.

### VRAM Planning

Use this table to plan your deployment. Total VRAM = model weights + KV cache + overhead.

| Variant | Q4 Weights | KV Cache @ 4K | KV Cache @ 8K | KV Cache @ 32K | KV Cache @ 128K |
|---------|-----------|---------------|---------------|----------------|-----------------|
| E2B | ~2 GB | ~0.5 GB | ~1 GB | ~3 GB | ~12 GB |
| E4B | ~3 GB | ~0.5 GB | ~1 GB | ~4 GB | ~14 GB |
| 26B MoE | ~10 GB | ~1 GB | ~2 GB | ~5 GB | ~16 GB |
| 31B Dense | ~18 GB | ~2 GB | ~4 GB | ~10 GB | ~22 GB |

Add ~2 GB overhead for Ollama runtime.

**Mac-specific notes:**
- Apple Silicon Macs share memory between CPU and GPU — your total unified memory is available for model + KV cache.
- M1/M2 (8 GB): E2B with `num-ctx: 4096`
- M1/M2 Pro (16 GB): E4B with `num-ctx: 8192`, or 26B MoE with `num-ctx: 4096`
- M1/M2 Max (32 GB): 26B MoE with `num-ctx: 32768`, or 31B with `num-ctx: 8192`
- M1/M2 Ultra (64+ GB): 31B with `num-ctx: 65536`+

### Vision Support

Gemma 4 supports image input with a configurable visual token budget. Supported budgets: 70, 140, 280, 560, 1120 tokens per image.

Higher token budgets give the model more detail from images but consume more of the context window. For JaiClaw's `media` extension, the default budget is usually sufficient.

### Model Selection Guide

| Use Case | Recommended Variant | Why |
|----------|-------------------|-----|
| Development/testing | E4B | Fast, low resources, good tool calling |
| Chat bot (low resources) | E2B | Fits on 4 GB, adequate for simple conversations |
| Production agent (balanced) | 26B MoE | Only activates 3.8B params — fast inference with 26B quality |
| Production agent (max quality) | 31B Dense | Highest accuracy on tool use benchmarks (86.4% on τ2-bench) |
| Document analysis | 26B or 31B | Larger context windows (256K) for long documents |
| Code generation | E4B or 31B | E4B for speed, 31B for quality |

### Benchmark Reference

| Benchmark | E2B | E4B | 26B MoE | 31B Dense |
|-----------|-----|-----|---------|-----------|
| MMLU | — | — | — | — |
| τ2-bench (agentic tool use) | — | — | 85.5% | 86.4% |
| Function calling | Good | Good | Very good | Excellent |

---

## Troubleshooting

### Response gets cut off mid-sentence

**Cause**: `num-predict` is too low (default: 128).

**Fix**: Increase `num-predict` to at least 2048:
```yaml
spring.ai.ollama.chat.options.num-predict: 4096
```

### Model loses context / forgets earlier messages

**Cause**: `num-ctx` is too small for the conversation length.

**Fix**: Increase `num-ctx`. Check token usage in logs:
```yaml
spring.ai.ollama.chat.options.num-ctx: 16384
```

### Tool calls fail with JSON parse errors

**Cause**: `num-predict` too low (tool call JSON truncated) or `temperature` too high (malformed JSON).

**Fix**:
```yaml
spring:
  ai:
    ollama:
      chat:
        options:
          num-predict: 4096
          temperature: 0.0
```

### Out of memory / Ollama crashes

**Cause**: `num-ctx` too large for available VRAM.

**Fix**: Reduce `num-ctx` or use a smaller model variant. Check VRAM usage:
```bash
# macOS
sudo powermetrics --samplers gpu_power -i 1000 -n 1

# Linux (NVIDIA)
nvidia-smi
```

### Model takes a long time to start responding

**Cause**: Model was unloaded from VRAM and needs to reload.

**Fix**: Increase `keep-alive`:
```yaml
spring.ai.ollama.chat.options.keep-alive: "30m"
```

### Repetitive output

**Cause**: `repeat-penalty` too low or disabled.

**Fix**:
```yaml
spring:
  ai:
    ollama:
      chat:
        options:
          repeat-penalty: 1.2
          repeat-last-n: 128
```

### Exposing Ollama on the Network

By default, Ollama only listens on `127.0.0.1`. To expose it to other machines (e.g., for remote JaiClaw deployments):

```bash
# Stop Ollama, then restart bound to all interfaces
OLLAMA_HOST=0.0.0.0:11434 ollama serve
```

Then configure JaiClaw to connect:
```yaml
spring:
  ai:
    ollama:
      base-url: http://192.168.x.x:11434
```

---

## Sources

- [Ollama Modelfile Reference](https://docs.ollama.com/modelfile)
- [Ollama API Reference](https://ollama.readthedocs.io/en/api/)
- [Spring AI Ollama Chat Reference](https://docs.spring.io/spring-ai/reference/api/chat/ollama-chat.html)
- [Gemma 4 Announcement — Google Blog](https://blog.google/innovation-and-ai/technology/developers-tools/gemma-4/)
- [Gemma 4 on Ollama](https://ollama.com/library/gemma4)
- [Gemma 4 Hardware Requirements Guide](https://avenchat.com/blog/gemma-4-hardware-requirements)
- [Gemma 4 VRAM Requirements](https://gemma4guide.com/guides/gemma4-vram-requirements)
