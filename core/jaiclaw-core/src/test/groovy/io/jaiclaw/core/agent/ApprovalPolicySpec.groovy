package io.jaiclaw.core.agent

import spock.lang.Specification

import java.time.Duration

/**
 * Resolution rules for the approval window and the timeout action.
 *
 * <p>The invariant worth protecting: {@code on-timeout} is configurable either
 * way, but resolves to {@link ApprovalPolicy.OnTimeout#DENY} unless an operator
 * typed {@code approve} for that specific scope. Auto-approval on silence must
 * never arrive by inheritance, omission, or a null.
 */
class ApprovalPolicySpec extends Specification {

    def "the framework default denies on silence"() {
        expect:
        ApprovalPolicy.DEFAULT.onTimeoutFor("anything") == ApprovalPolicy.OnTimeout.DENY
        !ApprovalPolicy.DEFAULT.autoApprove()
        ApprovalPolicy.DEFAULT.defaultTimeout() == Duration.ofMinutes(5)
    }

    def "nulls coalesce to deny and the default window, never to approve"() {
        when: "absent YAML binds every field as null"
        def policy = new ApprovalPolicy(false, null, null, null)

        then:
        policy.onTimeoutFor("anything") == ApprovalPolicy.OnTimeout.DENY
        policy.defaultTimeout() == ApprovalPolicy.DEFAULT_TIMEOUT
        policy.tools().isEmpty()
    }

    def "a non-positive default timeout falls back rather than expiring instantly"() {
        expect: "zero or negative would otherwise deny every call the moment it is asked"
        new ApprovalPolicy(false, window, null, null).defaultTimeout() == ApprovalPolicy.DEFAULT_TIMEOUT

        where:
        window << [Duration.ZERO, Duration.ofSeconds(-30)]
    }

    def "an explicit per-tool approve is honoured"() {
        given:
        def policy = new ApprovalPolicy(false, Duration.ofMinutes(5),
                ApprovalPolicy.OnTimeout.DENY,
                ["harmless": new ApprovalPolicy.ToolApprovalPolicy(
                        Duration.ofMinutes(1), ApprovalPolicy.OnTimeout.APPROVE)])

        expect:
        policy.onTimeoutFor("harmless") == ApprovalPolicy.OnTimeout.APPROVE
    }

    def "a per-tool approve does not leak to sibling tools"() {
        given: "one tool opted in to auto-approval on silence"
        def policy = new ApprovalPolicy(false, Duration.ofMinutes(5),
                ApprovalPolicy.OnTimeout.DENY,
                ["harmless": new ApprovalPolicy.ToolApprovalPolicy(
                        null, ApprovalPolicy.OnTimeout.APPROVE)])

        expect: "every other tool still denies — opt-in is per tool, not a mode"
        policy.onTimeoutFor("shell_exec") == ApprovalPolicy.OnTimeout.DENY
        policy.onTimeoutFor("dropDatabase") == ApprovalPolicy.OnTimeout.DENY
        policy.onTimeoutFor(null) == ApprovalPolicy.OnTimeout.DENY
    }

    def "setting only a per-tool timeout does not opt that tool into approval"() {
        given: "the most likely operator mistake — a short window, no action stated"
        def policy = new ApprovalPolicy(false, Duration.ofMinutes(30),
                ApprovalPolicy.OnTimeout.DENY,
                ["shell_exec": new ApprovalPolicy.ToolApprovalPolicy(
                        Duration.ofMinutes(2), null)])

        expect: "the window narrows but silence still denies"
        policy.timeoutFor("shell_exec") == Duration.ofMinutes(2)
        policy.onTimeoutFor("shell_exec") == ApprovalPolicy.OnTimeout.DENY
    }

    def "a deployment-wide approve is possible but must be stated"() {
        given:
        def policy = new ApprovalPolicy(false, Duration.ofMinutes(5),
                ApprovalPolicy.OnTimeout.APPROVE, [:])

        expect: "available for unattended runs, but never reachable by omission"
        policy.onTimeoutFor("anything") == ApprovalPolicy.OnTimeout.APPROVE
    }

    def "an unlisted tool inherits the policy window"() {
        given:
        def policy = new ApprovalPolicy(false, Duration.ofMinutes(10),
                ApprovalPolicy.OnTimeout.DENY,
                ["shell_exec": new ApprovalPolicy.ToolApprovalPolicy(Duration.ofMinutes(2), null)])

        expect:
        policy.timeoutFor("web_fetch") == Duration.ofMinutes(10)
        policy.timeoutFor(null) == Duration.ofMinutes(10)
    }

    def "a non-positive per-tool timeout falls back to the policy window"() {
        given:
        def policy = new ApprovalPolicy(false, Duration.ofMinutes(10),
                ApprovalPolicy.OnTimeout.DENY,
                ["shell_exec": new ApprovalPolicy.ToolApprovalPolicy(Duration.ZERO, null)])

        expect:
        policy.timeoutFor("shell_exec") == Duration.ofMinutes(10)
    }

    def "the tools map is defensively copied"() {
        given:
        def mutable = ["a": new ApprovalPolicy.ToolApprovalPolicy(Duration.ofMinutes(1), null)]
        def policy = new ApprovalPolicy(false, Duration.ofMinutes(5),
                ApprovalPolicy.OnTimeout.DENY, mutable)

        when: "a caller mutates the map it handed in"
        mutable["b"] = new ApprovalPolicy.ToolApprovalPolicy(null, ApprovalPolicy.OnTimeout.APPROVE)

        then: "the policy is unaffected — approval config cannot be changed after construction"
        policy.tools().size() == 1
        policy.onTimeoutFor("b") == ApprovalPolicy.OnTimeout.DENY
    }
}
