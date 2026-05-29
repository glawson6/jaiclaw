package io.jaiclaw.wiki;

import io.jaiclaw.core.tool.ToolCallback;
import io.jaiclaw.tools.ToolRegistry;

import java.util.List;

/**
 * Factory for wiki tools.
 */
public final class WikiTools {

    private WikiTools() {}

    public static List<ToolCallback> all(WikiService service) {
        return List.of(
                new WikiReadTool(service),
                new WikiWriteTool(service),
                new WikiSearchTool(service),
                new WikiListTool(service),
                new WikiDeleteTool(service)
        );
    }

    public static void registerAll(ToolRegistry registry, WikiService service) {
        all(service).forEach(registry::register);
    }
}
