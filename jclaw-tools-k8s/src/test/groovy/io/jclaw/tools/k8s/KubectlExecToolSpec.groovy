package io.jclaw.tools.k8s

import io.jclaw.core.tool.ToolContext
import io.jclaw.core.tool.ToolResult
import spock.lang.Specification

class KubectlExecToolSpec extends Specification {

    def tool = new KubectlExecTool()

    def context = new ToolContext("agent1", "session1", "sid1", "/tmp")

    def "tool name is kubectl_exec"() {
        expect:
        tool.definition().name() == "kubectl_exec"
    }

    def "rejects commands that don't start with kubectl"() {
        when:
        def result = tool.execute(["command": "ls -la"], context)

        then:
        result instanceof ToolResult.Error
        (result as ToolResult.Error).message().contains("must start with 'kubectl'")
    }

    def "description instructs LLM to prefer built-in tools"() {
        expect:
        tool.definition().description().contains("Prefer built-in k8s_* tools")
    }

    def "description warns about mutating commands"() {
        expect:
        tool.definition().description().contains("mutating commands")
        tool.definition().description().contains("delete")
        tool.definition().description().contains("apply")
    }
}
