# Skills & Blueprints Catalogs

Every skill and blueprint that ships with JaiClaw is defined once — in a `SKILL.md` file or a blueprint YAML — and extracted into machine-readable JSON catalogs at build time. This page tells you where to find both, and how the doc-generation pipeline works.

## Live catalogs

The auto-generated catalog files live at:

- [`docs/api/skills-catalog.json`](../api/skills-catalog.json) — 70 bundled skills
- [`docs/api/blueprints-catalog.json`](../api/blueprints-catalog.json) — 3 shipped sample blueprints

Both files share the envelope shape:

```json
{
  "count": 70,
  "entries": [
    {
      "name": "…",
      "description": "…",
      "path": "core/jaiclaw-skills/src/…/SKILL.md",
      "…other frontmatter fields…"
    }
  ]
}
```

## Regenerate

The catalogs are stale as soon as anyone edits a `SKILL.md` or blueprint YAML. Regenerate:

```bash
./mvnw io.jaiclaw:jaiclaw-maven-plugin:catalog -N
```

Wire the goal into a project's build if you want it always-fresh:

```xml
<plugin>
    <groupId>io.jaiclaw</groupId>
    <artifactId>jaiclaw-maven-plugin</artifactId>
    <version>${project.version}</version>
    <executions>
        <execution>
            <id>catalog</id>
            <goals><goal>catalog</goal></goals>
            <phase>process-resources</phase>
        </execution>
    </executions>
</plugin>
```

The goal is idempotent and fast (~1 second for the current tree) so wiring it in has minimal cost.

## Configuration

Every parameter has a sensible default. Override via `-D` or `<configuration>`:

| Parameter | Default | Purpose |
|---|---|---|
| `jaiclaw.catalog.skillsDir` | `core/jaiclaw-skills/src/main/resources/skills` | Where to walk for `SKILL.md` |
| `jaiclaw.catalog.blueprintsDir` | `extensions/jaiclaw-blueprints/src/main/resources/blueprints/samples` | Where to walk for blueprint YAML |
| `jaiclaw.catalog.outputDir` | `docs/api` | Where to emit the JSON files |
| `jaiclaw.catalog.skip` | `false` | Skip the goal entirely |

## Why docs-as-data?

Inspired by the Hermes Agent pattern (see [SIBLING-SURVEY-2026-07-07.md](../dev/SIBLING-SURVEY-2026-07-07.md)): the source of truth for a skill or blueprint is one file, and every downstream rendering — docs, dashboard forms, CLI slash-commands, agent seed prompts — reads that file's extracted data.

Concretely, a docs site (Docusaurus, Astro, or plain static-site-generator) can fetch the JSON at build time and render:

- A **searchable card grid** of skills with filters by category / tag
- **Blueprint gallery** with copy-buttons for the CLI invocation and deep-links into the dashboard
- **API stability listing** cross-referenced against the skill's `alwaysInclude` and version fields

None of that renderer exists yet in this repo — the JSON catalog is the substrate on which we'd build it. What ships today is the catalog itself.

## What the catalog does NOT include

Two things are deliberately out of scope for the goal:

1. **Optional skills** — the Hermes-style `optional-skills/` tier we discussed in the sibling survey isn't defined for JaiClaw yet. When it lands (per the enterprise-skill filter proposal), the catalog goal will grow a second walk-root to produce `optional-skills-catalog.json`.

2. **Runtime skill availability** — the catalog reflects `SKILL.md` files on disk, not skills the `SkillLoader` will actually load at runtime (which depends on `jaiclaw.skills.allow-bundled`, tenant filtering, etc.). To see what a running agent has loaded, use the shell command `jaiclaw skills` or the MCP `skills_list` tool.
