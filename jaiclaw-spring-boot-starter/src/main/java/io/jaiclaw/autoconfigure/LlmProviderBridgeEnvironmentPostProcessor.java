package io.jaiclaw.autoconfigure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.ClassUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Bridges JaiClaw's per-agent LLM provider selection into Spring AI's
 * {@code spring.ai.model.chat} discriminator so multi-starter apps
 * boot cleanly.
 *
 * <p><strong>The problem this solves:</strong> When an app ships more
 * than one Spring AI provider starter (e.g.
 * {@code spring-ai-starter-model-anthropic} AND
 * {@code spring-ai-starter-model-openai}) so it can flip providers at
 * deploy time, Spring Boot 4 fails at startup with
 * {@code No qualifying bean of type ChatModel} unless the operator
 * knows to set {@code spring.ai.model.chat=<provider>}. Spring AI's
 * provider auto-configs are gated with
 * {@code @ConditionalOnProperty(name="spring.ai.model.chat", ..., matchIfMissing=true)}
 * — with the property unset, all providers on the classpath try to
 * register their {@code ChatModel} beans and the {@code @Primary}-less
 * autowire trips.
 *
 * <p><strong>What this post-processor does:</strong>
 * <ol>
 *   <li>If an operator has already set {@code spring.ai.model.chat},
 *       do nothing — the explicit setting always wins.</li>
 *   <li>Otherwise, resolve a provider from
 *       {@code jaiclaw.agent.agents.<id>.llm.provider}. Try the
 *       default agent first ({@code jaiclaw.agent.default-agent}, or
 *       {@code "default"} when unset); if that yields nothing, scan
 *       every configured agent and pick the first non-null provider.
 *       Inject the resolved value as
 *       {@code spring.ai.model.chat=<provider>} into a
 *       {@link MapPropertySource} named {@code jaiclawLlmProviderBridge},
 *       added at highest priority.</li>
 *   <li>If no bridge source is available AND two or more Spring AI
 *       provider starters are on the classpath, throw
 *       {@link IllegalStateException} with a targeted message naming
 *       both properties an operator can set. This is strictly better
 *       than the downstream "No qualifying bean" mystery.</li>
 * </ol>
 *
 * <p>Ordered at {@code Ordered.HIGHEST_PRECEDENCE + 20} — earlier than
 * Spring's default env-var/system-property sources but after
 * {@link io.jaiclaw.autoconfigure.secrets.SecretsEnvironmentPostProcessor}
 * (which lands at {@code HIGHEST_PRECEDENCE + 10}) so provider names
 * backed by secrets resolve correctly.
 *
 * <p>Registered via {@code META-INF/spring.factories}.
 */
public final class LlmProviderBridgeEnvironmentPostProcessor
        implements EnvironmentPostProcessor, Ordered {

    private static final Logger log = LoggerFactory.getLogger(
            LlmProviderBridgeEnvironmentPostProcessor.class);

    static final String SPRING_AI_SELECTOR = "spring.ai.model.chat";
    static final String DEFAULT_AGENT_KEY = "jaiclaw.agent.default-agent";
    static final String AGENTS_PREFIX = "jaiclaw.agent.agents.";
    static final String AGENTS_BINDING_PREFIX = "jaiclaw.agent.agents";
    static final String BRIDGE_SOURCE_NAME = "jaiclawLlmProviderBridge";

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 20;
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment,
                                        SpringApplication application) {
        // 1. Never override an explicit operator setting.
        if (environment.getProperty(SPRING_AI_SELECTOR) != null) {
            return;
        }

        // 2. Bridge path: derive the provider from JaiClaw agent config.
        Resolution resolved = resolveProvider(environment);
        if (resolved != null) {
            environment.getPropertySources().addFirst(new MapPropertySource(
                    BRIDGE_SOURCE_NAME,
                    Map.of(SPRING_AI_SELECTOR, resolved.provider())));
            log.info("Bridged {}={} from jaiclaw.agent.agents.{}.llm.provider (source: {})",
                    SPRING_AI_SELECTOR, resolved.provider(),
                    resolved.agentId(), resolved.source());
            return;
        }

        // 3. Fail-fast path: multiple Spring AI provider starters AND no bridge source.
        List<String> detected = detectProviderStarters(application.getClassLoader());
        if (detected.size() >= 2) {
            throw new IllegalStateException(
                    "Multiple Spring AI chat provider starters detected on the "
                            + "classpath (" + String.join(", ", detected) + ") but no "
                            + "selector is set. Either:\n"
                            + "  - Set `" + SPRING_AI_SELECTOR + "=<provider>` in "
                            + "application.yml or as an env var, OR\n"
                            + "  - Configure `jaiclaw.agent.agents.<agentId>.llm.provider"
                            + "=<provider>` on any JaiClaw agent so the framework "
                            + "auto-bridges it.\n"
                            + "See jaiclaw-spring-boot-starter's LlmProviderBridgeEnvironmentPostProcessor "
                            + "for the resolution rules.");
        }
    }

    /**
     * Try the default agent's provider first; fall back to scanning
     * every configured agent's provider field and picking the first
     * non-null value. Returns {@code null} when nothing bindable is
     * found — the caller then decides whether to fail-fast.
     */
    private static Resolution resolveProvider(ConfigurableEnvironment environment) {
        String defaultAgentId = environment.getProperty(DEFAULT_AGENT_KEY, "default");
        String defaultProvider = environment.getProperty(
                AGENTS_PREFIX + defaultAgentId + ".llm.provider");
        if (defaultProvider != null && !defaultProvider.isBlank()) {
            return new Resolution(defaultProvider.trim(), defaultAgentId, "default-agent");
        }

        // Fallback scan — enumerate every agent id via Spring's Binder
        // and probe each for a provider. Boot's Binder is the only
        // reliable way to enumerate map keys under a prefix.
        Binder binder = Binder.get(environment);
        Map<String, Object> agents = binder
                .bind(AGENTS_BINDING_PREFIX, Bindable.mapOf(String.class, Object.class))
                .orElse(Map.of());
        for (String agentId : agents.keySet()) {
            if (agentId.equals(defaultAgentId)) continue; // already tried
            String provider = environment.getProperty(
                    AGENTS_PREFIX + agentId + ".llm.provider");
            if (provider != null && !provider.isBlank()) {
                return new Resolution(provider.trim(), agentId, "fallback-scan");
            }
        }
        return null;
    }

    /**
     * Probe for Spring AI provider marker classes on the classpath.
     * Returns a list of the provider names whose starter is present.
     */
    private static List<String> detectProviderStarters(ClassLoader cl) {
        // Marker classes correspond to the current Spring AI 2.0 provider
        // starter artifact names in jaiclaw-spring-boot-starter/pom.xml.
        // Note: google-genai replaced the pre-2.0 vertexai-gemini starter.
        return Stream.of(
                new Probe("openai", "org.springframework.ai.openai.OpenAiChatModel"),
                new Probe("anthropic", "org.springframework.ai.anthropic.AnthropicChatModel"),
                new Probe("ollama", "org.springframework.ai.ollama.OllamaChatModel"),
                new Probe("google-genai", "org.springframework.ai.google.genai.GoogleGenAiChatModel"),
                new Probe("bedrock-converse", "org.springframework.ai.bedrock.converse.BedrockProxyChatModel"),
                new Probe("deepseek", "org.springframework.ai.deepseek.DeepSeekChatModel"),
                new Probe("mistralai", "org.springframework.ai.mistralai.MistralAiChatModel")
        ).filter(p -> ClassUtils.isPresent(p.marker(), cl))
                .map(Probe::starter)
                .toList();
    }

    private record Probe(String starter, String marker) {}

    private record Resolution(String provider, String agentId, String source) {}
}
