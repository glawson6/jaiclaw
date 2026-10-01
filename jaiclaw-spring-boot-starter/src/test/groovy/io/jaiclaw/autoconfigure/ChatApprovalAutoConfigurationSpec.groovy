package io.jaiclaw.autoconfigure

import io.jaiclaw.core.agent.ApprovalPolicy
import io.jaiclaw.core.agent.PendingApprovalRegistry
import io.jaiclaw.core.agent.ToolApprovalHandler
import io.jaiclaw.gateway.approval.ApprovalReplyFilter
import io.jaiclaw.gateway.approval.ApproverResolver
import io.jaiclaw.gateway.approval.ChatApprovalProperties
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.core.annotation.Order
import spock.lang.Specification

import java.lang.reflect.Method

/**
 * Chat approval must be off unless explicitly enabled, and its filter must
 * order after authorization.
 *
 * <p>Follows the reflection idiom used by {@link DelegationAutoConfigurationSpec}:
 * a full ApplicationContext test would need a ChatModel and the whole agent
 * stack, while these assertions pin the gating contract itself — which is what
 * decides whether tool calls start requiring a human.
 */
class ChatApprovalAutoConfigurationSpec extends Specification {

    private static Class<?> config() {
        JaiClawGatewayAutoConfiguration.declaredClasses.find {
            it.simpleName == "ChatApprovalConfiguration"
        }
    }

    private static Method bean(String name) {
        config().declaredMethods.find { it.name == name }
    }

    def "the configuration exists and is gated on jaiclaw.approval.chat.enabled=true"() {
        given:
        Class<?> c = config()

        expect:
        c != null

        and: "opt-in — registering a handler changes how every PROMPT_ALWAYS tool behaves"
        with(c.getAnnotation(ConditionalOnProperty)) {
            name() == ["jaiclaw.approval.chat.enabled"] as String[]
            havingValue() == "true"
            // Absent property means disabled: a handler must never arrive by upgrade.
            !matchIfMissing()
        }
    }

    def "it binds ChatApprovalProperties"() {
        expect:
        config().getAnnotation(EnableConfigurationProperties)
                .value().toList().contains(ChatApprovalProperties)
    }

    def "all four beans are declared"() {
        expect:
        ["pendingApprovalRegistry", "approverResolver",
         "chatApprovalHandler", "approvalReplyFilter"].every { bean(it) != null }
    }

    def "the handler is published as the ToolApprovalHandler SPI type"() {
        given:
        Method m = bean("chatApprovalHandler")

        expect: "so ExplicitToolLoop picks it up without knowing about chat"
        m.returnType == ToolApprovalHandler

        and: "an adopter's own handler wins"
        m.getAnnotation(ConditionalOnMissingBean).value().toList().contains(ToolApprovalHandler)
    }

    def "the reply filter orders after authorization filters"() {
        given:
        Method m = bean("approvalReplyFilter")

        expect: "TelegramUserIdFilter is 100; no point matching a reply from a blocked peer"
        m.getAnnotation(Order).value() == 200
        m.returnType == ApprovalReplyFilter
    }

    def "every bean backs off when an adopter supplies their own"() {
        expect:
        bean(name).getAnnotation(ConditionalOnMissingBean) != null

        where:
        name << ["pendingApprovalRegistry", "approverResolver",
                 "chatApprovalHandler", "approvalReplyFilter"]
    }

    def "the resolver is published as the SPI type so a custom rota can replace it"() {
        given:
        Method m = bean("approverResolver")

        expect:
        m.returnType == ApproverResolver
        m.getAnnotation(ConditionalOnMissingBean).value().toList().contains(ApproverResolver)
    }

    // --- properties contract ---

    def "properties default to disabled with nobody configured"() {
        when:
        def props = ChatApprovalProperties.defaults()

        then:
        !props.enabled()
        props.approvers().isEmpty()
        props.toApprovers().isEmpty()
    }

    def "incomplete approver entries are dropped rather than carried forward"() {
        given: "entries that cannot be messaged"
        def props = new ChatApprovalProperties(true, [
                new ChatApprovalProperties.ApproverConfig("telegram", "acct", "9001"),
                new ChatApprovalProperties.ApproverConfig(null, "acct", "9002"),
                new ChatApprovalProperties.ApproverConfig("telegram", "acct", "   "),
                new ChatApprovalProperties.ApproverConfig("telegram", "acct", null),
        ])

        when:
        def approvers = props.toApprovers()

        then: "only the usable one survives, so the startup check sees the truth"
        approvers.size() == 1
        approvers[0].channelId() == "telegram"
        approvers[0].peerId() == "9001"
    }

    def "a null approver list binds as empty rather than NPE-ing"() {
        when:
        def props = new ChatApprovalProperties(true, null)

        then:
        props.approvers().isEmpty()
        props.toApprovers().isEmpty()
    }

    def "ChatApprovalProperties follows the Boot 4 one-public-constructor rule"() {
        expect: "an overload makes the Instantiator pick by arity and drop nested YAML"
        ChatApprovalProperties.constructors.length == 1
        ChatApprovalProperties.ApproverConfig.constructors.length == 1
    }

    def "approval timing is NOT in the chat properties"() {
        when: "timing is per-agent and per-tool; the approver is deployment-wide"
        def names = ChatApprovalProperties.recordComponents*.name

        then: "it lives at tool-loop.approval instead — see ApprovalPolicy"
        names == ["enabled", "approvers"]
        !names.contains("timeout")
        !names.contains("onTimeout")

        and: "and the policy default is still deny-on-silence"
        ApprovalPolicy.DEFAULT.onTimeoutFor("anything") == ApprovalPolicy.OnTimeout.DENY
    }
}
