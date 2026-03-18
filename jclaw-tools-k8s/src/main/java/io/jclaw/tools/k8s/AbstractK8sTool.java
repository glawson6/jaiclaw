package io.jclaw.tools.k8s;

import io.jclaw.core.tool.ToolDefinition;
import io.jclaw.tools.builtin.AbstractBuiltinTool;

/**
 * Base class for Kubernetes tools that holds a reference to the client provider.
 */
public abstract class AbstractK8sTool extends AbstractBuiltinTool {

    protected final KubernetesClientProvider clientProvider;

    protected AbstractK8sTool(ToolDefinition definition, KubernetesClientProvider clientProvider) {
        super(definition);
        this.clientProvider = clientProvider;
    }

    protected int optionalIntParam(java.util.Map<String, Object> params, String key, int defaultValue) {
        Object value = params.get(key);
        if (value == null) {
            return defaultValue;
        }
        return ((Number) value).intValue();
    }
}
