# Agentic Workflow in JaiClaw

This document covers how JaiClaw implements agentic workflows — autonomous AI agents that use tools iteratively, with human-in-the-loop approval, lifecycle hooks, context compaction, and workspace memory.

## Architecture Overview

```
                          ┌──────────────────────────────────────────┐
                          │              AgentRuntime                │
                          │                                         │
  User Input ────────────►│  1. BEFORE_AGENT_START hook             │
                          │  2. Session lookup / create             │
                          │  3. Context compaction (if needed)      │
                          │  4. Build message history               │
                          │  5. Load workspace memory               │
                          │  6. BEFORE_PROMPT_BUILD hook (modifying)│
                          │  7. LLM_INPUT hook                      │
                          │  8. ┌──────────────────────────┐        │
                          │     │   Tool Loop (see below)  │        │
                          │     └──────────────────────────┘        │
                          │  9. LLM_OUTPUT hook                     │
                          │ 10. Record in session                   │
                          │ 11. AGENT_END hook                      │
                          │                                         │
  Response ◄──────────────│                                         │
                          └──────────────────────────────────────────┘
```

## Two Tool Loop Modes

JaiClaw supports two modes for executing tool calls, configured per agent via `jaiclaw.agent.agents.<name>.tool-loop.mode`:

### Spring AI Mode (default)

```yaml
tool-loop:
  mode: spring-ai
```

Uses Spring AI's `ChatClient` built-in tool execution loop. The LLM decides when to call tools and Spring AI handles the call/response cycle transparently. This mode is simpler but does not support per-step hooks or approval gates.

Best for: straightforward agents where observability at each tool step is not required.

### Explicit Mode

```yaml
tool-loop:
  mode: explicit
```

Uses JaiClaw's `ExplicitToolLoop`, which calls `ChatModel` directly and manages each tool call iteration manually. This enables:

- **BEFORE_TOOL_CALL / AFTER_TOOL_CALL hooks** at each iteration
- **Human-in-the-loop approval** before tool execution
- **Audit trails** with per-call timing and parameters
- **Iteration capping** with configurable `max-iterations`

```
┌───────────────────────────────────────────────────────────┐
│                    ExplicitToolLoop                       │
│                                                          │
│  for each iteration (1..maxIterations):                  │
│    1. Call LLM with messages + tool definitions          │
│    2. If response has no tool calls → return final text  │
│    3. For each tool call in response:                    │
│       a. Fire BEFORE_TOOL_CALL hook                      │
│       b. If approval required:                           │
│          └─ ToolApprovalHandler.requestApproval()         │
│             ├─ Approved  → proceed                       │
│             ├─ Denied    → return denial as tool result   │
│             └─ Modified  → use modified parameters        │
│       c. Execute tool                                    │
│       d. Fire AFTER_TOOL_CALL hook                       │
│    4. Append tool results to messages                    │
│    5. Continue loop                                      │
│                                                          │
│  Returns: LoopResult(finalText, history, iterationsUsed) │
└───────────────────────────────────────────────────────────┘
```

## SPI Interfaces

All agentic capabilities are defined as SPIs in `jaiclaw-core`, allowing the agent runtime to use them without hard dependencies on implementation modules.

### ContextCompactor

```java
// jaiclaw-core/src/main/java/io/jaiclaw/core/agent/ContextCompactor.java
public interface ContextCompactor {
    List<Message> compactIfNeeded(List<Message> messages, int contextWindowSize,
                                  Function<String, String> llmCall);
}
```

Checks if the conversation exceeds the token budget and summarizes older messages via an LLM call. Implementation: `CompactionServiceAdapter` in `jaiclaw-compaction`.

### AgentHookDispatcher

```java
// jaiclaw-core/src/main/java/io/jaiclaw/core/agent/AgentHookDispatcher.java
public interface AgentHookDispatcher {
    <E, C> void fireVoid(HookName hookName, E event, C context);
    <E, C> E fireModifying(HookName hookName, E event, C context);
    boolean hasHandlers(HookName hookName);
}
```

Dispatches lifecycle events to registered hook handlers. Void hooks run in parallel (fire-and-forget). Modifying hooks run sequentially, each receiving the output of the previous handler.

### ToolApprovalHandler

```java
// jaiclaw-core/src/main/java/io/jaiclaw/core/agent/ToolApprovalHandler.java
public interface ToolApprovalHandler {
    CompletableFuture<ToolApprovalDecision> requestApproval(
            String toolName, Map<String, Object> parameters, String sessionKey);
}
```

Called before each tool execution when `require-approval: true`. Returns one of three decisions:

```java
sealed interface ToolApprovalDecision {
    record Approved() implements ToolApprovalDecision {}
    record Denied(String reason) implements ToolApprovalDecision {}
    record Modified(Map<String, Object> parameters) implements ToolApprovalDecision {}
}
```

### MemoryProvider

```java
// jaiclaw-core/src/main/java/io/jaiclaw/core/agent/MemoryProvider.java
public interface MemoryProvider {
    String loadMemory(String workspaceDir);
}
```

Loads workspace-specific memory content into the system prompt. Implementation: `WorkspaceMemoryProvider` in `jaiclaw-memory`.

## Hook System

JaiClaw defines 16 lifecycle hook events covering the full agent execution pipeline:

| Hook Name | Type | Event Object | When |
|-----------|------|-------------|------|
| `BEFORE_MODEL_RESOLVE` | void | — | Before selecting the AI model |
| `BEFORE_PROMPT_BUILD` | **modifying** | `String` (system prompt) | Before assembling the system prompt |
| `BEFORE_AGENT_START` | void | — | Before agent execution begins |
| `AGENT_END` | void | — | After agent execution completes |
| `LLM_INPUT` | void | messages | Before sending to the LLM |
| `LLM_OUTPUT` | void | response | After receiving LLM response |
| `BEFORE_COMPACTION` | void | — | Before context compaction runs |
| `AFTER_COMPACTION` | void | — | After context compaction completes |
| `SESSION_START` | void | session | When a new session is created |
| `SESSION_END` | void | session | When a session ends |
| `BEFORE_RESET` | void | session | Before a session is reset |
| `MESSAGE_RECEIVED` | void | message | When inbound message arrives |
| `MESSAGE_SENDING` | void | message | Before outbound message is sent |
| `MESSAGE_SENT` | void | message | After outbound message is delivered |
| `BEFORE_TOOL_CALL` | void | `ToolCallEvent` | Before each tool execution (explicit mode) |
| `AFTER_TOOL_CALL` | void | `ToolCallEvent` | After each tool execution (explicit mode) |

### Registering Hooks in Plugins

```java
@Override
public void register(PluginApi api) {
    // Void hook — no return value
    api.on(HookName.BEFORE_TOOL_CALL, (event, ctx) -> {
        var e = (ToolCallEvent) event;
        log.info("Tool called: {}", e.toolName());
        return null;  // void hooks return null
    });

    // Modifying hook — returns modified event
    api.on(HookName.BEFORE_PROMPT_BUILD, (event, ctx) -> {
        if (event instanceof String prompt) {
            return prompt + "\n## Custom Instructions\n...";
        }
        return event;
    });
}
```

## Human-in-the-Loop Approval

Enable approval gates by setting `require-approval: true` in the tool loop config:

```yaml
jaiclaw:
  agent:
    agents:
      default:
        tool-loop:
          mode: explicit
          require-approval: true
```

Then provide a `ToolApprovalHandler` bean:

```java
@Component
public class ConsoleApprovalHandler implements ToolApprovalHandler {
    @Override
    public CompletableFuture<ToolApprovalDecision> requestApproval(
            String toolName, Map<String, Object> parameters, String sessionKey) {
        System.out.printf("Approve '%s' with params %s? (y/n): ", toolName, parameters);
        String input = System.console().readLine().trim().toLowerCase();
        if ("y".equals(input)) {
            return CompletableFuture.completedFuture(new ToolApprovalDecision.Approved());
        }
        return CompletableFuture.completedFuture(
            new ToolApprovalDecision.Denied("User denied"));
    }
}
```

The `Modified` decision allows the approval handler to alter tool parameters before execution — useful for injecting safety constraints or overriding values.

## Context Compaction

When conversations grow long, compaction summarizes older messages to stay within the LLM's context window:

```yaml
jaiclaw:
  compaction:
    enabled: true
    trigger-threshold: 50000    # token count threshold
    target-token-percent: 50    # compress to this % of the window
```

The `ContextCompactor` SPI receives a `Function<String, String>` for making LLM calls to generate summaries. Hooks `BEFORE_COMPACTION` and `AFTER_COMPACTION` fire around the compaction process.

## Workspace Memory

Memory content is loaded into the system prompt at the start of each agent run:

```java
public interface MemoryProvider {
    String loadMemory(String workspaceDir);
}
```

The `WorkspaceMemoryProvider` reads from workspace-specific files, allowing agents to remember context across sessions.

## Streaming

The `AgentRuntime` supports streaming responses via Reactor:

```java
Flux<String> runStreaming(String userInput, AgentRuntimeContext context)
```

Streaming uses Spring AI mode (ChatClient's built-in streaming). Text chunks are emitted as they arrive from the LLM, enabling responsive real-time output in UIs and CLIs.

## Configuration Reference

```yaml
jaiclaw:
  agent:
    default-agent: default
    agents:
      <agent-name>:
        id: <string>                    # agent identifier
        name: <string>                  # display name
        workspace: <path>               # workspace directory for memory
        model:
          primary: <model-name>         # primary LLM model
          fallbacks: [<model>, ...]     # fallback models
          thinking-model: <model>       # model for reasoning tasks
        tools:
          profile: full|coding|...      # tool profile filter
          allow: [<tool>, ...]          # explicit tool allowlist
          deny: [<tool>, ...]           # explicit tool denylist
        tool-loop:
          mode: spring-ai|explicit      # tool loop mode (default: spring-ai)
          max-iterations: 25            # max tool call rounds (default: 25)
          require-approval: false       # human approval gate (default: false)

  compaction:
    enabled: false                      # enable context compaction
    trigger-threshold: 50000            # token threshold to trigger
    target-token-percent: 50            # target size after compaction

  plugins:
    enabled: true                       # enable plugin discovery
```

## Example Walkthroughs

### Incident Responder

Demonstrates **explicit tool loop + human-in-the-loop approval + hooks**. The agent triages production incidents using `check_service_health` and `query_logs` tools, then proposes remediation via `restart_service` and `scale_service` — both requiring human approval. BEFORE/AFTER_TOOL_CALL hooks log all tool invocations and track remediation actions.

### Research Assistant

Demonstrates **multi-iteration tool loop + context compaction + memory**. The agent searches sources, fetches articles, saves findings, and generates reports across many iterations. Compaction kicks in automatically when the conversation grows long, and BEFORE/AFTER_COMPACTION hooks provide observability.

### Data Pipeline Orchestrator

Demonstrates **explicit tool loop + audit trail hooks + approval for destructive ops**. The agent validates schemas, runs transforms, previews data, and loads results. BEFORE/AFTER_TOOL_CALL hooks build a detailed audit trail with timing. The AGENT_END hook prints a full audit summary. The `load_data` tool requires approval.

### Code Scaffolder

Demonstrates **Spring AI tool loop + streaming + prompt customization**. The agent browses templates and generates project scaffolding files. A BEFORE_PROMPT_BUILD modifying hook injects coding standards into the system prompt, ensuring generated code follows conventions.

## Plugin Development Guide

Plugins implement `JaiClawPlugin` and register tools + hooks via `PluginApi`:

```java
@Component
public class MyPlugin implements JaiClawPlugin {

    @Override
    public PluginDefinition definition() {
        return new PluginDefinition(
            "my-plugin", "My Plugin", "Description", "1.0.0", PluginKind.GENERAL);
    }

    @Override
    public void register(PluginApi api) {
        // Register tools
        api.registerTool(new MyTool());

        // Register hooks
        api.on(HookName.BEFORE_TOOL_CALL, (event, ctx) -> {
            // handle event
            return null;
        });
    }
}
```

Tools implement `ToolCallback`:

```java
static class MyTool implements ToolCallback {
    @Override
    public ToolDefinition definition() {
        return new ToolDefinition("my_tool", "Description", "section", """
            {"type":"object","properties":{"param":{"type":"string"}},"required":["param"]}
        """);
    }

    @Override
    public ToolResult execute(Map<String, Object> parameters, ToolContext context) {
        String param = (String) parameters.get("param");
        return new ToolResult.Success("Result: " + param);
    }
}
```

Plugins are discovered via:
1. **Spring component scanning** (`@Component` annotation)
2. **ServiceLoader** (`META-INF/services/io.jaiclaw.plugin.JaiClawPlugin`)
3. **Explicit registration** via `PluginDiscovery` API
