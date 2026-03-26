# jaiclaw-rest-cli-architect (jrestcli)

Scaffolds Spring Shell CLI projects that wrap REST APIs. Every generated command is dual-use: human-facing CLI command AND JaiClaw LLM tool.

## Design Goals

- **Library by default** — plugs into JaiClaw as a standard module with tools the LLM can call (`cli_scaffold_project`, `cli_parse_openapi`, `cli_validate_spec`, `cli_from_openapi`)
- **Standalone with profile** — build with `-Pstandalone` for an executable JAR with Spring Shell REPL
- **Three input modes** — full JSON spec, interactive LLM conversation, or partial JSON with LLM gap-filling
- **Pure Java templates** — text blocks + `.formatted()`, no template engine dependency
- **Dual-use output** — generated projects have `@ShellComponent` commands with inner `ToolCallback` classes

## Build Modes

### Library (default)

```bash
./mvnw install -pl jaiclaw-rest-cli-architect -DskipTests
```

Produces a regular JAR. When on the classpath of `jaiclaw-gateway-app` or `jaiclaw-shell`, `CliArchitectAutoConfiguration` auto-registers tools into the `ToolRegistry`.

### Standalone (`-Pstandalone`)

```bash
./mvnw package -pl jaiclaw-rest-cli-architect -Pstandalone -DskipTests
```

Produces an executable fat JAR with Spring Shell. Adds Spring AI Anthropic for interactive mode.

### JBang

```bash
jbang jrestcli.java scaffold --spec acme.json
```

## Three Execution Modes

### Mode 1: Full JSON (`scaffold --spec project.json`)

No LLM needed. The JSON spec answers all questions. `ProjectGenerator` produces files deterministically.

```bash
java -jar target/jaiclaw-rest-cli-architect-0.1.0-SNAPSHOT.jar scaffold --spec acme.json
```

### Mode 2: Interactive (`interactive`)

LLM-driven. Walks through the 7-phase conversation from the cli-architect skill. Requires `-Pstandalone` build and an AI API key.

```bash
java -jar target/jaiclaw-rest-cli-architect-0.1.0-SNAPSHOT.jar interactive
```

### Mode 3: Partial JSON (`scaffold --spec partial.json --fill`)

JSON provides what's known. The LLM identifies gaps, asks the user to fill them, then generates.

```bash
java -jar target/jaiclaw-rest-cli-architect-0.1.0-SNAPSHOT.jar scaffold --spec partial.json --fill
```

## Commands

| Command | Description |
|---------|-------------|
| `scaffold --spec <path> [--fill]` | Generate project from JSON spec. `--fill` identifies gaps for LLM filling. |
| `validate --spec <path>` | Validate spec, report missing/invalid fields |
| `from-openapi --url <url> [--output <path>]` | Parse OpenAPI spec, generate spec JSON template |
| `interactive` | Full LLM-driven 7-phase conversation (requires AI model) |

## Spec JSON Format

```json
{
  "name": "acme",
  "mode": "STANDALONE",
  "outputDir": "/path/to/output",
  "groupId": "com.example",
  "packageName": "com.example.cli.acme",
  "outputFormat": "both",
  "api": {
    "title": "Acme CRM API",
    "baseUrl": "https://api.acme.com/v1",
    "openapiSpec": "https://api.acme.com/v1/openapi.json"
  },
  "auth": {
    "type": "header",
    "headerName": "X-API-Key",
    "headerValuePrefix": "",
    "envVar": "ACME_API_KEY"
  },
  "endpoints": [
    {
      "method": "GET",
      "path": "/users",
      "operationId": "listUsers",
      "summary": "List all users",
      "commandKey": "list-users",
      "params": []
    },
    {
      "method": "GET",
      "path": "/users/{id}",
      "operationId": "getUser",
      "summary": "Get user by ID",
      "commandKey": "get-user",
      "params": [{ "name": "id", "type": "string", "in": "path", "required": true }]
    }
  ]
}
```

### Spec Fields

| Field | Required | Description |
|-------|----------|-------------|
| `name` | Yes | Short name for CLI prefix and module (e.g. "acme") |
| `mode` | No | `JAICLAW_SUBMODULE`, `EXTERNAL_SUBMODULE`, `STANDALONE` (default), `JBANG` |
| `outputDir` | For non-JAICLAW modes | Where to write generated files |
| `groupId` | For non-JAICLAW modes | Maven groupId |
| `packageName` | No | Java package (defaults to `{groupId}.cli.{name}`) |
| `outputFormat` | No | `json`, `table`, or `both` (default) |
| `api.title` | No | Display name for the API |
| `api.baseUrl` | Yes* | Base URL (*or provide `openapiSpec`) |
| `api.openapiSpec` | No | URL/path to OpenAPI spec (auto-extracts endpoints/auth) |
| `auth.type` | No | `none` (default), `header`, `basic`, `oauth2` |
| `endpoints` | Yes* | Endpoint list (*not needed when `openapiSpec` provided) |

### Auth Types

**Header** (`X-API-Key`, `Authorization: Bearer ...`):
```json
{ "type": "header", "headerName": "X-API-Key", "envVar": "ACME_API_KEY" }
```

**Basic**:
```json
{ "type": "basic", "usernameEnv": "ACME_USER", "passwordEnv": "ACME_PASS" }
```

**OAuth2 Client Credentials**:
```json
{ "type": "oauth2", "tokenUrl": "https://auth.example.com/token", "clientIdEnv": "ACME_CLIENT_ID", "clientSecretEnv": "ACME_CLIENT_SECRET", "scopes": ["read"] }
```

## Generated Project Structure

### STANDALONE / EXTERNAL_SUBMODULE

```
{name}-cli/
  pom.xml
  .env.example
  src/main/java/{package}/
    {Name}CliApplication.java
    {Name}ApiClient.java
    {Name}Commands.java        # @ShellComponent + inner ToolCallback classes
  src/main/resources/
    application.yml
  src/test/groovy/{package}/
    {Name}CommandsSpec.groovy
```

### JAICLAW_SUBMODULE

Same as above but POM uses `jaiclaw-parent`, no Application class needed.

### JBANG

Single file: `{Name}Cli.java` with `//DEPS` directives.

## LLM Tools (Library Mode)

When on the classpath, these tools are registered in `ToolRegistry`:

| Tool | Description |
|------|-------------|
| `cli_scaffold_project` | Generate project from spec JSON |
| `cli_parse_openapi` | Parse OpenAPI spec to structured data |
| `cli_validate_spec` | Validate spec JSON |
| `cli_from_openapi` | Generate spec template from OpenAPI URL |

## JSON Schema

`src/main/resources/spec-schema.json` provides a JSON Schema for IDE autocompletion in spec files.
