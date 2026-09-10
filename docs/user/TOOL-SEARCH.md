# Tool Search and Deferred Schemas

Keeps a large tool catalog out of every API call. Tools you mark **deferred** are
not advertised to the model up front; a `tool_search` builtin surfaces them on
demand, and anything it returns stays callable for the rest of the session.

Introduced in 1.2.0. **Off by default** — with `enabled: false` nothing is
deferred, `tool_search` is not registered, and the model receives exactly the
tool list it received before.

## The problem it solves

Every tool's JSON Schema is sent on every request. A deployment with a handful of
builtins plus two MCP servers and a few Camel routes can easily reach 60+ tools,
most irrelevant to any given conversation. That is paid for on every turn, in
every session.

Measured on the 60-tool fixture in `ToolSearchE2ESpec`: **60 tools registered, 7
schemas sent** (6 core + `tool_search`).

## Configuration

```yaml
jaiclaw:
  tools:
    search:
      enabled: true
      sources: [mcp, camel]     # defer whole classes of tools
      sections: [k8s]           # …or whole sections
      globs: ["kubectl_*"]      # …or by name pattern
      names: [some_rare_tool]   # …or individually
      limit: 5                  # results per search
```

Rules are additive: a tool is deferred if it matches **any** of them.

### How `sources` is determined

Most tools do not declare a source, so it is **derived from the implementing
class's package**:

| Package contains | Reported source |
|---|---|
| `.mcp.` | `mcp` |
| `.camel.` | `camel` |
| `io.jaiclaw.<module>.…` | the module name (`kanban`, `pipeline`, …) |
| `io.jaiclaw.tools.…` / `io.jaiclaw.core.…` | `builtin` |

A tool that stamps `ToolDefinition.source()` explicitly is always believed and
overrides the inference.

Package inference is a heuristic, and treated as one: it feeds a context-economy
control, never an authorization decision. A wrong guess means a schema is sent
that could have been deferred — not that a tool becomes reachable. Profile and
allow/deny filtering are unaffected.

Check what a tool resolves to with `ToolRegistry.sourceOf(name)`.

Enabling search with **no** rules registers `tool_search` but defers nothing.
That is a reasonable first step — the model gains the ability to look around
without changing what it is sent.

## What the model sees

Turn 1 — the non-deferred tools, plus:

```json
{
  "name": "tool_search",
  "description": "Find tools that are available to you but whose details were not included up front..."
}
```

When it needs something it cannot see:

```json
{"query": "create a jira ticket", "limit": 5}
```

comes back as

```json
{"query":"create a jira ticket","count":1,
 "tools":[{"name":"mcp_tool_jira","description":"Create and update Jira issues",
           "section":"remote","input_schema":"{…}"}],
 "note":"These tools are now available to call directly."}
```

From the next turn on, `mcp_tool_jira`'s schema is included for that session.

If the model calls a deferred tool it has not discovered, the error names
`tool_search` rather than saying the tool does not exist — so it can recover
instead of concluding the capability is missing.

## Deferral is not access control

**A deferred tool is fully permitted — it is simply not advertised.** Deferral is
a context-economy control.

Access control remains tool profiles and allow/deny policy:

- `ToolDefinition.isAvailableIn()` is deliberately unaffected by `deferred`.
- `resolveActive()` filters deferred tools *after* profile filtering, never
  before, so discovering a tool can never widen what a profile permits.
- `tool_search` results are bounded by the run's profile, so it cannot be used to
  enumerate tools the run is not allowed to call.

Never rely on deferral to hide a dangerous tool. Use a profile, an allow/deny
policy, or an [approval floor](./BUDGETS-AND-GUARDS.md#approval-floors).

## Search ranking

The index is **lexical, not vector-based** — `jaiclaw-tools` is a dependency of
nearly every module, and an embedding model or vector store there would be felt
by every adopter. Scoring is weighted term overlap:

| Signal | Weight |
|---|---|
| Exact name match | 100 |
| Name token | 25 |
| Keyword | 12 |
| Section | 8 |
| Description token | 4 |

Ties break on name, so identical queries return a stable order — a model that saw
results reshuffle each turn could not build a reliable habit.

Improve results by giving tools **keywords**:

```java
ToolDefinition.builder()
    .name("kubectl_get")
    .description("List Kubernetes resources")
    .section("k8s")
    .keywords(Set.of("pods", "deployments", "namespace"))
    .build();
```

## Session scope

Discoveries last for the **session**, not the turn: a model that discovers a tool,
uses it, and finds it gone next turn would search for it again every turn — worse
than never deferring it.

Discoveries are held in memory, bounded per session (64 tools) and in total (1000
sessions, LRU). They are not persisted: a restart costs one extra `tool_search`.
`SessionToolDiscoveries` can be replaced with a durable implementation without
changing the tool surface.

## Sizing guidance

| Catalog size | Recommendation |
|---|---|
| < 15 tools | Leave disabled. The overhead is not worth the indirection. |
| 15–40 | Consider deferring `sources: [mcp, camel]`. |
| 40+ | Enable. Keep the tools used in most conversations non-deferred. |

Keep frequently-used tools **out** of the deferral rules. A tool needed in most
conversations costs an extra round trip every time it is deferred.

## See also

- [`DELEGATION.md`](./DELEGATION.md) — subagents get their own narrower tool set
- [`BUDGETS-AND-GUARDS.md`](./BUDGETS-AND-GUARDS.md) — iteration budgets, approval floors
