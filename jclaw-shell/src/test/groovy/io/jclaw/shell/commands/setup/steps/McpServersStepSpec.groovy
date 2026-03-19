package io.jclaw.shell.commands.setup.steps

import io.jclaw.shell.commands.setup.OnboardResult
import org.springframework.shell.component.context.BaseComponentContext
import org.springframework.shell.component.flow.ComponentFlow
import org.springframework.shell.component.flow.ConfirmationInputSpec
import org.springframework.shell.component.flow.SingleItemSelectorSpec
import org.springframework.shell.component.flow.StringInputSpec
import spock.lang.Specification

class McpServersStepSpec extends Specification {

    ComponentFlow.Builder flowBuilder = Mock()

    McpServersStep step = new McpServersStep(flowBuilder)

    def "name returns MCP Servers"() {
        expect:
        step.name() == "MCP Servers"
    }

    def "quickstart mode skips MCP configuration"() {
        given:
        def result = new OnboardResult()
        result.setFlowMode(OnboardResult.FlowMode.QUICKSTART)

        when:
        boolean success = step.execute(result)

        then:
        success
        result.mcpServers().isEmpty()
        0 * flowBuilder._
    }

    def "manual mode with no servers added"() {
        given:
        def result = new OnboardResult()
        result.setFlowMode(OnboardResult.FlowMode.MANUAL)

        // Confirm "no" to adding MCP server
        def confirmContext = new BaseComponentContext()
        confirmContext.put("add-mcp", false)
        def confirmFlowResult = Mock(ComponentFlow.ComponentFlowResult) {
            getContext() >> confirmContext
        }
        def confirmFlow = Mock(ComponentFlow) {
            run() >> confirmFlowResult
        }

        def clonedBuilder = Mock(ComponentFlow.Builder)
        def confirmSpec = Mock(ConfirmationInputSpec)

        flowBuilder.clone() >> clonedBuilder
        clonedBuilder.reset() >> clonedBuilder
        clonedBuilder.withConfirmationInput("add-mcp") >> confirmSpec
        confirmSpec.name(_) >> confirmSpec
        confirmSpec.defaultValue(_) >> confirmSpec
        confirmSpec.and() >> clonedBuilder
        clonedBuilder.build() >> confirmFlow

        when:
        boolean success = step.execute(result)

        then:
        success
        result.mcpServers().isEmpty()
    }

    def "manual mode adds stdio MCP server"() {
        given:
        def result = new OnboardResult()
        result.setFlowMode(OnboardResult.FlowMode.MANUAL)

        // First iteration: confirm yes
        def confirmYesContext = new BaseComponentContext()
        confirmYesContext.put("add-mcp", true)
        def confirmYesResult = Mock(ComponentFlow.ComponentFlowResult) {
            getContext() >> confirmYesContext
        }
        def confirmYesFlow = Mock(ComponentFlow) {
            run() >> confirmYesResult
        }

        // Second iteration: confirm no
        def confirmNoContext = new BaseComponentContext()
        confirmNoContext.put("add-mcp", false)
        def confirmNoResult = Mock(ComponentFlow.ComponentFlowResult) {
            getContext() >> confirmNoContext
        }
        def confirmNoFlow = Mock(ComponentFlow) {
            run() >> confirmNoResult
        }

        // Details flow
        def detailsContext = new BaseComponentContext()
        detailsContext.put("mcp-name", "filesystem")
        detailsContext.put("mcp-description", "File access")
        detailsContext.put("mcp-transport", "stdio")
        def detailsResult = Mock(ComponentFlow.ComponentFlowResult) {
            getContext() >> detailsContext
        }
        def detailsFlow = Mock(ComponentFlow) {
            run() >> detailsResult
        }

        // Command flow
        def cmdContext = new BaseComponentContext()
        cmdContext.put("mcp-command", "npx @modelcontextprotocol/server-filesystem /tmp")
        def cmdResult = Mock(ComponentFlow.ComponentFlowResult) {
            getContext() >> cmdContext
        }
        def cmdFlow = Mock(ComponentFlow) {
            run() >> cmdResult
        }

        // Mock builder chains — 4 calls total (confirm yes, details, command, confirm no)
        def b1 = Mock(ComponentFlow.Builder) // confirm yes
        def b2 = Mock(ComponentFlow.Builder) // details
        def b3 = Mock(ComponentFlow.Builder) // command
        def b4 = Mock(ComponentFlow.Builder) // confirm no

        flowBuilder.clone() >>> [b1, b2, b3, b4]

        // Confirm yes chain
        def confirmSpec1 = Mock(ConfirmationInputSpec)
        b1.reset() >> b1
        b1.withConfirmationInput("add-mcp") >> confirmSpec1
        confirmSpec1.name(_) >> confirmSpec1
        confirmSpec1.defaultValue(_) >> confirmSpec1
        confirmSpec1.and() >> b1
        b1.build() >> confirmYesFlow

        // Details chain
        def stringInput1 = Mock(StringInputSpec)
        def stringInput2 = Mock(StringInputSpec)
        def selectorSpec = Mock(SingleItemSelectorSpec)
        b2.reset() >> b2
        b2.withStringInput("mcp-name") >> stringInput1
        stringInput1.name(_) >> stringInput1
        stringInput1.and() >> b2
        b2.withStringInput("mcp-description") >> stringInput2
        stringInput2.name(_) >> stringInput2
        stringInput2.defaultValue(_) >> stringInput2
        stringInput2.and() >> b2
        b2.withSingleItemSelector("mcp-transport") >> selectorSpec
        selectorSpec.name(_) >> selectorSpec
        selectorSpec.selectItem(_, _) >> selectorSpec
        selectorSpec.and() >> b2
        b2.build() >> detailsFlow

        // Command chain
        def stringInput3 = Mock(StringInputSpec)
        b3.reset() >> b3
        b3.withStringInput("mcp-command") >> stringInput3
        stringInput3.name(_) >> stringInput3
        stringInput3.and() >> b3
        b3.build() >> cmdFlow

        // Confirm no chain
        def confirmSpec2 = Mock(ConfirmationInputSpec)
        b4.reset() >> b4
        b4.withConfirmationInput("add-mcp") >> confirmSpec2
        confirmSpec2.name(_) >> confirmSpec2
        confirmSpec2.defaultValue(_) >> confirmSpec2
        confirmSpec2.and() >> b4
        b4.build() >> confirmNoFlow

        when:
        boolean success = step.execute(result)

        then:
        success
        result.mcpServers().size() == 1
        def server = result.mcpServers()[0]
        server.name() == "filesystem"
        server.description() == "File access"
        server.transportType() == OnboardResult.McpTransportType.STDIO
        server.command() == "npx"
        server.args() == ["@modelcontextprotocol/server-filesystem", "/tmp"]
    }
}
