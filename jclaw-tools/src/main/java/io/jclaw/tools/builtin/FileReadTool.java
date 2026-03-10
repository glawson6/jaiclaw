package io.jclaw.tools.builtin;

import io.jclaw.core.tool.ToolContext;
import io.jclaw.core.tool.ToolDefinition;
import io.jclaw.core.tool.ToolProfile;
import io.jclaw.core.tool.ToolResult;
import io.jclaw.tools.ToolCatalog;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

/**
 * Reads the contents of a file from the workspace.
 */
public class FileReadTool extends AbstractBuiltinTool {

    private static final String INPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "path": {
                  "type": "string",
                  "description": "Path to the file to read, relative to the workspace directory"
                },
                "offset": {
                  "type": "integer",
                  "description": "Line number to start reading from (0-based). Defaults to 0."
                },
                "limit": {
                  "type": "integer",
                  "description": "Maximum number of lines to read. Defaults to all lines."
                }
              },
              "required": ["path"]
            }""";

    public FileReadTool() {
        super(new ToolDefinition(
                "file_read",
                "Read the contents of a file. Returns the file content with line numbers.",
                ToolCatalog.SECTION_FILES,
                INPUT_SCHEMA,
                Set.of(ToolProfile.MINIMAL, ToolProfile.CODING, ToolProfile.FULL)
        ));
    }

    @Override
    protected ToolResult doExecute(Map<String, Object> parameters, ToolContext context) throws Exception {
        String filePath = requireParam(parameters, "path");
        Path resolved = Path.of(context.workspaceDir()).resolve(filePath).normalize();

        if (!Files.exists(resolved)) {
            return new ToolResult.Error("File not found: " + filePath);
        }
        if (!Files.isRegularFile(resolved)) {
            return new ToolResult.Error("Not a regular file: " + filePath);
        }

        var lines = Files.readAllLines(resolved);
        int offset = parameters.containsKey("offset")
                ? ((Number) parameters.get("offset")).intValue() : 0;
        int limit = parameters.containsKey("limit")
                ? ((Number) parameters.get("limit")).intValue() : lines.size();

        offset = Math.max(0, Math.min(offset, lines.size()));
        limit = Math.max(0, Math.min(limit, lines.size() - offset));

        var sb = new StringBuilder();
        for (int i = offset; i < offset + limit; i++) {
            sb.append(String.format("%6d\t%s%n", i + 1, lines.get(i)));
        }
        return new ToolResult.Success(sb.toString());
    }
}
