# Docs-as-Data Catalogs

Auto-generated JSON catalogs. Do not edit by hand. Regenerate with:

```bash
./mvnw io.jaiclaw:jaiclaw-maven-plugin:catalog -N
```

Sources of truth:

| Catalog | Extracted from |
|---|---|
| `skills-catalog.json` | `core/jaiclaw-skills/src/main/resources/skills/**/SKILL.md` frontmatter |
| `blueprints-catalog.json` | `extensions/jaiclaw-blueprints/src/main/resources/blueprints/**/*.yml` |

Both catalogs share the same envelope shape:

```json
{
  "count": 42,
  "entries": [ /* array of entries, each mirroring its YAML source */ ]
}
```

The point of the catalogs is docs-as-data: a static site can iterate the JSON at build time and produce cards, tables, filters, and search — instead of maintaining a hand-written list that drifts. This mirrors the Hermes Agent pattern (`extract-*.py` scripts + a React catalog component) with JVM ergonomics.
