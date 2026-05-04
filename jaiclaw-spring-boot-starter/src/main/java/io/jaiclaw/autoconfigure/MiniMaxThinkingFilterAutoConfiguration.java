package io.jaiclaw.autoconfigure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

import java.util.List;

/**
 * Auto-configuration that filters MiniMax thinking content blocks from ChatModel responses.
 *
 * <p>MiniMax's Anthropic-compatible API ({@code api.minimax.io/anthropic}) always returns
 * thinking content blocks in responses, even when thinking mode is not requested. Spring AI
 * creates separate {@link Generation} objects for thinking vs text blocks, and the thinking
 * content leaks into user-facing responses (chain-of-thought reasoning).
 *
 * <p>This auto-configuration wraps every {@link ChatModel} bean with a decorator that
 * filters out thinking generations (identified by a "signature" metadata key). It is enabled
 * by default and can be disabled with {@code jaiclaw.models.minimax.filter-thinking=false}.
 *
 * <p><b>Note:</b> This filter does not reach {@link ChatModel} instances created internally
 * by Embabel's {@code SpringAiLlmService}. Embabel apps must use a separate
 * {@code SmartInitializingSingleton} approach (see {@code camel-html-summarizer-embabel}
 * example).
 */
@AutoConfiguration
@AutoConfigureAfter(JaiClawAutoConfiguration.class)
@ConditionalOnClass(name = "org.springframework.ai.chat.model.ChatModel")
@ConditionalOnProperty(name = "jaiclaw.models.minimax.filter-thinking", havingValue = "true", matchIfMissing = true)
public class MiniMaxThinkingFilterAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(MiniMaxThinkingFilterAutoConfiguration.class);

    @Bean
    static BeanPostProcessor miniMaxThinkingFilter() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
                if (bean instanceof ChatModel chatModel && !(bean instanceof ThinkingFilterChatModel)) {
                    log.info("Wrapping ChatModel '{}' with MiniMax thinking filter (type: {})",
                            beanName, chatModel.getClass().getSimpleName());
                    return new ThinkingFilterChatModel(chatModel);
                }
                return bean;
            }
        };
    }

    /**
     * ChatModel decorator that filters thinking generations from responses.
     *
     * <p>When a response contains multiple generations (thinking + text), this removes
     * generations whose metadata contains a "signature" key (MiniMax's thinking blocks)
     * and returns only text generations.
     */
    static class ThinkingFilterChatModel implements ChatModel {

        private static final Logger log = LoggerFactory.getLogger(ThinkingFilterChatModel.class);
        private final ChatModel delegate;

        ThinkingFilterChatModel(ChatModel delegate) {
            this.delegate = delegate;
        }

        ChatModel getDelegate() {
            return delegate;
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
