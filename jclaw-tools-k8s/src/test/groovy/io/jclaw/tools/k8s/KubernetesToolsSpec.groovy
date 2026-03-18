package io.jclaw.tools.k8s

import io.jclaw.tools.ToolCatalog
import io.jclaw.tools.ToolRegistry
import spock.lang.Specification
import spock.lang.Subject

class KubernetesToolsSpec extends Specification {

    def clientProvider = new KubernetesClientProvider()

    def "all() returns exactly 9 tools"() {
        when:
        def tools = KubernetesTools.all(clientProvider)

        then:
        tools.size() == 9
    }

    def "all tools are in Kubernetes section"() {
        when:
        def tools = KubernetesTools.all(clientProvider)

        then:
        tools.every { it.definition().section() == ToolCatalog.SECTION_KUBERNETES }
    }

    def "all tool names start with k8s_ or kubectl_"() {
        when:
        def tools = KubernetesTools.all(clientProvider)

        then:
        tools.every {
            it.definition().name().startsWith("k8s_") ||
            it.definition().name().startsWith("kubectl_")
        }
    }

    def "tool names are unique"() {
        when:
        def tools = KubernetesTools.all(clientProvider)
        def names = tools.collect { it.definition().name() }

        then:
        names.unique().size() == names.size()
    }

    def "registerAll adds all tools to registry"() {
        given:
        def registry = new ToolRegistry()

        when:
        KubernetesTools.registerAll(registry, clientProvider)

        then:
        registry.size() == 9
        registry.contains("k8s_list_namespaces")
        registry.contains("k8s_list_pods")
        registry.contains("k8s_get_pod_logs")
        registry.contains("k8s_describe_resource")
        registry.contains("k8s_list_events")
        registry.contains("k8s_list_nodes")
        registry.contains("k8s_list_deployments")
        registry.contains("k8s_get_resource_usage")
        registry.contains("kubectl_exec")
    }

    def "expected tool names are present"() {
        when:
        def tools = KubernetesTools.all(clientProvider)
        def names = tools.collect { it.definition().name() } as Set

        then:
        names == [
            "k8s_list_namespaces",
            "k8s_list_pods",
            "k8s_get_pod_logs",
            "k8s_describe_resource",
            "k8s_list_events",
            "k8s_list_nodes",
            "k8s_list_deployments",
            "k8s_get_resource_usage",
            "kubectl_exec"
        ] as Set
    }

    def "all tools have non-blank descriptions"() {
        when:
        def tools = KubernetesTools.all(clientProvider)

        then:
        tools.every { !it.definition().description().isBlank() }
    }

    def "all tools have valid input schema"() {
        when:
        def tools = KubernetesTools.all(clientProvider)

        then:
        tools.every { it.definition().inputSchema().contains('"type"') }
    }
}
