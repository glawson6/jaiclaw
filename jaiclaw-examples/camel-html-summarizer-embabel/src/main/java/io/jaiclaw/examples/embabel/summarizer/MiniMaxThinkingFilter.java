package io.jaiclaw.examples.embabel.summarizer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Configuration;

import java.lang.reflect.Field;
import java.util.List;

/**
 * Fixes MiniMax's always-on thinking blocks for Embabel integration.
 *
 * <p>MiniMax's Anthropic-compatible API always returns thinking content blocks in the
 * response, even when thinking mode is not requested. Spring AI 1.1.4 creates separate
 * {@link Generation} objects for thinking and text content blocks. Embabel's
 * {@code SpringAiLlmMessageSender} takes the first generation (thinking) as the response,
 * causing JSON parsing failures when structured output is expected.
 *
 * <p>This configuration wraps each Anthropic ChatModel (inside Embabel's SpringAiLlmService
 * singletons) with a decorator that filters thinking generations from the response,
 * ensuring only text generations are returned to Embabel's tool loop.
 *
 * <p><b>Only needed when using MiniMax via the Anthropic-compatible endpoint.</b>
 *
 * <p><b>Why this exists despite the framework-level filter:</b> JaiClaw's
 * {@code MiniMaxThinkingFilterAutoConfiguration} wraps {@code ChatModel} beans via
 * {@code BeanPostProcessor}, but Embabel creates its own {@code ChatModel} instances
 * internally inside {@code SpringAiLlmService} — those are not Spring-managed beans
 * and the {@code BeanPostProcessor} cannot reach them. This class uses
 * {@code SmartInitializingSingleton} + reflection to wrap those internal instances
 * after all singletons are initialized.
 */
@Configuration
public class MiniMaxThinkingFilter implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(MiniMaxThinkingFilter.class);

    private final ConfigurableBeanFactory beanFactory;

    public MiniMaxThinkingFilter(ConfigurableBeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }

    @Override
    public void afterSingletonsInstantiated() {
        // Find all SpringAiLlmService singletons and wrap their ChatModel
        for (String name : beanFactory.getSingletonNames()) {
            Object bean = beanFactory.getSingleton(name);
            if (bean != null && bean.getClass().getName().contains("SpringAiLlmService")) {
                wrapChatModel(name, bean);
            }
        }
    }

    private void wrapChatModel(String beanName, Object llmService) {
        try {
            Field chatModelField = llmService.getClass().getDeclaredField("chatModel");
            chatModelField.setAccessible(true);
            ChatModel original = (ChatModel) chatModelField.get(llmService);
            ChatModel wrapped = new ThinkingFilterChatModel(original);
            chatModelField.set(llmService, wrapped);
            log.info("Wrapped ChatModel in '{}' with MiniMax thinking filter", beanName);
        } catch (Exception e) {
            log.warn("Could not wrap ChatModel in '{}': {} — MiniMax thinking blocks may cause JSON parse errors. "
                            + "Add JVM arg: --add-opens java.base/java.lang.reflect=ALL-UNNAMED",
                    beanName, e.getMessage());
        }
    }

    /**
     * ChatModel decorator that filters thinking generations from responses.
     *
     * <p>When a response contains multiple generations (thinking + text), this
     * filters out generations whose metadata contains a "signature" key (thinking
     * blocks from MiniMax), returning only text generations.
     */
    static class ThinkingFilterChatModel implements ChatModel {

        private static final Logger log = LoggerFactory.getLogger(ThinkingFilterChatModel.class);
        private final ChatModel delegate;

        ThinkingFilterChatModel(ChatModel delegate) {
            this.delegate = delegate;
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            ChatResponse response = delegate.call(prompt);
            return filterThinkingGenerations(response);
        }

        private ChatResponse filterThinkingGenerations(ChatResponse response) {
            if (response == null || response.getResults() == null || response.getResults().size() <= 1) {
                return response;
            }

            List<Generation> allGenerations = response.getResults();

            // Spring AI 1.1.4's AnthropicChatModel.toChatResponse() puts "signature"
            // into the AssistantMessage metadata for thinking content blocks.
            // Text blocks do not have this key. Filter to keep only non-thinking generations.
            List<Generation> textGenerations = allGenerations.stream()
                    .filter(gen -> {
                        if (gen.getOutput() == null) {
                            return false;
                        }
                        boolean isThinking = gen.getOutput().getMetadata() != null
                                && gen.getOutput().getMetadata().containsKey("signature");
                        if (isThinking) {
                            log.debug("Filtering thinking generation (signature: {})",
                                    gen.getOutput().getMetadata().get("signature"));
                        }
                        return !isThinking;
                    })
                    .toList();

            if (textGenerations.isEmpty()) {
                log.warn("All generations filtered as thinking; using last generation as fallback");
                textGenerations = List.of(allGenerations.getLast());
            }

            if (textGenerations.size() != allGenerations.size()) {
                log.debug("MiniMax thinking filter: {} thinking removed, {} text kept",
                        allGenerations.size() - textGenerations.size(), textGenerations.size());
            }

            return new ChatResponse(textGenerations, response.getMetadata());
        }
    }
}
