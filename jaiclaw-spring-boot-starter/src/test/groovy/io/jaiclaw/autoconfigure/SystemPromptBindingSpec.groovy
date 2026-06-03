package io.jaiclaw.autoconfigure

import io.jaiclaw.config.SystemPromptConfig
import org.springframework.core.env.Environment
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

/**
 * Tests the Environment-based fallback logic for system prompt resolution
 * in JaiClawAutoConfiguration. When Spring Boot's record binding fails for
 * deeply nested Map<String, Record>, the auto-configuration falls back to
 * reading individual properties from the Environment.
 *
 * This spec verifies the fallback resolves correctly with and without the
 * strategy field — matching SystemPromptConfig's compact constructor behavior
 * of defaulting null/blank strategy to "inline".
 */
class SystemPromptBindingSpec extends Specification {

    static final String PREFIX = "jaiclaw.agent.agents.default.system-prompt"

    Environment env = Mock(Environment)

    /**
     * Mirrors the fallback logic in JaiClawAutoConfiguration.agentRuntime()
     * (lines ~465-475 after the fix).
     */
    private SystemPromptConfig resolveFromEnvironment() {
        String strategy = env.getProperty(PREFIX + ".strategy")
        String content = env.getProperty(PREFIX + ".content")
        String source = env.getProperty(PREFIX + ".source")
        if (strategy != null || content != null || source != null) {
            boolean append = Boolean.parseBoolean(env.getProperty(PREFIX + ".append", "false"))
            return new SystemPromptConfig(strategy, content, source, append)
        }
        return null
    }

    def "resolves system prompt with explicit strategy and content"() {
        given:
        env.getProperty(PREFIX + ".strategy") >> "inline"
        env.getProperty(PREFIX + ".content") >> "You are a helpful assistant."
        env.getProperty(PREFIX + ".source") >> null
        env.getProperty(PREFIX + ".append", "false") >> "false"

        when:
        SystemPromptConfig config = resolveFromEnvironment()

        then:
        config != null
        config.strategy() == "inline"
        config.content() == "You are a helpful assistant."
        !config.append()
    }

    def "resolves system prompt when only content is provided (no strategy)"() {
        given: "content is set but strategy is absent — the scaffolder's original output shape"
        env.getProperty(PREFIX + ".strategy") >> null
        env.getProperty(PREFIX + ".content") >> "You are a helpful assistant."
        env.getProperty(PREFIX + ".source") >> null
        env.getProperty(PREFIX + ".append", "false") >> "false"

        when:
        SystemPromptConfig config = resolveFromEnvironment()

        then: "strategy defaults to 'inline' via SystemPromptConfig compact constructor"
        config != null
        config.strategy() == "inline"
        config.content() == "You are a helpful assistant."
    }

    def "resolves classpath strategy with source"() {
        given:
        env.getProperty(PREFIX + ".strategy") >> "classpath"
        env.getProperty(PREFIX + ".content") >> null
        env.getProperty(PREFIX + ".source") >> "prompts/system.md"
        env.getProperty(PREFIX + ".append", "false") >> "true"

        when:
        SystemPromptConfig config = resolveFromEnvironment()

        then:
        config != null
        config.strategy() == "classpath"
        config.source() == "prompts/system.md"
        config.append()
    }

    def "resolves when only source is provided (no strategy, no content)"() {
        given:
        env.getProperty(PREFIX + ".strategy") >> null
        env.getProperty(PREFIX + ".content") >> null
        env.getProperty(PREFIX + ".source") >> "prompts/system.md"
        env.getProperty(PREFIX + ".append", "false") >> "false"

        when:
        SystemPromptConfig config = resolveFromEnvironment()

        then: "strategy defaults to 'inline' — caller will use source for loading"
        config != null
        config.strategy() == "inline"
        config.source() == "prompts/system.md"
    }

    def "returns null when no system-prompt properties are set"() {
        given:
        env.getProperty(PREFIX + ".strategy") >> null
        env.getProperty(PREFIX + ".content") >> null
        env.getProperty(PREFIX + ".source") >> null

        when:
        SystemPromptConfig config = resolveFromEnvironment()

        then:
        config == null
    }

    def "append defaults to false when not specified"() {
        given:
        env.getProperty(PREFIX + ".strategy") >> "inline"
        env.getProperty(PREFIX + ".content") >> "prompt text"
        env.getProperty(PREFIX + ".source") >> null
        env.getProperty(PREFIX + ".append", "false") >> "false"

        when:
        SystemPromptConfig config = resolveFromEnvironment()

        then:
        !config.append()
    }

    def "SystemPromptConfig compact constructor defaults blank strategy to inline"() {
        when:
        SystemPromptConfig config = new SystemPromptConfig("", "content", null, false)

        then:
        config.strategy() == "inline"
    }

    def "SystemPromptConfig compact constructor defaults null strategy to inline"() {
        when:
        SystemPromptConfig config = new SystemPromptConfig(null, "content", null, false)

        then:
        config.strategy() == "inline"
    }
}
