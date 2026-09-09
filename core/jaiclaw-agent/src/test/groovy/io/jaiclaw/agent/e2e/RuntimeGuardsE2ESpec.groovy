package io.jaiclaw.agent.e2e

import io.jaiclaw.agent.loop.ExplicitToolLoop
import io.jaiclaw.core.agent.AgentHookDispatcher
import io.jaiclaw.core.agent.ApprovalFloor
import io.jaiclaw.core.agent.IterationBudget
import io.jaiclaw.core.agent.ToolApprovalDecision
import io.jaiclaw.core.agent.ToolApprovalHandler
import io.jaiclaw.core.agent.ToolLoopConfig
import io.jaiclaw.core.hook.event.BudgetWarningEvent
import io.jaiclaw.core.hook.event.RepetitionDetectedEvent
import io.jaiclaw.core.ops.EmergencyStop
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.model.Generation
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.tool.ToolCallback
import org.springframework.ai.tool.definition.DefaultToolDefinition
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicInteger

/**
 * §5.2 row 1 of the 1.2.0 plan — the runtime guards, exercised through the real
 * tool loop rather than in isolation.
 *
 * Uses a scripted ChatModel (no network) so the assertions are about the guards,
 * not about a provider's behaviour.
 */
class RuntimeGuardsE2ESpec extends Specification {

    @TempDir
    Path tmp

    ChatModel chatModel = Mock()
    AgentHookDispatcher hooks = Mock()

    private AssistantMessage text(String t) {
        AssistantMessage.builder().content(t).build()
    }

    private AssistantMessage callsTool(String name, String args) {
        AssistantMessage.builder().content("")
                .toolCalls([new AssistantMessage.ToolCall("tc-1", "function", name, args)])
                .build()
    }

    private ChatResponse resp(AssistantMessage m) {
        new ChatResponse(List.of(new Generation(m)))
    }

    private ToolCallback tool(String name, AtomicInteger counter = null, String result = "ok") {
        def def_ = DefaultToolDefinition.builder()
                .name(name).description("test tool").inputSchema('{"type":"object"}').build()
        Mock(ToolCallback) {
            getToolDefinition() >> def_
            call(_) >> { counter?.incrementAndGet(); result }
        }
    }

    // ── Budget ───────────────────────────────────────────────────────────────

    def "budget exhaustion ends the run with a final summary turn, not a placeholder"() {
        given: "a 2-iteration budget and a model that would loop forever"
        def config = new ToolLoopConfig(ToolLoopConfig.Mode.EXPLICIT, 2, false,
                IterationBudget.of(2), 0.9d, 0, [:])
        def loop = new ExplicitToolLoop(chatModel, config, hooks, null)
        def calls = new AtomicInteger()

        chatModel.call(_ as Prompt) >>> [
                resp(callsTool("search", '{"q":"a"}')),
                resp(callsTool("search", '{"q":"b"}')),
                resp(text("I found two results before running out of budget."))
        ]

        when:
        def result = loop.execute("sys", [], "go", ["search": tool("search", calls)], "agent", "sess")

        then: "the model got a chance to summarise"
        result.finalText() == "I found two results before running out of budget."
        result.iterationsUsed() == 2
        calls.get() == 2
    }

    def "the model is warned once, in a tool result, as the budget runs low"() {
        given: "a 2-iteration budget warning at 50% — so the warning lands on iteration 1"
        def config = new ToolLoopConfig(ToolLoopConfig.Mode.EXPLICIT, 2, false,
                IterationBudget.of(2), 0.5d, 0, [:])
        def loop = new ExplicitToolLoop(chatModel, config, hooks, null)

        chatModel.call(_ as Prompt) >>> [
                resp(callsTool("search", '{"q":"a"}')),
                resp(text("done"))
        ]

        when:
        def result = loop.execute("sys", [], "go", ["search": tool("search")], "agent", "sess")

        then: "the budget notice was appended to the tool result the model saw"
        result.history().any { it.result()?.contains("[budget]") }

        and: "and surfaced as an event exactly once"
        1 * hooks.fireVoid({ it instanceof BudgetWarningEvent })
    }

    // ── Repetition ───────────────────────────────────────────────────────────

    def "three identical consecutive calls trip the repetition guard"() {
        given: "a model that asks for the same thing over and over"
        def config = new ToolLoopConfig(ToolLoopConfig.Mode.EXPLICIT, 10, false,
                IterationBudget.of(10), 0.9d, 3, [:])
        def loop = new ExplicitToolLoop(chatModel, config, hooks, null)
        def calls = new AtomicInteger()

        def stuck = resp(callsTool("search", '{"q":"same"}'))
        chatModel.call(_ as Prompt) >>> [stuck, stuck, stuck, stuck, stuck, resp(text("fine, stopping"))]

        when:
        def result = loop.execute("sys", [], "go", ["search": tool("search", calls)], "agent", "sess")

        then: "the third identical call is answered with a correction rather than executed"
        result.history().any { it.result()?.contains("Repeated call detected") }
        1 * hooks.fireVoid({ it instanceof RepetitionDetectedEvent && !it.forcedFinal() })

        and: "the tool was not executed for the corrected call"
        calls.get() < 5
    }

    def "different arguments reset the repetition run"() {
        given:
        def config = new ToolLoopConfig(ToolLoopConfig.Mode.EXPLICIT, 10, false,
                IterationBudget.of(10), 0.9d, 3, [:])
        def loop = new ExplicitToolLoop(chatModel, config, hooks, null)

        chatModel.call(_ as Prompt) >>> [
                resp(callsTool("search", '{"q":"a"}')),
                resp(callsTool("search", '{"q":"a"}')),
                resp(callsTool("search", '{"q":"b"}')),   // resets
                resp(callsTool("search", '{"q":"a"}')),
                resp(text("done"))
        ]

        when:
        def result = loop.execute("sys", [], "go", ["search": tool("search")], "agent", "sess")

        then: "exploring with varied parameters is never treated as looping"
        result.finalText() == "done"
        !result.history().any { it.result()?.contains("Repeated call detected") }
        0 * hooks.fireVoid({ it instanceof RepetitionDetectedEvent })
    }

    // ── Empty responses ──────────────────────────────────────────────────────

    def "two consecutive empty responses force a final turn"() {
        given:
        def config = new ToolLoopConfig(ToolLoopConfig.Mode.EXPLICIT, 10, false,
                IterationBudget.of(10), 0.9d, 3, [:])
        def loop = new ExplicitToolLoop(chatModel, config, hooks, null)

        chatModel.call(_ as Prompt) >>> [
                resp(text("")),
                resp(text("")),
                resp(text("Sorry — here is the answer."))
        ]

        when:
        def result = loop.execute("sys", [], "go", [:], "agent", "sess")

        then: "the run recovers instead of spinning until the budget drains"
        result.finalText() == "Sorry — here is the answer."
    }

    // ── Approval floors ──────────────────────────────────────────────────────

    def "a PROMPT_ALWAYS floor forces approval even when the session does not require it"() {
        given: "requireApproval=false, but shell_exec carries a floor"
        def handler = Mock(ToolApprovalHandler)
        def config = new ToolLoopConfig(ToolLoopConfig.Mode.EXPLICIT, 5, false,
                IterationBudget.of(5), 0.9d, 3,
                ["shell_exec": ApprovalFloor.PROMPT_ALWAYS])
        def loop = new ExplicitToolLoop(chatModel, config, hooks, handler)

        chatModel.call(_ as Prompt) >>> [
                resp(callsTool("shell_exec", '{"cmd":"ls"}')),
                resp(text("done"))
        ]

        when:
        loop.execute("sys", [], "go", ["shell_exec": tool("shell_exec")], "agent", "sess")

        then: "the handler is consulted despite requireApproval being false"
        1 * handler.requestApproval("shell_exec", _, "sess") >>
                CompletableFuture.completedFuture(new ToolApprovalDecision.Approved())
    }

    def "a DENY floor refuses without ever asking the approval handler"() {
        given:
        def handler = Mock(ToolApprovalHandler)
        def config = new ToolLoopConfig(ToolLoopConfig.Mode.EXPLICIT, 5, true,
                IterationBudget.of(5), 0.9d, 3,
                ["rm_rf": ApprovalFloor.DENY])
        def loop = new ExplicitToolLoop(chatModel, config, hooks, handler)
        def calls = new AtomicInteger()

        chatModel.call(_ as Prompt) >>> [
                resp(callsTool("rm_rf", '{"path":"/"}')),
                resp(text("understood, I will not do that"))
        ]

        when:
        def result = loop.execute("sys", [], "go", ["rm_rf": tool("rm_rf", calls)], "agent", "sess")

        then: "no approval round-trip, no execution, and the model is told why"
        0 * handler.requestApproval(*_)
        calls.get() == 0
        result.history().any { it.result()?.contains("approval floor") }

        and: "the run continues rather than failing — the model can choose another path"
        result.finalText() == "understood, I will not do that"
    }

    // ── Emergency stop ───────────────────────────────────────────────────────

    def "an in-flight run completes even though the stop is engaged mid-run"() {
        given: "the stop is engaged while the loop is already executing"
        def estop = new EmergencyStop(tmp.resolve("ESTOP"))
        def config = new ToolLoopConfig(ToolLoopConfig.Mode.EXPLICIT, 5, false,
                IterationBudget.of(5), 0.9d, 0, [:])
        def loop = new ExplicitToolLoop(chatModel, config, hooks, null)

        def engagingTool = Mock(ToolCallback) {
            getToolDefinition() >> DefaultToolDefinition.builder()
                    .name("slow").description("engages the stop mid-run")
                    .inputSchema('{"type":"object"}').build()
            call(_) >> { estop.engage("engaged mid-run"); "done" }
        }

        chatModel.call(_ as Prompt) >>> [
                resp(callsTool("slow", '{}')),
                resp(text("finished despite the pause"))
        ]

        when:
        def result = loop.execute("sys", [], "go", ["slow": engagingTool], "agent", "sess")

        then: "ESTOP pauses NEW work; it never kills a turn already running"
        estop.isEngaged()
        result.finalText() == "finished despite the pause"
    }
}
