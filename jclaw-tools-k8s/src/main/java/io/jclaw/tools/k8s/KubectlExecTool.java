package io.jclaw.tools.k8s;

import io.jclaw.core.tool.ToolContext;
import io.jclaw.core.tool.ToolDefinition;
import io.jclaw.core.tool.ToolProfile;
import io.jclaw.core.tool.ToolResult;
import io.jclaw.tools.ToolCatalog;
import io.jclaw.tools.builtin.AbstractBuiltinTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Executes any kubectl command as a subprocess. This is the Tier 2 fallback tool
 * for operations not covered by the purpose-built k8s_* tools.
 *
 * <p>The tool description instructs the LLM to prefer built-in k8s_* tools and warn
 * users before running mutating commands.
 */
public class KubectlExecTool extends AbstractBuiltinTool {

    private static final Logger log = LoggerFactory.getLogger(KubectlExecTool.class);

    private static final long DEFAULT_TIMEOUT_SECONDS = 120;

    private static final String INPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "command": {
                  "type": "string",
                  "description": "The full kubectl command to execute (e.g. 'kubectl get pods -A')"
                },
                "timeout": {
                  "type": "integer",
                  "description": "Timeout in seconds (default: 120)"
                }
              },
              "required": ["command"]
            }""";

    public KubectlExecTool() {
        super(new ToolDefinition(
                "kubectl_exec",
                "Execute any kubectl command. IMPORTANT: Prefer built-in k8s_* tools (k8s_list_pods, k8s_get_pod_logs, etc.) first — only use this for operations not covered by those tools. Before running mutating commands (delete, apply, patch, scale, rollout, drain, cordon, taint, edit), warn the user and request confirmation.",
                ToolCatalog.SECTION_KUBERNETES,
                INPUT_SCHEMA,
                Set.of(ToolProfile.FULL)
        ));
    }

    @Override
    protected ToolResult doExecute(Map<String, Object> parameters, ToolContext context) throws Exception {
        String command = requireParam(parameters, "command");
        long timeout = parameters.containsKey("timeout")
                ? ((Number) parameters.get("timeout")).longValue()
                : DEFAULT_TIMEOUT_SECONDS;

        // Ensure command starts with kubectl
        if (!command.trim().startsWith("kubectl")) {
            return new ToolResult.Error("Command must start with 'kubectl'. Got: " + command);
        }

        log.info("Executing kubectl command: {}", command);

        ProcessBuilder pb = new ProcessBuilder("sh", "-c", command)
                .redirectErrorStream(true);

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
            return new ToolResult.Error("kubectl command timed out after " + timeout + " seconds.");
        }

        int exitCode = process.exitValue();
        String result = output.toString();

        if (exitCode != 0) {
            return new ToolResult.Error("kubectl exit code " + exitCode + ":\n" + result);
        }
        return new ToolResult.Success(result, Map.of("exitCode", exitCode));
    }
}
