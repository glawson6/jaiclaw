# YAML Multi-Entry Exclusion Bug: Orphaned List Items After Embabel Opt-In Cleanup

## Problem

When making `embabel-agent-starter` optional in `jaiclaw-spring-boot-starter` (the Embabel opt-in fix), the bulk removal of `AgentPlatformAutoConfiguration` from `spring.autoconfigure.exclude` lists broke 3 example YAML files that had **multiple entries** in the exclusion list.

The removal regex targeted only the `AgentPlatformAutoConfiguration` entry along with the `autoconfigure:` and `exclude:` keys, but files with a second exclusion entry (`JaiClawSecurityAutoConfiguration`) were left with an orphaned YAML list item directly under `spring:`, producing invalid YAML:

```yaml
# BROKEN — orphaned list item under spring:
spring:
      - io.jaiclaw.security.JaiClawSecurityAutoConfiguration
  ai:
    model:
      chat: ${AI_PROVIDER:anthropic}
```

This caused `jaiclaw-maven-plugin:analyze` to fail with:

```
expected <block end>, but found '<block mapping start>'
 in 'reader', line 56, column 3:
     ai:
     ^
```

## Affected Files

| File | Additional Exclusion |
|------|---------------------|
| `jaiclaw-examples/camel-html-summarizer-telegram/src/main/resources/application.yml` | `JaiClawSecurityAutoConfiguration` |
| `jaiclaw-examples/camel-pdf-filler-telegram/src/main/resources/application.yml` | `JaiClawSecurityAutoConfiguration` |
| `jaiclaw-examples/camel-pdf-filler/src/main/resources/application.yml` | `JaiClawSecurityAutoConfiguration` |

## Root Cause

The bulk removal used a regex that matched the exact 3-line pattern:

```
  autoconfigure:
    exclude:
      - com.embabel.agent.autoconfigure.platform.AgentPlatformAutoConfiguration
```

This assumed every `exclude` list contained only the single `AgentPlatformAutoConfiguration` entry. The 3 camel examples also excluded `JaiClawSecurityAutoConfiguration` (as a workaround for the security mode=none NPE bug documented in `JAICLAW-SECURITY-BUG.md`), making the list 4 lines instead of 3. After removal, the `autoconfigure:` and `exclude:` keys were deleted but the second list entry remained.

## Fix Applied

Restored the `autoconfigure:` / `exclude:` structure for the remaining `JaiClawSecurityAutoConfiguration` entry:

```yaml
# FIXED — proper YAML structure preserved
spring:
  autoconfigure:
    exclude:
      - io.jaiclaw.security.JaiClawSecurityAutoConfiguration
  ai:
    model:
      chat: ${AI_PROVIDER:anthropic}
```

## Related

- `JAICLAW-SECURITY-BUG.md` — the security mode=none NPE bug that necessitated the `JaiClawSecurityAutoConfiguration` exclusion in these 3 camel examples. Once that bug is fixed, the exclusion can be removed entirely.

## Resolution

**Resolved.** The `JaiClawSecurityAutoConfiguration` exclusion that was restored as an emergency YAML fix has been removed entirely from all 3 affected files. The underlying security mode=none NPE bug (documented in `JAICLAW-SECURITY-BUG.md`) has been fixed in the codebase:

- `SecurityModeLogger.logApiKeyMode()` now has a null guard for `apiKeyProvider`
- `JaiClawSecurityProperties` compact constructor defaults only when `mode == null`, not `isBlank()`

With these fixes applied, `mode=none` works correctly without any auto-configuration exclusions. The 3 camel examples now use the intended `NoneSecurityConfiguration` (which sets up a `SecurityFilterChain` with `permitAll()`) instead of bypassing security entirely.

## Prevention

When performing bulk YAML modifications across the project:

1. Parse YAML structurally (e.g., via SnakeYAML or a YAML-aware tool) rather than line-based regex
2. After bulk edits, run `./mvnw compile -o` to validate all YAML files via the `jaiclaw-maven-plugin:analyze` goal
3. Check for multi-entry lists before assuming a fixed line count for removal
