---
name: cli-architect
description: "Generate Spring Shell CLI modules that wrap REST APIs, with dual-use as both human CLI commands and JaiClaw LLM tools. Use when asked to create a CLI for an API, wrap an API in a command-line tool, build a CLI wrapper, or generate commands for a REST service. Supports OpenAPI spec parsing, API docs fetching, and auto-extraction of auth patterns."
alwaysInclude: false
requiredBins: []
platforms: [darwin, linux]
version: 1.0.0
---

# CLI Architect

Generate Spring Shell CLI modules that wrap arbitrary REST APIs. Every generated command is dual-use: it works as a human-facing CLI command AND as a JaiClaw tool the LLM can call directly.

## Conversation Flow

Follow these 7 phases in order. Do not skip phases. Ask questions conversationally — gather what you can from provided docs/specs before asking the user.

---

### Phase 1: Project Location

Ask the user where the generated CLI module should live:

**Option A — JaiClaw sub-module:**
- Lives at `jaiclaw/jaiclaw-cli-{name}/`
- Inherits `jaiclaw-parent` POM (versions, plugins, test infra)
- Added as `<module>` to root POM and BOM
- Auto-discovered by `jaiclaw-shell` and `jaiclaw-gateway-app` when on classpath

**Option B — Sub-module in another project:**
- Ask for the project path (e.g., `/Users/tap/dev/workspaces/myproject`)
- Read that project's root POM to determine parent, groupId, version conventions
- Added as `<module>` to that project's root POM
- Imports `jaiclaw-bom` in `<dependencyManagement>` for JaiClaw deps

**Option C — Standalone new project:**
- Ask for the output path and desired groupId/artifactId
- Generates a complete Spring Boot project with `spring-boot-starter-parent`
- Imports `jaiclaw-bom` for JaiClaw deps
- Self-contained — runs with `./mvnw spring-boot:run`

**Option D — JBang script:**
- Generates a single self-contained `.java` file with JBang `//DEPS` directives
- No Maven project, no build step — just `jbang {Name}Cli.java`
- Best for lightweight wrappers, quick prototypes, or APIs with few endpoints
- Still supports interactive REPL and non-interactive piping

Store the chosen mode and paths for use in Phase 7.

---

### Phase 2: API Discovery

Ask: "What API are you wrapping?"

Accept any of these inputs (try in order):

1. **OpenAPI/Swagger spec URL or file path** — best source. Fetch or read the spec, parse it to extract:
   - API title, description, base URL (`servers[0].url`)
   - All endpoints (path + method + operationId + summary + parameters + request body schema)
   - Security schemes (from `securityDefinitions` / `components.securitySchemes`)
   - Group endpoints by tags

2. **API docs URL** — use **WebFetch** to retrieve the docs page. Parse for:
   - Base URL patterns
   - Endpoint listings (look for REST patterns: `GET /v1/...`, `POST /v2/...`)
   - Auth instructions (look for "Authentication", "Authorization", "API Key", "Bearer Token", "OAuth")
   - Rate limit info

3. **Manual description** — if no spec or docs, ask:
   - What is the API called?
   - What is the base URL?
   - What endpoints do you need? (method + path + description + parameters)

Store all discovered API metadata for subsequent phases.

---

### Phase 3: Authentication

Extract auth from the OpenAPI spec or docs first, then confirm with the user.

Present what was found:
> "I found the following auth methods in the API spec: [list]. Is this correct? Which should the CLI use?"

If nothing was found, ask directly:
> "How does this API authenticate?"

Supported auth patterns (first-class):

**OAuth2 Client Credentials:**
```yaml
auth:
  type: oauth2
  token-url: https://auth.example.com/oauth/token
  client-id: ${ACME_CLIENT_ID}
  client-secret: ${ACME_CLIENT_SECRET}
  scopes: [read, write]
```

**Custom Header (API Key, Bearer Token):**
```yaml
auth:
  type: header
  header-name: X-API-Key          # or "Authorization"
  header-value-prefix: ""         # or "Bearer "
  env-var: ACME_API_KEY
```

**Basic Auth:**
```yaml
auth:
  type: basic
  username-env: ACME_USERNAME
  password-env: ACME_PASSWORD
```

**No Auth:**
```yaml
auth:
  type: none
```

Ask the user to name the environment variables. Suggest sensible defaults based on the API name:
- `{API_NAME}_API_KEY` for header auth
- `{API_NAME}_CLIENT_ID` / `{API_NAME}_CLIENT_SECRET` for OAuth2
- `{API_NAME}_BASE_URL` for the base URL (always generated)

---

### Phase 4: Endpoint Selection

If many endpoints were discovered, present them grouped by tag/category:

```
Users (5 endpoints):
  GET  /v1/users          — List all users
  GET  /v1/users/{id}     — Get user by ID
  POST /v1/users          — Create user
  PUT  /v1/users/{id}     — Update user
  DEL  /v1/users/{id}     — Delete user

Orders (3 endpoints):
  GET  /v1/orders          — List orders
  GET  /v1/orders/{id}     — Get order
  POST /v1/orders          — Create order
```

Ask: "Which endpoints should the CLI wrap?"
- **All** — wrap every discovered endpoint
- **Categories** — "users, orders" — wrap all in those groups
- **Specific** — user picks individual endpoints

For each selected endpoint, confirm:
- CLI command name (derive from operationId or method+path, e.g., `acme list-users`)
- Tool name (snake_case, e.g., `acme_list_users`)
- Parameters (path params, query params, request body fields)

---

### Phase 5: Naming

Ask: "What should the command group be called?"

This determines:
- CLI prefix: `{name} list-users`, `{name} get-order 123`
- Tool section: all tools grouped under `{name}` in the LLM prompt
- Module artifact: `jaiclaw-cli-{name}` or `{name}-cli`
- Package: `io.jaiclaw.cli.{name}` or `{groupId}.cli.{name}`
- Config prefix: `{name}.api.*` in YAML

Suggest a default based on the API name (e.g., "Acme CRM" → `acme`).

---

### Phase 6: Output Format & Interaction Modes

The generated CLI **must** work in two modes:

- **Interactive** (REPL) — `./mvnw spring-boot:run` or `java -jar app.jar` drops into a Spring Shell prompt
- **Non-interactive** (script/pipe) — `java -jar app.jar {prefix} list-users | jq '.[]'` runs a single command and exits

Ask: "How should API responses be formatted?"

Options:
1. **JSON pretty-print** (default) — `objectMapper.writerWithDefaultPrettyPrinter()`
2. **Table** — extract key fields into tabular output
3. **Both** — add `--format` / `-f` flag (json | table), default to table for list endpoints and json for detail endpoints

If table is selected, for each list endpoint ask which fields to show as columns (or auto-detect from response schema).

**Non-interactive requirements:**
- All commands output clean JSON to stdout (no ANSI colors, no banners, no decorative formatting when piped)
- Exit codes: `0` for success, `1` for client errors, `2` for auth errors, `3` for server errors
- Errors go to stderr, data goes to stdout — enables clean `| jq` piping
- Spring Shell's interactive prompt is disabled when CLI args are present

---

### Phase 7: Generate

Generate all files based on the collected information. The exact file set depends on the project location mode from Phase 1.

#### File Generation — All Modes

**1. ApiClient class** — shared REST client with auth:

```java
package {package};

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class {Name}ApiClient {

    private final RestClient restClient;

    public {Name}ApiClient(
            @Value("${{prefix}.api.base-url}") String baseUrl
            /* auth params from Phase 3 */) {

        RestClient.Builder builder = RestClient.builder()
                .baseUrl(baseUrl);

        // Auth configuration based on Phase 3 selection:
        // HEADER:  .defaultHeader(headerName, prefix + envValue)
        // OAUTH2:  .requestInterceptor(new OAuth2Interceptor(tokenUrl, clientId, clientSecret))
        // BASIC:   .defaultHeaders(h -> h.setBasicAuth(username, password))

        this.restClient = builder.build();
    }

    public String get(String path) {
        return restClient.get().uri(path).retrieve().body(String.class);
    }

    public String get(String path, Object... uriVars) {
        return restClient.get().uri(path, uriVars).retrieve().body(String.class);
    }

    public String post(String path, Object body) {
        return restClient.post().uri(path)
                .body(body)
                .retrieve().body(String.class);
    }

    public String put(String path, Object body, Object... uriVars) {
        return restClient.put().uri(path, uriVars)
                .body(body)
                .retrieve().body(String.class);
    }

    public String delete(String path, Object... uriVars) {
        return restClient.delete().uri(path, uriVars).retrieve().body(String.class);
    }
}
```

**2. OAuth2Interceptor (if OAuth2 auth):**

```java
package {package};

import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

public class OAuth2Interceptor implements ClientHttpRequestInterceptor {

    private final String tokenUrl;
    private final String clientId;
    private final String clientSecret;
    private final String scopes;
    private final ReentrantLock lock = new ReentrantLock();

    private String accessToken;
    private Instant expiry = Instant.EPOCH;

    public OAuth2Interceptor(String tokenUrl, String clientId,
                             String clientSecret, String scopes) {
        this.tokenUrl = tokenUrl;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.scopes = scopes;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                        ClientHttpRequestExecution execution) throws IOException {
        request.getHeaders().setBearerAuth(getAccessToken());
        return execution.execute(request, body);
    }

    private String getAccessToken() {
        if (Instant.now().isBefore(expiry.minusSeconds(30))) {
            return accessToken;
        }
        lock.lock();
        try {
            if (Instant.now().isBefore(expiry.minusSeconds(30))) {
                return accessToken;
            }
            var response = RestClient.create().post()
                    .uri(tokenUrl)
                    .headers(h -> h.setBasicAuth(clientId, clientSecret))
                    .body("grant_type=client_credentials&scope=" + scopes)
                    .retrieve()
                    .body(Map.class);

            accessToken = (String) response.get("access_token");
            int expiresIn = (int) response.getOrDefault("expires_in", 3600);
            expiry = Instant.now().plusSeconds(expiresIn);
            return accessToken;
        } finally {
            lock.unlock();
        }
    }
}
```

**3. Command classes** — one per endpoint group, dual-use:

```java
package {package};

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jaiclaw.core.tool.ToolCallback;
import io.jaiclaw.core.tool.ToolContext;
import io.jaiclaw.core.tool.ToolDefinition;
import io.jaiclaw.core.tool.ToolResult;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

@ShellComponent
public class {Name}{Group}Commands {

    private final {Name}ApiClient client;
    private final ObjectMapper objectMapper;

    public {Name}{Group}Commands({Name}ApiClient client, ObjectMapper objectMapper) {
        this.client = client;
        this.objectMapper = objectMapper;
    }

    // ═══ CLI Command ═══
    @ShellMethod(value = "{endpoint.summary}", key = "{prefix} {command-key}")
    public String {methodName}(
            @ShellOption(defaultValue = ShellOption.NULL) String id,
            @ShellOption(value = "--format", defaultValue = "json") String format
            /* more params */) {
        String raw = client.get("{endpoint.path}", id);
        return formatOutput(raw, format);
    }

    private String formatOutput(String json, String format) {
        try {
            Object parsed = objectMapper.readValue(json, Object.class);
            if ("table".equals(format)) {
                // Table formatting for list endpoints (implement per-command)
                return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(parsed);
            }
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(parsed);
        } catch (Exception e) {
            return json; // Already formatted or not JSON — return as-is
        }
    }

    // ═══ JaiClaw Tool (inner class per endpoint) ═══
    @Component
    public static class {ToolClassName} implements ToolCallback {

        private final {Name}{Group}Commands commands;

        public {ToolClassName}({Name}{Group}Commands commands) {
            this.commands = commands;
        }

        @Override
        public ToolDefinition definition() {
            return new ToolDefinition(
                    "{prefix}_{tool_name}",
                    "{endpoint.summary}",
                    "{prefix}",
                    """
                    {
                      "type": "object",
                      "properties": {
                        // generated from endpoint parameters
                      },
                      "required": [/* required params */]
                    }
                    """,
                    Set.of(io.jaiclaw.core.tool.ToolProfile.FULL)
            );
        }

        @Override
        public ToolResult execute(Map<String, Object> parameters, ToolContext context) {
            try {
                String result = commands.{methodName}(
                        (String) parameters.get("id")
                        /* extract more params */);
                return new ToolResult.Success(result);
            } catch (Exception e) {
                return new ToolResult.Error("Failed to call {endpoint}: " + e.getMessage());
            }
        }
    }
}
```

**4. application.yml:**

```yaml
spring:
  shell:
    interactive:
      enabled: true              # REPL mode by default; Spring Shell auto-disables when CLI args present
    script:
      enabled: true              # Allow script mode (run single commands)
  main:
    banner-mode: "off"           # No banner when piped

{prefix}:
  api:
    base-url: ${{PREFIX}_BASE_URL:https://api.example.com}
    # Auth-specific config (varies by auth type)
    key: ${{PREFIX}_API_KEY:}
    # OR
    oauth2:
      token-url: ${{PREFIX}_TOKEN_URL:}
      client-id: ${{PREFIX}_CLIENT_ID:}
      client-secret: ${{PREFIX}_CLIENT_SECRET:}
      scopes: ${{PREFIX}_SCOPES:}
```

**5. .env.example:**

```bash
{PREFIX}_BASE_URL=https://api.example.com
{PREFIX}_API_KEY=your-api-key-here
# OR for OAuth2:
# {PREFIX}_TOKEN_URL=https://auth.example.com/oauth/token
# {PREFIX}_CLIENT_ID=your-client-id
# {PREFIX}_CLIENT_SECRET=your-client-secret
```

**6. Spock test stub:**

```groovy
package {package}

import spock.lang.Specification

class {Name}{Group}CommandsSpec extends Specification {

    def client = Mock({Name}ApiClient)
    def commands = new {Name}{Group}Commands(client)

    def "{prefix} {command-key} calls correct endpoint"() {
        when:
        commands.{methodName}(/* params */)

        then:
        1 * client.get("{endpoint.path}", /* params */)
    }
}
```

#### Mode-Specific Generation

**Mode A — JaiClaw sub-module:**

POM uses `<parent>jaiclaw-parent</parent>`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>io.jaiclaw</groupId>
        <artifactId>jaiclaw-parent</artifactId>
        <version>0.1.0-SNAPSHOT</version>
    </parent>

    <artifactId>jaiclaw-cli-{name}</artifactId>
    <name>JaiClaw CLI: {DisplayName}</name>

    <dependencies>
        <dependency>
            <groupId>io.jaiclaw</groupId>
            <artifactId>jaiclaw-core</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.shell</groupId>
            <artifactId>spring-shell-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
            <scope>provided</scope>
        </dependency>
        <!-- Test deps -->
        <dependency>
            <groupId>org.apache.groovy</groupId>
            <artifactId>groovy</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.spockframework</groupId>
            <artifactId>spock-core</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.codehaus.gmavenplus</groupId>
                <artifactId>gmavenplus-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

After generating, also:
- Add `<module>jaiclaw-cli-{name}</module>` to root `pom.xml`
- Add BOM entry to `jaiclaw-bom/pom.xml`

**Mode B — Sub-module in another project:**

- Read the target project's root POM to find parent groupId, version, and conventions
- Generate POM with that project's parent
- Add `jaiclaw-bom` as BOM import:

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.jaiclaw</groupId>
            <artifactId>jaiclaw-bom</artifactId>
            <version>0.1.0-SNAPSHOT</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

- Add `<module>` to that project's root POM

**Mode C — Standalone project:**

- Generate full Spring Boot project with `spring-boot-starter-parent`
- Include an `Application.java` main class
- Import `jaiclaw-bom` for JaiClaw dependencies
- Generate Maven wrapper files: `mvnw`, `mvnw.cmd`, `.mvn/wrapper/`

**Mode D — JBang script:**

- Generate a single `{Name}Cli.java` JBang script (self-contained, no Maven project needed)
- Uses `//DEPS` directives for Spring Boot, Spring Shell, and JaiClaw BOM
- Runs with `jbang {Name}Cli.java` or `jbang {Name}Cli.java {prefix} list-users`
- Ideal for quick prototyping or lightweight CLIs that don't need a full Maven project

JBang script template:

```java
///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 21
//DEPS org.springframework.boot:spring-boot-starter:3.5.6
//DEPS org.springframework.shell:spring-shell-starter:3.4.0
//DEPS org.springframework.boot:spring-boot-starter-web:3.5.6
//DEPS io.jaiclaw:jaiclaw-core:0.1.0-SNAPSHOT

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
// ... rest of classes in single file

@SpringBootApplication
public class {Name}Cli {
    public static void main(String[] args) {
        SpringApplication.run({Name}Cli.class, args);
    }
    // Embed ApiClient, Commands, and Tool inner classes here
}
```

---

## Post-Generation

After generating all files:

1. **Compile** — run `./mvnw compile -pl {module} -am` (Mode A) or `./mvnw compile` (Mode B/C) or `jbang build {Name}Cli.java` (Mode D)
2. **Verify** — confirm no compile errors
3. **Report** — show the user:
   - Files created
   - Environment variables to set
   - How to run (based on mode)
   - Example commands (interactive and non-interactive)
   - Available JaiClaw tools: `{prefix}_list_users`, `{prefix}_get_order`

**Usage examples to show:**

```bash
# ═══ Interactive (REPL) ═══
./mvnw spring-boot:run              # Mode A/B/C
jbang {Name}Cli.java                # Mode D
# Drops into shell> prompt, type commands:
#   {prefix} list-users
#   {prefix} get-user 123

# ═══ Non-interactive (single command, pipe-friendly) ═══
java -jar target/{artifact}.jar {prefix} list-users
java -jar target/{artifact}.jar {prefix} get-user 123
jbang {Name}Cli.java {prefix} list-users              # Mode D

# ═══ Piping with jq ═══
java -jar target/{artifact}.jar {prefix} list-users | jq '.[].name'
java -jar target/{artifact}.jar {prefix} get-user 123 | jq '.email'
jbang {Name}Cli.java {prefix} list-users | jq 'length'

# ═══ Scripting ═══
for id in $(java -jar target/{artifact}.jar {prefix} list-users | jq -r '.[].id'); do
  java -jar target/{artifact}.jar {prefix} get-user "$id" | jq '{id: .id, name: .name}'
done
```

---

## OpenAPI Spec Parsing Guide

When parsing an OpenAPI 3.x spec (JSON or YAML):

- **Base URL**: `servers[0].url`
- **Endpoints**: `paths` object — each key is a path, values contain methods (get/post/put/delete)
- **Parameters**: `parameters` array on path or operation level (path, query, header)
- **Request body**: `requestBody.content['application/json'].schema`
- **Auth**: `components.securitySchemes` — look for:
  - `type: apiKey` → header auth (check `in` and `name` fields)
  - `type: http, scheme: bearer` → Bearer token
  - `type: http, scheme: basic` → Basic auth
  - `type: oauth2` → check `flows.clientCredentials.tokenUrl` for client credentials
- **Tags**: `tags` on each operation — use for grouping commands
- **Operation ID**: `operationId` — use for method/command naming

For Swagger 2.x specs:
- **Base URL**: `host` + `basePath`
- **Auth**: `securityDefinitions`
- Everything else similar but slightly different structure

---

## Naming Conventions

| Source | CLI Command | Tool Name | Java Method |
|--------|-------------|-----------|-------------|
| `GET /v1/users` | `{prefix} list-users` | `{prefix}_list_users` | `listUsers()` |
| `GET /v1/users/{id}` | `{prefix} get-user` | `{prefix}_get_user` | `getUser(id)` |
| `POST /v1/users` | `{prefix} create-user` | `{prefix}_create_user` | `createUser(body)` |
| `PUT /v1/users/{id}` | `{prefix} update-user` | `{prefix}_update_user` | `updateUser(id, body)` |
| `DELETE /v1/users/{id}` | `{prefix} delete-user` | `{prefix}_delete_user` | `deleteUser(id)` |

Derive from `operationId` when available. Fall back to `{method}{Resource}` pattern.

---

## Safety Rules

- Always confirm destructive operations (DELETE endpoints) with the user before generating
- Never hardcode credentials in generated code — always use environment variables
- Mark DELETE/PUT/POST tools with clear descriptions noting they are mutating
- Add `@ShellMethodAvailability` guards for destructive commands when feasible
