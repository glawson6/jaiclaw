---
name: ascii-rendering
description: Render ASCII art (boxes, diagrams, scatter plots, tables) via the ascii_box and ascii_render tools.
version: 1.0.0
---

# ASCII Rendering

Render ASCII art back to the user — diagrams, boxed messages, scatter plots,
tables. Use this instead of hand-drawing borders character-by-character
(which is slow, token-heavy, and rarely lines up).

## When to use which tool

| You want to… | Use |
| --- | --- |
| Wrap a short message in a box (status, warning, callout) | `ascii_box` |
| Wrap text with a title banner ("STATUS", "ERROR", section header) | `ascii_box` with `title` |
| Build any structured diagram (multiple boxes, lines, plots) | `ascii_render` |
| Render numeric data as a scatter chart | `ascii_render` with a `plot` element |
| Draw a table grid with rows × columns | `ascii_render` with a `table` element |
| Need rich HTML output (interactive, styled, images) | not this skill — use the `canvas_*` tools |

`ascii_box` is the 90% shortcut. Reach for `ascii_render` when you need
more than one shape on the canvas.

---

## `ascii_box` — text in a box

```json
{
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

| Key | Required | Default | Notes |
|---|---|---|---|
| `content` | yes | — | Text. Embedded `\n` honoured; long lines wrap at word boundaries. |
| `width` | no | 60 | Max inner width. The box itself is two chars wider. Clamped to `[4, 500]`. |
| `border` | no | `single` | One of `single`, `double`, `bold`, `rounded`. Unknown styles fall back to `single`. |
| `title` | no | — | Rendered on the top edge as `[ title ]`. Dropped if it doesn't fit the box width. |

Border previews:

```
single:   ┌─┐   double:   ╔═╗   bold:   ┏━┓   rounded:   ╭─╮
          │ │              ║ ║          ┃ ┃              │ │
          └─┘              ╚═╝          ┗━┛              ╰─╯
```

---

## `ascii_render` — declarative scene

Specify a canvas size and a z-ordered list of elements:

```json
{
  "width": 40,
  "height": 5,
  "elements": [
    {"type": "rectangle"},
    {"type": "label", "params": {"text": "Hello, JaiClaw", "x": 12, "y": 2}}
  ]
}
```

Top-level keys:

| Key | Required | Default | Notes |
|---|---|---|---|
| `width` | yes | — | Canvas width in characters. |
| `height` | yes | — | Canvas height in characters. |
| `elements` | yes | — | List of `{type, params}` entries; drawn in order. |
| `trim` | no | `true` | Trim trailing whitespace from each rendered line. |

### Element catalogue

Every element accepts a `params` object. Position/size keys are
optional — omitting them uses a sensible default (centred, full canvas).
Coordinates: origin `(0, 0)` top-left; `x` grows right, `y` grows down.

| Type | Required params | Optional params | Use for |
|---|---|---|---|
| `rectangle` | — | `x`, `y`, `width`, `height` | borders, panels, callouts |
| `line` | `x1`, `y1`, `x2`, `y2` | `pen` (single char) | connectors, arrows, dividers |
| `label` | `text` | `x`, `y`, `width` | single-line text; truncated with `…` if too long |
| `text` | `text` | `x`, `y`, `width`, `height` | multi-line text; word-wraps inside the region |
| `dot` | — | `x`, `y` | a single marker |
| `circle` | — | `x`, `y`, `radius` | circular outline |
| `ellipse` | — | `x`, `y`, `width`, `height` | oval outline |
| `table` | `rows`, `columns` (both > 0) | `x`, `y`, `width`, `height` | grid lines |
| `plot` | `points` (list of `[x, y]`), `width`, `height` (> 0) | `x`, `y` | scatter chart |

### Pattern: two boxes + an arrow

```json
{
  "width": 60, "height": 7,
  "elements": [
    {"type": "rectangle", "params": {"x": 0,  "y": 1, "width": 18, "height": 5}},
    {"type": "label",     "params": {"text": "  source",      "x": 1,  "y": 3}},
    {"type": "rectangle", "params": {"x": 42, "y": 1, "width": 18, "height": 5}},
    {"type": "label",     "params": {"text": "  destination", "x": 43, "y": 3}},
    {"type": "line",      "params": {"x1": 18, "y1": 3, "x2": 42, "y2": 3, "pen": "─"}},
    {"type": "label",     "params": {"text": "transfer", "x": 26, "y": 2}}
  ]
}
```

### Pattern: scatter plot

```json
{
  "width": 50, "height": 14,
  "elements": [
    {"type": "rectangle"},
    {"type": "label", "params": {"text": " latency vs load ", "x": 16, "y": 0}},
    {"type": "plot", "params": {
      "x": 2, "y": 2, "width": 46, "height": 10,
      "points": [[0,1], [1,1.4], [2,2.7], [3,3.6], [4,4.1], [5,4.4], [6,4.7]]
    }}
  ]
}
```

---

## Error handling

Both tools return `ToolResult.Error` on bad input with a precise message:

- Canvas-level: `"Missing or non-positive 'width'."`
- Per-element: `"Element[3] (table): 'columns' and 'rows' must be positive."`
- Unknown type: `"Element[2]: unknown element type 'marshmallow'."`

If you see an error, fix the indicated element and retry. Don't try to
hand-render the result.

---

## Tips

- Keep diagrams **small** — 60 chars wide × 12 high is comfortable in
  most chat UIs. Going wider may wrap awkwardly on mobile.
- For multi-line text inside a region, use `text` (not `label`) — `label`
  is single-line only.
- When connecting boxes with lines, set `pen` to a Unicode horizontal
  (`─`) or vertical (`│`) for clean look; default is `●` which is dotty
  but visible everywhere.
- `plot` auto-normalises the points to fit the region; you don't have to
  scale your data first.
- Need styling, images, or interactivity? Stop and switch to the canvas
  tools (`canvas_present`) — ASCII has hard limits.
