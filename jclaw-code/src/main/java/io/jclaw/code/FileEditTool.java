package io.jclaw.code;

import io.jclaw.core.tool.ToolContext;
import io.jclaw.core.tool.ToolDefinition;
import io.jclaw.core.tool.ToolProfile;
import io.jclaw.core.tool.ToolResult;
import io.jclaw.tools.ToolCatalog;
import io.jclaw.tools.builtin.AbstractBuiltinTool;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

/**
 * Surgical string replacement tool inspired by Claude Code's Edit tool.
 * Finds a unique {@code old_string} in a file and replaces it with {@code new_string}.
 */
public class FileEditTool extends AbstractBuiltinTool {

    private static final String INPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "path": {
                  "type": "string",
                  "description": "Path to the file to edit, relative to the workspace directory"
                },
                "old_string": {
                  "type": "string",
                  "description": "The exact text to find in the file"
                },
                "new_string": {
                  "type": "string",
                  "description": "The replacement text"
                },
                "replace_all": {
                  "type": "boolean",
                  "description": "Replace all occurrences instead of requiring uniqueness. Defaults to false."
                }
              },
              "required": ["path", "old_string", "new_string"]
            }""";

    public FileEditTool() {
        super(new ToolDefinition(
                "file_edit",
                "Perform a surgical string replacement in a file. Finds old_string and replaces it with new_string. "
                        + "Fails if old_string is not found or is not unique (unless replace_all is true).",
                ToolCatalog.SECTION_FILES,
                INPUT_SCHEMA,
                Set.of(ToolProfile.CODING, ToolProfile.FULL)
        ));
    }

    @Override
    protected ToolResult doExecute(Map<String, Object> parameters, ToolContext context) throws Exception {
        String filePath = requireParam(parameters, "path");
        String oldString = requireParam(parameters, "old_string");
        String newString = requireParam(parameters, "new_string");
        boolean replaceAll = Boolean.parseBoolean(
                optionalParam(parameters, "replace_all", "false"));

        Path resolved = Path.of(context.workspaceDir()).resolve(filePath).normalize();

        if (!Files.exists(resolved)) {
            return new ToolResult.Error("File not found: " + filePath);
        }
        if (!Files.isRegularFile(resolved)) {
            return new ToolResult.Error("Not a regular file: " + filePath);
        }

        String content = Files.readString(resolved);
        int count = countOccurrences(content, oldString);

        if (count == 0) {
            return new ToolResult.Error("old_string not found in " + filePath);
        }
        if (count > 1 && !replaceAll) {
            return new ToolResult.Error(
                    "old_string found " + count + " times in " + filePath
                            + ". Provide more context to make it unique, or set replace_all=true.");
        }

        String updated;
        if (replaceAll) {
            updated = content.replace(oldString, newString);
        } else {
            // Single replacement at the first (and only) occurrence
            int idx = content.indexOf(oldString);
            updated = content.substring(0, idx) + newString + content.substring(idx + oldString.length());
        }

        Files.writeString(resolved, updated);

        // Build a context snippet around the first replacement
        int editLine = lineNumberAt(content, content.indexOf(oldString));
        return new ToolResult.Success(
                "Replaced " + count + " occurrence(s) in " + filePath + " (around line " + editLine + ")");
    }

    private static int countOccurrences(String text, String target) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(target, idx)) != -1) {
            count++;
            idx += target.length();
        }
        return count;
    }

    private static int lineNumberAt(String text, int charIndex) {
        int line = 1;
        for (int i = 0; i < charIndex && i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }
}
