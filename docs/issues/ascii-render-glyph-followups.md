# ASCII renderer glyph work — deferred follow-ups

**Area:** `core/jaiclaw-ascii-render/src/main/java/io/jaiclaw/asciirender/glyph/**` + `core/jaiclaw-ascii-render/src/main/java/io/jaiclaw/asciirender/element/Glyph.java`
**Trigger:** The August 2026 glyph-vocabulary + `GlyphContribution` SPI landing (this PR) explicitly deferred four extensions to keep the initial change surface small. They are recorded here so nothing gets lost.
**Severity:** enhancement — no v1 blockers; each item is a self-contained future ticket.

## Context

The first cut ships:

- A `glyph` scene element with a curated built-in vocabulary (`ok` / `fail` / `warn` / `warning` / `info` / `arrow` / `bullet` / `star` / `pending` / `question` and aliases).
- A `GlyphRegistry` SPI (`GlyphContribution` beans merged by `GlyphRegistryAutoConfiguration`) so adopters can add their own without forking.
- An optional `AnsiPalette` post-render colorizer that respects `NO_COLOR` + `isatty()` and matches the exact ANSI codes in `install.sh` + `JaiClaw.java`.

Everything below was consciously left out.

## Deferred follow-ups

### 1. Canvas attribute plane

**Problem.** Colour, bold, underline, dim etc. currently live outside the canvas — `AnsiPalette` post-processes the rendered text. Some scenes want colour on non-glyph text (a label reading "ERROR", a red border, a dim caption). Today, only registered glyphs get coloured.

**Why deferred now.** The canvas invariant is "one cell = one Unicode char" — assumed by `Rectangle`, `Label`, `BorderedBox`, `Text`, `Table`, `Plot`, `Line`, `Dot`, `Ellipse`, `Circle`, and every adopter `IElement`. Adding a parallel attribute plane means reworking `Canvas` (`core/jaiclaw-ascii-render/src/main/java/io/jaiclaw/asciirender/core/Canvas.java`) and every existing element's `draw()` path. The ANSI post-process pass covers 90% of the real-world use case (status glyphs are the main thing users want in colour) at zero cost to the rest of the module.

**Rough shape when picked up.** New sibling data structure `AttributeCanvas` (or `List<StringBuilder>` for ANSI-open codes + a parallel one for close codes, indexed by cell). Every element optionally writes its attributes to that plane. A new `Canvas.emit(RenderTarget target)` variant walks both planes together and interleaves colour codes with characters. `getText()` stays pure-text.

**Blast radius.** Every `IElement` `draw()` gets an optional overload; existing implementations keep working via a default no-op attribute write. `RenderableTemplate` implementations that render pure text still work. Downstream doc-embedded scene specs (under `docs/user/agent-diagrams/`, `docs/user/features/mcp-diagrams/`) unchanged — they'd opt in per-element as needed.

### 2. Retrofit existing scenes to use glyphs

**Problem.** The doc-embedded JSON scene specs under `docs/user/agent-diagrams/` and `docs/user/features/mcp-diagrams/` currently draw status markers as ad-hoc labels (`{"type":"label","params":{"text":"✓"}}`) or arrowhead characters embedded in label text. This works but scatters the glyph vocabulary; a reader can't grep for "how does JaiClaw draw ok/fail" and find one canonical spot.

**Why deferred now.** Pure-doc work; safest done as a separate PR once real adopter feedback has settled the vocabulary shape (e.g. the `⧗` pending glyph might turn out to have a better alternative once people use it in anger).

**Rough shape when picked up.** Walk both diagram dirs, replace each `label` whose text is a single glyph with a `glyph` element pointing at the appropriate name. Regenerate the checked-in `.txt` files via each dir's `render.sh`. No framework code changes.

**Blast radius.** ~20 scene JSONs + their `.txt` siblings. Each individual edit is 3–5 characters; the diff review is the biggest cost. Zero framework impact.

### 3. Extended attribute vocabulary

**Problem.** `AnsiPalette` today emits foreground colour only. Some scenes want bold ("BUILD PASSED"), dim (secondary text), underline (links), or background colour (highlighted rows).

**Why deferred now.** Foreground-only keeps the palette + `NO_COLOR` semantics simple in v1. `AttributedString`-style bitmasks add complexity to the palette-code data structure and to the `colorize` regex; the extra vocabulary is only useful if adopters actually ask for it.

**Rough shape when picked up.** Extend `AnsiPalette` with an `Attribute` bitmask (BOLD, DIM, UNDERLINE, BG_*) attachable to each `GlyphSemanticClass` or overridable per-glyph. `withAttribute(GlyphSemanticClass, Attribute)` fluent method. The `colorize` pass concatenates the class code + attribute codes before each glyph and closes with a single `\033[0m`. Semantic classes stay the same — no vocabulary churn.

**Blast radius.** Adds fields to `AnsiPalette`; existing `withCode` + `defaultPalette` unchanged; adopters that never call the new API see zero difference.

### 4. Windows CMD ANSI enabling

**Problem.** Non-Windows-Terminal CMD (the legacy console) does not interpret ANSI escape sequences by default. Adopters running JaiClaw's shell on legacy CMD will see raw `\033[0;32m` bytes around every glyph.

**Why deferred now.** Adopters that need it can call `org.fusesource.jansi.AnsiConsole.systemInstall()` themselves — the Jansi library is already pulled in transitively by Spring Shell in the JaiClaw shell app. First-party opt-in support isn't a blocker for anyone yet.

**Rough shape when picked up.** Add `AnsiPalette.installNativeSupport()` — a static one-liner that calls `AnsiConsole.systemInstall()` when the JVM is on Windows and Jansi is on the classpath (`@ConditionalOnClass` if we host it in an auto-config). A second flavour `AnsiPalette.uninstallNativeSupport()` symmetrically calls `systemUninstall()`. Document under `AnsiPalette`'s class javadoc + the `GLYPHS.md` "Terminal detection" section.

**Blast radius.** Zero on macOS/Linux. Optional dep on `org.fusesource.jansi:jansi` — already indirectly present through Spring Shell. If we auto-install at startup, guard behind a `jaiclaw.ascii.ansi.native-install=false` property so adopters can opt out.

---

## Not tickets

These come up in conversation but are **not** planned work — recording so we don't keep re-arguing them:

- **Rename `Dot` to `Glyph`.** `Dot` stays for backward-compat; the new element is orthogonal (Dot has a fixed `*`, Glyph is name-addressable).
- **Separate `glyph_render` MCP tool.** One tool, one schema — glyphs are a new element type inside `ascii_render`.
- **Full-color 24-bit truecolor palette (`\033[38;2;R;G;Bm`).** ANSI 4-bit codes match `install.sh` exactly — visual consistency across the shell/Java boundary is the goal. Adopters who want truecolor can supply their own `AnsiPalette` via `@Bean AnsiPalette`.
