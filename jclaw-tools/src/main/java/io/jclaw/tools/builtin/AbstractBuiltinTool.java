package io.jclaw.tools.builtin;

import io.jclaw.core.tool.ToolCallback;
import io.jclaw.core.tool.ToolContext;
import io.jclaw.core.tool.ToolDefinition;
import io.jclaw.core.tool.ToolResult;

import java.util.Map;

/**
 * Base class for built-in tools that provides the definition and common error handling.
 */
public abstract class AbstractBuiltinTool implements ToolCallback {

    private final ToolDefinition definition;

    protected AbstractBuiltinTool(ToolDefinition definition) {
        this.definition = definition;
    }

    @Override
    public final ToolDefinition definition() {
        return definition;
    }

    @Override
    public final ToolResult execute(Map<String, Object> parameters, ToolContext context) {
        try {
            return doExecute(parameters, context);
        } catch (Exception e) {
            return new ToolResult.Error(e.getMessage(), e);
        }
    }

    protected abstract ToolResult doExecute(Map<String, Object> parameters, ToolContext context) throws Exception;

    protected String requireParam(Map<String, Object> params, String key) {
        Object value = params.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Missing required parameter: " + key);
        }
        return value.toString();
    }

    protected String optionalParam(Map<String, Object> params, String key, String defaultValue) {
        Object value = params.get(key);
        return value != null ? value.toString() : defaultValue;
    }
}
