package net.taptech.jaiclaw.example.multiprovider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

import java.util.Map;

/**
 * Boot-and-log fixture that exercises
 * {@link io.jaiclaw.autoconfigure.LlmProviderBridgeEnvironmentPostProcessor}
 * with BOTH the Anthropic + OpenAI Spring AI starters on the classpath.
 *
 * <p>Prints a single line at boot in a fixed format so the e2e-test
 * skill's Scenario 7 can grep + assert:
 * <pre>
 * MPT-BOOT-OK selector=&lt;value&gt; chat-model-class=&lt;fqn&gt; chat-model-count=&lt;n&gt;
 * </pre>
 *
 * <p>Then exits with status 0. Success is measured by the log line
 * (or the presence of the fail-fast exception on stderr).
 *
 * <p>Configuration comes entirely from environment variables + defaults
 * in application.yml. See README.md for the four sub-scenarios and the
 * exact env-var recipes.
 */
@SpringBootApplication
public class MultiProviderToggleApp {

    private static final Logger log = LoggerFactory.getLogger(MultiProviderToggleApp.class);

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(MultiProviderToggleApp.class);
        // No web server — this is a boot-and-log fixture.
        app.setWebApplicationType(org.springframework.boot.WebApplicationType.NONE);
        app.run(args);
    }

    /**
     * Fires after all beans are constructed. Emits the marker line the
     * e2e-test skill greps for, then signals Spring to exit cleanly.
     */
    @Bean
    public CommandLineRunner mptBootMarker(Environment env,
                                            ObjectProvider<ChatModel> chatModels,
                                            ApplicationContext ctx) {
        return args -> {
            String selector = env.getProperty("spring.ai.model.chat", "<unset>");
            Map<String, ChatModel> chats = ctx.getBeansOfType(ChatModel.class);
            // Unwrap the JaiClaw ThinkingFilterChatModel wrapper so the
            // e2e-test grep can discriminate openai vs anthropic. When a
            // downstream ChatModel gets wrapped, the marker line reports
            // the *underlying* provider class name so skill scenario 7 can
            // assert on it.
            String chatClass = chats.isEmpty()
                    ? "<none>"
                    : unwrap(chats.values().iterator().next()).getClass().getName();
            log.info("MPT-BOOT-OK selector={} chat-model-class={} chat-model-count={}",
                    selector, chatClass, chats.size());
            // Exit cleanly so the skill can move on to the next sub-test.
            SpringApplication.exit(ctx, () -> 0);
        };
    }

    /**
     * Best-effort unwrap for wrapper chat models. JaiClaw's
     * MiniMaxThinkingFilterChatModel holds the delegate via a private
     * {@code delegate} field; reflect it out. If the field isn't present,
     * return the input unchanged.
     */
    private static ChatModel unwrap(ChatModel chatModel) {
        try {
            java.lang.reflect.Field delegateField = chatModel.getClass().getDeclaredField("delegate");
            delegateField.setAccessible(true);
            Object delegate = delegateField.get(chatModel);
            if (delegate instanceof ChatModel inner) {
                return unwrap(inner);
            }
        } catch (NoSuchFieldException | IllegalAccessException e) {
            // Not a wrapper we recognise — fall through.
        }
        return chatModel;
    }
}
