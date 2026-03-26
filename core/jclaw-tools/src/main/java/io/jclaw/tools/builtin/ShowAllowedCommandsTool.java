package io.jclaw.tools.builtin;

import io.jclaw.core.tool.ToolContext;
import io.jclaw.core.tool.ToolDefinition;
import io.jclaw.core.tool.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Companion tool for {@link WhitelistedCommandTool} that reports the configured
 * allowlist and binary availability status.
 *
 * <p>Skills reference this tool to discover at runtime which commands are available
 * on the current server, rather than hardcoding command lists in markdown.
 *
 * <p>For each allowed prefix, the tool checks whether the binary (first token)
 * exists on PATH via {@code which} and groups them into "Available" and
 * "Not Installed" sections.
 */
public class ShowAllowedCommandsTool extends AbstractBuiltinTool {

    private static final Logger log = LoggerFactory.getLogger(ShowAllowedCommandsTool.class);

    private static final String EMPTY_SCHEMA = """
            {"type":"object","properties":{},"required":[]}""";

    private final WhitelistedCommandConfig config;

    public ShowAllowedCommandsTool(WhitelistedCommandConfig config, String toolName) {
        super(new ToolDefinition(
                toolName != null && !toolName.isBlank() ? toolName : "show_allowed_commands",
                "Show the list of allowed command prefixes and their availability on this server.",
                config.section(),
                EMPTY_SCHEMA,
                config.profiles()
        ));
        this.config = config;
    }

    public ShowAllowedCommandsTool(WhitelistedCommandConfig config) {
        this(config, "show_allowed_commands");
    }

    @Override
    protected ToolResult doExecute(Map<String, Object> parameters, ToolContext context) throws Exception {
        List<String> available = new ArrayList<>();
        List<String> notInstalled = new ArrayList<>();

        for (String prefix : config.allowedPrefixes()) {
            String binary = prefix.trim().split("\\s+")[0];
            if (isBinaryAvailable(binary)) {
                available.add(prefix);
            } else {
                notInstalled.add(prefix + "  [NOT FOUND on PATH]");
            }
        }

        int total = config.allowedPrefixes().size();
        StringBuilder sb = new StringBuilder();
        sb.append("# Allowed Commands\n\n");

        sb.append("## Available (").append(available.size()).append("/").append(total).append(")\n");
        for (String cmd : available) {
            sb.append("- ").append(cmd).append("\n");
        }

        if (!notInstalled.isEmpty()) {
            sb.append("\n## Not Installed (").append(notInstalled.size()).append("/").append(total).append(")\n");
            for (String cmd : notInstalled) {
                sb.append("- ").append(cmd).append("\n");
            }
        }

        return new ToolResult.Success(sb.toString(), Map.of(
                "total", total,
                "available", available.size(),
                "notInstalled", notInstalled.size()
        ));
    }

    private boolean isBinaryAvailable(String binary) {
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
