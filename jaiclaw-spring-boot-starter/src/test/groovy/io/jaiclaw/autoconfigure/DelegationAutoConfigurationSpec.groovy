package io.jaiclaw.autoconfigure

import io.jaiclaw.agent.delegation.SubAgentLauncher
import io.jaiclaw.agent.delegation.tool.DelegateStatusTool
import io.jaiclaw.agent.delegation.tool.DelegateTaskTool
import io.jaiclaw.config.DelegationProperties
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import spock.lang.Specification

import java.lang.reflect.Method

/**
 * Delegation must be off unless explicitly enabled. A full ApplicationContext
 * test would need a ChatModel and the whole agent stack; these assertions pin the
 * gating contract itself, which is what actually decides whether a model can
 * reach delegate_task.
 */
class DelegationAutoConfigurationSpec extends Specification {

    private static Method bean(String name) {
        JaiClawAgentAutoConfiguration.declaredMethods.find { it.name == name }
    }

    def "the launcher is gated on jaiclaw.agent.delegation.enabled=true"() {
        given:
        Method m = bean("subAgentLauncher")

        expect: "no property, no launcher — and therefore no delegation tools"
        m != null
        with(m.getAnnotation(ConditionalOnProperty)) {
            prefix() == "jaiclaw.agent.delegation"
            name() == ["enabled"] as String[]
            havingValue() == "true"
            // matchIfMissing defaults to false: absent property means disabled.
            !matchIfMissing()
        }
    }

    def "both tools are gated on the launcher bean existing"() {
        expect:
        with(bean(method).getAnnotation(ConditionalOnBean)) {
            value() == [SubAgentLauncher] as Class[]
        }

        where:
        method << ["delegateTaskTool", "delegateStatusTool"]
    }

    def "the tool beans are declared with their concrete types"() {
        expect:
        bean("delegateTaskTool").returnType == DelegateTaskTool
        bean("delegateStatusTool").returnType == DelegateStatusTool
    }

    def "properties bean is always present so operators can inspect the policy"() {
        given:
        Method m = bean("delegationProperties")

        expect: "not gated — readable even while delegation is off"
        m != null
        m.getAnnotation(ConditionalOnProperty) == null
        m.returnType == DelegationProperties
    }

    def "delegation defaults to disabled with conservative limits"() {
        when:
        def defaults = DelegationProperties.defaults()

        then:
        !defaults.enabled()
        defaults.maxDepth() == 2
        defaults.maxConcurrent() == 4
        defaults.childMaxIterations() == 50
    }
}
