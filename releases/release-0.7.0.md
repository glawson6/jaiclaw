# JaiClaw 0.7.0 Release Notes

**Release Date**: 2026-06-06

## Highlights

- **Composite tool profiles** — define named tool profile compositions in YAML that combine multiple profiles with include/exclude overrides, enabling fine-grained per-agent tool access control
- **Dependency CVE remediation** — upgraded Tomcat, Netty, Log4j, Apache Camel, and Spring Cloud to patch 122 known CVEs identified via OWASP dependency-check scan
- **OWASP dependency-check plugin** — added to `pluginManagement` for automated CVE scanning against the NVD database

## New Features

### Composite Tool Profiles

New `CompositeToolProfile` record in `jaiclaw-core` and `CompositeToolProfileRegistry` in `jaiclaw-config` allow defining named tool profile compositions via YAML configuration:

```yaml
jaiclaw:
  tools:
    composite-profiles:
      analyst:
        profiles: [FULL, WEB]
        exclude: [shell-exec, code-run]
      readonly:
        profiles: [MINIMAL]
        include: [web-fetch]
```

- `ToolRegistry.resolveToolsForProfile()` now supports composite profile names alongside built-in `ToolProfile` enum values
- `AgentRuntime` and `TenantAgentRuntimeFactory` propagate composite profiles through the agent lifecycle
- `JaiClawAutoConfiguration` wires `CompositeToolProfileRegistry` from `ToolsProperties`
- Full Spock test coverage for registry, tool resolution, and agent runtime integration

## Breaking Changes

None.

## Dependency Updates

| Dependency | Previous | New | CVEs Addressed |
|-----------|----------|-----|----------------|
| `spring-cloud.version` | 2025.0.1 | 2025.0.2 | Spring Cloud Gateway CVEs |
| `tomcat.version` | 10.1.39 (BOM) | 10.1.55 | CVE-2025-31650, CVE-2025-31651 |
| `netty.version` | 4.1.126.Final (BOM) | 4.1.135.Final | CVE-2025-24970, CVE-2025-25193 |
| `log4j2.version` | 2.24.3 (BOM) | 2.26.0 | CVE-2024-12798 |
| `camel.version` | 4.18.1 | 4.18.2 | Multiple Apache Camel CVEs |

## Bug Fixes

None.

## Security Fixes

- **122 dependency CVEs remediated** via version overrides for Tomcat, Netty, Log4j, Apache Camel, and Spring Cloud
- **OWASP dependency-check plugin** (`12.1.0`) added to `pluginManagement` for automated CVE scanning — run with `./mvnw dependency-check:check`
- **45 false positives identified** — mxparser/XStream CVEs (XStream 1.4.20 is latest; conflated by NVD matching) and channel-name collisions (WhatsApp/Signal/Telegram client app CVEs matched against JaiClaw adapter modules)

## Infrastructure

- Cleaned up stale security reports (`security-report-2026-05-13-orig.md`, `security-report-2026-05-14.md`)
