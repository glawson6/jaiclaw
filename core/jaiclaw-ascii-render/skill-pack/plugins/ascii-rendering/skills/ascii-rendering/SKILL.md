---
name: ascii-rendering
description: This skill should be used when the user asks to "draw a diagram", "show a status box", "render ASCII art", "make a callout", "wrap text in a box", "plot these numbers", or any request involving boxed messages, two-box-and-arrow diagrams, scatter plots, or tables rendered as ASCII. Uses JBang to invoke the JaiClaw ASCII renderer; do not hand-draw borders character-by-character.
version: 0.1.0
---

# ASCII Rendering

This skill lets you render structured ASCII art instead of hand-drawing
borders character-by-character. Two scripts under `scripts/`, both
JBang-shebanged so they self-resolve their Maven dependency on first
run.

## When to use

Use this skill whenever the user asks for:

- A boxed status message, callout, or warning
- A simple diagram (two or three boxes joined by lines)
- A scatter plot of numbers
- A table grid
- "Draw me X in ASCII"

Do **not** use it when the user wants rich HTML, interactive UI, or
images — those need a different tool.

## What's here

- `scripts/RenderBox.java` — quick wrapper for "put this text in a box"
- `scripts/RenderScene.java` — full scene renderer (rectangles, lines,
  labels, text blocks, dots, circles, ellipses, tables, scatter plots)
- `examples/*.json` — three reference scenes you can adapt

## How to invoke

### Quick boxed message — `RenderBox.java`

```
jbang ${CLAUDE_PLUGIN_ROOT}/skills/ascii-rendering/scripts/RenderBox.java --content "Build green — all tests pass." \
                              --title=STATUS --border=double
```

Output (stdout):

```
╔════════════════════════════════════════════════════════════╗
║[ STATUS ]                                                  ║
║Build green — all tests pass.                               ║
╚════════════════════════════════════════════════════════════╝
```

Flags:

| Flag | Default | Notes |
|---|---|---|
| `--content TEXT` or `--file PATH` or stdin | — | Source text |
| `--width N` | 60 | Max inner width (clamped to `[4, 500]`) |
| `--border single\|double\|bold\|rounded` | `single` | Border style |
| `--title TEXT` | — | Top-edge banner `[ TEXT ]` |

### Structured scene — `RenderScene.java`

Write the scene JSON to a temp file, then:

```
jbang ${CLAUDE_PLUGIN_ROOT}/skills/ascii-rendering/scripts/RenderScene.java --file /tmp/scene.json
```

Or pipe via stdin:

```
echo '{"width":40,"height":5,"elements":[{"type":"rectangle"}]}' | \
    jbang ${CLAUDE_PLUGIN_ROOT}/skills/ascii-rendering/scripts/RenderScene.java
```

## Scene JSON shape

```jsonc
{
  "width":  40,                  // canvas width in characters
  "height": 5,                   // canvas height in characters
  "trim":   true,                // optional; default true
  "elements": [
    {"type": "rectangle"},
    {"type": "label",
     "params": {"text": "Hello", "x": 15, "y": 2}}
  ]
}
```

### Element catalogue

Coordinates: origin `(0, 0)` at top-left; `x` grows right, `y` grows down.
Position keys are optional — omitting them centres or fills automatically.

| `type` | Required params | Optional params |
|---|---|---|
| `rectangle` | — | `x`, `y`, `width`, `height` |
| `line` | `x1`, `y1`, `x2`, `y2` | `pen` (single char, default `●`) |
| `label` | `text` | `x`, `y`, `width` (single line; truncated with `…`) |
| `text` | `text` | `x`, `y`, `width`, `height` (multi-line; word-wraps) |
| `dot` | — | `x`, `y` |
| `circle` | — | `x`, `y`, `radius` |
| `ellipse` | — | `x`, `y`, `width`, `height` |
| `table` | `rows`, `columns` (both > 0) | `x`, `y`, `width`, `height` |
| `plot` | `points` (list of `[x, y]`), `width`, `height` (> 0) | `x`, `y` |

Look at `${CLAUDE_PLUGIN_ROOT}/skills/ascii-rendering/examples/boxed-hello.json`,
`call-graph.json`, and `scatter-plot.json` in the same directory for
full reference scenes.

## Errors

Bad input prints to stderr with a precise location, then exits non-zero:

- `[element 3]: 'columns' and 'rows' must be positive.`
- `[element 2]: Unknown element type 'marshmallow'.`
- `Missing or non-positive 'width'.`

When you see one, fix the indicated element and retry the script.
Don't try to hand-render the result; the script is the authoritative
renderer.

## Prerequisites

The host must have JBang installed (`brew install jbang` /
`curl -Ls https://sh.jbang.dev | bash`). JBang manages Java
automatically; no separate Java install needed.

First invocation pulls the `jaiclaw-ascii-render` jar + Jackson +
SLF4J from Maven (~3 MB total) and caches them under
`~/.jbang/cache/`. Subsequent calls start in well under a second.
