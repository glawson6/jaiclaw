---
name: jclaw-developer
description: Comprehensive guide for building JClaw applications — project setup, tools, plugins, channels, skills, and testing
alwaysInclude: false
requiredBins: [mvn]
platforms: [darwin, linux]
version: 1.0.0
tenantIds: []
---

# JClaw Developer Guide

You are an expert at building applications with JClaw — a Java 21 / Spring Boot 3.5 / Spring AI framework for embeddable AI agents with multi-channel messaging, tool execution, plugins, skills, and MCP server hosting.

## 1. Architecture Overview

JClaw is layered bottom-up:

| Layer | Modules | Purpose |
|-------|---------|---------|
| 0 - Core | `jclaw-core` | Pure Java records, sealed interfaces, enums — zero Spring dependency |
| 1 - Channel SPI | `jclaw-channel-api` | `ChannelAdapter` SPI, `ChannelMessage`, attachments |
| 2 - Tools | `jclaw-tools` | `ToolRegistry`, built-in tools, Spring AI bridge |
| 3 - Features | `jclaw-agent`, `jclaw-skills`, `jclaw-plugin-sdk`, `jclaw-memory`, `jclaw-security`, `jclaw-documents`, `jclaw-media`, `jclaw-audit`, `jclaw-compaction`, `jclaw-browser`, `jclaw-cron`, `jclaw-voice`, `jclaw-identity`, `jclaw-canvas`, `jclaw-code` | Agent runtime, session management, plugins, memory, security, media, scheduling |
| 4 - Gateway | `jclaw-gateway`, channel adapters (Telegram, Slack, Discord, Email, SMS) | REST/WebSocket API, webhook dispatch, MCP hosting |
| 5 - Auto-Config | `jclaw-spring-boot-starter` | 3-phase auto-configuration |
| 6 - Starters | `jclaw-starter-anthropic`, `jclaw-starter-openai`, `jclaw-starter-gateway`, etc. | Dependency aggregation |
| 7 - Apps | `jclaw-gateway-app`, `jclaw-shell` | Runnable Spring Boot applications |

Key design decisions:
- **Java records everywhere** for immutable value types
- **Sealed interfaces** for `ToolResult` (Success/Error), `DeliveryResult` (Success/Failure)
- **Dual tool bridge**: JClaw `ToolCallback` SPI ↔ Spring AI `ToolCallback` via `SpringAiToolBridge`
- **Tool profiles**: MINIMAL (read-only), CODING (file ops), MESSAGING (channels), FULL (all)
- **Session key**: `{agentId}:{channel}:{accountId}:{peerId}`

## 2. Quick Start

### Minimal pom.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.5.6</version>
    </parent>

    <groupId>com.example</groupId>
    <artifactId>my-jclaw-app</artifactId>
    <version>0.1.0-SNAPSHOT</version>

    <dependencies>
        <dependency>
            <groupId>io.jclaw</groupId>
            <artifactId>jclaw-spring-boot-starter</artifactId>
            <version>0.1.0-SNAPSHOT</version>
        </dependency>
        <dependency>
            <groupId>io.jclaw</groupId>
            <artifactId>jclaw-gateway</artifactId>
            <version>0.1.0-SNAPSHOT</version>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-starter-model-anthropic</artifactId>
        </dependency>
    </dependencies>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.ai</groupId>
                <artifactId>spring-ai-bom</artifactId>
                <version>1.1.1</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

### Application Class

```java
package com.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class MyJClawApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyJClawApplication.class, args);
    }
}
```

### application.yml

```yaml
server:
  port: 8080

jclaw:
  identity:
    name: My Assistant
    description: An AI assistant built with JClaw
  agent:
    default-agent: default
    agents:
      default:
        id: default
        name: Default Agent
        tools:
          profile: full    # MINIMAL | CODING | MESSAGING | FULL

spring:
  ai:
    anthropic:
      enabled: true
      api-key: ${ANTHROPIC_API_KEY}
      chat:
        options:
          model: claude-sonnet-4-5    # Always override — Spring AI default is retired
```

Run with: `mvn spring-boot:run`

## 3. Creating Custom Tools

Tools are the primary way to give the LLM executable capabilities. Implement `io.jclaw.core.tool.ToolCallback`:

```java
package com.example.tools;

import io.jclaw.core.tool.ToolCallback;
import io.jclaw.core.tool.ToolContext;
import io.jclaw.core.tool.ToolDefinition;
import io.jclaw.core.tool.ToolResult;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class SearchFaqTool implements ToolCallback {

    @Override
    public ToolDefinition definition() {
        return new ToolDefinition(
                "search_faq",                              // name (snake_case)
                "Search the FAQ knowledge base",           // description for LLM
                "helpdesk",                                // section (groups tools in prompt)
                """
                {
                  "type": "object",
                  "properties": {
                    "question": { "type": "string", "description": "The user's question" },
                    "category": { "type": "string", "description": "FAQ category" }
                  },
                  "required": ["question"]
                }
                """
        );
    }

    @Override
    public ToolResult execute(Map<String, Object> parameters, ToolContext context) {
        String question = (String) parameters.get("question");
        String category = (String) parameters.getOrDefault("category", "general");

        // Your implementation here — query a database, call an API, etc.
        String result = queryFaqDatabase(question, category);

        return new ToolResult.Success(result);
        // Or on failure: return new ToolResult.Error("FAQ service unavailable");
    }
}
```

### Core Tool Types

| Type | Class | Purpose |
|------|-------|---------|
| `ToolCallback` | Interface | SPI — implement `definition()` and `execute()` |
| `ToolDefinition` | Record | Name, description, section, JSON Schema, profiles |
| `ToolResult` | Sealed interface | `Success(content)` or `Error(message)` |
| `ToolContext` | Record | Runtime context: agentId, sessionKey, sessionId, workspaceDir |
| `ToolProfile` | Enum | MINIMAL, CODING, MESSAGING, FULL |

### Tool Profiles

Control tool visibility per agent:

```java
import io.jclaw.core.tool.ToolProfile;
import java.util.Set;

// Tool available in CODING and FULL profiles
new ToolDefinition("file_edit", "Edit a file", "files", schema,
        Set.of(ToolProfile.CODING, ToolProfile.FULL));

// Default (no profiles arg) = FULL only
new ToolDefinition("danger_tool", "Risky operation", "admin", schema);
```

### Registration

Tools annotated with `@Component` are auto-discovered by Spring. They are automatically registered in the `ToolRegistry` and bridged to Spring AI via `SpringAiToolBridge`.

## 4. Creating Plugins

Plugins bundle multiple tools and can hook into lifecycle events:

```java
package com.example.plugins;

import io.jclaw.core.plugin.PluginDefinition;
import io.jclaw.core.plugin.PluginKind;
import io.jclaw.plugin.JClawPlugin;
import io.jclaw.plugin.PluginApi;
import org.springframework.stereotype.Component;

@Component
public class CodeReviewPlugin implements JClawPlugin {

    @Override
    public PluginDefinition definition() {
        return new PluginDefinition(
                "code-review",          // id
                "Code Review Plugin",   // name
                "PR diff and review",   // description
                "1.0.0",                // version
                PluginKind.GENERAL      // kind: GENERAL | MEMORY | CONTEXT_ENGINE | CHANNEL | PROVIDER
        );
    }

    @Override
    public void register(PluginApi api) {
        api.registerTool(new GetDiffTool());       // Register tools
        api.registerTool(new PostCommentTool());
        // api.on(hookName, handler);              // Register lifecycle hooks
    }
}
```

Plugin discovery works via:
1. Spring `@Component` scanning (recommended)
2. `ServiceLoader` (`META-INF/services/io.jclaw.plugin.JClawPlugin`)
3. Explicit `PluginRegistry.register()` calls

## 5. Creating Skills

Skills are markdown files with YAML frontmatter that provide behavioral guidance to the LLM.

### Skill File Format

Create `SKILL.md` in a named directory:

```
my-app/
└── .jclaw/skills/
    └── my-domain/
        └── SKILL.md
```

```markdown
---
name: my-domain-skill
description: Domain expertise for X
alwaysInclude: false
requiredBins: [git]
platforms: [darwin, linux, windows]
version: 1.0.0
tenantIds: []
---

# My Domain Skill

Instructions for the LLM about how to handle domain-specific tasks...

## Tool Usage
- Use **my_tool** for...
- Use **other_tool** when...
```

### Frontmatter Fields

| Field | Type | Default | Purpose |
|-------|------|---------|---------|
| `name` | string | derived from directory name | Skill identifier |
| `description` | string | "" | One-line description |
| `alwaysInclude` | boolean | false | Include in every system prompt |
| `requiredBins` | string[] | [] | Required CLI tools (checked via `which`) |
| `platforms` | string[] | [] | Supported OSes: darwin, linux, windows |
| `version` | string | "1.0.0" | Semantic version |
| `tenantIds` | string[] | [] | Empty = all tenants; populated = restrict to listed tenants |

### Loading

- **Bundled skills**: Place in `src/main/resources/skills/{name}/SKILL.md` — loaded from classpath
- **Workspace skills**: Place in `.jclaw/skills/{name}/SKILL.md` — loaded from configured `jclaw.skills.workspace-dir`
- **Eligibility**: Skills are filtered at load time by platform and binary availability

## 6. Channel Adapters

JClaw supports 5 messaging channels out of the box:

| Channel | Module | Inbound | Outbound |
|---------|--------|---------|----------|
| Telegram | `jclaw-channel-telegram` | Bot API polling + webhook | Bot API sendMessage |
| Slack | `jclaw-channel-slack` | Socket Mode + Events API | Web API |
| Discord | `jclaw-channel-discord` | Gateway WebSocket + Interactions | REST API |
| Email | `jclaw-channel-email` | IMAP polling | SMTP |
| SMS | `jclaw-channel-sms` | Twilio webhook | Twilio REST API |

### Channel Configuration

```yaml
jclaw:
  channels:
    telegram:
      enabled: true
      bot-token: ${TELEGRAM_BOT_TOKEN}
    slack:
      enabled: true
      bot-token: ${SLACK_BOT_TOKEN}
      app-token: ${SLACK_APP_TOKEN}
    discord:
      enabled: true
      bot-token: ${DISCORD_BOT_TOKEN}
    email:
      enabled: true
      imap-host: imap.gmail.com
      smtp-host: smtp.gmail.com
      username: ${EMAIL_USERNAME}
      password: ${EMAIL_PASSWORD}
    sms:
      enabled: true
      account-sid: ${TWILIO_ACCOUNT_SID}
      auth-token: ${TWILIO_AUTH_TOKEN}
      from-number: ${TWILIO_FROM_NUMBER}
```

### Custom Channel Adapter

Implement `io.jclaw.channel.ChannelAdapter`:

```java
public interface ChannelAdapter {
    String channelId();              // e.g., "my-channel"
    String displayName();            // e.g., "My Channel"
    void start(ChannelMessageHandler handler);   // Begin receiving messages
    DeliveryResult sendMessage(ChannelMessage message);  // Send outbound
    default void stop() {}
    default boolean supportsStreaming() { return false; }
}
```

## 7. Configuration Reference

```yaml
jclaw:
  identity:
    name: "Assistant Name"
    description: "What this assistant does"

  agent:
    default-agent: default
    agents:
      default:
        id: default
        name: Agent Name
        tools:
          profile: full          # MINIMAL | CODING | MESSAGING | FULL

  skills:
    allow-bundled: ["*"]         # Glob patterns for allowed bundled skills
    workspace-dir: null          # Path to workspace skills directory

  security:
    mode: api-key                # api-key | jwt | none
    api-key: ${JCLAW_API_KEY:}
    jwt:
      secret: ${JWT_SECRET:}

  mcp-servers:                   # External MCP tool providers
    server-name:
      description: "Server description"
      type: http                 # http | stdio | sse
      url: "http://localhost:8090/mcp/server-name"
      enabled: true

  channels:                      # See Channel Adapters section
    telegram:
      enabled: false
    slack:
      enabled: false

spring:
  ai:
    anthropic:
      enabled: true
      api-key: ${ANTHROPIC_API_KEY}
      chat:
        options:
          model: claude-sonnet-4-5
    openai:
      enabled: false
      api-key: ${OPENAI_API_KEY:not-set}
    ollama:
      enabled: false
      base-url: http://localhost:11434
```

## 8. Starters & Dependencies

Use the appropriate starter for your deployment:

| Starter | Use Case | Includes |
|---------|----------|----------|
| `jclaw-starter-anthropic` | Anthropic Claude apps | starter + anthropic AI provider |
| `jclaw-starter-openai` | OpenAI apps | starter + openai AI provider |
| `jclaw-starter-ollama` | Local LLM apps | starter + ollama AI provider |
| `jclaw-starter-gateway` | Full gateway with all channels | starter + gateway + all 5 channel adapters |
| `jclaw-starter-shell` | CLI app | starter + Spring Shell |
| `jclaw-starter-embabel` | Embabel agent integration | starter + embabel agent |
| `jclaw-starter-personal-assistant` | Personal assistant preset | starter + common features |
| `jclaw-starter-k8s-monitor` | K8s monitoring preset | starter + k8s tools |

When building inside the JClaw mono-repo, use `<parent>jclaw-parent</parent>` and omit versions (managed by parent BOM).

## 9. Multi-Tenancy

JClaw supports per-tenant isolation:

- **TenantContext**: ThreadLocal via `TenantContextHolder`, carries `tenantId` + metadata
- **JWT auth**: `JwtTenantResolver` extracts tenant from JWT claims
- **API key auth**: `BotTokenTenantResolver` maps tokens to tenants
- **Session isolation**: Session keys include tenant context
- **Skill scoping**: Skills can be restricted to specific tenants via `tenantIds` in metadata
- **Tool context**: `ToolContext.contextData` carries tenant info to tools

## 10. MCP Server Hosting

JClaw can host MCP (Model Context Protocol) servers, exposing tools to external clients:

### McpToolProvider SPI

```java
public interface McpToolProvider {
    String serverName();
    List<McpToolDefinition> tools();
    McpToolResult executeTool(String toolName, Map<String, Object> args);
}
```

### MCP Transport Types

| Type | Config key | Use case |
|------|-----------|----------|
| HTTP | `type: http` | Streamable HTTP with JSON-RPC 2.0 |
| stdio | `type: stdio` | Subprocess communication |
| SSE | `type: sse` | Server-Sent Events |

### REST Endpoints

- `GET /mcp` — List all MCP servers
- `GET /mcp/{serverName}/tools` — List tools for a server
- `POST /mcp/{serverName}/tools/{toolName}` — Execute a tool

## 11. Cron Jobs

Schedule recurring agent tasks:

```java
import io.jclaw.core.model.CronJob;
import io.jclaw.cron.CronService;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MyCronConfig {

    @Bean
    ApplicationRunner registerJobs(CronService cronService) {
        return args -> {
            CronJob job = new CronJob(
                    "daily-report",             // id
                    "Daily Sales Report",       // name
                    "default",                  // agentId
                    "0 9 * * MON-FRI",          // cron schedule
                    "America/New_York",         // timezone
                    "Generate a sales report with key metrics",  // prompt
                    "telegram",                 // delivery channel
                    null,                       // delivery target (null = default)
                    true,                       // enabled
                    null,                       // lastRunAt
                    null                        // nextRunAt
            );
            cronService.addJob(job);
        };
    }
}
```

## 12. Testing

JClaw uses **Spock Framework** (Groovy) for all tests:

- Test files: `src/test/groovy/` with `*Spec.groovy` naming
- Dependencies needed: `groovy`, `spock-core` (test scope)
- For mocking concrete classes: add `byte-buddy` + `objenesis` (test scope)
- Build plugin: `gmavenplus-plugin` for Groovy compilation

```bash
# Run all tests
mvn test

# Run a single spec
mvn test -Dtest=MyToolSpec

# Run tests offline (skip Nexus)
mvn test -o
```

### Example Spec

```groovy
package com.example

import io.jclaw.core.tool.ToolContext
import io.jclaw.core.tool.ToolResult
import spock.lang.Specification

class SearchFaqToolSpec extends Specification {

    def tool = new SearchFaqTool()

    def "returns FAQ results for valid question"() {
        given:
        def params = [question: "How do I reset my password?"]
        def context = new ToolContext("default", "default:web:user1:peer1", "sess-1", "/tmp")

        when:
        def result = tool.execute(params, context)

        then:
        result instanceof ToolResult.Success
        ((ToolResult.Success) result).content().contains("password")
    }
}
```

## 13. MCP Resource References

The following resource URIs are designed for a future JClaw MCP resource server. When available, an LLM can fetch detailed documentation on demand:

| Resource URI | Description |
|-------------|-------------|
| `jclaw://docs/architecture` | Full architecture diagram with all layers |
| `jclaw://docs/modules` | Complete module dependency graph (39 modules) |
| `jclaw://docs/auto-config` | Auto-configuration ordering and bean wiring details |
| `jclaw://examples/tool` | Complete custom tool implementation with tests |
| `jclaw://examples/plugin` | Complete plugin implementation with tool registration |
| `jclaw://examples/skill` | Skill file template with all frontmatter fields |
| `jclaw://examples/channel` | Custom channel adapter implementation |
| `jclaw://examples/cron` | Cron job scheduling example |
| `jclaw://examples/app-scaffold` | Full app scaffold: pom.xml + Application + application.yml |
| `jclaw://examples/helpdesk-bot` | Multi-tenant helpdesk bot (FAQ + tickets) |
| `jclaw://examples/code-review-bot` | Code review plugin with diff analysis |
| `jclaw://examples/daily-briefing` | Cron-scheduled daily briefing with weather + news |
| `jclaw://schema/application-yml` | Full configuration schema reference |
| `jclaw://schema/tool-definition` | ToolDefinition JSON Schema specification |
| `jclaw://schema/skill-frontmatter` | Skill YAML frontmatter specification |

When the MCP resource server is not available, use the inline examples in this skill as your reference.
