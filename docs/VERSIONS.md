# Version History

This document tracks notable changes between JaiClaw releases.

## 0.2.0

Released 2026-05-04.

### New Features

- **OAuth Resource Owner Password Flow** — Added `ResourceOwnerPasswordFlow` in `jaiclaw-identity` supporting Keycloak (`KeycloakOAuthProvider`) and Spring Authorization Server (`SpringAuthServerOAuthProvider`). `OAuthFlowManager` updated to handle the new flow type.

- **MiniMax Thinking Filter** — New `MiniMaxThinkingFilterAutoConfiguration` in `jaiclaw-spring-boot-starter` wraps all `ChatModel` beans via `BeanPostProcessor` to strip MiniMax's always-on thinking content blocks from responses. Enabled by default (`jaiclaw.models.minimax.filter-thinking: true`). Disable with `jaiclaw.models.minimax.filter-thinking: false`.

### Dependency Upgrades

- Embabel Agent: 0.3.4 → 0.3.5
- Spring Shell: 3.4.0 → 3.4.2
- Maven GPG Plugin: 3.2.7 → 3.2.8

### Infrastructure

- Added Spock test support to `jaiclaw-spring-boot-starter` (groovy, spock-core, byte-buddy, gmavenplus-plugin)
- New test specs for OAuth ROPC flow (3 specs) and MiniMax Thinking Filter (1 spec)
- Documentation updates to `OPERATIONS.md` covering MiniMax Thinking Filter configuration

## 0.1.0

Released 2026-04-22. Initial release.

### Baseline

- **10 core modules** — jaiclaw-core, jaiclaw-channel-api, jaiclaw-config, jaiclaw-tools, jaiclaw-agent, jaiclaw-skills, jaiclaw-plugin-sdk, jaiclaw-memory, jaiclaw-security, jaiclaw-gateway
- **7 channel adapters** — Telegram, Slack, Discord, Email, SMS, Signal, Teams
- **25 extension modules** — documents, media, audit, compaction, browser, code, cron, voice, identity, canvas, docstore, subscription, tools-k8s, tools-security, calendar, messaging, discord-tools, slack-tools, voice-call, camel, embabel-delegate, docs, and more
- **4 standalone CLI tools** — perplexity, rest-cli-architect, skill-creator, prompt-analyzer
- **3 runnable apps** — gateway-app, shell, cron-manager-app
- **19 starters** — auto-configuration modules for all major features
- **25 examples** — demonstrating channels, tools, skills, MCP, Embabel integration, and more
- **BOM** (`jaiclaw-bom`) for version alignment
- **Maven plugin** (`jaiclaw-maven-plugin`) with `jaiclaw:analyze` goal for CI token budget enforcement
- **Multi-tenancy** — single/multi mode with JWT-based tenant resolution
- **MCP hosting** — tool and resource providers via REST, SSE, and stdio
- **Security hardening** — opt-in profile with SSRF protection, webhook verification, timing-safe comparisons
- **500+ Spock tests** across 28+ modules
