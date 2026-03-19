package io.jclaw.memory;

import io.jclaw.core.tool.ToolCallback;
import io.jclaw.core.tool.ToolContext;
import io.jclaw.core.tool.ToolDefinition;
import io.jclaw.core.tool.ToolResult;

import java.util.Map;

/**
 * Tool that allows the agent to explicitly save a note to long-term workspace memory.
 * Writes to MEMORY.md (optionally under a section) and appends to the daily log.
 */
public class MemorySaveTool implements ToolCallback {

    private static final String INPUT_SCHEMA = """
            {"type":"object","properties":{
              "content":{"type":"string","description":"The note to save to long-term memory"},
              "section":{"type":"string","description":"Optional section header in MEMORY.md (e.g., 'Preferences', 'Decisions')"}
            },"required":["content"]}""";

    private static final ToolDefinition DEFINITION = new ToolDefinition(
            "memory_save",
            "Save a note to long-term workspace memory. Use this to persist important information, decisions, user preferences, or learnings across sessions.",
            "Memory",
            INPUT_SCHEMA);

    private final WorkspaceMemoryManager workspaceMemory;
    private final DailyLogAppender dailyLog;

    public MemorySaveTool(WorkspaceMemoryManager workspaceMemory, DailyLogAppender dailyLog) {
        this.workspaceMemory = workspaceMemory;
        this.dailyLog = dailyLog;
    }

    @Override
    public ToolDefinition definition() {
        return DEFINITION;
    }

    @Override
    public ToolResult execute(Map<String, Object> parameters, ToolContext context) {
        try {
            String content = (String) parameters.get("content");
            if (content == null || content.isBlank()) {
                return new ToolResult.Error("Missing required parameter: content");
            }

            String section = (String) parameters.getOrDefault("section", "Notes");

            workspaceMemory.appendToSection(section, content);
            dailyLog.append("Saved to memory: " + content);

            return new ToolResult.Success("Saved to long-term memory under section '" + section + "'");
        } catch (Exception e) {
            return new ToolResult.Error("Failed to save memory: " + e.getMessage(), e);
        }
    }
}
