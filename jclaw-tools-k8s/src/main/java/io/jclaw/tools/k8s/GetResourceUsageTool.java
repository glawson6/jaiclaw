package io.jclaw.tools.k8s;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import io.jclaw.core.tool.ToolContext;
import io.jclaw.core.tool.ToolDefinition;
import io.jclaw.core.tool.ToolProfile;
import io.jclaw.core.tool.ToolResult;
import io.jclaw.tools.ToolCatalog;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Shows resource requests and limits vs node capacity for a namespace or cluster-wide.
 */
public class GetResourceUsageTool extends AbstractK8sTool {

    private static final String INPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "namespace": {
                  "type": "string",
                  "description": "Kubernetes namespace (omit for cluster-wide summary)"
                }
              },
              "required": []
            }""";

    public GetResourceUsageTool(KubernetesClientProvider clientProvider) {
        super(new ToolDefinition(
                "k8s_get_resource_usage",
                "Show resource requests/limits (CPU and memory) for pods vs cluster capacity. Helps identify over/under-provisioned workloads.",
                ToolCatalog.SECTION_KUBERNETES,
                INPUT_SCHEMA,
                Set.of(ToolProfile.FULL)
        ), clientProvider);
    }

    @Override
    protected ToolResult doExecute(Map<String, Object> parameters, ToolContext context) throws Exception {
        String namespace = optionalParam(parameters, "namespace", null);

        List<Pod> pods = namespace != null
                ? clientProvider.getClient().pods().inNamespace(namespace).list().getItems()
                : clientProvider.getClient().pods().inAnyNamespace().list().getItems();

        BigDecimal totalCpuRequests = BigDecimal.ZERO;
        BigDecimal totalCpuLimits = BigDecimal.ZERO;
        long totalMemoryRequestsBytes = 0;
        long totalMemoryLimitsBytes = 0;
        int podCount = 0;
        int containerCount = 0;

        for (Pod pod : pods) {
            if (!"Running".equals(pod.getStatus().getPhase())) {
                continue;
            }
            podCount++;
            for (Container container : pod.getSpec().getContainers()) {
                containerCount++;
                ResourceRequirements resources = container.getResources();
                if (resources == null) continue;

                if (resources.getRequests() != null) {
                    Quantity cpuReq = resources.getRequests().get("cpu");
                    if (cpuReq != null) {
                        totalCpuRequests = totalCpuRequests.add(cpuToMillicores(cpuReq));
                    }
                    Quantity memReq = resources.getRequests().get("memory");
                    if (memReq != null) {
                        totalMemoryRequestsBytes += memoryToBytes(memReq);
                    }
                }
                if (resources.getLimits() != null) {
                    Quantity cpuLimit = resources.getLimits().get("cpu");
                    if (cpuLimit != null) {
                        totalCpuLimits = totalCpuLimits.add(cpuToMillicores(cpuLimit));
                    }
                    Quantity memLimit = resources.getLimits().get("memory");
                    if (memLimit != null) {
                        totalMemoryLimitsBytes += memoryToBytes(memLimit);
                    }
                }
            }
        }

        // Get cluster capacity from nodes
        BigDecimal clusterCpuMillicores = BigDecimal.ZERO;
        long clusterMemoryBytes = 0;
        var nodes = clientProvider.getClient().nodes().list().getItems();
        for (var node : nodes) {
            Map<String, Quantity> capacity = node.getStatus().getAllocatable();
            if (capacity == null) capacity = node.getStatus().getCapacity();
            if (capacity != null) {
                Quantity cpu = capacity.get("cpu");
                if (cpu != null) {
                    clusterCpuMillicores = clusterCpuMillicores.add(cpuToMillicores(cpu));
                }
                Quantity mem = capacity.get("memory");
                if (mem != null) {
                    clusterMemoryBytes += memoryToBytes(mem);
                }
            }
        }

        String scope = namespace != null ? "Namespace: " + namespace : "Cluster-wide";
        StringBuilder sb = new StringBuilder();
        sb.append("Resource Usage Summary (").append(scope).append(")\n");
        sb.append("=".repeat(50)).append('\n');
        sb.append("Running Pods: ").append(podCount).append(" (").append(containerCount).append(" containers)\n\n");

        sb.append("CPU:\n");
        sb.append("  Requests: ").append(totalCpuRequests).append("m\n");
        sb.append("  Limits:   ").append(totalCpuLimits).append("m\n");
        sb.append("  Capacity: ").append(clusterCpuMillicores).append("m\n");
        if (clusterCpuMillicores.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal utilPct = totalCpuRequests.multiply(BigDecimal.valueOf(100))
                    .divide(clusterCpuMillicores, 1, java.math.RoundingMode.HALF_UP);
            sb.append("  Utilization (requests): ").append(utilPct).append("%\n");
        }

        sb.append("\nMemory:\n");
        sb.append("  Requests: ").append(humanizeBytes(totalMemoryRequestsBytes)).append('\n');
        sb.append("  Limits:   ").append(humanizeBytes(totalMemoryLimitsBytes)).append('\n');
        sb.append("  Capacity: ").append(humanizeBytes(clusterMemoryBytes)).append('\n');
        if (clusterMemoryBytes > 0) {
            double memPct = (double) totalMemoryRequestsBytes / clusterMemoryBytes * 100;
            sb.append("  Utilization (requests): ").append(String.format("%.1f%%", memPct)).append('\n');
        }

        return new ToolResult.Success(sb.toString());
    }

    private BigDecimal cpuToMillicores(Quantity q) {
        String value = q.getAmount();
        String format = q.getFormat();
        if (format != null && format.endsWith("m")) {
            return new BigDecimal(value);
        }
        // Convert cores to millicores
        return new BigDecimal(value).multiply(BigDecimal.valueOf(1000));
    }

    private long memoryToBytes(Quantity q) {
        String amount = q.getAmount();
        String format = q.getFormat();
        long base = Long.parseLong(amount);
        if (format == null || format.isEmpty()) return base;
        return switch (format) {
            case "Ki" -> base * 1024;
            case "Mi" -> base * 1024 * 1024;
            case "Gi" -> base * 1024 * 1024 * 1024;
            case "Ti" -> base * 1024L * 1024 * 1024 * 1024;
            case "K", "k" -> base * 1000;
            case "M" -> base * 1000 * 1000;
            case "G" -> base * 1000 * 1000 * 1000;
            default -> base;
        };
    }

    private String humanizeBytes(long bytes) {
        if (bytes < 1024) return bytes + "B";
        if (bytes < 1024 * 1024) return String.format("%.1fKi", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format("%.1fMi", bytes / (1024.0 * 1024));
        return String.format("%.1fGi", bytes / (1024.0 * 1024 * 1024));
    }
}
