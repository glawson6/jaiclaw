package io.jaiclaw.pipeline.processors.integration

import io.jaiclaw.core.tool.ToolCallback
import io.jaiclaw.core.tool.ToolContext
import io.jaiclaw.core.tool.ToolResult
import io.jaiclaw.pipeline.PipelineContext
import io.jaiclaw.pipeline.StageDefinition
import io.jaiclaw.pipeline.StageType
import io.jaiclaw.tools.ToolRegistry
import org.apache.camel.Exchange
import org.apache.camel.Message
import spock.lang.Specification

class ToolStageProcessorSpec extends Specification {

    ToolRegistry registry = new ToolRegistry()
    Exchange exchange = Mock()
    Message message = Mock()
    PipelineContext ctx = new PipelineContext(
            "pipe", "exec", null, null, 0, 1, null, null,
            [:] as Map, ["__input__": "hello"] as Map)
    StageDefinition stage = new StageDefinition(
            "s", StageType.PROCESSOR, "b", null, null, null, null, null, null)

    def setup() { exchange.getIn() >> message }

    def "invokes the resolved tool with parsed args and sets Success content on the exchange"() {
        given:
        ToolCallback callback = Mock()
        callback.definition() >> new io.jaiclaw.core.tool.ToolDefinition(
                "greet", "hi", "{}")
        registry.register(callback)
        ToolStageProcessor p = new ToolStageProcessor(registry)

        when:
        p.process(exchange, stage, ctx,
                [tool: "greet", args: '{"name":"alice"}'])

        then:
        1 * callback.execute({ Map m -> m.name == "alice" }, _ as ToolContext) >>
                new ToolResult.Success("hi alice", [:] as Map)
        1 * message.setBody("hi alice")
    }

    def "template-renders args before parsing as JSON"() {
        given:
        ToolCallback callback = Mock()
        callback.definition() >> new io.jaiclaw.core.tool.ToolDefinition("t", "", "{}")
        registry.register(callback)
        ToolStageProcessor p = new ToolStageProcessor(registry)

        when:
        p.process(exchange, stage, ctx,
                [tool: "t", args: '{"greeting":"{{input}}"}'])

        then:
        1 * callback.execute({ Map m -> m.greeting == "hello" }, _) >>
                new ToolResult.Success("ok", [:] as Map)
    }

    def "blank args map to an empty argument map"() {
        given:
        ToolCallback callback = Mock()
        callback.definition() >> new io.jaiclaw.core.tool.ToolDefinition("t", "", "{}")
        registry.register(callback)
        ToolStageProcessor p = new ToolStageProcessor(registry)

        when:
        p.process(exchange, stage, ctx, [tool: "t"])

        then:
        1 * callback.execute({ Map m -> m.isEmpty() }, _) >>
                new ToolResult.Success("done", [:] as Map)
    }

    def "unknown tool throws"() {
        given:
        ToolStageProcessor p = new ToolStageProcessor(registry)

        when:
        p.process(exchange, stage, ctx, [tool: "does-not-exist"])

        then:
        thrown(IllegalArgumentException)
    }

    def "Error result trips the pipeline via IllegalStateException"() {
        given:
        ToolCallback callback = Mock()
        callback.definition() >> new io.jaiclaw.core.tool.ToolDefinition("t", "", "{}")
        registry.register(callback)
        ToolStageProcessor p = new ToolStageProcessor(registry)

        when:
        p.process(exchange, stage, ctx, [tool: "t"])

        then:
        1 * callback.execute(_, _) >> new ToolResult.Error("boom", null)
        thrown(IllegalStateException)
    }
}
