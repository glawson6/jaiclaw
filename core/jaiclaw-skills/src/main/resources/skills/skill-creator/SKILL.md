---
name: skill-creator
description: "Create, edit, improve, or audit JaiClaw skills. Use when creating a new skill from scratch or when asked to improve, review, audit, or clean up an existing SKILL.md file."
alwaysInclude: false
requiredBins: []
platforms: [darwin, linux]
---

# Skill Creator

Create and edit JaiClaw skills — modular knowledge packages that extend the agent with specialized workflows and domain expertise.

## Skill Structure

```
skill-name/
└── SKILL.md (required)
```

### SKILL.md Format

```markdown
---
name: my-skill
description: What the skill does and when to use it
alwaysInclude: false
requiredBins: [tool-name]
platforms: [darwin, linux]
version: 1.0.0
tenantIds: []
---

# Skill Title

Instructions for the LLM...
```

### Frontmatter Fields

| Field | Type | Default | Purpose |
|-------|------|---------|---------|
| `name` | string | required | Skill identifier |
| `description` | string | required | One-line description (triggers skill selection) |
| `alwaysInclude` | boolean | false | Include in every system prompt |
| `requiredBins` | string[] | [] | Required CLI tools (checked via `which`) |
| `platforms` | string[] | [] | Supported OSes: darwin, linux, windows |
| `version` | string | "1.0.0" | Semantic version |
| `tenantIds` | string[] | [] | Empty = all tenants; populated = restrict |

## Design Principles

### Concise is Key

The context window is shared. Only add context the LLM doesn't already have. Challenge each piece: "Does the agent really need this?"

### Progressive Disclosure

1. **Metadata** (name + description) — always in context
2. **SKILL.md body** — loaded when skill triggers
3. Keep body under 500 lines

### Degrees of Freedom

- **High freedom**: text instructions when multiple approaches work
- **Medium freedom**: pseudocode when a preferred pattern exists
- **Low freedom**: exact scripts when consistency is critical

## Creation Process

1. **Understand** — gather concrete usage examples
2. **Plan** — identify reusable resources (scripts, references)
3. **Create** — write the SKILL.md with frontmatter and instructions
4. **Place** — put in `src/main/resources/skills/{name}/SKILL.md` (bundled) or `.jaiclaw/skills/{name}/SKILL.md` (workspace)
5. **Test** — verify skill loads and triggers correctly
6. **Iterate** — refine based on real usage

## Naming

- Lowercase letters, digits, and hyphens only
- Short, verb-led phrases describing the action
- Namespace by tool when it improves clarity (e.g., `gh-issues`)

## What NOT to Include

- README.md, CHANGELOG.md, or other auxiliary docs
- User-facing documentation about the creation process
- Information the LLM already knows well
