package io.jaiclaw.example.camel.pdffiller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

/**
 * Outbound route: consumes the LLM's field mapping response, fills the PDF
 * template, stores the result in the ArtifactStore, and writes it to the outbox.
 */
@Configuration
public class PdfOutputRoute extends RouteBuilder {

    private static final Logger log = LoggerFactory.getLogger(PdfOutputRoute.class);

    private final ArtifactStore artifactStore;
    private final PdfFormFiller pdfFormFiller;
    private final TemplateManager templateManager;
    private final String outbox;
    private final ObjectMapper mapper = new ObjectMapper();

    public PdfOutputRoute(
            ArtifactStore artifactStore,
            PdfFormFiller pdfFormFiller,
            TemplateManager templateManager,
            @Value("${app.outbox:target/data/outbox}") String outbox) {
        this.artifactStore = artifactStore;
        this.pdfFormFiller = pdfFormFiller;
        this.templateManager = templateManager;
        this.outbox = outbox;
    }

    @Override
    public void configure() {
        from("seda:jaiclaw-pdf-filler-out")
                .routeId("pdf-output")
                .process(new Processor() {
                    @Override
                    public void process(Exchange exchange) throws Exception {
                        String peerId = exchange.getIn().getHeader("JaiClawPeerId", String.class);
                        String body = exchange.getIn().getBody(String.class);
                        processPdfFill(peerId, body);
                    }
                });
    }

    private void processPdfFill(String peerId, String llmResponse) {
        try {
            // Detect agent error messages (not JSON) — e.g. "I encountered an error: ..."
            if (llmResponse != null && llmResponse.startsWith("I encountered an error")) {
                artifactStore.updateStatus(peerId, ArtifactStatus.FAILED, llmResponse);
                log.error("Agent returned error for [{}]: {}", peerId, llmResponse);
                return;
            }

            artifactStore.updateStatus(peerId, ArtifactStatus.PROCESSING, null);

            // Extract JSON from LLM response (may be wrapped in markdown code fences)
            String jsonStr = extractJson(llmResponse);
            JsonNode root = mapper.readTree(jsonStr);

            Map<String, String> fieldMappings = mapper.convertValue(
                    root.get("fieldMappings"), new TypeReference<>() {});
            List<String> unmapped = root.has("unmapped")
                    ? mapper.convertValue(root.get("unmapped"), new TypeReference<>() {})
                    : List.of();
            List<String> warnings = root.has("warnings")
                    ? mapper.convertValue(root.get("warnings"), new TypeReference<>() {})
                    : List.of();

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
                if (!unmapped.isEmpty()) {
                    log.warn("Unmapped fields for [{}]: {}", peerId, unmapped);
                }
                if (!success.skippedFields().isEmpty()) {
                    log.warn("Skipped fields for [{}]: {}", peerId, success.skippedFields());
                }
            } else if (result instanceof PdfFormResult.Failure failure) {
                artifactStore.updateStatus(peerId, ArtifactStatus.FAILED, failure.reason());
                log.error("Failed to fill PDF for [{}]: {}", peerId, failure.reason());
            }
        } catch (Exception e) {
            artifactStore.updateStatus(peerId, ArtifactStatus.FAILED, e.getMessage());
            log.error("Error processing PDF fill for [{}]", peerId, e);
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
