package io.jclaw.tools.k8s;

import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.api.model.NodeCondition;
import io.jclaw.core.tool.ToolContext;
import io.jclaw.core.tool.ToolDefinition;
import io.jclaw.core.tool.ToolProfile;
import io.jclaw.core.tool.ToolResult;
import io.jclaw.tools.ToolCatalog;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Lists cluster nodes with their status, roles, and capacity.
 */
public class ListNodesTool extends AbstractK8sTool {

    public ListNodesTool(KubernetesClientProvider clientProvider) {
        super(new ToolDefinition(
                "k8s_list_nodes",
                "List cluster nodes with status, roles, capacity (CPU/memory), and conditions.",
                ToolCatalog.SECTION_KUBERNETES,
                """
                {"type":"object","properties":{},"required":[]}""",
                Set.of(ToolProfile.FULL)
        ), clientProvider);
    }

    @Override
    protected ToolResult doExecute(Map<String, Object> parameters, ToolContext context) throws Exception {
        List<Node> nodes = clientProvider.getClient().nodes().list().getItems();

        if (nodes.isEmpty()) {
            return new ToolResult.Success("No nodes found.");
        }

        StringBuilder sb = new StringBuilder();
        for (Node node : nodes) {
            String name = node.getMetadata().getName();

            // Determine roles from labels
            Map<String, String> labels = node.getMetadata().getLabels();
            String roles = labels == null ? "" : labels.entrySet().stream()
                    .filter(e -> e.getKey().startsWith("node-role.kubernetes.io/"))
                    .map(e -> e.getKey().substring("node-role.kubernetes.io/".length()))
                    .reduce((a, b) -> a + "," + b)
                    .orElse("<none>");

            // Status from conditions
            String status = "Unknown";
            if (node.getStatus().getConditions() != null) {
                for (NodeCondition cond : node.getStatus().getConditions()) {
                    if ("Ready".equals(cond.getType())) {
                        status = "True".equals(cond.getStatus()) ? "Ready" : "NotReady";
                        break;
                    }
                }
            }

            // Capacity
            Map<String, io.fabric8.kubernetes.api.model.Quantity> capacity = node.getStatus().getCapacity();
            String cpu = capacity != null && capacity.containsKey("cpu")
                    ? capacity.get("cpu").toString() : "?";
            String memory = capacity != null && capacity.containsKey("memory")
                    ? capacity.get("memory").toString() : "?";

            sb.append("Node: ").append(name).append('\n');
            sb.append("  Status: ").append(status).append('\n');
            sb.append("  Roles: ").append(roles).append('\n');
            sb.append("  Capacity: CPU=").append(cpu).append(", Memory=").append(memory).append('\n');

            // OS and kernel info
            if (node.getStatus().getNodeInfo() != null) {
                sb.append("  OS: ").append(node.getStatus().getNodeInfo().getOsImage()).append('\n');
                sb.append("  Kubelet: ").append(node.getStatus().getNodeInfo().getKubeletVersion()).append('\n');
            }
            sb.append('\n');
        }

        return new ToolResult.Success(sb.toString());
    }
}
