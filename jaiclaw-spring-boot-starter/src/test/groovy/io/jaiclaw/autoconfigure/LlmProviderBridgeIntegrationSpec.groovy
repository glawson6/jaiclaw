package io.jaiclaw.autoconfigure

import org.springframework.ai.chat.model.ChatModel
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.Configuration
import spock.lang.Specification

/**
 * Real-Spring-context integration tests for
 * {@link LlmProviderBridgeEnvironmentPostProcessor}.
 *
 * <p>Unlike the sibling unit spec (which uses a stub classloader),
 * this spec boots an actual Spring context with both
 * {@code spring-ai-starter-model-openai} AND
 * {@code spring-ai-starter-model-anthropic} on the classpath (they
 * are declared as {@code <optional>true</optional>} in
 * jaiclaw-spring-boot-starter's pom, which puts them on this
 * module's test classpath). Verifies that the bridge + fail-fast
 * behave correctly end-to-end through Spring's real
 * autoconfiguration cycle.
 *
 * <p>Fake API keys are supplied so the starters can bootstrap their
 * {@code ChatModel} beans; the tests never invoke the models.
 */
class LlmProviderBridgeIntegrationSpec extends Specification {

    /**
     * Minimal fixture — imports ONLY the Spring AI provider autoconfigs
     * needed to observe which ChatModel bean the bridge caused to fire.
     * Using @Configuration + @ImportAutoConfiguration instead of
     * @SpringBootApplication skips the full autoconfig scan (which would
     * otherwise fail on unrelated JaiClaw autoconfigs that require the
     * complete transitive classpath). The LlmProviderBridgeEnvironmentPostProcessor
     * still runs — it's registered via spring.factories at env-post-process
     * time, which fires regardless of whether autoconfigs are scanned.
     */
    @Configuration
    @ImportAutoConfiguration([
            org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration.class,
            org.springframework.ai.model.anthropic.autoconfigure.AnthropicChatAutoConfiguration.class,
            org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration.class,
            org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration.class
    ])
    static class Fixture {}

    private static String[] argsWith(String... entries) {
        return entries as String[]
    }

    def "Scenario A: default agent's llm.provider bridges to OpenAI ChatModel bean"() {
        given:
        String[] args = argsWith(
                "--jaiclaw.agent.agents.default.llm.provider=openai",
                "--spring.ai.openai.api-key=stub-openai-key",
                "--spring.ai.anthropic.api-key=stub-anthropic-key"
        )
        SpringApplication app = new SpringApplication(Fixture)
        app.setWebApplicationType(org.springframework.boot.WebApplicationType.NONE)

        when:
        ConfigurableApplicationContext ctx = app.run(args)

        then: "the bridge injected the selector"
        ctx.environment.getProperty("spring.ai.model.chat") == "openai"

        and: "exactly one ChatModel is registered, and it's the OpenAI one"
        Map<String, ChatModel> chatModels = ctx.getBeansOfType(ChatModel.class)
        chatModels.size() == 1
        chatModels.values().first().class.name.contains("OpenAi")

        cleanup:
        ctx?.close()
    }

    def "Scenario B: no bridge source + both starters → fail-fast IllegalStateException"() {
        given:
        String[] args = argsWith(
                "--spring.ai.openai.api-key=stub-openai-key",
                "--spring.ai.anthropic.api-key=stub-anthropic-key"
        )
        SpringApplication app = new SpringApplication(Fixture)
        app.setWebApplicationType(org.springframework.boot.WebApplicationType.NONE)

        when:
        app.run(args)

        then: "post-processor throws before any autoconfig fires; cause chain contains ISE"
        Throwable ex = thrown()
        Throwable cause = ex
        while (cause != null && !(cause instanceof IllegalStateException)) {
            cause = cause.cause
        }
        cause != null
        cause.message.contains("Multiple Spring AI chat provider starters detected")
        cause.message.contains("spring.ai.model.chat")
        cause.message.contains("jaiclaw.agent.agents.<agentId>.llm.provider")
    }

    def "Scenario C: explicit spring.ai.model.chat wins over jaiclaw.agent.*.llm.provider"() {
        given:
        String[] args = argsWith(
                "--spring.ai.model.chat=anthropic",
                "--jaiclaw.agent.agents.default.llm.provider=openai",
                "--spring.ai.openai.api-key=stub-openai-key",
                "--spring.ai.anthropic.api-key=stub-anthropic-key"
        )
        SpringApplication app = new SpringApplication(Fixture)
        app.setWebApplicationType(org.springframework.boot.WebApplicationType.NONE)

        when:
        ConfigurableApplicationContext ctx = app.run(args)

        then: "operator's anthropic wins, not the agent's openai"
        ctx.environment.getProperty("spring.ai.model.chat") == "anthropic"

        and: "the resolved ChatModel is the Anthropic one"
        Map<String, ChatModel> chatModels = ctx.getBeansOfType(ChatModel.class)
        chatModels.size() == 1
        chatModels.values().first().class.name.contains("Anthropic")

        cleanup:
        ctx?.close()
    }
}
