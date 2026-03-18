package io.jclaw.tools.k8s;

import io.jclaw.core.tool.ToolContext;
import io.jclaw.core.tool.ToolDefinition;
import io.jclaw.core.tool.ToolProfile;
import io.jclaw.core.tool.ToolResult;
import io.jclaw.tools.ToolCatalog;

import java.util.Map;
import java.util.Set;

/**
 * Gets logs from a pod, optionally from a specific container.
 */
public class GetPodLogsTool extends AbstractK8sTool {

    private static final String INPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "namespace": {
                  "type": "string",
                  "description": "Kubernetes namespace"
                },
                "pod": {
                  "type": "string",
                  "description": "Pod name"
                },
                "container": {
                  "type": "string",
                  "description": "Container name (optional, for multi-container pods)"
                },
                "tailLines": {
                  "type": "integer",
                  "description": "Number of lines to tail (default: 100)"
                }
              },
              "required": ["namespace", "pod"]
            }""";

    public GetPodLogsTool(KubernetesClientProvider clientProvider) {
        super(new ToolDefinition(
                "k8s_get_pod_logs",
                "Get logs from a pod/container. Returns the last N lines (default 100).",
                ToolCatalog.SECTION_KUBERNETES,
                INPUT_SCHEMA,
                Set.of(ToolProfile.FULL)
        ), clientProvider);
    }

    @Override
    protected ToolResult doExecute(Map<String, Object> parameters, ToolContext context) throws Exception {
        String namespace = requireParam(parameters, "namespace");
        String pod = requireParam(parameters, "pod");
        String container = optionalParam(parameters, "container", null);
        int tailLines = optionalIntParam(parameters, "tailLines", 100);

        var podLog = clientProvider.getClient().pods()
                .inNamespace(namespace)
                .withName(pod);

        String logs;
        if (container != null) {
            logs = podLog.inContainer(container).tailingLines(tailLines).getLog();
        } else {
            logs = podLog.tailingLines(tailLines).getLog();
        }

        if (logs == null || logs.isBlank()) {
            return new ToolResult.Success("No logs available for pod '" + pod + "'.");
        }

        return new ToolResult.Success(logs);
    }
}
