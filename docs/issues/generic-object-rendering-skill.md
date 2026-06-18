# Issue: generic ASCII object-rendering skill + SPI

## Summary

Any jaiclaw-based app that wants to render a domain object (event, task, ticket, patient, order, …) as framed monospaced ASCII through reliable tool calls currently has to re-invent the same five pieces:

1. A `Template` SPI (`name() / description() / render(params) → String`).
2. A registry that collects template beans by name.
3. A `render_response`-style tool whose JSON schema discriminates by template name.
4. A profile-aware width helper.
5. **~30 lines of system-prompt rules** that get the LLM to actually call the tool, paste its output verbatim, and not forge it.

Item 5 is the load-bearing one. The `jaiclaw-event-agent` reference app spent multiple days iterating across MiniMax-M2.7/M3, gpt-4.1-mini, gpt-4o-mini, and gpt-5-mini to find the exact set of rules that makes LLMs respect tool output for ASCII rendering. The full saga is captured in `jaiclaw-event-agent/docs/llm-tool-call-fidelity.md`. Every future jaiclaw app that wants framed object output will re-discover the same lessons unless we lift them into the framework.

The framework should ship: the SPI, the registry, the tool, the profile helper, and the SKILL.md with the four fidelity rules. Apps should ship: their concrete templates (one per shape — `event_card`, `task_kanban`, etc.).

## The five LLM failure modes (observed)

Each one looks the same in a chat client (broken or invented card). Each has a different fix.

| # | Failure | Models seen on | Fix that worked |
|---|---|---|---|
| 1 | Hand-drew a box from scratch, no tool call | MiniMax-M2.7, MiniMax-M3 | Knowledge-gap framing: *"You do not know how the cards look. The format is owned by the tool."* |
| 2 | Called the tool, then paraphrased the output into bullets | gpt-4.1-mini, gpt-4o-mini | Verbatim-only reply rule: *"Wrap the tool's exact output in a triple-backtick code fence. That IS the reply. No preamble, no summary, no rephrase."* |
| 3 | Forged a card that LOOKED correct by mimicking a WRONG/RIGHT example in the prompt | gpt-5-mini | Remove all example outputs from the prompt. Examples teach forging. |
| 4 | Called the tool, then transliterated `│` (U+2502) → `|` (U+007C) when "copying" | gpt-5-mini | Byte-for-byte rule: *"Do NOT transliterate characters. Every byte the tool produced must appear in your reply unchanged. Do NOT strip the borders."* |
| 5 | Invented object ids/titles/times the tool was never asked to render | MiniMax-M2.7, MiniMax-M3 | Naming forgery: *"Producing `id: evt-...` lines or status chips without a tool result is forgery — you do not have the data."* |

None of the five failures had useful log evidence in the chat UI. Per-tool entry/exit logging (which the framework should provide for the new tool) is essential for distinguishing "model never called the tool" from "model called the tool then forged the response."

## What worked, generalized

The four rules below were the minimum viable set. Removing any one brought a regression. They are model-agnostic and domain-agnostic — they belong in the framework SKILL.md, not in each app's chat-system prompt.

### Rule 1 — Knowledge-gap framing
> **You do not know how the cards, grids, banners, or diffs look.** The exact widths, label formats, status chips, and frame characters are owned by the rendering tool. The format changes over time and varies by deployment profile. Anything you "remember" about the format from earlier in this conversation, from training, or from a previous reply is WRONG by default.

This works because it's both **true** (deploys tune widths via `AsciiRenderProfiles`) and **enforceable** (the model can't fake what it doesn't have).

### Rule 2 — Naming forgery as forgery
> Producing labeled blocks (`When:`, `Where:`, etc.), status chips (`[ CATEGORY / KIND ]`), or `id: …` lines yourself is **forgery** — you do not have the data.

Naming the failure mode pulls it into the model's attention. "Don't do X" is weak; "doing X is called forgery and is wrong" sticks.

### Rule 3 — Byte-for-byte + no-strip-borders
> **Do NOT transliterate characters.** If the tool emitted `│` (U+2502) you write `│`. Not `|` (U+007C). Not anything else.
>
> **Do NOT strip the borders.** If the tool's output starts with `┌──┐` and ends with `└──┘`, your reply must too. A "card" without its borders is a forged card with the borders deleted — equally wrong.

Both halves are needed. With only the verbatim half, the model strips the borders thinking it's "cleaning up." With only the no-strip half, the model substitutes ASCII look-alikes thinking that isn't "drawing a box."

### Rule 4 — Verbatim-only reply
> Wrap the tool's exact output in a triple-backtick code fence and that IS the reply. No preamble. No summary. No bullet rephrase. One short follow-up question is fine AFTER the closing fence.

### What did NOT work
For the record, so we don't waste effort re-trying:

- **Soft normative language** (*"never", "always", "preferably"*) — invisible to most models.
- **WRONG / RIGHT visual examples** — gpt-5-mini mimicked the RIGHT example so well it skipped the tool call entirely. Examples teach forging.
- **Listing the tool's name repeatedly** — without explaining the *gating function* the tool serves, the model rationalizes skipping it.

## Proposed module + scope

**Extend `jaiclaw-ascii-render`.** It already ships `AsciiSceneFactory`, `AsciiRenderProfile` (name, width, padding), `AsciiRenderProfiles.defaultProfile()`, and a `skill-pack/`. Adding the new SPI, registry, tool, helper, and SKILL.md fits the module's existing purpose. One pom dep for apps that want object rendering.

**Tool-discovery design — app brings its own templates.** The framework ships NO concrete templates. Apps subclass `RenderableTemplate` for each domain shape (`event_card`, `task_card`, `ticket_diff`, etc.) and `@Bean`-register them. The framework's tool builds its JSON schema dynamically from `registry.names()`. This avoids the framework picking opinions about what an "event" looks like that won't fit a medical-records or financial app.

**App-side hydration — no Repository SPI.** Templates that need storage take a service in their constructor (`new EventCardTemplate(EventService events)`). The framework doesn't define what storage looks like. This matches today's event-agent pattern; just lifts the SPI up one level.

## Proposed SPI

```java
package io.jaiclaw.asciirender.skill;

import java.util.Map;
import java.util.Set;

public interface RenderableTemplate {

    /** Tool-call discriminator. Lowercase snake_case (e.g. event_card, task_kanban). */
    String name();

    /** One-sentence description shown to the LLM in the tool's parameter docs. */
    String description();

    /**
     * Names of the params this template understands. Used by the registry
     * to build the union JSON schema. A template ignores params it doesn't
     * recognize; the framework doesn't police this.
     */
    Set<String> parameterNames();

    /**
     * Build the framed ASCII output. Implementations should NOT throw —
     * return an {@code [ ERROR ]} banner on bad input so the LLM always
     * gets something parseable.
     */
    String render(Map<String, Object> params);
}
```

Notes:
- No data accessor in the signature. Apps inject their stores into template constructors.
- Returns plain `String`. The tool layer wraps in `ToolResult.Success`.
- `parameterNames()` is the registry's hook for auto-building the JSON schema — no manifest required.

## Proposed bean graph

```java
// Framework provides
@Bean
public RenderableTemplateRegistry templateRegistry(List<RenderableTemplate> templates) {
    return new RenderableTemplateRegistry(templates);   // first-wins by name
}

@Bean
public ToolCallback renderResponseTool(RenderableTemplateRegistry registry) {
    return new RenderResponseTool(registry);   // schema built from registry.names() ∪ parameterNames()
}

// App provides — one bean per template
@Bean
public RenderableTemplate eventCardTemplate(EventService events) {
    return new EventCardTemplate(events);
}
```

`render_response` tool's dynamic JSON schema:

```json
{
  "type": "object",
  "properties": {
    "template": { "type": "string", "enum": ["event_card", "task_kanban", ...] },
    "<param>":  { "type": "string" }    // for each name in union(parameterNames())
  },
  "required": ["template"]
}
```

The tool dispatches `registry.find(template).render(params)`. Unknown template → `ToolResult.Error` with the available names listed.

## Profile-awareness contract

Lift the event-agent's `SceneSpecHelpers.activeWidth(min, max)` helper into the framework. It already reads `AsciiRenderProfiles.defaultProfile().width()` and clamps. Each template calls:

```java
int width = SceneSpecHelpers.activeWidth(MIN_WIDTH, MAX_WIDTH);
boolean narrow = width < someThreshold;
// build wide or narrow layout
```

Profile changes (`jaiclaw.ascii.default-profile: telegram_mobile`) re-route every template's layout with no code change. This is already proven in the event-agent.

## The SKILL.md (framework-shipped)

Path: `core/jaiclaw-ascii-render/src/main/resources/skills/object-rendering/SKILL.md`

Frontmatter: `alwaysInclude: true` so it ships in every chat agent's system prompt.

Body contains the four fidelity rules verbatim plus a generic routing decision table:

| User intent | Tool call |
|---|---|
| show one X in detail | `render_response template=<x>_card x_id=...` |
| show many X | `render_response template=<x>_grid` |
| confirm an action | `render_response template=summary_banner` |
| before/after diff | `render_response template=<x>_diff` |
| query returned nothing | `render_response template=empty_state` |

The SKILL.md is general; app-specific routing (e.g., "what tool for *next week*") stays in the app's chat-system prompt. The framework's job is the fidelity rules.

## Required logging

The new tool MUST log entry/exit per call:

```
render_response called — template=<name>, params=<keys>
render_response — template=<name> produced <N> chars
render_response — unknown template '<name>'; known: [...]
```

Without these logs we couldn't tell, in the event-agent saga, which of the 5 failure modes we were hitting on any given turn. Three different failures look identical in chat: "no tool call", "tool called then paraphrased", "tool called then transliterated". Per-tool logs disambiguate.

## Migration path for `jaiclaw-event-agent`

Once the framework ships:

1. Delete `event-agent-core/src/main/java/net/taptech/eventagent/view/template/ResponseTemplate.java`.
2. Delete `event-agent-core/src/main/java/net/taptech/eventagent/view/template/ResponseTemplateRegistry.java`.
3. Delete `event-agent-core/src/main/java/net/taptech/eventagent/tools/RenderResponseTool.java`.
4. Each of the 5 `*Template.java` files swaps `implements ResponseTemplate` → `implements io.jaiclaw.asciirender.skill.RenderableTemplate`. Method signatures match.
5. Delete the per-app fidelity rules from `event-agent-app/src/main/resources/prompts/chat-system.md` — they're now in the framework skill. Keep app-specific routing (intent → template name).
6. Net: ~200 lines removed from the app. Every future rendering-fidelity bug becomes a framework bug fixed once.

## Out of scope

- **Repository SPI.** Apps own their storage. Templates take a service in the constructor.
- **Non-ASCII renderers.** This is ASCII-specific because `AsciiSceneFactory` is. HTML / image rendering is a separate problem.
- **LLM-output post-processing / forced substitution.** Replacing the model's text reply with the captured tool output app-side is tracked separately; the prompt-based approach is what's proven to work today and is what this skill ships.
- **Generic template implementations.** No `event_card`, `task_card`, etc. shipped by the framework. Apps map their domain types.

## Risks

- **Skill text bloat.** Adding ~30 lines of fidelity rules to every jaiclaw app's system prompt. Mitigation: framework setting `jaiclaw.skills.object-rendering.enabled: false` opts apps out.
- **Tool JSON schema growth.** Union of all template params could get noisy. Mitigation: templates declare only what they consume; `parameterNames()` is the union, not the framework's responsibility to police.
- **Skill text becomes stale as models improve.** New models may not need the byte-for-byte rule. Mitigation: rules are explicit enough that an obsolete one is recognizable, and apps can override the skill via the workspace skill path (`~/.jaiclaw/skills/object-rendering/SKILL.md`).

## Recommendation

Ship the SPI + registry + tool + helper + SKILL.md as a single 0.10.0 increment in `jaiclaw-ascii-render`. The four fidelity rules are the load-bearing piece; everything else is plumbing. Once shipped, migrate `jaiclaw-event-agent` to the framework version as the canonical example app.

## Tracking

- Discovered: 2026-06-17/18, during the `jaiclaw-event-agent` chat-agent fidelity work.
- Source material: `jaiclaw-event-agent/docs/llm-tool-call-fidelity.md` (the saga + reasoning).
- Reference implementation today: `jaiclaw-event-agent/event-agent-core/src/main/java/net/taptech/eventagent/view/template/*.java` + `tools/RenderResponseTool.java`.
- Related: `multi-provider-chatmodel-selection.md` (also discovered during this work; orthogonal but bundles into the same overall app cleanup once both ship).
