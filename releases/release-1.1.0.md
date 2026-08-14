# JaiClaw 1.1.0 Release Notes

**Release Date:** 2026-08-13
**Distribution:** Maven Central + TapTech Nexus (`tooling.taptech.net`)

> 1.1.0 is the **Embabel GA + post-1.0.0 consolidation release.** It's the first JaiClaw release since 1.0.0 that reaches Maven Central: the Boot-4 line-swap that shipped as 1.0.0 was blocked from Central by `com.embabel.agent:embabel-agent-*:2.0.0-SNAPSHOT`. With Embabel `1.5.0` GA on Central (2026-08-11), that block is gone.

## Highlights

- **Embabel `1.5.0` GA on Maven Central** — the Boot-4 stack (Spring Boot 4.1.0 + Spring AI 2.0.0 GA + Spring 7.0.8 + Kotlin 2.2.21 + Jackson 3.1.4) resolves cleanly from Central. JaiClaw itself can publish there again.
- **New `glyph` scene element in `jaiclaw-ascii-render`** — first-class status glyphs (`ok`, `fail`, `warn`, `warning`, `info`, `arrow`, `bullet`, `star`, `pending`, `question`, plus aliases) via `{"type":"glyph","params":{"x":..,"y":..,"name":"ok"}}`. Custom-glyph SPI (`GlyphContribution` bean) lets adopters register their own from any Spring Boot app without forking. Optional `AnsiPalette` post-render colorizer respects `NO_COLOR` + `isatty()` and matches the ANSI codes already in `install.sh` and `JaiClaw.java`. Full docs at `core/jaiclaw-ascii-render/skill-pack/plugins/ascii-rendering/skills/ascii-rendering/GLYPHS.md`.
- **New "How to Build an AI Agent" doc set** — `docs/user/WHAT-IS-AGENTIC-AI.md` (plain-English primer) + `docs/user/BUILDING-AGENTS.md` (8-module architectural blueprint mapped to JaiClaw code) + `docs/user/agent-diagrams/*` (9 checked-in ASCII scenes). Reading path is `README.md → WHAT-IS-AGENTIC-AI.md → BUILDING-AGENTS.md → GETTING-STARTED.md`.
- **New MCP design-patterns doc** — `docs/user/features/mcp-design-patterns.md` catalogues the five reusable JaiClaw MCP shapes (agent-hosted tools, resource providers, tool + resource pairs, composed servers, external-server delegation) with the ASCII diagrams under `docs/user/features/mcp-diagrams/`.
- **GitHub actionable slash-command dispatcher** — `jaiclaw-cli-github` gained a slash-command dispatcher for GitHub Actions workflows. Details in the module's README.
- **Overload-ambiguity fix in `github_comment` tool** — `GHIssue.comment(String)` in `github-api 1.330` has two `public` overloads with identical erased signatures (`void` vs. `GHIssueComment`). Production dispatches correctly via bytecode descriptor; Spock's byte-buddy mock proxy is descriptor-blind and picked between them intermittently, causing a 1-in-3 test flake after the Embabel 1.5.0 bump. Fix: extract the ambiguous call into `protected GHIssueComment postComment(GHIssue, String)` — tests mock the unambiguous helper via `Spy`. 10/10 isolated runs green after the fix. Full postmortem at `docs/issues/embabel-1.5.0-github-mock-flake.md`.

## Distribution

**Maven Central + TapTech Nexus.** This release is the first JaiClaw artifact set on Maven Central since 0.9.3 (1.0.0 shipped Nexus-only pending the Embabel GA).

**Adopter setup — Maven Central (no repo declaration needed):**

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>io.jaiclaw</groupId>
      <artifactId>jaiclaw-bom</artifactId>
      <version>1.1.0</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
```

**Adopter setup — TapTech Nexus (alternative mirror):**

```xml
<repository>
  <id>taptech-releases</id>
  <url>https://tooling.taptech.net/repository/maven-releases/</url>
  <releases><enabled>true</enabled></releases>
  <snapshots><enabled>false</enabled></snapshots>
</repository>
```

## Dependency updates

| Dependency | 1.0.0 | 1.1.0 | Notes |
|---|---|---|---|
| Embabel Agent | Boot-4 SNAPSHOT line | **1.5.0 GA** | On Maven Central; unblocks JaiClaw's Central publish |
| Spring Boot | 4.1.0 | 4.1.0 | unchanged |
| Spring AI | 2.0.0 | 2.0.0 | unchanged |
| Spring Framework | 7.0.x | 7.0.8 | Boot-managed |
| Kotlin | (indirect) | 2.2.21 | via Embabel transitive |
| Jackson | 3.x | 3.1.4 | via Embabel transitive |
| Everything else | — | unchanged | see 1.0.0 for the full Boot-4 stack |

## Bug fixes

- **`GithubCommentTool` overload flake** — see Highlights above.

## New APIs

### `io.jaiclaw.asciirender.element.Glyph`
```java
public class Glyph implements IElement {
    public Glyph(int x, int y, String glyph, GlyphSemanticClass semanticClass);
    public static Glyph byName(String name, int x, int y, GlyphRegistry registry);
    // ...
}
```

### `io.jaiclaw.asciirender.glyph.*`
- `GlyphDefinition` (record: name, glyph, semanticClass, description)
- `GlyphSemanticClass` (enum: SUCCESS, WARNING, ERROR, INFO, NEUTRAL, DECORATIVE)
- `GlyphSet` (default vocabulary)
- `GlyphRegistry` (lookup surface, `global()` shared registry, `setGlobal()` for tests)
- `GlyphContribution` (SPI adopters implement to register custom glyphs)
- `AnsiPalette` (optional post-render colorizer)
- `AnsiSupport` (TTY / `NO_COLOR` detection)

### `io.jaiclaw.tools.builtin.ascii.GlyphRegistryAutoConfiguration`
Auto-configured in `jaiclaw-tools` — merges `GlyphSet.defaults()` with every `GlyphContribution` bean in the context, installs the composite as `GlyphRegistry.global()`, and provides a default `AnsiPalette` bean.

### `io.jaiclaw.tools.github.tools.GithubCommentTool.postComment(GHIssue, String)`
Extracted helper (protected, overridable) that wraps the ambiguous `GHIssue.comment(String)` call. See the flake postmortem for the JVM-level rationale.

## Breaking changes

None. All Highlights are additive:
- New scene element type `glyph` — existing scenes render unchanged.
- New `GlyphRegistry` auto-config — beans back off cleanly if the ascii-render module isn't on the classpath.
- New docs — no code impact.
- `GithubCommentTool.postComment` extraction — internal refactor; the tool's tool-callback surface is unchanged.

## Verification

- **Full reactor test**: 86 modules, 4,349 tests, 0 failures, 0 errors, 10 skipped.
- **Embabel resolution**: `mvn dependency:tree -pl :jaiclaw-spring-boot-starter` confirms `com.embabel.agent:embabel-agent-*:1.5.0` resolves from Maven Central (no snapshot repo required for consumers).
- **Glyph rendering**: 9 checked-in scenes under `docs/user/agent-diagrams/glyph-*.txt` regenerate byte-identically via `./render.sh`.
- **Overload flake**: 10/10 isolated `mvn test -pl :jaiclaw-tools-github -o` runs pass after the `postComment` extraction fix.

## Known follow-ups (post-1.1.0)

Carried forward from 1.0.0:
- **Onboarding wizard** — still awaiting the Shell 4 component-model port. `start.sh` / `bin/jaiclaw` non-wizard paths work.
- **Redis TaskStore CAS** — still requires the Lettuce migration or SDR-4-compatible bookkeeping fix; `RedisTaskStoreContractSpec` remains `@Ignore`d.
- **`OAuthProviderDemoConfig`** — still needs the Spring AI 2.0 `OpenAIOkHttpClient` rebuild.

New in 1.1.0 (see `docs/issues/`):
- **Glyph attribute plane** (`docs/issues/ascii-render-glyph-followups.md`) — inline color / bold / underline inside the canvas itself. Deferred because it breaks the "one cell = one char" invariant every existing element assumes. The current `AnsiPalette` post-render pass covers 90% of the use case.
- **Extended AnsiPalette attributes** — colored backgrounds, bold, underline, dim; foreground-only for v1.
- **Windows CMD ANSI enabling** — adopters that need it call `AnsiConsole.systemInstall()` themselves for now.

## Acknowledgements

The Embabel `1.5.0` GA on Maven Central (2026-08-11) is the load-bearing upstream event this release depends on. Special credit to the Embabel maintainers for prioritizing the Boot-4-compatible release line (PR #1765) over the parked `2.0.0-SNAPSHOT` — that decision unblocked JaiClaw's Central publish for the whole downstream Java ecosystem.
