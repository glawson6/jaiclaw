package io.jclaw.tools.bridge;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jclaw.core.tool.ToolContext;
import io.jclaw.core.tool.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.definition.DefaultToolDefinition;

import java.util.Map;

/**
 * Adapts a JClaw {@link io.jclaw.core.tool.ToolCallback} into a Spring AI
 * {@link org.springframework.ai.tool.ToolCallback} so it can be registered
 * with the Spring AI {@code ChatClient}.
 */
public final class SpringAiToolBridge implements org.springframework.ai.tool.ToolCallback {

    private static final Logger log = LoggerFactory.getLogger(SpringAiToolBridge.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final io.jclaw.core.tool.ToolCallback jclawTool;
    private final org.springframework.ai.tool.definition.ToolDefinition springToolDef;
    private final ToolContext defaultContext;

    public SpringAiToolBridge(io.jclaw.core.tool.ToolCallback jclawTool, ToolContext defaultContext) {
        this.jclawTool = jclawTool;
        this.defaultContext = defaultContext;

        var def = jclawTool.definition();
        this.springToolDef = DefaultToolDefinition.builder()
                .name(def.name())
                .description(def.description())
                .inputSchema(def.inputSchema())
                .build();
    }

    public SpringAiToolBridge(io.jclaw.core.tool.ToolCallback jclawTool) {
        this(jclawTool, new ToolContext("default", "default", "default", "."));
    }

    @Override
    public org.springframework.ai.tool.definition.ToolDefinition getToolDefinition() {
        return springToolDef;
    }

    @Override
    public String call(String toolInput) {
        return call(toolInput, null);
    }

    @Override
    public String call(String toolInput, org.springframework.ai.chat.model.ToolContext springContext) {
        try {
            Map<String, Object> params = (toolInput == null || toolInput.isBlank())
                    ? Map.of()
                    : MAPPER.readValue(toolInput, MAP_TYPE);

            ToolContext ctx = mergeContext(springContext);
            ToolResult result = jclawTool.execute(params, ctx);

            return switch (result) {
                case ToolResult.Success s -> s.content();
                case ToolResult.Error e -> "ERROR: " + e.message();
            };
        } catch (Exception e) {
            log.error("Tool execution failed: {}", jclawTool.definition().name(), e);
            return "ERROR: " + e.getMessage();
        }
    }

    /** Unwrap the original JClaw tool. */
    public io.jclaw.core.tool.ToolCallback unwrap() {
        return jclawTool;
    }

    private ToolContext mergeContext(org.springframework.ai.chat.model.ToolContext springContext) {
        if (springContext == null) {
            return defaultContext;
        }
        Map<String, Object> merged = new java.util.HashMap<>(defaultContext.contextData());
        merged.putAll(springContext.getContext());
        return new ToolContext(
                defaultContext.agentId(),
                defaultContext.sessionKey(),
                defaultContext.sessionId(),
                defaultContext.workspaceDir(),
                Map.copyOf(merged)
        );
    }

    /**
     * Convenience factory: adapt a list of JClaw tools to Spring AI ToolCallbacks.
     */
    public static java.util.List<org.springframework.ai.tool.ToolCallback> bridgeAll(
            java.util.Collection<? extends io.jclaw.core.tool.ToolCallback> tools,
            ToolContext context) {
        return tools.stream()
                .map(t -> (org.springframework.ai.tool.ToolCallback) new SpringAiToolBridge(t, context))
                .toList();
    }
}
