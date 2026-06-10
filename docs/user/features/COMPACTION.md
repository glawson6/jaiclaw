# Context Compaction

Module: `jaiclaw-compaction`

## Overview

Context compaction automatically summarizes older conversation messages when the context window fills up, preserving key identifiers (UUIDs, URLs, file paths) while reducing token count. This prevents context window overflow during long conversations.

## How It Works

1. **Token Estimation** — `TokenEstimator` uses a ~4 chars/token heuristic to estimate token usage for messages
2. **Threshold Check** — `CompactionService.compactIfNeeded()` checks if current token usage exceeds `triggerThreshold` (default 80%) of the context window
3. **Summarization** — If triggered, older messages are formatted and passed to an LLM via `CompactionSummarizer` to produce a concise summary
4. **Identifier Preservation** — `IdentifierPreserver` extracts UUIDs, URLs, IPs, and file paths from the original messages and verifies they appear in the summary
5. **Message Replacement** — Summarized messages are replaced with a single `SystemMessage` containing the summary, preserving recent messages

## Configuration

```java
// Default: enabled, triggers at 80% capacity, targets 20% of window for summary
CompactionConfig.DEFAULT

// Disabled
CompactionConfig.DISABLED

// Custom
new CompactionConfig(
    true,    // enabled
    0.8,     // triggerThreshold (80% of context window)
    20,      // targetTokenPercent (aim for 20% of window)
    null     // summaryModel (null = use default)
)
```

## Usage

```java
// Create service with config
CompactionService compaction = new CompactionService(CompactionConfig.DEFAULT);

// Check if compaction is needed and get result
CompactionResult result = compaction.compactIfNeeded(messages, contextWindowSize, llmFunction);

// Or apply compaction directly (returns original messages if not needed)
List<Message> compacted = compaction.applyCompaction(messages, contextWindowSize, llmFunction);
```

The `llmFunction` is a `Function<String, String>` that sends a prompt to the LLM and returns the response. This abstraction keeps the compaction module decoupled from any specific LLM client.

## Key Classes

| Class | Purpose |
|-------|---------|
| `CompactionService` | Main orchestrator — call `compactIfNeeded()` or `applyCompaction()` |
| `TokenEstimator` | Estimates token count for text, messages, and message lists |
| `CompactionSummarizer` | Formats messages and calls LLM for summarization |
| `IdentifierPreserver` | Extracts and validates preservation of key identifiers |
| `CompactionConfig` | Configuration record (in `jaiclaw-core`) |
| `CompactionResult` | Result record with summary, token counts, messages removed (in `jaiclaw-core`) |
