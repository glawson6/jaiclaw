# ASCII Rendering

Module: `jaiclaw-ascii-render`

## Overview

`jaiclaw-ascii-render` is a pure-Java library for producing ASCII art —
boxes, diagrams, lines, plots, tables — from a declarative scene
description. Two surfaces ship on top of it:

- **Default agent tools** — `ascii_render` and `ascii_box` are
  registered in `BuiltinTools.all(...)` so every JaiClaw agent can draw
  diagrams without extra wiring.
- **Library factory** — `AsciiSceneFactory` takes a `Map<String, Object>`
  or a JSON string and returns ASCII text. Callable from any code (web
  app, batch script, custom tool, MCP server, scratch `main`).

The library has no Spring dependency. Its only runtime deps are SLF4J
and `jackson-databind` (for `fromJson` / `renderJson`).

---

## For agent users

Two tools in the **Rendering** section, both with profiles
`{FULL, CODING, MESSAGING}` so they're available everywhere agents
typically run.

| Tool | Description |
|------|-------------|
| `ascii_render` | Render a declarative scene (rectangles, lines, labels, text blocks, dots, circles, ellipses, tables, scatter plots) to ASCII art. |
| `ascii_box` | Quick "wrap text in a Unicode box" with four border styles and an optional title. |

### `ascii_box` — the shortcut

For the common "put this text in a box and stop hand-drawing borders"
case:

```
ascii_box: {
  "content": "Build green — all tests passing.",
  "title": "STATUS",
  "border": "double"
}
```

Output:

```
╔════════════════════════════════════════════════════════════╗
║[ STATUS ]                                                  ║
║Build green — all tests passing.                            ║
╚════════════════════════════════════════════════════════════╝
```

Parameters:

- `content` *(required)* — text to wrap; embedded newlines honoured,
  long lines wrap at word boundaries
- `width` *(default 60)* — maximum inner width; the box itself is two
  characters wider; clamped to `[4, 500]`
- `border` *(default `single`)* — one of `single`, `double`, `bold`,
  `rounded`. Unknown styles log a warning and fall back to `single`
- `title` *(optional)* — rendered on the top edge as `[ title ]`

### `ascii_render` — the scene tool

For everything more structured than a box. The LLM hands the tool a
scene: canvas size + an ordered list of elements drawn in z-order.

```
ascii_render: {
  "width": 40,
  "height": 5,
  "elements": [
    {"type": "rectangle"},
    {"type": "label", "params": {"text": "Hello, JaiClaw", "x": 12, "y": 2}}
  ]
}
```

Output:

```
┌──────────────────────────────────────┐
│                                      │
│           Hello, JaiClaw             │
│                                      │
└──────────────────────────────────────┘
```

Top-level parameters:

| Key | Required | Description |
|---|---|---|
| `width` | yes | canvas width in characters |
| `height` | yes | canvas height in characters |
| `elements` | yes | list of `{type, params}` entries |
| `trim` | no (default `true`) | trim trailing whitespace from each rendered line |

#### Supported element types

Every element accepts a `params` object. Position/size keys are
optional — when absent the element falls back to a sensible default
(centred, full-canvas, etc.) via the library's `Integer.MIN_VALUE`
"auto" sentinel.

| Type | Required params | Optional params |
|---|---|---|
| `rectangle` | — | `x`, `y`, `width`, `height` |
| `line` | `x1`, `y1`, `x2`, `y2` | `pen` (single character, default `●`) |
| `label` | `text` | `x`, `y`, `width` (single-line; truncated with `…` if too long) |
| `text` | `text` | `x`, `y`, `width`, `height` (multi-line; word-wraps inside the region) |
| `dot` | — | `x`, `y` |
| `circle` | — | `x`, `y`, `radius` |
| `ellipse` | — | `x`, `y`, `width`, `height` |
| `table` | `rows`, `columns` (both > 0) | `x`, `y`, `width`, `height` |
| `plot` | `points` (non-empty list of `[x, y]` pairs), `width`, `height` (both > 0) | `x`, `y` |

#### A real scene — call-graph diagram

```
ascii_render: {
  "width": 60, "height": 12,
  "elements": [
    {"type": "rectangle", "params": {"x": 0,  "y": 0, "width": 18, "height": 5}},
    {"type": "label",     "params": {"text": "  agent",     "x": 1, "y": 1}},
    {"type": "label",     "params": {"text": "  io.jaiclaw","x": 1, "y": 3}},
    {"type": "rectangle", "params": {"x": 42, "y": 0, "width": 18, "height": 5}},
    {"type": "label",     "params": {"text": "  mcp server", "x": 43, "y": 1}},
    {"type": "line",      "params": {"x1": 18, "y1": 2, "x2": 42, "y2": 2, "pen": "─"}},
    {"type": "label",     "params": {"text": "tool calls", "x": 23, "y": 1}}
  ]
}
```

```
┌────────────────┐                        ┌────────────────┐
│  agent         │     tool calls         │  mcp server    │
│                │─────────────────────────                │
│  io.jaiclaw    │                        │                │
└────────────────┘                        └────────────────┘
```

#### Errors

Bad input returns a `ToolResult.Error` with a precise message:

- Canvas-level: `"Missing or non-positive 'width'."`,
  `"'elements' must be a list of element objects."`
- Per-element: `"Element[3] (table): 'columns' and 'rows' must be
  positive."`
- Unknown type: `"Element[2]: unknown element type 'marshmallow'."`

The error format is stable; downstream tooling can parse the index.

---

## For developers

`AsciiSceneFactory` is the public facade. Three entry points cover
every consumption pattern:

```java
import io.jaiclaw.asciirender.factory.AsciiSceneFactory;
import io.jaiclaw.asciirender.factory.SceneSpec;

// 1. From a Map<String, Object> — typical for Spring MessageConverter
//    output, MCP arguments, or any code that already has a parsed map.
SceneSpec scene = AsciiSceneFactory.fromMap(Map.of(
    "width", 40,
    "height", 5,
    "elements", List.of(
        Map.of("type", "rectangle"),
        Map.of("type", "label",
               "params", Map.of("text", "Hello", "x", 15, "y", 2)))));
String art = AsciiSceneFactory.render(scene);

// 2. From a JSON string — web apps, batch scripts, anywhere the input
//    arrives as JSON text.
String art2 = AsciiSceneFactory.renderJson("""
    {"width": 40, "height": 5,
     "elements": [{"type": "rectangle"},
                  {"type": "label",
                   "params": {"text": "Hello", "x": 15, "y": 2}}]}
    """);

// 3. Hand-construct a SceneSpec for fully typed input.
SceneSpec scene2 = new SceneSpec(40, 5, List.of(
    ElementSpec.of("rectangle"),
    new ElementSpec("label", Map.of("text", "Hello", "x", 15, "y", 2))));
String art3 = AsciiSceneFactory.render(scene2);
```

### Advanced — mutate before rendering

`toContext(SceneSpec)` exposes the intermediate `IContext` so callers
can append extra elements at runtime before drawing:

```java
IContext ctx = AsciiSceneFactory.toContext(scene);
// add more elements via the library builder if needed,
// then render manually:
String art = new Render().render(ctx).getText();
```

### Public types

| Class | Type | Description |
|---|---|---|
| `SceneSpec` | record | Immutable scene: `width`, `height`, `elements`, `trim` |
| `ElementSpec` | record | `{type, params}` — one entry in the scene |
| `AsciiSceneFactory` | facade | `fromMap`, `fromJson`, `toContext`, `render`, `renderJson` |
| `SceneSpecException` | exception | Extends `IllegalArgumentException`; carries `elementIndex` + `elementType` for structured error reporting |

### Direct library use (no JSON)

For Java callers that don't want to round-trip through a map, the
underlying fluent API is unchanged:

```java
import io.jaiclaw.asciirender.core.*;
import io.jaiclaw.asciirender.element.*;

String art = new Render().render(
    Render.builder()
        .width(40).height(5)
        .layer(new Region(0, 0, 40, 5))
          .element(new Rectangle())
          .element(new Label("Hello", 15, 2))
        .build())
    .trim().getText();
```

---

## As a skill

The renderer is exposed as both a JaiClaw skill and a portable Anthropic
Claude Skill — same content, two delivery models.

### JaiClaw skill (in-prompt instruction module)

`core/jaiclaw-skills/src/main/resources/skills/ascii-rendering/SKILL.md`
ships with the framework. Like every bundled skill it's gated by
`jaiclaw.skills.allow-bundled` and is `alwaysInclude: false` so it
doesn't burn tokens on every request — it's available via
`/skills` listing and search so the LLM can pull it in when relevant.

The skill body teaches the LLM: when to choose `ascii_box` vs
`ascii_render` vs the canvas tools, the full element catalogue with
required + optional params, three reference scenes (boxed status,
two-box-and-arrow, scatter plot), and how to read the structured
error messages.

To pin the skill on for an agent that should always have it:

```yaml
jaiclaw:
  skills:
    allow-bundled: [ascii-rendering]
```

### Anthropic Claude Skill (portable JBang pack)

For Claude clients outside JaiClaw — claude.ai, Claude Desktop,
Claude Code — the renderer also ships as a self-contained Claude Skill
zip under `core/jaiclaw-ascii-render/skill-pack/`. The zip contains:

- `SKILL.md` — Anthropic's frontmatter (`name`, `description`) plus
  usage docs Claude reads when deciding to invoke the skill.
- `scripts/RenderScene.java` and `scripts/RenderBox.java` — JBang
  shebanged single-file Java programs. `//DEPS` pulls
  `io.jaiclaw:jaiclaw-ascii-render` from Maven (or the TapTech Nexus
  for SNAPSHOT builds) on first run; cached at `~/.jbang/cache/`.
- `examples/*.json` — three reference scenes Claude can adapt.

Claude calls them via its code-execution tool:

```
echo "Build green" | jbang scripts/RenderBox.java --title=STATUS --border=double
jbang scripts/RenderScene.java --file /tmp/scene.json
```

Build the upload zip:

```bash
cd core/jaiclaw-ascii-render/skill-pack
./build-skill-zip.sh
# → dist/ascii-rendering-skill-0.1.0.zip
```

The host must have JBang installed (`brew install jbang` or
`curl -Ls https://sh.jbang.dev | bash`). JBang manages the Java
runtime automatically — no separate JDK install. First invocation
downloads ~3 MB; subsequent calls start in well under a second.

## Architecture

```
AsciiRenderTool / AsciiBoxTool        AsciiRenderMcpToolProvider
       (built-in tools)                (external MCP clients)
              │                                   │
              └─────────────┬─────────────────────┘
                            ↓
                  AsciiSceneFactory  (facade — fromMap/fromJson/render)
                            │
            ┌───────────────┴───────────────┐
            ↓                               ↓
       SceneSpec                       ElementBuilders
       (record)                        (dispatch + per-type builders)
                                            │
                                            ↓
                                  io.jaiclaw.asciirender.element.*
                                  Rectangle, Line, Label, Text, Dot,
                                  Circle, Ellipse, Table, Plot
                                            │
                                            ↓
                                  Render + Canvas (library core)
                                            │
                                            ↓
                                       String (ASCII)
```

- **Library core** (`io.jaiclaw.asciirender.{api, core, element, element.plot}`) —
  pure Java, only SLF4J + Jackson at runtime.
- **Factory** (`io.jaiclaw.asciirender.factory`) — JSON/map dispatch,
  same module as the library.
- **Tools** (`io.jaiclaw.tools.builtin.AsciiRenderTool`,
  `AsciiBoxTool`) — thin wrappers in `jaiclaw-tools` that delegate to
  the factory.
- **MCP** (`io.jaiclaw.tools.builtin.mcp.AsciiRenderMcpToolProvider`) —
  exposes both tools at `/mcp/ascii-render` for external clients.

The tool and the MCP path share the factory; they emit identical
output for identical input (verified by spec).

## Configuration

No required configuration. The tools are default-on whenever
`jaiclaw-tools` is on the classpath (which is the standard JaiClaw
starter). The MCP provider auto-registers when `jaiclaw-gateway` is
also present.

Opt out of the MCP exposure:

```yaml
jaiclaw:
  tools:
    ascii-render:
      mcp:
        enabled: false
```

## Limitations

- ASCII only. No colour, no Unicode beyond box-drawing glyphs. Use
  [Canvas (A2UI)](CANVAS.md) for rich HTML output.
- One scene per call; no streaming or partial rendering.
- The `plot` element is a scatter plot with `*` markers. Line plots,
  bar charts, and styled markers are not built in.
- AWT-backed bitmap fonts from the upstream reference were dropped
  during the port to keep the library AWT-free.
