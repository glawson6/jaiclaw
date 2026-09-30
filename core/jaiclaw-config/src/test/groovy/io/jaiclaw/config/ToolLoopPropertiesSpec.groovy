package io.jaiclaw.config

import io.jaiclaw.core.agent.ApprovalFloor
import io.jaiclaw.core.agent.ToolLoopConfig
import spock.lang.Specification

class ToolLoopPropertiesSpec extends Specification {

    // ToolLoopProperties is bound by Spring Boot's constructor binder, so it is
    // allowed exactly one public constructor (see the record's javadoc). Adding
    // the approval block therefore widens the canonical constructor rather than
    // adding an overload, and these call sites pass the default explicitly.
    private static final ToolLoopProperties.ApprovalProperties APPROVAL_DEFAULTS =
            ToolLoopProperties.ApprovalProperties.defaults()

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
        def props = new ToolLoopProperties("explicit", 12, false, 0, 0.9d, 3, [:], APPROVAL_DEFAULTS)

        expect:
        props.toConfig().budgetTemplate().size() == 12
    }

    def "an explicit budget overrides maxIterations for the run counter"() {
        given: "a child-style config: hard cap 50, but only 10 iterations budgeted"
        def props = new ToolLoopProperties("explicit", 50, false, 10, 0.9d, 3, [:], APPROVAL_DEFAULTS)

        when:
        def config = props.toConfig()

        then:
        config.maxIterations() == 50
        config.budgetTemplate().size() == 10
    }

    def "mode maps to the ToolLoopConfig enum, defaulting to spring-ai"() {
        expect:
        new ToolLoopProperties(mode, 5, false, 0, 0.9d, 3, [:], APPROVAL_DEFAULTS).toConfig().mode() == expected

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
                ["shell_exec": ApprovalFloor.PROMPT_ALWAYS], APPROVAL_DEFAULTS)

        expect:
        props.toConfig().floorFor("shell_exec") == ApprovalFloor.PROMPT_ALWAYS
        props.toConfig().floorFor("web_fetch") == ApprovalFloor.NONE
    }

    def "exactly one public constructor — the Boot 4 record-binder rule"() {
        when: "counting public constructors on this @ConfigurationProperties record"
        def publicCtors = ToolLoopProperties.getConstructors()

        then: "an overload would make Boot's Instantiator pick by parameter count and drop nested YAML"
        publicCtors.length == 1

        and: "8 components since 1.3.0 added the nested approval block"
        publicCtors[0].parameterCount == 8
    }

    def "the nested approval record follows the same one-constructor rule"() {
        when: "ApprovalProperties is itself bound from YAML, so the rule applies to it too"
        def publicCtors = ToolLoopProperties.ApprovalProperties.getConstructors()

        then:
        publicCtors.length == 1
        publicCtors[0].parameterCount == 4
    }

    def "approval defaults are inert: no auto-approve, 5m window, deny on silence"() {
        when:
        def policy = ToolLoopProperties.defaults().toConfig().approvalPolicy()

        then:
        !policy.autoApprove()
        policy.defaultTimeout() == java.time.Duration.ofMinutes(5)
        policy.onTimeoutFor("anything") == io.jaiclaw.core.agent.ApprovalPolicy.OnTimeout.DENY
    }

    def "per-tool approval overrides reach the resulting ToolLoopConfig"() {
        given:
        def approval = new ToolLoopProperties.ApprovalProperties(
                false, java.time.Duration.ofMinutes(10),
                io.jaiclaw.core.agent.ApprovalPolicy.OnTimeout.DENY,
                ["shell_exec": new ToolLoopProperties.ApprovalProperties.ToolApproval(
                        java.time.Duration.ofMinutes(2), null)])
        def props = new ToolLoopProperties("explicit", 25, false, 0, 0.9d, 3, [:], approval)

        when:
        def policy = props.toConfig().approvalPolicy()

        then: "the per-tool window applies"
        policy.timeoutFor("shell_exec") == java.time.Duration.ofMinutes(2)

        and: "an unlisted tool inherits the policy default"
        policy.timeoutFor("web_fetch") == java.time.Duration.ofMinutes(10)

        and: "a null per-tool on-timeout inherits too"
        policy.onTimeoutFor("shell_exec") == io.jaiclaw.core.agent.ApprovalPolicy.OnTimeout.DENY
    }
}
