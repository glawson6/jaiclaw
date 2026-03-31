package io.jaiclaw.tools.builtin;

import io.jaiclaw.core.tool.ToolContext;
import io.jaiclaw.core.tool.ToolDefinition;
import io.jaiclaw.core.tool.ToolProfile;
import io.jaiclaw.core.tool.ToolResult;
import io.jaiclaw.tools.ToolCatalog;
import io.jaiclaw.tools.exec.SafeProcessEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Built-in tool that delegates sub-tasks to the Claude CLI in non-interactive mode.
 * Uses {@code claude -p} (print mode) with {@code --bare} and {@code --no-session-persistence}
 * by default. Supports streaming output via an optional {@link Consumer} in
 * {@link ToolContext#contextData()} under key {@link #STREAM_CONSUMER_KEY}.
 */
public class ClaudeCliTool extends AbstractBuiltinTool {

    private static final Logger log = LoggerFactory.getLogger(ClaudeCliTool.class);

    /** Key in {@link ToolContext#contextData()} for an optional {@code Consumer<String>} that receives streaming chunks. */
    public static final String STREAM_CONSUMER_KEY = "streamConsumer";

    private static final long DEFAULT_TIMEOUT_SECONDS = 300;
    private static final long MAX_TIMEOUT_SECONDS = 1800;
    private static final int MAX_OUTPUT_CHARS = 200_000;

    private static final String INPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "prompt": {
                  "type": "string",
                  "description": "The prompt to send to Claude CLI"
                },
                "model": {
                  "type": "string",
                  "description": "Model to use (e.g. claude-sonnet-4-20250514)"
                },
                "systemPrompt": {
                  "type": "string",
                  "description": "System prompt for the Claude session"
                },
                "maxTurns": {
                  "type": "integer",
                  "description": "Maximum number of agentic turns"
                },
                "maxBudget": {
                  "type": "number",
                  "description": "Maximum budget in USD"
                },
                "allowedTools": {
                  "type": "string",
                  "description": "Comma-separated list of allowed tools"
                },
                "disallowedTools": {
                  "type": "string",
                  "description": "Comma-separated list of disallowed tools"
                },
                "effort": {
                  "type": "string",
                  "enum": ["low", "medium", "high"],
                  "description": "Effort level for the response"
                },
                "bare": {
                  "type": "boolean",
                  "description": "Use --bare flag for plain output (default true)"
                },
                "stream": {
                  "type": "boolean",
                  "description": "Stream output chunks via context consumer (default false)"
                },
                "continueSession": {
                  "type": "string",
                  "description": "Session ID to resume (uses --resume, omits --no-session-persistence)"
                },
                "stdin": {
                  "type": "string",
                  "description": "Content to pipe to Claude's stdin"
                },
                "timeout": {
                  "type": "integer",
                  "description": "Timeout in seconds (default 300, max 1800)"
                },
                "outputFormat": {
                  "type": "string",
                  "enum": ["json", "stream-json", "text"],
                  "description": "Output format flag"
                }
              },
              "required": ["prompt"]
            }""";

    public ClaudeCliTool() {
        super(new ToolDefinition(
                "claude_cli",
                "Invoke Claude CLI in non-interactive mode to delegate complex sub-tasks. "
                        + "Runs 'claude -p' with the given prompt and returns the result. "
                        + "Supports model selection, system prompts, tool filtering, budget limits, and streaming.",
                ToolCatalog.SECTION_EXEC,
                INPUT_SCHEMA,
                Set.of(ToolProfile.FULL)
        ));
    }

    @Override
    protected ToolResult doExecute(Map<String, Object> parameters, ToolContext context) throws Exception {
        String prompt = requireParam(parameters, "prompt");

        List<String> command = buildCommand(parameters);
        log.info("Executing claude CLI: {}", String.join(" ", command));

        ProcessBuilder pb = new ProcessBuilder(command)
                .directory(Path.of(context.workspaceDir()).toFile())
                .redirectErrorStream(true);
        SafeProcessEnvironment.apply(pb);

        Process process = pb.start();

        // Pipe stdin if provided
        String stdin = optionalParam(parameters, "stdin", null);
        if (stdin != null) {
            try (OutputStream os = process.getOutputStream()) {
                os.write(stdin.getBytes(StandardCharsets.UTF_8));
                os.flush();
            }
        }

        long timeout = resolveTimeout(parameters);
        boolean streaming = Boolean.TRUE.equals(parameters.get("stream"));
        @SuppressWarnings("unchecked")
        Consumer<String> streamConsumer = streaming
                ? (Consumer<String>) context.contextData().get(STREAM_CONSUMER_KEY)
                : null;

        String output;
        if (streamConsumer != null) {
            output = readStreaming(process, streamConsumer);
        } else {
            output = readFull(process);
        }

        boolean completed = process.waitFor(timeout, TimeUnit.SECONDS);
        if (!completed) {
            process.destroyForcibly();
            return new ToolResult.Error("Claude CLI timed out after " + timeout + " seconds");
        }

        int exitCode = process.exitValue();

        // Truncate if too large
        if (output.length() > MAX_OUTPUT_CHARS) {
            output = output.substring(0, MAX_OUTPUT_CHARS)
                    + "\n\n[Output truncated at " + MAX_OUTPUT_CHARS + " characters]";
        }

        if (exitCode != 0) {
            return new ToolResult.Error("Claude CLI exited with code " + exitCode + ":\n" + output);
        }
        return new ToolResult.Success(output, Map.of("exitCode", exitCode));
    }

    /**
     * Builds the command-line arguments for the Claude CLI process.
     * Package-private for testability.
     */
    List<String> buildCommand(Map<String, Object> parameters) {
        String prompt = parameters.get("prompt").toString();
        List<String> cmd = new ArrayList<>();
        cmd.add("claude");
        cmd.add("-p");

        // --bare (default true)
        boolean bare = !Boolean.FALSE.equals(parameters.get("bare"));
        if (bare) {
            cmd.add("--bare");
        }

        // Session persistence
        String continueSession = optionalParam(parameters, "continueSession", null);
        if (continueSession != null) {
            cmd.add("--resume");
            cmd.add(continueSession);
        } else {
            cmd.add("--no-session-persistence");
        }

        // Model
        String model = optionalParam(parameters, "model", null);
        if (model != null) {
            cmd.add("--model");
            cmd.add(model);
        }

        // System prompt
        String systemPrompt = optionalParam(parameters, "systemPrompt", null);
        if (systemPrompt != null) {
            cmd.add("--system-prompt");
            cmd.add(systemPrompt);
        }

        // Max turns
        if (parameters.containsKey("maxTurns")) {
            cmd.add("--max-turns");
            cmd.add(parameters.get("maxTurns").toString());
        }

        // Max budget
        if (parameters.containsKey("maxBudget")) {
            cmd.add("--max-budget-usd");
            cmd.add(parameters.get("maxBudget").toString());
        }

        // Allowed tools (CSV)
        String allowedTools = optionalParam(parameters, "allowedTools", null);
        if (allowedTools != null) {
            for (String tool : allowedTools.split(",")) {
                String trimmed = tool.trim();
                if (!trimmed.isEmpty()) {
                    cmd.add("--allowedTools");
                    cmd.add(trimmed);
                }
            }
        }

        // Disallowed tools (CSV)
        String disallowedTools = optionalParam(parameters, "disallowedTools", null);
        if (disallowedTools != null) {
            for (String tool : disallowedTools.split(",")) {
                String trimmed = tool.trim();
                if (!trimmed.isEmpty()) {
                    cmd.add("--disallowedTools");
                    cmd.add(trimmed);
                }
            }
        }

        // Effort
        String effort = optionalParam(parameters, "effort", null);
        if (effort != null) {
            cmd.add("--effort");
            cmd.add(effort);
        }

        // Output format
        String outputFormat = optionalParam(parameters, "outputFormat", null);
        if (outputFormat != null) {
            cmd.add("--output-format");
            cmd.add(outputFormat);
        }

        // Prompt is always the last argument
        cmd.add(prompt);
        return cmd;
    }

    private String readFull(Process process) throws Exception {
        var sb = new StringBuilder();
        try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
        }
        return sb.toString();
    }

    private String readStreaming(Process process, Consumer<String> consumer) throws Exception {
        var sb = new StringBuilder();
        try (var reader = new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)) {
            char[] buf = new char[1024];
            int read;
            while ((read = reader.read(buf)) != -1) {
                String chunk = new String(buf, 0, read);
                sb.append(chunk);
                consumer.accept(chunk);
            }
        }
        return sb.toString();
    }

    private long resolveTimeout(Map<String, Object> parameters) {
        if (!parameters.containsKey("timeout")) {
            return DEFAULT_TIMEOUT_SECONDS;
        }
        long timeout = ((Number) parameters.get("timeout")).longValue();
        return Math.min(Math.max(timeout, 1), MAX_TIMEOUT_SECONDS);
    }
}
