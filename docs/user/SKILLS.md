# Skills

> **Audience:** anyone running JaiClaw who's wondered "why is my
> token bill so high?" or "how do I teach the agent to do X?"

Skills are JaiClaw's mechanism for behavioral instructions — Markdown
files with YAML frontmatter that get loaded into the LLM's system
prompt. A skill might be "how to debug Kubernetes pod failures" or
"write commit messages in our style" — domain knowledge that doesn't
fit in a tool description.

This page covers what skills are, the **cost** of the default config
(spoiler: ~26K input tokens per LLM call), and how to tune both.

## How a skill is shaped

```markdown
---
name: kubectl-helper
description: When the user asks about Kubernetes troubleshooting, prefer kubectl over the dashboard.
version: 1.0.0
tenantIds: []         # empty = all tenants
---
# kubectl Helper

When debugging a misbehaving pod, prefer `kubectl describe pod <name>`
over the dashboard — it shows events, conditions, and recent
restarts in one view.

If the pod is `CrashLoopBackOff`, always check `kubectl logs <pod>
--previous` to see what crashed.
```

The frontmatter is consumed by `SkillLoader`; the body is what the
LLM actually sees in its system prompt.

Three places skills live:

1. **Bundled** — `core/jaiclaw-skills/src/main/resources/skills/`.
   62 skills ship in the framework. Always on classpath.
2. **Workspace** — `~/.jaiclaw/skills/<name>/SKILL.md` (or whatever
   `jaiclaw.skills.workspace-dir` points at). Custom per-deployment.
3. **MCP / plugin** — registered programmatically by plugins via the
   `SkillProvider` SPI.

## The cost story (most important part of this page)

By default, **every bundled skill is loaded into every LLM call** —
the `allow-bundled` property defaults to `["*"]` (wildcard).

On a typical system that means:

- ~27 of the 62 bundled skills pass eligibility checks (some have
  conditional metadata)
- ~26,000 input tokens added to *every* LLM request
- A "hello" prompt that should cost ~500 tokens instead costs ~33,000

At Anthropic's published Sonnet 4.5 pricing
(\$3 / 1M input tokens), that's the difference between
\$0.0015 and \$0.10 per `hello` — a 67× multiplier paid on every
turn. For a low-volume hobby project it's invisible; for a chat bot
handling 10,000 user messages a day it's roughly $1,000/month in
overhead vs $15/month.

The default is **only correct for the general-purpose shell** that
ships in `apps/jaiclaw-shell` — that app *should* have every bundled
skill available. Any other deployment (custom Spring app, embedded
library use, an example) should explicitly slim the set.

## Configuration: `allow-bundled`

```yaml
jaiclaw:
  skills:
    allow-bundled: []                  # load NO bundled skills
    # or:
    # allow-bundled: ["*"]             # default — load all (62)
    # or:
    # allow-bundled: [summarize, web-research]    # whitelist by name
```

Three sensible defaults by application shape:

| App shape | Recommended value |
|---|---|
| Embedded library in a custom Spring Boot app | `[]` (load only your own skills) |
| Single-domain chat bot (helpdesk, travel planner, etc.) | `[]` + a 1-3 skill whitelist of what's relevant |
| Multi-tool general-purpose shell | `["*"]` (default — that's what it's for) |
| Example app | `[]` — see CLAUDE.md § Example README Requirements |

After changing the config, verify the actual cost with the agent
runtime's INFO log on startup — `AgentRuntime` reports the
prompt-token impact of the loaded skill set.

## Build-time check: `jaiclaw-maven-plugin`

Every example app's `pom.xml` includes:

```xml
<plugin>
  <groupId>io.jaiclaw</groupId>
  <artifactId>jaiclaw-maven-plugin</artifactId>
  <version>${project.version}</version>
  <executions>
    <execution>
      <goals><goal>analyze</goal></goals>
    </execution>
  </executions>
</plugin>
```

The `analyze` goal fires during the `verify` phase and surfaces:

- Apps using `allow-bundled: ["*"]` (the dangerous default)
- Token-budget overruns relative to the configured budget
- Skill / model / tool mismatches

For your own apps, add the plugin to the same `verify` phase and set
a target budget. The plugin fails the build if your config silently
adds the 26K-token tax.

## Authoring a custom skill

The simplest custom skill is just a Markdown file with frontmatter.
Drop it in `~/.jaiclaw/skills/<name>/SKILL.md`:

```markdown
---
name: my-team-style
description: When writing summaries, follow our internal style guide.
version: 1.0.0
---
# My Team Style

Write summaries in active voice. Lead with the verdict, then evidence.
Use bullets only for >3 parallel items.
```

Then in `application.yml`:

```yaml
jaiclaw:
  skills:
    workspace-dir: ~/.jaiclaw/skills    # already the default
    allow-bundled: []
    allow-workspace: ["*"]              # or a tighter whitelist
```

Boot the app — `SkillLoader` finds it on startup and the body lands
in the system prompt for matching agents.

## Tenant-scoped skills (multi-tenant mode)

```markdown
---
name: acme-corp-onboarding
description: Acme Corp onboarding instructions
tenantIds: [acme-corp]      # only loaded for this tenant
---
```

In `multi` tenant mode (`jaiclaw.tenant.mode: multi`), the
`TenantSkillRegistry` filters skills by `tenantIds`. Empty list ==
available to every tenant.

## Versioning

`SkillMetadata.version` is informational today — it's surfaced in
the audit trail and MCP resource listings but not enforced. Bump it
when you change skill content so downstream consumers (other agents
calling via MCP) can detect drift.

## Where to next

- [How to create skills](../faqs/how-to-create-skills.md) — older,
  hands-on tutorial
- [How to load skills](../faqs/how-to-load-skills.md) — config
  details
- [TOKEN-OPTIMIZATION.md](TOKEN-OPTIMIZATION.md) — the rest of the
  token-budget story (skills aren't the only contributor)
- [OPERATIONS.md § Skills Configuration](OPERATIONS.md#skills-configuration)
  — full reference + operational guidance
