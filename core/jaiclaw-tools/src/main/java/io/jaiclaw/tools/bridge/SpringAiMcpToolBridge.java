package io.jaiclaw.tools.bridge;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jaiclaw.core.mcp.McpToolDefinition;
import io.jaiclaw.core.mcp.McpToolProvider;
import io.jaiclaw.core.mcp.McpToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.DefaultToolDefinition;

import java.util.Collection;
import java.util.Map;

/**
 * Adapts a JaiClaw {@link McpToolProvider} into Spring AI {@link ToolCallbackProvider}
 * so its tools are discovered by the Spring AI MCP server auto-configuration.
 *
 * <p>For each {@link McpToolDefinition} the provider exposes, a Spring AI
 * {@link ToolCallback} is created that delegates {@code call()} to
 * {@link McpToolProvider#execute(String, Map, io.jaiclaw.core.tenant.TenantContext)}.
 */
public final class SpringAiMcpToolBridge implements ToolCallback {

    private static final Logger log = LoggerFactory.getLogger(SpringAiMcpToolBridge.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final McpToolProvider provider;
    private final McpToolDefinition toolDef;
    private final org.springframework.ai.tool.definition.ToolDefinition springToolDef;

    public SpringAiMcpToolBridge(McpToolProvider provider, McpToolDefinition toolDef) {
        this.provider = provider;
        this.toolDef = toolDef;
        this.springToolDef = DefaultToolDefinition.builder()
                .name(toolDef.name())
                .description(toolDef.description())
                .inputSchema(toolDef.inputSchema())
                .build();
    }

    @Override
    public org.springframework.ai.tool.definition.ToolDefinition getToolDefinition() {
        return springToolDef;
    }

    @Override
    public String call(String toolInput) {
        try {
            Map<String, Object> params = (toolInput == null || toolInput.isBlank())
                    ? Map.of()
                    : MAPPER.readValue(toolInput, MAP_TYPE);

            McpToolResult result = provider.execute(toolDef.name(), params, null);

            return result.isError()
                    ? "ERROR: " + result.content()
                    : result.content();
        } catch (Exception e) {
            log.error("MCP tool execution failed: {}", toolDef.name(), e);
            return "ERROR: " + e.getMessage();
        }
    }

    /**
     * Convenience factory: adapt all tools from a collection of {@link McpToolProvider}s
     * into a single {@link ToolCallbackProvider}.
     */
    public static ToolCallbackProvider bridgeAll(Collection<? extends McpToolProvider> providers) {
        ToolCallback[] callbacks = providers.stream()
                .flatMap(p -> {
                    log.info("Bridging McpToolProvider '{}' with {} tools",
                            p.getServerName(), p.getTools().size());
                    return p.getTools().stream()
                            .map(def -> (ToolCallback) new SpringAiMcpToolBridge(p, def));
                })
                .toArray(ToolCallback[]::new);
        return ToolCallbackProvider.from(callbacks);
    }
}
