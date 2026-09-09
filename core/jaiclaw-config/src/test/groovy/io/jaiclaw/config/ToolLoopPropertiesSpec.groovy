package io.jaiclaw.config

import io.jaiclaw.core.agent.ApprovalFloor
import io.jaiclaw.core.agent.ToolLoopConfig
import spock.lang.Specification

class ToolLoopPropertiesSpec extends Specification {

    def "defaults() matches the pre-1.2.0 behaviour"() {
        when:
        def props = ToolLoopProperties.defaults()

        then:
        props.mode() == "spring-ai"
        props.maxIterations() == 25
        !props.requireApproval()
        props.budgetMaxIterations() == 0
        props.repetitionThreshold() == ToolLoopConfig.DEFAULT_REPETITION_THRESHOLD
        props.approvalFloors().isEmpty()
    }

    def "budget falls back to maxIterations when not set independently"() {
        given:
        def props = new ToolLoopProperties("explicit", 12, false, 0, 0.9d, 3, [:])

        expect:
        props.toConfig().budgetTemplate().size() == 12
    }

    def "an explicit budget overrides maxIterations for the run counter"() {
        given: "a child-style config: hard cap 50, but only 10 iterations budgeted"
        def props = new ToolLoopProperties("explicit", 50, false, 10, 0.9d, 3, [:])

        when:
        def config = props.toConfig()

        then:
        config.maxIterations() == 50
        config.budgetTemplate().size() == 10
    }

    def "mode maps to the ToolLoopConfig enum, defaulting to spring-ai"() {
        expect:
        new ToolLoopProperties(mode, 5, false, 0, 0.9d, 3, [:]).toConfig().mode() == expected

        where:
        mode        | expected
        "explicit"  | ToolLoopConfig.Mode.EXPLICIT
        "EXPLICIT"  | ToolLoopConfig.Mode.EXPLICIT
        "spring-ai" | ToolLoopConfig.Mode.SPRING_AI
        "nonsense"  | ToolLoopConfig.Mode.SPRING_AI
        null        | ToolLoopConfig.Mode.SPRING_AI
    }

    def "approval floors reach the resulting ToolLoopConfig"() {
        given:
        def props = new ToolLoopProperties("explicit", 25, false, 0, 0.9d, 3,
                ["shell_exec": ApprovalFloor.PROMPT_ALWAYS])

        expect:
        props.toConfig().floorFor("shell_exec") == ApprovalFloor.PROMPT_ALWAYS
        props.toConfig().floorFor("web_fetch") == ApprovalFloor.NONE
    }

    def "exactly one public constructor — the Boot 4 record-binder rule"() {
        when: "counting public constructors on this @ConfigurationProperties record"
        def publicCtors = ToolLoopProperties.getConstructors()

        then: "an overload would make Boot's Instantiator pick by parameter count and drop nested YAML"
        publicCtors.length == 1
        publicCtors[0].parameterCount == 7
    }
}
