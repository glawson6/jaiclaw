# MCP in JaiClaw

**Framework version:** 1.0.1-SNAPSHOT (post-1.0.0 release)
**Audience:** adopters running JaiClaw with MCP consumers (Claude Desktop, other MCP clients, downstream agents), and framework contributors writing new MCP servers.

The **Model Context Protocol** (MCP, from Anthropic) is a JSON-RPC contract that lets an LLM host discover and invoke tools + resources hosted by external servers. JaiClaw treats MCP as a first-class exposure surface: any JaiClaw capability — a tool, a document repository, an agent — can be published as an MCP server with a small amount of code.

This document is the canonical reference for JaiClaw's MCP support. For the six canonical MCP integration patterns and how each is achieved with JaiClaw, see [mcp-design-patterns.md](mcp-design-patterns.md).

## What MCP is in JaiClaw

Every JaiClaw gateway hosts one or more **named MCP servers**, each mounted at `/mcp/{serverName}`. The gateway routes JSON-RPC over HTTP by default; the same providers can also be exposed over SSE or stdio.

- **Server surface** is defined by two SPIs: `McpToolProvider` (interactive tools the LLM invokes) and `McpResourceProvider` (read-only browsable content the LLM can list + read)
- **Discovery** is automatic — every Spring bean implementing either SPI is registered with `McpServerRegistry` at startup and mounted under its `getServerName()`
- **Requests are tenant-aware** — every `execute()` and `read()` call receives the current `TenantContext`, and providers are expected to enforce per-tenant isolation
- **The same capability can be exposed twice** — once as an in-process `ToolCallback` (agents in the same JVM call it directly), once as an `McpToolProvider` (external MCP clients call it via HTTP). Both paths share the underlying library code.

## Two SPIs

### `McpToolProvider` — interactive tools

**File:** [`core/jaiclaw-core/src/main/java/io/jaiclaw/core/mcp/McpToolProvider.java`](../../../core/jaiclaw-core/src/main/java/io/jaiclaw/core/mcp/McpToolProvider.java)

```java
public interface McpToolProvider {
    String getServerName();                                             // route name
    String getServerDescription();                                      // for MCP discovery
    List<McpToolDefinition> getTools();                                 // list at /mcp/{name}/tools
    McpToolResult execute(String toolName,
                          Map<String, Object> args,
                          TenantContext tenant);                        // POST /mcp/{name}/tools/{tool}
}
```

Each tool definition carries a name, description, and JSON Schema for its input. `McpToolResult` is a `record(String content, boolean isError, Map<String, Object> metadata)` — the same shape as MCP wire responses.

### `McpResourceProvider` — read-only content

**File:** [`core/jaiclaw-core/src/main/java/io/jaiclaw/core/mcp/McpResourceProvider.java`](../../../core/jaiclaw-core/src/main/java/io/jaiclaw/core/mcp/McpResourceProvider.java)

```java
public interface McpResourceProvider {
    String getServerName();
    String getServerDescription();
    List<McpResourceDefinition> getResources();                         // list at /mcp/{name}/resources
    Optional<McpResourceContent> read(String uri, TenantContext tenant); // POST /mcp/{name}/resources/read
}
```

Resources have opaque URIs (e.g. `docs://architecture`) and a MIME type. Use for documents, config, wiki pages, dataset previews — anything the LLM benefits from reading rather than invoking.

### Choosing between them

- **Interactive, side effects, LLM decides args**: `McpToolProvider`
- **Passive content, LLM only chooses what to read**: `McpResourceProvider`
- **Both**: implement both SPIs on the same class (perfectly legal — same `getServerName()` mounts them at the same route). See `DocsMcpToolProvider` + `DocsMcpResourceProvider` in `extensions/jaiclaw-docs/`.

## Gateway wiring

**File:** [`core/jaiclaw-gateway/src/main/java/io/jaiclaw/gateway/mcp/McpServerRegistry.java`](../../../core/jaiclaw-gateway/src/main/java/io/jaiclaw/gateway/mcp/McpServerRegistry.java)

- Two `ConcurrentHashMap`s (one for tool providers, one for resource providers), keyed by `getServerName()`
- Populated at startup via Spring's `List<McpToolProvider>` + `List<McpResourceProvider>` injection
- Exposes `get(serverName)` and `getResourceProvider(serverName)` for the controller

**File:** [`core/jaiclaw-gateway/src/main/java/io/jaiclaw/gateway/mcp/McpController.java`](../../../core/jaiclaw-gateway/src/main/java/io/jaiclaw/gateway/mcp/McpController.java) (`@RequestMapping("/mcp")`)

| Method | Route | Purpose |
|---|---|---|
| `GET` | `/mcp` | List all mounted servers (name + description) |
| `GET` | `/mcp/{serverName}/tools` | List tools for a server |
| `POST` | `/mcp/{serverName}/tools/{toolName}` | Invoke a tool |
| `GET` | `/mcp/{serverName}/resources` | List resources |
| `POST` | `/mcp/{serverName}/resources/read` | Read a specific resource by URI |

Every route resolves the current tenant via `TenantResolver` and passes it into the provider's `execute()` / `read()` call.

## Transports

JaiClaw as an MCP **host** (server) speaks HTTP by default via the gateway controller above. JaiClaw as an MCP **client** (calling remote MCP servers) supports three transports, all under `core/jaiclaw-gateway/src/main/java/io/jaiclaw/gateway/mcp/transport/`:

| Transport | JaiClaw class | Use when |
|---|---|---|
| HTTP | `HttpMcpToolProvider` | The remote MCP server is a plain HTTP endpoint (most public MCP servers) |
| SSE | `SseMcpToolProvider` | The remote server streams responses (long-running tool calls) |
| stdio | `StdioMcpToolProvider` | The remote server is a local subprocess (Claude Desktop pattern — see [CLAUDE-DESKTOP-MCP.md](../CLAUDE-DESKTOP-MCP.md)) |

All three implement the same `McpToolProvider` SPI, so a JaiClaw gateway can re-host remote MCP servers under its own `/mcp/{name}` routing without adopters knowing the underlying transport. This is the foundation of the **Hierarchical MCP** pattern in [mcp-design-patterns.md § 5](mcp-design-patterns.md#5-hierarchical-mcp).

## Multi-tenancy

Every SPI call receives a `TenantContext`. In `MULTI` tenant mode (`jaiclaw.tenant.mode=multi`), the framework fails closed on missing context — providers can trust the `tenant` parameter is non-null and belongs to an authenticated request.

Providers are expected to:
1. Scope all persistence to the current tenant (file paths, cache keys, DB rows)
2. Validate that any tenant-scoped identifier passed in `args` belongs to `tenant.getTenantId()` — defense-in-depth against cross-tenant argument spoofing
3. Include the tenant in any downstream audit record

See [`core/jaiclaw-core/src/main/java/io/jaiclaw/core/tenant/TenantGuard.java`](../../../core/jaiclaw-core/src/main/java/io/jaiclaw/core/tenant/TenantGuard.java) for the shared helper.

## `ToolCallback` vs `McpToolProvider`

Two distinct SPIs — same architectural role, different scopes:

| Concern | `ToolCallback` | `McpToolProvider` |
|---|---|---|
| Where it runs | In-process; agent calls the method directly | HTTP endpoint; MCP client marshals JSON over the wire |
| SPI location | [`core/jaiclaw-core/.../tool/ToolCallback.java`](../../../core/jaiclaw-core/src/main/java/io/jaiclaw/core/tool/ToolCallback.java) | [`core/jaiclaw-core/.../mcp/McpToolProvider.java`](../../../core/jaiclaw-core/src/main/java/io/jaiclaw/core/mcp/McpToolProvider.java) |
| Discovery | `ToolBeanDiscovery` auto-registers into `ToolRegistry` | `McpServerRegistry` auto-registers under `getServerName()` |
| Consumer | The LLM the JaiClaw agent is talking to | Any MCP client (Claude Desktop, another JaiClaw gateway, a downstream agent) |
| Input | `Map<String, Object> params` + `ToolContext` | Same shape (`Map<String, Object> args` + `TenantContext`) |
| Output | Typed `ToolResult.Success | Error` union | `McpToolResult(String, boolean isError, Map metadata)` |

**The two are complementary.** Most JaiClaw extensions implement both — the same underlying capability, exposed once per surface. The smallest reference implementation is:

- **In-process tool**: [`AsciiRenderTool.java`](../../../core/jaiclaw-tools/src/main/java/io/jaiclaw/tools/builtin/AsciiRenderTool.java) — `implements ToolCallback`
- **MCP surface**: [`AsciiRenderMcpToolProvider.java`](../../../core/jaiclaw-tools/src/main/java/io/jaiclaw/tools/builtin/mcp/AsciiRenderMcpToolProvider.java) — `implements McpToolProvider`, delegates to the same underlying `AsciiRenderTool`

The `SpringAiToolBridge` at [`core/jaiclaw-tools/src/main/java/io/jaiclaw/tools/bridge/SpringAiToolBridge.java`](../../../core/jaiclaw-tools/src/main/java/io/jaiclaw/tools/bridge/SpringAiToolBridge.java) adapts JaiClaw's `ToolCallback` to Spring AI's `ToolCallback` so agents built on Spring AI's ChatClient see JaiClaw tools transparently.

## Writing your own MCP server

Three steps. Total code: ~30 lines.

### Step 1 — Implement `McpToolProvider`

```java
public class WeatherMcpToolProvider implements McpToolProvider {

    @Override
    public String getServerName() { return "weather"; }

    @Override
    public String getServerDescription() {
        return "Current + forecast weather via NOAA.";
    }

    @Override
    public List<McpToolDefinition> getTools() {
        return List.of(
            new McpToolDefinition(
                "current",
                "Get the current weather at a location.",
                """
                {"type":"object","properties":{
                  "lat":{"type":"number"},"lon":{"type":"number"}
                },"required":["lat","lon"]}"""
            )
        );
    }

    @Override
    public McpToolResult execute(String toolName, Map<String, Object> args, TenantContext tenant) {
        if (!"current".equals(toolName)) {
            return McpToolResult.error("unknown tool: " + toolName);
        }
        double lat = ((Number) args.get("lat")).doubleValue();
        double lon = ((Number) args.get("lon")).doubleValue();
        // ... call NOAA, return McpToolResult.success(json)
        return McpToolResult.success("{\"tempF\":72}");
    }
}
```

### Step 2 — Register as a Spring bean

Any Spring `@Configuration` class in a module on the classpath:

```java
@Bean
public McpToolProvider weatherMcpToolProvider() {
    return new WeatherMcpToolProvider();
}
```

That's it — `McpServerRegistry` picks it up automatically via `List<McpToolProvider>` injection.

### Step 3 — Verify

After the gateway starts:

```bash
curl http://localhost:8080/mcp                                    # weather appears in the list
curl http://localhost:8080/mcp/weather/tools                      # current appears
curl -X POST http://localhost:8080/mcp/weather/tools/current \
    -H 'Content-Type: application/json' \
    -d '{"lat":38.9,"lon":-77.0}'                                 # returns your JSON
```

**Smallest complete reference impl in the repo:** [`AsciiRenderMcpToolProvider.java`](../../../core/jaiclaw-tools/src/main/java/io/jaiclaw/tools/builtin/mcp/AsciiRenderMcpToolProvider.java) (~50 lines, two tools). Read it end-to-end before writing your first custom provider.

## Existing MCP surfaces in the reactor

| Server name | Module | What it exposes |
|---|---|---|
| `ascii-render` | `core/jaiclaw-tools` | `ascii_render` (diagrams / plots), `ascii_box` (text box) |
| `docs` | `extensions/jaiclaw-docs` | Full-text search across bundled documentation + browsable doc resources |
| `messaging` | `extensions/jaiclaw-messaging` | 8 tools — send message, broadcast, list channels, agent chat, session mgmt |
| `agentmind-memory` | `extensions/jaiclaw-agentmind-memory` | Read + reflect on user memory (TENANT / AGENT / PEER scopes) |
| `agentmind-soul` | `extensions/jaiclaw-agentmind-soul` | Per-agent Soul markdown read/write |
| `calendar` | `extensions/jaiclaw-calendar` | Calendar CRUD, event scheduling |
| `cron-manager` | `extensions/jaiclaw-cron-manager` | Cron job create/list/delete/run |
| `github` | `extensions/jaiclaw-tools-github` | 12 tools — issue/PR/commit surface (see `docs/compliance/` for federal-context usage) |
| `discord` | `extensions/jaiclaw-discord-tools` | Discord message send, channel list |
| `kanban` | `extensions/jaiclaw-kanban` | Kanban board CRUD, column transitions |
| `voicecall` | `extensions/jaiclaw-voice-call` | Voice call initiation + status |
| `pipeline` | `extensions/jaiclaw-pipeline` | Pipeline trigger, status, render |
| `pipeline-authoring` | `extensions/jaiclaw-pipeline-authoring` | `pipeline_validate`, opt-in `pipeline_deploy` |
| `blueprints` | `extensions/jaiclaw-blueprints` | Automation blueprint discovery |
| *(plus MCP-mounted resource providers)* | | `docs`, and any adopter-registered `McpResourceProvider` |

## See also

- **[mcp-design-patterns.md](mcp-design-patterns.md)** — the six canonical MCP integration patterns (Direct API Wrapper, Composite Service, MCP-to-Agent, Event-Driven Integration, Hierarchical MCP, Local Resource Access) with ASCII diagrams and JaiClaw exemplar code
- **[CLAUDE-DESKTOP-MCP.md](../CLAUDE-DESKTOP-MCP.md)** — recipe for exposing a JaiClaw MCP server as a standalone JBang stdio server (Claude Desktop compatibility, no full JaiClaw runtime needed)
- **[docs/dev/ARCHITECTURE.md](../../dev/ARCHITECTURE.md)** — gateway architecture, how MCP fits alongside the shell, chat, and channel dispatch surfaces
- **[docs/compliance/README.md](../../compliance/README.md)** — MCP hosting posture across the eight regulatory frameworks (Section 508, FedRAMP, FISMA, NIST 800-53, FIPS 140-3, CMMC, HIPAA, GDPR); tenant metadata + audit trail apply uniformly to MCP calls
