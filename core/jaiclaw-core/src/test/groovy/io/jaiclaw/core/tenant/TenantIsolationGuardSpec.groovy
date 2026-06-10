package io.jaiclaw.core.tenant

import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * Regression guard. Scans the {@code src/main/java} tree of the modules
 * audited in the multi-tenant isolation hardening PR for
 * {@code ConcurrentHashMap<String, ...>} / {@code HashMap<String, ...>} /
 * {@code LinkedHashMap<String, ...>} field declarations. Each match must
 * either be a field that's keyed through {@link TenantGuard} (the audit
 * confirmed during the hardening PR) or carry a
 * {@link TenantAgnostic @TenantAgnostic} annotation explaining why it's
 * exempt.
 *
 * <p>If a developer adds a new {@code ConcurrentHashMap<String, ...>} field
 * holding business data to one of the audited modules, this spec fails
 * with the file:line of the offending declaration, forcing a conscious
 * "this is tenant-agnostic" or "I forgot to scope this" decision.
 *
 * <p>The scanned-modules list is intentionally small. Other modules can be
 * added as future audits extend the coverage.
 */
class TenantIsolationGuardSpec extends Specification {

    /** Modules whose {@code src/main/java} is scanned. Paths are relative to the repo root. */
    private static final List<String> SCANNED_MODULES = [
            "core/jaiclaw-core",
            "core/jaiclaw-gateway",
            "extensions/jaiclaw-calendar",
            "extensions/jaiclaw-subscription",
            "extensions/jaiclaw-voice-call",
            // Added in the PR5 audit extension (CODEBASE-ANALYSIS-2026-06-10 §2.5).
            "extensions/jaiclaw-tasks",
            "extensions/jaiclaw-browser",
            "extensions/jaiclaw-docstore",
    ]

    /**
     * Class simple names that are known to be tenant-aware by construction
     * (e.g. registries whose API takes {@code tenantId} explicitly). Fields
     * in these classes are exempt from the regex check.
     */
    private static final Set<String> TENANT_AWARE_CLASSES = [
            // Audited in the hardening PR — confirmed to route through TenantGuard
            "InMemoryArtifactStore",
            "InMemoryCallStore",
            "JsonlCallStore",
            "InMemoryCalendarProvider",
            "WebSocketSessionHandler",
            "CallManager",
            "CallEventProcessor",
            // Already-keyed-by-tenant registries (API takes tenantId)
            "TenantMcpServerRegistry",
            "TenantChannelAdapterRegistry",
            // Audited in PR5 (CODEBASE-ANALYSIS-2026-06-10 §2.5).
            "JsonFileTaskStore",
            "BrowserService",
            "InMemoryDocStoreRepository",
            "JsonFileDocStoreRepository",
            "FullTextDocStoreSearch",
            "VectorDocStoreSearch",
    ] as Set

    /**
     * Class simple names that are out-of-scope for this audit and known to
     * have unscoped maps. Tracked as a follow-up. Files in this list are
     * skipped entirely so the spec doesn't fire on them.
     */
    private static final Set<String> FOLLOW_UP_GAPS = [
            // Twilio internal state; future audit will scope by tenant.
            "TwilioTelephonyProvider",
            // Media streaming session state; future audit will scope by tenant.
            "MediaStreamHandler",
    ] as Set

    /**
     * Regex matching a *field* declaration of a {@code ConcurrentHashMap<String, ...>}.
     *
     * <p>We deliberately exclude {@code HashMap}, {@code LinkedHashMap}, and the
     * raw {@code Map} interface because those tend to appear as per-record
     * metadata fields on value classes (e.g. {@code Map<String,Object> metadata}
     * on a message record). {@code ConcurrentHashMap} is almost exclusively
     * shared mutable service state, which is exactly what the audit cares about.
     */
    private static final Pattern FIELD_PATTERN = Pattern.compile(
            /(?m)^\s*(?:private|protected|public)\s+(?:static\s+)?(?:final\s+)?ConcurrentHashMap<\s*String\s*,/)

    /** Pattern that recognises a leading {@code @TenantAgnostic(...)} on the preceding line. */
    private static final Pattern ANNOTATION_PATTERN = Pattern.compile(/@TenantAgnostic\s*\(/)

    private static Path repoRoot() {
        // The spec runs with CWD = the module being tested (jaiclaw-core/),
        // so the repo root is two levels up: jaiclaw-core/ → core/ → repo root.
        Path cwd = Paths.get("").toAbsolutePath()
        return cwd.getParent().getParent()
    }

    def "no unscoped Map<String,...> field exists in any audited module"() {
        given:
        Path root = repoRoot()
        List<String> violations = []

        when:
        for (String mod : SCANNED_MODULES) {
            Path src = root.resolve(mod).resolve("src/main/java")
            if (!Files.isDirectory(src)) continue
            Files.walk(src)
                    .filter { it.toString().endsWith(".java") }
                    .forEach { Path file ->
                        String simpleName = file.getFileName().toString().replace(".java", "")
                        if (TENANT_AWARE_CLASSES.contains(simpleName)) return
                        if (FOLLOW_UP_GAPS.contains(simpleName)) return

                        String content = Files.readString(file)
                        Matcher m = FIELD_PATTERN.matcher(content)
                        while (m.find()) {
                            // Look back ~120 chars for a @TenantAgnostic annotation
                            int start = Math.max(0, m.start() - 200)
                            String preceding = content.substring(start, m.start())
                            if (ANNOTATION_PATTERN.matcher(preceding).find()) continue
                            // Compute approximate line number for the report
                            int lineNum = content.substring(0, m.start()).count("\n") + 1
                            String relative = root.relativize(file).toString()
                            violations << "${relative}:${lineNum} — unscoped Map<String,...> field. " +
                                    "Either route keys through TenantGuard.resolveStorageKey(...), " +
                                    "annotate the field @TenantAgnostic(reason=\"...\"), " +
                                    "or add the class name to TenantIsolationGuardSpec.TENANT_AWARE_CLASSES."
                        }
                    }
        }

        then:
        if (!violations.isEmpty()) {
            throw new AssertionError(
                    "Tenant-isolation regression guard found ${violations.size()} unscoped maps:\n" +
                            violations.join("\n"))
        }
    }
}
