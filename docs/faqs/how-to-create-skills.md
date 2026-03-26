# How to Create Skills

JaiClaw skills are modular knowledge packages that extend the agent with specialized workflows and domain expertise. Each skill is a single `SKILL.md` file inside a named directory.

## Skill Directory Structure

```
skill-name/
└── SKILL.md
```

Skills can live in two locations:
- **Bundled**: `src/main/resources/skills/{name}/SKILL.md` — shipped with JaiClaw
- **Workspace**: `.jaiclaw/skills/{name}/SKILL.md` — project-specific or custom

## SKILL.md File Format

Every skill is a Markdown file with YAML frontmatter:

```markdown
---
name: my-skill
description: What the skill does and when to use it
alwaysInclude: false
requiredBins: [docker]
platforms: [darwin, linux]
version: 1.0.0
tenantIds: []
---

# My Skill

Instructions for the LLM go here...
```

## Frontmatter Fields

| Field | Type | Default | Required | Purpose |
|-------|------|---------|----------|---------|
| `name` | string | — | yes | Skill identifier (lowercase, hyphens, digits only) |
| `description` | string | — | yes | One-line description — triggers skill selection by the agent |
| `alwaysInclude` | boolean | `false` | no | Always include in every system prompt |
| `requiredBins` | string[] | `[]` | no | CLI tools that must be on PATH (checked via `which`) |
| `platforms` | string[] | `[]` | no | Supported OSes: `darwin`, `linux`, `windows`. Empty = all |
| `version` | string | `"1.0.0"` | no | Semantic version for tracking changes |
| `tenantIds` | string[] | `[]` | no | Empty = available to all tenants; populated = restricted |

## Writing the Body

The body after the frontmatter is the skill's instructions — what the LLM reads when the skill is activated.

### Design Principles

1. **Be concise** — The context window is shared across all active skills. Only include information the LLM doesn't already know.

2. **Progressive disclosure** — The `description` field is always visible (for skill selection). The full body only loads when the skill triggers. Keep the body under 500 lines.

3. **Match freedom to need**:
   - **High freedom**: Plain text instructions when multiple approaches are valid
   - **Medium freedom**: Pseudocode when there's a preferred pattern
   - **Low freedom**: Exact scripts/commands when consistency is critical

### What to Include

- Step-by-step workflows the agent should follow
- Exact commands or code patterns when precision matters
- Constraints and guardrails (what NOT to do)
- Examples of expected inputs and outputs

### What NOT to Include

- Information the LLM already knows well (general programming, common tools)
- README or CHANGELOG files — a skill is just `SKILL.md`
- User-facing documentation about the skill itself

## Naming Conventions

- Lowercase letters, digits, and hyphens only: `my-skill`, `gh-issues`, `docker-deploy`
- Short, verb-led phrases describing the action
- Namespace by tool when it improves clarity (e.g., `gh-issues` not just `issues`)

## Creation Process

1. **Understand** — Gather concrete usage examples of what the skill should do
2. **Plan** — Identify reusable resources (scripts, config templates, references)
3. **Create** — Write the `SKILL.md` with frontmatter and instructions
4. **Place** — Put it in the appropriate location (bundled or workspace)
5. **Test** — Verify the skill loads and triggers correctly
6. **Iterate** — Refine based on real usage

## Using the Skill Creator CLI

JaiClaw includes a `jaiclaw-skill-creator` module that automates skill generation via an LLM.

### Interactive Mode

Start a guided conversation to create a skill:

```bash
# In the shell REPL
skill-create --name my-skill --output-dir ./skills

# Non-interactive from command line (standalone JAR)
java -jar jaiclaw-skill-creator.jar skill-create --name my-skill
```

Commands during the interactive session:
- `/save` — Generate the final SKILL.md and write it to disk
- `/exit` — Quit without saving

### Non-Interactive Mode

Generate a skill from a YAML spec file:

```bash
# From a spec file
skill-generate --spec my-skill-spec.yaml --output-dir ./skills
```

### YAML Spec Format

```yaml
name: my-skill
description: "Does X when Y"
platforms: [darwin, linux]
requiredBins: [docker]
purpose: |
  Multi-line description of what the skill should do,
  including key behaviors and constraints.
```

Required fields: `name`, `description`, `purpose`. Optional: `platforms`, `requiredBins`.

## Example: Complete Skill

```markdown
---
name: docker-deploy
description: Deploy services using Docker Compose with health checks and rollback
requiredBins: [docker, docker-compose]
platforms: [darwin, linux]
version: 1.0.0
---

# Docker Deploy

Deploy services using Docker Compose with zero-downtime rolling updates.

## Workflow

1. Validate the `docker-compose.yml` exists in the project root
2. Run `docker-compose config` to validate syntax
3. Pull latest images: `docker-compose pull`
4. Deploy with rolling update: `docker-compose up -d --remove-orphans`
5. Wait for health checks: `docker-compose ps` until all services are healthy
6. If any service is unhealthy after 60s, rollback: `docker-compose up -d --force-recreate`

## Constraints

- Never run `docker-compose down` — it causes downtime
- Always use `--remove-orphans` to clean up stale containers
- Check for `.env` file and warn if missing
```

## See Also

- [How to Load Skills](how-to-load-skills.md) — Configuring which skills are active
