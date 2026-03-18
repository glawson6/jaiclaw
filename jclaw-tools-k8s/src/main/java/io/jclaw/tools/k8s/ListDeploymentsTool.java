package io.jclaw.tools.k8s;

import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.jclaw.core.tool.ToolContext;
import io.jclaw.core.tool.ToolDefinition;
import io.jclaw.core.tool.ToolProfile;
import io.jclaw.core.tool.ToolResult;
import io.jclaw.tools.ToolCatalog;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Lists deployments in a namespace with their replica status.
 */
public class ListDeploymentsTool extends AbstractK8sTool {

    private static final String INPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "namespace": {
                  "type": "string",
                  "description": "Kubernetes namespace (default: 'default')"
                }
              },
              "required": []
            }""";

    public ListDeploymentsTool(KubernetesClientProvider clientProvider) {
        super(new ToolDefinition(
                "k8s_list_deployments",
                "List deployments in a namespace with desired/ready/available replica counts.",
                ToolCatalog.SECTION_KUBERNETES,
                INPUT_SCHEMA,
                Set.of(ToolProfile.FULL)
        ), clientProvider);
    }

    @Override
    protected ToolResult doExecute(Map<String, Object> parameters, ToolContext context) throws Exception {
        String namespace = optionalParam(parameters, "namespace", "default");

        List<Deployment> deployments = clientProvider.getClient()
                .apps().deployments().inNamespace(namespace).list().getItems();

        if (deployments.isEmpty()) {
            return new ToolResult.Success("No deployments found in namespace '" + namespace + "'.");
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%-40s %-10s %-10s %-10s %-10s%n",
                "NAME", "DESIRED", "READY", "AVAILABLE", "UP-TO-DATE"));
        sb.append("-".repeat(85)).append('\n');

        for (Deployment dep : deployments) {
            String name = dep.getMetadata().getName();
            int desired = dep.getSpec().getReplicas() != null ? dep.getSpec().getReplicas() : 0;
            int ready = dep.getStatus().getReadyReplicas() != null ? dep.getStatus().getReadyReplicas() : 0;
            int available = dep.getStatus().getAvailableReplicas() != null ? dep.getStatus().getAvailableReplicas() : 0;
            int upToDate = dep.getStatus().getUpdatedReplicas() != null ? dep.getStatus().getUpdatedReplicas() : 0;

            sb.append(String.format("%-40s %-10d %-10d %-10d %-10d%n",
                    name, desired, ready, available, upToDate));
        }

        return new ToolResult.Success(sb.toString());
    }
}
