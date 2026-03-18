package io.jclaw.tools.k8s;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.jclaw.core.tool.ToolContext;
import io.jclaw.core.tool.ToolDefinition;
import io.jclaw.core.tool.ToolProfile;
import io.jclaw.core.tool.ToolResult;
import io.jclaw.tools.ToolCatalog;

import java.util.Map;
import java.util.Set;

/**
 * Describes a Kubernetes resource (pod, deployment, service, node, etc.) by kind and name.
 */
public class DescribeResourceTool extends AbstractK8sTool {

    private static final String INPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "namespace": {
                  "type": "string",
                  "description": "Kubernetes namespace (not needed for cluster-scoped resources like nodes)"
                },
                "kind": {
                  "type": "string",
                  "description": "Resource kind: pod, deployment, service, node, configmap, secret, statefulset, daemonset, job, cronjob, ingress, pvc"
                },
                "name": {
                  "type": "string",
                  "description": "Resource name"
                }
              },
              "required": ["kind", "name"]
            }""";

    public DescribeResourceTool(KubernetesClientProvider clientProvider) {
        super(new ToolDefinition(
                "k8s_describe_resource",
                "Describe a Kubernetes resource (pod, deployment, service, node, configmap, etc.) showing its full spec, status, events, and conditions.",
                ToolCatalog.SECTION_KUBERNETES,
                INPUT_SCHEMA,
                Set.of(ToolProfile.FULL)
        ), clientProvider);
    }

    @Override
    protected ToolResult doExecute(Map<String, Object> parameters, ToolContext context) throws Exception {
        String kind = requireParam(parameters, "kind").toLowerCase();
        String name = requireParam(parameters, "name");
        String namespace = optionalParam(parameters, "namespace", "default");

        KubernetesClient client = clientProvider.getClient();
        HasMetadata resource = fetchResource(client, kind, namespace, name);

        if (resource == null) {
            return new ToolResult.Error("Resource " + kind + "/" + name + " not found in namespace '" + namespace + "'.");
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Kind: ").append(resource.getKind()).append('\n');
        sb.append("Name: ").append(resource.getMetadata().getName()).append('\n');
        if (resource.getMetadata().getNamespace() != null) {
            sb.append("Namespace: ").append(resource.getMetadata().getNamespace()).append('\n');
        }
        sb.append("Created: ").append(resource.getMetadata().getCreationTimestamp()).append('\n');

        Map<String, String> labels = resource.getMetadata().getLabels();
        if (labels != null && !labels.isEmpty()) {
            sb.append("Labels:\n");
            labels.forEach((k, v) -> sb.append("  ").append(k).append("=").append(v).append('\n'));
        }

        Map<String, String> annotations = resource.getMetadata().getAnnotations();
        if (annotations != null && !annotations.isEmpty()) {
            sb.append("Annotations:\n");
            annotations.forEach((k, v) -> sb.append("  ").append(k).append("=").append(v).append('\n'));
        }

        return new ToolResult.Success(sb.toString());
    }

    private HasMetadata fetchResource(KubernetesClient client, String kind, String namespace, String name) {
        return switch (kind) {
            case "pod" -> client.pods().inNamespace(namespace).withName(name).get();
            case "deployment" -> client.apps().deployments().inNamespace(namespace).withName(name).get();
            case "service" -> client.services().inNamespace(namespace).withName(name).get();
            case "node" -> client.nodes().withName(name).get();
            case "configmap" -> client.configMaps().inNamespace(namespace).withName(name).get();
            case "secret" -> client.secrets().inNamespace(namespace).withName(name).get();
            case "statefulset" -> client.apps().statefulSets().inNamespace(namespace).withName(name).get();
            case "daemonset" -> client.apps().daemonSets().inNamespace(namespace).withName(name).get();
            case "job" -> client.batch().v1().jobs().inNamespace(namespace).withName(name).get();
            case "cronjob" -> client.batch().v1().cronjobs().inNamespace(namespace).withName(name).get();
            case "ingress" -> client.network().v1().ingresses().inNamespace(namespace).withName(name).get();
            case "pvc", "persistentvolumeclaim" -> client.persistentVolumeClaims().inNamespace(namespace).withName(name).get();
            default -> throw new IllegalArgumentException("Unsupported resource kind: " + kind +
                    ". Supported: pod, deployment, service, node, configmap, secret, statefulset, daemonset, job, cronjob, ingress, pvc");
        };
    }
}
