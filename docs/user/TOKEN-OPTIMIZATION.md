# Token Optimization Developer Guide

Most AI-powered tools waste significant tokens on every LLM request. They load full skill libraries, register every tool schema, accumulate unbounded conversation history, and duplicate definitions across system prompts and API fields. This inflates costs by 10-60x for simple interactions and degrades response quality by diluting the model's attention.

JaiClaw's architecture gives you fine-grained control over exactly what goes into each API call — and a static analysis tool to measure the impact before you deploy.

---

## The Problem: Token Bloat in AI Frameworks

A typical AI framework sends a "kitchen sink" system prompt on every call:

| Source | Tokens | When Needed |
|--------|--------|-------------|
| 59 bundled skill instructions | ~26,000 | Rarely — most apps use 0-2 skills |
| Tool schemas for all registered tools | ~4,000-8,000 | Depends on the task — a summarizer needs zero |
| Auto-generated boilerplate (identity, rules, date) | ~500 | Sometimes |
| Accumulated conversation history | Grows linearly | Only recent context matters |

**Concrete example**: A simple "summarize this paragraph" query that should cost ~300 input tokens instead costs ~33,000 — because the framework loads all 59 skills, all tool schemas, and the full prompt template regardless of the task.

The cost is not just financial. Irrelevant context actively harms response quality — a model that sees kubectl instructions and GitHub workflow skills when asked to summarize HTML will produce worse summaries than one given a clean, focused prompt.

---

## Start Here: The Prompt Analyzer

Before tuning any configuration, measure where your tokens are going. JaiClaw ships a static analysis tool — `jaiclaw-prompt-analyzer` — that reads your project's `application.yml` and estimates the per-request token cost of your fixed overhead (system prompt, skills, tools) without running the app or making any API calls.

### Running the Analyzer

**Standalone CLI** (build with `-Pstandalone`):

```bash
# Analyze a project directory
java -jar jaiclaw-prompt-analyzer.jar prompt-analyze --path /path/to/my-project

# CI gate: fail if estimated tokens exceed threshold
java -jar jaiclaw-prompt-analyzer.jar prompt-check --path /path/to/my-project --threshold 5000
```

**From the JaiClaw shell** (if the analyzer module is on the classpath):

```
prompt-analyze --path /path/to/my-project
prompt-check --path /path/to/my-project --threshold 5000
```

**As an LLM tool**: When embedded in a running JaiClaw agent, the analyzer registers a `prompt_analyze` tool that the agent can invoke on any project directory.

### Reading the Report

```
Prompt Token Analysis: my-project
==================================

Component                  Tokens    Details
---------                  ------    -------
System prompt                  148    (configured)
Skills (0 loaded)                0    allow-bundled: []
Built-in tools (6)           1,872    profile: full
                             ------
Estimated total              2,020    (excludes conversation history)

Warnings:
  (none)
```

The report breaks down tokens by component — system prompt, skills, built-in tools, and project-specific tools — so you know exactly which optimization mechanism to apply. It also scans your source for custom `ToolCallback` and `JaiClawPlugin` implementations and estimates their schema overhead.

### The `prompt-check` CI Gate

Use `prompt-check` in your build pipeline to prevent token regressions:

```bash
# Fails with exit code 1 if overhead exceeds 5,000 tokens
java -jar jaiclaw-prompt-analyzer.jar prompt-check --path . --threshold 5000
```

Output on failure:

```
FAIL: estimated 33,412 tokens exceeds threshold of 5,000

<full analysis report follows>
```

This catches the most common mistake — forgetting to set `allow-bundled: []` in a focused application, which silently adds ~26K tokens to every request.

### Warnings

The analyzer detects common misconfigurations and reports them as warnings. For example, if `allow-bundled` is unset (defaulting to `["*"]`), the report flags it:

```
Warnings:
  allow-bundled defaults to ["*"] — loading all 59 skills (~26,000 tokens)
```

**Source**: `ProjectScanner`, `AnalysisReport`, `PromptAnalyzerCommands` (`jaiclaw-prompt-analyzer`)

---

## JaiClaw's Optimization Stack

Seven mechanisms targeting different sources of token waste. They compose — use any combination. Run the prompt analyzer after each change to verify the impact.

### 1. Skill Filtering (`allow-bundled`)

**What it controls**: Which bundled SKILL.md files are loaded into the system prompt's `# Skills` section.

**The default**: `allow-bundled: ["*"]` loads all 59 bundled skills. This is appropriate for the general-purpose shell where users might ask about anything. It is inappropriate for focused applications.

**Configuration** (`jaiclaw.skills.allow-bundled`):

```yaml
# Load nothing — drops the entire Skills section from the system prompt
jaiclaw:
  skills:
    allow-bundled: []

# Whitelist specific skills
jaiclaw:
  skills:
    allow-bundled: [summarize, web-research]

# Default: load everything (general-purpose shell only)
jaiclaw:
  skills:
    allow-bundled: ["*"]
```

**How it works**: `SkillLoader.loadConfigured()` reads the `allowBundled` list and applies three-way logic:

- `null` or contains `"*"` — loads all bundled skills
- Empty list `[]` — loads none, returns `List.of()`
- Specific names — loads only skills whose `name` matches

Before the allowlist is applied, `SkillEligibilityChecker.isEligible()` automatically excludes skills whose `requiredBins` or `supportedPlatforms` don't match the runtime environment. A skill requiring `kubectl` is silently dropped when kubectl isn't installed, regardless of the allowlist.

**Token savings**: ~26,000 tokens when set to `[]`.

**Source**: `SkillLoader` (`jaiclaw-skills`), `SkillEligibilityChecker` (`jaiclaw-skills`), `SkillsProperties` (`jaiclaw-config`)

---

### 2. Tool Profile Filtering

**What it controls**: Which tool schemas are sent in the API request's `tools` field.

**Five profiles** (`ToolProfile` enum):

| Profile | What's Included | Use Case |
|---------|----------------|----------|
| `NONE` | Zero tools | Pure conversation, summarization, Q&A |
| `MINIMAL` | Read-only tools (file read, grep, glob) | Analysis without side effects |
| `CODING` | File read/write/edit, shell execution | Software engineering (default) |
| `MESSAGING` | Channel send/receive capabilities | Messaging bots |
| `FULL` | All registered tools | General-purpose agents |

**Configuration** (`jaiclaw.agent.agents.<name>.tools`):

```yaml
jaiclaw:
  agent:
    agents:
      default:
        tools:
          profile: "none"       # No tools at all
          allow: []             # Additional allowlist (within the profile)
          deny: [shell_exec]    # Blocklist (overrides allow)
```

**Layered resolution**: `ToolRegistry.resolveForPolicy()` applies three filters in sequence:

1. **Profile filter** — only tools tagged with the selected profile (or `FULL`) pass through
2. **Allow filter** — if non-empty, only named tools from step 1 are kept
3. **Deny filter** — named tools are removed regardless of the above

Each tool declares which profiles it belongs to. For example, `FileReadTool` is tagged with `{MINIMAL, CODING, FULL}`, so it appears under MINIMAL but not under NONE or MESSAGING. `FULL` is a wildcard — it includes all tools regardless of their tags.

**Token savings**: All tool schema tokens when set to `"none"` (~4,000-8,000 tokens depending on registered tools).

**Source**: `ToolProfile` (`jaiclaw-core`), `ToolRegistry.resolveForPolicy()` (`jaiclaw-tools`), `AgentProperties.ToolPolicyConfig` (`jaiclaw-config`)

---

### 3. No Tool Duplication in System Prompt

**What it controls**: Prevents tool descriptions from appearing twice — once in the system prompt text and once in the API `tools` field.

**How it works**: Spring AI sends tool definitions as structured JSON schemas in the API request's `tools` array. This is the native function-calling mechanism for OpenAI, Anthropic, and Ollama APIs. JaiClaw deliberately does **not** duplicate these definitions in the system prompt text.

```java
// SystemPromptBuilder.java — intentional no-op
@Deprecated
public SystemPromptBuilder tools(Object tools) {
    return this;  // tools NOT rendered into system prompt
}
```

The `build()` method includes an explicit comment:

```java
// Tools section omitted — Spring AI sends tool definitions as structured JSON schemas
// in the API request. Duplicating them in the system prompt wastes tokens.
```

Many frameworks duplicate tool descriptions in both places — the system prompt (natural language) and the API tools field (JSON schema). This doubles the token cost of every tool without any benefit, since models already see the structured schema.

**Token savings**: Prevents 2x tool schema tokens. Always active — no configuration needed.

**Source**: `SystemPromptBuilder` (`jaiclaw-agent`), `SpringAiToolBridge` (`jaiclaw-tools`)

---

### 4. System Prompt Replacement

**What it controls**: Whether the auto-generated system prompt (identity, skills, boilerplate) is replaced entirely by your custom text.

**Two modes** (`jaiclaw.agent.agents.<name>.system-prompt`):

```yaml
# Replace mode (default): your text IS the entire system prompt
system-prompt:
  strategy: inline
  append: false          # default
  content: >
    You are a concise HTML summarizer. Output 2-3 sentences. Nothing else.

# Append mode: your text is added after the auto-generated prompt
system-prompt:
  strategy: classpath
  append: true
  source: prompts/additional-instructions.txt
```

When `append: false` (the default), `AgentRuntime.buildSystemPrompt()` returns your custom text as-is, bypassing `SystemPromptBuilder` entirely. No identity section, no skills section, no date line — just your text.

**Four loading strategies**:

| Strategy | Loads From | Example |
|----------|-----------|---------|
| `inline` | `content` field directly | Short prompts in YAML |
| `classpath` | Spring classpath resource via `source` | `prompts/summarizer.txt` |
| `file` | File system path via `source` | `/opt/prompts/custom.txt` |
| `url` | HTTP(S) URL via `source` | Remote prompt management |

**Token savings**: Replaces all auto-generated boilerplate (~500+ tokens of identity, date, rules) with exactly what you write.

**Source**: `SystemPromptConfig` (`jaiclaw-config`), `AgentRuntime.buildSystemPrompt()` (`jaiclaw-agent`)

---

### 5. Stateless Channels

**What it controls**: Whether conversation history is accumulated across messages.

**The problem with history growth**: In a pipeline that processes 100 files sequentially, the 100th LLM call carries all 99 prior responses in its conversation history. Token usage grows linearly: message 1 costs N tokens, message 100 costs ~100N tokens.

**Configuration** (`jaiclaw.camel.channels[*].stateless`):

```yaml
jaiclaw:
  camel:
    channels:
      - channel-id: html-summarizer
        stateless: true     # Fresh session per message
```

When `stateless: true`, `GatewayService.onMessage()` creates a fresh ephemeral session with a random UUID for every inbound message. The session is never stored in `SessionManager` — it has no history and is discarded after the response is generated.

```java
// GatewayService.java — the stateless branch
if (stateless) {
    session = Session.create(UUID.randomUUID().toString(), sessionKey, defaultAgentId, tid);
} else {
    session = sessionManager.getOrCreate(sessionKey, defaultAgentId);
}
```

**Token savings**: Zero history growth. Every call pays only for the system prompt + current input, not accumulated context.

**Source**: `GatewayService.onMessage()` (`jaiclaw-gateway`), `CamelChannelConfig` (`jaiclaw-camel`)

---

### 6. Context Compaction

**What it controls**: Automatic summarization of old conversation history when the context window fills up.

**How it works**: `CompactionService.compactIfNeeded()` checks current token usage against a configurable threshold. When triggered, it:

1. Identifies the oldest messages to summarize (keeping the most recent ~20% of the window intact)
2. Sends the old messages to the LLM for summarization via `CompactionSummarizer`
3. Runs `IdentifierPreserver.findMissing()` to catch any UUIDs, URLs, file paths, or IP addresses that the summary dropped — these are appended verbatim
4. Replaces the old messages with a single `[Context Summary]` system message

**Configuration** (`CompactionConfig`):

| Parameter | Default | Description |
|-----------|---------|-------------|
| `enabled` | `true` | Whether compaction is active |
| `triggerThreshold` | `0.8` | Fraction of context window that triggers compaction (80%) |
| `targetTokenPercent` | `20` | Keep recent messages filling this % of the window |
| `summaryModel` | `null` | Model for summarization (null = use agent's primary model) |

For a 128K-token context window (Claude Sonnet): compaction triggers at ~102K tokens and compresses old history down to fit within ~25K tokens, freeing ~77K tokens for continued conversation.

**Identifier preservation**: `IdentifierPreserver` scans for four types of identifiers using regex:

| Type | Pattern |
|------|---------|
| UUID | `8-4-4-4-12` hex format |
| URL | `http://` or `https://` URLs |
| IPv4 | Dotted-quad addresses |
| File path | Unix paths with 2+ segments |

Any identifier present in the original conversation but missing from the LLM-generated summary is appended to the summary text, ensuring nothing critical is lost.

**Token savings**: ~80% reduction at the trigger point. Unlike stateless mode, compaction works for long-running conversations where history matters.

**Source**: `CompactionService` (`jaiclaw-compaction`), `CompactionSummarizer` (`jaiclaw-compaction`), `IdentifierPreserver` (`jaiclaw-compaction`), `CompactionConfig` (`jaiclaw-core`)

---

### 7. Response Sanitizer

**What it controls**: Prevents chain-of-thought (CoT) reasoning from polluting conversation history.

Without sanitization, internal reasoning like "The user wants me to..." or "Let me think about..." leaks into the assistant message stored in history. On the next turn, the model pays tokens to re-read its own reasoning — context that provides no value.

`ResponseSanitizer` strips CoT prefixes from every response before it enters the session. If the entire response is detected as reasoning with no actual content, JaiClaw automatically retries with a minimal override prompt to extract a direct answer.

**Token savings**: Prevents reasoning tokens from accumulating in history. The savings compound over multi-turn conversations — each unsanitized CoT prefix would be re-sent on every subsequent turn.

**Source**: `ResponseSanitizer` (`jaiclaw-agent`)

---

## Token Visibility & Monitoring

After applying optimizations, verify the results with JaiClaw's built-in logging.

### INFO-Level: Per-Request Summary

Every LLM call logs input tokens, output tokens, and totals. Anthropic prompt cache hits/misses are logged when present.

```
LLM usage — request: 312 tokens, response: 87 tokens, total: 399 tokens
LLM cache — read: 280 tokens, write: 32 tokens
```

This is always on at the default `INFO` log level for `io.jaiclaw.agent.AgentRuntime`.

### TRACE-Level: Token Breakdown

Enable with `logging.level.io.jaiclaw.agent.LlmTraceLogger=TRACE` to see a per-component breakdown:

```
LLM request — token breakdown: system ~75 + tools ~0 + conversation ~237 = ~312 estimated
```

This shows exactly where your tokens are going — system prompt, tool schemas, or conversation history — so you can target the right optimization mechanism.

---

## Putting It Together: Maximum Optimization

The `camel-html-summarizer` example demonstrates the full optimization stack. It processes HTML files dropped into a directory and writes summaries to an output directory.

### Step 1: Measure the Baseline

Run the prompt analyzer against the project before and after optimization:

```bash
java -jar jaiclaw-prompt-analyzer.jar prompt-analyze --path jaiclaw-examples/camel-html-summarizer
```

### Step 2: The Optimized Configuration

```yaml
jaiclaw:
  agent:
    agents:
      default:
        system-prompt:
          strategy: inline
          content: >
            You are a concise HTML summarizer. Your entire response must be
            the summary itself — nothing else.
            Output exactly 2-3 sentences summarizing the HTML content.
        tools:
          profile: "none"           # No tools needed for summarization
  skills:
    allow-bundled: []               # No skills needed
  camel:
    channels:
      - channel-id: html-summarizer
        stateless: true             # Fresh session per file
```

### Step 3: Verify with the Analyzer

```
Prompt Token Analysis: camel-html-summarizer
=============================================

Component                  Tokens    Details
---------                  ------    -------
System prompt                  75    (configured)
Skills (0 loaded)                0    allow-bundled: []
Built-in tools (0)               0    profile: none
                             ------
Estimated total                 75    (excludes conversation history)

Warnings:
  (none)
```

### Step 4: Confirm at Runtime

Enable TRACE logging to verify actual token usage matches the static estimate:

```yaml
logging:
  level:
    io.jaiclaw.agent.LlmTraceLogger: TRACE
```

### Cost Comparison

| Component | Default Config | Optimized Config | Savings |
|-----------|---------------|-----------------|---------|
| Skills | ~26,000 tokens (59 skills) | 0 tokens | `allow-bundled: []` |
| Tool schemas | ~4,000 tokens | 0 tokens | `profile: "none"` |
| System prompt boilerplate | ~500 tokens | ~75 tokens (custom only) | `append: false` |
| History (100th file) | ~30,000+ tokens | 0 tokens | `stateless: true` |
| **Total (100th call)** | **~60,000+ tokens** | **~75 + input** | **~99% reduction** |

The first call drops from ~33,000 input tokens to ~300. By the 100th file, the difference is even more dramatic because stateless mode prevents history accumulation.

### Step 5: Add a CI Gate

Prevent future regressions by adding the threshold check to your build:

```bash
java -jar jaiclaw-prompt-analyzer.jar prompt-check \
  --path jaiclaw-examples/camel-html-summarizer \
  --threshold 500
```

---

## Summary

| Mechanism | Config | Savings | When to Use |
|-----------|--------|---------|-------------|
| **Prompt Analyzer** | `prompt-analyze` / `prompt-check` | Identifies waste before deploy | Every project, CI pipelines |
| Skill filtering | `allow-bundled: []` | ~26K tokens | Always in focused apps |
| Tool profile | `profile: "none"` | ~4-8K tokens | Pure conversation/summarization |
| No prompt duplication | Automatic | Prevents 2x tool tokens | Always on |
| System prompt replace | `append: false` | Replaces boilerplate | Custom agents |
| Stateless channels | `stateless: true` | Zero history growth | Batch/pipeline processing |
| Compaction | Auto at 80% threshold | ~80% at trigger | Long conversations |
| Response sanitizer | Automatic | Prevents CoT accumulation | Always on |

**Recommended workflow**:

1. Run `prompt-analyze` on your project to see the baseline
2. Set `allow-bundled: []` and `profile: "none"`, then add back only what you need
3. Run `prompt-analyze` again to verify the reduction
4. Add `prompt-check --threshold <target>` to your CI pipeline
5. Monitor runtime token usage via the INFO-level logs
