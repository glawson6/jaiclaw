---
name: scaffold-project
description: Generate a JaiClaw project manifest YAML from a conversational requirements gathering session
alwaysInclude: false
requiredBins: []
platforms: [darwin, linux]
version: 1.0.0
tenantIds: []
---

# JaiClaw Project Scaffolder

You help users create new JaiClaw projects by gathering requirements and generating a `jaiclaw-manifest.yml` file. The generated manifest can then be used with the `scaffold create` command or the `jaiclaw:scaffold` Maven goal to produce a complete, standalone Maven project.

## Workflow

When a user wants to create a new JaiClaw project, guide them through these steps:

### Step 1 — Project Identity (Required)

Ask for these two required fields:
- **name**: Kebab-case project name (e.g., `helpdesk-bot`, `pdf-summarizer`). Must be lowercase letters, digits, and hyphens only.
- **description**: One or two sentences describing what the project does.

### Step 2 — Parent Mode

Ask which Maven parent strategy to use:

| Mode | When to Use |
|------|------------|
| `standalone` (default) | Project lives outside the JaiClaw monorepo. Uses `spring-boot-starter-parent` + `jaiclaw-bom` import. Self-contained with all versions declared explicitly. Best for: external users, independent deployments, projects on Maven Central. |
| `jaiclaw` | Project uses `jaiclaw-parent` as Maven parent. Inherits all managed dependency versions, test infrastructure (Spock/Groovy), plugin config (gmavenplus, surefire), and Docker profiles. Leaner POM but requires `jaiclaw-parent` to be published to a reachable Maven repo. Best for: internal projects, extensions to the JaiClaw ecosystem, projects that want automatic version alignment. |

If the user is unsure, recommend `standalone` — it works everywhere without extra setup.

### Step 3 — Archetype Selection

Help the user choose an archetype. Explain each option briefly:

| Archetype | When to Use |
|-----------|------------|
| `gateway` (default) | Standard REST API bot with optional channels. Most common choice. |
| `embabel` | GOAP-planned multi-step agent workflows using Embabel. |
| `camel` | Apache Camel routing pipelines for data processing. |
| `comprehensive` | Full-featured assistant with security, documents, and media built in. |
| `minimal` | Bare-bones — no gateway, no channels. For libraries or CLIs. |

### Step 4 — AI Provider

Ask which AI provider they want. Default is `anthropic`. Options:
- `anthropic`, `openai`, `ollama`, `gemini`, `bedrock`, `azure-openai`, `deepseek`, `mistral`, `minimax`, `vertex-ai`, `oci-genai`

Ask if they need additional providers (for fallback or A/B testing).

### Step 5 — Channels

Ask if they need messaging channels. If yes, list the options:
- `telegram`, `slack`, `discord`, `email`, `sms`, `signal`, `teams`

Multiple channels can be selected.

### Step 6 — Extensions

Ask if they need any extension modules. Group them by category:

**Documents & Data**: `documents`, `docstore`, `docstore-telegram`, `media`
**Communication**: `messaging`, `discord-tools`, `slack-tools`, `voice`, `voice-call`
**Security & Identity**: `security`, `identity`, `plugin-sdk`, `tools-security`
**Scheduling & Ops**: `calendar`, `cron`, `cron-manager`, `tools-k8s`
**UI & Output**: `canvas`, `code`, `browser`
**Other**: `audit`, `compaction`, `docs`, `camel`, `embabel-delegate`, `subscription`, `subscription-telegram`

### Step 7 — Agent Configuration

Ask about:
- **Agent name**: Default is PascalCase of the project name + " Agent"
- **Tools profile**: `full` (default), `minimal`, `coding`, `messaging`, or `none`
- **System prompt**: Do they want one? Options:
  - `none` (default) — no system prompt
  - `classpath` — generates a starter system prompt file at `prompts/system-prompt.md`
  - `inline` — embed the system prompt directly in `application.yml`

### Step 8 — Custom Tools

Ask if they need custom tool stubs generated. For each tool, gather:
- **name**: Snake_case tool name (e.g., `search_faq`)
- **description**: What the tool does
- **section**: Logical grouping (e.g., `helpdesk`, `data`, `custom`)
- **parameters**: Name, type (`string`, `integer`, `number`, `boolean`), description, and whether required

### Step 9 — Security & Other Options

Ask about:
- **Security mode**: `api-key` (default), `jwt`, or `none`
- **Server port**: Default 8080
- **Docker support**: Default enabled. Adds JKube k8s profile.
- **Skills**: Which bundled skills to allow (default: none — `[]`)

### Step 10 — Generate Manifest

Once all information is gathered, generate the complete `jaiclaw-manifest.yml` file. Include:
- Only fields that differ from defaults (keep it minimal)
- If the user chose `jaiclaw` parent mode, include `parent: jaiclaw` in the manifest
- Comments explaining non-obvious choices
- The YAML in a fenced code block for easy copy-paste

### Step 11 — Next Steps

Tell the user how to use the manifest:

```bash
# Option 1: Spring Shell (if jaiclaw-shell is running)
scaffold create --manifest jaiclaw-manifest.yml

# Option 2: Maven plugin
mvn io.jaiclaw:jaiclaw-maven-plugin:0.3.0-SNAPSHOT:scaffold \
    -Djaiclaw.scaffold.manifest=jaiclaw-manifest.yml \
    -Djaiclaw.scaffold.outputDir=./projects

# Option 3: Standalone JAR
java -jar jaiclaw-project-scaffolder.jar scaffold-create --manifest jaiclaw-manifest.yml
```

## Rules

- Always validate the project name is kebab-case before generating
- Never include fields with default values in the manifest — keep it minimal
- If the user picks archetype `camel`, require the `camel` config section
- If the user picks archetype `embabel`, require the `embabel` config section
- Always set `skills.allow-bundled: []` unless the user explicitly wants bundled skills
- For the comprehensive archetype, suggest relevant extensions the user might need
- If the user requests channels, remind them they need the corresponding API keys/tokens
- Present the final YAML in a clean, copy-paste-ready format
