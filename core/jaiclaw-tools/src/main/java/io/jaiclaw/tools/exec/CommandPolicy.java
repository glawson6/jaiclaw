package io.jaiclaw.tools.exec;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Pure Java command validator for shell and kubectl execution policies.
 * No Spring dependency — usable from any context.
 */
public final class CommandPolicy {

    private CommandPolicy() {}

    /** Pattern matching shell metacharacters used for injection: ; | && || $( ` > < & */
    private static final Pattern SHELL_METACHAR_PATTERN = Pattern.compile(
            "[;|&`<>]|\\$\\("
    );

    /**
     * Validate a shell command against the given exec policy config.
     *
     * @return empty if allowed, or an error message if blocked
     */
    public static Optional<String> validate(String command, ExecPolicyConfig config) {
        if (command == null || command.isBlank()) {
            return Optional.of("Command must not be empty");
        }

        String trimmed = command.trim();

        // Blocked patterns are always checked (even in unrestricted mode)
        Optional<String> blockedMatch = checkBlockedPatterns(trimmed, config.blockedPatterns());
        if (blockedMatch.isPresent()) {
            return blockedMatch;
        }

        return switch (config.policy()) {
            case ExecPolicyConfig.POLICY_UNRESTRICTED -> Optional.empty();
            case ExecPolicyConfig.POLICY_ALLOWLIST -> validateAllowlist(trimmed, config.allowedCommands());
            case ExecPolicyConfig.POLICY_DENY_DANGEROUS -> validateDenyDangerous(trimmed);
            default -> Optional.of("Unknown exec policy: " + config.policy());
        };
    }

    /**
     * Validate a kubectl command against the given kubectl policy config.
     *
     * @return empty if allowed, or an error message if blocked
     */
    public static Optional<String> validateKubectl(String command, KubectlPolicyConfig config) {
        if (command == null || command.isBlank()) {
            return Optional.of("Command must not be empty");
        }

        String trimmed = command.trim();

        // Must start with "kubectl" as its first token
        String firstToken = extractFirstToken(trimmed);
        if (!"kubectl".equals(firstToken)) {
            return Optional.of("Command must start with 'kubectl'. Got: " + command);
        }

        // Extract the kubectl verb (second token)
        String verb = extractKubectlVerb(trimmed);
        if (verb == null) {
            return Optional.of("No kubectl verb specified");
        }

        // Check blocked verbs (always applied)
        if (config.blockedVerbs().contains(verb)) {
            return Optional.of("Kubectl verb '" + verb + "' is blocked by policy");
        }

        return switch (config.policy()) {
            case KubectlPolicyConfig.POLICY_UNRESTRICTED -> Optional.empty();
            case KubectlPolicyConfig.POLICY_READ_ONLY -> validateKubectlReadOnly(verb);
            case KubectlPolicyConfig.POLICY_ALLOWLIST -> validateKubectlAllowlist(verb, config.allowedVerbs());
            default -> Optional.of("Unknown kubectl policy: " + config.policy());
        };
    }

    /**
     * Extract the first whitespace-delimited token from a command string.
     * Used for token-based allowlist matching (fixes prefix bypass vulnerability).
     */
    public static String extractFirstToken(String command) {
        String trimmed = command.trim();
        int spaceIdx = trimmed.indexOf(' ');
        return spaceIdx == -1 ? trimmed : trimmed.substring(0, spaceIdx);
    }

    // --- Private helpers ---

    private static Optional<String> checkBlockedPatterns(String command, List<String> blockedPatterns) {
        if (blockedPatterns == null || blockedPatterns.isEmpty()) {
            return Optional.empty();
        }
        for (String pattern : blockedPatterns) {
            if (command.contains(pattern)) {
                return Optional.of("Command blocked by pattern: " + pattern);
            }
        }
        return Optional.empty();
    }

    private static Optional<String> validateAllowlist(String command, List<String> allowedCommands) {
        String firstToken = extractFirstToken(command);
        if (allowedCommands.contains(firstToken)) {
            return Optional.empty();
        }
        return Optional.of("Command '" + firstToken + "' is not in the allowed list. "
                + "Allowed: " + String.join(", ", allowedCommands));
    }

    private static Optional<String> validateDenyDangerous(String command) {
        if (SHELL_METACHAR_PATTERN.matcher(command).find()) {
            return Optional.of("Command contains blocked shell metacharacters. "
                    + "Piping, chaining, and redirection are not allowed in deny-dangerous mode.");
        }
        return Optional.empty();
    }

    private static String extractKubectlVerb(String command) {
        String[] tokens = command.trim().split("\\s+");
        if (tokens.length < 2) {
            return null;
        }
        // Skip flags before verb (e.g., "kubectl --context=foo get pods")
        // Handles: --flag=value, --flag value, -f value, -f=value
        boolean skipNext = false;
        for (int i = 1; i < tokens.length; i++) {
            if (skipNext) {
                skipNext = false;
                continue;
            }
            if (tokens[i].startsWith("--")) {
                // Long flag: skip next token only if no '=' (e.g., "--namespace foo")
                if (!tokens[i].contains("=")) {
                    skipNext = true;
                }
            } else if (tokens[i].startsWith("-")) {
                // Short flag: "-n kube-system" — skip next token as the flag value
                if (tokens[i].length() <= 2) {
                    skipNext = true;
                }
                // Flags like "-nkube-system" (value attached) — no skip needed
            } else {
                return tokens[i];
            }
        }
        return null;
    }

    private static Optional<String> validateKubectlReadOnly(String verb) {
        if (KubectlPolicyConfig.READ_ONLY_VERBS.contains(verb)) {
            return Optional.empty();
        }
        return Optional.of("Kubectl verb '" + verb + "' is not allowed in read-only mode. "
                + "Allowed: " + String.join(", ", KubectlPolicyConfig.READ_ONLY_VERBS));
    }

    private static Optional<String> validateKubectlAllowlist(String verb, List<String> allowedVerbs) {
        if (allowedVerbs.contains(verb)) {
            return Optional.empty();
        }
        return Optional.of("Kubectl verb '" + verb + "' is not in the allowed list. "
                + "Allowed: " + String.join(", ", allowedVerbs));
    }
}
