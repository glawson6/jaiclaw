package io.jaiclaw.tools.builtin;

import io.jaiclaw.core.tool.ToolContext;
import io.jaiclaw.core.tool.ToolDefinition;
import io.jaiclaw.core.tool.ToolResult;
import io.jaiclaw.tools.exec.SafeProcessEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Executes shell commands with a configurable prefix allowlist for safety.
 *
 * <p>Every command must begin with one of the allowed prefixes defined in the
 * {@link WhitelistedCommandConfig}. Commands outside the allowlist are rejected
 * immediately. Before execution, the binary (first token of the matching prefix)
 * is checked for availability on PATH via {@code which}.
 *
 * <p>Output is capped at {@code maxOutputLines} and execution is capped at
 * {@code timeoutSeconds} to prevent runaway processes.
 *
 * <p>This tool is generic and reusable — different applications can configure it
 * with different tool names, sections, and prefix lists. For example,
 * taptech-monitors uses it as {@code local_command} with monitoring prefixes.
 */
public class WhitelistedCommandTool extends AbstractBuiltinTool {

    private static final Logger log = LoggerFactory.getLogger(WhitelistedCommandTool.class);

    private static final String INPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "command": {
                  "type": "string",
                  "description": "The shell command to execute. Must start with an allowed prefix."
                },
                "timeout": {
                  "type": "integer",
                  "description": "Timeout in seconds (default: configured value)"
                }
              },
              "required": ["command"]
            }""";

    /** Shell metacharacters that enable injection: ; | && || $( ` > < & */
    private static final Pattern SHELL_METACHAR_PATTERN = Pattern.compile(
            "[;|&`<>]|\\$\\("
    );

    private final WhitelistedCommandConfig config;

    public WhitelistedCommandTool(WhitelistedCommandConfig config) {
        super(new ToolDefinition(
                config.toolName(),
                "Execute a whitelisted shell command. "
                        + "Commands must start with an allowed prefix. "
                        + "Returns stdout/stderr output.",
                config.section(),
                INPUT_SCHEMA,
                config.profiles()
        ));
        this.config = config;
    }

    @Override
    protected ToolResult doExecute(Map<String, Object> parameters, ToolContext context) throws Exception {
        String command = requireParam(parameters, "command").trim();
        int timeout = parameters.containsKey("timeout")
                ? Math.min(((Number) parameters.get("timeout")).intValue(), config.timeoutSeconds())
                : config.timeoutSeconds();

        // Safety: verify command starts with an allowed prefix
        if (!isCommandAllowed(command)) {
            log.warn("Blocked disallowed command: {}", command);
            return new ToolResult.Error(
                    "Command not allowed. Must start with one of: "
                            + config.allowedPrefixes().stream()
                            .collect(Collectors.joining(", ")));
        }

        // Safety: block shell metacharacters to prevent piping/chaining after the prefix
        if (containsShellMetachars(command)) {
            log.warn("Blocked command with shell metacharacters: {}", command);
            return new ToolResult.Error(
                    "Command contains blocked shell metacharacters. "
                            + "Piping, chaining, and redirection are not allowed.");
        }

        // Check if the binary exists on PATH
        String binary = extractBinary(command);
        if (binary != null && !isBinaryAvailable(binary)) {
            log.warn("Binary not found on PATH: {}", binary);
            return new ToolResult.Error(
                    "Command '" + binary + "' is not installed or not on PATH. "
                            + "Install it before using this command.");
        }

        log.info("Executing whitelisted command: {}", command);

        ProcessBuilder pb = new ProcessBuilder("sh", "-c", command)
                .redirectErrorStream(true);
        SafeProcessEnvironment.apply(pb);

        Process process = pb.start();
        StringBuilder output = new StringBuilder();
        int lineCount = 0;

        try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (lineCount < config.maxOutputLines()) {
                    output.append(line).append('\n');
                }
                lineCount++;
            }
        }

        boolean completed = process.waitFor(timeout, TimeUnit.SECONDS);
        if (!completed) {
            process.destroyForcibly();
            return new ToolResult.Error("Command timed out after " + timeout + " seconds");
        }

        int exitCode = process.exitValue();
        String result = output.toString();

        if (lineCount > config.maxOutputLines()) {
            result += "\n... (" + (lineCount - config.maxOutputLines()) + " more lines truncated)";
        }

        if (exitCode != 0) {
            return new ToolResult.Success(
                    "Exit code " + exitCode + ":\n" + result,
                    Map.of("exitCode", exitCode));
        }
        return new ToolResult.Success(result, Map.of("exitCode", exitCode));
    }

    /**
     * Check if a command starts with any of the allowed prefixes.
     * Uses token-based matching to prevent prefix bypass (e.g., "git clone-and-exfil").
     */
    boolean isCommandAllowed(String command) {
        String trimmed = command.trim();
        return config.allowedPrefixes().stream()
                .anyMatch(prefix -> matchesPrefix(trimmed, prefix));
    }

    /**
     * Token-boundary-aware prefix matching. For single-token prefixes (e.g., "git"),
     * requires a word boundary after the prefix to prevent "git" matching "git-exfil".
     * For multi-token prefixes (e.g., "cat /proc/"), uses standard startsWith since
     * the path prefix acts as a directory constraint.
     */
    private static boolean matchesPrefix(String command, String prefix) {
        if (!command.startsWith(prefix)) {
            return false;
        }
        // Multi-token prefixes (containing spaces) use startsWith — path constraints
        if (prefix.contains(" ")) {
            return true;
        }
        // Single-token prefixes require a word boundary
        return command.length() == prefix.length()
                || Character.isWhitespace(command.charAt(prefix.length()));
    }

    /**
     * Extract the binary name from a command by finding which allowed prefix matches
     * and returning its first token.
     */
    String extractBinary(String command) {
        String trimmed = command.trim();
        for (String prefix : config.allowedPrefixes()) {
            if (trimmed.startsWith(prefix)) {
                // First token of the prefix is the binary
                String[] tokens = prefix.trim().split("\\s+");
                return tokens[0];
            }
        }
        return null;
    }

    /**
     * Check if a command contains shell metacharacters that could enable injection.
     */
    boolean containsShellMetachars(String command) {
        return SHELL_METACHAR_PATTERN.matcher(command).find();
    }

    /**
     * Check if a binary is available on PATH using {@code which}.
     */
    boolean isBinaryAvailable(String binary) {
        try {
            Process process = new ProcessBuilder("which", binary)
                    .redirectErrorStream(true)
                    .start();
            boolean completed = process.waitFor(5, TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (Exception e) {
            log.debug("Failed to check binary availability for '{}': {}", binary, e.getMessage());
            return false;
        }
    }
}
