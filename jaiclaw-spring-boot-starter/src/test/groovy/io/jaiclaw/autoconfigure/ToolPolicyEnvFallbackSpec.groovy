package io.jaiclaw.autoconfigure

import io.jaiclaw.config.AgentProperties
import org.springframework.core.env.Environment
import spock.lang.Specification

/**
 * Tests the Environment-based fallback for {@code tools.allow} /
 * {@code tools.deny} / {@code tools.profile} resolution in
 * {@code JaiClawAgentAutoConfiguration}.
 *
 * <p>Mirrors the production logic in the same shape as
 * {@link SystemPromptBindingSpec} — when Spring Boot's record binding
 * partially fails for the {@code Map<String, AgentConfig>} shape, the
 * auto-config reads {@code profile}, {@code allow}, and {@code deny}
 * directly from {@link Environment}. Pre-0.9.1 it only read
 * {@code profile}; lists were silently dropped — see
 * {@code docs/issues/tool-allow-deny-env-fallback.md}.
 */
class ToolPolicyEnvFallbackSpec extends Specification {

    static final String AGENT_ID = "chat"
    static final String PREFIX = "jaiclaw.agent.agents.${AGENT_ID}.tools"

    Environment env = Mock(Environment)

    // ---- helpers mirroring the production code -----------------------

    /**
     * Mirror of {@code JaiClawAgentAutoConfiguration.readStringList} —
     * walks {@code prefix[0]}, {@code prefix[1]}, … until the first
     * {@code null} value.
     */
    private List<String> readStringList(String prefix) {
        List<String> result = []
        for (int i = 0; ; i++) {
            String value = env.getProperty(prefix + "[" + i + "]")
            if (value == null) break
            result << value
        }
        return result
    }

    /**
     * Mirror of {@code JaiClawAgentAutoConfiguration.resolveToolsFromEnvironment}
     * — see the production class for the canonical implementation.
     */
    private AgentProperties.ToolPolicyConfig resolveToolsFromEnvironment(AgentProperties.AgentConfig bound) {
        String envProfile = env.getProperty(PREFIX + ".profile")
        List<String> envAllow = readStringList(PREFIX + ".allow")
        List<String> envDeny = readStringList(PREFIX + ".deny")

        if (envProfile == null && envAllow.isEmpty() && envDeny.isEmpty()) {
            return null
        }
        if (envProfile != null
                && bound != null && bound.tools() != null
                && envProfile.equals(bound.tools().profile())
                && envAllow.isEmpty() && envDeny.isEmpty()) {
            return null
        }
        String effectiveProfile = envProfile != null
                ? envProfile
                : (bound != null && bound.tools() != null ? bound.tools().profile() : "coding")
        List<String> effectiveAllow = !envAllow.isEmpty()
                ? envAllow
                : (bound != null && bound.tools() != null ? bound.tools().allow() : [])
        List<String> effectiveDeny = !envDeny.isEmpty()
                ? envDeny
                : (bound != null && bound.tools() != null ? bound.tools().deny() : [])
        return new AgentProperties.ToolPolicyConfig(effectiveProfile, effectiveAllow, effectiveDeny)
    }

    private void stubAllow(List<String> values) {
        for (int i = 0; i < values.size(); i++) {
            env.getProperty(PREFIX + ".allow[" + i + "]") >> values[i]
        }
        env.getProperty(PREFIX + ".allow[" + values.size() + "]") >> null
    }

    private void stubDeny(List<String> values) {
        for (int i = 0; i < values.size(); i++) {
            env.getProperty(PREFIX + ".deny[" + i + "]") >> values[i]
        }
        env.getProperty(PREFIX + ".deny[" + values.size() + "]") >> null
    }

    // ---- cases ------------------------------------------------------

    def "profile only — returns config with empty allow/deny"() {
        given:
        env.getProperty(PREFIX + ".profile") >> "full"
        stubAllow([])
        stubDeny([])

        when:
        def config = resolveToolsFromEnvironment(null)

        then:
        config != null
        config.profile() == "full"
        config.allow() == []
        config.deny() == []
    }

    def "profile + indexed allow list — list flows through"() {
        given:
        env.getProperty(PREFIX + ".profile") >> "full"
        stubAllow(["extract_event", "create_event", "confirm_event"])
        stubDeny([])

        when:
        def config = resolveToolsFromEnvironment(null)

        then:
        config != null
        config.profile() == "full"
        config.allow() == ["extract_event", "create_event", "confirm_event"]
        config.deny() == []
    }

    def "profile + indexed deny list — list flows through"() {
        given:
        env.getProperty(PREFIX + ".profile") >> "full"
        stubAllow([])
        stubDeny(["shell_exec", "claude_cli"])

        when:
        def config = resolveToolsFromEnvironment(null)

        then:
        config != null
        config.profile() == "full"
        config.allow() == []
        config.deny() == ["shell_exec", "claude_cli"]
    }

    def "all three populated — all three flow through"() {
        given:
        env.getProperty(PREFIX + ".profile") >> "minimal"
        stubAllow(["echo"])
        stubDeny(["shell_exec"])

        when:
        def config = resolveToolsFromEnvironment(null)

        then:
        config != null
        config.profile() == "minimal"
        config.allow() == ["echo"]
        config.deny() == ["shell_exec"]
    }

    def "allow list only (no profile set) — bound profile preserved, env allow honored"() {
        given:
        AgentProperties.ToolPolicyConfig boundTools = new AgentProperties.ToolPolicyConfig(
                "coding", [], [])
        AgentProperties.AgentConfig bound = AgentProperties.AgentConfig.builder()
                .id("chat").tools(boundTools).build()
        env.getProperty(PREFIX + ".profile") >> null
        stubAllow(["a", "b"])
        stubDeny([])

        when:
        def config = resolveToolsFromEnvironment(bound)

        then:
        config != null
        config.profile() == "coding"   // taken from bound config
        config.allow() == ["a", "b"]   // taken from env
        config.deny() == []
    }

    def "empty env + no bound config — returns null"() {
        given:
        env.getProperty(PREFIX + ".profile") >> null
        stubAllow([])
        stubDeny([])

        when:
        def config = resolveToolsFromEnvironment(null)

        then:
        config == null
    }

    def "record binding already correct (matching profile, no env lists) — returns null"() {
        given:
        AgentProperties.ToolPolicyConfig boundTools = new AgentProperties.ToolPolicyConfig(
                "full", ["echo"], [])
        AgentProperties.AgentConfig bound = AgentProperties.AgentConfig.builder()
                .id("chat").tools(boundTools).build()
        env.getProperty(PREFIX + ".profile") >> "full"
        stubAllow([])
        stubDeny([])

        when:
        def config = resolveToolsFromEnvironment(bound)

        then: "no override applied — bound config wins"
        config == null
    }

    def "readStringList stops at first gap"() {
        given:
        env.getProperty(PREFIX + ".allow[0]") >> "first"
        env.getProperty(PREFIX + ".allow[1]") >> null
        env.getProperty(PREFIX + ".allow[2]") >> "third"   // unreachable

        when:
        List<String> result = readStringList(PREFIX + ".allow")

        then:
        result == ["first"]
    }
}
