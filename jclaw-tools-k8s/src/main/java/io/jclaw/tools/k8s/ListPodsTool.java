package io.jclaw.tools.k8s;

import io.fabric8.kubernetes.api.model.Pod;
import io.jclaw.core.tool.ToolContext;
import io.jclaw.core.tool.ToolDefinition;
import io.jclaw.core.tool.ToolProfile;
import io.jclaw.core.tool.ToolResult;
import io.jclaw.tools.ToolCatalog;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Lists pods in a namespace with optional label selector filtering.
 */
public class ListPodsTool extends AbstractK8sTool {

    private static final String INPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "namespace": {
                  "type": "string",
                  "description": "Kubernetes namespace (default: 'default')"
                },
                "labelSelector": {
                  "type": "string",
                  "description": "Label selector to filter pods (e.g. 'app=nginx')"
                }
              },
              "required": []
            }""";

    public ListPodsTool(KubernetesClientProvider clientProvider) {
        super(new ToolDefinition(
                "k8s_list_pods",
                "List pods in a Kubernetes namespace with their status. Optionally filter by label selector.",
                ToolCatalog.SECTION_KUBERNETES,
                INPUT_SCHEMA,
                Set.of(ToolProfile.FULL)
        ), clientProvider);
    }

    @Override
    protected ToolResult doExecute(Map<String, Object> parameters, ToolContext context) throws Exception {
        String namespace = optionalParam(parameters, "namespace", "default");
        String labelSelector = optionalParam(parameters, "labelSelector", null);

        List<Pod> pods;
        if (labelSelector != null) {
            pods = clientProvider.getClient().pods().inNamespace(namespace)
                    .withLabelSelector(labelSelector).list().getItems();
        } else {
            pods = clientProvider.getClient().pods().inNamespace(namespace).list().getItems();
        }
        if (pods.isEmpty()) {
            return new ToolResult.Success("No pods found in namespace '" + namespace + "'.");
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%-45s %-12s %-8s %-10s%n", "NAME", "STATUS", "READY", "RESTARTS"));
        sb.append("-".repeat(80)).append('\n');

        for (Pod pod : pods) {
            String name = pod.getMetadata().getName();
            String phase = pod.getStatus().getPhase();

            long readyCount = pod.getStatus().getContainerStatuses() == null ? 0 :
                    pod.getStatus().getContainerStatuses().stream()
                            .filter(cs -> Boolean.TRUE.equals(cs.getReady()))
                            .count();
            long totalContainers = pod.getSpec().getContainers().size();
            String ready = readyCount + "/" + totalContainers;

            int restarts = pod.getStatus().getContainerStatuses() == null ? 0 :
                    pod.getStatus().getContainerStatuses().stream()
                            .mapToInt(cs -> cs.getRestartCount())
                            .sum();

            sb.append(String.format("%-45s %-12s %-8s %-10d%n", name, phase, ready, restarts));
        }

        return new ToolResult.Success(sb.toString());
    }
}
