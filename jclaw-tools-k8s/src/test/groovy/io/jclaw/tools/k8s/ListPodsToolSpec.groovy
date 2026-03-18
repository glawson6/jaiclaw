package io.jclaw.tools.k8s

import io.fabric8.kubernetes.api.model.*
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.dsl.MixedOperation
import io.fabric8.kubernetes.client.dsl.PodResource
import io.jclaw.core.tool.ToolContext
import io.jclaw.core.tool.ToolResult
import spock.lang.Specification

class ListPodsToolSpec extends Specification {

    def clientProvider = Mock(KubernetesClientProvider)
    def client = Mock(KubernetesClient)
    def tool = new ListPodsTool(clientProvider)
    def context = new ToolContext("agent1", "session1", "sid1", "/tmp")

    def setup() {
        clientProvider.getClient() >> client
    }

    def "tool name is k8s_list_pods"() {
        expect:
        tool.definition().name() == "k8s_list_pods"
    }

    def "returns 'no pods' when namespace is empty"() {
        given:
        def podOp = Mock(MixedOperation)
        def namespacedPodOp = Mock(io.fabric8.kubernetes.client.dsl.NonNamespaceOperation)
        client.pods() >> podOp
        podOp.inNamespace("default") >> namespacedPodOp

        def podList = new PodList()
        podList.setItems([])
        namespacedPodOp.list() >> podList

        when:
        def result = tool.execute([:], context)

        then:
        result instanceof ToolResult.Success
        (result as ToolResult.Success).content().contains("No pods found")
    }

    def "uses default namespace when not specified"() {
        given:
        def podOp = Mock(MixedOperation)
        def namespacedPodOp = Mock(io.fabric8.kubernetes.client.dsl.NonNamespaceOperation)
        client.pods() >> podOp

        def podList = new PodList()
        podList.setItems([])
        namespacedPodOp.list() >> podList

        when:
        tool.execute([:], context)

        then:
        1 * podOp.inNamespace("default") >> namespacedPodOp
    }

    def "input schema allows namespace and labelSelector"() {
        expect:
        tool.definition().inputSchema().contains("namespace")
        tool.definition().inputSchema().contains("labelSelector")
    }
}
