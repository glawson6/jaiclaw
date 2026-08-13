# Status Glyphs

The `glyph` scene element renders a named Unicode marker at (x, y).
Every JaiClaw app that pulls `jaiclaw-ascii-render` gets a curated
built-in vocabulary — the same set `install.sh`, `JaiClaw.java`, and
`bin/jaiclaw` already emit — so scenes look visually consistent with
the rest of the shell surface.

Glyphs are **plain Unicode text** written into the canvas. Color is a
separate opt-in decoration applied by `AnsiPalette` to the rendered
output — the canvas itself stays character-per-cell.

## Built-in vocabulary

| Name(s) | Char | Semantic class | Use |
|---|---|---|---|
| `ok`, `check` | `✓` | SUCCESS | Passing check, completed task |
| `fail`, `cross` | `✗` | ERROR | Failed step, blocker |
| `warn`, `bang` | `!` | WARNING | Simple warning |
| `warning` | `⚠` | WARNING | Prominent warning triangle |
| `info`, `arrowhead` | `▸` | INFO | Note, informational bullet |
| `arrow` | `▶` | INFO | Right-pointing action arrow |
| `bullet`, `dot` | `●` | NEUTRAL | Filled list bullet |
| `star` | `★` | INFO | Highlight, favourite |
| `pending`, `hourglass` | `⧗` | NEUTRAL | Task in progress |
| `question` | `?` | INFO | Unknown / prompt |

Aliases (`check`, `cross`, `dot`, `hourglass`, `arrowhead`, `bang`) all
resolve to the same glyph as the primary name — pick whichever reads
better in your scene spec.

## Using a glyph in a scene

```json
{
  "width": 40,
  "height": 3,
  "elements": [
    {"type": "glyph", "params": {"x": 2, "y": 1, "name": "ok"}},
    {"type": "label", "params": {"text": "gateway ready", "x": 5, "y": 1}}
  ]
}
```

Renders (with the canvas box omitted):

```
  ✓  gateway ready
```

## Examples

Five reference scenes live under `docs/user/agent-diagrams/` with
their checked-in `.txt` renderings. Regenerate any of them via
`./render.sh <name>` from that directory.

### 1. System status grid — `glyph-example.json`

Mixed status flags for a service dashboard. Uses `ok`, `warning`,
`fail`, `pending`, `info`.

```json
{
  "width": 60, "height": 10,
  "elements": [
    {"type": "label",     "params": {"text": "SYSTEM STATUS", "x": 2, "y": 0}},
    {"type": "rectangle", "params": {"x": 0, "y": 1, "width": 60, "height": 9}},
    {"type": "glyph", "params": {"x": 3, "y": 3, "name": "ok"}},
    {"type": "label", "params": {"text": "gateway     ready", "x": 6, "y": 3}},
    {"type": "glyph", "params": {"x": 3, "y": 4, "name": "ok"}},
    {"type": "label", "params": {"text": "auth        ready", "x": 6, "y": 4}},
    {"type": "glyph", "params": {"x": 3, "y": 5, "name": "warning"}},
    {"type": "label", "params": {"text": "cache       stale", "x": 6, "y": 5}},
    {"type": "glyph", "params": {"x": 3, "y": 6, "name": "fail"}},
    {"type": "label", "params": {"text": "queue       offline", "x": 6, "y": 6}},
    {"type": "glyph", "params": {"x": 3, "y": 7, "name": "pending"}},
    {"type": "label", "params": {"text": "migration   running", "x": 6, "y": 7}},
    {"type": "glyph", "params": {"x": 3, "y": 8, "name": "info"}},
    {"type": "label", "params": {"text": "docs        updated", "x": 6, "y": 8}}
  ]
}
```

Renders to:

```
  SYSTEM STATUS
┌──────────────────────────────────────────────────────────┐
│                                                          │
│  ✓  gateway     ready                                    │
│  ✓  auth        ready                                    │
│  ⚠  cache       stale                                    │
│  ✗  queue       offline                                  │
│  ⧗  migration   running                                  │
│  ▸  docs        updated                                  │
└──────────────────────────────────────────────────────────┘
```

### 2. Release checklist — `glyph-checklist.json`

Task list with mixed pass / pending / fail states — the classic
"where do we stand right now" screen for CI or a release captain.

```json
{
  "width": 56, "height": 10,
  "elements": [
    {"type": "label",     "params": {"text": "RELEASE 1.0.2 CHECKLIST", "x": 2, "y": 0}},
    {"type": "rectangle", "params": {"x": 0, "y": 1, "width": 56, "height": 9}},
    {"type": "glyph", "params": {"x": 3, "y": 3, "name": "ok"}},
    {"type": "label", "params": {"text": "compile all modules", "x": 6, "y": 3}},
    {"type": "glyph", "params": {"x": 3, "y": 4, "name": "ok"}},
    {"type": "label", "params": {"text": "unit tests (2,341 passing)", "x": 6, "y": 4}},
    {"type": "glyph", "params": {"x": 3, "y": 5, "name": "pending"}},
    {"type": "label", "params": {"text": "integration tests", "x": 6, "y": 5}},
    {"type": "glyph", "params": {"x": 3, "y": 6, "name": "fail"}},
    {"type": "label", "params": {"text": "dep vulnerability scan", "x": 6, "y": 6}},
    {"type": "glyph", "params": {"x": 3, "y": 7, "name": "question"}},
    {"type": "label", "params": {"text": "release notes review", "x": 6, "y": 7}},
    {"type": "glyph", "params": {"x": 3, "y": 8, "name": "pending"}},
    {"type": "label", "params": {"text": "sign + push artifacts", "x": 6, "y": 8}}
  ]
}
```

Renders to:

```
  RELEASE 1.0.2 CHECKLIST
┌──────────────────────────────────────────────────────┐
│                                                      │
│  ✓  compile all modules                              │
│  ✓  unit tests (2,341 passing)                       │
│  ⧗  integration tests                                │
│  ✗  dep vulnerability scan                           │
│  ?  release notes review                             │
│  ⧗  sign + push artifacts                            │
└──────────────────────────────────────────────────────┘
```

### 3. Pipeline flow — `glyph-progress.json`

`arrow` glyphs mark transitions; `bullet` marks completed stages,
`pending` marks the running stage, `question` marks the gated one.

```json
{
  "width": 66, "height": 6,
  "elements": [
    {"type": "label", "params": {"text": "BUILD PIPELINE", "x": 2, "y": 0}},
    {"type": "glyph", "params": {"x": 2,  "y": 2, "name": "bullet"}},
    {"type": "label", "params": {"text": "fetch", "x": 4, "y": 2}},
    {"type": "glyph", "params": {"x": 11, "y": 2, "name": "arrow"}},
    {"type": "glyph", "params": {"x": 14, "y": 2, "name": "bullet"}},
    {"type": "label", "params": {"text": "compile", "x": 16, "y": 2}},
    {"type": "glyph", "params": {"x": 25, "y": 2, "name": "arrow"}},
    {"type": "glyph", "params": {"x": 28, "y": 2, "name": "bullet"}},
    {"type": "label", "params": {"text": "test", "x": 30, "y": 2}},
    {"type": "glyph", "params": {"x": 36, "y": 2, "name": "arrow"}},
    {"type": "glyph", "params": {"x": 39, "y": 2, "name": "pending"}},
    {"type": "label", "params": {"text": "package", "x": 41, "y": 2}},
    {"type": "glyph", "params": {"x": 51, "y": 2, "name": "arrow"}},
    {"type": "glyph", "params": {"x": 54, "y": 2, "name": "question"}},
    {"type": "label", "params": {"text": "deploy", "x": 56, "y": 2}},
    {"type": "label", "params": {"text": "● complete  ⧗ running  ? gated", "x": 2, "y": 4}}
  ]
}
```

Renders to:

```
BUILD PIPELINE

● fetch  ▶  ● compile  ▶  ● test  ▶  ⧗ package   ▶  ? deploy

● complete  ⧗ running  ? gated
```

### 4. Vocabulary reference — `glyph-legend.json`

A rendered cheat-sheet of every built-in name. Useful embedded in
docs or shell `--help` output.

```
  BUILT-IN GLYPH VOCABULARY
┌──────────────────────────────────────────────────────────┐
│                                                          │
│  ✓  ok / check       (SUCCESS)                           │
│  ✗  fail / cross     (ERROR)                             │
│  !  warn / bang      (WARNING)                           │
│  ⚠  warning          (WARNING)                           │
│  ▸  info / arrowhead (INFO)                              │
│  ▶  arrow            (INFO)                              │
│  ●  bullet / dot     (NEUTRAL)                           │
│  ★  star             (INFO)                              │
│  ⧗  pending / hourglass (NEUTRAL)                        │
│  ?  question         (INFO)                              │
└──────────────────────────────────────────────────────────┘
```

See `glyph-legend.json` for the full spec.

### 5. Literal glyphs (no registry lookup) — `glyph-custom-emoji.json`

The `glyph` element also accepts a **literal** character + optional
`semanticClass` — handy for a one-off character that doesn't warrant
a `GlyphContribution` registration.

```json
{
  "width": 56, "height": 8,
  "elements": [
    {"type": "label",     "params": {"text": "DEPLOYMENT TARGETS", "x": 2, "y": 0}},
    {"type": "rectangle", "params": {"x": 0, "y": 1, "width": 56, "height": 7}},
    {"type": "glyph", "params": {"x": 3, "y": 3, "glyph": "☁", "semanticClass": "INFO"}},
    {"type": "label", "params": {"text": "aws-prod-us-east", "x": 6, "y": 3}},
    {"type": "glyph", "params": {"x": 3, "y": 4, "glyph": "☁", "semanticClass": "INFO"}},
    {"type": "label", "params": {"text": "gcp-staging-eu", "x": 6, "y": 4}},
    {"type": "glyph", "params": {"x": 3, "y": 5, "glyph": "⌂", "semanticClass": "NEUTRAL"}},
    {"type": "label", "params": {"text": "onprem-dc-1", "x": 6, "y": 5}},
    {"type": "glyph", "params": {"x": 3, "y": 6, "glyph": "⚡", "semanticClass": "SUCCESS"}},
    {"type": "label", "params": {"text": "edge-worker-fast", "x": 6, "y": 6}}
  ]
}
```

Renders to:

```
  DEPLOYMENT TARGETS
┌──────────────────────────────────────────────────────┐
│                                                      │
│  ☁  aws-prod-us-east                                 │
│  ☁  gcp-staging-eu                                   │
│  ⌂  onprem-dc-1                                      │
│  ⚡  edge-worker-fast                                 │
└──────────────────────────────────────────────────────┘
```

The `semanticClass` value (SUCCESS / WARNING / ERROR / INFO / NEUTRAL /
DECORATIVE) tells `AnsiPalette` how to colourise the glyph when the
rendered output is piped through the terminal colorizer — so `⚡`
appears green in a colour-capable terminal and stays plain in a pipe
or Markdown viewer.

### From Java

The same scenes work through the programmatic API:

```java
import io.jaiclaw.asciirender.factory.AsciiSceneFactory;
import java.util.List;
import java.util.Map;

String rendered = AsciiSceneFactory.render(Map.of(
    "width",  30,
    "height", 3,
    "elements", List.of(
        Map.of("type", "glyph", "params", Map.of("x", 2, "y", 1, "name", "ok")),
        Map.of("type", "label", "params", Map.of("text", "gateway ready", "x", 5, "y", 1))
    )
));
System.out.println(rendered);
```

### Through the MCP `ascii_render` tool

Adopters running a JaiClaw gateway can invoke the same shapes over
`POST /mcp/ascii-render` with the standard `tools/call` envelope:

```bash
curl -sS -X POST http://localhost:8080/mcp/ascii-render \
  -H 'Content-Type: application/json' \
  -d '{
    "jsonrpc": "2.0", "id": 1, "method": "tools/call",
    "params": {
      "name": "ascii_render",
      "arguments": {
        "width": 30, "height": 3,
        "elements": [
          {"type": "glyph", "params": {"x": 2, "y": 1, "name": "ok"}},
          {"type": "label", "params": {"text": "gateway ready", "x": 5, "y": 1}}
        ]
      }
    }
  }' | jq -r '.result.content[0].text'
```

## Adding your own glyphs

Adopters register additional glyphs from any Spring Boot app by
declaring a `GlyphContribution` bean. The framework's
`GlyphRegistryAutoConfiguration` collects every bean of that type from
the context and merges them over the built-in defaults.

```java
import io.jaiclaw.asciirender.glyph.*;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppGlyphs {

    @Bean
    GlyphContribution appGlyphs() {
        return () -> List.of(
            new GlyphDefinition("pass",   "✅",
                GlyphSemanticClass.SUCCESS, "Emoji check mark"),
            new GlyphDefinition("onprem", "🏢",
                GlyphSemanticClass.NEUTRAL, "On-premises building"),
            new GlyphDefinition("thunder","⚡",
                GlyphSemanticClass.INFO,    "Fast path")
        );
    }
}
```

Then the LLM (or your own scene JSON) can address the new names:

```json
{"type": "glyph", "params": {"x": 0, "y": 0, "name": "thunder"}}
```

**Overriding a built-in** — legitimate; the registry uses last-write-
wins, and the framework logs an INFO line naming the responsible
contribution so operators can trace visual regressions back to it.

## Colouring the rendered output

`AsciiSceneFactory.render(...)` returns plain Unicode text. That's the
right form for embedding in Markdown docs, log files, and MCP tool
results. When you want colour in a terminal, pipe the output through
`AnsiPalette`:

```java
import io.jaiclaw.asciirender.factory.AsciiSceneFactory;
import io.jaiclaw.asciirender.glyph.AnsiPalette;
import io.jaiclaw.asciirender.glyph.GlyphRegistry;

String rendered  = AsciiSceneFactory.render(scene);
String colorized = AnsiPalette.defaultPalette()
        .colorize(rendered, GlyphRegistry.global());
System.out.print(colorized);
```

The default palette matches the exact ANSI codes in `install.sh` and
`JaiClaw.java`:

| Semantic class | ANSI code | Foreground |
|---|---|---|
| SUCCESS    | `\033[0;32m` | green |
| ERROR      | `\033[0;31m` | red |
| WARNING    | `\033[1;33m` | bold yellow |
| INFO       | `\033[0;36m` | cyan |
| NEUTRAL    | *(none)*     | (default) |
| DECORATIVE | *(none)*     | (default) |

Adopters swap individual codes without replacing the whole palette:

```java
AnsiPalette bright = AnsiPalette.defaultPalette()
        .withCode(GlyphSemanticClass.SUCCESS, "\033[1;92m");   // bright green
```

## Terminal detection: NO_COLOR + isatty

`AnsiPalette.defaultPalette()` inherits its color-enabled state from
`AnsiSupport.isColorEnabled()`, which is `true` only when both:

- The `NO_COLOR` environment variable is unset (see
  [no-color.org](https://no-color.org) — a widely-respected
  convention).
- `System.console()` returns non-null — i.e., stdout is a real TTY,
  not a pipe / file redirect / IDE console.

Piped output (`app | cat`), redirected output (`app > log.txt`), and
CI logs all get plain Unicode; the same code that colours on a
terminal produces byte-identical Markdown-safe output everywhere else.

Force the colour flag explicitly (tests, or an adopter that knows
their target terminal handles ANSI):

```java
AnsiPalette forced = AnsiPalette.defaultPalette().withColorEnabled(true);
```

## Related shell helpers

For shell scripts, the same look is available via `install.sh`'s
`ok()` / `warn()` / `err()` / `info()` helpers — they emit the same
glyphs and ANSI codes, so a mixed Java + bash pipeline looks
consistent. Search for `RED=` in `install.sh` and `JaiClaw.java` for
the reference palette.
