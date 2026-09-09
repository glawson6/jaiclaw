package io.jaiclaw.core.agent

import spock.lang.Specification

class ToolLoopConfigSpec extends Specification {

    def "the legacy three-arg constructor keeps today's behaviour"() {
        when:
        def config = new ToolLoopConfig(ToolLoopConfig.Mode.EXPLICIT, 10, false)

        then: "guards default to neutral values"
        config.maxIterations() == 10
        config.budgetTemplate().size() == 10
        config.budgetWarningRatio() == ToolLoopConfig.DEFAULT_WARNING_RATIO
        config.repetitionThreshold() == ToolLoopConfig.DEFAULT_REPETITION_THRESHOLD
        config.approvalFloors().isEmpty()
        config.floorFor("anything") == ApprovalFloor.NONE
    }

    def "the budget template defaults to maxIterations"() {
        expect:
        new ToolLoopConfig(ToolLoopConfig.Mode.EXPLICIT, 7, false).newRunBudget().size() == 7
    }

    def "newRunBudget hands out independent counters"() {
        given:
        def config = new ToolLoopConfig(ToolLoopConfig.Mode.EXPLICIT, 5, false)

        when:
        def a = config.newRunBudget()
        def b = config.newRunBudget()
        a.tryConsume()

        then:
        a.remaining() == 4
        b.remaining() == 5
    }

    def "invalid warning ratios fall back to the default"() {
        expect:
        new ToolLoopConfig(ToolLoopConfig.Mode.EXPLICIT, 5, false, null, ratio, 3, [:])
                .budgetWarningRatio() == ToolLoopConfig.DEFAULT_WARNING_RATIO

        where:
        ratio << [0.0d, -1.0d, 1.5d]
    }

    def "a zero repetition threshold disables the guard but is preserved"() {
        given:
        def config = new ToolLoopConfig(ToolLoopConfig.Mode.EXPLICIT, 5, false, null, 0.9d, 0, [:])

        expect:
        config.repetitionThreshold() == 0
        !config.repetitionGuardEnabled()
    }

    def "approval floors are exposed per tool and defensively copied"() {
        given:
        def floors = ["shell_exec": ApprovalFloor.PROMPT_ALWAYS, "rm_rf": ApprovalFloor.DENY]
        def config = new ToolLoopConfig(ToolLoopConfig.Mode.EXPLICIT, 5, false, null, 0.9d, 3, floors)

        when: "the caller mutates the map it passed in"
        floors.put("web_fetch", ApprovalFloor.DENY)

        then: "the config is unaffected"
        config.floorFor("shell_exec") == ApprovalFloor.PROMPT_ALWAYS
        config.floorFor("rm_rf") == ApprovalFloor.DENY
        config.floorFor("web_fetch") == ApprovalFloor.NONE
        config.floorFor(null) == ApprovalFloor.NONE
    }

    def "DEFAULT is unchanged from the pre-1.2.0 shape"() {
        expect:
        ToolLoopConfig.DEFAULT.mode() == ToolLoopConfig.Mode.SPRING_AI
        ToolLoopConfig.DEFAULT.maxIterations() == 25
        !ToolLoopConfig.DEFAULT.requireApproval()
    }
}
