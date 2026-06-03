# JaiClaw 0.6.0 Release Notes

**Release Date**: 2026-06-02

## Highlights

- **4 new channel adapters** (WhatsApp, Google Chat, LINE, Matrix) — bringing the total to 11 messaging platforms
- **10 new extension modules** including observability (Micrometer/OTLP), web search, model catalog, memory wiki, task management, video generation, and more
- **Security hardening fixes** — `mode=none` NPE resolved, Embabel auto-configuration made fully opt-in, stale workarounds removed

## New Modules

### Channels

| Module | Artifact ID | Description |
|--------|-------------|-------------|
| `channels/jaiclaw-channel-whatsapp` | `jaiclaw-channel-whatsapp` | WhatsApp Business API adapter with webhook verification and message conversion |
| `channels/jaiclaw-channel-googlechat` | `jaiclaw-channel-googlechat` | Google Chat adapter with Space events and card message support |
| `channels/jaiclaw-channel-line` | `jaiclaw-channel-line` | LINE Messaging API adapter with webhook signature verification |
| `channels/jaiclaw-channel-matrix` | `jaiclaw-channel-matrix` | Matrix protocol adapter with room-based messaging and E2EE support |

### Extensions

| Module | Artifact ID | Description |
|--------|-------------|-------------|
| `extensions/jaiclaw-observability` | `jaiclaw-observability` | Micrometer metrics and OpenTelemetry tracing for agent runtime |
| `extensions/jaiclaw-web-search` | `jaiclaw-web-search` | Web search tool integration (SearXNG, Brave, Tavily) |
| `extensions/jaiclaw-model-catalog` | `jaiclaw-model-catalog` | Runtime model discovery and capability introspection |
| `extensions/jaiclaw-memory-wiki` | `jaiclaw-memory-wiki` | Persistent wiki-style knowledge base with vector search |
| `extensions/jaiclaw-tasks` | `jaiclaw-tasks` | Task/todo management with persistence and agent tool exposure |
| `extensions/jaiclaw-video-generation` | `jaiclaw-video-generation` | Video generation SPI with provider abstraction |

### Starters

| Module | Artifact ID | Description |
|--------|-------------|-------------|
| `jaiclaw-starters/jaiclaw-starter-whatsapp` | `jaiclaw-starter-whatsapp` | Spring Boot starter for WhatsApp channel |
| `jaiclaw-starters/jaiclaw-starter-web-search` | `jaiclaw-starter-web-search` | Spring Boot starter for web search extension |
| `jaiclaw-starters/jaiclaw-starter-model-catalog` | `jaiclaw-starter-model-catalog` | Spring Boot starter for model catalog |
| `jaiclaw-starters/jaiclaw-starter-wiki` | `jaiclaw-starter-wiki` | Spring Boot starter for memory wiki |
| `jaiclaw-starters/jaiclaw-starter-tasks` | `jaiclaw-starter-tasks` | Spring Boot starter for task management |
| `jaiclaw-starters/jaiclaw-starter-observability` | `jaiclaw-starter-observability` | Spring Boot starter for observability |
| `jaiclaw-starters/jaiclaw-starter-secrets` | `jaiclaw-starter-secrets` | Spring Boot starter for secrets management |

### Core Enhancements

- **Message chunking** (`MessageChunker`, `PlatformLimits`) in `jaiclaw-channel-api` — automatic message splitting for platform-specific length limits
- **Thread ownership tracking** (`ThreadOwnershipTracker`, `MentionDetector`) in `jaiclaw-agent` — multi-agent thread routing with @mention detection
- **Explicit tool loop** (`ExplicitToolLoop`) in `jaiclaw-agent` — structured tool execution with configurable loop limits
- **Per-tenant channel config** (`TenantChannelsConfig`, expanded `ChannelsProperties`) in `jaiclaw-config`
- **Voice and video config records** (`VoiceProperties`, `VideoProperties`) in `jaiclaw-config`

## Breaking Changes

- **Embabel is now fully opt-in**: `embabel-agent-starter` dependency in `jaiclaw-spring-boot-starter` is `<optional>true</optional>`. Projects using Embabel must explicitly add `jaiclaw-starter-embabel`. Non-Embabel projects no longer need `AgentPlatformAutoConfiguration` exclusions.
- **Security `mode=none` behavior change**: Previously required excluding `JaiClawSecurityAutoConfiguration` as a workaround. Now correctly activates `NoneSecurityConfiguration` with a `permitAll()` filter chain (more secure than no filter chain at all).

## Dependency Updates

| Dependency | Previous | New |
|-----------|----------|-----|
| `spring-boot.version` | 3.5.14 | 3.5.14 (unchanged) |
| `spring-ai.version` | 1.1.7 | 1.1.7 (unchanged) |
| `embabel-agent.version` | 0.3.5 | 0.3.5 (unchanged) |
| `spring-shell.version` | 3.4.2 | 3.4.2 (unchanged) |

## Bug Fixes

- **Security mode=none NPE** — `SecurityModeLogger.logApiKeyMode()` now guards against null `ApiKeyProvider`; `JaiClawSecurityProperties` compact constructor defaults only when `mode == null` (not `isBlank()`), preserving explicit `mode=none` binding
- **YAML multi-exclude orphan** — Removed stale `JaiClawSecurityAutoConfiguration` exclusion from 3 camel example YAML files that was left over from the Embabel opt-in cleanup
- **Embabel opt-in cleanup** — Removed `AgentPlatformAutoConfiguration` exclusions from all example/app YAML files now that the dependency is optional

## Security Fixes

- Fixed `SecurityModeLogger` null dereference that could crash applications configured with `jaiclaw.security.mode=none`
- Camel examples now run with proper `NoneSecurityConfiguration` filter chain instead of bypassing security entirely via auto-configuration exclusion
