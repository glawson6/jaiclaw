# Gemma 4 Performance & Hardware Guide

Hardware requirements, benchmark data, and model selection guidance for running Google Gemma 4 locally with JaiClaw and Ollama.

---

## TL;DR — Which Model Should I Use?

| Your Device | Recommended Model | Why |
|---|---|---|
| MacBook Air / 8GB laptop | `gemma4:e2b` | Fits in 8GB, fast inference, multimodal + audio |
| 16GB Mac / gaming PC | `gemma4:e4b` | Best balance — reliable function calling, 9.6 GB |
| RTX 3090 / 24GB Mac | `gemma4:26b` | MoE — 3x faster than 31B, near-same quality |
| RTX 5090 / 48GB+ Mac | `gemma4:31b` | Best open model under 70B, #3 on Arena AI |

> **Note:** When in doubt, start with `gemma4:e4b` (the default). It runs on most hardware and supports function calling out of the box.

---

## Model Overview

All four variants are Apache 2.0 licensed, support text + image input, and run via Ollama.

| Variant | Parameters | Active Params | Download (Q4) | Context | Key Capability |
|---|---|---|---|---|---|
| **E2B** | ~2B effective | 2B | 7.2 GB | 128K | Edge/IoT, audio input, multimodal |
| **E4B** | ~4B effective | 4B | 9.6 GB | 128K | Budget local, function calling, general chat |
| **26B MoE** | 26B total | 3.8B | 18 GB | 256K | Speed/quality balance, 128 experts (8 active) |
| **31B Dense** | 31B | 31B | 20 GB | 256K | Best quality, agentic systems, competitive coding |

The E2B and E4B models also support **native audio input** (speech recognition, audio classification).

---

## Benchmarks

### Reasoning & Math

| Benchmark | E2B | E4B | 26B MoE | 31B Dense |
|---|---|---|---|---|
| MMLU Pro | 60.0% | 69.4% | 82.6% | **85.2%** |
| AIME 2026 | 37.5% | 42.5% | 88.3% | **89.2%** |
| GPQA Diamond | 43.4% | 58.6% | 82.3% | **84.3%** |
| BigBench Extra Hard | 21.9% | 33.1% | 64.8% | **74.4%** |
| MMMLU (multilingual) | 67.4% | 76.6% | 86.3% | **88.4%** |

### Coding

| Benchmark | E2B | E4B | 26B MoE | 31B Dense |
|---|---|---|---|---|
| LiveCodeBench v6 | 44.0% | 52.0% | 77.1% | **80.0%** |
| Codeforces ELO | 633 | 940 | 1718 | **2150** |
| HumanEval (pass@1) | — | — | — | **94.1%** |

The 31B's Codeforces ELO of 2150 places it at expert competitive programmer level — up from 110 in Gemma 3.

### Vision

| Benchmark | E2B | E4B | 26B MoE | 31B Dense |
|---|---|---|---|---|
| MMMU Pro | 44.2% | 52.6% | 73.8% | **76.9%** |
| MATH-Vision | 52.4% | 59.5% | 82.4% | **85.6%** |

### Competitive Comparison

| Benchmark | Gemma 4 31B | Qwen 3.5 27B | Llama 4 Scout (~109B MoE) |
|---|---|---|---|
| MMLU Pro | 85.2% | **86.1%** | ~80% |
| GPQA Diamond | 84.3% | **85.5%** | ~74% |
| AIME 2026 | **89.2%** | ~85% | — |
| LiveCodeBench v6 | 80.0% | **83.6%** | ~68% |
| Codeforces ELO | **2150** | ~1900 | ~1400 |
| MMMU Pro (vision) | **76.9%** | 75.0% | ~65% |
| Arena AI Ranking | **#3** | ~#2 | ~#10 |

**Key takeaways:**
- Qwen 3.5 27B edges Gemma 4 31B on text reasoning (MMLU Pro, GPQA Diamond, LiveCodeBench)
- Gemma 4 31B dominates on competitive programming (Codeforces ELO 2150) and vision tasks
- Llama 4 Scout has 109B total params but trails both on reasoning benchmarks
- All three use permissive licenses (Gemma 4 and Qwen 3.5: Apache 2.0; Llama 4: community license with 700M MAU limit)

---

## Hardware Requirements

### Minimum VRAM/RAM by Variant

| Model | Q4_K_M | Q8_0 | BF16 (Full) |
|---|---|---|---|
| **E2B** | ~2 GB | ~5 GB | ~10 GB |
| **E4B** | ~5 GB | ~8 GB | ~16 GB |
| **26B MoE** | ~18 GB | ~28 GB | ~52 GB |
| **31B Dense** | ~20 GB | ~34 GB | ~62 GB |

These are model-only numbers. Add 1–20 GB for KV cache depending on context length (see [Context Window & Memory](#context-window--memory)).

### Consumer GPUs

| GPU | VRAM | Best Model (Q4) | Generation Speed | Notes |
|---|---|---|---|---|
| **RTX 3090** | 24 GB | 26B MoE | ~119 t/s | 31B fits at short context only (~34 t/s) |
| **RTX 4090** | 24 GB | 26B MoE | ~50 t/s | 31B at Q4 fits, ~28 t/s; OOM at 256K context |
| **RTX 5090** | 32 GB | 31B Dense | ~61 t/s | 26B MoE: ~180 t/s; both fit with 256K context |

> **Note:** The 26B MoE runs ~3x faster than the 31B Dense on the same GPU because only 3.8B parameters are active per token.

### Apple Silicon

| Mac | Unified Memory | Recommended Model | Expected Speed |
|---|---|---|---|
| MacBook Air M1/M2 | 8–16 GB | E2B or E4B @ Q4 | Smooth for chat |
| Mac Mini M4 | 16 GB | E4B @ Q4 | ~2s response time |
| Mac Mini M4 Pro | 24 GB | 26B MoE @ Q4 | ~20–30 t/s |
| MacBook Pro M4 Max | 48–128 GB | 31B Dense @ Q8 | ~15–20 t/s |
| Mac Studio M3/M4 Ultra | 192 GB | 31B Dense @ BF16 | ~12–18 t/s |

On Apple Silicon, **memory bandwidth matters more than chip generation**. An M3 Max (400 GB/s) outperforms an M4 Pro (273 GB/s) on the same model. The M2/M3/M4 Ultra at 800 GB/s provides the best experience.

> **Note:** Ollama uses Metal acceleration natively on macOS. No GPU passthrough configuration needed.

### Datacenter GPUs

| GPU | VRAM | 26B MoE | 31B Dense |
|---|---|---|---|
| **A100** | 40/80 GB | All quants fit on 80GB | BF16 fits on 80GB |
| **H100** | 80 GB | ~120 t/s generation | ~65 t/s generation |
| **DGX Spark** (Grace Blackwell) | 128 GB | Full BF16 + 256K context | Full BF16 + 256K context |

### CPU-Only

| Model | Feasibility | Expected Speed |
|---|---|---|
| E2B @ Q4 | Usable | ~2–5 t/s |
| E4B @ Q4 | Marginal | ~0.5–1 t/s |
| 26B / 31B | Not practical | < 1 t/s |

CPU inference requires roughly 2x the model file size in system RAM. For anything larger than E4B, a GPU or Apple Silicon unified memory is strongly recommended.

---

## Inference Speed

Token generation rates at Q4_K_M quantization, 4K context length:

| Hardware | E2B | E4B | 26B MoE | 31B Dense |
|---|---|---|---|---|
| RTX 3090 (24GB) | — | — | 119 t/s | 34 t/s |
| RTX 5090 (32GB) | — | — | 180 t/s | 61 t/s |
| M4 Pro (24GB) | — | — | ~25 t/s | — |
| H100 (80GB) | — | — | ~120 t/s | ~65 t/s |

**MoE speed advantage:** The 26B MoE is consistently ~3x faster than the 31B Dense across all hardware, while scoring only 1–3 percentage points lower on benchmarks.

**First-request warmup:** Ollama loads the model into GPU/unified memory on the first request. This takes 5–30 seconds depending on model size and storage speed. Subsequent requests are fast.

---

## Context Window & Memory

Longer context windows require additional VRAM for the KV cache:

| Context Length | Additional VRAM (approx.) |
|---|---|
| 8K | +0.5–1 GB |
| 32K | +2–4 GB |
| 128K | +8–16 GB |
| 256K | +16–20 GB |

**Example:** The 26B MoE at Q4 needs ~18 GB for model weights + ~5 GB for 256K KV cache = ~23 GB total. This fits on a 24GB RTX 3090 or RTX 4090. The 31B Dense at Q4 with 256K context needs ~40 GB — requiring a 48GB+ GPU or Apple Silicon with sufficient unified memory.

> **Note:** Gemma 4 supports FP8 KV cache optimization, which halves KV cache memory usage at minimal quality loss. Check your inference framework for FP8 KV cache support.

The JaiClaw gemma4-local example defaults to 8192 context (`OLLAMA_NUM_CTX=8192`) to keep memory usage conservative. Increase it if you have headroom:

```bash
OLLAMA_NUM_CTX=32768 OLLAMA_MODEL=gemma4:26b ../../mvnw spring-boot:run
```

---

## Capabilities by Variant

### E2B — Edge & Multimodal
- Smallest footprint, runs on phones and IoT devices
- Native audio input (speech recognition, classification)
- Image understanding
- Basic chat and summarization
- Limited tool use / function calling

### E4B — Budget Local (Default)
- Reliable function calling with JaiClaw tools
- Good balance of speed and quality
- Image understanding
- Native audio input
- Fits on most laptops with 16GB RAM

### 26B MoE — Speed/Quality Sweet Spot
- 128 experts, 8 active per token (3.8B active params)
- ~3x faster than 31B Dense, near-same quality
- 256K context window
- Strong reasoning (88.3% AIME 2026)
- Best choice for agentic workflows where latency matters

### 31B Dense — Maximum Quality
- #3 on Arena AI global leaderboard
- Codeforces ELO 2150 (expert competitive programmer)
- 94.1% HumanEval pass@1
- 256K context window
- Best for complex agentic systems, code generation, and long-context tasks
- Needs more VRAM and runs 3x slower than 26B MoE

---

## Ollama Quantization Guide

### What the Quantizations Mean

| Quantization | Precision | Size vs BF16 | Quality | Use Case |
|---|---|---|---|---|
| **Q4_K_M** | 4-bit mixed | ~30% of BF16 | Good — minimal quality loss | Default. Best size/quality tradeoff |
| **Q8_0** | 8-bit | ~50% of BF16 | Very good — near-full precision | When you have extra VRAM |
| **BF16** | 16-bit (full) | 100% | Reference quality | Research, benchmarking, datacenter |

**Recommendation:** Use Q4_K_M (the default Ollama tags) unless you have VRAM to spare. The quality difference between Q4 and Q8 is small for most tasks. BF16 is only worth it on 80GB+ GPUs or high-memory Apple Silicon.

### All Ollama Tags

| Tag | Variant | Quantization | Download Size | Context |
|---|---|---|---|---|
| `gemma4:e2b` | E2B | Q4_K_M | 7.2 GB | 128K |
| `gemma4:e2b-it-q4_K_M` | E2B | Q4_K_M | 7.2 GB | 128K |
| `gemma4:e2b-it-q8_0` | E2B | Q8_0 | 8.1 GB | 128K |
| `gemma4:e2b-it-bf16` | E2B | BF16 | 10 GB | 128K |
| `gemma4:e4b` | E4B | Q4_K_M | 9.6 GB | 128K |
| `gemma4:e4b-it-q4_K_M` | E4B | Q4_K_M | 9.6 GB | 128K |
| `gemma4:e4b-it-q8_0` | E4B | Q8_0 | 12 GB | 128K |
| `gemma4:e4b-it-bf16` | E4B | BF16 | 16 GB | 128K |
| `gemma4:26b` | 26B MoE | Q4_K_M | 18 GB | 256K |
| `gemma4:26b-a4b-it-q4_K_M` | 26B MoE | Q4_K_M | 18 GB | 256K |
| `gemma4:26b-a4b-it-q8_0` | 26B MoE | Q8_0 | 28 GB | 256K |
| `gemma4:31b` | 31B Dense | Q4_K_M | 20 GB | 256K |
| `gemma4:31b-it-q4_K_M` | 31B Dense | Q4_K_M | 20 GB | 256K |
| `gemma4:31b-it-q8_0` | 31B Dense | Q8_0 | 34 GB | 256K |
| `gemma4:31b-it-bf16` | 31B Dense | BF16 | 63 GB | 256K |
| `gemma4:31b-cloud` | 31B Dense | Cloud | — | 256K |
| `gemma4:latest` | E4B | Q4_K_M | 9.6 GB | 128K |

Pull any variant: `ollama pull gemma4:<tag>`

---

## Running with JaiClaw

### Configuration

In `application.yml` (or via environment variables):

```yaml
spring:
  ai:
    ollama:
      base-url: ${OLLAMA_BASE_URL:http://localhost:11434}
      chat:
        options:
          model: ${OLLAMA_MODEL:gemma4:e4b}
          temperature: ${OLLAMA_TEMPERATURE:0.7}
          num-ctx: ${OLLAMA_NUM_CTX:8192}
```

### Environment Variables

| Variable | Default | Description |
|---|---|---|
| `OLLAMA_BASE_URL` | `http://localhost:11434` | Ollama API endpoint |
| `OLLAMA_MODEL` | `gemma4:e4b` | Model tag from the table above |
| `OLLAMA_TEMPERATURE` | `0.7` | Response creativity (0.0–1.0) |
| `OLLAMA_NUM_CTX` | `8192` | Context window size (tokens) |

### Quick Start

```bash
# Pull a model
ollama pull gemma4:e4b

# Run the gemma4-local example
cd jaiclaw-examples/gemma4-local
OLLAMA_MODEL=gemma4:e4b ../../mvnw spring-boot:run

# Or use a larger model if your hardware supports it
OLLAMA_MODEL=gemma4:26b ../../mvnw spring-boot:run
```

See the [gemma4-local example](../jaiclaw-examples/gemma4-local/) for full setup instructions including Docker Compose.

---

## Troubleshooting

**"Model not found"** — Run `ollama pull gemma4:<tag>` first. Check available models with `ollama list`.

**Out of memory (OOM)** — Try a smaller model or lower quantization:
```bash
# Switch from 26B to E4B
OLLAMA_MODEL=gemma4:e4b ../../mvnw spring-boot:run

# Or reduce context window
OLLAMA_NUM_CTX=4096 OLLAMA_MODEL=gemma4:26b ../../mvnw spring-boot:run
```

**"Connection refused on port 11434"** — Ollama isn't running. Start it with `ollama serve` or `brew services start ollama`.

**Slow first response** — Normal. Ollama loads the model into GPU memory on first request (5–30s depending on model size). Subsequent responses are fast.

**Thermal throttling on Mac** — Sustained inference on Apple Silicon laptops can trigger thermal throttling. Monitor with `sudo powermetrics --samplers smc` and consider a cooling pad for long sessions. Desktop Macs (Mac Mini, Mac Studio, Mac Pro) handle sustained load better.

**26B MoE slower than expected** — Early llama.cpp/Ollama builds may not fully optimize the MoE routing. Update to the latest Ollama version: `brew upgrade ollama`.

---

*Data sourced from [Google Gemma 4 model card](https://ai.google.dev/gemma/docs/core/model_card_4), [Arena AI leaderboard](https://lmarena.ai/), community hardware benchmarks (llama.cpp, Ollama), and [Ollama model registry](https://ollama.com/library/gemma4/tags). Benchmark numbers may vary with inference framework and configuration.*
