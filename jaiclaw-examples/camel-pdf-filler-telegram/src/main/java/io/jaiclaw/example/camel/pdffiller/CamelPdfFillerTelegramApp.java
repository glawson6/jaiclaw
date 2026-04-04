package io.jaiclaw.example.camel.pdffiller;

import io.jaiclaw.core.artifact.ArtifactStore;
import io.jaiclaw.core.artifact.InMemoryArtifactStore;
import io.jaiclaw.documents.PdfFormFiller;
import io.jaiclaw.documents.PdfFormReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * Human-in-the-loop PDF form-filling pipeline with Telegram integration.
 *
 * <p>Same as the automated pipeline, but when the LLM can't confidently map
 * all JSON fields, it asks the user on Telegram to resolve ambiguities.
 */
@SpringBootApplication
public class CamelPdfFillerTelegramApp {

    private static final Logger log = LoggerFactory.getLogger(CamelPdfFillerTelegramApp.class);

    public static void main(String[] args) {
        SpringApplication.run(CamelPdfFillerTelegramApp.class, args);
    }

    @Bean
    ArtifactStore artifactStore() {
        return new InMemoryArtifactStore();
    }

    @Bean
    PdfFormReader pdfFormReader() {
        return new PdfFormReader();
    }

    @Bean
    PdfFormFiller pdfFormFiller() {
        return new PdfFormFiller();
    }

    @Bean
    ApplicationRunner startupLogger(Environment env, ChatModel chatModel, TemplateManager templateManager) {
        return args -> {
            String provider = env.getProperty("spring.ai.model.chat", "anthropic");
            String model = switch (provider) {
                case "anthropic" -> env.getProperty("spring.ai.anthropic.chat.options.model", "claude-haiku-4-5");
                case "openai" -> env.getProperty("spring.ai.openai.chat.options.model", "gpt-4o");
                default -> "unknown";
            };
            String inbox = env.getProperty("app.inbox", "target/data/inbox");
            String outbox = env.getProperty("app.outbox", "target/data/outbox");
            String template = env.getProperty("app.template", "file:target/data/templates/sample-form.pdf");
            String chatId = env.getProperty("app.telegram.chat-id", "not-set");
            int fieldCount = templateManager.getFields().size();

            log.info("=== PDF Filler (Telegram) Configuration ===");
            log.info("  AI Provider : {} | Model: {} | ChatModel: {}", provider, model, chatModel.getClass().getSimpleName());
            log.info("  Template    : {} ({} form fields)", template, fieldCount);
            log.info("  Inbox       : {}", inbox);
            log.info("  Outbox      : {}", outbox);
            log.info("  Telegram    : chat-id={}", chatId);
            log.info("============================================");
        };
    }
}
