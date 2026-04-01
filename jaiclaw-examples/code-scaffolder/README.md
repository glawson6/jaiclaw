# Code Scaffolder Example

Project scaffolding agent — uses the Spring AI tool loop to browse templates and generate complete project structures, with a plugin hook that injects coding standards into every prompt.

## What This Demonstrates

- **Spring AI tool loop** (default mode) with up to 25 iterations for multi-file generation
- **BEFORE_PROMPT_BUILD** modifying hook to inject coding standards into the system prompt
- **Plugin-based tool registration** via `JaiClawPlugin` SPI
- Four custom tools: `list_templates`, `read_template`, `generate_file`, `create_project_structure`
- Template system with variable substitution (project name, package path, entity name)

## Architecture

Where this example fits in JaiClaw:

```
┌──────────────────────────────────────────────────────────┐
│                  CODE SCAFFOLDER APP                       │
│                (standalone Spring Boot)                    │
├──────────────────┬───────────────────────────────────────┤
│ Gateway          │  REST API (/api/chat, /api/health)      │
├──────────────────┼───────────────────────────────────────┤
│ Plugin           │  CodeScaffolderPlugin                    │
│                  │  → 4 tools + BEFORE_PROMPT_BUILD hook    │
├──────────────────┼───────────────────────────────────────┤
│ Agent            │  Spring AI tool loop (max 25 iters)      │
│                  │  → list → read → create → generate       │
├──────────────────┼───────────────────────────────────────┤
│ Core             │  jaiclaw-core (records, SPI)              │
└──────────────────┴───────────────────────────────────────┘

Data flow:
  User ──("scaffold a Spring Boot API")──► Agent
                                            │
                              ┌──────────────┤
                              ▼              ▼
                       list_templates   read_template
                              │              │
                              ▼              ▼
                    create_project_structure
                              │
                              ▼ (repeated)
                       generate_file × N
                              │
                              ▼
                    Complete project skeleton
```

## Prerequisites

- Java 21+
- JaiClaw built and installed (`./mvnw install -DskipTests` from project root)
- Anthropic API key

## Build & Run

```bash
cd jaiclaw-examples/code-scaffolder
export JAVA_HOME=$HOME/.sdkman/candidates/java/21.0.9-oracle
ANTHROPIC_API_KEY=sk-ant-... ../../mvnw spring-boot:run
```

### Using MiniMax

To run with MiniMax instead of Anthropic:

```bash
AI_PROVIDER=minimax MINIMAX_ENABLED=true MINIMAX_API_KEY=your-key ../../mvnw spring-boot:run
```


## Testing It

```bash
# Generate a Spring Boot API project
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"content": "Create a Spring Boot REST API project called inventory-service with an Item entity"}'

# List available templates
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"content": "What project templates are available for Java?"}'

# Health check
curl http://localhost:8080/api/health
```
