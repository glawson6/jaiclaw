package io.jaiclaw.tools.builtin;

import io.jaiclaw.core.tool.ToolContext;
import io.jaiclaw.core.tool.ToolDefinition;
import io.jaiclaw.core.tool.ToolProfile;
import io.jaiclaw.core.tool.ToolResult;
import io.jaiclaw.tools.ToolCatalog;
import io.jaiclaw.tools.exec.CommandPolicy;
import io.jaiclaw.tools.exec.ExecPolicyConfig;
import io.jaiclaw.tools.exec.SafeProcessEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Executes a shell command in the workspace directory and returns its output.
 */
public class ShellExecTool extends AbstractBuiltinTool {

    private static final Logger log = LoggerFactory.getLogger(ShellExecTool.class);

    private static final long DEFAULT_TIMEOUT_SECONDS = 120;

    private static final String INPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "command": {
                  "type": "string",
                  "description": "The shell command to execute"
                },
                "timeout": {
                  "type": "integer",
                  "description": "Timeout in seconds (default 120)"
                }
              },
              "required": ["command"]
            }""";

    private final ExecPolicyConfig policyConfig;

    public ShellExecTool() {
        this(ExecPolicyConfig.DEFAULT);
    }

    public ShellExecTool(ExecPolicyConfig policyConfig) {
        super(new ToolDefinition(
                "shell_exec",
                "Execute a shell command and return its stdout/stderr output.",
                ToolCatalog.SECTION_EXEC,
                INPUT_SCHEMA,
                Set.of(ToolProfile.CODING, ToolProfile.FULL)
        ));
        this.policyConfig = policyConfig;
    }

    @Override
    protected ToolResult doExecute(Map<String, Object> parameters, ToolContext context) throws Exception {
        String command = requireParam(parameters, "command");

        // Validate command against policy
        Optional<String> violation = CommandPolicy.validate(command, policyConfig);
        if (violation.isPresent()) {
            log.warn("Command blocked by policy: {}", violation.get());
            return new ToolResult.Error("Command blocked: " + violation.get());
        }

        long timeout = parameters.containsKey("timeout")
                ? ((Number) parameters.get("timeout")).longValue()
                : DEFAULT_TIMEOUT_SECONDS;

        // Cap timeout at configured maximum
        timeout = Math.min(timeout, policyConfig.maxTimeout());

        log.info("Executing shell command: {}", command);

        ProcessBuilder pb = new ProcessBuilder("sh", "-c", command)
                .directory(Path.of(context.workspaceDir()).toFile())
                .redirectErrorStream(true);
        SafeProcessEnvironment.apply(pb);

        Process process = pb.start();
        StringBuilder output = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append('\n');
            }
        }

        boolean completed = process.waitFor(timeout, TimeUnit.SECONDS);
        if (!completed) {
            process.destroyForcibly();
            return new ToolResult.Error("Command timed out after " + timeout + " seconds");
        }

        int exitCode = process.exitValue();
        String result = output.toString();

        if (exitCode != 0) {
            return new ToolResult.Error("Exit code " + exitCode + ":\n" + result);
        }
        return new ToolResult.Success(result, Map.of("exitCode", exitCode));
    }
}
