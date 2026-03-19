package io.jclaw.canvas;

import io.jclaw.core.tool.ToolCallback;
import io.jclaw.core.tool.ToolContext;
import io.jclaw.core.tool.ToolDefinition;
import io.jclaw.core.tool.ToolResult;

import java.util.List;
import java.util.Map;

/**
 * Factory for canvas tools that allow the agent to present rich visual content.
 */
public final class CanvasTools {

    public static final String SECTION_CANVAS = "Canvas";

    private CanvasTools() {}

    public static List<ToolCallback> all(CanvasService canvasService) {
        return List.of(
                new PresentTool(canvasService),
                new EvalTool(canvasService),
                new SnapshotTool(canvasService)
        );
    }

    static class PresentTool implements ToolCallback {
        private static final ToolDefinition DEF = new ToolDefinition("canvas_present",
                "Push HTML content to connected clients for rich visual display (dashboards, charts, forms)",
                SECTION_CANVAS,
                """
                {"type":"object","properties":{"html":{"type":"string","description":"HTML content to display"},"url":{"type":"string","description":"URL to navigate to (alternative to html)"}},"required":[]}""");
        private final CanvasService service;
        PresentTool(CanvasService service) { this.service = service; }
        @Override public ToolDefinition definition() { return DEF; }
        @Override public ToolResult execute(Map<String, Object> params, ToolContext ctx) {
            String html = (String) params.get("html");
            if (html == null || html.isBlank()) {
                return new ToolResult.Error("Missing required parameter: html");
            }
            String url = service.present(html);
            return new ToolResult.Success("Canvas presented at: " + url);
        }
    }

    static class EvalTool implements ToolCallback {
        private static final ToolDefinition DEF = new ToolDefinition("canvas_eval",
                "Execute JavaScript in the canvas context",
                SECTION_CANVAS,
                """
                {"type":"object","properties":{"javascript":{"type":"string","description":"JavaScript to execute"}},"required":["javascript"]}""");
        private final CanvasService service;
        EvalTool(CanvasService service) { this.service = service; }
        @Override public ToolDefinition definition() { return DEF; }
        @Override public ToolResult execute(Map<String, Object> params, ToolContext ctx) {
            return new ToolResult.Success("JavaScript evaluation requires a connected client");
        }
    }

    static class SnapshotTool implements ToolCallback {
        private static final ToolDefinition DEF = new ToolDefinition("canvas_snapshot",
                "Capture the current canvas content as HTML",
                SECTION_CANVAS);
        private final CanvasService service;
        SnapshotTool(CanvasService service) { this.service = service; }
        @Override public ToolDefinition definition() { return DEF; }
        @Override public ToolResult execute(Map<String, Object> params, ToolContext ctx) {
            return service.getCurrentContent()
                    .map(html -> (ToolResult) new ToolResult.Success(html))
                    .orElse(new ToolResult.Error("No canvas content available"));
        }
    }
}
