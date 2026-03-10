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
 * Writes content to a file in the workspace, creating parent directories as needed.
 */
public class FileWriteTool extends AbstractBuiltinTool {

    private static final String INPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "path": {
                  "type": "string",
                  "description": "Path to the file to write, relative to the workspace directory"
                },
                "content": {
                  "type": "string",
                  "description": "The content to write to the file"
                }
              },
              "required": ["path", "content"]
            }""";

    public FileWriteTool() {
        super(new ToolDefinition(
                "file_write",
                "Write content to a file. Creates the file and parent directories if they don't exist.",
                ToolCatalog.SECTION_FILES,
                INPUT_SCHEMA,
                Set.of(ToolProfile.CODING, ToolProfile.FULL)
        ));
    }

    @Override
    protected ToolResult doExecute(Map<String, Object> parameters, ToolContext context) throws Exception {
        String filePath = requireParam(parameters, "path");
        String content = requireParam(parameters, "content");

        Path resolved = Path.of(context.workspaceDir()).resolve(filePath).normalize();
        Files.createDirectories(resolved.getParent());
        Files.writeString(resolved, content);

        return new ToolResult.Success("Wrote " + content.length() + " characters to " + filePath);
    }
}
