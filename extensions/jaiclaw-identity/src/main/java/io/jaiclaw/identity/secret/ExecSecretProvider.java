package io.jaiclaw.identity.secret;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Resolves secrets by spawning an external command.
 * <p>
 * Protocol: writes JSON to stdin ({@code { protocolVersion: 1, provider, ids: [...] }}),
 * reads JSON from stdout ({@code { protocolVersion: 1, values: { id: value }, errors: { id: { message } } }}).
 */
public class ExecSecretProvider {

    private static final Logger log = LoggerFactory.getLogger(ExecSecretProvider.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Resolve a secret by executing an external command.
     *
     * @param id     the secret ID to resolve
     * @param config provider config with command and args
     * @return the secret value
     */
    public String resolve(String id, SecretProviderConfig config) {
        if (config.command() == null || config.command().isBlank()) {
            throw new SecretRefResolver.SecretResolutionException(
                    "Exec secret provider has no command configured");
        }

        validateCommand(config);

        List<String> command = new ArrayList<>();
        command.add(config.command());
        if (config.args() != null) {
            command.addAll(config.args());
        }

        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            // No shell interpretation
            pb.redirectErrorStream(false);

            // Sanitize environment — clear inherited vars, keep only safe + configured
            Map<String, String> currentEnv = System.getenv();
            pb.environment().clear();
            for (String key : List.of("PATH", "HOME", "LANG", "TERM", "USER", "SHELL", "TMPDIR")) {
                String value = currentEnv.get(key);
                if (value != null) {
                    pb.environment().put(key, value);
                }
            }
            // Add explicitly configured extra env vars
            if (config.env() != null) {
                pb.environment().putAll(config.env());
            }

            Process process = pb.start();

            // Write request to stdin
            String request = MAPPER.writeValueAsString(Map.of(
                    "protocolVersion", 1,
                    "provider", config.name() != null ? config.name() : "default",
                    "ids", List.of(id)
            ));
            try (OutputStream stdin = process.getOutputStream()) {
                stdin.write(request.getBytes(StandardCharsets.UTF_8));
                stdin.flush();
            }

            long timeoutMs = config.timeoutMs() > 0 ? config.timeoutMs() : SecretProviderConfig.DEFAULT_TIMEOUT_MS;
            boolean finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
            if (!finished) {
                // Kill the entire process tree — descendants first, then the parent
                destroyProcessTree(process);
                throw new SecretRefResolver.SecretResolutionException(
                        "Exec secret provider timed out after " + timeoutMs + "ms: " + config.command());
            }

            byte[] stdout = process.getInputStream().readAllBytes();
            if (stdout.length > config.maxBytes()) {
                throw new SecretRefResolver.SecretResolutionException(
                        "Exec output exceeds max size: " + stdout.length);
            }

            if (process.exitValue() != 0) {
                String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
                throw new SecretRefResolver.SecretResolutionException(
                        "Exec secret provider exited with code " + process.exitValue()
                                + ": " + stderr.trim());
            }

            String output = new String(stdout, StandardCharsets.UTF_8).trim();

            // Try JSON protocol response
            if (config.jsonOnly() || output.startsWith("{")) {
                JsonNode root = MAPPER.readTree(output);
                JsonNode values = root.get("values");
                if (values != null && values.has(id)) {
                    JsonNode value = values.get(id);
                    return value.isTextual() ? value.asText() : value.toString();
                }
                JsonNode errors = root.get("errors");
                if (errors != null && errors.has(id)) {
                    String errorMsg = errors.get(id).has("message")
                            ? errors.get(id).get("message").asText()
                            : "unknown error";
                    throw new SecretRefResolver.SecretResolutionException(
                            "Exec provider error for '" + id + "': " + errorMsg);
                }
                throw new SecretRefResolver.SecretResolutionException(
                        "Exec provider returned no value for '" + id + "'");
            }

            // Raw string output (non-JSON)
            return output;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new SecretRefResolver.SecretResolutionException(
                    "Exec secret provider failed: " + config.command(), e);
        }
    }

    private void validateCommand(SecretProviderConfig config) {
        Path commandPath = Path.of(config.command());
        if (!commandPath.isAbsolute()) {
            throw new SecretRefResolver.SecretResolutionException(
                    "Exec command must be an absolute path: " + config.command());
        }
    }

    /**
     * Kill the entire process tree — all descendants first, then the parent.
     * Prevents orphaned child processes from surviving the timeout.
     */
    private static void destroyProcessTree(Process process) {
        ProcessHandle handle = process.toHandle();
        // Kill descendants in reverse order (deepest first)
        handle.descendants().forEach(descendant -> {
            try {
                descendant.destroyForcibly();
            } catch (Exception e) {
                // Best effort — descendant may have already exited
            }
        });
        process.destroyForcibly();
    }
}
