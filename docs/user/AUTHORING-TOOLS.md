# Authoring Tools

> **Audience:** anyone adding custom capabilities to a JaiClaw agent —
> file operations, API calls, business-domain actions, anything the LLM
> should be able to *do* rather than just *know*.

This page walks through the tool-authoring shape end-to-end, starting
from the [hello-world example](../../jaiclaw-examples/hello-world/) and
adding complexity as it goes. By the end you'll know what tools look
like in code, how parameters propagate from JSON Schema → LLM → your
handler, when to reach for which API, and what JaiClaw conventions
matter for review.

## The shape of a tool

Every tool implements one SPI:

```java
public interface ToolCallback {
    ToolDefinition definition();
    ToolResult execute(Map<String, Object> parameters, ToolContext context);
}
```

- `definition()` returns metadata the LLM sees: name, description, JSON
  Schema for parameters, and the profile(s) the tool participates in.
- `execute(params, ctx)` is what runs when the LLM picks this tool.
  Returns `ToolResult.Success` or `ToolResult.Error` — both are sealed
  subtypes of `ToolResult`.

JaiClaw uses its own `ToolCallback` SPI, then bridges to Spring AI's
`ToolCallback` interface via `SpringAiToolBridge`. You write against the
JaiClaw SPI; the bridge handles the rest.

## The minimal tool

From [`jaiclaw-examples/hello-world/`](../../jaiclaw-examples/hello-world/src/main/java/io/jaiclaw/examples/helloworld/HelloWorldConfig.java):

```java
@Configuration
public class HelloWorldConfig {

    @Bean
    public ToolCallback echoTool() {
        return new ToolCallback() {
            @Override
            public ToolDefinition definition() {
                return ToolDefinition.builder()
                        .name("echo")
                        .description("Echo back the supplied text. Use this to demonstrate tool calls.")
                        .section("hello-world")
                        .inputSchema("""
                                {
                                  "type": "object",
                                  "properties": {
                                    "text": {
                                      "type": "string",
                                      "description": "The text to echo back."
                                    }
                                  },
                                  "required": ["text"]
                                }""")
                        .profiles(Set.of(ToolProfile.FULL))
                        .build();
            }

            @Override
            public ToolResult execute(Map<String, Object> parameters, ToolContext context) {
                Object text = parameters.get("text");
                if (text == null) {
                    return new ToolResult.Error("missing required parameter: text");
                }
                return new ToolResult.Success(String.valueOf(text));
            }
        };
    }
}
```

That's a complete, registered, callable tool — no XML, no annotations
beyond `@Bean` / `@Configuration`. The LLM gets `echo` in its tool list
the next request.

## Wiring conventions

### `@Configuration` over `@Component`

This is a hard JaiClaw convention. Tool classes (or anonymous
implementations) get wired via a `@Bean` factory in a `@Configuration`,
not via `@Component` on the implementing class. Why:

- Bean wiring stays visible in one place — easier to read, easier to
  override.
- No surprises with component scan ordering vs. auto-configuration.
- Trivial to inject `@Configuration`-scope test doubles.

### Constructor-style tool wiring

For tools with real dependencies (HTTP clients, repos, configured
options), keep the tool a plain class and inject dependencies in its
constructor:

```java
public class FaqTool implements ToolCallback {
    private final FaqRepository repo;
    public FaqTool(FaqRepository repo) { this.repo = repo; }
    // definition() + execute(...) as above
}

@Configuration
public class FaqConfig {
    @Bean
    public ToolCallback faqTool(FaqRepository repo) {
        return new FaqTool(repo);
    }
}
```

## The JSON Schema

The `inputSchema` string is what Spring AI ships to the LLM as the tool
function's parameter schema. Keep three things in mind:

1. **The schema and your `parameters.get(...)` extractions must agree.**
   If the schema says `text` and your handler reads `message`, the LLM
   does the right thing (passes `text`) and your handler silently sees
   null. There is no compile-time check.
2. **Required fields go in `required`.** The LLM tries hard to populate
   anything in `required`; everything else is optional and may be
   absent.
3. **Descriptions matter.** The model reads them. A field with a vague
   description gets vague values; one with a tight description (units,
   format, examples) gets clean values.

A reasonable schema for a "find the next available meeting slot" tool:

```json
{
  "type": "object",
  "properties": {
    "durationMinutes": {
      "type": "integer",
      "minimum": 15,
      "maximum": 240,
      "description": "Meeting length in minutes. Round up to the nearest 15."
    },
    "preferredWindow": {
      "type": "string",
      "enum": ["morning", "afternoon", "any"],
      "description": "Time-of-day preference. Defaults to 'any' if not specified."
    },
    "attendees": {
      "type": "array",
      "items": { "type": "string", "format": "email" },
      "description": "Email addresses of required attendees."
    }
  },
  "required": ["durationMinutes"]
}
```

A `SchemaBuilder` fluent helper is on the roadmap (see
`CODEBASE-ANALYSIS-2026-06-10.md` §3.2 step 2 / Phase 3 #3) so schema
and parameter names won't drift; until then, keep the schema string
adjacent to the field-extraction code so they stay in sync.

## `ToolProfile` — controlling which tools are visible

Every tool is tagged with one or more `ToolProfile`s. The agent runtime
filters tools by the active profile so the LLM only sees what's
relevant.

```java
.profiles(Set.of(ToolProfile.FULL))                           // everywhere
.profiles(Set.of(ToolProfile.CODING))                          // coding-focused agents
.profiles(Set.of(ToolProfile.WEB, ToolProfile.CODING))         // both
```

`FULL` is the catch-all — a `FULL`-profile agent sees every tool
regardless of its own profile tags. Anything else is filtered. The
audit (`CODEBASE-ANALYSIS-2026-06-10.md` §3.2 step 3) tracks a
typed-parameter binding upgrade for Phase 3 that will also make profile
selection more ergonomic.

## `ToolContext` — what your handler can ask for

```java
public record ToolContext(
        String agentId,
        String sessionKey,
        String sessionId,
        String workspaceDir,
        Map<String, Object> contextData
) {}
```

- `agentId` — which `JaiClawAgent` config is currently active. Useful
  when one tool is registered to multiple agents with different
  behaviors per agent.
- `sessionKey` — full session identifier
  (`{agentId}:{channel}:{accountId}:{peerId}`). Use this when you need
  to scope state to one conversation.
- `workspaceDir` — the agent's scratch directory. Tools that read/write
  files should default to writing inside `workspaceDir` (the
  `WorkspaceBoundary` guard, when enabled, fails reads/writes that
  escape it).
- `contextData` — arbitrary per-call context populated by plugins and
  channel adapters. Read-only from a tool's perspective.

## Returning results

```java
return new ToolResult.Success("the response text");
return new ToolResult.Error("missing required parameter: text");
```

`Success` carries a `String`. The LLM treats it as the tool's output
and includes it in the next turn of reasoning. Pick a format that
the model can parse — usually JSON or a short natural-language string.

`Error` carries a `String` message. The LLM sees the error and decides
what to do; often it'll retry with adjusted parameters or pick a
different tool.

**Don't return huge blobs.** Every byte you return counts as input
tokens on the *next* LLM call. Summarize, paginate, or stash large
results out-of-band (e.g., write to a file in `workspaceDir` and
return the path).

## Common patterns

### Lookups against an external API

```java
public ToolResult execute(Map<String, Object> parameters, ToolContext context) {
    String query = (String) parameters.get("query");
    try {
        HttpResponse<String> response = httpClient.send(...);
        if (response.statusCode() >= 400) {
            return new ToolResult.Error("API returned HTTP " + response.statusCode());
        }
        return new ToolResult.Success(response.body());
    } catch (IOException | InterruptedException e) {
        return new ToolResult.Error("failed to call API: " + e.getMessage());
    }
}
```

### State that survives across calls

The `Map<String, Object> parameters` view is per-call only. For per-session
state, either persist via your own service (recommended) or read/write
to `context.workspaceDir()`.

If you need state across the entire process, your tool class is a
regular Spring bean — keep it on the field.

### Tools that mutate the same business resource concurrently

The agent runtime can call multiple tools in parallel during a single
LLM turn. If your tools mutate shared state, synchronize it the same
way you would for any concurrent service — `ConcurrentHashMap`,
`AtomicReference`, virtual threads with `@TenantAgnostic` annotation,
etc. See `CONTRIBUTING.md` § Multi-tenancy conformance check for the
hard rules.

## Per-tenant tool behavior

In multi-tenant mode, every tool gets the same `ToolContext` regardless
of tenant — the tenant is on the thread (`TenantContextHolder`). Two
patterns:

1. **Tool is tenant-aware in its handler:**
   ```java
   TenantContext ctx = TenantContextHolder.get();
   if (ctx != null) {
       // load per-tenant config / data
   }
   ```
2. **Tool delegates to a service that's tenant-aware** (more common).
   The service uses `TenantGuard.resolveStorageKey(...)` for keys and
   `TenantContextPropagator` for async work.

Per [CONTRIBUTING.md](../../CONTRIBUTING.md): if your tool reads/writes
shared state, the storage layer must be tenant-scoped.

## Default rendering tools (`ascii_render`, `ascii_box`)

Two tools in the **Rendering** section ship as defaults alongside the
file/shell/web pair so every agent can emit diagrams without bringing
in a library:

| Tool | Use |
|---|---|
| `ascii_box` | Quick text-in-a-box (single / double / bold / rounded borders, optional title) |
| `ascii_render` | Declarative scene — rectangles, lines, labels, text blocks, dots, circles, ellipses, tables, scatter plots |

Both delegate to `AsciiSceneFactory` in the pure-Java
`jaiclaw-ascii-render` library, so the same JSON description is
renderable from non-agent code (web app, batch script, custom tool)
via `AsciiSceneFactory.renderJson(jsonString)`.

See [features/RENDERING.md](features/RENDERING.md) for the JSON
schema, the full element catalogue, and developer-facing usage.

## Built-in tools to learn from

The framework ships ~40 built-in tools — read a few to internalize the
conventions:

| Tool | Lives at | Why read it |
|---|---|---|
| `ShellExecTool` | `core/jaiclaw-tools/src/main/java/io/jaiclaw/tools/builtin/` | Real tool with policy guard, timeout, sandboxing |
| `WebFetchTool` | same | HTTP client + SSRF guard pattern |
| `AsciiRenderTool` | same | Thin adapter that delegates the work to a library facade — clean separation between tool wiring and engine |
| `FileEditTool` | `core/jaiclaw-tools/src/main/java/io/jaiclaw/tools/code/` | Workspace-boundary enforcement |
| Calendar `CreateEventTool` | `extensions/jaiclaw-calendar/...` | Tenant-aware service delegation |
| `FaqTool` | `jaiclaw-examples/helpdesk-bot/...` | Minimal example of value-shape returns |

## Where to next

- **Test your tools** — add Spock specs under `src/test/groovy/`. Mock
  external HTTP / DB calls; assert on the `ToolResult` shape.
- **Register custom tools at scale** — see the `ToolCatalog` static
  registry in `jaiclaw-tools` for how the built-in 38 are organized;
  the same pattern works for custom tool families.
- **Skills** are the companion: tools are *actions*, skills are
  *instructions*. See [AUTHORING-SKILLS.md](AUTHORING-SKILLS.md).
- **Channels** are how messages get to the agent. See
  [AUTHORING-CHANNELS.md](AUTHORING-CHANNELS.md).
