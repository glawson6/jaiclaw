package io.jclaw.tools.k8s;

import io.jclaw.core.tool.ToolContext;
import io.jclaw.core.tool.ToolDefinition;
import io.jclaw.core.tool.ToolProfile;
import io.jclaw.core.tool.ToolResult;
import io.jclaw.tools.ToolCatalog;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Lists all namespaces in the Kubernetes cluster.
 */
public class ListNamespacesTool extends AbstractK8sTool {

    public ListNamespacesTool(KubernetesClientProvider clientProvider) {
        super(new ToolDefinition(
                "k8s_list_namespaces",
                "List all namespaces in the Kubernetes cluster.",
                ToolCatalog.SECTION_KUBERNETES,
                """
                {"type":"object","properties":{},"required":[]}""",
                Set.of(ToolProfile.FULL)
        ), clientProvider);
    }

    @Override
    protected ToolResult doExecute(Map<String, Object> parameters, ToolContext context) throws Exception {
        String result = clientProvider.getClient().namespaces().list().getItems().stream()
                .map(ns -> {
                    String name = ns.getMetadata().getName();
                    String phase = ns.getStatus().getPhase();
                    return name + " (" + phase + ")";
                })
                .collect(Collectors.joining("\n"));

        return new ToolResult.Success(result.isEmpty() ? "No namespaces found." : result);
    }
}
