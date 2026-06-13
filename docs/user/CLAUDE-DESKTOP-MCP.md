# Using JaiClaw Tools from Claude Desktop

Claude Desktop supports custom tools via MCP (Model Context Protocol) — any command-line program that speaks JSON-RPC 2.0 over stdin/stdout can be registered as a tool server. This page shows how to expose JaiClaw's Java tools to Desktop using a single JBang script.

## Why JBang

JBang scripts are single Java files with declarative dependencies — no Maven project, no jar build step, no Spring Boot. You point JBang at a `.java` file and it:

1. Resolves `//DEPS` declarations from Maven Central (or a configured Nexus).
2. Compiles the script.
3. Runs it as a normal Java program.

The result is the lightest possible distribution: one file, one path in `claude_desktop_config.json`. Desktop launches `jbang /path/to/Script.java` and the user gets MCP tools.

## Reference implementation: ascii-render

The JaiClaw repo ships a working example at:

```
core/jaiclaw-ascii-render/skill-pack/plugins/ascii-rendering/skills/ascii-rendering/scripts/AsciiRenderMcpServer.java
```

It exposes two MCP tools:

- **`ascii_box`** — wrap text in a Unicode-bordered box (status messages, callouts, banners). Supports four border styles and an optional title.
- **`ascii_render`** — render a structured ASCII scene (rectangles, lines, labels, plots, tables) from a canvas spec.

The script is ~250 lines, has no Spring Boot, and reuses the same library code (`io.jaiclaw.asciirender.factory.AsciiBox`, `AsciiSceneFactory`) that the in-process built-in tools use. Wire format is identical to the equivalent MCP endpoints the JaiClaw gateway hosts at `/mcp/ascii-render/*`.

## Install the ascii-render tools in Claude Desktop

### Prerequisites

- **JBang** must be on the PATH that Claude Desktop sees:
  ```bash
  brew install jbang     # or: curl -Ls https://sh.jbang.dev | bash
  which jbang            # note the absolute path — needed below
  ```
- **Java 21** — JBang installs and manages this automatically on first run; no manual setup.

### Step 1 — Warm the JBang dependency cache

JBang resolves `//DEPS` on first run. Do this once from a terminal so:

- Maven authentication (`~/.m2/settings.xml`) is used to fetch artifacts from private repos like `tooling.taptech.net`.
- Subsequent launches start in well under a second from `~/.jbang/cache/`.

```bash
echo '{"jsonrpc":"2.0","id":1,"method":"initialize"}' \
  | jbang /absolute/path/to/jaiclaw/core/jaiclaw-ascii-render/skill-pack/plugins/ascii-rendering/skills/ascii-rendering/scripts/AsciiRenderMcpServer.java
```

You should see one JSON line on stdout describing the server's capabilities. If you see auth or download errors, fix them now — Claude Desktop will hit the same paths.

### Step 2 — Edit `claude_desktop_config.json`

Open Desktop's config:

| Platform | Path |
|---|---|
| macOS | `~/Library/Application Support/Claude/claude_desktop_config.json` |
| Windows | `%APPDATA%\Claude\claude_desktop_config.json` |
| Linux | `~/.config/Claude/claude_desktop_config.json` |

Add (or merge into) an `mcpServers` block:

```json
{
  "mcpServers": {
    "ascii-render": {
      "command": "/opt/homebrew/bin/jbang",
      "args": [
        "/absolute/path/to/jaiclaw/core/jaiclaw-ascii-render/skill-pack/plugins/ascii-rendering/skills/ascii-rendering/scripts/AsciiRenderMcpServer.java"
      ]
    }
  }
}
```

Important: use the **absolute path** to `jbang`. Desktop on macOS does not inherit your shell PATH, so a bare `"command": "jbang"` will fail with "command not found". Find the right path with `which jbang`.

### Step 3 — Restart Claude Desktop

Fully quit (⌘Q on macOS) and relaunch. In a new conversation, open the tools palette — `ascii-render` should list the two tools above.

### Try it

Prompt Desktop:

> Draw me a status box that says "ready for production" using `ascii_box` with a double border and a title of STATUS.

Desktop calls the tool and pastes the rendered box inline.

## Building your own JBang + MCP server for other JaiClaw tools

The `AsciiRenderMcpServer.java` script is a working template. To expose a different JaiClaw tool to Desktop:

1. **Copy the script** to a new file (e.g., `MyToolMcpServer.java`).
2. **Update `//DEPS`** to reference the JaiClaw module containing your tool.
3. **Replace the tool schemas** in `asciiBoxToolSchema()` / `asciiRenderToolSchema()` with your tool's JSON Schema.
4. **Replace the `tools/call` dispatch** in `callAsciiBox()` / `callAsciiRender()` with calls into your tool's library API.
5. **Register the script in `claude_desktop_config.json`** under a new server name.

### Required MCP methods

A minimal MCP server must respond to these JSON-RPC 2.0 methods:

| Method | When | Response |
|---|---|---|
| `initialize` | Desktop boot | `{protocolVersion, capabilities:{tools:{}}, serverInfo:{name,version}}` |
| `notifications/initialized` | After init | None — it's a notification (no `id`), return `null` from your handler |
| `tools/list` | Tool palette load | `{tools: [{name, description, inputSchema}]}` |
| `tools/call` | User invokes tool | `{content: [{type:"text", text:"..."}], isError: boolean}` |
| `ping` | Liveness check | Empty object `{}` |

### Critical rules

1. **stdout must be pure JSON-RPC.** Every log line, banner, or warning goes to **stderr** or Desktop will reject the stream as malformed.
2. **Notifications get no response.** Methods with no `id` field return `null` from your handler — do not write anything to stdout.
3. **One message per line.** Use `println`, never pretty-printed multi-line JSON.
4. **Tool errors go in the `result`, not `error`.** A failed `tools/call` returns `result: {content: [...], isError: true}`. JSON-RPC `error` blocks are reserved for protocol failures (unknown method, malformed params).

## Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| Tools don't appear after restart | Server crashed at startup | Check `~/Library/Logs/Claude/mcp-server-<name>.log` — first line should be your script's `[ready]` marker |
| "jbang: command not found" in logs | Desktop doesn't have shell PATH | Use absolute path to `jbang` in `command` (find with `which jbang`) |
| "Could not resolve" or auth errors | Maven settings missing for private repo | Run step 1 from a terminal with working `~/.m2/settings.xml`; the cache is reused |
| Stream rejected by Desktop | Something writing to stdout besides JSON-RPC | Redirect all logs to stderr; ensure `println` (not `print`) for each response |
| Tool returns no output | Response missing `content` array | `result` must be `{content: [{type:"text", text:"..."}], isError: bool}` |

## See also

- **[ASCII rendering reference](features/RENDERING.md)** — the in-process built-in tool surface (same library, called from JaiClaw agents instead of Desktop).
- **[Operations guide](OPERATIONS.md)** — running the full JaiClaw gateway, which hosts the same tools at `/mcp/ascii-render/*` as a long-running HTTP/SSE server (alternative to the stdio script for production setups).
- **[Authoring tools](AUTHORING-TOOLS.md)** — how to write new JaiClaw built-in tools (which then can be exposed via this pattern).
