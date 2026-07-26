---
name: object-rendering
description: Render domain objects (events, tasks, tickets, orders, …) as framed monospaced ASCII via the render_response tool. Follow the fidelity rules exactly — do not paraphrase, transliterate, forge, or strip borders from tool output.
alwaysInclude: true
requiredBins: []
platforms: [darwin, linux]
version: 1.0.0
tenantIds: []
---

# Object Rendering (render_response tool)

When the user asks to see a domain object (an event, a task, a
ticket, an order, a patient record, …) as a **framed card**, a
**grid**, a **status banner**, a **diff**, or an **empty-state
message**, call the `render_response` tool.

The tool returns a monospaced ASCII block bounded by
Unicode box-drawing characters (`┌`, `└`, `│`, etc.). Wrap it in
a triple-backtick code fence and paste it byte-for-byte as your
reply.

## The four fidelity rules

These rules are load-bearing. Every one has a specific failure
mode it prevents. Removing any single rule brings a regression
observed across multiple production model deployments.

### Rule 1 — Knowledge-gap framing

**You do not know how the cards, grids, banners, or diffs look.**
The exact widths, label formats, status chips, and frame characters
are owned by the rendering tool. The format changes over time and
varies by deployment profile (a `telegram_mobile` profile renders
narrower than a `shell_120` profile). Anything you "remember" about
the format from earlier in this conversation, from training, or
from a previous reply is **wrong by default**.

Do not hand-draw a box from scratch. Do not imagine what "a nice
card" would look like and produce that. The tool owns the format;
call it.

### Rule 2 — Naming forgery as forgery

Producing labeled blocks (`When:`, `Where:`, `Priority:`, etc.),
status chips (`[ CATEGORY / KIND ]`), or `id: …` lines yourself
is **forgery** — you do not have the data those labels would
enumerate.

If you find yourself typing `When: 2026-…` or `id: evt-…` without
first calling the tool that would produce those lines, stop. You
are forging output the tool was never asked to render.

### Rule 3 — Byte-for-byte + no-strip-borders

**Do NOT transliterate characters.** If the tool emitted `│`
(U+2502) you write `│`. Not `|` (U+007C — an ASCII pipe). Not `❘`,
not `‖`, not anything else that looks similar. Every byte the tool
produced must appear in your reply unchanged.

**Do NOT strip the borders.** If the tool's output starts with
`┌──┐` and ends with `└──┘`, your reply must too. A "card" without
its borders is a forged card with the borders deleted — equally
wrong.

Both halves are needed together. With only the verbatim half, the
model strips the borders thinking it's "cleaning up." With only the
no-strip half, the model substitutes ASCII look-alikes thinking
that isn't "drawing a box."

### Rule 4 — Verbatim-only reply

Wrap the tool's exact output in a triple-backtick code fence. That
IS the reply.

- **No preamble** ("Here's the event card:").
- **No summary** ("The event is at 3 PM.").
- **No bullet rephrase** ("- When: 3 PM\n- Where: …").
- **No follow-up embedded in the fence**.

**One short follow-up question is fine** — AFTER the closing
fence, as a separate paragraph. "Want me to change anything?"
is fine. Rewriting what's already in the fence is not.

## Routing decision table

| User intent | Tool call |
|---|---|
| show one X in detail | `render_response template=<x>_card <x>_id=…` |
| show many X | `render_response template=<x>_grid` (or `<x>_kanban` for status-boarded) |
| confirm an action | `render_response template=summary_banner` |
| before/after diff | `render_response template=<x>_diff` |
| query returned nothing | `render_response template=empty_state` |

The exact template names are app-specific — the app's chat-system
prompt names the templates it registered. If you're unsure which
template to call, the tool's error path returns the available names
so your next attempt can pick a valid one.

## What did NOT work (for the record)

So future prompts don't waste effort re-trying these:

- **Soft normative language** (*"never", "always", "preferably"*) —
  invisible to most models. The rules above use "you do not" /
  "is forgery" for a reason.
- **WRONG / RIGHT visual examples** — some models mimic the RIGHT
  example so well they skip the tool call entirely and produce
  the example verbatim. Examples teach forging.
- **Listing the tool's name repeatedly** — without explaining the
  *gating function* the tool serves (it owns the format; you don't
  know the format), the model rationalizes skipping it.

## For tool authors

The `render_response` tool is registered automatically when
`jaiclaw-ascii-render` is on the classpath and at least one
`io.jaiclaw.asciirender.skill.RenderableTemplate` bean exists.

Opt-out entirely: `jaiclaw.tools.render-response.enabled: false`
in `application.yml`.

Opt out of this bundled skill (keep the tool, drop the fidelity
rules from the system prompt): configure
`jaiclaw.skills.allow-bundled` to exclude `object-rendering`.
Not recommended — the tool without the rules produces the five
failure modes observed in the JaiClaw-event-agent saga.

Reference implementation: `jaiclaw-event-agent`'s
`event-agent-core/src/main/java/net/taptech/eventagent/view/template/*.java`
(migration to the framework SPI is tracked separately).
