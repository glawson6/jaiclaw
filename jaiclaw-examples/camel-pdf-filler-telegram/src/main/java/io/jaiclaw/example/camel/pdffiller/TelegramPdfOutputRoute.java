package io.jaiclaw.example.camel.pdffiller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jaiclaw.channel.ChannelAdapter;
import io.jaiclaw.channel.ChannelMessage;
import io.jaiclaw.channel.ChannelRegistry;
import io.jaiclaw.channel.DeliveryResult;
import io.jaiclaw.core.artifact.ArtifactStatus;
import io.jaiclaw.core.artifact.ArtifactStore;
import io.jaiclaw.core.artifact.StoredArtifact;
import io.jaiclaw.documents.PdfFormFiller;
import io.jaiclaw.documents.PdfFormResult;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Outbound route: consumes the LLM's field mapping response, fills the PDF
 * template, and sends a notification to Telegram.
 *
 * <p>When the LLM identifies unmapped fields and sets {@code clarificationNeeded: true},
 * the question is forwarded to the Telegram user. The stateful channel preserves
 * conversation context, so the user's reply flows back through the agent loop.
 * The agent processes the corrections and returns a final complete mapping.
 */
@Configuration
public class TelegramPdfOutputRoute extends RouteBuilder {

    private static final Logger log = LoggerFactory.getLogger(TelegramPdfOutputRoute.class);

    private final ArtifactStore artifactStore;
    private final PdfFormFiller pdfFormFiller;
    private final TemplateManager templateManager;
    private final ChannelRegistry channelRegistry;
    private final String outbox;
    private final String chatId;
    private final ObjectMapper mapper = new ObjectMapper();

    public TelegramPdfOutputRoute(
            ArtifactStore artifactStore,
            PdfFormFiller pdfFormFiller,
            TemplateManager templateManager,
            ChannelRegistry channelRegistry,
            @Value("${app.outbox:target/data/outbox}") String outbox,
            @Value("${app.telegram.chat-id}") String chatId) {
        this.artifactStore = artifactStore;
        this.pdfFormFiller = pdfFormFiller;
        this.templateManager = templateManager;
        this.channelRegistry = channelRegistry;
        this.outbox = outbox;
        this.chatId = chatId;
    }

    @Override
    public void configure() {
        from("seda:jaiclaw-pdf-filler-telegram-out")
                .routeId("telegram-pdf-output")
                .process(new Processor() {
                    @Override
                    public void process(Exchange exchange) throws Exception {
                        String peerId = exchange.getIn().getHeader("JaiClawPeerId", String.class);
                        String body = exchange.getIn().getBody(String.class);
                        processLlmResponse(peerId, body);
                    }
                });
    }

    private void processLlmResponse(String peerId, String llmResponse) {
        try {
            // Detect agent error messages (not JSON) — e.g. "I encountered an error: ..."
            if (llmResponse != null && llmResponse.startsWith("I encountered an error")) {
                artifactStore.updateStatus(peerId, ArtifactStatus.FAILED, llmResponse);
                sendToTelegram("PDF fill failed for [" + peerId + "]: " + llmResponse);
                log.error("Agent returned error for [{}]: {}", peerId, llmResponse);
                return;
            }

            String jsonStr = extractJson(llmResponse);
            JsonNode root = mapper.readTree(jsonStr);

            boolean clarificationNeeded = root.has("clarificationNeeded")
                    && root.get("clarificationNeeded").asBoolean(false);

            if (clarificationNeeded && root.has("question")) {
                // Human-in-the-loop: send question to Telegram
                String question = root.get("question").asText();
                artifactStore.updateStatus(peerId, ArtifactStatus.PROCESSING,
                        "Waiting for user clarification on Telegram");
                sendToTelegram("PDF Fill [" + peerId + "] needs your input:\n\n" + question);
                log.info("Sent clarification request to Telegram for [{}]", peerId);
                return;
            }

            // Final mapping — fill the PDF
            Map<String, String> fieldMappings = mapper.convertValue(
                    root.get("fieldMappings"), new TypeReference<>() {});
            List<String> unmapped = root.has("unmapped")
                    ? mapper.convertValue(root.get("unmapped"), new TypeReference<>() {})
                    : List.of();
            List<String> warnings = root.has("warnings")
                    ? mapper.convertValue(root.get("warnings"), new TypeReference<>() {})
                    : List.of();

            artifactStore.updateStatus(peerId, ArtifactStatus.PROCESSING, null);

            PdfFormResult result = pdfFormFiller.fill(templateManager.getTemplateBytes(), fieldMappings);
            if (result instanceof PdfFormResult.Success success) {
                Map<String, String> metadata = new HashMap<>();
                if (!unmapped.isEmpty()) {
                    metadata.put("unmapped", String.join(",", unmapped));
                }
                if (!warnings.isEmpty()) {
                    metadata.put("warnings", String.join("; ", warnings));
                }
                if (!success.skippedFields().isEmpty()) {
                    metadata.put("skipped", String.join("; ", success.skippedFields()));
                }

                StoredArtifact artifact = new StoredArtifact(
                        peerId, success.pdfBytes(), "application/pdf",
                        peerId + ".pdf", ArtifactStatus.COMPLETED, null,
                        Instant.now(), Map.copyOf(metadata));
                artifactStore.save(artifact);

                Path outboxDir = Path.of(outbox);
                Files.createDirectories(outboxDir);
                Path outputPath = outboxDir.resolve(peerId + ".pdf");
                Files.write(outputPath, success.pdfBytes());

                log.info("Filled PDF for [{}] ({} fields set, {} skipped) -> {}",
                        peerId, success.fieldsSet(), success.skippedFields().size(), outputPath);

                String notification = "PDF filled for [" + peerId + "] with "
                        + success.fieldsSet() + " fields set.";
                if (!unmapped.isEmpty()) {
                    notification += "\nUnmapped: " + unmapped;
                }
                if (!success.skippedFields().isEmpty()) {
                    notification += "\nSkipped: " + success.skippedFields();
                }
                sendToTelegram(notification);
            } else if (result instanceof PdfFormResult.Failure failure) {
                artifactStore.updateStatus(peerId, ArtifactStatus.FAILED, failure.reason());
                sendToTelegram("PDF fill failed for [" + peerId + "]: " + failure.reason());
                log.error("Failed to fill PDF for [{}]: {}", peerId, failure.reason());
            }
        } catch (Exception e) {
            artifactStore.updateStatus(peerId, ArtifactStatus.FAILED, e.getMessage());
            log.error("Error processing PDF fill for [{}]", peerId, e);
        }
    }

    private void sendToTelegram(String content) {
        ChannelAdapter telegramAdapter = channelRegistry.get("telegram")
                .orElseThrow(() -> new IllegalStateException(
                        "Telegram adapter not found in ChannelRegistry"));

        ChannelMessage outbound = ChannelMessage.outbound(
                UUID.randomUUID().toString(),
                "telegram",
                "camel-pdf-filler",
                chatId,
                content);

        DeliveryResult result = telegramAdapter.sendMessage(outbound);
        if (result instanceof DeliveryResult.Failure failure) {
            log.error("Failed to send message to Telegram: {}", failure.message());
        }
    }

    private String extractJson(String response) {
        String trimmed = response.strip();
        // Strip markdown code fences if present (```json ... ```)
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstNewline > 0 && lastFence > firstNewline) {
                trimmed = trimmed.substring(firstNewline + 1, lastFence).strip();
            }
        }
        // Fallback: extract JSON object between first { and last }
        int firstBrace = trimmed.indexOf('{');
        int lastBrace = trimmed.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            trimmed = trimmed.substring(firstBrace, lastBrace + 1);
        }
        return trimmed;
    }
}
