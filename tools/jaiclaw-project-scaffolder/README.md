# JaiClaw Project Scaffolder

Generate complete, standalone JaiClaw Maven projects from a simple YAML manifest.

## Problem

JaiClaw has 25 example applications that all follow predictable patterns — 5 dependency archetypes with variation in AI provider, extensions, channels, tools, and configuration. Creating a new JaiClaw project by hand requires understanding:

- Which Maven dependencies to include for your chosen archetype (gateway, camel, embabel, etc.)
- How to wire `spring-boot-starter-parent` + `jaiclaw-bom` imports correctly
- Which Spring AI starter artifact corresponds to your AI provider
- The correct `application.yml` structure with Embabel exclusions, provider config, skill budgets
- How to create `ToolCallback` implementations with proper JSON schema
- The security, session, and multi-tenancy configuration patterns

New users often copy an existing example and strip out what they don't need — error-prone and time-consuming.

## Solution

Define your project in a YAML manifest (only `name` and `description` are required), and the scaffolder generates a complete Maven project ready to build and run. Two parent modes are supported:

- **Standalone** (default) — uses `spring-boot-starter-parent` + `jaiclaw-bom` import. Self-contained, no monorepo coupling.
- **JaiClaw** — uses `jaiclaw-parent` as Maven parent. Inherits managed versions, plugin config, and test infrastructure for a leaner POM.

## Architecture

Two entry points, one core engine:

```
tools/jaiclaw-project-scaffolder/     ← Core engine + Spring Shell CLI
  └─ ProjectManifest, generators, ProjectGenerator

jaiclaw-maven-plugin/                 ← Thin scaffold goal
  └─ ScaffoldMojo → delegates to ProjectGenerator
```

```
YAML Manifest → ProjectManifest.fromYamlMap() → validate()
                                                    │
                              ┌─────────────────────┼──────────────────────┐
                              ▼                     ▼                      ▼
                       PomGenerator      ApplicationYmlGenerator    ToolStubGenerator
                              │                     │                      │
                              ▼                     ▼                      ▼
                      ApplicationClassGenerator  ReadmeGenerator  SystemPromptGenerator
                              │                     │                      │
                              └─────────────────────┼──────────────────────┘
                                                    ▼
                                           ProjectGenerator
                                         (orchestrator — writes files)
```

## Manifest Schema Reference

```yaml
# jaiclaw-manifest.yml — only 'name' and 'description' are required

name: my-bot                             # REQUIRED. Kebab-case → artifactId + directory name
description: My awesome bot              # REQUIRED. Used in pom.xml and README

group-id: com.example                    # Default: com.example
java-package: com.example.mybot         # Default: derived from group-id + sanitized name
version: 0.1.0-SNAPSHOT                  # Default: 0.1.0-SNAPSHOT
jaiclaw-version: 0.3.0-SNAPSHOT          # Default: 0.3.0-SNAPSHOT

# Maven parent strategy
parent: standalone                       # standalone|jaiclaw (default: standalone)

# Core dependency pattern — determines which JaiClaw modules are included
archetype: gateway                       # gateway|embabel|camel|comprehensive|minimal
                                         # Default: gateway

# AI model provider
ai-provider:
  primary: anthropic                     # Default: anthropic
  additional: [minimax, ollama]          # Extra providers (disabled by default in yml)

# JaiClaw extension modules (jaiclaw-{name})
extensions: [security, documents, code]

# Channel modules (jaiclaw-channel-{name})
channels: [telegram, slack]

# Agent configuration
agent:
  name: My Agent                         # Default: PascalCase(name) + " Agent"
  tools-profile: full                    # none|minimal|coding|messaging|full (default: full)
  system-prompt:
    strategy: classpath                  # inline|classpath|none (default: none)
    content: ""                          # For inline strategy
    source: prompts/system-prompt.md     # For classpath strategy

# Skill budget control
skills:
  allow-bundled: []                      # Default: [] (critical for token budget)

# Security mode
security:
  mode: api-key                          # api-key|jwt|none (default: api-key)

# Custom tool stubs — generates compilable @Component ToolCallback classes
custom-tools:
  - name: search_faq
    description: Search the FAQ knowledge base
    section: helpdesk
    parameters:
      question: { type: string, description: "User question", required: true }

# Camel config — only for archetype: camel
camel:
  channel-id: my-channel
  display-name: My Channel
  stateless: true
  version: 4.18.1

# Embabel config — only for archetype: embabel
embabel:
  default-llm: claude-sonnet-4-5
  workflow: MyWorkflowAgent

# Server port
server:
  port: 8080                             # Default: 8080

# Docker / JKube profile
docker:
  enabled: true                          # Default: true
  base-image: eclipse-temurin:21-jre     # Default: eclipse-temurin:21-jre

# README content
readme:
  problem: ""                            # TODO placeholders if empty
  solution: ""
```

### Archetypes

| Archetype | Core Dependencies | Use Case |
|-----------|------------------|----------|
| `gateway` | starter + gateway + web | Standard REST API bot with channels |
| `embabel` | starter + gateway + embabel-delegate + web | GOAP-planned multi-step agent |
| `camel` | starter + gateway + camel + web | Apache Camel routing pipelines |
| `comprehensive` | starter + gateway + security + documents + media + web | Full-featured assistant |
| `minimal` | starter + web | Bare-bones, no gateway or channels |

### Valid Extensions

audit, browser, calendar, camel, canvas, code, compaction, cron, cron-manager, discord-tools, docs, docstore, docstore-telegram, documents, embabel-delegate, identity, media, messaging, plugin-sdk, security, slack-tools, subscription, subscription-telegram, tools-k8s, tools-security, voice, voice-call

### Valid Channels

telegram, slack, discord, email, sms, signal, teams

### Valid AI Providers

anthropic, openai, ollama, gemini, bedrock, azure-openai, deepseek, mistral, minimax, vertex-ai, oci-genai

## Manifest Examples Catalog

### 1. Minimal Bot — All Defaults

```yaml
name: minimal-bot
description: A minimal JaiClaw bot with all defaults
```

### 2. Helpdesk Bot — Security + Custom Tools + Multi-Channel

```yaml
name: helpdesk-bot
description: Multi-tenant support bot with FAQ lookup

extensions: [security, plugin-sdk]
channels: [telegram, slack]

agent:
  name: Helpdesk Agent
  tools-profile: full
  system-prompt:
    strategy: classpath
    source: prompts/system-prompt.md

security:
  mode: api-key

custom-tools:
  - name: search_faq
    description: Search the FAQ knowledge base
    section: helpdesk
    parameters:
      question: { type: string, description: "The user's question", required: true }
      category: { type: string, description: "FAQ category" }
```

### 3. PDF Summarizer — Camel Archetype

```yaml
name: pdf-summarizer
description: Camel-based PDF document summarizer

archetype: camel

extensions: [documents]

agent:
  name: PDF Summarizer
  tools-profile: minimal
  system-prompt:
    strategy: classpath
    source: prompts/system-prompt.md

skills:
  allow-bundled: [summarize]

security:
  mode: none

camel:
  channel-id: pdf-summarizer
  display-name: PDF Summarizer
  stateless: true
```

### 4. Research Planner — Embabel Archetype

```yaml
name: research-planner
description: Embabel-powered research planning agent

archetype: embabel

ai-provider:
  primary: gemini

skills:
  allow-bundled: [web-research]

security:
  mode: none

embabel:
  default-llm: claude-sonnet-4-5
  workflow: ResearchPlannerAgent

docker:
  enabled: false
```

### 5. Personal Assistant — Comprehensive

```yaml
name: personal-assistant
description: Full-featured personal assistant

archetype: comprehensive
group-id: io.mycompany
java-package: io.mycompany.assistant

ai-provider:
  primary: anthropic
  additional: [openai, ollama]

extensions: [calendar, voice, browser, identity, canvas]
channels: [telegram, slack, discord]

agent:
  name: Personal Assistant
  tools-profile: full
  system-prompt:
    strategy: inline
    content: |
      You are a personal assistant that helps with scheduling,
      web research, and daily tasks.

skills:
  allow-bundled: [summarize, web-research]

security:
  mode: jwt

server:
  port: 9090
```

### 6. Telegram Bot — Single Channel

```yaml
name: telegram-support
description: Telegram-only support bot

channels: [telegram]

agent:
  name: Support Bot
  system-prompt:
    strategy: inline
    content: You are a helpful support bot on Telegram.
```

### 7. Slack + Discord Bot — OpenAI Provider

```yaml
name: community-bot
description: Community bot for Slack and Discord

ai-provider:
  primary: openai

channels: [slack, discord]

agent:
  name: Community Bot
  tools-profile: messaging
```

### 8. Document Q&A

```yaml
name: document-qa
description: Document question-answering with vector search

extensions: [documents, docstore, security]

agent:
  name: Document QA Agent
  tools-profile: full
  system-prompt:
    strategy: classpath
    source: prompts/system-prompt.md
```

### 9. Code Review Bot

```yaml
name: code-review-bot
description: Automated code review assistant

extensions: [code]

agent:
  name: Code Reviewer
  tools-profile: coding

custom-tools:
  - name: review_pull_request
    description: Review a GitHub pull request
    section: code-review
    parameters:
      pr_url: { type: string, description: "GitHub PR URL", required: true }
      focus: { type: string, description: "Review focus area" }
```

### 10. Ollama Local — No API Keys

```yaml
name: local-assistant
description: Local AI assistant using Ollama

ai-provider:
  primary: ollama

security:
  mode: none

docker:
  enabled: false
```

### 11. Data Pipeline — Camel Stateless

```yaml
name: data-pipeline
description: ETL data pipeline with AI enrichment

archetype: camel

extensions: [documents]

agent:
  tools-profile: minimal

security:
  mode: none

camel:
  channel-id: data-pipeline
  display-name: Data Pipeline
  stateless: true

custom-tools:
  - name: transform_record
    description: Transform and enrich a data record
    section: etl
    parameters:
      record: { type: string, description: "JSON record to transform", required: true }
      schema: { type: string, description: "Target schema name" }
```

### 12. Minimal CLI — No Gateway

```yaml
name: cli-tool
description: Minimal command-line AI tool

archetype: minimal

security:
  mode: none

docker:
  enabled: false
```

## Parent Mode: Standalone vs JaiClaw

The `parent` field controls the Maven parent POM strategy. This is the most significant architectural choice in the manifest — it determines how your project resolves dependency versions and plugin configuration.

### Comparison

| Aspect | `standalone` (default) | `jaiclaw` |
|--------|----------------------|-----------|
| **Maven parent** | `spring-boot-starter-parent` | `jaiclaw-parent` |
| **Version management** | `jaiclaw-bom` import in `<dependencyManagement>` | Inherited from parent POM |
| **Test deps** | Explicit versions (Groovy 4.0.24, Spock 2.3) | Inherited — no versions in POM |
| **Build plugins** | gmavenplus, surefire config declared | Inherited — lean `<build>` |
| **JKube/Docker** | Inline `<profiles>` block | Inherited from parent's k8s profile |
| **POM size** | ~150 lines | ~70 lines |
| **Portability** | Works anywhere, no monorepo needed | Requires `jaiclaw-parent` in a Maven repo |
| **Version alignment** | Manual — update BOM version | Automatic — inherits parent version |
| **jaiclaw-maven-plugin** | Not included | Included with `${project.version}` + `analyze` goal |

### When to Use Each

**Choose `standalone` (default) when:**
- Building a project outside the JaiClaw monorepo
- Distributing to users who may not have `jaiclaw-parent` published
- You want full control over all dependency versions
- Deploying to environments where only Maven Central artifacts are available

**Choose `jaiclaw` when:**
- Building internal projects that extend the JaiClaw ecosystem
- You want automatic version alignment with JaiClaw releases
- You want the leanest possible POM
- `jaiclaw-parent` is published to your Maven repository (Maven Central or private Nexus)

### Example: Same Project in Both Modes

**Standalone mode** (default — `parent: standalone` or omitted):
```yaml
name: helpdesk-bot
description: Multi-tenant support bot with FAQ lookup
extensions: [security, plugin-sdk]
channels: [telegram, slack]
```

**JaiClaw mode** (`parent: jaiclaw`):
```yaml
name: helpdesk-bot
description: Multi-tenant support bot with FAQ lookup
parent: jaiclaw
extensions: [security, plugin-sdk]
channels: [telegram, slack]
```

The only manifest difference is one line: `parent: jaiclaw`. The generated POM is significantly different — see the generated POM comparison below.

### Generated POM Comparison

**Standalone mode** (~150 lines):
```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.5.14</version>
    <relativePath/>
</parent>

<groupId>com.example</groupId>
<artifactId>helpdesk-bot</artifactId>
<version>0.1.0-SNAPSHOT</version>

<properties>
    <java.version>21</java.version>
</properties>

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.jaiclaw</groupId>
            <artifactId>jaiclaw-bom</artifactId>
            <version>0.3.0-SNAPSHOT</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
<!-- ... dependencies, test deps with versions, gmavenplus, surefire, JKube profile ... -->
```

**JaiClaw mode** (~70 lines):
```xml
<parent>
    <groupId>io.jaiclaw</groupId>
    <artifactId>jaiclaw-parent</artifactId>
    <version>0.3.0-SNAPSHOT</version>
    <relativePath/>
</parent>

<artifactId>helpdesk-bot</artifactId>
<!-- No groupId (inherited), no version (inherited), no properties, no dependencyManagement -->
<!-- ... dependencies only, jaiclaw-maven-plugin with ${project.version} ... -->
```

## Generated Project Structure

```
my-bot/
  pom.xml                          ← Varies by parent mode (see above)
  README.md                        ← Problem/Solution/Architecture/Build sections
  src/
    main/
      java/com/example/mybot/
        MyBotApplication.java      ← @SpringBootApplication main class
        SearchFaqTool.java         ← Generated from custom-tools (if any)
      resources/
        application.yml            ← Full JaiClaw + Spring AI config
        prompts/system-prompt.md   ← If strategy=classpath
    test/
      groovy/com/example/mybot/   ← Empty, ready for Spock specs
```

## Build & Run

### Building the Scaffolder

```bash
export JAVA_HOME=/Users/tap/.sdkman/candidates/java/21.0.9-oracle

# Compile
./mvnw compile -pl :jaiclaw-project-scaffolder -am -o

# Run tests
./mvnw test -pl :jaiclaw-project-scaffolder -o

# Build standalone JAR
./mvnw package -pl :jaiclaw-project-scaffolder -Pstandalone -am -DskipTests -o
```

### Using the Scaffolder

**Standalone JAR:**
```bash
java -jar tools/jaiclaw-project-scaffolder/target/jaiclaw-project-scaffolder-*.jar \
    scaffold-create --manifest my-bot.yml --output-dir ./projects
```

**Embedded in jaiclaw-shell:**
```
shell:> scaffold create --manifest /path/to/my-bot.yml
```

**Maven Plugin:**
```bash
mvn io.jaiclaw:jaiclaw-maven-plugin:0.3.0-SNAPSHOT:scaffold \
    -Djaiclaw.scaffold.manifest=my-bot.yml \
    -Djaiclaw.scaffold.outputDir=./projects
```

### Building a Generated Project

```bash
cd projects/my-bot
mvn clean package -DskipTests
ANTHROPIC_API_KEY=your-key java -jar target/my-bot-0.1.0-SNAPSHOT.jar
```

## MCP Tool Integration

The scaffolder exposes three MCP tools via `ScaffoldMcpToolProvider` (server name: `project-scaffolder`). These are auto-configured when the scaffolder module is on the classpath.

### `scaffold_project`

Generates a complete standalone project from a YAML manifest string.

**Parameters:**
- `manifest_yaml` (string, required) — complete jaiclaw-manifest.yml content
- `output_dir` (string, optional) — parent directory for the project (default: `.`)

**Returns:** JSON with `status`, `project_path`, `artifact_id`, `archetype`, `ai_provider`, `extensions`, `channels`, `custom_tools`

### `validate_manifest`

Validates a manifest YAML without generating any files.

**Parameters:**
- `manifest_yaml` (string, required) — YAML content to validate

**Returns:** JSON with `valid` (boolean) and either project summary fields or `error` message

### `list_options`

Lists valid values for manifest fields.

**Parameters:**
- `category` (string, required) — one of: `extensions`, `channels`, `providers`, `archetypes`, `tool-profiles`, `security-modes`, `prompt-strategies`, `parent-modes`

**Returns:** JSON with `category` and `options` array

## Bundled Skill: `scaffold-project`

The `scaffold-project` skill (in `core/jaiclaw-skills/src/main/resources/skills/scaffold-project/SKILL.md`) guides the JaiClaw agent through an interactive requirements-gathering session to produce a `jaiclaw-manifest.yml`. Enable it with:

```yaml
jaiclaw:
  skills:
    allow-bundled: [scaffold-project]
```

The skill walks the user through 10 steps: project identity, archetype selection, AI provider, channels, extensions, agent configuration, custom tools, security, manifest generation, and next steps.

## Design

### Text Blocks vs Templates

Uses Java 21 text blocks + StringBuilder for code generation instead of an external template engine (Mustache, FreeMarker). This keeps the module lightweight with zero additional dependencies beyond Jackson YAML, and matches existing patterns in the codebase (`SkillSpec.toPrompt()`, `AnalyzeMojo` formatting).

### Two Parent Modes

Generated projects can use either `spring-boot-starter-parent` + `jaiclaw-bom` import (standalone, the default) or `jaiclaw-parent` directly (jaiclaw mode). Standalone mode is fully self-contained and portable. JaiClaw mode produces a leaner POM by inheriting managed versions, plugin config, and test infrastructure from the parent — but requires `jaiclaw-parent` to be published to a reachable Maven repository.

### Dual Entry Points

Spring Shell CLI for interactive/embedded use (works inside jaiclaw-shell) + Maven plugin goal for CI/scripting. Core logic lives in the scaffolder module; the maven plugin is a thin delegate.

### KnownModules Constants

`KnownModules.java` is the single source of truth for valid extension, channel, and provider names. When new modules are added to JaiClaw, update this class.

### Custom Tool Stubs

`custom-tools` definitions generate compilable `@Component` classes that implement `ToolCallback` with proper `ToolDefinition` (including JSON schema from parameters) and a TODO placeholder in `execute()`. Users fill in the implementation.

## Extending

### Adding a New Archetype

1. Add the enum value to `ProjectManifest.Archetype`
2. Add a case to `PomGenerator.coreDependencies()`
3. Add archetype-specific YAML sections to `ApplicationYmlGenerator`
4. Update this README's archetype table

### Adding a New AI Provider

1. Add the provider name to `KnownModules.AI_PROVIDERS`
2. Add a case to `KnownModules.springAiStarterArtifact()`
3. Add provider config to `ApplicationYmlGenerator.appendProviderConfig()`
4. Add API key prereqs to `ReadmeGenerator.appendApiKeyPrereqs()`

### Adding a New Extension or Channel

1. Add the name to `KnownModules.EXTENSIONS` or `KnownModules.CHANNELS`
2. No other changes needed — extensions and channels are wired generically
