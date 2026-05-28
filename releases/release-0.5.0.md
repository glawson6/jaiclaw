# JaiClaw 0.5.0 Release Notes

**Release Date**: 2026-05-28

## Highlights

- **Spring AI 1.1.7** upgrade (from 1.1.4), resolving CVE-2026-41863 and bringing updated model support across all Spring AI providers
- **New `jaiclaw-email` extension** with SMTP2Go-backed email sending, MCP tool provider, and `jaiclaw-starter-email` Spring Boot starter
- **New `jaiclaw-rules` extension** (Drools integration) for rule-based decision-making with classpath, filesystem, and URL rule loader strategies

## New Modules

| Module | Artifact ID | Description |
|--------|-------------|-------------|
| `extensions/jaiclaw-email` | `jaiclaw-email` | Email sending extension: `EmailSender` SPI, `Smtp2goEmailSender`, `SendEmailTool`, `EmailMcpToolProvider` |
| `jaiclaw-starters/jaiclaw-starter-email` | `jaiclaw-starter-email` | Spring Boot starter for `jaiclaw-email` |
| `extensions/jaiclaw-rules` | `jaiclaw-rules` | Drools-based rule engine: `RuleExecutionService`, `DroolsConfig`, classpath/filesystem/URL rule loaders, `SECTION_RULES` tool catalog entry |
| `jaiclaw-starters/jaiclaw-starter-rules` | `jaiclaw-starter-rules` | Spring Boot starter for `jaiclaw-rules` |

## New Examples

- `jaiclaw-examples/procurement-approval` — multi-step procurement workflow with approval chain
- `jaiclaw-examples/support-triage` — customer support ticket triage and routing
- `jaiclaw-examples/tax-advisor` — tax calculation and comparison tool
- `jaiclaw-examples/onboarding-intake` — employee onboarding intake form workflow
- `jaiclaw-examples/code-scaffolder` — YAML manifest-driven project code generation (see `jaiclaw-project-scaffolder` tool)

## Breaking Changes

None.

## Dependency Updates

| Dependency | Previous | New |
|-----------|----------|-----|
| `spring-ai.version` | 1.1.4 | 1.1.7 |
| `spring-boot.version` | 3.5.14 | 3.5.14 (unchanged) |
| `embabel-agent.version` | 0.3.5 | 0.3.5 (unchanged) |
| `spring-shell.version` | 3.4.2 | 3.4.2 (unchanged) |

## Bug Fixes

None beyond what Spring AI 1.1.7 provides.

## Security Fixes

- Spring AI 1.1.7 resolves CVE-2026-41863 (HTTP header injection via unescaped model parameters in Spring AI REST transport layer)
- `jaiclaw-rules`: path traversal protection in classpath rule loader (`..` references rejected)
- `jaiclaw-rules`: protocol allowlist in URL rule loader (only HTTP/HTTPS permitted, rejects `ftp://`, `file://`, etc.)
