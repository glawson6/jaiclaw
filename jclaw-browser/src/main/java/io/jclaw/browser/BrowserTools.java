package io.jclaw.browser;

import io.jclaw.core.tool.ToolCallback;
import io.jclaw.core.tool.ToolContext;
import io.jclaw.core.tool.ToolDefinition;
import io.jclaw.core.tool.ToolResult;
import io.jclaw.tools.ToolCatalog;

import java.util.List;
import java.util.Map;

/**
 * Factory for all browser automation tools.
 * Each tool delegates to the shared {@link BrowserService}.
 */
public final class BrowserTools {

    public static final String SECTION_BROWSER = "Browser";

    private BrowserTools() {}

    public static List<ToolCallback> all(BrowserService browserService) {
        return List.of(
                new NavigateTool(browserService),
                new ClickTool(browserService),
                new TypeTool(browserService),
                new ScreenshotTool(browserService),
                new EvaluateTool(browserService),
                new ReadPageTool(browserService),
                new ListTabsTool(browserService),
                new CloseTabTool(browserService)
        );
    }

    static class NavigateTool implements ToolCallback {
        private static final ToolDefinition DEF = new ToolDefinition("browser_navigate",
                "Navigate to a URL and return the page content",
                SECTION_BROWSER,
                """
                {"type":"object","properties":{"url":{"type":"string","description":"URL to navigate to"}},"required":["url"]}""");
        private final BrowserService service;
        NavigateTool(BrowserService service) { this.service = service; }
        @Override public ToolDefinition definition() { return DEF; }
        @Override public ToolResult execute(Map<String, Object> params, ToolContext ctx) {
            String url = (String) params.get("url");
            if (url == null) return new ToolResult.Error("Missing required parameter: url");
            String result = service.getDefaultSession().navigate(url);
            return new ToolResult.Success(result);
        }
    }

    static class ClickTool implements ToolCallback {
        private static final ToolDefinition DEF = new ToolDefinition("browser_click",
                "Click an element by CSS/XPath selector",
                SECTION_BROWSER,
                """
                {"type":"object","properties":{"selector":{"type":"string","description":"CSS or XPath selector"}},"required":["selector"]}""");
        private final BrowserService service;
        ClickTool(BrowserService service) { this.service = service; }
        @Override public ToolDefinition definition() { return DEF; }
        @Override public ToolResult execute(Map<String, Object> params, ToolContext ctx) {
            String selector = (String) params.get("selector");
            if (selector == null) return new ToolResult.Error("Missing required parameter: selector");
            return new ToolResult.Success(service.getDefaultSession().click(selector));
        }
    }

    static class TypeTool implements ToolCallback {
        private static final ToolDefinition DEF = new ToolDefinition("browser_type",
                "Type text into an input field",
                SECTION_BROWSER,
                """
                {"type":"object","properties":{"selector":{"type":"string","description":"CSS selector of the input"},"text":{"type":"string","description":"Text to type"}},"required":["selector","text"]}""");
        private final BrowserService service;
        TypeTool(BrowserService service) { this.service = service; }
        @Override public ToolDefinition definition() { return DEF; }
        @Override public ToolResult execute(Map<String, Object> params, ToolContext ctx) {
            String selector = (String) params.get("selector");
            String text = (String) params.get("text");
            if (selector == null || text == null) return new ToolResult.Error("Missing required parameters");
            return new ToolResult.Success(service.getDefaultSession().type(selector, text));
        }
    }

    static class ScreenshotTool implements ToolCallback {
        private static final ToolDefinition DEF = new ToolDefinition("browser_screenshot",
                "Capture a screenshot of the current page (returns base64 PNG)",
                SECTION_BROWSER);
        private final BrowserService service;
        ScreenshotTool(BrowserService service) { this.service = service; }
        @Override public ToolDefinition definition() { return DEF; }
        @Override public ToolResult execute(Map<String, Object> params, ToolContext ctx) {
            return new ToolResult.Success(service.getDefaultSession().screenshot());
        }
    }

    static class EvaluateTool implements ToolCallback {
        private static final ToolDefinition DEF = new ToolDefinition("browser_evaluate",
                "Execute JavaScript in the page context and return the result",
                SECTION_BROWSER,
                """
                {"type":"object","properties":{"javascript":{"type":"string","description":"JavaScript code to execute"}},"required":["javascript"]}""");
        private final BrowserService service;
        EvaluateTool(BrowserService service) { this.service = service; }
        @Override public ToolDefinition definition() { return DEF; }
        @Override public ToolResult execute(Map<String, Object> params, ToolContext ctx) {
            String js = (String) params.get("javascript");
            if (js == null) return new ToolResult.Error("Missing required parameter: javascript");
            return new ToolResult.Success(service.getDefaultSession().evaluate(js));
        }
    }

    static class ReadPageTool implements ToolCallback {
        private static final ToolDefinition DEF = new ToolDefinition("browser_read_page",
                "Read the text content of the current page or a specific element",
                SECTION_BROWSER,
                """
                {"type":"object","properties":{"selector":{"type":"string","description":"Optional CSS selector to read a specific element"}},"required":[]}""");
        private final BrowserService service;
        ReadPageTool(BrowserService service) { this.service = service; }
        @Override public ToolDefinition definition() { return DEF; }
        @Override public ToolResult execute(Map<String, Object> params, ToolContext ctx) {
            String selector = (String) params.get("selector");
            return new ToolResult.Success(service.getDefaultSession().readPageContent(selector));
        }
    }

    static class ListTabsTool implements ToolCallback {
        private static final ToolDefinition DEF = new ToolDefinition("browser_list_tabs",
                "List all open browser sessions with their current URLs",
                SECTION_BROWSER);
        private final BrowserService service;
        ListTabsTool(BrowserService service) { this.service = service; }
        @Override public ToolDefinition definition() { return DEF; }
        @Override public ToolResult execute(Map<String, Object> params, ToolContext ctx) {
            List<String> sessions = service.listSessions();
            if (sessions.isEmpty()) return new ToolResult.Success("No browser sessions open");
            StringBuilder sb = new StringBuilder();
            for (String id : sessions) {
                sb.append(id).append("\n");
            }
            return new ToolResult.Success(sb.toString());
        }
    }

    static class CloseTabTool implements ToolCallback {
        private static final ToolDefinition DEF = new ToolDefinition("browser_close_tab",
                "Close a browser session/tab",
                SECTION_BROWSER,
                """
                {"type":"object","properties":{"tabId":{"type":"string","description":"Session ID to close (default: 'default')"}},"required":[]}""");
        private final BrowserService service;
        CloseTabTool(BrowserService service) { this.service = service; }
        @Override public ToolDefinition definition() { return DEF; }
        @Override public ToolResult execute(Map<String, Object> params, ToolContext ctx) {
            String tabId = params.getOrDefault("tabId", "default").toString();
            service.closeSession(tabId);
            return new ToolResult.Success("Closed session: " + tabId);
        }
    }
}
