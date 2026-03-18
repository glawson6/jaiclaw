package io.jclaw.tools.k8s;

import io.jclaw.core.tool.ToolCallback;
import io.jclaw.tools.ToolRegistry;

import java.util.List;

/**
 * Factory for creating and registering all Kubernetes tools.
 */
public final class KubernetesTools {

    private KubernetesTools() {}

    public static List<ToolCallback> all(KubernetesClientProvider clientProvider) {
        return List.of(
                new ListNamespacesTool(clientProvider),
                new ListPodsTool(clientProvider),
                new GetPodLogsTool(clientProvider),
                new DescribeResourceTool(clientProvider),
                new ListEventsTool(clientProvider),
                new ListNodesTool(clientProvider),
                new ListDeploymentsTool(clientProvider),
                new GetResourceUsageTool(clientProvider),
                new KubectlExecTool()
        );
    }

    public static void registerAll(ToolRegistry registry, KubernetesClientProvider clientProvider) {
        registry.registerAll(all(clientProvider));
    }
}
