# Authoring Skills

> **Audience:** anyone teaching a JaiClaw agent domain knowledge or
> behavioral conventions. Skills are how you encode "when you see X,
> prefer approach Y."

This page covers the skill format end-to-end, when a skill is the right
tool (vs. a real tool, a prompt template, or RAG), and how to keep your
skill library cheap to load.

For the cost story — why the default loads ~26K tokens per request —
see [SKILLS.md](SKILLS.md). This doc focuses on the authoring shape.

## What is a skill, really

A skill is a Markdown file with YAML frontmatter. The frontmatter is
metadata; the body is text that gets injected into the LLM's system
prompt when the skill is eligible. Nothing more, nothing magic.

```markdown
---
name: kubectl-helper
description: When debugging Kubernetes issues, prefer kubectl over the dashboard.
version: 1.0.0
tenantIds: []         # empty = all tenants
---
# kubectl Helper

When debugging a misbehaving pod, prefer `kubectl describe pod <name>`
over the dashboard — it shows events, conditions, and recent restarts
in one view.

If the pod is `CrashLoopBackOff`, always check `kubectl logs <pod>
--previous` to see what crashed.
```

When this skill is loaded, the agent's system prompt grows by exactly
the size of the body. The LLM uses it the same way it uses any other
system-prompt content.

## When a skill is the right answer

| You want to... | Use |
|---|---|
| Teach the agent a domain convention | **Skill** |
| Give the agent the ability to *do* something (call API, read file) | **Tool** |
| Inject a specific phrasing or output structure | **Prompt template** |
| Give the agent searchable knowledge that's too big for the prompt | **RAG / docstore** |

Skills cost input tokens on every LLM call. RAG costs an embedding
lookup + per-call retrieval. Tools cost nothing until invoked.

A rule of thumb: if the same instruction would apply to *every* call,
a skill is fine. If it only applies once in a while, RAG-it or push
that knowledge into a tool's response.

## Frontmatter reference

| Field | Type | Default | Purpose |
|---|---|---|---|
| `name` | string | required | Unique skill identifier; kebab-case |
| `description` | string | required | One-line summary; surfaced in MCP listings + audit |
| `version` | string | `0.0.0` | Semver; bump on body changes for downstream-MCP visibility |
| `tenantIds` | list[string] | `[]` (all) | Multi-tenant filter — empty = available to every tenant |
| `requires` | list[string] | `[]` | Other skills that must also be loaded for this one to be eligible |
| `tags` | list[string] | `[]` | Cataloging only; not used by loader |

`requires` is a soft constraint — the loader skips this skill if its
required peer isn't present in the loaded set. Useful for "advanced X"
skills that depend on "basic X" being on too.

## Body content conventions

Three things make a skill body work well:

### 1. Lead with the verdict, then the why

The LLM reads top-down. A skill that opens with "when X, do Y" gets the
behavior right far more reliably than one that explains the background
first and only mentions the verdict mid-paragraph.

**Good:**

```markdown
When the user asks about Kubernetes troubleshooting, prefer `kubectl`
commands over the Kubernetes Dashboard UI. The dashboard is slow,
caches state poorly, and isn't scriptable.
```

**Less good:**

```markdown
The Kubernetes Dashboard is a popular tool, but it has some drawbacks —
it can be slow and isn't scriptable. So when the user asks about
troubleshooting, you might prefer kubectl commands.
```

### 2. Be specific about what's in scope and what isn't

A skill that says "use kubectl" is ambiguous — every k8s command? Just
troubleshooting? Including writes? Make it tight:

```markdown
For read-only troubleshooting (describe, logs, get, top), use kubectl.
For mutations (apply, delete, patch), confirm with the user first
before issuing the command.
```

### 3. Keep examples concrete

Examples turn skills from advice into reproducible behavior. Show the
exact command, exact prompt phrasing, exact response shape:

```markdown
## Examples

When asked "why is the foo pod restarting?":

  kubectl describe pod foo
  kubectl logs foo --previous --tail=100

Report the most recent termination reason from the `Status` block and
the last 5-10 lines of the previous logs. Don't dump full output.
```

## Where skills live

Three places, in load order:

1. **Bundled** — `core/jaiclaw-skills/src/main/resources/skills/<name>/SKILL.md`
   in the JaiClaw distribution. ~62 skills. Always on classpath.
2. **Workspace** — `~/.jaiclaw/skills/<name>/SKILL.md` (or whatever
   `jaiclaw.skills.workspace-dir` points at). For deployment-specific
   customizations.
3. **Plugin / MCP** — registered programmatically via the
   `SkillProvider` SPI. For skills that come from external systems
   (e.g., a corporate playbook hosted in Confluence and fetched via
   MCP).

Loading order matters because the LLM reads the system prompt
top-down. Bundled skills appear first; workspace skills override or
augment; plugin/MCP skills come last (most-specific wins).

## Configuration: scoping what loads

```yaml
jaiclaw:
  skills:
    workspace-dir: ~/.jaiclaw/skills
    allow-bundled: []                     # load NO bundled skills
    # allow-bundled: ["*"]                # default — load all (62)
    # allow-bundled: [summarize, web-research]   # whitelist
    allow-workspace: ["*"]                # load all workspace skills
    # allow-workspace: [my-team-style]    # whitelist
```

Two rules:

1. **Every embedded library use should set `allow-bundled: []`.** The
   default `["*"]` is for the general-purpose shell. See
   [SKILLS.md § The cost story](SKILLS.md#the-cost-story-most-important-part-of-this-page).
2. **Whitelist beats catch-all.** When you do load bundled skills,
   name them: `allow-bundled: [summarize, web-research]`. New bundled
   skills in future releases won't silently inflate your prompt.

## Tenant-scoped skills

```markdown
---
name: acme-corp-onboarding
tenantIds: [acme-corp]
description: Acme Corp's internal onboarding workflow
---
```

In `multi` tenant mode, `TenantSkillRegistry` filters skills by
`tenantIds` at agent-build time. The LLM serving tenant `beta-co`
never sees skills that list only `acme-corp`.

Empty `tenantIds` = available to every tenant. Don't conflate
"available to none" with "system-wide" — both are empty list; if you
want a skill genuinely disabled, remove it from `allow-bundled` /
`allow-workspace` instead.

## Versioning

`SkillMetadata.version` is informational at runtime — it's not enforced
by the loader. But it appears in:

- The MCP `resources/list` payload, so downstream agents can detect
  drift
- The audit trail (`AuditEvent`) for every LLM call
- The `jaiclaw-maven-plugin` analyze output

Bump it on every meaningful body change (not whitespace) so consumers
have a signal to recheck.

## Authoring workflow

The minimum loop:

```bash
# 1. Drop the file
mkdir -p ~/.jaiclaw/skills/my-team-style
$EDITOR ~/.jaiclaw/skills/my-team-style/SKILL.md

# 2. Make sure it loads
cat <<YAML >> application.yml
jaiclaw:
  skills:
    workspace-dir: ~/.jaiclaw/skills
    allow-workspace: [my-team-style]
YAML

# 3. Boot, check the AgentRuntime INFO log on startup
./mvnw spring-boot:run -pl :my-app
# look for: "Loaded skill: my-team-style (version 1.0.0)"

# 4. Hit it
curl -X POST http://localhost:8080/api/chat -d '{"sessionId":"t","message":"..."}'
```

`SkillLoader` emits a warning if the frontmatter is malformed (bad
YAML, missing required field). It's fail-fast — a broken skill prevents
startup.

## Anti-patterns

### Skill that's just a tool description

If the skill says "when the user asks X, call tool Y" — the tool's
own description should already say "use this when the user asks X." A
skill that just repeats that is wasted tokens. Make the tool
description tighter instead.

### Skill that contradicts another loaded skill

Two skills that disagree leave the LLM to pick — and it'll pick badly.
If skill A says "always use kubectl" and skill B says "always confirm
before kubectl", the LLM picks based on text proximity in the prompt,
which is fragile. Merge or sequence them: one skill, one stance.

### Skill that's actually three skills

If the body has three unrelated H2 sections ("kubectl", "AWS billing",
"git conventions"), split into three skills. Each becomes
independently load-controllable and the descriptions land more
precisely.

### Skill that hard-codes secrets

Skills end up in your system prompt and (depending on your audit
config) in trajectory logs. Don't put API keys, customer names,
internal URLs, etc. in skill bodies. Use a tool that reads from
environment variables instead.

## Where to next

- [SKILLS.md](SKILLS.md) — the cost story + operator-facing reference
- [How to create skills](../faqs/how-to-create-skills.md) — older,
  hands-on tutorial with screenshots
- [How to load skills](../faqs/how-to-load-skills.md) — config details
- [AUTHORING-TOOLS.md](AUTHORING-TOOLS.md) — the action complement to
  skills
- The 62 bundled skills under
  `core/jaiclaw-skills/src/main/resources/skills/` — read a few to
  internalize the shape
