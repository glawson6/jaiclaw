# JaiClaw Security Bug: NPE in SecurityModeLogger when mode=none

## Problem

When `jaiclaw.security.mode` is set to `none`, the application fails to start with a `NullPointerException` in `SecurityModeLogger`:

```
java.lang.NullPointerException: Cannot invoke "io.jaiclaw.security.ApiKeyProvider.getMaskedKey()"
  because "this.apiKeyProvider" is null
    at io.jaiclaw.security.SecurityModeLogger.logApiKeyMode(SecurityModeLogger.java:36)
    at io.jaiclaw.security.SecurityModeLogger.afterSingletonsInstantiated(SecurityModeLogger.java:27)
```

## Root Cause

There are two interacting issues in `jaiclaw-security`:

### 1. `JaiClawSecurityProperties` compact constructor defaults `mode` to `"api-key"`

In `JaiClawSecurityProperties.java` (lines 33-41), the compact constructor overrides a user-supplied `mode` of `none` under certain conditions:

```java
public JaiClawSecurityProperties {
    if (mode == null || mode.isBlank()) {
        if (enabled) {
            mode = "jwt";
        } else {
            mode = "api-key";  // <-- overrides before Spring binds the property
        }
    }
}
```

When Spring Boot binds the record from YAML/env properties, the compact constructor runs. If the `mode` field arrives as `null` during initial construction (before the actual value is bound), the constructor silently replaces it with `"api-key"`. This causes the `ApiKeySecurityConfiguration` inner class to activate (due to `matchIfMissing = true`) instead of `NoneSecurityConfiguration`.

### 2. `SecurityModeLogger` does not guard against null `ApiKeyProvider`

In `SecurityModeLogger.java` (lines 34-37):

```java
private void logApiKeyMode() {
    log.info("Security mode: api-key - API key: {} (source: {})",
            apiKeyProvider.getMaskedKey(), apiKeyProvider.getSource());
}
```

When mode resolves to `"api-key"` but `NoneSecurityConfiguration` was intended (and `ApiKeySecurityConfiguration` didn't create an `ApiKeyProvider` bean), the `apiKeyProvider` field is null, causing the NPE.

The `SecurityModeLogger` bean is created with `ObjectProvider<ApiKeyProvider>.getIfAvailable()` which correctly returns null when no bean exists, but `logApiKeyMode()` unconditionally dereferences it.

## Reproduction

```yaml
jaiclaw:
  security:
    mode: none
```

Or via environment variable:

```
JAICLAW_SECURITY_MODE=none
```

Start the application. It crashes with the NPE above.

## Workaround

Exclude both `JaiClawSecurityAutoConfiguration` and `SecurityAutoConfiguration`:

```yaml
spring:
  autoconfigure:
    exclude:
      - io.jaiclaw.security.JaiClawSecurityAutoConfiguration
      - org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration
```

This disables all JaiClaw security and Spring Security entirely. All endpoints become unauthenticated, which is acceptable for local development but not for production.

## Suggested Fixes

### Fix 1: Null guard in `SecurityModeLogger.logApiKeyMode()` (minimal)

```java
private void logApiKeyMode() {
    if (apiKeyProvider == null) {
        log.warn("Security mode: api-key - but no ApiKeyProvider bean found");
        return;
    }
    log.info("Security mode: api-key - API key: {} (source: {})",
            apiKeyProvider.getMaskedKey(), apiKeyProvider.getSource());
}
```

### Fix 2: Fix compact constructor to not override explicit `mode` values (root cause)

The compact constructor should only set the default when the mode was genuinely not provided by the user, not when it arrives as an empty string from Spring property binding:

```java
public JaiClawSecurityProperties {
    if (mode == null) {
        mode = enabled ? "jwt" : "api-key";
    }
    // ... rest unchanged
}
```

Note: removing the `mode.isBlank()` check preserves the user's intent when they explicitly set `mode` to a value via YAML or env var.

### Fix 3: Remove `matchIfMissing = true` from `ApiKeySecurityConfiguration` (defensive)

```java
@ConditionalOnProperty(name = "jaiclaw.security.mode", havingValue = "api-key")
static class ApiKeySecurityConfiguration {
```

Without `matchIfMissing = true`, the `api-key` configuration only activates when explicitly requested. Combined with the compact constructor default, this would still work for the default case but would prevent the conflict when `mode=none` is set.

## Affected Versions

- `jaiclaw-security` (observed in JaiClaw 0.6.0-SNAPSHOT)
- `JaiClawSecurityAutoConfiguration.java`
- `SecurityModeLogger.java`
- `JaiClawSecurityProperties.java`

## Resolution

**Resolved.** All three suggested fixes have been applied:

1. **Fix 1 (null guard)**: `SecurityModeLogger.logApiKeyMode()` now checks `apiKeyProvider == null` before dereferencing, logging a warning instead of throwing NPE.
2. **Fix 2 (compact constructor)**: `JaiClawSecurityProperties` compact constructor now defaults only when `mode == null`, preserving explicit values like `"none"` that arrive as non-null strings from Spring property binding.
3. **Workaround removed**: The `JaiClawSecurityAutoConfiguration` exclusion that was applied as a workaround in 3 camel examples (`camel-html-summarizer-telegram`, `camel-pdf-filler-telegram`, `camel-pdf-filler`) has been removed. These examples now correctly use `mode=none` with the `NoneSecurityConfiguration` filter chain.

## Affected Files

| File | Location |
|------|----------|
| `SecurityModeLogger.java` | `jaiclaw/core/jaiclaw-security/src/main/java/io/jaiclaw/security/` |
| `JaiClawSecurityAutoConfiguration.java` | `jaiclaw/core/jaiclaw-security/src/main/java/io/jaiclaw/security/` |
| `JaiClawSecurityProperties.java` | `jaiclaw/core/jaiclaw-security/src/main/java/io/jaiclaw/security/` |
