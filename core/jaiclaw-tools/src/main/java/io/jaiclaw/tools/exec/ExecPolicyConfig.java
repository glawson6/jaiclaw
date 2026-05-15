package io.jaiclaw.tools.exec;

import java.util.List;

/**
 * Configuration for the shell execution policy. Pure Java — no Spring dependency.
 *
 * @param policy          "unrestricted" (default), "allowlist", or "deny-dangerous"
 * @param allowedCommands commands allowed when policy is "allowlist" (matched by first token)
 * @param blockedPatterns substrings always blocked (even in unrestricted mode)
 * @param maxTimeout      ceiling in seconds for command execution (default 300)
 */
public record ExecPolicyConfig(
        String policy,
        List<String> allowedCommands,
        List<String> blockedPatterns,
        int maxTimeout
) {
    public ExecPolicyConfig {
        if (allowedCommands == null) allowedCommands = List.of();
        if (blockedPatterns == null) blockedPatterns = List.of();
    }
    public static final String POLICY_UNRESTRICTED = "unrestricted";
    public static final String POLICY_ALLOWLIST = "allowlist";
    public static final String POLICY_DENY_DANGEROUS = "deny-dangerous";

    public static final List<String> DEFAULT_BLOCKED_PATTERNS = List.of(
            "rm -rf /", "mkfs", "> /dev/sd"
    );

    public static final ExecPolicyConfig DEFAULT = new ExecPolicyConfig(
            POLICY_DENY_DANGEROUS, List.of(), DEFAULT_BLOCKED_PATTERNS, 300
    );
}
