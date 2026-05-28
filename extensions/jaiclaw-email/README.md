# JaiClaw Email Extension

## Problem

JaiClaw applications that need to send emails (notifications, reports, confirmations) from LLM agents have no library support. The existing `jaiclaw-channel-email` module is tightly coupled to the `ChannelAdapter` SPI for IMAP/SMTP channel messaging and cannot be used for standalone programmatic email sending.

## Solution

`jaiclaw-email` provides an SPI-based email sending extension with a pluggable provider model. It ships with an SMTP2GO implementation and exposes email sending as both an LLM tool (via `ToolCallback`) and an MCP tool (via `McpToolProvider`). Adding the dependency to any JaiClaw application gives agents the ability to compose and send emails.

## Architecture

```
EmailSender (SPI)
    │
    ├── Smtp2goEmailSender (SMTP2GO REST API implementation)
    │
    ├── SendEmailTool (AbstractBuiltinTool → ToolRegistry)
    │       └── LLM agents call email_send tool
    │
    └── EmailMcpToolProvider (McpToolProvider)
            └── External MCP clients call send_email tool

EmailMessage (record + builder) ──→ EmailResult (sealed: Sent | Failed)

JaiClawEmailAutoConfiguration
    ├── EmailProperties (bound from jaiclaw.email.*)
    ├── Smtp2goEmailSender bean
    ├── EmailToolsRegistrar (registers into ToolRegistry)
    └── EmailMcpToolProvider bean
```

**Key classes:**

| Class | Role |
|-------|------|
| `EmailMessage` | Immutable record with builder for email composition |
| `EmailResult` | Sealed interface with `Sent` and `Failed` subtypes |
| `EmailSender` | SPI interface for pluggable providers |
| `Smtp2goEmailSender` | SMTP2GO REST API implementation |
| `SendEmailTool` | LLM tool exposing `email_send` |
| `EmailTools` | Factory for tool creation and registration |
| `EmailMcpToolProvider` | MCP server exposing `send_email` |
| `EmailProperties` | Configuration record for `jaiclaw.email.*` |

## Design

- **SPI-based provider model** — `EmailSender` interface allows swapping SMTP2GO for AWS SES, SendGrid, or custom providers without changing tool/MCP code
- **Sealed result types** — `EmailResult.Sent` / `EmailResult.Failed` provide type-safe, pattern-matchable outcomes (richer than boolean returns)
- **From/fromName resolution** — message-level overrides take precedence over `EmailProperties` defaults, allowing per-message sender customization
- **No Spring annotations on core classes** — `EmailSender`, `Smtp2goEmailSender`, `SendEmailTool` are pure Java, wired via `@Configuration` for testability
- **Follows `jaiclaw-rules` pattern** — auto-configuration, tool registration, MCP provider, and Spock tests all follow the established extension module pattern

## Build & Run

### Prerequisites

- Java 21
- SMTP2GO API key (set as `SMTP2GO_API_KEY` environment variable)

### Build

```bash
# Compile with dependencies
./mvnw compile -pl :jaiclaw-email -am

# Run tests
./mvnw test -pl :jaiclaw-email

# Install (for downstream modules)
./mvnw install -pl :jaiclaw-starter-email -am -DskipTests
```

### Configuration

Add to your `application.yml`:

```yaml
jaiclaw:
  email:
    enabled: true
    provider: smtp2go
    default-from: noreply@example.com
    default-from-name: AI Assistant
    smtp2go:
      api-key: ${SMTP2GO_API_KEY}
```

### Verify

Once configured, the `email_send` tool appears in the agent's tool list. Test via the shell:

```
> Send an email to test@example.com with subject "Hello" and body "Test from JaiClaw"
```

The MCP server is available at `/mcp/email` with the `send_email` tool.
